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
            if (descriptor.os == "darwin") {
                assertTrue(profile.supportedServices.isEmpty(), "Darwin C+ services are not implemented yet")
                assertEquals("unsupported", profile.syscallMode)
            } else {
                assertTrue(profile.supportedServices.containsAll(
                    setOf("memory", "file", "process", "time", "threads", "sync", "atomics", "socket-transport", "dns")
                ), "${descriptor.targetTriple} service profile is incomplete")
            }
        }
        val linux = requireNotNull(TargetRegistry.load(root.resolve("abi/linux-x86_64.toml")).descriptor)
        assertEquals(60, LinuxSyscallCatalogue.forTarget(linux).first { it.name == "exit" }.number)
    }

    @Test
    fun catalogsLinuxSocketSyscallsAndAdvertisesImplementedWindowsSocketTransport() {
        val root = SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize().parent!!.parent!!
        val expected = mapOf(
            "linux-x86_64" to mapOf(
                "read" to 0L, "exit" to 60L, "socket" to 41L, "connect" to 42L,
                "accept" to 43L, "sendto" to 44L, "recvfrom" to 45L, "shutdown" to 48L,
                "bind" to 49L, "listen" to 50L, "getsockname" to 51L, "getpeername" to 52L,
                "accept4" to 288L
            ),
            "linux-aarch64" to mapOf(
                "read" to 63L, "exit" to 93L, "socket" to 198L, "bind" to 200L,
                "listen" to 201L, "accept" to 202L, "connect" to 203L,
                "getsockname" to 204L, "getpeername" to 205L, "sendto" to 206L,
                "recvfrom" to 207L, "shutdown" to 210L, "accept4" to 242L
            )
        )
        expected.forEach { (targetName, expectedCalls) ->
            val descriptor = requireNotNull(TargetRegistry.load(root.resolve("abi/$targetName.toml")).descriptor)
            val calls = LinuxSyscallCatalogue.forTarget(descriptor).associate { it.name to it.number }
            expectedCalls.forEach { (name, number) -> assertEquals(number, calls[name], "$targetName $name") }
            assertTrue("socket-transport" in PlatformAbiRegistry.profile(descriptor).supportedServices)
        }
        val windows = requireNotNull(TargetRegistry.load(root.resolve("abi/windows-x86_64.toml")).descriptor)
        assertTrue("socket-transport" in PlatformAbiRegistry.profile(windows).supportedServices)
    }
}
