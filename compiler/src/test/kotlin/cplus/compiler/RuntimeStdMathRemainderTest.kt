package cplus.compiler

import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

class RuntimeStdMathRemainderTest {
    @Test
    fun c17RemainderFamiliesUseTheRuntimeWithoutHostMathSymbols() {
        assumeTrue(System.getProperty("os.name").contains("linux", ignoreCase = true))
        val manifestPath = SdkManifestLocator.defaultManifestPath()
        val manifest = requireNotNull(SdkManifestLoader.load(manifestPath).manifest)
        val target = TargetInfo(targetTriple = "linux-x86_64")
        val resolution = requireNotNull(SdkResolver.resolve(manifest, target).resolution)
        val plan = requireNotNull(RuntimeLinker.plan(resolution, target).plan)
        val directory = Files.createTempDirectory("cplus-math-remainder")
        val source = directory.resolve("math_remainder.c").also {
            Files.writeString(it, """
                #include <errno.h>
                #include <math.h>

                int main(void) {
                    int quotient = 99;
                    int dividend_integer;
                    int divisor_integer;
                    int truncated_quotient;
                    int expected_quotient_low;
                    int expected_remainder;
                    int expected_fmod;
                    int divisor_magnitude;
                    int remainder_magnitude;
                    float float_result;
                    double double_result;
                    long double long_result;

                    if (fmodf(5.5f, 2.0f) != 1.5f || fmod(-5.5, 2.0) != -1.5 ||
                        fmodl(5.0L, -2.0L) != 1.0L) return 1;
                    if (remainderf(5.5f, 2.0f) != -0.5f || remainder(3.0, 2.0) != -1.0 ||
                        remainderl(5.0L, 2.0L) != 1.0L || remainder(7.0, 2.0) != -1.0 ||
                        remainder(-7.0, 2.0) != 1.0) return 2;

                    if (remquof(29.0f, 3.0f, &quotient) != -1.0f || quotient != 2) return 3;
                    if (remquo(-29.0, 3.0, &quotient) != 1.0 || quotient != -2) return 4;
                    if (remquol(29.0L, -3.0L, &quotient) != -1.0L || quotient != -2) return 5;
                    if (remquo(5.0, 2.0, &quotient) != 1.0 || quotient != 2) return 6;
                    if (remquo(7.0, 2.0, &quotient) != -1.0 || quotient != 4) return 7;
                    if (remquo(123456789.0, 1.0, &quotient) != 0.0 || quotient != 5) return 8;

                    float_result = fmodf(-4.0f, 2.0f);
                    double_result = remainder(-4.0, 2.0);
                    long_result = remquol(-4.0L, 2.0L, &quotient);
                    if (!signbit(float_result) || !signbit(double_result) ||
                        !signbit(long_result) || quotient != -2) return 9;

                    if (fmod(0x1p100, 3.0) != 1.0) return 10;
                    if (remquo(0x1p100, 3.0, &quotient) != 1.0 || quotient != 5 ||
                        remquol(0x1p16000L, 3.0L, &quotient) != 1.0L || quotient != 5) return 22;
                    if (fmod(0x1p-1073, 0x1p-1074) != 0.0 ||
                        remquo(0x1p-1073, 0x1p-1074, &quotient) != 0.0 || quotient != 2) return 11;
                    if (remainder(0x1p-1073, 0x1.8p-1073) != -0x1p-1074) return 12;
                    if (remainderl(0x1p-16444L, 0x1.8p-16444L) != -0x1p-16445L) return 23;

                    if (remainder(1.0, HUGE_VAL) != 1.0 ||
                        remquo(1.0, HUGE_VAL, &quotient) != 1.0 || quotient != 0) return 13;
                    errno = 55;
                    if (!isnan(fmod(1.0, 0.0)) || errno != EDOM) return 14;
                    errno = 55;
                    if (!isnan(remainder(HUGE_VAL, 1.0)) || errno != EDOM) return 15;
                    errno = 55;
                    if (!isnan(remquo(1.0, 0.0, &quotient)) || errno != EDOM || quotient != 0) return 16;
                    errno = 55;
                    if (!isnan(fmod(NAN, 1.0)) || errno != 55) return 17;
                    if (!isnan(remainder(1.0, NAN))) return 18;

                    for (dividend_integer = -32; dividend_integer <= 32; dividend_integer++) {
                        for (divisor_integer = -9; divisor_integer <= 9; divisor_integer++) {
                            if (divisor_integer == 0) continue;
                            truncated_quotient = dividend_integer / divisor_integer;
                            expected_fmod = dividend_integer % divisor_integer;
                            divisor_magnitude = divisor_integer < 0 ? -divisor_integer : divisor_integer;
                            remainder_magnitude = expected_fmod < 0 ? -expected_fmod : expected_fmod;
                            if (remainder_magnitude * 2 > divisor_magnitude ||
                                (remainder_magnitude * 2 == divisor_magnitude &&
                                    truncated_quotient % 2 != 0)) {
                                truncated_quotient +=
                                    ((dividend_integer < 0) != (divisor_integer < 0)) ? -1 : 1;
                            }
                            expected_quotient_low = truncated_quotient % 8;
                            expected_remainder = dividend_integer - truncated_quotient * divisor_integer;
                            if (fmodf((float)dividend_integer, (float)divisor_integer) !=
                                    (float)expected_fmod ||
                                fmod((double)dividend_integer, (double)divisor_integer) !=
                                    (double)expected_fmod ||
                                fmodl((long double)dividend_integer, (long double)divisor_integer) !=
                                    (long double)expected_fmod) return 19;
                            if (remainderf((float)dividend_integer, (float)divisor_integer) !=
                                    (float)expected_remainder ||
                                remainder((double)dividend_integer, (double)divisor_integer) !=
                                    (double)expected_remainder ||
                                remainderl((long double)dividend_integer, (long double)divisor_integer) !=
                                    (long double)expected_remainder) return 20;
                            if (remquof((float)dividend_integer, (float)divisor_integer, &quotient) !=
                                    (float)expected_remainder || quotient != expected_quotient_low ||
                                remquo((double)dividend_integer, (double)divisor_integer, &quotient) !=
                                    (double)expected_remainder || quotient != expected_quotient_low ||
                                remquol((long double)dividend_integer, (long double)divisor_integer,
                                    &quotient) != (long double)expected_remainder ||
                                    quotient != expected_quotient_low) return 21;
                        }
                    }
                    return 0;
                }
            """.trimIndent())
        }
        val executable = directory.resolve("math-remainder")
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
                throw AssertionError("C17 math remainder fixture timed out; artifacts at $directory")
            }
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(0, process.exitValue(), "remainder fixture returned ${process.exitValue()}: $output")
        } finally {
            Files.deleteIfExists(executable)
            Files.deleteIfExists(source)
            Files.deleteIfExists(directory)
        }
    }
}
