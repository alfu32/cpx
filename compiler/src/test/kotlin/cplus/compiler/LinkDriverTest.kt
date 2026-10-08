package cplus.compiler

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.nio.file.Files

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
        assertTrue(command.contains("-DCPLUS_LONG_DOUBLE_FORMAT=2"))
        assertTrue(command.any { portablePath(it).endsWith("sdk/libc/include") })
        assertTrue(command.any { portablePath(it).endsWith("sdk/runtime/include") })
        assertFalse(command.any { it.contains("glibc", ignoreCase = true) || it.contains("musl", ignoreCase = true) })
    }

    @Test
    fun compilerSelectionDoesNotSelectAHostLibcProfile() {
        val capabilities = CCompilerToolchains.classify("x86_64-w64-mingw32-gcc")
        assertTrue(capabilities.supportsNoDefaultLibraries)
        assertTrue(CCompilerToolchains.targetFlags(TargetInfo(targetTriple = "windows-x86_64"), "clang").single().startsWith("--target="))
    }

    @Test
    fun probesAndValidatesTheAdvertisedLinuxInt128CompilerAbi() {
        val root = SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize().parent!!.parent!!
        val linux = requireNotNull(TargetRegistry.load(root.resolve("abi/linux-x86_64.toml")).descriptor)
        val windows = requireNotNull(TargetRegistry.load(root.resolve("abi/windows-x86_64.toml")).descriptor)

        assertTrue(CCompilerToolchains.supportsInt128(linux, "cc"))
        assertTrue(CCompilerToolchains.supportsFloatingAbi(linux, "cc"))
        assertTrue(CCompilerToolchains.validateTargetFeatures(linux, "cc").isEmpty())
        assertTrue(CCompilerToolchains.validateTargetFeatures(linux, "cl.exe").any { "128-bit" in it })
        assertFalse(CCompilerToolchains.supportsInt128(windows, "cl.exe"))
    }

    @Test
    fun rejectsACompilerWhoseLongDoubleAbiDiffersFromTheTargetDescriptor() {
        val root = SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize().parent!!.parent!!
        val linux = requireNotNull(TargetRegistry.load(root.resolve("abi/linux-x86_64.toml")).descriptor)
        val wrongFloatingTypes = linux.floatingTypes.toMutableMap().apply {
            put("long double", getValue("double"))
        }
        val mismatched = linux.copy(floatingTypes = wrongFloatingTypes)

        assertFalse(CCompilerToolchains.supportsFloatingAbi(mismatched, "cc"))
        assertTrue(CCompilerToolchains.validateTargetFeatures(mismatched, "cc").any { "floating ABI" in it })
    }

    @Test
    fun refusesLinkingWhenTheSelectedCompilerCannotHonorAdvertisedInt128Abi() {
        val root = SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize().parent!!.parent!!
        val manifest = requireNotNull(SdkManifestLoader.load(SdkManifestLocator.defaultManifestPath()).manifest)
        val target = TargetInfo(targetTriple = "linux-x86_64")
        val resolution = requireNotNull(SdkResolver.resolve(manifest, target).resolution)
        val descriptor = requireNotNull(TargetRegistry.load(root.resolve("abi/linux-x86_64.toml")).descriptor)
        val sdk = resolution.copy(targetDescriptor = descriptor)
        val plan = requireNotNull(RuntimeLinker.plan(sdk, target).plan)
        val directory = Files.createTempDirectory("cplus-int128-link-rejection")
        val generated = directory.resolve("generated.c")
        val output = directory.resolve("program")

        val result = LinkDriver.link(
            LinkRequest(generated, output, target, sdk, cCompiler = "cl.exe"),
            plan
        )

        assertFalse(result.isSuccessful)
        assertTrue(result.output.contains("does not satisfy the verified 128-bit"), result.output)
        assertFalse(Files.exists(output))
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
        assertTrue(command.contains("/DCPLUS_LONG_DOUBLE_FORMAT=2"))
        assertTrue(command.contains("kernel32.lib"))
        assertTrue(command.any { portablePath(it).endsWith("sdk/libc/include") })
        assertTrue(command.any { portablePath(it).endsWith("sdk/runtime/include") })
        assertFalse(command.any { it.startsWith("-l") })
    }

    private fun portablePath(value: String): String = value.replace('\\', '/')
}
