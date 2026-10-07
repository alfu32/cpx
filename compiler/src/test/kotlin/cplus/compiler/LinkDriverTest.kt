package cplus.compiler

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LinkDriverTest {
    @Test
    fun windowsGnuInvocationUsesOnlyTargetRuntimeAndOsImports() {
        val manifest = requireNotNull(SdkManifestLoader.load(SdkManifestLocator.defaultManifestPath()).manifest)
        val target = TargetInfo(targetTriple = "windows-x86_64")
        val resolution = requireNotNull(SdkResolver.resolve(manifest, target).resolution)
        val plan = requireNotNull(RuntimeLinker.plan(resolution, target).plan)
        val command = LinkDriver.command(
            LinkRequest(
                generatedSource = java.nio.file.Path.of("main.c"),
                output = java.nio.file.Path.of("main.exe"),
                target = target,
                sdk = resolution,
                cCompiler = "x86_64-w64-mingw32-gcc"
            ),
            plan
        )

        assertTrue(command.first() == "x86_64-w64-mingw32-gcc")
        assertTrue(command.contains("-lkernel32"))
        assertTrue(command.contains("-nostdlib"))
        assertTrue(command.contains("-I"))
        assertTrue(command.any { it.endsWith("sdk/libc/include") })
        assertTrue(command.any { it.endsWith("sdk/runtime/include") })
        assertFalse(command.any { it.contains("glibc", ignoreCase = true) || it.contains("musl", ignoreCase = true) })
    }

    @Test
    fun compilerSelectionDoesNotSelectAHostLibcProfile() {
        val capabilities = CCompilerToolchains.classify("x86_64-w64-mingw32-gcc")
        assertTrue(capabilities.supportsNoDefaultLibraries)
        assertTrue(CCompilerToolchains.targetFlags(TargetInfo(targetTriple = "windows-x86_64"), "clang").single().startsWith("--target="))
    }

    @Test
    fun msvcStyleInvocationSuppressesDefaultCrtAndUsesOnlyKernel32() {
        val manifest = requireNotNull(SdkManifestLoader.load(SdkManifestLocator.defaultManifestPath()).manifest)
        val target = TargetInfo(targetTriple = "windows-x86_64")
        val resolution = requireNotNull(SdkResolver.resolve(manifest, target).resolution)
        val plan = requireNotNull(RuntimeLinker.plan(resolution, target).plan)
        val command = LinkDriver.command(
            LinkRequest(
                generatedSource = java.nio.file.Path.of("main.c"),
                output = java.nio.file.Path.of("main.exe"),
                target = target,
                sdk = resolution,
                cCompiler = "clang-cl"
            ),
            plan
        )

        assertTrue(command.contains("/NODEFAULTLIB"))
        assertTrue(command.contains("/ENTRY:mainCRTStartup"))
        assertTrue(command.contains("/DCPLUS_RUNTIME_NO_WEAK"))
        assertTrue(command.contains("kernel32.lib"))
        assertTrue(command.any { it.endsWith("sdk/libc/include") })
        assertTrue(command.any { it.endsWith("sdk/runtime/include") })
        assertFalse(command.any { it.startsWith("-l") })
    }
}
