package cplus.compiler

import kotlin.test.Test
import kotlin.test.assertTrue

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
}
