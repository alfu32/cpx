package cplus.semantic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CHeaderFunctionScannerTest {
    @Test
    fun recordsForwardTagsTypedefChainsAndAggregateFieldTypes() {
        val declarations = CHeaderImportService().sourceDeclarations(
            """
            typedef unsigned long size_base;
            typedef size_base size_alias;
            typedef int (*callback_t)(const char *text, long count);
            struct Node;
            struct Node {
                struct Node *next;
                unsigned int value;
            };
            typedef struct Node Node;
            typedef union {
                int integer;
                double real;
            } Number;
            enum Color { RED = 1, GREEN = RED + 1 };
            """.trimIndent()
        )

        assertEquals("unsigned long", declarations.getValue("size_base").typeName)
        assertEquals("size_base", declarations.getValue("size_alias").typeName)
        assertEquals(
            CFunctionPointerType("int", listOf("const char*", "long"), false),
            declarations.getValue("callback_t").functionPointerType
        )
        val node = declarations.getValue("Node")
        assertEquals("struct Node", node.typeName)
        assertEquals(listOf(CHeaderField("next", "struct Node*"), CHeaderField("value", "unsigned int")), node.fields)
        assertEquals("union Number", declarations.getValue("Number").typeName)
        assertEquals(ForeignDeclarationKind.ENUM_VALUE, declarations.getValue("RED").kind)
        assertEquals(ForeignDeclarationKind.ENUM_VALUE, declarations.getValue("GREEN").kind)
        assertEquals("RED+1", declarations.getValue("GREEN").constantExpression)
    }

    @Test
    fun marksCyclicTypedefDependenciesUnsupported() {
        val declarations = CHeaderImportService().sourceDeclarations(
            "typedef second_t first_t; typedef first_t second_t;"
        )

        assertTrue(declarations.getValue("first_t").unsupportedReason?.contains("cyclic") == true)
        assertTrue(declarations.getValue("second_t").unsupportedReason?.contains("cyclic") == true)
    }

    @Test
    fun refusesToGuessCompilerSpecificBitFieldLayout() {
        val declaration = CHeaderImportService().sourceDeclarations(
            "struct Flags { unsigned int ready:1; };"
        ).getValue("Flags")

        assertTrue(declaration.unsupportedReason?.contains("bit-field") == true)
    }

    @Test
    fun exposesOnlySafeObjectLikeMacroConstantsWithInferredTypes() {
        val source = java.nio.file.Path.of("constants.h").toAbsolutePath().normalize()
        val declarations = CHeaderImportService().sourceDeclarations(
            "",
            listOf(
                CHeaderMacro("ANSWER", null, "42", source, 4),
                CHeaderMacro("LIMIT", null, "8ULL", source, 5),
                CHeaderMacro("RATE", null, "1.5f", source, 6),
                CHeaderMacro("TITLE", null, "\"hello\"", source, 7),
                CHeaderMacro("EXPRESSION", null, "2 + 3", source, 8),
                CHeaderMacro("FUNCTION_LIKE", "value", "(value + 1)", source, 9),
                CHeaderMacro("ZERO_ARGUMENT_FUNCTION", "", "7", source, 10)
            )
        )

        assertEquals("int", declarations.getValue("ANSWER").typeName)
        assertEquals("unsigned long long", declarations.getValue("LIMIT").typeName)
        assertEquals("float", declarations.getValue("RATE").typeName)
        assertEquals("char*", declarations.getValue("TITLE").typeName)
        assertEquals("\"hello\"", declarations.getValue("TITLE").constantExpression)
        assertEquals(source, declarations.getValue("TITLE").externalSource)
        assertEquals(7, declarations.getValue("TITLE").externalLine)
        assertTrue("EXPRESSION" !in declarations)
        assertTrue("FUNCTION_LIKE" !in declarations)
        assertTrue("ZERO_ARGUMENT_FUNCTION" !in declarations)
    }

    @Test
    fun indexesExternalGlobalsWithoutExportingStaticOrFunctionLocalVariables() {
        val declarations = CHeaderImportService().sourceDeclarations(
            """
            extern const char *label;
            extern int *global_pointer;
            int global_count = 3;
            static int private_header_state;
            int helper(void) {
                int function_local = 7;
                return function_local;
            }
            """.trimIndent()
        )

        assertTrue("label" in declarations, "declarations were ${declarations.keys}")
        assertEquals("const char*", declarations.getValue("label").typeName)
        assertEquals("int*", declarations.getValue("global_pointer").typeName)
        assertEquals("int", declarations.getValue("global_count").typeName)
        assertTrue("private_header_state" !in declarations)
        assertTrue("function_local" !in declarations)
    }

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
            #include <stddef.h>
            /* int comment_only(int x); */
            const char *message = "int string_only(int x);";
            int real_function(int value);
        """.trimIndent()

        val declarations = CHeaderImportService().sourceDeclarations(source)

        assertEquals(setOf("real_function", "message"), declarations.keys)
    }
}
