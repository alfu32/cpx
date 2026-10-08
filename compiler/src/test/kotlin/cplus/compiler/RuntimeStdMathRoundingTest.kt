package cplus.compiler

import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

class RuntimeStdMathRoundingTest {
    @Test
    fun c17RoundingAndIntegerConversionsUseTargetRangeAndRoundingRules() {
        assumeTrue(System.getProperty("os.name").contains("linux", ignoreCase = true))
        val manifestPath = SdkManifestLocator.defaultManifestPath()
        val manifest = requireNotNull(SdkManifestLoader.load(manifestPath).manifest)
        val target = TargetInfo(targetTriple = "linux-x86_64")
        val resolution = requireNotNull(SdkResolver.resolve(manifest, target).resolution)
        val plan = requireNotNull(RuntimeLinker.plan(resolution, target).plan)
        val directory = Files.createTempDirectory("cplus-math-rounding")
        val source = directory.resolve("math_rounding.c").also {
            Files.writeString(it, """
                #include <errno.h>
                #include <limits.h>
                #include <math.h>

                _Static_assert(sizeof(long) == 8, "Linux x86_64 long width");
                _Static_assert(sizeof(long long) == 8, "long long width");
                _Static_assert(LONG_MAX == 9223372036854775807L, "LP64 LONG_MAX");
                _Static_assert(LONG_MIN == (-9223372036854775807L - 1L), "LP64 LONG_MIN");

                int main(void) {
                    if (truncf(3.75f) != 3.0f || trunc(-3.75) != -3.0 || truncl(3.75L) != 3.0L) return 1;
                    if (ceilf(2.25f) != 3.0f || ceil(-2.25) != -2.0 || ceill(2.25L) != 3.0L) return 2;
                    if (floorf(-2.25f) != -3.0f || floor(2.25) != 2.0 || floorl(-2.25L) != -3.0L) return 3;
                    if (roundf(2.5f) != 3.0f || round(-2.5) != -3.0 || roundl(1.5L) != 2.0L) return 4;
                    if (rintf(2.5f) != 2.0f || rint(3.5) != 4.0 || rintl(-2.5L) != -2.0L) return 5;
                    if (nearbyintf(3.5f) != 4.0f || nearbyint(2.5) != 2.0 || nearbyintl(-3.5L) != -4.0L) return 6;

                    if (!signbit(truncf(-0.75f)) || !signbit(ceil(-0.75)) ||
                        !signbit(roundl(-0.25L)) || !signbit(nearbyint(-0.25)) ||
                        signbit(floor(0.75))) return 7;
                    if (fpclassify(trunc(HUGE_VAL)) != FP_INFINITE ||
                        fpclassify(round(NAN)) != FP_NAN || fpclassify(rintl(-HUGE_VALL)) != FP_INFINITE) return 8;

                    if (lrintf(2.5f) != 2 || lrint(3.5) != 4 || lrintl(-2.5L) != -2) return 9;
                    if (lroundf(2.5f) != 3 || lround(-2.5) != -3 || lroundl(3.5L) != 4) return 10;
                    if (llrintf(-3.5f) != -4 || llrint(2.5) != 2 || llrintl(-2.5L) != -2) return 11;
                    if (llroundf(-2.5f) != -3 || llround(2.5) != 3 || llroundl(-3.5L) != -4) return 12;

                    errno = 55;
                    if (lrint(-0x1p63) != LONG_MIN || errno != 55) return 13;
                    if (llrint(-0x1p63) != LLONG_MIN || errno != 55) return 14;
                    errno = 0;
                    if (lrint(0x1p63) != LONG_MIN || errno != EDOM) return 15;
                    errno = 0;
                    if (llround(0x1p63) != LLONG_MIN || errno != EDOM) return 16;
                    errno = 0;
                    if (llrint(NAN) != LLONG_MIN || errno != EDOM) return 17;
                    errno = 0;
                    if (lround(HUGE_VAL) != LONG_MIN || errno != EDOM) return 18;
                    return 0;
                }
            """.trimIndent())
        }
        val executable = directory.resolve("math-rounding")
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
                throw AssertionError("C17 math rounding fixture timed out; artifacts at $directory")
            }
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(0, process.exitValue(), "rounding fixture returned ${process.exitValue()}: $output")
        } finally {
            Files.deleteIfExists(executable)
            Files.deleteIfExists(source)
            Files.deleteIfExists(directory)
        }
    }
}
