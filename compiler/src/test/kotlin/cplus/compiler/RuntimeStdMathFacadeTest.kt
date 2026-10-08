package cplus.compiler

import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

class RuntimeStdMathFacadeTest {
    @Test
    fun stdMathFacadeLinksEveryDeclaredEntryPointAndExecutesAcrossRealPrecisions() {
        val isWindows = System.getProperty("os.name").contains("windows", ignoreCase = true)
        assumeTrue(isWindows || System.getProperty("os.name").contains("linux", ignoreCase = true))
        val manifestPath = SdkManifestLocator.defaultManifestPath()
        val manifest = requireNotNull(SdkManifestLoader.load(manifestPath).manifest)
        val target = TargetInfo(targetTriple = if (isWindows) "windows-x86_64" else "linux-x86_64")
        val resolution = requireNotNull(SdkResolver.resolve(manifest, target).resolution)
        val plan = requireNotNull(RuntimeLinker.plan(resolution, target).plan)
        val root = manifestPath.toAbsolutePath().normalize().parent!!.parent!!
        val mathModule = root.resolve("std/src/math.cp")
        val directory = Files.createTempDirectory("cplus-std-math-facade")
        val mainSource = directory.resolve("main.cp").also {
            Files.writeString(it, """
                import {
                    std_math_abs,
                    std_math_fabs,
                    std_math_sqrtf, std_math_sqrt, std_math_sqrtl,
                    std_math_sinf, std_math_sin, std_math_sinl,
                    std_math_erf, std_math_tgamma,
                    std_math_fdimf, std_math_fmax, std_math_fminl,
                    std_math_fmaf, std_math_fma, std_math_fmal,
                    std_math_frexp, std_math_modf, std_math_ilogb,
                    std_math_ldexp, std_math_scalbln,
                    std_math_lrint, std_math_llround,
                    std_math_nan, std_math_nexttoward,
                    std_math_remquo
                } from std.math;
                import { tgamma } from c.math;

                int main() {
                    int exponent = 0;
                    int quotient = 0;
                    double integral = 0.0;
                    if (std_math_abs(-3.0) != 3.0) return 1;
                    if (std_math_sqrtf((float)4.0) != (float)2.0 || std_math_sqrt(9.0) != 3.0 ||
                        std_math_sqrtl((long double)16.0) != (long double)4.0) return 2;
                    if (std_math_sinf((float)0.5) < (float)0.4794 || std_math_sinf((float)0.5) > (float)0.4795 ||
                        std_math_sin(0.5) < 0.4794 || std_math_sinl((long double)0.5) < (long double)0.4794) return 3;
                    if (std_math_erf(1.0) < 0.8427) return 41;
                    if (std_math_tgamma(5.0) != tgamma(5.0)) return 42;
                    if (std_math_fabs(std_math_tgamma(5.0) - 24.0) > 0.000000000001) return 43;
                    if (std_math_fdimf((float)7.0, (float)3.0) != (float)4.0 ||
                        std_math_fmax(-0.0, 0.0) != 0.0 ||
                        std_math_fminl(-(long double)0.0, (long double)0.0) != (long double)0.0) return 5;
                    if (std_math_fmaf((float)1.0, (float)2.0, (float)3.0) != (float)5.0 ||
                        std_math_fma(1.0, 2.0, 3.0) != 5.0 ||
                        std_math_fmal((long double)1.0, (long double)2.0, (long double)3.0) != (long double)5.0) return 6;
                    if (std_math_frexp(8.0, &exponent) != 0.5 || exponent != 4) return 7;
                    if (std_math_modf(3.25, &integral) != 0.25 || integral != 3.0) return 8;
                    if (std_math_ilogb(8.0) != 3 || std_math_ldexp(0.5, 4) != 8.0 ||
                        std_math_scalbln(0.5, 4) != 8.0) return 9;
                    if (std_math_lrint(2.0) != 2 || std_math_llround(2.5) != 3) return 10;
                    if (!(std_math_nan("") != std_math_nan("") ) ||
                        std_math_nexttoward(1.0, (long double)2.0) <= 1.0) return 11;
                    if (std_math_remquo(7.0, 2.0, &quotient) != -1.0 || quotient == 0) return 12;
                    return 0;
                }
            """.trimIndent())
        }
        val generatedC = directory.resolve("std_math_facade.c")
        val executable = directory.resolve(if (isWindows) "std-math-facade.exe" else "std-math-facade")
        val declaredNames = Regex("(?m)^pub\\s+.+\\s+(std_math_[A-Za-z0-9_]+)\\s*\\(")
            .findAll(Files.readString(mathModule))
            .map { it.groupValues[1] }
            .toSet()
        try {
            assertEquals(172, declaredNames.size, "std.math declarations must remain unique and complete")
            val compilation = CPlusCompiler().compile(CompileRequest(listOf(mainSource, mathModule), target))
            assertTrue(compilation.isSuccessful, compilation.diagnostics.joinToString())
            assertEquals(1, compilation.generatedUnits.size)
            Files.writeString(generatedC, compilation.generatedUnits.single().text)

            val exportAuditPlan = plan.copy(
                linkerFlags = plan.linkerFlags + declaredNames.map { "-Wl,--undefined=$it" }
            )
            val link = LinkDriver.link(LinkRequest(generatedC, executable, target, resolution), exportAuditPlan)
            assertTrue(link.isSuccessful, link.output)

            val nm = ProcessBuilder("nm", "-g", "--defined-only", executable.toString())
                .redirectErrorStream(true)
                .start()
            val symbols = nm.inputStream.bufferedReader().readLines()
                .mapNotNull { it.trim().split(Regex("\\s+")).lastOrNull() }
                .toSet()
            assertEquals(0, nm.waitFor())
            assertTrue(declaredNames.all(symbols::contains), "Missing runtime exports: ${declaredNames - symbols}")

            if (isWindows) {
                val descriptor = resolution.targetDescriptor
                    ?: requireNotNull(TargetRegistry.load(resolution.layout.abiDescriptor).descriptor)
                val audit = RuntimeDependencyAuditor.inspect(executable, descriptor, target.buildProfile)
                assertTrue(audit.isSuccessful, audit.diagnostics.joinToString())
            } else {
                val undefined = ProcessBuilder("nm", "-u", executable.toString())
                    .redirectErrorStream(true)
                    .start()
                val undefinedSymbols = undefined.inputStream.bufferedReader().readText()
                assertEquals(0, undefined.waitFor(), undefinedSymbols)
                assertTrue(undefinedSymbols.isBlank(), "std.math facade depends on host symbols: $undefinedSymbols")
            }

            val process = ProcessBuilder(executable.toString()).redirectErrorStream(true).start()
            if (!process.waitFor(15, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                process.waitFor(2, TimeUnit.SECONDS)
                throw AssertionError("std.math façade fixture timed out; artifacts at $directory")
            }
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(0, process.exitValue(), "std.math façade fixture failed: $output")
        } finally {
            Files.deleteIfExists(executable)
            Files.deleteIfExists(generatedC)
            Files.deleteIfExists(mainSource)
            Files.deleteIfExists(directory)
        }
    }
}
