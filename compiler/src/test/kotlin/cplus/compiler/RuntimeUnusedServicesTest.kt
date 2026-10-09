package cplus.compiler

import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

class RuntimeUnusedServicesTest {
    @Test
    fun minimalCplusProgramDropsUnreferencedPlatformServicesAndPassesDependencyAudit() {
        assumeTrue(System.getProperty("os.name").contains("linux", ignoreCase = true))
        val manifestPath = SdkManifestLocator.defaultManifestPath()
        val manifest = requireNotNull(SdkManifestLoader.load(manifestPath).manifest)
        val target = TargetInfo(targetTriple = "linux-x86_64")
        val resolution = requireNotNull(SdkResolver.resolve(manifest, target).resolution)
        val plan = requireNotNull(RuntimeLinker.plan(resolution, target).plan)
        val directory = Files.createTempDirectory("cplus-unused-runtime-services")
        val source = directory.resolve("minimal.cp").also {
            Files.writeString(it, "int main() { return 0; }")
        }
        val generated = directory.resolve("minimal.c")
        val executable = directory.resolve("minimal")
        try {
            val compilation = CPlusCompiler().compile(CompileRequest(listOf(source), target))
            assertTrue(compilation.isSuccessful, compilation.diagnostics.joinToString())
            Files.writeString(generated, compilation.generatedUnits.single().text)

            val link = LinkDriver.link(LinkRequest(generated, executable, target, resolution), plan)
            assertTrue(link.isSuccessful, link.output)
            val nm = ProcessBuilder("nm", "-g", "--defined-only", executable.toString())
                .redirectErrorStream(true)
                .start()
            val symbols = nm.inputStream.bufferedReader().readLines()
                .mapNotNull { it.trim().split(Regex("\\s+")).lastOrNull() }
                .toSet()
            assertEquals(0, nm.waitFor())
            setOf(
                "platform_socket_open",
                "platform_network_resolve",
                "platform_mutex_lock",
                "platform_clock_wall_nanoseconds",
                "std_math_sin",
                "sin"
            ).forEach { symbol -> assertFalse(symbol in symbols, "unused service symbol '$symbol' was retained") }
            setOf(
                "__cplus_test_begin",
                "__cplus_test_finish",
                "__cplus_test_report_truth",
                "__cplus_test_report_equality"
            ).forEach { symbol -> assertFalse(symbol in symbols, "test-only runtime symbol '$symbol' leaked into a normal product") }

            val audit = RuntimeDependencyAuditor.inspect(
                executable,
                requireNotNull(TargetRegistry.load(resolution.layout.abiDescriptor).descriptor),
                target.buildProfile
            )
            assertTrue(audit.isSuccessful, audit.diagnostics.joinToString())

            val process = ProcessBuilder(executable.toString()).start()
            assertTrue(process.waitFor(15, TimeUnit.SECONDS), "minimal C+ executable timed out")
            assertEquals(0, process.exitValue())
        } finally {
            Files.deleteIfExists(executable)
            Files.deleteIfExists(generated)
            Files.deleteIfExists(source)
            Files.deleteIfExists(directory)
        }
    }
}
