package cplus.cli

import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TestProcessRunnerTest {
    @Test
    fun isolatesRunsDrainsNoisyStreamsAndCleansReportFiles() {
        val directory = Files.createTempDirectory("cplus test process")
        val executable = buildProbe(directory)
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val runner = TestProcessRunner(stdout, stderr)
        try {
            val first = runner.run(executable, "isolated", 10, directory)
            val second = runner.run(executable, "isolated", 10, directory)
            assertTrue(first.isSuccessful, first.error.orEmpty())
            assertTrue(second.isSuccessful, second.error.orEmpty())
            assertEquals(listOf(true), first.protocol?.assertions)
            assertEquals(listOf(true), second.protocol?.assertions)

            val noisy = runner.run(executable, "noisy", 10, directory)
            assertTrue(noisy.isSuccessful, noisy.error.orEmpty())
            assertTrue(stdout.size() > 128 * 1024)
            assertTrue(stderr.size() > 128 * 1024)
            assertEquals(emptyList(), Files.list(directory).use { it.filter { path -> path.fileName.toString().startsWith("cplus-test-result-") }.toList() })
        } finally {
            removeTree(directory)
        }
    }

    @Test
    fun treatsMissingCompletionNonzeroCrashAndMissingReportAsExecutionErrors() {
        val directory = Files.createTempDirectory("cplus-test-process-errors")
        val executable = buildProbe(directory)
        val runner = TestProcessRunner(ByteArrayOutputStream(), ByteArrayOutputStream())
        try {
            val missingCompletion = runner.run(executable, "missing-complete", 10, directory)
            val nonzero = runner.run(executable, "nonzero", 10, directory)
            val crash = runner.run(executable, "crash", 10, directory)
            val noReport = runner.run(executable, "no-report", 10, directory)

            assertFalse(missingCompletion.isSuccessful)
            assertTrue(assertNotNull(missingCompletion.error).contains("COMPLETE"))
            assertFalse(nonzero.isSuccessful)
            assertTrue(assertNotNull(nonzero.error).contains("status 7"))
            assertFalse(crash.isSuccessful)
            assertTrue(assertNotNull(crash.error).contains("status"))
            assertFalse(noReport.isSuccessful)
            assertTrue(assertNotNull(noReport.error).contains("did not produce"))
        } finally {
            removeTree(directory)
        }
    }

    @Test
    fun timeoutReapsTheChildAndStartFailureIsReported() {
        val directory = Files.createTempDirectory("cplus-test-process-timeout")
        val executable = buildProbe(directory)
        val runner = TestProcessRunner(ByteArrayOutputStream(), ByteArrayOutputStream())
        try {
            val startedAt = System.nanoTime()
            val timeout = runner.run(executable, "timeout", 1, directory)
            val elapsedSeconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startedAt)
            val missing = runner.run(directory.resolve("does-not-exist"), "fixture", 1, directory)

            assertTrue(timeout.timedOut)
            assertTrue(assertNotNull(timeout.error).contains("timed out"))
            assertTrue(elapsedSeconds < 8, "timed child was not reaped promptly: $elapsedSeconds seconds")
            assertFalse(missing.isSuccessful)
            assertTrue(assertNotNull(missing.error).contains("unable to start"))
            assertEquals(emptyList(), Files.list(directory).use { it.filter { path -> path.fileName.toString().startsWith("cplus-test-result-") }.toList() })
        } finally {
            removeTree(directory)
        }
    }

    @Test
    fun interruptionTerminatesTheActiveChildAndReturnsPromptly() {
        val directory = Files.createTempDirectory("cplus-test-process-cancel")
        val executable = buildProbe(directory)
        val runner = TestProcessRunner(ByteArrayOutputStream(), ByteArrayOutputStream())
        val result = AtomicReference<FixtureProcessResult?>()
        val worker = Thread { result.set(runner.run(executable, "timeout", 30, directory)) }
        try {
            worker.start()
            Thread.sleep(300)
            worker.interrupt()
            worker.join(5_000)

            assertFalse(worker.isAlive, "cancelled fixture process was not reaped")
            assertTrue(assertNotNull(result.get()).error?.contains("interrupted") == true)
            assertEquals(emptyList(), Files.list(directory).use { it.filter { path -> path.fileName.toString().startsWith("cplus-test-result-") }.toList() })
        } finally {
            if (worker.isAlive) worker.interrupt()
            removeTree(directory)
        }
    }

    private fun buildProbe(directory: Path): Path {
        val source = directory.resolve("fixture_probe.c")
        val executable = directory.resolve(if (System.getProperty("os.name").contains("windows", true)) "fixture_probe.exe" else "fixture_probe")
        Files.writeString(
            source,
            """
                #include <stdio.h>
                #include <stdlib.h>
                #include <string.h>

                static int fixture_state;
                static int write_records(const char* identity, const char* path, int completion) {
                    FILE* output = fopen(path, "wb");
                    int passed = ++fixture_state == 1;
                    if (!output) return 10;
                    fprintf(output, "CPLUS-TEST\t1\tBEGIN\t%s\n", identity);
                    fprintf(output, "CPLUS-TEST\t1\tASSERT\t1\t%s\n", passed ? "PASS" : "FAIL");
                    if (completion) fprintf(output, "CPLUS-TEST\t1\tCOMPLETE\t%d\t%d\t1\n", passed ? 1 : 0, passed ? 0 : 1);
                    return fclose(output) == 0 ? 0 : 11;
                }

                int main(int argc, char** argv) {
                    volatile unsigned long long counter = 0;
                    int index;
                    if (argc != 3) return 90;
                    if (strcmp(argv[1], "timeout") == 0) { for (;;) counter++; }
                    if (strcmp(argv[1], "no-report") == 0) { remove(argv[2]); return 0; }
                    if (strcmp(argv[1], "missing-complete") == 0) return write_records(argv[1], argv[2], 0);
                    if (strcmp(argv[1], "nonzero") == 0) { write_records(argv[1], argv[2], 1); return 7; }
                    if (strcmp(argv[1], "crash") == 0) { write_records(argv[1], argv[2], 0); abort(); }
                    if (write_records(argv[1], argv[2], 1) != 0) return 91;
                    if (strcmp(argv[1], "noisy") == 0) {
                        for (index = 0; index < 150000; index++) { fputc('o', stdout); fputc('e', stderr); }
                        fflush(stdout); fflush(stderr);
                    }
                    return 0;
                }
            """.trimIndent()
        )
        val configured = System.getenv("CC")?.split(Regex("\\s+")).orEmpty().filter(String::isNotBlank)
        val candidates = listOf(configured, listOf("cc"), listOf("gcc"), listOf("clang")).filter(List<String>::isNotEmpty)
        var lastError = "no C compiler candidate was available"
        for (command in candidates) {
            try {
                val compiler = ProcessBuilder(command + listOf("-std=c17", source.toString(), "-o", executable.toString()))
                    .redirectErrorStream(true)
                    .start()
                val output = compiler.inputStream.bufferedReader().readText()
                if (compiler.waitFor() == 0) return executable
                lastError = "${command.first()} failed to compile process probe: $output"
            } catch (error: Exception) {
                lastError = "${command.first()} could not be started: ${error.message}"
            }
        }
        throw AssertionError(lastError)
    }

    private fun removeTree(root: Path) {
        Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
    }
}
