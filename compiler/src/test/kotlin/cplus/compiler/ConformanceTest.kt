package cplus.compiler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files

class ConformanceTest {
    @Test
    fun targetRunnerDistinguishesUnavailableEmulatorFromExecutionFailure() {
        assertEquals(
            emptyList(),
            C17TargetRunner.commandPrefix("linux-x86_64", "Linux", "amd64", emptyList())
        )
        assertNull(C17TargetRunner.commandPrefix("linux-aarch64", "Linux", "x86_64", emptyList()))

        val directory = Files.createTempDirectory("cplus-target-runner")
        val emulator = Files.createFile(directory.resolve("qemu-aarch64"))
        val staticEmulator = Files.createFile(directory.resolve("qemu-aarch64-static"))
        assertTrue(emulator.toFile().setExecutable(true))
        assertTrue(staticEmulator.toFile().setExecutable(true))
        try {
            assertEquals(
                listOf(emulator.toAbsolutePath().normalize().toString()),
                C17TargetRunner.commandPrefix("linux-aarch64", "Linux", "x86_64", listOf(directory))
            )
            Files.delete(emulator)
            assertEquals(
                listOf(staticEmulator.toAbsolutePath().normalize().toString()),
                C17TargetRunner.commandPrefix("linux-aarch64", "Linux", "x86_64", listOf(directory))
            )
        } finally {
            Files.deleteIfExists(emulator)
            Files.deleteIfExists(staticEmulator)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun initialMatrixHasRuntimeAbiAndNativeStdCoverage() {
        val root = SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize().parent!!.parent!!
        val descriptor = requireNotNull(TargetRegistry.load(root.resolve("abi/linux-x86_64.toml")).descriptor)
        val report = ConformanceMatrix.initial(descriptor, BuildProfile())
        assertTrue(report.cases.none { it.status == "pass" })
        assertTrue(report.cases.all { it.status == "planned" })
        assertTrue(report.cases.any { it.area == ConformanceArea.NATIVE_STD && it.status == "planned" })

        val unsupportedReport = ConformanceMatrix.initial(descriptor.copy(os = "freebsd"), BuildProfile())
        assertTrue(unsupportedReport.cases.any {
            it.id == "platform.services" && it.status == "unsupported"
        })
    }

    @Test
    fun linuxC17AuditExecutesIndependentFixturesAndDependencyChecks() {
        assumeTrue(System.getProperty("os.name").contains("linux", ignoreCase = true))
        val manifest = requireNotNull(SdkManifestLoader.load(SdkManifestLocator.defaultManifestPath()).manifest)
        val target = TargetInfo(targetTriple = "linux-x86_64")
        val resolution = requireNotNull(SdkResolver.resolve(manifest, target).resolution)
        val report = C17ConformanceRunner.run(resolution, target)
        assertTrue(report.isComplete, report.cases.filter { it.status != "pass" }.joinToString())
        assertTrue(report.cases.any { it.id == "fixture.execution.basic" && it.status == "pass" })
        assertTrue(report.cases.any { it.id == "fixture.streams.stdio" && it.status == "pass" })
        assertTrue(report.cases.any { it.id == "fixture.dependencies.context" && it.status == "pass" })
    }

    @Test
    fun linuxC17SetjmpFixturePassesWithOptimizationOnAvailableRunners() {
        assumeTrue(System.getProperty("os.name").contains("linux", ignoreCase = true))
        val manifest = requireNotNull(SdkManifestLoader.load(SdkManifestLocator.defaultManifestPath()).manifest)
        val root = SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize().parent!!.parent!!
        val source = root.resolve("conformance/c17/c17-context.c")
        val compilers = listOf("cc", "clang").filter { compiler ->
            runCatching { ProcessBuilder(compiler, "--version").start().waitFor() == 0 }.getOrDefault(false)
        }
        assertTrue(compilers.isNotEmpty(), "neither cc nor clang is available")
        val targets = buildList {
            add("linux-x86_64" to emptyList<String>())
            C17TargetRunner.commandPrefix("linux-aarch64")?.let { add("linux-aarch64" to it) }
        }
        val directory = Files.createTempDirectory("cplus-c17-setjmp-optimized")

        try {
            targets.forEach { (triple, runner) ->
                val target = TargetInfo(targetTriple = triple)
                val resolution = requireNotNull(SdkResolver.resolve(manifest, target).resolution)
                val plan = requireNotNull(RuntimeLinker.plan(resolution, target).plan)
                val targetCompilers = if (triple == "linux-aarch64") listOf("clang") else compilers
                targetCompilers.forEach { compiler ->
                    val executable = directory.resolve("setjmp_${triple.replace('-', '_')}_${compiler.replace('/', '_')}")
                    val request = LinkRequest(source, executable, target, resolution, cCompiler = compiler)
                    val process = ProcessBuilder(LinkDriver.command(request, plan) + "-O2")
                        .redirectErrorStream(true)
                        .start()
                    val compileOutput = process.inputStream.bufferedReader().readText()
                    assertEquals(0, process.waitFor(), "$triple/$compiler optimized link: $compileOutput")
                    val descriptor = resolution.targetDescriptor
                        ?: requireNotNull(TargetRegistry.load(resolution.layout.abiDescriptor).descriptor)
                    val audit = RuntimeDependencyAuditor.inspect(executable, descriptor, target.buildProfile)
                    assertTrue(audit.isSuccessful, "$triple/$compiler: ${audit.diagnostics.joinToString()}")
                    val run = ProcessBuilder(runner + executable.toString()).redirectErrorStream(true).start()
                    val output = run.inputStream.bufferedReader().readText()
                    assertEquals(0, run.waitFor(), "$triple/$compiler optimized run: $output")
                    Files.deleteIfExists(executable)
                }
            }
        } finally {
            Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }

    @Test
    fun linuxAarch64C17AuditExecutesSupportedFixturesWhenRunnerIsAvailable() {
        assumeTrue(System.getProperty("os.name").contains("linux", ignoreCase = true))
        assumeTrue(C17TargetRunner.commandPrefix("linux-aarch64") != null, "AArch64 QEMU user-mode runner is unavailable")
        val manifest = requireNotNull(SdkManifestLoader.load(SdkManifestLocator.defaultManifestPath()).manifest)
        val target = TargetInfo(targetTriple = "linux-aarch64")
        val resolution = requireNotNull(SdkResolver.resolve(manifest, target).resolution)
        val report = C17ConformanceRunner.run(resolution, target)

        assertTrue(report.cases.none { it.status == "fail" }, report.cases.filter { it.status == "fail" }.joinToString())
        assertTrue(report.cases.any { it.id == "fixture.execution.basic" && it.status == "pass" })
        assertTrue(report.cases.any { it.id == "fixture.dependencies.basic" && it.status == "pass" })
        assertTrue(report.cases.any { it.id == "fixture.execution.stdio" && it.status == "pass" })
        assertTrue(report.cases.any { it.id == "fixture.streams.stdio" && it.status == "pass" })
        assertTrue(report.cases.any { it.id == "libc.setjmp-context" && it.status == "pass" })
        assertTrue(report.cases.any { it.id == "fixture.execution.context" && it.status == "pass" })
        assertTrue(report.cases.any { it.id == "fixture.dependencies.context" && it.status == "pass" })
    }

    @Test
    fun complexRuntimeAuditIsCapabilityGated() {
        val manifest = requireNotNull(SdkManifestLoader.load(SdkManifestLocator.defaultManifestPath()).manifest)
        val target = TargetInfo(targetTriple = "linux-aarch64")
        val resolution = requireNotNull(SdkResolver.resolve(manifest, target).resolution)
        val report = C17ConformanceAudit.inspect(resolution, target)

        assertTrue(report.cases.any {
            it.id == "runtime.source.complex.arithmetic" && it.status == "unsupported"
        })
    }
}
