package cplus.semantic

import cplus.core.*
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TraitImportVisibilityTest {
    @Test
    fun selectiveAndAliasedModuleImportsActivatePublicExtensionsWithoutValueBindings() {
        val selective = analyze(
            mapOf(
                "types" to "pub struct point_t { int value; };",
                "extensions" to "import {point_t} from types; pub comptime trait point_t { int area(self) { return self.value; } }",
                "client" to "import {point_t as Position} from types; import {area as renamed} from extensions; int main() { Position p; return p.area(); }"
            )
        )

        assertTrue(selective.isSuccessful, selective.diagnostics.joinToString())
        val selectiveModel = assertNotNull(selective.model)
        assertEquals(setOf("extensions", "types"), selectiveModel.extensionModuleImports["client"])
        assertEquals(listOf("area"), selectiveModel.lookupMethods(selectiveModel.structs.getValue("point_t"), "area", "client").map { it.symbol.name })
        assertFalse("area" in selectiveModel.functions, "extension activation must not introduce a free function binding")

        val aliasedModule = analyze(
            mapOf(
                "types" to "pub struct point_t { int value; };",
                "extensions" to "import {point_t} from types; pub comptime trait point_t { int area(self) { return self.value; } }",
                "client" to "import {point_t} from types; import extensions as ext; int main() { point_t p; return p.area(); }"
            )
        )
        assertTrue(aliasedModule.isSuccessful, aliasedModule.diagnostics.joinToString())
        assertEquals(setOf("extensions", "types"), aliasedModule.model!!.extensionModuleImports["client"])
    }

    @Test
    fun privateUnimportedAndTransitivelyImportedExtensionsStayHidden() {
        val hidden = analyze(
            mapOf(
                "types" to "pub struct point_t { int value; };",
                "extensions" to "import {point_t} from types; pub comptime trait point_t { int area(self) { return self.value; } }",
                "client" to "import {point_t} from types; int main() { point_t p; return p.area(); }"
            )
        )
        assertTrue(hidden.diagnostics.any { it.code == "SEM302" || it.code == "SEM304" }, hidden.diagnostics.joinToString())
        assertTrue(hidden.model!!.resolvedMethodCalls.isEmpty())

        val transitive = analyze(
            mapOf(
                "types" to "pub struct point_t { int value; };",
                "extensions" to "import {point_t} from types; pub comptime trait point_t { int area(self) { return self.value; } }",
                "bridge" to "import {area} from extensions; import client;",
                "client" to "import {point_t} from types; import bridge; int main() { point_t p; return p.area(); }"
            )
        )
        assertTrue(transitive.diagnostics.any { it.code == "SEM302" || it.code == "SEM304" }, transitive.diagnostics.joinToString())
        assertEquals(setOf("bridge", "types"), transitive.model!!.extensionModuleImports["client"])

        val privateImport = analyze(
            mapOf(
                "types" to "pub struct point_t { int value; };",
                "extensions" to "import {point_t} from types; comptime trait point_t { int secret(self) { return 1; } }",
                "client" to "import {point_t} from types; import {secret} from extensions; int main() { point_t p; return p.secret(); }"
            )
        )
        assertTrue(privateImport.diagnostics.any { it.code == "SEM404" || it.code == "SEM406" }, privateImport.diagnostics.joinToString())
        assertTrue(privateImport.diagnostics.any { it.code == "SEM302" || it.code == "SEM304" }, privateImport.diagnostics.joinToString())
    }

    @Test
    fun importedProviderAmbiguityIsDeterministicAndDoesNotSelectAWinner() {
        fun ambiguous(imports: String): Pair<String, SemanticResult> {
            val result = analyze(
                mapOf(
                    "types" to "pub struct point_t { int value; }; pub typedef point_t point_alias_t;",
                    "first" to "import {point_t} from types; pub comptime trait point_t { int describe(self) { return 1; } }",
                    "second" to "import {point_alias_t} from types; pub comptime trait point_alias_t { int describe(self) { return 2; } }",
                    "client" to "import {point_t} from types; $imports int main() { point_t p; return p.describe(); }"
                )
            )
            val diagnostic = result.diagnostics.single { it.code == "SEM418" }
            return diagnostic.message to result
        }

        val forward = ambiguous("import {describe} from first; import second;")
        val reverse = ambiguous("import second; import {describe} from first;")

        assertEquals(forward.first, reverse.first)
        assertTrue(forward.first.contains("first") && forward.first.contains("second"))
        assertTrue(forward.second.model!!.resolvedMethodCalls.isEmpty())
    }

    @Test
    fun localExtensionCollisionsWithNativeMethodsFieldsAndOtherExtensionsAreDiagnosed() {
        val result = analyze(
            mapOf(
                "local" to """
                    struct point_t {
                        int value;
                        int nativeName(self) { return self.value; }
                    };
                    comptime trait point_t {
                        int nativeName(self) { return self.value; }
                        int value(self) { return self.value; }
                    }
                    comptime trait point_t { int repeated(self) { return 1; } }
                    comptime trait point_t { int repeated(self) { return 2; } }
                """
            )
        )

        assertTrue(result.diagnostics.count { it.code == "SEM416" } >= 2, result.diagnostics.joinToString())
        assertTrue(result.diagnostics.any { it.code == "SEM420" }, result.diagnostics.joinToString())
    }

    @Test
    fun publicExtensionsCannotExposePrivateTypesInTargetOrSignature() {
        val result = analyze(
            mapOf(
                "library" to """
                    struct hidden_t { int value; };
                    pub struct visible_t { int value; };
                    pub comptime trait hidden_t { int reveal(self) { return self.value; } }
                    pub comptime trait visible_t {
                        hidden_t convert(self) { hidden_t result; return result; }
                    }
                """
            )
        )

        assertTrue(result.diagnostics.count { it.code == "SEM421" } >= 2, result.diagnostics.joinToString())
    }

    private fun analyze(sources: Map<String, String>): SemanticResult {
        val modules = sources.map { (moduleName, text) ->
            val source = SourceFile(
                SourceFileId(200 + sources.keys.indexOf(moduleName)),
                Path.of("$moduleName.cp"),
                text.trimIndent(),
                1
            )
            val parsed = Parser(Lexer().lex(source)).parse()
            assertTrue(parsed.diagnostics.isEmpty(), parsed.diagnostics.joinToString())
            moduleName to AstBuilder().build(parsed.syntax)
        }
        val declarations = modules.flatMap { it.second.declarations }
        val program = AstProgram(
            declarations,
            modules.last().second.origin,
            modules.map { (name, parsed) -> AstModule(name, parsed.declarations) }
        )
        return SemanticAnalyzer().analyze(program, knownModules = sources.keys)
    }
}
