package cplus.compiler

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

class RuntimeStdProcessTest {
    @Test
    fun cplusStdProcessForwardsIdentityArgumentsEnvironmentSpawnWaitExitAndChannels() {
        assumeTrue(System.getProperty("os.name").contains("linux", ignoreCase = true))
        val manifestPath = SdkManifestLocator.defaultManifestPath()
        val manifest = requireNotNull(SdkManifestLoader.load(manifestPath).manifest)
        val target = TargetInfo(targetTriple = "linux-x86_64")
        val resolution = requireNotNull(SdkResolver.resolve(manifest, target).resolution)
        val plan = requireNotNull(RuntimeLinker.plan(resolution, target).plan)
        val root = manifestPath.toAbsolutePath().normalize().parent!!.parent!!
        val directory = Files.createTempDirectory("cplus-std-process")
        val mainSource = directory.resolve("main.cp").also {
            Files.writeString(it, """
                import {
                    std_process_argument,
                    std_process_argument_count,
                    std_process_environment,
                    std_process_exit,
                    std_process_id,
                    std_process_spawn,
                    std_process_stderr_write,
                    std_process_stdin_read,
                    std_process_stdout_write,
                    std_process_wait
                } from std.process;

                static int same(const char* left, const char* right) {
                    while (*left && *left == *right) { left++; right++; }
                    return *left == *right;
                }

                int main() {
                    const char* const* environment = std_process_environment();
                    unsigned long long argument_count = std_process_argument_count();
                    unsigned long long index = 0;
                    int found_environment = 0;
                    char input[2];
                    int exit_status = -1;
                    long long childProcess;
                    const char* child_arguments[3];
                    if (std_process_id() <= 0) return 1;
                    for (; environment && environment[index]; index++) {
                        if (same(environment[index], "CPX_STD_PROCESS_TEST=present")) found_environment = 1;
                    }
                    if (!found_environment) return 2;
                    if (std_process_stdin_read((void*)0, 1) != -2 ||
                        std_process_stdout_write((const char*)0, 1) != -2 ||
                        std_process_stderr_write((const char*)0, 1) != -2) return 3;
                    if (std_process_stdin_read((void*)0, 0) != 0 ||
                        std_process_stdout_write((const char*)0, 0) != 0 ||
                        std_process_stderr_write((const char*)0, 0) != 0) return 4;
                    if (std_process_spawn((const char*)0, (const char* const*)0) != -2 ||
                        std_process_wait(-1, &exit_status) != -2) return 5;
                    if (argument_count == 2) {
                        if (!same(std_process_argument(1), "child")) return 6;
                        if (std_process_argument(2) != (const char*)0) return 7;
                        if (std_process_stdin_read(input, 2) != 0) return 8;
                        if (std_process_stdout_write("child-out\n", 10) != 10 ||
                            std_process_stderr_write("child-err\n", 10) != 10) return 9;
                        std_process_exit(23);
                        return 24;
                    }
                    if (argument_count != 1 || std_process_argument(0) == (const char*)0 ||
                        std_process_argument(1) != (const char*)0) return 10;
                    if (std_process_stdin_read(input, 2) != 2 || input[0] != 'I' || input[1] != 'N') return 11;
                    child_arguments[0] = std_process_argument(0);
                    child_arguments[1] = "child";
                    child_arguments[2] = (const char*)0;
                    childProcess = std_process_spawn(child_arguments[0], child_arguments);
                    if (childProcess < 0) return 12;
                    if (std_process_wait(childProcess, (int*)0) != -2) return 13;
                    if (std_process_wait(childProcess, &exit_status) != 0 || exit_status != 23) return 14;
                    if (std_process_stdout_write("parent-out\n", 11) != 11 ||
                        std_process_stderr_write("parent-err\n", 11) != 11) return 15;
                    return 0;
                }
            """.trimIndent())
        }
        val executable = directory.resolve("std-process")
        try {
            val compilation = CPlusCompiler().compile(
                CompileRequest(listOf(root.resolve("std/src/process.cp"), mainSource), target)
            )
            assertTrue(compilation.isSuccessful, compilation.diagnostics.joinToString())
            assertEquals(1, compilation.generatedUnits.size)
            Files.writeString(directory.resolve("std-process.c"), compilation.generatedUnits.single().text)

            val link = LinkDriver.link(
                LinkRequest(directory.resolve("std-process.c"), executable, target, resolution),
                plan
            )
            assertTrue(link.isSuccessful, link.output)
            val undefinedSymbols = ProcessBuilder("nm", "-u", executable.toString()).start()
            val undefinedOutput = undefinedSymbols.inputStream.bufferedReader().readText()
            assertEquals(0, undefinedSymbols.waitFor(), undefinedOutput)
            assertTrue(undefinedOutput.isBlank(), undefinedOutput)

            val process = ProcessBuilder(executable.toString())
                .apply { environment()["CPX_STD_PROCESS_TEST"] = "present" }
                .start()
            process.outputStream.use { it.write(byteArrayOf('I'.code.toByte(), 'N'.code.toByte())) }
            val stdout = process.inputStream.readBytes().toString(Charsets.UTF_8)
            val stderr = process.errorStream.readBytes().toString(Charsets.UTF_8)
            assertEquals(0, process.waitFor(), "stdout=$stdout stderr=$stderr")
            assertEquals("child-out\nparent-out\n", stdout)
            assertEquals("child-err\nparent-err\n", stderr)
        } finally {
            Files.deleteIfExists(executable)
            Files.deleteIfExists(directory.resolve("std-process.c"))
            Files.deleteIfExists(mainSource)
            Files.deleteIfExists(directory)
        }
    }
}
