package cplus.compiler

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

class RuntimeThreadPalTest {
    @Test
    fun linuxCloneCreatesJoinableThreadWithIndependentStaticTls() {
        assumeTrue(System.getProperty("os.name").contains("linux", ignoreCase = true))
        val root = SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize().parent!!.parent!!
        val directory = Files.createTempDirectory("cplus-runtime-threads")
        val source = directory.resolve("thread_test.c").also {
            Files.writeString(it, """
                #include "cplus_platform.h"
                #include "cplus_runtime.h"
                #include <errno.h>

                _Thread_local int initialized_tls = 41;
                _Thread_local int zero_tls;

                typedef struct thread_result {
                    long long parent_id;
                    int observed;
                } thread_result;

                static void* thread_entry(void* opaque) {
                    thread_result* result = (thread_result*)opaque;
                    if (platform_thread_current_id() <= 0 ||
                        platform_thread_current_id() == result->parent_id) return (void*)1;
                    if (!__cplus_runtime_thread_is_attached()) return (void*)2;
                    if (initialized_tls != 41 || zero_tls != 0 || errno != 0) return (void*)3;
                    initialized_tls = 99;
                    zero_tls = 7;
                    errno = 13;
                    result->observed = initialized_tls + zero_tls + errno;
                    return (void*)0x12345;
                }

                int main(int argc, char** argv) {
                    thread_result shared;
                    long long parent_id;
                    long long thread;
                    unsigned int iteration;
                    void* thread_value = (void*)0;
                    (void)argc;
                    (void)argv;
                    if (CPLUS_PAL_API_VERSION != 4) return 1;
                    if (platform_thread_create((cplus_thread_entry_t)0, (void*)0) != CPLUS_PAL_INVALID_ARGUMENT) return 2;
                    if (platform_thread_join(-1, (void**)0) != CPLUS_PAL_INVALID_ARGUMENT) return 3;
                    parent_id = platform_thread_current_id();
                    if (parent_id <= 0 || platform_thread_yield() != 0) return 4;
                    initialized_tls = 52;
                    zero_tls = 6;
                    errno = 5;
                    shared.parent_id = parent_id;
                    shared.observed = 0;
                    for (iteration = 0; iteration < 8; iteration++) {
                        shared.observed = 0;
                        thread_value = (void*)0;
                        thread = platform_thread_create(thread_entry, &shared);
                        if (thread <= 0) return 5;
                        if (platform_thread_join(thread, &thread_value) != 0) return 6;
                        if (thread_value != (void*)0x12345 || shared.observed != 119) return 7;
                    }
                    if (initialized_tls != 52 || zero_tls != 6 || errno != 5) return 8;
                    return 0;
                }
            """.trimIndent())
        }
        val executable = directory.resolve("thread_test")
        try {
            val compile = ProcessBuilder(
                "cc", "-std=c17", "-ffreestanding", "-fno-builtin", "-fno-stack-protector",
                "-fno-pie", "-nostdlib", "-static", "-Wl,-e,_start",
                "-Wl,-T,${root.resolve("platform/linux/thread-tls.ld")}",
                "-I", root.resolve("libc/include").toString(),
                "-I", root.resolve("runtime/include").toString(),
                "-I", root.resolve("platform/linux").toString(),
                source.toString(), root.resolve("startup/linux-x86_64/start.S").toString(),
                root.resolve("platform/linux/thread-x86_64.S").toString(),
                root.resolve("runtime/src/startup.c").toString(),
                root.resolve("runtime/src/libc_core.c").toString(),
                root.resolve("runtime/src/allocator.c").toString(),
                root.resolve("runtime/src/memory.c").toString(),
                root.resolve("platform/linux/runtime.c").toString(),
                "-o", executable.toString()
            ).redirectErrorStream(true).start()
            val compileOutput = compile.inputStream.bufferedReader().readText()
            assertEquals(0, compile.waitFor(), compileOutput)
            val undefinedSymbols = ProcessBuilder("nm", "-u", executable.toString()).start()
            val undefinedOutput = undefinedSymbols.inputStream.bufferedReader().readText()
            assertEquals(0, undefinedSymbols.waitFor(), undefinedOutput)
            assertTrue(undefinedOutput.isBlank(), undefinedOutput)
            val run = ProcessBuilder(executable.toString()).redirectErrorStream(true).start()
            val runOutput = run.inputStream.bufferedReader().readText()
            val runExit = run.waitFor()
            assertEquals(0, runExit, "thread fixture failed with output '$runOutput'; artifacts at $directory")
        } finally {
            Files.deleteIfExists(executable)
            Files.deleteIfExists(source)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun threadPalUsesFixedWidthHandlesAcrossDeclaredTargets() {
        val root = SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize().parent!!.parent!!
        val api = root.resolve("platform/api/thread.cp")
        listOf("linux-x86_64", "linux-aarch64", "windows-x86_64", "windows-aarch64").forEach { targetName ->
            val result = CPlusCompiler().compile(
                CompileRequest(listOf(api), target = TargetInfo(targetTriple = targetName))
            )
            assertTrue(result.isSuccessful, "$targetName: ${result.diagnostics.joinToString()}")
            val model = requireNotNull(result.semanticModel)
            val descriptor = requireNotNull(TargetRegistry.load(root.resolve("abi/$targetName.toml")).descriptor)
            assertEquals(8, AbiLayoutEngine(descriptor).layout(model.functions.getValue("platform_thread_create").returnType).size, targetName)
            val generated = result.generatedUnits.joinToString("\n") { it.text }
            assertTrue("platform_thread_create" in generated)
            assertTrue("platform_thread_join" in generated)
            assertTrue("platform_thread_current_id" in generated)
            assertTrue("platform_thread_yield" in generated)
        }
    }
}
