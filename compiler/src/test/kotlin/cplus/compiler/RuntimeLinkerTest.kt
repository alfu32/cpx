package cplus.compiler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.nio.file.Files

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

    @Test
    fun normalTerminationRunsHandlersInReverseRegistrationOrder() {
        val manifest = requireNotNull(SdkManifestLoader.load(SdkManifestLocator.defaultManifestPath()).manifest)
        val resolution = requireNotNull(SdkResolver.resolve(manifest, TargetInfo()).resolution)
        val plan = requireNotNull(RuntimeLinker.plan(resolution, TargetInfo()).plan)
        val directory = Files.createTempDirectory("cplus-runtime-termination")
        val source = directory.resolve("termination.c").also {
            Files.writeString(it, """
                typedef void (*handler)(void);
                int __cplus_register_exit_handler(handler);
                int __cplus_terminate_normal(int status);
                static int marker;
                static void first(void) { marker = marker * 10 + 1; }
                static void second(void) { marker = marker * 10 + 2; }
                int main(void) {
                    __cplus_register_exit_handler(first);
                    __cplus_register_exit_handler(second);
                    __cplus_terminate_normal(marker);
                    return marker;
                }
            """.trimIndent())
        }
        val executable = directory.resolve("termination")
        val process = ProcessBuilder(
            listOf("cc", "-std=c17") + plan.compilerFlags +
                listOf(source.toString()) + plan.runtimeSources.map { it.toString() } +
                plan.startupSources.map { it.toString() } + listOf("-o", executable.toString())
        ).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()

        assertEquals(0, process.waitFor(), output)
        assertEquals(21, ProcessBuilder(executable.toString()).start().waitFor())
    }
}
