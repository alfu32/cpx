package cplus.compiler

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TestFixtureLoweringTest {
    @Test
    fun normalModeErasesFixturesAndTestModeAddsStableDispatcher() {
        val directory = Files.createTempDirectory("cplus-test-lowering")
        val source = directory.resolve("main.cp")
        Files.writeString(
            source,
            """
                test a fixture { int local = 1; }
                int main() { return 0; }
            """.trimIndent()
        )

        val compiler = CPlusCompiler()
        val normal = compiler.compile(CompileRequest(listOf(source)))
        assertTrue(normal.isSuccessful, normal.diagnostics.joinToString())
        val normalC = normal.generatedUnits.single().text
        assertFalse(normalC.contains("fixture_"))
        assertTrue(normalC.contains("int main("))

        val test = compiler.compile(CompileRequest(listOf(source), mode = CompilationMode.TEST))
        assertTrue(test.isSuccessful, test.diagnostics.joinToString())
        val testC = test.generatedUnits.single().text
        assertTrue(testC.contains("int main("))
        assertTrue(testC.contains("__cplus_test_fixture_"))
        assertTrue(testC.contains("__cplus_user_main_"))
        assertNotNull(test.artifacts.single().lowered?.unit?.testProduct)
    }

    @Test
    fun testModeWithoutFixturesDoesNotInventDispatcher() {
        val directory = Files.createTempDirectory("cplus-empty-test-lowering")
        val source = directory.resolve("main.cp")
        Files.writeString(source, "int main() { return 0; }")

        val result = CPlusCompiler().compile(CompileRequest(listOf(source), mode = CompilationMode.TEST))
        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val generated = result.generatedUnits.single().text
        assertTrue(generated.contains("int main("))
        assertFalse(generated.contains("__cplus_test_fixture_"))
        assertTrue(result.artifacts.single().lowered?.unit?.testProduct == null)
    }
}
