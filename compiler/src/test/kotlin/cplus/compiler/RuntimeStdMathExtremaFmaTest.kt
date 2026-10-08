package cplus.compiler

import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

class RuntimeStdMathExtremaFmaTest {
    @Test
    fun c17PositiveDifferenceExtremaAndFmaUseTheRuntime() {
        assumeTrue(System.getProperty("os.name").contains("linux", ignoreCase = true))
        val manifestPath = SdkManifestLocator.defaultManifestPath()
        val manifest = requireNotNull(SdkManifestLoader.load(manifestPath).manifest)
        val target = TargetInfo(targetTriple = "linux-x86_64")
        val resolution = requireNotNull(SdkResolver.resolve(manifest, target).resolution)
        val plan = requireNotNull(RuntimeLinker.plan(resolution, target).plan)
        val directory = Files.createTempDirectory("cplus-math-extrema-fma")
        val source = directory.resolve("math_extrema_fma.c").also {
            Files.writeString(it, """
                #include <errno.h>
                #include <math.h>

                int main(void) {
                    double largest_double = 0x1.fffffffffffffp+1023;
                    if (fdim(7.0, 3.0) != 4.0 || fdim(3.0, 7.0) != 0.0 ||
                        fdim(-0.0, 0.0) != 0.0 || signbit(fdim(-0.0, 0.0))) return 1;
                    if (fmax(-0.0, 0.0) != 0.0 || signbit(fmax(-0.0, 0.0)) ||
                        fmin(-0.0, 0.0) != 0.0 || !signbit(fmin(-0.0, 0.0))) return 2;
                    if (fmax(NAN, 4.0) != 4.0 || fmin(4.0, NAN) != 4.0 ||
                        !isnan(fmax(NAN, NAN)) || !isnan(fmin(NAN, NAN))) return 3;
                    if (fdimf(7.0f, 3.0f) != 4.0f || fmaxf(NAN, 2.0f) != 2.0f ||
                        fminl(-0.0L, 0.0L) != 0.0L || !signbit(fminl(-0.0L, 0.0L))) return 4;

                    double simple_fma = fma(1.0, 1.0, 1.0);
                    if (simple_fma != 2.0) return 58;
                    double fused_probe = fma(0x1.0000000000001p0, 0x1.0000000000001p0,
                            -0x1.0000000000002p0);
                    if (fused_probe != 0x1p-104) return 54;
                    if (fma(0x1.0000002p0, 0x1.ffffffcp-1, -1.0) != -0x1p-54) return 6;
                    if (fmaf(0x1.001p0f, 0x1.ffep-1f, -1.0f) != -0x1p-24f) return 7;
                    if (fmal(0x1.00000001p0L, 1.0L - 0x1p-32L, -1.0L) != -0x1p-64L) return 8;
                    if (fma(1.0, 1.0, -1.0) != 0.0 || signbit(fma(1.0, 1.0, -1.0))) return 9;
                    if (fma(1.0, 1.0, 0x1p-53) != 1.0 ||
                        fma(1.0, 1.0, 0x1p-52) != 0x1.0000000000001p0 ||
                        fma(1.0, 1.0, 0x1p-1074) != 1.0) return 21;

                    errno = 55;
                    if (fma(0x1p-537, 0x1p-537, 0.0) != 0x1p-1074 || errno != 55) return 10;
                    errno = 55;
                    if (fma(0x1p-538, 0x1p-538, 0.0) != 0.0 || errno != ERANGE) return 11;
                    errno = 55;
                    if (fma(largest_double, 2.0, -largest_double) != largest_double || errno != 55) return 12;
                    errno = 55;
                    if (!isinf(fma(largest_double, 2.0, 0.0)) || errno != ERANGE) return 13;
                    errno = 55;
                    if (!isinf(fdim(largest_double, -largest_double)) || errno != ERANGE) return 22;
                    errno = 55;
                    if (!isnan(fma(HUGE_VAL, 0.0, 1.0)) || errno != EDOM) return 14;
                    errno = 55;
                    if (!isnan(fma(HUGE_VAL, 1.0, -HUGE_VAL)) || errno != EDOM) return 15;
                    return 0;
                }
            """.trimIndent())
        }
        val executable = directory.resolve("math-extrema-fma")
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
                throw AssertionError("C17 math extrema/FMA fixture timed out; artifacts at $directory")
            }
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(0, process.exitValue(), "math fixture returned ${process.exitValue()}: $output")
        } finally {
            Files.deleteIfExists(executable)
            Files.deleteIfExists(source)
            Files.deleteIfExists(directory)
        }
    }
}
