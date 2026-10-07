package cplus.compiler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RuntimeLinkerTest {
    @Test
    fun selectsCplusStartupAndRuntimeForLinuxTarget() {
        val manifest = requireNotNull(SdkManifestLoader.load(SdkManifestLocator.defaultManifestPath()).manifest)
        val resolution = requireNotNull(SdkResolver.resolve(manifest, TargetInfo()).resolution)

        val result = RuntimeLinker.plan(resolution, TargetInfo())

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        assertEquals(RuntimeProfile.CPLUS, result.plan!!.profile)
        assertTrue(result.plan.compilerFlags.contains("-nostdlib"))
        assertTrue(result.plan.startupSources.single().fileName.toString() == "start.S")
    }

    @Test
    fun systemRuntimeLeavesStartupToDownstreamToolchain() {
        val manifest = requireNotNull(SdkManifestLoader.load(SdkManifestLocator.defaultManifestPath()).manifest)
        val resolution = requireNotNull(SdkResolver.resolve(manifest, TargetInfo()).resolution)
        val target = TargetInfo(buildProfile = BuildProfile(RuntimeProfile.SYSTEM, LibcProfile.C17))

        val result = RuntimeLinker.plan(resolution, target)

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        assertTrue(result.plan!!.startupSources.isEmpty())
        assertTrue(result.plan.runtimeSources.isEmpty())
    }
}
