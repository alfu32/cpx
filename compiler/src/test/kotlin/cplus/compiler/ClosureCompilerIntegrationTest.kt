package cplus.compiler

import java.nio.file.Files
import cplus.core.AstFunction
import cplus.core.AstStruct
import cplus.core.Origin
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
        val hoisted = result.artifacts.single().ast.declarations.filter {
            (it is AstStruct && it.name == "outer__add__env_t") ||
                (it is AstFunction && it.name == "outer__add")
        }
        assertEquals(2, hoisted.size)
        assertTrue(hoisted.all { it.origin is Origin.Generated })
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

    @Test
    fun nestedInnerFunctionsCaptureTheirParentEnvironmentAndRunAsC() {
        val directory = Files.createTempDirectory("cplus-nested-closure")
        val source = directory.resolve("main.cp")
        Files.writeString(
            source,
            """
                int outer(int base) {
                    int inner(int increment) {
                        int leaf(int value) {
                            return base + increment + value;
                        }
                        return leaf(3);
                    }
                    return inner(2);
                }

                int main() {
                    return outer(4) == 9 ? 0 : 1;
                }
            """.trimIndent()
        )

        val result = CPlusCompiler().compile(CompileRequest(listOf(source)))

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val generated = result.generatedUnits.single().text
        assertTrue(generated.contains("struct outer__inner__env_t"))
        assertTrue(generated.contains("struct outer__inner__leaf__env_t"))
        assertTrue(generated.contains("outer__inner__leaf(&outer__inner__leaf__env, 3)"))

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
