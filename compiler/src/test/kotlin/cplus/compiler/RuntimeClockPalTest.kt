package cplus.compiler

import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

class RuntimeClockPalTest {
    @Test
    fun windowsProvidesWallMonotonicAndProcessCpuClocksThroughProductionPal() {
        assumeTrue(System.getProperty("os.name").contains("windows", ignoreCase = true))
        val manifest = requireNotNull(SdkManifestLoader.load(SdkManifestLocator.defaultManifestPath()).manifest)
        val target = TargetInfo(targetTriple = "windows-x86_64")
        val resolution = requireNotNull(SdkResolver.resolve(manifest, target).resolution)
        val plan = requireNotNull(RuntimeLinker.plan(resolution, target).plan)
        val directory = Files.createTempDirectory("cplus-windows-runtime-clocks")
        val source = directory.resolve("clock_test.c").also {
            Files.writeString(it, """
                #include "cplus_platform.h"
                #include <time.h>

                int main(void) {
                    long long wall = platform_clock_wall_nanoseconds();
                    long long monotonic = platform_clock_monotonic_nanoseconds();
                    long long cpu_before = platform_clock_process_cpu_nanoseconds();
                    long long cpu_after;
                    time_t stored = -1;
                    time_t seconds = time(&stored);
                    clock_t ticks;
                    volatile unsigned long long work = 0;
                    unsigned long long index;
                    if (wall <= 0 || monotonic < 0 || cpu_before < 0 || wall <= monotonic) return 1;
                    if (platform_clock_monotonic_nanoseconds() < monotonic) return 2;
                    if (seconds < 0 || stored != seconds || seconds < wall / 1000000000LL) return 3;
                    if (time((time_t*)0) < 0 || CLOCKS_PER_SEC != 1000000000LL) return 4;
                    for (index = 0; index < 3000000ULL; index++) work += index;
                    if (work == 0) return 5;
                    ticks = clock();
                    cpu_after = platform_clock_process_cpu_nanoseconds();
                    if (cpu_after < cpu_before || ticks < 0 || ticks > cpu_after) return 6;
                    if (platform_clock_ticks() < monotonic) return 7;
                    return 0;
                }
            """.trimIndent())
        }
        val executable = directory.resolve("clock_test.exe")
        try {
            val link = LinkDriver.link(LinkRequest(source, executable, target, resolution), plan)
            assertTrue(link.isSuccessful, link.output)
            val descriptor = resolution.targetDescriptor
                ?: requireNotNull(TargetRegistry.load(resolution.layout.abiDescriptor).descriptor)
            val audit = RuntimeDependencyAuditor.inspect(executable, descriptor, target.buildProfile)
            assertTrue(audit.isSuccessful, audit.diagnostics.joinToString())
            val process = ProcessBuilder(executable.toString()).redirectErrorStream(true).start()
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                process.waitFor(2, TimeUnit.SECONDS)
                throw AssertionError("Windows clock fixture timed out; artifacts at $directory")
            }
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(0, process.exitValue(), "Windows clock fixture failed: $output")
        } finally {
            Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }

    @Test
    fun linuxProvidesDistinctCheckedWallMonotonicAndProcessCpuNanoseconds() {
        assumeTrue(System.getProperty("os.name").contains("linux", ignoreCase = true))
        val root = SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize().parent!!.parent!!
        val directory = Files.createTempDirectory("cplus-runtime-clocks")
        val source = directory.resolve("clock_test.c").also {
            Files.writeString(it, """
                #include "cplus_platform.h"
                #include <time.h>
                #include "runtime.c"

                int main(int argc, char** argv) {
                    long long wall;
                    long long monotonic;
                    long long cpu_before;
                    long long cpu_after;
                    time_t stored_time = -1;
                    time_t first_time;
                    clock_t libc_clock;
                    volatile unsigned long long work = 0;
                    unsigned long long index;
                    (void)argc;
                    (void)argv;
                    if (CPLUS_PAL_API_VERSION != 4) return 13;
                    if (cplus_linux_timespec_nanoseconds(1, 7) != 1000000007LL) return 1;
                    if (cplus_linux_timespec_nanoseconds(9223372036LL, 854775807LL) != 9223372036854775807LL) return 2;
                    if (cplus_linux_timespec_nanoseconds(9223372037LL, 0) != CPLUS_PAL_IO_ERROR) return 3;
                    if (cplus_linux_timespec_nanoseconds(1, 1000000000LL) != CPLUS_PAL_IO_ERROR) return 4;
                    if (cplus_linux_timespec_nanoseconds(-1, 0) != CPLUS_PAL_IO_ERROR) return 12;
                    wall = platform_clock_wall_nanoseconds();
                    monotonic = platform_clock_monotonic_nanoseconds();
                    cpu_before = platform_clock_process_cpu_nanoseconds();
                    if (wall < 0 || monotonic < 0 || cpu_before < 0 || wall <= monotonic) return 5;
                    if (platform_clock_monotonic_nanoseconds() < monotonic) return 6;
                    first_time = time(&stored_time);
                    if (first_time < 0 || stored_time != first_time ||
                        stored_time < wall / 1000000000LL || time((time_t*)0) < 0) return 7;
                    if (CLOCKS_PER_SEC != 1000000000LL) return 8;
                    for (index = 0; index < 5000000ULL; index++) work += index;
                    if (work == 0) return 9;
                    libc_clock = clock();
                    cpu_after = platform_clock_process_cpu_nanoseconds();
                    if (cpu_after < cpu_before || libc_clock < 0 || libc_clock > cpu_after) return 10;
                    if (platform_clock_ticks() < monotonic) return 11;
                    return 0;
                }
            """.trimIndent())
        }
        val executable = directory.resolve("clock_test")
        try {
            val compile = ProcessBuilder(
                "cc", "-std=c17", "-ffreestanding", "-fno-builtin", "-fno-stack-protector",
                "-fno-pie", "-nostdlib", "-static", "-Wl,-e,_start",
                "-Wl,-T,${root.resolve("platform/linux/thread-tls.ld")}",
                "-I", root.resolve("libc/include").toString(),
                "-I", root.resolve("runtime/include").toString(),
                "-I", root.resolve("platform/linux").toString(),
                source.toString(), root.resolve("startup/linux-x86_64/start.S").toString(),
                root.resolve("runtime/src/startup.c").toString(),
                root.resolve("runtime/src/time.c").toString(), "-o", executable.toString()
            ).redirectErrorStream(true).start()
            val compileOutput = compile.inputStream.bufferedReader().readText()
            assertEquals(0, compile.waitFor(), compileOutput)
            val undefinedSymbols = ProcessBuilder("nm", "-u", executable.toString()).start()
            val undefinedOutput = undefinedSymbols.inputStream.bufferedReader().readText()
            assertEquals(0, undefinedSymbols.waitFor(), undefinedOutput)
            assertTrue(undefinedOutput.isBlank(), undefinedOutput)
            assertEquals(0, ProcessBuilder(executable.toString()).start().waitFor())
        } finally {
            Files.deleteIfExists(executable)
            Files.deleteIfExists(source)
            Files.deleteIfExists(directory)
        }
    }
}
