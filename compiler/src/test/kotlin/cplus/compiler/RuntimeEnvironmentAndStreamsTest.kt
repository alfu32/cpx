package cplus.compiler

import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

class RuntimeEnvironmentAndStreamsTest {
    @Test
    fun windowsStartupExposesArgumentsEnvironmentAndStandardChannels() {
        assumeTrue(System.getProperty("os.name").contains("windows", ignoreCase = true))
        val manifest = requireNotNull(SdkManifestLoader.load(SdkManifestLocator.defaultManifestPath()).manifest)
        val target = TargetInfo(targetTriple = "windows-x86_64")
        val resolution = requireNotNull(SdkResolver.resolve(manifest, target).resolution)
        val plan = requireNotNull(RuntimeLinker.plan(resolution, target).plan)
        val directory = Files.createTempDirectory("cplus-windows-runtime-stdio")
        val source = directory.resolve("stdio_test.c").also {
            Files.writeString(it, """
                #include "cplus_platform.h"

                static int equals(const char* left, const char* right) {
                    while (*left && *left == *right) { left++; right++; }
                    return *left == *right;
                }

                static int begins_with(const char* value, const char* prefix) {
                    while (*prefix) if (*value++ != *prefix++) return 0;
                    return 1;
                }

                int main(int argc, char** argv) {
                    const char* const* environment = platform_process_environment();
                    int found_environment = 0;
                    unsigned long long index;
                    char input[12];
                    if (argc != 3 || platform_process_argument_count() != 3 ||
                        !equals(argv[1], "alpha") || !equals(argv[2], "two words")) return 1;
                    if (!equals(platform_process_argument(1), "alpha") ||
                        platform_process_argument(3) != (const char*)0) return 2;
                    for (index = 0; environment && environment[index]; index++) {
                        if (begins_with(environment[index], "CPX_PROCESS_INHERIT=")) {
                            found_environment = equals(environment[index] + 20, "inherited");
                            break;
                        }
                    }
                    if (!found_environment) return 3;
                    if (platform_read_stdin((void*)0, 1) != CPLUS_PAL_INVALID_ARGUMENT ||
                        platform_read_stdin(input, 0) != 0) return 4;
                    if (platform_read_stdin(input, sizeof(input) - 1) != sizeof(input) - 1) return 5;
                    input[sizeof(input) - 1] = '\0';
                    if (!equals(input, "stdin-check") ||
                        platform_write_stdout((const char*)0, 1) != CPLUS_PAL_INVALID_ARGUMENT) return 5;
                    if (platform_write_stdout("stdout-check", 12) != 12 ||
                        platform_write_stderr("stderr-check", 12) != 12) return 6;
                    return 0;
                }
            """.trimIndent())
        }
        val executable = directory.resolve("stdio_test.exe")
        try {
            val link = LinkDriver.link(LinkRequest(source, executable, target, resolution), plan)
            assertTrue(link.isSuccessful, link.output)
            val descriptor = resolution.targetDescriptor
                ?: requireNotNull(TargetRegistry.load(resolution.layout.abiDescriptor).descriptor)
            val audit = RuntimeDependencyAuditor.inspect(executable, descriptor, target.buildProfile)
            assertTrue(audit.isSuccessful, audit.diagnostics.joinToString())
            val builder = ProcessBuilder(executable.toString(), "alpha", "two words")
            builder.environment()["CPX_PROCESS_INHERIT"] = "inherited"
            val process = builder.start()
            process.outputStream.use { it.write("stdin-check".toByteArray(Charsets.UTF_8)) }
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                process.waitFor(2, TimeUnit.SECONDS)
                throw AssertionError("Windows process-channel fixture timed out; artifacts at $directory")
            }
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            assertEquals(0, process.exitValue(), "Windows process-channel fixture failed: stdout='$stdout', stderr='$stderr'")
            assertEquals("stdout-check", stdout)
            assertEquals("stderr-check", stderr)
        } finally {
            Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }

    @Test
    fun linuxStartupExposesArgumentsEnvironmentAndRoutesStandardChannels() {
        assumeTrue(System.getProperty("os.name").contains("linux", ignoreCase = true))
        val root = SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize().parent!!.parent!!
        val directory = Files.createTempDirectory("cplus-runtime-stdio")
        val source = directory.resolve("stdio_test.c").also {
            Files.writeString(it, """
                #include "cplus_platform.h"
                #include <stdio.h>

                static int same(const char* left, const char* right) {
                    while (*left && *left == *right) { left++; right++; }
                    return *left == *right;
                }

                static long close_descriptor(long descriptor) {
                    register long number __asm__("rax") = 3;
                    register long argument __asm__("rdi") = descriptor;
                    __asm__ volatile("syscall" : "+a"(number) : "D"(argument) : "rcx", "r11", "memory");
                    return number;
                }

                int main(int argc, char** argv) {
                    const char* const* environment = platform_process_environment();
                    int found_environment = 0;
                    unsigned char value;
                    int index;
                    if (argc != 3 || platform_process_argument_count() != 3) return 1;
                    if (!same(argv[1], "alpha") || !same(argv[2], "two words")) return 2;
                    if (!same(platform_process_argument(1), "alpha") ||
                        platform_process_argument(3) != (const char*)0) return 3;
                    if (platform_read_stdin((void*)0, 1) != CPLUS_PAL_INVALID_ARGUMENT ||
                        platform_read_stdin((void*)0, 0) != 0) return 9;
                    if (platform_write_stdout((const char*)0, 1) != CPLUS_PAL_INVALID_ARGUMENT ||
                        platform_write_stdout((const char*)0, 0) != 0) return 10;
                    if (platform_write_stderr((const char*)0, 1) != CPLUS_PAL_INVALID_ARGUMENT ||
                        platform_write_stderr((const char*)0, 0) != 0) return 11;
                    for (index = 0; environment && environment[index]; index++) {
                        if (same(environment[index], "CPX_RUNTIME_ENV=present")) found_environment = 1;
                    }
                    if (!found_environment) return 4;
                    if (fgetc(stdin) != 'A' || fgetc(stdin) != 255 || fgetc(stdin) != EOF) return 5;
                    if (printf("out:%s\n", argv[1]) != 10) return 6;
                    if (fprintf(stderr, "err:%d", 7) != 5) return 7;
                    if (fputc('!', stderr) != '!') return 8;
                    if (close_descriptor(0) != 0 || platform_read_stdin(&value, 1) != CPLUS_PAL_IO_ERROR) return 12;
                    if (close_descriptor(1) != 0 || platform_write_stdout("x", 1) != CPLUS_PAL_IO_ERROR) return 13;
                    if (close_descriptor(2) != 0 || platform_write_stderr("x", 1) != CPLUS_PAL_IO_ERROR) return 14;
                    return 0;
                }
            """.trimIndent())
        }
        val executable = directory.resolve("stdio_test")
        try {
            val compile = ProcessBuilder(
                "cc", "-std=c17", "-ffreestanding", "-fno-builtin", "-fno-stack-protector",
                "-fno-pie", "-nostdlib", "-static", "-Wl,-e,_start",
                "-Wl,-T,${root.resolve("platform/linux/thread-tls.ld")}",
                "-I", root.resolve("libc/include").toString(),
                "-I", root.resolve("runtime/include").toString(),
                source.toString(), root.resolve("startup/linux-x86_64/start.S").toString(),
                root.resolve("runtime/src/startup.c").toString(),
                root.resolve("runtime/src/stdio.c").toString(),
                root.resolve("runtime/src/format.c").toString(),
                root.resolve("runtime/src/libc_core.c").toString(),
                root.resolve("runtime/src/memory.c").toString(),
                root.resolve("runtime/src/allocator.c").toString(),
                root.resolve("platform/linux/runtime.c").toString(), "-o", executable.toString()
            ).redirectErrorStream(true).start()
            val compileOutput = compile.inputStream.bufferedReader().readText()
            assertEquals(0, compile.waitFor(), compileOutput)
            val undefinedSymbols = ProcessBuilder("nm", "-u", executable.toString()).start()
            val undefinedOutput = undefinedSymbols.inputStream.bufferedReader().readText()
            assertEquals(0, undefinedSymbols.waitFor(), undefinedOutput)
            assertTrue(undefinedOutput.isBlank(), undefinedOutput)

            val process = ProcessBuilder(executable.toString(), "alpha", "two words")
                .apply { environment()["CPX_RUNTIME_ENV"] = "present" }
                .start()
            process.outputStream.use { it.write(byteArrayOf('A'.code.toByte(), 0xff.toByte())) }
            val stdout = process.inputStream.readBytes().toString(Charsets.UTF_8)
            val stderr = process.errorStream.readBytes().toString(Charsets.UTF_8)
            assertEquals(0, process.waitFor(), "stdout=$stdout stderr=$stderr")
            assertEquals("out:alpha\n", stdout)
            assertEquals("err:7!", stderr)
        } finally {
            Files.deleteIfExists(executable)
            Files.deleteIfExists(source)
            Files.deleteIfExists(directory)
        }
    }
}
