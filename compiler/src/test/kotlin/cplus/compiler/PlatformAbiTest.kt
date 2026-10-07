package cplus.compiler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlatformAbiTest {
    @Test
    fun mapsInitialPlatformProfilesWithoutHostAssumptions() {
        val root = SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize().parent!!.parent!!
        for (name in listOf("linux-x86_64", "linux-aarch64", "windows-x86_64", "darwin-aarch64")) {
            val descriptor = requireNotNull(TargetRegistry.load(root.resolve("abi/$name.toml")).descriptor)
            val profile = PlatformAbiRegistry.profile(descriptor)
            assertEquals(descriptor.objectFormat, profile.objectFormat)
            assertTrue(profile.supportedServices.isNotEmpty())
        }
        val linux = requireNotNull(TargetRegistry.load(root.resolve("abi/linux-x86_64.toml")).descriptor)
        assertEquals(60, LinuxSyscallCatalogue.forTarget(linux).first { it.name == "exit" }.number)
    }
}
