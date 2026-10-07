package cplus.semantic

import cplus.core.AstBuilder
import cplus.core.Lexer
import cplus.core.Parser
import cplus.core.SourceFile
import cplus.core.SourceFileId
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SemanticTypeTest {
    @Test
    fun cSourceSymbolsExposeForeignToolingInformation() {
        val cText = "int helper_value(void) { return 12; }"
        val cSource = SourceFile(SourceFileId(18), Path.of("helper.c"), cText, 1)
        val cplusText = "int main() { return helper_value(); }"
        val source = SourceFile(SourceFileId(19), Path.of("main.cp"), cplusText, 1)
        val parsed = Parser(Lexer().lex(source)).parse()

        val result = SemanticAnalyzer().analyze(
            AstBuilder().build(parsed.syntax),
            foreignSources = listOf(CSourceUnit(cSource, "c.source.helper"))
        )

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val model = result.model!!
        val helper = model.foreignFunctions.getValue("helper_value")
        assertEquals(SymbolKind.FOREIGN, helper.symbol.kind)
        assertEquals("int", model.functionSignature("helper_value")!!.returnType.name)
        assertEquals(helper.symbol, model.lookup("helper_value"))
        assertEquals(cSource.id, helper.symbol.origin.primaryRange!!.file)
        assertEquals(cText.indexOf("int helper_value"), helper.symbol.origin.primaryRange!!.startOffset)
    }

    @Test
    fun unsupportedHeaderPreprocessorContentRemainsDiagnostic() {
        val service = CHeaderImportService(mapOf("c.test" to "#define MAGIC 1\n"))
        val text = "import { MAGIC } from c.test; int main() { return 0; }"
        val source = SourceFile(SourceFileId(7), Path.of("header-boundary.cp"), text, 1)
        val parsed = Parser(Lexer().lex(source)).parse()

        val result = SemanticAnalyzer(service).analyze(AstBuilder().build(parsed.syntax))
        assertTrue(result.diagnostics.any { it.code == "SEM409" }, result.diagnostics.joinToString())
        assertTrue(!result.isSuccessful)
    }

    @Test
    fun packageAndModuleNamesFormDeterministicQualifiedSymbols() {
        val text = "package demo.core; pub int main() { return 0; }"
        val source = SourceFile(SourceFileId(6), Path.of("package-symbols.cp"), text, 1)
        val parsed = Parser(Lexer().lex(source)).parse()
        assertTrue(parsed.diagnostics.isEmpty(), parsed.diagnostics.joinToString())

        val result = SemanticAnalyzer().analyze(AstBuilder().build(parsed.syntax))
        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val model = result.model!!
        assertEquals("demo.core", model.modulePackages["<main>"])
        assertEquals(setOf("<main>"), model.packageModules["demo.core"])
        assertEquals("demo.core::<main>::main", model.functions.getValue("main").symbol.qualifiedName.value)
        assertEquals(Visibility.PUBLIC, model.functions.getValue("main").symbol.visibility)
    }

    @Test
    fun aliasesResolveAcrossSourceOrder() {
        val text = """
            count_t value;
            typedef int count_t;

            int main() {
                return value;
            }
        """.trimIndent()
        val source = SourceFile(SourceFileId(3), Path.of("forward-alias.cp"), text, 1)
        val parsed = Parser(Lexer().lex(source)).parse()
        assertTrue(parsed.diagnostics.isEmpty(), parsed.diagnostics.joinToString())

        val result = SemanticAnalyzer().analyze(AstBuilder().build(parsed.syntax))
        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        assertEquals("count_t", result.model!!.symbols.first { it.name == "value" }.type.name)
    }

    @Test
    fun cyclicAliasesProduceAStableDiagnostic() {
        val text = "typedef b_t a_t; typedef a_t b_t; int main() { return 0; }"
        val source = SourceFile(SourceFileId(4), Path.of("cyclic-alias.cp"), text, 1)
        val parsed = Parser(Lexer().lex(source)).parse()

        val result = SemanticAnalyzer().analyze(AstBuilder().build(parsed.syntax))
        assertTrue(result.diagnostics.any { it.code == "SEM110" }, result.diagnostics.joinToString())
        assertTrue(!result.isSuccessful)
    }

    @Test
    fun callResolutionChecksArgumentTypes() {
        val text = """
            int identity(int value) {
                return value;
            }

            int main() {
                return identity("wrong");
            }
        """.trimIndent()
        val source = SourceFile(SourceFileId(5), Path.of("call-types.cp"), text, 1)
        val parsed = Parser(Lexer().lex(source)).parse()

        val result = SemanticAnalyzer().analyze(AstBuilder().build(parsed.syntax))
        assertTrue(result.diagnostics.any { it.code == "SEM306" }, result.diagnostics.joinToString())
        assertTrue(!result.isSuccessful)
    }

    @Test
    fun nonAssignableExpressionProducesAssignmentDiagnostic() {
        val text = "int main() { return 1 = 2; }"
        val source = SourceFile(SourceFileId(12), Path.of("invalid-assignment.cp"), text, 1)
        val parsed = Parser(Lexer().lex(source)).parse()

        val result = SemanticAnalyzer().analyze(AstBuilder().build(parsed.syntax))

        assertTrue(result.diagnostics.any { it.code == "SEM307" }, result.diagnostics.joinToString())
        assertTrue(!result.isSuccessful)
    }

    @Test
    fun initializerTypesAndPointerArithmeticAreValidated() {
        val text = """
            int invalid_global = "wrong";

            int main() {
                int value = "wrong";
                return value * &value;
            }
        """.trimIndent()
        val source = SourceFile(SourceFileId(13), Path.of("initializer-pointer-errors.cp"), text, 1)
        val parsed = Parser(Lexer().lex(source)).parse()

        val result = SemanticAnalyzer().analyze(AstBuilder().build(parsed.syntax))

        assertTrue(result.diagnostics.count { it.code == "SEM308" } >= 2, result.diagnostics.joinToString())
        assertTrue(result.diagnostics.any { it.code == "SEM316" }, result.diagnostics.joinToString())
        assertTrue(!result.isSuccessful)
    }

    @Test
    fun equivalentAliasAndPointerSpellingSharesCanonicalTypeIdentity() {
        val text = """
            typedef int count_t;
            int* pointer_a;
            count_t* pointer_b;

            int one(int value) {
                return value;
            }

            count_t two(count_t value) {
                return value;
            }

            int main() {
                return one(two(3));
            }
        """.trimIndent()
        val source = SourceFile(SourceFileId(2), Path.of("canonical-types.cp"), text, 1)
        val parsed = Parser(Lexer().lex(source)).parse()
        assertTrue(parsed.diagnostics.isEmpty(), parsed.diagnostics.joinToString())

        val result = SemanticAnalyzer().analyze(AstBuilder().build(parsed.syntax))
        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val model = result.model!!
        val pointerA = model.symbols.first { it.name == "pointer_a" }.type
        val pointerB = model.symbols.first { it.name == "pointer_b" }.type
        assertEquals(model.canonicalTypeId(pointerA), model.canonicalTypeId(pointerB))
        assertEquals(
            model.canonicalTypeId(model.functions.getValue("one").signature),
            model.canonicalTypeId(model.functions.getValue("two").signature)
        )
    }

    @Test
    fun primitiveCanonicalIdentityPreservesSignednessAndIntegerRank() {
        val text = """
            char plain_char;
            signed char signed_char;
            unsigned char unsigned_char;
            short signed_short;
            unsigned short unsigned_short;
            int signed_int;
            unsigned int unsigned_int;
            long signed_long;
            unsigned long unsigned_long;
            long long signed_long_long;
            unsigned long long unsigned_long_long;
            long first(long int value) { return value; }
            long second(int long value) { return value; }
            long third(long long value) { return value; }
            int main() { return 0; }
        """.trimIndent()
        val source = SourceFile(SourceFileId(23), Path.of("primitive-identities.cp"), text, 1)
        val parsed = Parser(Lexer().lex(source)).parse()
        assertTrue(parsed.diagnostics.isEmpty(), parsed.diagnostics.joinToString())

        val result = SemanticAnalyzer().analyze(AstBuilder().build(parsed.syntax))

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val model = result.model!!
        fun canonicalId(name: String) = model.canonicalTypeId(model.symbols.first { it.name == name }.type)

        assertNotEquals(canonicalId("plain_char"), canonicalId("signed_char"))
        assertNotEquals(canonicalId("signed_char"), canonicalId("unsigned_char"))
        assertNotEquals(canonicalId("signed_short"), canonicalId("unsigned_short"))
        assertNotEquals(canonicalId("signed_short"), canonicalId("signed_int"))
        assertNotEquals(canonicalId("signed_int"), canonicalId("unsigned_int"))
        assertNotEquals(canonicalId("signed_int"), canonicalId("signed_long"))
        assertNotEquals(canonicalId("signed_long"), canonicalId("unsigned_long"))
        assertNotEquals(canonicalId("signed_long"), canonicalId("signed_long_long"))
        assertNotEquals(canonicalId("unsigned_long"), canonicalId("unsigned_long_long"))
        assertEquals(
            canonicalId("signed_int"),
            model.canonicalTypeId(PrimitiveType(TypeId(-100), "signed int"))
        )
        assertEquals(
            canonicalId("signed_short"),
            model.canonicalTypeId(PrimitiveType(TypeId(-101), "signed short"))
        )
        assertEquals(
            model.canonicalTypeId(model.functions.getValue("first").signature),
            model.canonicalTypeId(model.functions.getValue("second").signature)
        )
        assertNotEquals(
            model.canonicalTypeId(model.functions.getValue("first").signature),
            model.canonicalTypeId(model.functions.getValue("third").signature)
        )
    }

    @Test
    fun pointerCompatibilityDoesNotCollapseDistinctIntegerTypes() {
        val text = """
            int takes_signed_char(signed char* value) { return 0; }
            int takes_long(long* value) { return 0; }
            int main() {
                char plain;
                long long wide;
                takes_signed_char(&plain);
                takes_long(&wide);
                return 0;
            }
        """.trimIndent()
        val source = SourceFile(SourceFileId(24), Path.of("primitive-pointer-compatibility.cp"), text, 1)
        val parsed = Parser(Lexer().lex(source)).parse()
        assertTrue(parsed.diagnostics.isEmpty(), parsed.diagnostics.joinToString())

        val result = SemanticAnalyzer().analyze(AstBuilder().build(parsed.syntax))

        assertEquals(2, result.diagnostics.count { it.code == "SEM306" }, result.diagnostics.joinToString())
    }

    @Test
    fun comptimeTypeIdentityRetainsAliasAndCanonicalIds() {
        val text = """
            typedef int count_t;
            struct item { int value; };
            count_t count;
            struct item item_value;
            int main() { return count + item_value.value; }
        """.trimIndent()
        val source = SourceFile(SourceFileId(22), Path.of("comptime-type-identity.cp"), text, 1)
        val parsed = Parser(Lexer().lex(source)).parse()

        val result = SemanticAnalyzer().analyze(AstBuilder().build(parsed.syntax))

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val model = result.model!!
        val alias = model.resolveComptimeTypeIdentity("count_t")!!
        val primitive = model.resolveComptimeTypeIdentity("int")!!
        val structure = model.resolveComptimeTypeIdentity("struct item")!!
        assertNotEquals(alias.typeId, alias.canonicalTypeId)
        assertEquals(primitive.canonicalTypeId, alias.canonicalTypeId)
        assertEquals("int", alias.canonicalText)
        assertEquals(structure.typeId, structure.canonicalTypeId)
        assertEquals("item", structure.canonicalText)
    }

    @Test
    fun functionDeclarationsProduceCanonicalFunctionTypes() {
        val text = """
            int add(int left, int right) {
                return left + right;
            }

            int main() {
                return add(1, 2);
            }
        """.trimIndent()
        val source = SourceFile(SourceFileId(1), Path.of("function-types.cp"), text, 1)
        val parsed = Parser(Lexer().lex(source)).parse()
        assertTrue(parsed.diagnostics.isEmpty(), parsed.diagnostics.joinToString())

        val result = SemanticAnalyzer().analyze(AstBuilder().build(parsed.syntax))
        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val add = result.model!!.functions.getValue("add")
        val signature = assertIs<FunctionType>(add.symbol.type)
        assertEquals(listOf("int", "int"), signature.parameterTypes.map { it.name })
        assertEquals("int", signature.returnType.name)
        assertEquals(signature, add.signature)
    }
}
