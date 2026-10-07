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
