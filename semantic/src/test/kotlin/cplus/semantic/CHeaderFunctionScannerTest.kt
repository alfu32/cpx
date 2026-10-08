package cplus.semantic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CHeaderFunctionScannerTest {
    @Test
    fun parsesMultilineMultiwordAndNestedCallbackDeclarators() {
        val declarations = CHeaderImportService().sourceDeclarations(
            """
            extern unsigned long long
            calculate(const unsigned char *bytes, size_t length,
                      int (*visitor)(const char *, long long), ...);
            int consume(void);
            """.trimIndent()
        )

        val calculate = declarations.getValue("calculate")
        assertEquals("unsigned long long", calculate.typeName)
        assertEquals(
            listOf("const unsigned char*", "size_t", "int(*)(const char*, long long)"),
            calculate.parameterTypes
        )
        assertTrue(calculate.isVariadic)
        assertEquals("int", declarations.getValue("consume").typeName)
        assertEquals(emptyList(), declarations.getValue("consume").parameterTypes)
    }

    @Test
    fun skipsFunctionBodiesAndNestedBracesWithoutInventingDeclarations() {
        val source = """
            static inline int helper(const char *text) {
                const char *brace = "}";
                /* { ignored } */
                if (text) { int local_only(int x) { return x; } }
                return 1;
            }
            int after_body(int value);
        """.trimIndent()

        val declarations = CHeaderImportService().sourceDeclarations(source)

        assertEquals(setOf("helper", "after_body"), declarations.keys)
        val helperRange = assertNotNull(declarations.getValue("helper").sourceRange)
        assertEquals(source.indexOf("static inline"), helperRange.first)
        assertTrue(source.substring(helperRange.first, helperRange.last + 1).contains("return 1;"))
    }

    @Test
    fun ignoresFunctionLikeTokensInsideCommentsAndStrings() {
        val source = """
            /* int comment_only(int x); */
            const char *message = "int string_only(int x);";
            int real_function(int value);
        """.trimIndent()

        val declarations = CHeaderImportService().sourceDeclarations(source)

        assertEquals(setOf("real_function"), declarations.keys)
    }
}
