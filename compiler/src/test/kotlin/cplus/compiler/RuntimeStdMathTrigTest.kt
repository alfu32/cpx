package cplus.compiler

import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

class RuntimeStdMathTrigTest {
    @Test
    fun c17TrigonometricAndHyperbolicFamiliesUseTheRuntime() {
        assumeTrue(System.getProperty("os.name").contains("linux", ignoreCase = true))
        val manifestPath = SdkManifestLocator.defaultManifestPath()
        val manifest = requireNotNull(SdkManifestLoader.load(manifestPath).manifest)
        val target = TargetInfo(targetTriple = "linux-x86_64")
        val resolution = requireNotNull(SdkResolver.resolve(manifest, target).resolution)
        val plan = requireNotNull(RuntimeLinker.plan(resolution, target).plan)
        val directory = Files.createTempDirectory("cplus-math-trig")
        val source = directory.resolve("math_trig.c").also {
            Files.writeString(it, """
                #include <errno.h>
                #include <math.h>

                int main(void) {
                    double pi = 3.14159265358979323846;
                    double half_pi = 1.57079632679489661923;

                    if (sin(0.0) != 0.0 || !signbit(sin(-0.0)) || cos(0.0) != 1.0) return 1;
                    if (fabs(sin(0.5) - 0.479425538604203) > 0.000000000001 ||
                        fabs(cos(0.5) - 0.877582561890373) > 0.000000000001 ||
                        fabs(tan(0.5) - 0.546302489843790) > 0.000000000001) return 2;
                    if (fabsf(sinf(0.5f) - 0.47942555f) > 0.00001f ||
                        fabsl(sinl(-0.5L) + 0.4794255386042030003L) > 0.000000000001L) return 3;
                    if (fabs(sin(1000000.0) + 0.349993502171293) > 0.000000001 ||
                        fabs(cos(1000000.0) - 0.936752127533145) > 0.000000001) return 4;
                    if (sin(half_pi) < 0.999999999999 || !isfinite(tan(half_pi)) ||
                        fabs(tan(half_pi) - 16331239353195370.0) > 100000000.0) return 5;
                    if (fabs(asin(0.5) - 0.523598775598299) > 0.000000000001 ||
                        fabs(acos(0.5) - 1.047197551196598) > 0.000000000001 ||
                        fabs(atan(1.0) - 0.785398163397448) > 0.000000000001) return 6;
                    if (fabs(atan2(1.0, -1.0) - 2.356194490192345) > 0.000000000001 ||
                        fabs(atan2(-1.0, -1.0) + 2.356194490192345) > 0.000000000001 ||
                        fabs(atan2(0.0, -1.0) - pi) > 0.000000000001 ||
                        fabs(atan2(-0.0, -1.0) + pi) > 0.000000000001 ||
                        !signbit(atan2(-0.0, 1.0))) return 7;

                    if (fabs(sinh(1.0) - 1.175201193643801) > 0.000000000001 ||
                        fabs(cosh(1.0) - 1.543080634815244) > 0.000000000001 ||
                        fabs(tanh(1.0) - 0.761594155955765) > 0.000000000001) return 8;
                    if (fabsf(sinhf(1.0f) - 1.1752012f) > 0.00001f ||
                        fabsl(coshl(1.0L) - 1.5430806348152437785L) > 0.000000000001L ||
                        !signbit(sinh(-0.0)) || tanh(-HUGE_VAL) != -1.0) return 9;
                    if (fabs(asinh(1.0) - 0.881373587019543) > 0.000000000001 ||
                        fabs(acosh(2.0) - 1.316957896924817) > 0.000000000001 ||
                        fabs(atanh(0.5) - 0.549306144334055) > 0.000000000001) return 10;
                    if (!isinf(asinh(HUGE_VAL)) || acosh(1.0) != 0.0 ||
                        fabsl(atanhl(0.5L) - 0.5493061443340548457L) > 0.000000000001L ||
                        !isnan(cosh(NAN))) return 11;

                    errno = 55;
                    if (!isnan(sin(HUGE_VAL)) || errno != EDOM) return 12;
                    errno = 55;
                    if (!isnan(asin(2.0)) || errno != EDOM) return 13;
                    errno = 55;
                    if (!isnan(acos(-2.0)) || errno != EDOM) return 14;
                    errno = 55;
                    if (!isnan(acosh(0.5)) || errno != EDOM) return 15;
                    errno = 55;
                    if (atanh(1.0) != HUGE_VAL || errno != ERANGE) return 16;
                    errno = 55;
                    if (!isnan(atanh(2.0)) || errno != EDOM) return 17;
                    return 0;
                }
            """.trimIndent())
        }
        val executable = directory.resolve("math-trig")
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
                throw AssertionError("C17 math trigonometric fixture timed out; artifacts at $directory")
            }
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(0, process.exitValue(), "trig fixture returned ${process.exitValue()}: $output")
        } finally {
            Files.deleteIfExists(executable)
            Files.deleteIfExists(source)
            Files.deleteIfExists(directory)
        }
    }
}
