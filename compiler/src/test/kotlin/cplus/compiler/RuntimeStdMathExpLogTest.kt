package cplus.compiler

import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

class RuntimeStdMathExpLogTest {
    @Test
    fun c17ExponentialAndLogarithmicFamiliesUseTheRuntime() {
        assumeTrue(System.getProperty("os.name").contains("linux", ignoreCase = true))
        val manifestPath = SdkManifestLocator.defaultManifestPath()
        val manifest = requireNotNull(SdkManifestLoader.load(manifestPath).manifest)
        val target = TargetInfo(targetTriple = "linux-x86_64")
        val resolution = requireNotNull(SdkResolver.resolve(manifest, target).resolution)
        val plan = requireNotNull(RuntimeLinker.plan(resolution, target).plan)
        val directory = Files.createTempDirectory("cplus-math-exp-log")
        val source = directory.resolve("math_exp_log.c").also {
            Files.writeString(it, """
                #include <errno.h>
                #include <math.h>

                int main(void) {
                    if (expf(0.0f) != 1.0f || fabs(exp(1.0) - 2.718281828459045) > 0.000000000001 ||
                        fabsf(expf(1.0f) - 2.7182817f) > 0.00001f ||
                        fabsl(expl(-1.0L) - 0.3678794411714423216L) > 0.000000000001L) return 1;
                    if (exp2f(10.0f) != 1024.0f || exp2(-1074.0) != 0x1p-1074 ||
                        exp2l(-16445.0L) != 0x1p-16445L) return 2;
                    if (expm1(0x1p-54) != 0x1p-54 || !signbit(expm1(-0.0)) ||
                        expm1(-HUGE_VAL) != -1.0 ||
                        fabsf(expm1f(1.0f) - 1.7182818f) > 0.00001f ||
                        fabsl(expm1l(1.0L) - 1.7182818284590452354L) > 0.000000000001L) return 3;
                    if (fabs(expm1(1.0) - 1.718281828459045) > 0.000000000001 ||
                        expm1(-1000.0) != -1.0) return 4;

                    if (logf(1.0f) != 0.0f || fabs(log(2.0) - 0.6931471805599453) > 0.000000000001 ||
                        fabsl(logl(2.0L) - 0.6931471805599453094L) > 0.000000000001L) return 5;
                    if (fabsf(log10f(100.0f) - 2.0f) > 0.00001f ||
                        fabs(log10(100.0) - 2.0) > 0.000000000001 ||
                        fabsl(log10l(100.0L) - 2.0L) > 0.000000000001L ||
                        log2f(8.0f) != 3.0f || log2(0.5) != -1.0 || log2l(8.0L) != 3.0L) return 6;
                    if (log1p(0x1p-54) != 0x1p-54 || !signbit(log1p(-0.0)) ||
                        fabs(log1p(1.0) - 0.6931471805599453) > 0.000000000001 ||
                        fabsf(log1pf(1.0f) - 0.6931472f) > 0.00001f ||
                        fabsl(log1pl(1.0L) - 0.6931471805599453094L) > 0.000000000001L) return 7;
                    if (logb(8.0) != 3.0 || logbf(0x1p-149f) != -149.0f ||
                        logbl(0x1p-16445L) != -16445.0L || logb(-0.5) != -1.0) return 8;

                    if (exp(-HUGE_VAL) != 0.0 || exp(HUGE_VAL) != HUGE_VAL ||
                        !isnan(exp(NAN)) || log(HUGE_VAL) != HUGE_VAL ||
                        !isnan(log(NAN)) || log1p(HUGE_VAL) != HUGE_VAL ||
                        logb(HUGE_VAL) != HUGE_VAL) return 9;
                    errno = 55;
                    if (exp(1000.0) != HUGE_VAL || errno != ERANGE) return 10;
                    errno = 55;
                    if (exp(-1000.0) != 0.0 || errno != ERANGE) return 11;
                    errno = 55;
                    if (exp2(1024.0) != HUGE_VAL || errno != ERANGE) return 12;
                    errno = 55;
                    if (log(0.0) != -HUGE_VAL || errno != ERANGE) return 13;
                    errno = 55;
                    if (!isnan(log(-1.0)) || errno != EDOM) return 14;
                    errno = 55;
                    if (log1p(-1.0) != -HUGE_VAL || errno != ERANGE) return 15;
                    errno = 55;
                    if (!isnan(log1p(-2.0)) || errno != EDOM) return 16;
                    errno = 55;
                    if (logb(0.0) != -HUGE_VAL || errno != ERANGE) return 17;
                    errno = 55;
                    if (expm1(-HUGE_VAL) != -1.0 || errno != 55) return 18;
                    return 0;
                }
            """.trimIndent())
        }
        val executable = directory.resolve("math-exp-log")
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
                throw AssertionError("C17 math exp/log fixture timed out; artifacts at $directory")
            }
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(0, process.exitValue(), "exp/log fixture returned ${process.exitValue()}: $output")
        } finally {
            Files.deleteIfExists(executable)
            Files.deleteIfExists(source)
            Files.deleteIfExists(directory)
        }
    }
}
