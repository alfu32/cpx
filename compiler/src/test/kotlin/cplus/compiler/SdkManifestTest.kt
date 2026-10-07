package cplus.compiler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.nio.file.Files

class SdkManifestTest {
    @Test
    fun loadsCompatibleManifestAndProducesStableIdentity() {
        val path = Files.createTempFile("cplus-sdk", ".toml")
        Files.writeString(path, manifestText())

        val result = SdkManifestLoader.load(path)

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val manifest = assertNotNull(result.manifest)
        assertEquals("0.1.0", manifest.sdkVersion)
        assertEquals("c17-1", manifest.libcProfileVersion)
        assertEquals(manifest.identity, result.manifest.identity)
        assertTrue(manifest.contentHash.isNotBlank())
    }

    @Test
    fun rejectsManifestWithoutRequiredVersionKey() {
        val path = Files.createTempFile("cplus-sdk-missing", ".toml")
        Files.writeString(path, manifestText().replace("cplus_abi_version = \"1\"\n", ""))

        val result = SdkManifestLoader.load(path)

        assertTrue(result.diagnostics.any { it.code == "SDK003" }, result.diagnostics.joinToString())
        assertTrue(!result.isSuccessful)
    }

    @Test
    fun rejectsIncompatibleRuntimeAbi() {
        val path = Files.createTempFile("cplus-sdk-incompatible", ".toml")
        Files.writeString(path, manifestText().replace("runtime_abi_version = \"1\"", "runtime_abi_version = \"2\""))

        val result = SdkManifestLoader.load(path)

        assertTrue(result.diagnostics.any { it.code == "SDK004" }, result.diagnostics.joinToString())
        assertTrue(!result.isSuccessful)
    }

    @Test
    fun validatesRuntimeAndLibcProfileCombinations() {
        val sdk = assertNotNull(SdkManifestLoader.load(SdkManifestLocator.defaultManifestPath()).manifest)

        assertTrue(BuildProfileValidator.validate(BuildProfile(RuntimeProfile.CPLUS, LibcProfile.C17), sdk).isEmpty())
        assertTrue(BuildProfileValidator.validate(BuildProfile(RuntimeProfile.FREESTANDING, LibcProfile.C17), sdk)
            .any { it.code == "SDK005" })
        assertTrue(BuildProfileValidator.validate(BuildProfile(RuntimeProfile.CPLUS, LibcProfile.NONE), sdk)
            .any { it.code == "SDK005" })
        assertTrue(BuildProfileValidator.validate(BuildProfile(RuntimeProfile.CPLUS, LibcProfile.C23), sdk)
            .any { it.code == "SDK006" || it.code == "SDK007" })
    }

    @Test
    fun exposesSelectedProfilesToComptimeTargetMetadata() {
        val target = TargetInfo(buildProfile = BuildProfile(RuntimeProfile.FREESTANDING, LibcProfile.NONE))

        val comptimeTarget = BuildProfileValidator.toComptimeTarget(target)

        assertEquals("freestanding", comptimeTarget.runtimeProfile)
        assertEquals("none", comptimeTarget.libcProfile)
    }

    private fun manifestText(): String = """
        sdk_version = "0.1.0"
        language_abi_version = "1"
        runtime_abi_version = "1"
        cplus_abi_version = "1"
        libc_profile_version = "c17-1"
    """.trimIndent() + "\n"
}
