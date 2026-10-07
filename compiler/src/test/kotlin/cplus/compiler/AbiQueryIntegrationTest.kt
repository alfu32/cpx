package cplus.compiler

import kotlin.test.Test
import kotlin.test.assertTrue

class AbiQueryIntegrationTest {
    @Test
    fun lowersAlignofAndOffsetofToCompilerOwnedCQueries() {
        val result = CPlusCompiler().compileText(
            java.nio.file.Path.of("abi_queries.cp"),
            """
            struct Record { char tag; int value; };
            size_t main() { return alignof(Record) + offsetof(Record, value) + layoutof(Record); }
            """.trimIndent()
        )

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val generated = result.generatedUnits.single().text
        assertTrue("#include <stddef.h>" in generated)
        assertTrue("_Alignof(struct Record)" in generated)
        assertTrue("offsetof(struct Record, value)" in generated)
    }
}
