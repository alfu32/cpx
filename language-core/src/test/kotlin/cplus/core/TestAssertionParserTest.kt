package cplus.core

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TestAssertionParserTest {
    @Test
    fun parsesFourAssertionFormsAndPreservesOperandSource() {
        val text = """
            test assertion forms {
                assert(value)
                assert("is nonzero", value + 1);
                assertEquals(expected(1, 2), actual)
                assertEquals("same value", expected, actual);
            }
        """.trimIndent()
        val source = SourceFile(SourceFileId(111), Path.of("assertions.cp"), text, 1)
        val parsed = Parser(Lexer().lex(source)).parse()
        val fixture = AstBuilder().build(parsed.syntax).declarations.filterIsInstance<AstTestFixture>().single()
        val assertions = fixture.body.statements.filterIsInstance<AstAssertion>()

        assertTrue(parsed.diagnostics.isEmpty(), parsed.diagnostics.joinToString())
        assertEquals(listOf(AssertionKind.TRUTH, AssertionKind.TRUTH, AssertionKind.EQUALITY, AssertionKind.EQUALITY), assertions.map { it.kind })
        assertEquals(listOf(null, "\"is nonzero\"", null, "\"same value\""), assertions.map { it.description?.let { desc -> (desc as AstStringLiteral).text } })
        assertEquals(listOf("value", "value + 1", "expected(1, 2)", "actual", "expected", "actual"), assertions.flatMap { it.operandSourceText })
        assertEquals(4, assertions.size)
    }

    @Test
    fun allowsOnlyFixtureAssertionTerminatorOmissionAndHandlesMultilineArguments() {
        val source = SourceFile(
            SourceFileId(112),
            Path.of("assertion-terminators.cp"),
            "test terminators { assert(\n  call(1, 2) /* inner comma */\n)\n assertEquals(1, 1) }",
            1
        )
        val parsed = Parser(Lexer().lex(source)).parse()
        val fixture = parsed.syntax.declarations.filterIsInstance<SyntaxTestFixture>().single()

        assertTrue(parsed.diagnostics.isEmpty(), parsed.diagnostics.joinToString())
        assertEquals(2, fixture.body.statements.filterIsInstance<SyntaxAssertion>().size)

        val sameLineSource = SourceFile(
            SourceFileId(113),
            Path.of("assertion-same-line.cp"),
            "test same line { assert(1) assert(2); }",
            1
        )
        val sameLine = Parser(Lexer().lex(sameLineSource)).parse()
        assertTrue(sameLine.diagnostics.any { it.code == "PARSE536" }, sameLine.diagnostics.joinToString())
        assertEquals(2, sameLine.syntax.declarations.filterIsInstance<SyntaxTestFixture>().single()
            .body.statements.filterIsInstance<SyntaxAssertion>().size)
    }

    @Test
    fun rejectsWrongArityAndKeepsOrdinaryCallsOutsideFixtureContext() {
        val badSource = SourceFile(
            SourceFileId(114),
            Path.of("assertion-arity.cp"),
            "test invalid { assert(); assertEquals(1); }",
            1
        )
        val bad = Parser(Lexer().lex(badSource)).parse()
        val invalid = bad.syntax.declarations.filterIsInstance<SyntaxTestFixture>().single()
            .body.statements.filterIsInstance<SyntaxAssertion>()
        assertEquals(listOf(AssertionKind.INVALID, AssertionKind.INVALID), invalid.map { it.kind })
        assertEquals(2, bad.diagnostics.count { it.code == "PARSE535" })

        val ordinarySource = SourceFile(
            SourceFileId(115),
            Path.of("ordinary-assert.cp"),
            "int assert(int value); int assertEquals(int left, int right); int main() { assert(1); assertEquals(1, 1); return 0; }",
            1
        )
        val ordinary = Parser(Lexer().lex(ordinarySource)).parse()
        val function = ordinary.syntax.declarations.filterIsInstance<SyntaxFunction>().last()
        val statements = (function.body as SyntaxBlock).statements
        assertTrue(ordinary.diagnostics.isEmpty(), ordinary.diagnostics.joinToString())
        assertTrue(statements.dropLast(1).all { it is SyntaxExpressionStatement })
        assertIs<SyntaxCall>((statements.first() as SyntaxExpressionStatement).expression)
    }

    @Test
    fun assertionsInsideInnerFunctionsAreNotFixtureAssertions() {
        val source = SourceFile(
            SourceFileId(116),
            Path.of("assertion-inner-function.cp"),
            "test nested function { int helper() { assert(1); return 0; } assert(1); }",
            1
        )
        val parsed = Parser(Lexer().lex(source)).parse()
        val fixture = parsed.syntax.declarations.filterIsInstance<SyntaxTestFixture>().single()
        val inner = fixture.body.statements.filterIsInstance<SyntaxInnerFunction>().single()
        val helperBody = inner.body as SyntaxBlock

        assertTrue(parsed.diagnostics.isEmpty(), parsed.diagnostics.joinToString())
        assertIs<SyntaxExpressionStatement>(helperBody.statements.first())
        assertIs<SyntaxReturn>(helperBody.statements.last())
        assertEquals(1, fixture.body.statements.filterIsInstance<SyntaxAssertion>().size)
    }
}
