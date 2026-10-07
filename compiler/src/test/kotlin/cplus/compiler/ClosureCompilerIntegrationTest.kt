package cplus.compiler

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClosureCompilerIntegrationTest {
    @Test
    fun capturedInnerFunctionLowersToEnvironmentAndRunsAsC() {
        val directory = Files.createTempDirectory("cplus-closure")
        val source = directory.resolve("main.cp")
        Files.writeString(
            source,
            """
                int outer() {
                    int x = 10;
                    int add(int y) {
                        return x + y;
                    }
                    return add(4);
                }

                int main() {
                    return outer() == 14 ? 0 : 1;
                }
            """.trimIndent()
        )

        val result = CPlusCompiler().compile(CompileRequest(listOf(source)))

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val generated = result.generatedUnits.single().text
        assertTrue(generated.contains("struct outer__add__env_t"))
        assertTrue(generated.contains("int outer__add"))
        assertTrue(generated.contains("outer__add(&outer__add__env, 4)"))

        val cFile = directory.resolve("program.c")
        val executable = directory.resolve("program")
        Files.writeString(cFile, generated)
        val compileProcess = ProcessBuilder("cc", "-std=c17", cFile.toString(), "-o", executable.toString())
            .redirectErrorStream(true)
            .start()
        val compileOutput = compileProcess.inputStream.bufferedReader().readText()
        assertEquals(0, compileProcess.waitFor(), compileOutput)
        val execution = ProcessBuilder(executable.toString()).redirectErrorStream(true).start()
        assertEquals(0, execution.waitFor())
    }
}
