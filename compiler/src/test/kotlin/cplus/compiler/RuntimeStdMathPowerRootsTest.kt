package cplus.compiler

import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

class RuntimeStdMathPowerRootsTest {
    @Test
    fun c17PowerRootAbsoluteAndHypotenuseFamiliesUseTheRuntime() {
        assumeTrue(System.getProperty("os.name").contains("linux", ignoreCase = true))
        val manifestPath = SdkManifestLocator.defaultManifestPath()
        val manifest = requireNotNull(SdkManifestLoader.load(manifestPath).manifest)
        val target = TargetInfo(targetTriple = "linux-x86_64")
        val resolution = requireNotNull(SdkResolver.resolve(manifest, target).resolution)
        val plan = requireNotNull(RuntimeLinker.plan(resolution, target).plan)
        val directory = Files.createTempDirectory("cplus-math-power-roots")
        val source = directory.resolve("math_power_roots.c").also {
            Files.writeString(it, """
                #include <errno.h>
                #include <math.h>

                int main(void) {
                    float float_root;
                    double double_root;
                    long double long_root;

                    if (fabsf(-3.0f) != 3.0f || fabs(-4.0) != 4.0 || fabsl(-5.0L) != 5.0L ||
                        signbit(fabs(-0.0))) return 1;
                    if (sqrtf(9.0f) != 3.0f || sqrt(0.25) != 0.5 || sqrtl(81.0L) != 9.0L ||
                        !signbit(sqrt(-0.0))) return 2;
                    if (sqrt(0x1p-1074) <= 0.0 || sqrtl(0x1p-16445L) <= 0.0L) return 3;
                    errno = 55;
                    if (!isnan(sqrt(-1.0)) || errno != EDOM) return 4;
                    errno = 55;
                    if (!isnan(sqrt(-HUGE_VALL)) || errno != EDOM) return 5;
                    errno = 55;
                    if (!isnan(sqrt(NAN)) || errno != 55) return 6;

                    if (cbrtf(-8.0f) != -2.0f || cbrt(27.0) != 3.0 ||
                        cbrtl(-125.0L) != -5.0L || !signbit(cbrt(-0.0))) return 7;
                    if (fabsf(hypotf(3.0f, 4.0f) - 5.0f) > 0.00001f) return 24;
                    if (fabs(hypot(5.0, 12.0) - 13.0) > 0.000000000001) return 25;
                    if (fabsl(hypotl(8.0L, 15.0L) - 17.0L) > 0.000000000001L) return 26;
                    if (!isfinite(hypot(0x1.fffffffffffffp+1022, 0x1.fffffffffffffp+1022)) ||
                        hypot(HUGE_VAL, NAN) != HUGE_VAL ||
                        signbit(hypot(copysign(NAN, -1.0), 2.0))) return 9;
                    errno = 0;
                    if (!isinf(hypot(0x1.fffffffffffffp+1023, 0x1.fffffffffffffp+1023)) ||
                        errno != ERANGE) return 10;

                    if (powf(2.0f, 10.0f) != 1024.0f || pow(-2.0, 3.0) != -8.0 ||
                        powl(2.0L, -3.0L) != 0.125L || pow(0.0, 0.0) != 1.0) return 11;
                    float_root = powf(9.0f, 0.5f);
                    double_root = pow(9.0, 0.5);
                    long_root = powl(9.0L, 0.5L);
                    if (fabsf(float_root - 3.0f) > 0.00001f ||
                        fabs(double_root - 3.0) > 0.000000000001 ||
                        fabsl(long_root - 3.0L) > 0.000000000001L) return 12;
                    if (fabs(pow(2.0, 0.5) - 1.4142135623730951) > 0.000000000001) return 13;
                    if (pow(1.0, NAN) != 1.0) return 27;
                    if (pow(NAN, 0.0) != 1.0) return 28;
                    if (pow(-1.0, HUGE_VAL) != 1.0) return 29;
                    if (pow(0.5, HUGE_VAL) != 0.0) return 30;
                    if (!isinf(pow(2.0, HUGE_VAL))) return 31;
                    errno = 55;
                    if (!isnan(pow(-2.0, 0.5)) || errno != EDOM) return 15;
                    errno = 0;
                    if (!isinf(pow(0.0, -2.0)) || errno != ERANGE) return 16;
                    errno = 0;
                    if (pow(2.0, 1024.0) != HUGE_VAL || errno != ERANGE) return 17;
                    errno = 0;
                    if (pow(0.5, 2000.0) != 0.0 || errno != ERANGE) return 18;
                    errno = 55;
                    if (pow(0.5, 1074.0) != 0x1p-1074 || errno != 55) return 32;
                    return 0;
                }
            """.trimIndent())
        }
        val executable = directory.resolve("math-power-roots")
        try {
            val link = LinkDriver.link(LinkRequest(source, executable, target, resolution), plan)
            assertTrue(link.isSuccessful, link.output)

            val undefinedSymbols = ProcessBuilder("nm", "-u", executable.toString()).start()
            val undefinedOutput = undefinedSymbols.inputStream.bufferedReader().readText()
            assertEquals(0, undefinedSymbols.waitFor(), undefinedOutput)
            assertTrue(undefinedOutput.isBlank(), "math fixture imports host symbols: $undefinedOutput")

            val process = ProcessBuilder(executable.toString()).redirectErrorStream(true).start()
            if (!process.waitFor(15, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                process.waitFor(2, TimeUnit.SECONDS)
                throw AssertionError("C17 math power/roots fixture timed out; artifacts at $directory")
            }
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(0, process.exitValue(), "power/roots fixture returned ${process.exitValue()}: $output")
        } finally {
            Files.deleteIfExists(executable)
            Files.deleteIfExists(source)
            Files.deleteIfExists(directory)
        }
    }
}
