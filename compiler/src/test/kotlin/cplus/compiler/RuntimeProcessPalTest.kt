package cplus.compiler

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.util.concurrent.TimeUnit

class RuntimeProcessPalTest {
    @Test
    fun windowsProcessPalSpawnsWaitsAndNormalizesLaunchFailures() {
        assumeTrue(System.getProperty("os.name").contains("windows", ignoreCase = true))
        val manifest = requireNotNull(SdkManifestLoader.load(SdkManifestLocator.defaultManifestPath()).manifest)
        val target = TargetInfo(targetTriple = "windows-x86_64")
        val resolution = requireNotNull(SdkResolver.resolve(manifest, target).resolution)
        val plan = requireNotNull(RuntimeLinker.plan(resolution, target).plan)
        val directory = Files.createTempDirectory("cplus-windows-process-pal")
        val source = directory.resolve("process_test.c").also {
            Files.writeString(it, """
                #include "cplus_platform.h"

                static int equals_text(const char* left, const char* right) {
                    unsigned long long index = 0;
                    while (left[index] && right[index] && left[index] == right[index]) index++;
                    return left[index] == right[index];
                }

                int main(int argc, char** argv) {
                    const char* arguments[] = {"process-test", "--child", (const char*)0};
                    const char* const* environment;
                    int status = -1;
                    long long child;
                    if (argc == 2 && argv[1][0] == '-' && argv[1][1] == '-' &&
                        argv[1][2] == 'c' && argv[1][3] == 'h' && argv[1][4] == 'i' &&
                        argv[1][5] == 'l' && argv[1][6] == 'd' && argv[1][7] == '\0') {
                        environment = platform_process_environment();
                        if (!environment) return 38;
                        while (*environment && !equals_text(*environment, "CPX_PROCESS_INHERIT=inherited")) environment++;
                        if (!*environment) return 39;
                        if (platform_write_stdout("process-child-output\n", 21) != 21) return 40;
                        return 37;
                    }
                    if (platform_process_id() <= 0) return 1;
                    if (platform_process_spawn((const char*)0, (const char* const*)0) != CPLUS_PAL_INVALID_ARGUMENT ||
                        platform_process_wait(-1, &status) != CPLUS_PAL_INVALID_ARGUMENT ||
                        platform_process_wait(1, (int*)0) != CPLUS_PAL_INVALID_ARGUMENT) return 2;
                    if (platform_process_spawn("C:/cplus/no-such-program.exe", arguments) != CPLUS_PAL_NOT_FOUND) return 3;
                    child = platform_process_spawn(argv[0], arguments);
                    if (child <= 0 || platform_process_wait(child, &status) != 0 || status != 37) return 4;
                    return 0;
                }
            """.trimIndent())
        }
        val executable = directory.resolve("process_test.exe")
        try {
            val link = LinkDriver.link(LinkRequest(source, executable, target, resolution), plan)
            assertTrue(link.isSuccessful, link.output)
            val descriptor = resolution.targetDescriptor
                ?: requireNotNull(TargetRegistry.load(resolution.layout.abiDescriptor).descriptor)
            val audit = RuntimeDependencyAuditor.inspect(executable, descriptor, target.buildProfile)
            assertTrue(audit.isSuccessful, audit.diagnostics.joinToString())
            val process = ProcessBuilder(executable.toString()).redirectErrorStream(true).apply {
                environment()["CPX_PROCESS_INHERIT"] = "inherited"
            }.start()
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                process.waitFor(2, TimeUnit.SECONDS)
                throw AssertionError("Windows process fixture timed out; artifacts at $directory")
            }
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(0, process.exitValue(), "Windows process fixture failed: $output")
            assertTrue(output.contains("process-child-output"), "Child stdout was not inherited: '$output'")
        } finally {
            Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }

    @Test
    fun linuxProcessPalSpawnsWaitsAndNormalizesLaunchFailures() {
        assumeTrue(System.getProperty("os.name").contains("linux", ignoreCase = true))
        val root = SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize().parent!!.parent!!
        val directory = Files.createTempDirectory("cplus-process-pal")
        val source = directory.resolve("process_test.c").also {
            Files.writeString(it, """
                #include "cplus_platform.h"

                int main(int argc, char** argv) {
                    const char* child_arguments[] = {
                        "/bin/sh",
                        "-c",
                        "printf 'process-child-output\\n'; test \"${'$'}CPX_PROCESS_INHERIT\" = inherited && test \"${'$'}1\" = child-argument || exit 90; exit 37",
                        "process-test",
                        "child-argument",
                        (const char*)0
                    };
                    const char* signal_arguments[] = {
                        "/bin/sh",
                        "-c",
                        "kill -TERM $$",
                        "signal-test",
                        (const char*)0
                    };
                    long long process;
                    int status = -1;
                    (void)argc;
                    (void)argv;
                    if (platform_process_id() <= 0) return 1;
                    if (platform_process_spawn((const char*)0, (const char* const*)0) != CPLUS_PAL_INVALID_ARGUMENT) return 2;
                    if (platform_process_spawn("/cplus/no/such/executable", (const char* const*)0) != CPLUS_PAL_NOT_FOUND) return 3;
                    if (platform_process_wait(-1, &status) != CPLUS_PAL_INVALID_ARGUMENT) return 4;
                    process = platform_process_spawn("/bin/sh", child_arguments);
                    if (process < 0) return 5;
                    if (platform_process_wait(process, &status) != 0) return 6;
                    if (status != 37) return 7;
                    process = platform_process_spawn("/bin/sh", signal_arguments);
                    if (process < 0 || platform_process_wait(process, &status) != 0) return 9;
                    if (status != 143) return 10;
                    return 0;
                }
            """.trimIndent())
        }
        val startup = directory.resolve("start.S").also {
            Files.writeString(it, """
                .text
                .globl _start
                .type _start, @function
                _start:
                    xor %rbp, %rbp
                    call __cplus_linux_initialize_main_tls
                    test %eax, %eax
                    jnz .Lexit
                    mov (%rsp), %rdi
                    lea 8(%rsp), %rsi
                    lea 16(%rsp,%rdi,8), %rdx
                    call __cplus_start
                    mov %eax, %edi
                    call platform_process_exit
                    hlt
                .Lexit:
                    mov %eax, %edi
                    call platform_process_exit
                    hlt
                .size _start, .-_start
                .section .note.GNU-stack,"",@progbits
            """.trimIndent())
        }
        val executable = directory.resolve("process_test")
        try {
            val compile = ProcessBuilder(
                "cc", "-std=c17", "-ffreestanding", "-fno-builtin", "-fno-stack-protector",
                "-fno-pie", "-nostdlib", "-static", "-Wl,-e,_start",
                "-Wl,-T,${root.resolve("platform/linux/thread-tls.ld")}",
                "-I", root.resolve("runtime/include").toString(), source.toString(), startup.toString(),
                root.resolve("runtime/src/startup.c").toString(), root.resolve("platform/linux/runtime.c").toString(),
                "-o", executable.toString()
            ).redirectErrorStream(true).start()
            val compileOutput = compile.inputStream.bufferedReader().readText()
            assertEquals(0, compile.waitFor(), compileOutput)

            val process = ProcessBuilder(executable.toString())
                .redirectErrorStream(true)
                .apply { environment()["CPX_PROCESS_INHERIT"] = "inherited" }
                .start()
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(0, process.waitFor(), output)
            assertTrue("process-child-output" in output, output)
        } finally {
            Files.deleteIfExists(executable)
            Files.deleteIfExists(startup)
            Files.deleteIfExists(source)
            Files.deleteIfExists(directory)
        }
    }
}
