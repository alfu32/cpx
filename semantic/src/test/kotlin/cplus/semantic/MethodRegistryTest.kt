package cplus.semantic

import cplus.core.*
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MethodRegistryTest {
    @Test
    fun receiverIdentityCanonicalizesAliasesButSeparatesNominalTypes() {
        val first = StructType(TypeId(101), "Point", emptyList(), moduleName = "geometry")
        val sameSpellingFromAnotherModule = StructType(TypeId(102), "Point", emptyList(), moduleName = "drawing")
        val alias = AliasType(TypeId(103), "PointAlias", first)
        val pointerAlias = PointerType(TypeId(104), alias)

        assertEquals(ReceiverIdentity.of(first), ReceiverIdentity.of(alias))
        assertEquals(ReceiverIdentity.of(first), ReceiverIdentity.of(pointerAlias))
        assertNotEquals(first.moduleName, sameSpellingFromAnotherModule.moduleName)
        assertNotEquals(ReceiverIdentity.of(first), ReceiverIdentity.of(sameSpellingFromAnotherModule))
    }

    @Test
    fun methodRegistryDetectsNativeDuplicatesAndFiltersExtensionsByDefiningModule() {
        val origin = Origin.Direct(SourceRange(SourceFileId(91), 0, 1))
        val owner = StructType(TypeId(201), "Point", emptyList())
        val native = method(owner, "length", "geometry", origin)
        val registry = MethodRegistry.Builder()

        assertTrue(registry.add(native))
        assertFalse(registry.add(native.copy(symbol = native.symbol.copy(id = SymbolId(92)))))
        assertTrue(registry.add(native.copy(
            symbol = native.symbol.copy(id = SymbolId(93)),
            definingModule = "geometry_ext",
            isExtension = true
        )))
        assertTrue(registry.add(native.copy(
            symbol = native.symbol.copy(id = SymbolId(94)),
            definingModule = "drawing_ext",
            isExtension = true
        )))

        val built = registry.build()
        assertEquals(listOf("geometry", "geometry_ext"), built.lookup(native.receiverIdentity, "length", setOf("geometry_ext")).map { it.definingModule })
        assertEquals(listOf("geometry", "drawing_ext"), built.lookup(native.receiverIdentity, "length", setOf("drawing_ext")).map { it.definingModule })
        assertEquals(listOf(native), built.lookup(native.receiverIdentity, "length"))
        assertTrue(owner.methods.isEmpty(), "registering extensions must not mutate the target type")
    }

    @Test
    fun nativeMethodKeepsItsOwnerAndDefiningModuleAndDuplicateIsDiagnosed() {
        val source = SourceFile(
            SourceFileId(95),
            Path.of("geometry.cp"),
            "struct Point { int length(self) { return 1; } };",
            1
        )
        val syntax = Parser(Lexer().lex(source)).parse()
        val ast = AstBuilder().build(syntax.syntax)
        val module = AstModule("geometry", ast.declarations)
        val model = assertNotNull(SemanticAnalyzer().analyze(ast.copy(modules = listOf(module))).model)
        val nativeMethod = model.methods.getValue("Point").getValue("length")

        assertSame(model.structs.getValue("Point"), nativeMethod.owner)
        assertEquals("geometry", (nativeMethod.owner as StructType).moduleName)
        assertEquals("geometry", nativeMethod.definingModule)
        assertEquals(ReceiverIdentity.of(nativeMethod.owner), nativeMethod.receiverIdentity)
        assertEquals(listOf(nativeMethod), model.lookupMethods(nativeMethod.owner, "length"))

        val duplicate = SourceFile(
            SourceFileId(96),
            Path.of("duplicate.cp"),
            "struct Point { int length(self) { return 1; } int length(self) { return 2; } };",
            1
        )
        val duplicateResult = SemanticAnalyzer().analyze(
            AstBuilder().build(Parser(Lexer().lex(duplicate)).parse().syntax)
        )
        assertTrue(duplicateResult.diagnostics.any { it.code == "SEM416" }, duplicateResult.diagnostics.joinToString())
    }

    private fun method(owner: CType, name: String, module: String, origin: Origin): MethodSymbol {
        val functionType = FunctionType(TypeId(300), PrimitiveType(TypeId(301), "int"), emptyList())
        val symbol = Symbol(SymbolId(302), name, SymbolKind.METHOD, functionType, origin, moduleName = module)
        return MethodSymbol(
            symbol,
            owner,
            ReceiverIdentity.of(owner),
            module,
            ReceiverKind.INSTANCE,
            functionType.returnType,
            emptyList(),
            functionType,
            owner
        )
    }
}
