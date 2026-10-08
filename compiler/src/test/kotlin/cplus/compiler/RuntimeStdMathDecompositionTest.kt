package cplus.compiler

import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

class RuntimeStdMathDecompositionTest {
    @Test
    fun c17DecompositionScalingSignNanAndAdjacentValueFunctionsUseTheRuntime() {
        assumeTrue(System.getProperty("os.name").contains("linux", ignoreCase = true))
        val manifestPath = SdkManifestLocator.defaultManifestPath()
        val manifest = requireNotNull(SdkManifestLoader.load(manifestPath).manifest)
        val target = TargetInfo(targetTriple = "linux-x86_64")
        val resolution = requireNotNull(SdkResolver.resolve(manifest, target).resolution)
        val plan = requireNotNull(RuntimeLinker.plan(resolution, target).plan)
        val directory = Files.createTempDirectory("cplus-math-decomposition")
        val source = directory.resolve("math_decomposition.c").also {
            Files.writeString(it, """
                #include <errno.h>
                #include <limits.h>
                #include <math.h>

                static int same_bytes(const unsigned char* left, const unsigned char* right, unsigned int size) {
                    unsigned int index;
                    for (index = 0; index < size; index++) if (left[index] != right[index]) return 0;
                    return 1;
                }

                int main(void) {
                    int exponent = 77;
                    float float_integral = 0.0f;
                    double double_integral = 0.0;
                    long double long_integral = 0.0L;
                    float float_fraction;
                    double double_fraction;
                    long double long_fraction;
                    double tagged_nan_a;
                    double tagged_nan_b;

                    if (ilogbf(8.0f) != 3 || ilogb(0.5) != -1 || ilogbl(16.0L) != 4) return 1;
                    if (ilogbf(0x1p-149f) != -149 || ilogb(0x1p-1074) != -1074 ||
                        ilogbl(0x1p-16445L) != -16445) return 2;
                    errno = 55;
                    if (ilogb(0.0) != FP_ILOGB0 || errno != EDOM) return 3;
                    errno = 55;
                    if (ilogbl(NAN) != FP_ILOGBNAN || errno != EDOM) return 4;
                    errno = 55;
                    if (ilogbf(HUGE_VALF) != INT_MAX || errno != 55) return 5;

                    if (frexpf(8.0f, &exponent) != 0.5f || exponent != 4) return 6;
                    if (frexp(0.75, &exponent) != 0.75 || exponent != 0) return 7;
                    if (frexpl(0x1p-16445L, &exponent) != 0.5L || exponent != -16444) return 8;
                    if (!signbit(frexpl(-0.0L, &exponent)) || exponent != 0) return 9;
                    if (fpclassify(frexpl(HUGE_VALL, &exponent)) != FP_INFINITE || exponent != 0) return 10;

                    float_fraction = modff(-3.75f, &float_integral);
                    double_fraction = modf(4.25, &double_integral);
                    long_fraction = modfl(-5.0L, &long_integral);
                    if (float_fraction != -0.75f || float_integral != -3.0f ||
                        double_fraction != 0.25 || double_integral != 4.0 ||
                        !signbit(long_fraction) || long_integral != -5.0L) return 11;
                    long_fraction = modfl(-HUGE_VALL, &long_integral);
                    if (!signbit(long_fraction) || fpclassify(long_integral) != FP_INFINITE ||
                        !signbit(long_integral)) return 12;
                    double_fraction = modf(NAN, &double_integral);
                    if (!isnan(double_fraction) || !isnan(double_integral)) return 13;

                    if (ldexpf(0.5f, 4) != 8.0f || scalbn(1.5, 3) != 12.0 ||
                        scalblnl(0.5L, 4) != 8.0L) return 14;
                    if (ldexp(0x1p-1074, 1) != 0x1p-1073) return 15;
                    errno = 55;
                    if (scalbn(0.0, INT_MAX) != 0.0 || errno != 55) return 16;
                    errno = 0;
                    if (ldexp(1.0, -2000) != 0.0 || errno != ERANGE) return 17;
                    errno = 0;
                    if (scalbn(1.0, 2048) != HUGE_VAL || errno != ERANGE) return 18;
                    errno = 55;
                    if (scalbln(0.0, LONG_MAX) != 0.0 || errno != 55) return 29;
                    errno = 0;
                    if (scalbln(1.0, LONG_MAX) != HUGE_VAL || errno != ERANGE) return 30;
                    errno = 0;
                    if (!signbit(scalbln(-1.0, LONG_MIN)) ||
                        scalbln(-1.0, LONG_MIN) != 0.0 || errno != ERANGE) return 31;

                    if (copysignf(1.0f, -0.0f) != -1.0f || copysign(-0.0, 1.0) != 0.0 ||
                        signbit(copysignl(NAN, -1.0L)) == 0) return 19;
                    if (!isnan(nanf("float-tag")) || !isnan(nan("double-tag")) ||
                        !isnan(nanl("long-tag"))) return 20;
                    tagged_nan_a = nan("alpha");
                    tagged_nan_b = nan("beta");
                    if (fpclassify(tagged_nan_a) != FP_NAN || fpclassify(tagged_nan_b) != FP_NAN ||
                        same_bytes((const unsigned char*)&tagged_nan_a,
                            (const unsigned char*)&tagged_nan_b, sizeof(tagged_nan_a))) return 21;

                    if (nextafterf(1.0f, 2.0f) != 0x1.000002p+0f ||
                        nextafter(1.0, 0.0) != 0x1.fffffffffffffp-1 || nextafterl(1.0L, 2.0L) <= 1.0L) return 22;
                    if (nextafter(0.0, -1.0) >= 0.0 || fpclassify(nextafter(0.0, -1.0)) != FP_SUBNORMAL ||
                        nextafter(-0.0, 1.0) <= 0.0) return 23;
                    if (nextafter(0x1p-1074, 0.0) != 0.0 ||
                        nextafter(0x1.fffffffffffffp+1023, HUGE_VAL) != HUGE_VAL) return 24;
                    errno = 0;
                    if (nextafter(0x1.fffffffffffffp+1023, HUGE_VAL) != HUGE_VAL || errno != ERANGE) return 25;
                    if (nextafterl(0x1p-16382L, 0.0L) >= 0x1p-16382L ||
                        fpclassify(nextafterl(0x1p-16382L, 0.0L)) != FP_SUBNORMAL) return 26;
                    if (nextafterl(0.0L, 1.0L) != 0x1p-16445L ||
                        nextafterl(0x1p-16445L, 0.0L) != 0.0L) return 32;
                    if (!isfinite(nextafterl(HUGE_VALL, 0.0L)) ||
                        !isfinite(nextafterl(-HUGE_VALL, 0.0L)) ||
                        nextafterl(HUGE_VALL, 0.0L) <= 0.0L ||
                        nextafterl(-HUGE_VALL, 0.0L) >= 0.0L) return 33;

                    if (nexttoward(1.0, 1.0L + 0x1p-63L) <= 1.0 ||
                        nexttowardf(1.0f, 1.0L + 0x1p-63L) <= 1.0f ||
                        nexttowardl(1.0L, 1.0L + 0x1p-63L) <= 1.0L) return 27;
                    if (fpclassify(nextafter(NAN, 1.0)) != FP_NAN ||
                        fpclassify(nextafter(1.0, NAN)) != FP_NAN) return 28;
                    return 0;
                }
            """.trimIndent())
        }
        val executable = directory.resolve("math-decomposition")
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
                throw AssertionError("C17 math decomposition fixture timed out; artifacts at $directory")
            }
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(0, process.exitValue(), "decomposition fixture returned ${process.exitValue()}: $output")
        } finally {
            Files.deleteIfExists(executable)
            Files.deleteIfExists(source)
            Files.deleteIfExists(directory)
        }
    }
}
