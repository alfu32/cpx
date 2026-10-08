package cplus.compiler

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

class RuntimeStdMathClassificationTest {
    @Test
    fun mathRuntimeCompilesStrictlyForEveryDeclaredTargetFormat() {
        assumeTrue(System.getProperty("os.name").contains("linux", ignoreCase = true))
        val manifestPath = SdkManifestLocator.defaultManifestPath()
        val manifest = requireNotNull(SdkManifestLoader.load(manifestPath).manifest)
        val resolution = requireNotNull(SdkResolver.resolve(manifest, TargetInfo()).resolution)
        val root = manifestPath.toAbsolutePath().normalize().parent!!.parent!!
        val mathSource = root.resolve("runtime/src/math.c")
        val targetNames = listOf("linux-x86_64", "linux-aarch64", "windows-x86_64", "windows-aarch64")

        val clangAvailable = runCatching {
            ProcessBuilder("clang", "--version").redirectErrorStream(true).start().waitFor() == 0
        }.getOrDefault(false)
        assumeTrue(clangAvailable, "clang is required for four-target math source validation")

        targetNames.forEach { targetName ->
            val target = TargetInfo(targetTriple = targetName)
            val descriptor = requireNotNull(TargetRegistry.load(root.resolve("abi/$targetName.toml")).descriptor)
            val command = listOf("clang") + CCompilerToolchains.targetFlags(target, "clang") +
                listOf("-std=c17", "-Wall", "-Wextra", "-Wpedantic", "-Werror", "-fsyntax-only", "-I",
                    resolution.layout.libcInclude.toString()) +
                CCompilerToolchains.targetAbiFlags(descriptor, "clang") + listOf(mathSource.toString())
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(0, process.waitFor(), "$targetName math source failed strict C17 validation:\n$output")
        }
    }

    @Test
    fun binary128ClassificationRecognizesExponentAndFractionFieldsSeparately() {
        assumeTrue(System.getProperty("os.name").contains("linux", ignoreCase = true))
        val manifestPath = SdkManifestLocator.defaultManifestPath()
        val root = manifestPath.toAbsolutePath().normalize().parent!!.parent!!
        val directory = Files.createTempDirectory("cplus-math-binary128")
        val source = directory.resolve("binary128.c").also {
            Files.writeString(it, """
                _Thread_local int errno;
                #include "math.c"

                int main(void) {
                    unsigned char bits[16] = {0};
                    if (cplus_math_classify_binary128(bits) != FP_ZERO) return 1;
                    bits[0] = 1;
                    if (cplus_math_classify_binary128(bits) != FP_SUBNORMAL) return 2;
                    bits[0] = 0;
                    bits[14] = 0xff;
                    bits[15] = 0x3f;
                    if (cplus_math_classify_binary128(bits) != FP_NORMAL) return 3;
                    bits[14] = 0xff;
                    bits[15] = 0x7f;
                    if (cplus_math_classify_binary128(bits) != FP_INFINITE) return 4;
                    bits[0] = 1;
                    if (cplus_math_classify_binary128(bits) != FP_NAN) return 5;

                    bits[0] = 0;
                    bits[13] = 0x40;
                    bits[14] = 0x00;
                    bits[15] = 0x40;
                    cplus_math_truncate_representation(bits, 0x4000, 16383, 112, 15);
                    if (bits[13] != 0 || cplus_math_is_odd_representation(bits, 0x4000, 16383, 112, 0)) return 6;

                    bits[13] = 0xc0;
                    cplus_math_truncate_representation(bits, 0x4000, 16383, 112, 15);
                    if (bits[13] != 0x80 || !cplus_math_is_odd_representation(bits, 0x4000, 16383, 112, 0)) return 7;
                    return 0;
                }
            """.trimIndent())
        }
        val executable = directory.resolve("binary128")
        try {
            val command = listOf("cc", "-std=c17", "-fno-builtin", "-DCPLUS_LONG_DOUBLE_FORMAT=3",
                "-I", root.resolve("libc/include").toString(), "-I", root.resolve("runtime/src").toString(),
                source.toString(), "-o", executable.toString())
            val compilation = ProcessBuilder(command).redirectErrorStream(true).start()
            val output = compilation.inputStream.bufferedReader().readText()
            assertEquals(0, compilation.waitFor(), output)
            assertEquals(0, ProcessBuilder(executable.toString()).start().waitFor())
        } finally {
            Files.deleteIfExists(executable)
            Files.deleteIfExists(source)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun c17MathClassificationAndComparisonMacrosUseTheCplusRuntime() {
        assumeTrue(System.getProperty("os.name").contains("linux", ignoreCase = true))
        val manifestPath = SdkManifestLocator.defaultManifestPath()
        val manifest = requireNotNull(SdkManifestLoader.load(manifestPath).manifest)
        val target = TargetInfo(targetTriple = "linux-x86_64")
        val resolution = requireNotNull(SdkResolver.resolve(manifest, target).resolution)
        val plan = requireNotNull(RuntimeLinker.plan(resolution, target).plan)
        val directory = Files.createTempDirectory("cplus-math-classification")
        val source = directory.resolve("math_classification.c").also {
            Files.writeString(it, """
                #include <errno.h>
                #include <math.h>

                _Static_assert(FP_ZERO == 0, "FP_ZERO value");
                _Static_assert(FP_SUBNORMAL == 1, "FP_SUBNORMAL value");
                _Static_assert(FP_NORMAL == 2, "FP_NORMAL value");
                _Static_assert(FP_INFINITE == 3, "FP_INFINITE value");
                _Static_assert(FP_NAN == 4, "FP_NAN value");
                _Static_assert(FP_ILOGB0 == (-2147483647 - 1), "zero ilogb value");
                _Static_assert(FP_ILOGBNAN == 2147483647, "NaN ilogb value");
                _Static_assert(FLT_EVAL_METHOD == 0, "evaluation method");
                _Static_assert(_Generic((float_t)0, float: 1, default: 0), "float_t type");
                _Static_assert(_Generic((double_t)0, double: 1, default: 0), "double_t type");
                _Static_assert(_Generic(INFINITY, float: 1, default: 0), "INFINITY type");
                _Static_assert(_Generic(NAN, float: 1, default: 0), "NAN type");
                _Static_assert(MATH_ERRNO == 1 && MATH_ERREXCEPT == 2, "math error indicators");
                _Static_assert(math_errhandling == MATH_ERRNO, "errno-only math error policy");

                static int unary_calls;
                static int left_calls;
                static int right_calls;

                static double next_value(void) {
                    unary_calls++;
                    return 1.0;
                }

                static double next_left(void) {
                    left_calls++;
                    return 2.0;
                }

                static double next_right(void) {
                    right_calls++;
                    return 3.0;
                }

                int main(void) {
                    float float_subnormal = 0x1p-149f;
                    double double_subnormal = 0x1p-1074;
                    long double long_subnormal = 0x1p-16445L;
                    float float_nan = (float)NAN;
                    double double_nan = (double)NAN;
                    long double long_nan = (long double)NAN;
                    errno = 77;
                    if (sqrt(4.0) < 1.999999 || sqrt(4.0) > 2.000001 || errno != 77) return 16;
                    if (fpclassify(sqrt(-1.0)) != FP_NAN || errno != EDOM) return 17;
                    errno = 77;

                    if (fpclassify(0.0f) != FP_ZERO || fpclassify(0.0) != FP_ZERO ||
                        fpclassify(0.0L) != FP_ZERO) return 1;
                    if (fpclassify(float_subnormal) != FP_SUBNORMAL ||
                        fpclassify(double_subnormal) != FP_SUBNORMAL ||
                        fpclassify(long_subnormal) != FP_SUBNORMAL) return 2;
                    if (fpclassify(1.0f) != FP_NORMAL || fpclassify(1.0) != FP_NORMAL ||
                        fpclassify(1.0L) != FP_NORMAL) return 3;
                    if (fpclassify(HUGE_VALF) != FP_INFINITE || fpclassify(HUGE_VAL) != FP_INFINITE ||
                        fpclassify(HUGE_VALL) != FP_INFINITE) return 4;
                    if (fpclassify(float_nan) != FP_NAN || fpclassify(double_nan) != FP_NAN ||
                        fpclassify(long_nan) != FP_NAN) return 5;

                    if (!isfinite(0.0f) || !isfinite(double_subnormal) || !isfinite(1.0L) ||
                        isfinite(HUGE_VAL) || isfinite(long_nan)) return 6;
                    if (!isinf(HUGE_VALF) || !isinf(HUGE_VAL) || !isinf(HUGE_VALL) ||
                        isinf(double_nan) || isinf(1.0)) return 7;
                    if (!isnan(float_nan) || !isnan(double_nan) || !isnan(long_nan) || isnan(1.0)) return 8;
                    if (!isnormal(1.0f) || !isnormal(1.0) || !isnormal(1.0L) ||
                        isnormal(0.0) || isnormal(double_subnormal) || isnormal(HUGE_VAL)) return 9;
                    if (!signbit(-0.0f) || !signbit(-0.0) || !signbit(-0.0L) ||
                        signbit(0.0f) || signbit(0.0) || signbit(0.0L)) return 10;

                    if (!isgreater(3.0f, 2.0f) || !isgreaterequal(2.0, 2.0) ||
                        !isless(2.0L, 3.0L) || !islessequal(2.0f, 2.0f) ||
                        !islessgreater(2.0, 3.0) || !isunordered(double_nan, 0.0)) return 11;
                    if (isgreater(double_nan, 0.0) || isgreaterequal(double_nan, 0.0) ||
                        isless(double_nan, 0.0) || islessequal(double_nan, 0.0) ||
                        islessgreater(double_nan, 0.0) || isunordered(1.0, 0.0)) return 12;
                    if (!isgreaterequal(-0.0L, 0.0L) || islessgreater(4.0f, 4.0f)) return 13;

                    if (!isfinite(next_value()) || unary_calls != 1) return 14;
                    if (!isless(next_left(), next_right()) || left_calls != 1 || right_calls != 1) return 15;
                    return 0;
                }
            """.trimIndent())
        }
        val executable = directory.resolve("math-classification")
        try {
            val link = LinkDriver.link(LinkRequest(source, executable, target, resolution), plan)
            assertTrue(link.isSuccessful, link.output)

            val undefinedSymbols = ProcessBuilder("nm", "-u", executable.toString()).start()
            val undefinedOutput = undefinedSymbols.inputStream.bufferedReader().readText()
            assertEquals(0, undefinedSymbols.waitFor(), undefinedOutput)
            assertTrue(undefinedOutput.isBlank(), "math fixture imports host symbols: $undefinedOutput")
            assertEquals(0, ProcessBuilder(executable.toString()).start().waitFor())
        } finally {
            Files.deleteIfExists(executable)
            Files.deleteIfExists(source)
            Files.deleteIfExists(directory)
        }
    }
}
