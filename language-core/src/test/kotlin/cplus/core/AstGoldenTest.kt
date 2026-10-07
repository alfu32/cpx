package cplus.core

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AstGoldenTest {
    @Test
    fun baselineProgramNormalizesToStableAstShape() {
        val text = "struct point_t { int x; }; int main() { return 0; }"
        val source = SourceFile(SourceFileId(1), Path.of("golden.cp"), text, 1)
        val parsed = Parser(Lexer().lex(source)).parse()
        val ast = AstBuilder().build(parsed.syntax)

        assertTrue(parsed.diagnostics.isEmpty(), parsed.diagnostics.joinToString())
        assertEquals(listOf("point_t", "main"), ast.declarations.map {
            when (it) {
                is AstStruct -> it.name
                is AstPackage -> it.name
                is AstAlias -> it.name
                is AstUnion -> it.name
                is AstEnum -> it.name
                is AstFunction -> it.name
                is AstGlobalVariable -> it.name
                is AstComptimeFunction -> it.name
                is AstCpxInvocation -> it.name
                is AstImport -> it.module
            }
        })
        val structure = ast.declarations.first() as AstStruct
        assertEquals("int", structure.fields.single().type.name)
        assertEquals("x", structure.fields.single().name)
    }

    @Test
    fun astArenaProvidesStableAddressableNodes() {
        val range = SourceRange(SourceFileId(1), 0, 1)
        val first = AstIntegerLiteral("1", Origin.Direct(range))
        val second = AstIntegerLiteral("2", Origin.Direct(range))
        val arena = AstArena()
        val id = arena.add(first)

        assertEquals(1, arena.size)
        assertEquals(first, arena[id])
        assertEquals(first, arena.replace(id, second))
        assertEquals(second, arena[id])
    }

    @Test
    fun malformedFixtureProducesRecoverableDiagnostic() {
        val text = "int main( { return 0; }"
        val source = SourceFile(SourceFileId(2), Path.of("malformed.cp"), text, 1)
        val parsed = Parser(Lexer().lex(source)).parse()

        assertTrue(parsed.diagnostics.any { it.code == "PARSE100" || it.code == "PARSE001" })
    }

    @Test
    fun cpxDeclarationAndInvocationRemainStructuredUntilExpansion() {
        val text = """
            comptime cpx<decl> optional(type T) {
                return {
                    struct optional_{T}_t { T value; };
                };
            }
            optional(int);
        """.trimIndent()
        val source = SourceFile(SourceFileId(3), Path.of("cpx.cp"), text, 1)
        val parsed = Parser(Lexer().lex(source)).parse()

        assertTrue(parsed.diagnostics.isEmpty(), parsed.diagnostics.joinToString())
        val definition = parsed.syntax.declarations[0] as SyntaxComptimeFunction
        val invocation = parsed.syntax.declarations[1] as SyntaxCpxInvocation
        assertEquals("optional", definition.name)
        assertEquals("decl", definition.category)
        assertEquals("T", definition.parameters.single().name)
        assertTrue(definition.template.contains("optional_{T}_t"))
        assertEquals(listOf("int"), invocation.arguments)
    }

    @Test
    fun importsPreserveSelectiveNamesQualifiedModuleAndAlias() {
        val text = "import { add, subtract } from math.arithmetic as arithmetic; int main() { return 0; }"
        val source = SourceFile(SourceFileId(4), Path.of("imports.cp"), text, 1)
        val parsed = Parser(Lexer().lex(source)).parse()

        assertTrue(parsed.diagnostics.isEmpty(), parsed.diagnostics.joinToString())
        val import = parsed.syntax.declarations.first() as SyntaxImport
        assertEquals(listOf("add", "subtract"), import.names)
        assertEquals("math.arithmetic", import.module)
        assertEquals("arithmetic", import.alias)
        val astImport = AstBuilder().build(parsed.syntax).declarations.first() as AstImport
        assertEquals("arithmetic", astImport.alias)
    }

    @Test
    fun packageDeclarationRemainsAStructuredModuleBoundary() {
        val source = SourceFile(SourceFileId(5), Path.of("package.cp"), "package collections.core; int main() { return 0; }", 1)
        val parsed = Parser(Lexer().lex(source)).parse()

        assertTrue(parsed.diagnostics.isEmpty(), parsed.diagnostics.joinToString())
        val packageDeclaration = AstBuilder().build(parsed.syntax).declarations.first() as AstPackage
        assertEquals("collections.core", packageDeclaration.name)
    }

    @Test
    fun expressionParserRetainsCallsMemberAccessAndIndexingAsStructure() {
        val source = SourceFile(
            SourceFileId(6),
            Path.of("expressions.cp"),
            "struct vector { int value; int get(self) { return self.value; } }; int main() { return vector.get()[0]; }",
            1
        )
        val parsed = Parser(Lexer().lex(source)).parse()
        val ast = AstBuilder().build(parsed.syntax)

        assertTrue(parsed.diagnostics.isEmpty(), parsed.diagnostics.joinToString())
        val structure = ast.declarations.filterIsInstance<AstStruct>().single()
        assertEquals("get", structure.methods.single().name)
        val returned = (ast.declarations.filterIsInstance<AstFunction>().single().body as AstBlock)
            .statements.single() as AstReturn
        val indexed = returned.expression as AstIndexAccess
        assertEquals("get", (indexed.receiver as AstCall).callee.let { it as AstMemberAccess }.member)
    }

    @Test
    fun parserRecoversFromIncompleteMemberCallAndContinuesWithLaterDeclarations() {
        val source = SourceFile(
            SourceFileId(7),
            Path.of("recovery.cp"),
            "int main() { return foo.bar( ; } int later() { return 0; }",
            1
        )
        val parsed = Parser(Lexer().lex(source)).parse()

        assertTrue(parsed.diagnostics.any { it.code == "PARSE001" })
        assertEquals(listOf("main", "later"), parsed.syntax.declarations.filterIsInstance<SyntaxFunction>().map { it.name })
    }
}
