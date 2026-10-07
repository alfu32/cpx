package cplus.compiler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TargetDescriptorTest {
    @Test
    fun loadsAllInitialTargetDescriptorsFromRegistry() {
        val root = SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize().parent!!.parent!!
        val descriptors = TargetRegistry.list(root.resolve("abi"))

        assertEquals(6, descriptors.size)
        descriptors.forEach { path ->
            val result = TargetRegistry.load(path)
            assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        }
    }

    @Test
    fun exposesStructuredTargetCapabilitiesToComptimeMetadata() {
        val path = SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize()
            .parent!!.parent!!.resolve("abi/linux-x86_64.toml")
        val descriptor = requireNotNull(TargetRegistry.load(path).descriptor)
        val target = descriptor.toComptimeTarget(TargetInfo())

        assertEquals("linux", target.os)
        assertEquals("x86_64", target.architecture)
        assertEquals(64, target.pointerBits)
        assertTrue(target.hasIntrinsic("syscall3"))
        assertTrue(target.supportsAbi("cplus"))
        assertTrue(target.hasFeature("atomics"))
    }
}
