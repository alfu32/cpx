package cplus.compiler

import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

class ConformanceTest {
    @Test
    fun initialMatrixHasRuntimeAbiAndNativeStdCoverage() {
        val root = SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize().parent!!.parent!!
        val descriptor = requireNotNull(TargetRegistry.load(root.resolve("abi/linux-x86_64.toml")).descriptor)
        val report = ConformanceMatrix.initial(descriptor, BuildProfile())
        assertTrue(report.cases.any { it.id == "runtime.startup" && it.status == "pass" })
        assertTrue(report.cases.any { it.id == "abi.layout" && it.status == "pass" })
        assertTrue(report.cases.any { it.area == ConformanceArea.NATIVE_STD })
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
