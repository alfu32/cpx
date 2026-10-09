package cplus.core

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TestFixtureParserTest {
    @Test
    fun fixtureRetainsNormalizedDescriptionBodyAndSourceOrigins() {
        val text = """
            test /* pre */ generated box (supports spaces): value works! /* brace { is trivia */ {
                const char* marker = "}";
                int value = 42;
                value += 1;
            }
            int after_fixture() { return 0; }
        """.trimIndent()
        val source = SourceFile(SourceFileId(91), Path.of("fixture.cp"), text, 1)
        val parsed = Parser(Lexer().lex(source)).parse()
        val syntax = parsed.syntax.declarations.filterIsInstance<SyntaxTestFixture>().single()
        val ast = AstBuilder().build(parsed.syntax).declarations.filterIsInstance<AstTestFixture>().single()

        assertTrue(parsed.diagnostics.isEmpty(), parsed.diagnostics.joinToString())
        assertEquals("generated box (supports spaces): value works!", syntax.description)
        assertEquals(syntax.descriptionRange, syntax.descriptionOrigin.primaryRange)
        assertEquals(syntax.description, ast.description)
        assertEquals(syntax.descriptionOrigin, ast.descriptionOrigin)
        assertEquals(3, ast.body.statements.size)
        assertIs<AstVariableDeclaration>(ast.body.statements[0])
        assertIs<AstVariableDeclaration>(ast.body.statements[1])
        assertIs<AstExpressionStatement>(ast.body.statements[2])
        assertTrue(ast.origin.primaryRange?.startOffset == text.indexOf("test"))
        assertEquals("after_fixture", parsed.syntax.declarations.filterIsInstance<SyntaxFunction>().single().name)
    }

    @Test
    fun duplicateDescriptionsAndTestIdentifierUsesRemainValid() {
        val source = SourceFile(
            SourceFileId(92),
            Path.of("fixture-duplicates.cp"),
            "test same description {}\ntest same description {}\nint test; int main() { test = 1; return test; }",
            1
        )
        val parsed = Parser(Lexer().lex(source)).parse()

        assertTrue(parsed.diagnostics.isEmpty(), parsed.diagnostics.joinToString())
        assertEquals(listOf("same description", "same description"),
            parsed.syntax.declarations.filterIsInstance<SyntaxTestFixture>().map { it.description })
        assertEquals("test", parsed.syntax.declarations.filterIsInstance<SyntaxGlobalVariable>().single().name)
    }

    @Test
    fun fixtureLineEndingsAreAcceptedAndDescriptionWhitespaceIsNormalized() {
        listOf("\n", "\r\n").forEachIndexed { index, newline ->
            val text = "test   whitespace\t is   normalized { }" + newline + "test second {}"
            val source = SourceFile(SourceFileId(93 + index), Path.of("line-ending.cp"), text, 1)
            val parsed = Parser(Lexer().lex(source)).parse()

            assertTrue(parsed.diagnostics.isEmpty(), parsed.diagnostics.joinToString())
            assertEquals(
                listOf("whitespace is normalized", "second"),
                parsed.syntax.declarations.filterIsInstance<SyntaxTestFixture>().map { it.description }
            )
        }
    }

    @Test
    fun invalidFixtureFormsDiagnoseAndRecoverToFollowingDeclarations() {
        val cases = listOf(
            "test {} int after_empty() { return 0; }" to "PARSE530",
            "test multi\nline {} int after_multiline() { return 0; }" to "PARSE531",
            "pub test public fixture {} int after_public() { return 0; }" to "PARSE532",
            "test outer { test inner {} } int after_nested() { return 0; }" to "PARSE533",
            "test missing { int local;" to "PARSE534"
        )

        cases.forEachIndexed { index, (text, code) ->
            val source = SourceFile(SourceFileId(95 + index), Path.of("invalid-fixture.cp"), text, 1)
            val parsed = Parser(Lexer().lex(source)).parse()

            assertTrue(parsed.diagnostics.any { it.code == code }, "$code expected: ${parsed.diagnostics}")
            if ("after_" in text) {
                assertTrue(parsed.syntax.declarations.filterIsInstance<SyntaxFunction>().any { it.name.startsWith("after_") },
                    "parser should continue after fixture error: ${parsed.syntax.declarations}")
            }
        }
    }

    @Test
    fun cStyleFunctionUsingTestAsItsReturnTypeIsNotReclassifiedAsFixture() {
        val source = SourceFile(
            SourceFileId(101),
            Path.of("test-identifier.cp"),
            "typedef int test; test value() { return 1; }",
            1
        )
        val parsed = Parser(Lexer().lex(source)).parse()

        assertTrue(parsed.diagnostics.isEmpty(), parsed.diagnostics.joinToString())
        assertTrue(parsed.syntax.declarations.filterIsInstance<SyntaxTestFixture>().isEmpty())
        assertEquals("value", parsed.syntax.declarations.filterIsInstance<SyntaxFunction>().single().name)
        assertIs<SyntaxAlias>(parsed.syntax.declarations.first())
    }

    @Test
    fun pointerReturningFunctionUsingTestAsItsReturnTypeIsNotReclassifiedAsFixture() {
        val source = SourceFile(
            SourceFileId(102),
            Path.of("test-pointer-return.cp"),
            "typedef int test; test *value() { return 0; }",
            1
        )
        val parsed = Parser(Lexer().lex(source)).parse()

        assertTrue(parsed.diagnostics.isEmpty(), parsed.diagnostics.joinToString())
        assertTrue(parsed.syntax.declarations.filterIsInstance<SyntaxTestFixture>().isEmpty())
        assertEquals("value", parsed.syntax.declarations.filterIsInstance<SyntaxFunction>().single().name)
    }
}
