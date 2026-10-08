package cplus.semantic

import cplus.core.*
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TraitResolutionTest {
    @Test
    fun resolvesStructPointerAndPrimitiveValueReceiversAndRecordsAdaptation() {
        val result = analyze(
            """
                struct point_t { int value; };
                comptime trait point_t {
                    int read(self*) { return self->value; }
                }
                comptime trait int {
                    int twice(self) { return self * 2; }
                }
                int main() {
                    point_t point;
                    int scalar = 3;
                    return point.read() + scalar.twice();
                }
            """
        )

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val model = assertNotNull(result.model)
        val calls = model.resolvedMethodCalls.values.associateBy { it.method.symbol.name }
        assertEquals(ReceiverAdaptation.ADDRESS, calls.getValue("read").adaptation)
        assertEquals(ReceiverAdaptation.VALUE, calls.getValue("twice").adaptation)
        assertEquals(listOf("read"), model.lookupMethods(model.structs.getValue("point_t"), "read", "<main>").map { it.symbol.name })
        val intType = model.types.filterIsInstance<PrimitiveType>().single { it.name == "int" }
        assertEquals(listOf("twice"), model.lookupMethods(intType, "twice", "<main>").map { it.symbol.name })
    }

    @Test
    fun resolvesAliasUnionEnumAndImportedTargetsToTheirCanonicalOwners() {
        val provider = parse(101, "geometry.cp", "pub struct point_t { int value; };")
        val consumer = parse(
            102,
            "client.cp",
            """
                import {point_t} from geometry;
                typedef unsigned long long score_t;
                union packet_t { int value; };
                enum shade_t { light };
                comptime trait point_t { int read(self) { return self.value; } }
                comptime trait score_t { int doubled(self) { return self * 2; } }
                comptime trait packet_t { int readPacket(self) { return self.value; } }
                comptime trait shade_t { int code(self) { return 1; } }
                int main() {
                    point_t point;
                    score_t score = 2;
                    packet_t packet;
                    shade_t shade;
                    return point.read() + score.doubled() + packet.readPacket() + shade.code();
                }
            """
        )
        val declarations = provider.declarations + consumer.declarations
        val program = AstProgram(
            declarations,
            consumer.origin,
            listOf(AstModule("geometry", provider.declarations), AstModule("client", consumer.declarations))
        )

        val result = SemanticAnalyzer().analyze(program, knownModules = setOf("geometry"))

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val model = assertNotNull(result.model)
        val importedPointMethod = model.methodRegistry.lookup(
            ReceiverIdentity.of(model.structs.getValue("point_t")),
            "read",
            model.extensionModuleImports["client"].orEmpty(),
            "client"
        ).single()
        assertEquals("client", importedPointMethod.definingModule)
        assertEquals("geometry", (importedPointMethod.owner as StructType).moduleName)
        assertEquals(ReceiverAdaptation.VALUE, model.resolvedMethodCalls.values.single { it.method.symbol.name == "doubled" }.adaptation)
        assertEquals(ReceiverAdaptation.ADDRESS, model.resolvedMethodCalls.values.single { it.method.symbol.name == "read" }.adaptation)
        assertTrue(model.resolvedMethodCalls.keys.any { it.callee is AstMemberAccess })
    }

    @Test
    fun rejectsInvalidTargetsBodiesArgumentsAndNonAddressablePointerReceivers() {
        val parsedProgram = parse(
            100,
            "traits.cp",
            """
                typedef int* pointer_t;
                typedef int array_t[4];
                comptime trait void { int invalid(self) { return 0; } }
                comptime trait missing_type { int invalid(self) { return 0; } }
                comptime trait pointer_t { int invalid(self) { return 0; } }
                comptime trait array_t { int invalid(self) { return 0; } }
                comptime trait int {
                    int wrongResult(self) { return "bad"; }
                    int needsArgument(self, int value) { return value; }
                    int addressOnly(self*) { return self; }
                }
                int main() {
                    int value = 1;
                    return value.needsArgument() + (1 + 2).addressOnly();
                }
            """
        )
        val origin = parsedProgram.origin
        val intType = AstTypeRef("int", false, 0, origin)
        val callbackType = AstTypeRef(
            "int",
            false,
            0,
            origin,
            functionParameters = listOf(AstParameter(intType, "value", origin = origin)),
            functionPointerDepth = 1
        )
        val templateMethod = parsedProgram.declarations.filterIsInstance<AstTrait>().first().methods
        val callbackAlias = AstAlias(callbackType, "callback_t", origin = origin)
        val callbackTrait = AstTrait("callback_t", origin, templateMethod, origin)
        val result = SemanticAnalyzer().analyze(
            parsedProgram.copy(declarations = parsedProgram.declarations + callbackAlias + callbackTrait)
        )

        assertTrue(result.diagnostics.count { it.code == "SEM417" } >= 4, result.diagnostics.joinToString())
        assertTrue(result.diagnostics.any { it.code == "SEM102" }, result.diagnostics.joinToString())
        assertTrue(result.diagnostics.any { it.code == "SEM203" }, result.diagnostics.joinToString())
        assertTrue(result.diagnostics.any { it.code == "SEM303" || it.code == "SEM304" }, result.diagnostics.joinToString())
        assertTrue(result.diagnostics.any { it.code == "SEM419" }, result.diagnostics.joinToString())
    }

    @Test
    fun rejectsPrivateImportedAndIncompleteForeignTraitTargets() {
        val provider = parse(103, "private-types.cp", "struct secret_t { int value; };")
        val consumer = parse(
            104,
            "private-client.cp",
            "import {secret_t} from geometry; comptime trait secret_t { int read(self) { return self.value; } }"
        )
        val privateResult = SemanticAnalyzer().analyze(
            AstProgram(
                provider.declarations + consumer.declarations,
                consumer.origin,
                listOf(AstModule("geometry", provider.declarations), AstModule("client", consumer.declarations))
            ),
            knownModules = setOf("geometry")
        )
        assertTrue(privateResult.diagnostics.any { it.code == "SEM406" }, privateResult.diagnostics.joinToString())

        val header = SourceFile(SourceFileId(105), Path.of("opaque.h"), "struct opaque_t;", 1)
        val opaqueConsumer = parse(
            106,
            "opaque-client.cp",
            "import {opaque_t} from c.test; comptime trait opaque_t { int size(self) { return 0; } }"
        )
        val opaqueResult = SemanticAnalyzer().analyze(
            opaqueConsumer,
            foreignSources = listOf(CSourceUnit(header, "c.test"))
        )
        assertTrue(opaqueResult.diagnostics.any { it.code == "SEM414" }, opaqueResult.diagnostics.joinToString())
        assertTrue(opaqueResult.diagnostics.any { it.code == "SEM417" }, opaqueResult.diagnostics.joinToString())
    }

    private fun analyze(text: String): SemanticResult {
        return SemanticAnalyzer().analyze(parse(100, "traits.cp", text))
    }

    private fun parse(sourceId: Int, path: String, text: String): AstProgram {
        val source = SourceFile(SourceFileId(sourceId), Path.of(path), text.trimIndent(), 1)
        val parsed = Parser(Lexer().lex(source)).parse()
        assertTrue(parsed.diagnostics.isEmpty(), parsed.diagnostics.joinToString())
        return AstBuilder().build(parsed.syntax)
    }
}
