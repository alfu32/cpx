package cplus.compiler

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

class RuntimeEnvironmentAndStreamsTest {
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
