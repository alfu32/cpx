package cplus.compiler

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SdkToolingTest {
    @Test
    fun doctorAndPackageIndexAreDeterministic() {
        val manifest = SdkManifestLocator.defaultManifestPath()
        val report = SdkDoctor.inspect(manifest)
        assertTrue(report.isSuccessful, report.diagnostics.joinToString())
        assertTrue("runtime link plan" in report.checks)
        val root = manifest.toAbsolutePath().normalize().parent!!.parent!!
        val first = SdkPackageIndex.serialize(SdkPackageIndex.build(root))
        val second = SdkPackageIndex.serialize(SdkPackageIndex.build(root))
        assertTrue(first == second)
        assertTrue(first.startsWith("CPLUS_SDK_PACKAGE_INDEX"))
    }

    @Test
    fun packageIndexIsStableWhenWrittenInsideSdkAndExcludesCacheTrees() {
        val root = Files.createTempDirectory("cplus-sdk-index")
        Files.createDirectories(root.resolve("std/src")).resolve("core.cp").toFile().writeText("pub int value;\n")
        Files.createDirectories(root.resolve("cache/metadata")).resolve("linux-x86_64.meta")
            .toFile().writeText("generated cache")
        val output = root.resolve("sdk-package.index")
        val first = SdkPackageIndex.serialize(SdkPackageIndex.build(root, setOf(output)))
        Files.writeString(output, first)
        val second = SdkPackageIndex.serialize(SdkPackageIndex.build(root, setOf(output)))

        assertEquals(first, second)
        assertTrue("std/src/core.cp" in first)
        assertTrue("cache/metadata" !in first)
        assertTrue("sdk-package.index" !in first)
        assertTrue('\r' !in first)
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
