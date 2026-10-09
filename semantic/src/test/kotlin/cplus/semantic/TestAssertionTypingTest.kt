package cplus.semantic

import cplus.core.*
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TestAssertionTypingTest {
    @Test
    fun typesAllAssertionFormsAndPreservesOriginalOperandTypes() {
        val result = analyze("""
            typedef unsigned long long count_t;
            enum State { Ready };
            test supported assertion types {
                count_t count = 1;
                long long signedCount = 2;
                State state = Ready;
                double floating = 1.0;
                double _Complex complexValue;
                int* first;
                int* second;
                char* message;
                assert(count);
                assert("count", count);
                assertEquals(signedCount, 2);
                assertEquals(state, Ready);
                assertEquals(floating, 1.0);
                assertEquals(complexValue, complexValue);
                assertEquals(first, second);
                assertEquals(first, 0);
                assertEquals(first, (0));
                assertEquals("pointer string", message, message);
            }
            int compare(int* pointer) { return pointer == 0; }
        """)

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val model = assertNotNull(result.model)
        assertEquals(10, model.typedTestAssertions.size)
        val typed = model.typedTestAssertions.values.sortedBy { it.assertion.origin.primaryRange?.startOffset }
        assertTrue(typed.all { it.comparisonType == null || it.comparisonType.name == "bool" })
        assertEquals("count_t", typed.first().operandTypes.single().name)
        assertEquals("long long", typed[2].operandTypes.first().name)
        assertEquals("State", typed[3].operandTypes.first().name)
        assertEquals("double _Complex", typed[5].operandTypes.first().name)
        assertIs<PointerType>(typed.last().descriptionType)
    }

    @Test
    fun rejectsNonScalarConditionsBadDescriptionsAndInvalidEqualityTypes() {
        val result = analyze("""
            struct Aggregate { int value; };
            void noValue() { return; }
            test invalid assertion types {
                Aggregate aggregate;
                int* pointer;
                assert(aggregate);
                assert(noValue());
                assert(1, 2);
                assertEquals(pointer, 1);
                assertEquals(aggregate, aggregate);
            }
        """)

        assertTrue(result.diagnostics.any { it.code == "SEM530" && it.message.contains("description") }, result.diagnostics.joinToString())
        assertEquals(2, result.diagnostics.count { it.code == "SEM531" }, result.diagnostics.joinToString())
        assertEquals(2, result.diagnostics.count { it.code == "SEM316" }, result.diagnostics.joinToString())
        assertTrue(result.diagnostics.filter { it.code in setOf("SEM530", "SEM531", "SEM316") }.all { it.range != null })
        assertEquals(5, result.model?.typedTestAssertions?.size)
    }

    private fun analyze(text: String): SemanticResult {
        val source = SourceFile(SourceFileId(131), Path.of("assertion-typing.cp"), text.trimIndent(), 1)
        val parsed = Parser(Lexer().lex(source)).parse()
        assertTrue(parsed.diagnostics.isEmpty(), parsed.diagnostics.joinToString())
        return SemanticAnalyzer().analyze(AstBuilder().build(parsed.syntax))
    }
}
