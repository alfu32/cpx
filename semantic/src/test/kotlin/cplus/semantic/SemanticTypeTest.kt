package cplus.semantic

import cplus.core.AstBuilder
import cplus.core.Lexer
import cplus.core.Parser
import cplus.core.SourceFile
import cplus.core.SourceFileId
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SemanticTypeTest {
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
