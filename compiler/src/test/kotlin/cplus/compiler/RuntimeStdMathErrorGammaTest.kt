package cplus.compiler

import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

class RuntimeStdMathErrorGammaTest {
    @Test
    fun c17ErrorAndGammaFamiliesUseTheRuntime() {
        assumeTrue(System.getProperty("os.name").contains("linux", ignoreCase = true))
        val manifestPath = SdkManifestLocator.defaultManifestPath()
        val manifest = requireNotNull(SdkManifestLoader.load(manifestPath).manifest)
        val target = TargetInfo(targetTriple = "linux-x86_64")
        val resolution = requireNotNull(SdkResolver.resolve(manifest, target).resolution)
        val plan = requireNotNull(RuntimeLinker.plan(resolution, target).plan)
        val directory = Files.createTempDirectory("cplus-math-error-gamma")
        val source = directory.resolve("math_error_gamma.c").also {
            Files.writeString(it, """
                #include <errno.h>
                #include <math.h>

                int main(void) {
                    if (erf(0.0) != 0.0 || !signbit(erf(-0.0))) return 1;
                    if (fabs(erf(1.0) - 0.8427007929497149) > 0.000000000001 ||
                        fabs(erfc(1.0) - 0.1572992070502851) > 0.000000000001 ||
                        fabs(erf(-1.0) + 0.8427007929497149) > 0.000000000001) return 2;
                    if (fabs(erfc(-1.0) - 1.8427007929497148) > 0.000000000001 ||
                        fabs(erfc(5.0) - 1.5374597944280351e-12) > 0.000000000000001) return 3;
                    if (fabsf(erff(1.0f) - 0.84270078f) > 0.00001f ||
                        fabsf(erfcf(1.0f) - 0.15729921f) > 0.00001f ||
                        fabsl(erfl(1.0L) - 0.84270079294971486934L) > 0.000000000001L ||
                        fabsl(erfcl(1.0L) - 0.15729920705028513066L) > 0.000000000001L ||
                        !signbit(erfl(-0.0L))) return 4;
                    if (erf(HUGE_VAL) != 1.0 || erfc(HUGE_VAL) != 0.0 ||
                        erfc(-HUGE_VAL) != 2.0 || !isnan(erf(NAN))) return 5;

                    if (fabs(lgamma(0.5) - 0.5723649429247001) > 0.000000000001 ||
                        fabs(lgamma(-0.5) - 1.2655121234846454) > 0.000000000001 ||
                        fabs(lgamma(5.0) - 3.1780538303479458) > 0.000000000001) return 6;
                    if (fabs(tgamma(0.5) - 1.772453850905516) > 0.000000000001 ||
                        fabs(tgamma(5.0) - 24.0) > 0.000000000001 ||
                        fabs(tgamma(-0.5) + 3.544907701811032) > 0.000000000001 ||
                        fabs(tgamma(-1.5) - 2.363271801207355) > 0.000000000001) return 7;
                    if (fabsf(tgammaf(0.5f) - 1.7724539f) > 0.00001f ||
                        fabsf(lgammaf(0.5f) - 0.5723649f) > 0.00001f ||
                        fabsl(tgammal(0.5L) - 1.7724538509055160273L) > 0.000000000001L ||
                        fabsl(tgammal(-0.5L) + 3.5449077018110320546L) > 0.000000000001L ||
                        fabsl(lgammal(-0.5L) - 1.2655121234846453965L) > 0.000000000001L) return 8;
                    if (tgamma(1.0) != 1.0) return 91;
                    if (tgamma(2.0) != 1.0) return 92;
                    if (lgamma(1.0) != 0.0) return 93;
                    if (lgamma(2.0) != 0.0) return 94;
                    if (!isinf(lgamma(HUGE_VAL))) return 95;
                    if (!isnan(tgamma(NAN))) return 96;

                    errno = 55;
                    if (!isnan(tgamma(-2.0)) || errno != EDOM) return 10;
                    errno = 55;
                    if (!isinf(lgamma(-3.0)) || errno != ERANGE) return 11;
                    errno = 55;
                    if (tgamma(0.0) != HUGE_VAL || errno != ERANGE) return 12;
                    errno = 55;
                    if (tgamma(-0.0) != -HUGE_VAL || errno != ERANGE) return 13;
                    errno = 55;
                    if (!isnan(tgamma(-HUGE_VAL)) || errno != EDOM) return 14;
                    errno = 55;
                    if (!isnan(lgamma(-HUGE_VAL)) || errno != EDOM) return 15;
                    errno = 55;
                    if (!isinf(tgamma(200.0)) || errno != ERANGE) return 16;
                    errno = 55;
                    if (tgamma(-201.5) != 0.0 || errno != ERANGE || signbit(tgamma(-201.5))) return 17;
                    errno = 55;
                    if (erfc(30.0) != 0.0 || errno != ERANGE) return 18;
                    errno = 55;
                    if (erf(30.0) != 1.0 || errno != 55) return 19;
                    errno = 55;
                    if (erf(10.0) != 1.0 || errno != 55 || erfc(-10.0) != 2.0 || errno != 55) return 20;
                    return 0;
                }
            """.trimIndent())
        }
        val executable = directory.resolve("math-error-gamma")
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
                throw AssertionError("C17 math error/gamma fixture timed out; artifacts at $directory")
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
