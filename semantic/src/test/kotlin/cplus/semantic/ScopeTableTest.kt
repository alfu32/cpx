package cplus.semantic

import cplus.core.AstBuilder
import cplus.core.Lexer
import cplus.core.Parser
import cplus.core.SourceFile
import cplus.core.SourceFileId
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ScopeTableTest {
    @Test
    fun lookupWalksParentsAndPrefersNearestShadowingBinding() {
        val scopes = ScopeTable()
        val packageScope = scopes.create(ScopeKind.PACKAGE)
        val functionScope = scopes.create(ScopeKind.FUNCTION, packageScope)
        val blockScope = scopes.create(ScopeKind.BLOCK, functionScope)

        scopes.define(packageScope, "value", SymbolId(1))
        scopes.define(packageScope, "packageOnly", SymbolId(4))
        scopes.define(functionScope, "value", SymbolId(2))
        scopes.define(blockScope, "local", SymbolId(3))

        assertEquals(listOf(SymbolId(2)), scopes.lookup(blockScope, "value"))
        assertEquals(listOf(SymbolId(3)), scopes.lookup(blockScope, "local"))
        assertEquals(listOf(SymbolId(4)), scopes.lookup(blockScope, "packageOnly"))
    }

    @Test
    fun semanticAnalysisBuildsModuleFunctionAndBlockScopes() {
        val source = SourceFile(
            SourceFileId(50),
            Path.of("scopes.cp"),
            "int main(int argument) { int outer = argument; { int inner = outer; return inner; } }",
            1
        )
        val parsed = Parser(Lexer().lex(source)).parse()
        val result = SemanticAnalyzer().analyze(AstBuilder().build(parsed.syntax))

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val model = assertNotNull(result.model)
        val kinds = model.scopes.all.map { it.kind }.toSet()
        assertTrue(ScopeKind.PACKAGE in kinds)
        assertTrue(ScopeKind.MODULE in kinds)
        assertTrue(ScopeKind.FUNCTION in kinds)
        assertTrue(ScopeKind.BLOCK in kinds)
        val innerScope = model.scopes.all.first { scope ->
            scope.kind == ScopeKind.BLOCK && model.scopes.lookup(scope.id, "inner").isNotEmpty()
        }
        assertTrue(model.scopes.lookup(innerScope.id, "outer").isNotEmpty())
        assertTrue(model.scopes.lookup(innerScope.id, "argument").isNotEmpty())
    }

    @Test
    fun declarationCatalogueRegistersForwardRuntimeAndCompileTimeEntities() {
        val source = SourceFile(
            SourceFileId(51),
            Path.of("catalogue.cp"),
            "comptime cpx<decl> make(type T) { return { struct generated_{T} { T value; }; }; } make(int); int main() { return use(); } int use() { return 0; }",
            1
        )
        val parsed = Parser(Lexer().lex(source)).parse()
        val result = SemanticAnalyzer().analyze(AstBuilder().build(parsed.syntax))
        val model = assertNotNull(result.model)

        assertTrue(model.declarationCatalogue.contains("use"))
        assertEquals("function", model.declarationCatalogue.named("use").single().kind)
        val makeDefinition = model.declarationCatalogue.named("make").first { it.kind == "comptime" }
        assertTrue(makeDefinition.compileTime)
        assertEquals(listOf("T"), makeDefinition.parameters)
    }
}
