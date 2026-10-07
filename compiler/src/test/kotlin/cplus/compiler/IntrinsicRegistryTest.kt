package cplus.compiler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IntrinsicRegistryTest {
    @Test
    fun loadsAndValidatesSdkIntrinsicCatalogue() {
        val resolution = requireNotNull(SdkResolver.resolve(
            requireNotNull(SdkManifestLoader.load(SdkManifestLocator.defaultManifestPath()).manifest),
            TargetInfo()
        ).resolution)
        val result = IntrinsicRegistry.load(resolution.layout.intrinsicCatalogue)
        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        assertTrue(result.definitions.size >= 17)
        assertTrue(result.definitions.any { it.name == "syscall6" })
        val target = requireNotNull(TargetRegistry.load(resolution).descriptor)
        assertTrue(IntrinsicRegistry.verify(result.definitions.first { it.name == "syscall3" }, target) == null)
        assertTrue(IntrinsicCallValidator.validate(IntrinsicCallContract("atomic_load", 2), result.definitions.first { it.name == "atomic_load" }) == null)
    }
}
