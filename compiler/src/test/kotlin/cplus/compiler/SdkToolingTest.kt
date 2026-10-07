package cplus.compiler

import kotlin.test.Test
import kotlin.test.assertTrue

class SdkToolingTest {
    @Test
    fun doctorAndPackageIndexAreDeterministic() {
        val manifest = SdkManifestLocator.defaultManifestPath()
        val report = SdkDoctor.inspect(manifest)
        assertTrue(report.isSuccessful, report.diagnostics.joinToString())
        val root = manifest.toAbsolutePath().normalize().parent!!.parent!!
        val first = SdkPackageIndex.serialize(SdkPackageIndex.build(root))
        val second = SdkPackageIndex.serialize(SdkPackageIndex.build(root))
        assertTrue(first == second)
        assertTrue(first.startsWith("CPLUS_SDK_PACKAGE_INDEX"))
    }

    @Test
    fun compilerAdaptersExposeSelfHostedCapabilities() {
        val capabilities = CCompilerToolchains.classify("cc")
        assertTrue(capabilities.supportsNoDefaultLibraries)
        assertTrue(CCompilerToolchains.validate(
            requireNotNull(TargetRegistry.load(
                SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize().parent!!.parent!!.resolve("abi/linux-x86_64.toml")
            ).descriptor), "cc", true
        ).isEmpty())
    }
}
