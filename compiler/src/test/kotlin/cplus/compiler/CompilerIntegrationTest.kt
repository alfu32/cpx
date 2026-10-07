package cplus.compiler

import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompilerIntegrationTest {
    @Test
    fun minimalProgramTranscodesAndExecutes() {
        val source = """
            struct point_t {
                int x;
                int y;
            };

            int add(int a, int b) {
                return a + b;
            }

            int main() {
                return add(1, 2);
            }
        """.trimIndent()
        val compiler = CPlusCompiler()
        val result = compiler.compileText(Files.createTempFile("cplus", ".cp"), source)

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val generated = result.generatedUnits.single().text
        assertTrue(generated.contains("struct point_t"))
        assertTrue(generated.contains("int add(int a, int b);"))
        assertTrue(generated.contains("return (a + b);"))

        val directory = Files.createTempDirectory("cplus-e2e")
        val cFile = directory.resolve("program.c")
        val executable = directory.resolve("program")
        cFile.writeText(generated)

        val compileProcess = ProcessBuilder("cc", "-std=c17", cFile.toString(), "-o", executable.toString())
            .redirectErrorStream(true)
            .start()
        val compileOutput = compileProcess.inputStream.bufferedReader().readText()
        assertEquals(0, compileProcess.waitFor(), compileOutput)

        val execution = ProcessBuilder(executable.toString()).redirectErrorStream(true).start()
        val executionOutput = execution.inputStream.bufferedReader().readText()
        assertEquals(3, execution.waitFor(), executionOutput)
    }

    @Test
    fun instanceAndStaticMethodsLowerToCallableCFunctions() {
        val source = """
            struct point_t {
                int x;

                int get(self) {
                    return self.x;
                }

                int default_value() {
                    return 4;
                }
            };

            int main() {
                point_t point;
                point.x = 3;
                return point.get() + point_t.default_value();
            }
        """.trimIndent()
        val result = CPlusCompiler().compileText(Files.createTempFile("cplus-methods", ".cp"), source)

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val generated = result.generatedUnits.single().text
        assertTrue(generated.contains("int point_t__get(struct point_t* self);"))
        assertTrue(generated.contains("int point_t__default_value();"))
        assertTrue(generated.contains("point_t__get(&point)"))
        assertTrue(generated.contains("point_t__default_value()"))
    }
}
