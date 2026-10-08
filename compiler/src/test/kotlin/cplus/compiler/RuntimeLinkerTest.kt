package cplus.compiler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.nio.file.Files
import org.junit.jupiter.api.Assumptions.assumeTrue

class RuntimeLinkerTest {
    @Test
    fun selectsCplusStartupAndRuntimeForLinuxTarget() {
        val manifest = requireNotNull(SdkManifestLoader.load(SdkManifestLocator.defaultManifestPath()).manifest)
        val resolution = requireNotNull(SdkResolver.resolve(manifest, TargetInfo()).resolution)

        val result = RuntimeLinker.plan(resolution, TargetInfo())

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        assertEquals(RuntimeProfile.CPLUS, result.plan!!.profile)
        assertTrue(result.plan.compilerFlags.contains("-nostdlib"))
        assertTrue(result.plan.compilerFlags.contains("-fno-pie"))
        assertTrue(result.plan.linkerFlags.contains("-static"))
        assertTrue(result.plan.linkerFlags.contains("-no-pie"))
        assertTrue(result.plan.startupSources.single().fileName.toString() == "start.S")
        assertTrue(result.plan.runtimeSources.any { it.fileName.toString() == "runtime.c" })
        assertTrue(result.plan.runtimeSources.any { it.fileName.toString() == "thread-x86_64.S" })
        assertTrue(result.plan.runtimeSources.any { it.fileName.toString() == "sync.c" })
        assertTrue(result.plan.runtimeSources.any { it.fileName.toString() == "sync_std.c" })
        assertTrue(result.plan.runtimeSources.any { it.fileName.toString() == "atomic.c" })
        assertTrue(result.plan.runtimeSources.any { it.fileName.toString() == "process.c" })
        assertTrue(result.plan.runtimeSources.any { it.fileName.toString() == "thread.c" })
        assertTrue(result.plan.runtimeSources.any { it.fileName.toString() == "net_address.c" })
        assertTrue(result.plan.runtimeSources.any { it.fileName.toString() == "net.c" })
        assertTrue(result.plan.runtimeSources.any { it.fileName.toString() == "network_dns.c" })
        assertTrue(result.plan.linkerFlags.any { it.contains("thread-tls.ld") })
    }

    @Test
    fun linuxAarch64UsesStaticLinkWithoutX86SpecificNoPieDriverFlag() {
        val manifest = requireNotNull(SdkManifestLoader.load(SdkManifestLocator.defaultManifestPath()).manifest)
        val target = TargetInfo(targetTriple = "linux-aarch64")
        val resolution = requireNotNull(SdkResolver.resolve(manifest, target).resolution)

        val result = RuntimeLinker.plan(resolution, target)

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        assertTrue(result.plan!!.linkerFlags.contains("-static"))
        assertTrue(result.plan.linkerFlags.none { it == "-no-pie" })
        assertTrue(result.plan.runtimeSources.any { it.fileName.toString() == "setjmp-aarch64.S" })
    }

    @Test
    fun selectsWindowsStartupAndPlatformAdapterWithoutCrt() {
        val manifest = requireNotNull(SdkManifestLoader.load(SdkManifestLocator.defaultManifestPath()).manifest)
        val target = TargetInfo(targetTriple = "windows-x86_64")
        val resolution = requireNotNull(SdkResolver.resolve(manifest, target).resolution)

        val result = RuntimeLinker.plan(resolution, target)

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        assertEquals("start.c", result.plan!!.startupSources.single().fileName.toString())
        assertTrue(result.plan.runtimeSources.any { it.toString().replace('\\', '/').contains("platform/windows/runtime.c") })
        assertTrue(result.plan.runtimeSources.any { it.toString().replace('\\', '/').contains("platform/windows/network.c") })
        assertTrue(result.plan.runtimeSources.any { it.fileName.toString() == "sync.c" })
        assertTrue(result.plan.runtimeSources.any { it.fileName.toString() == "sync_std.c" })
        assertTrue(result.plan.runtimeSources.any { it.fileName.toString() == "atomic.c" })
        assertTrue(result.plan.runtimeSources.any { it.fileName.toString() == "process.c" })
        assertTrue(result.plan.runtimeSources.any { it.fileName.toString() == "thread.c" })
        assertTrue(result.plan.runtimeSources.any { it.fileName.toString() == "net_address.c" })
        assertTrue(result.plan.runtimeSources.any { it.fileName.toString() == "net.c" })
        assertTrue(result.plan.runtimeSources.none { it.fileName.toString() == "network_dns.c" })
        assertTrue(result.plan.runtimeSources.none { it.fileName.toString().startsWith("thread-") })
        assertTrue(result.plan.linkerFlags.none { it.contains("thread-tls.ld") })
        assertTrue(result.plan.linkerFlags.contains("-lkernel32"))
        assertTrue(result.plan.compilerFlags.contains("-nostdlib"))
    }

    @Test
    fun rejectsUnimplementedDarwinSelfHostedRuntimeWithStableDiagnostic() {
        val manifest = requireNotNull(SdkManifestLoader.load(SdkManifestLocator.defaultManifestPath()).manifest)
        val target = TargetInfo(targetTriple = "darwin-aarch64")
        val resolution = requireNotNull(SdkResolver.resolve(manifest, target).resolution)

        val result = RuntimeLinker.plan(resolution, target)

        assertTrue(!result.isSuccessful)
        assertEquals("SDK013", result.diagnostics.single().code)
        assertEquals(
            "self-hosted runtime startup is not available for target 'darwin-aarch64'",
            result.diagnostics.single().message
        )
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
    fun compilerHelpersMustBePresentInTheSelectedRuntimePlan() {
        val manifest = requireNotNull(SdkManifestLoader.load(SdkManifestLocator.defaultManifestPath()).manifest)
        val resolution = requireNotNull(SdkResolver.resolve(manifest, TargetInfo()).resolution)
        val cplusPlan = requireNotNull(RuntimeLinker.plan(resolution, TargetInfo()).plan)
        assertTrue(RuntimeHelperCatalogue.validate(listOf("__cplus_format"), cplusPlan).isEmpty())

        val systemTarget = TargetInfo(buildProfile = BuildProfile(RuntimeProfile.SYSTEM, LibcProfile.C17))
        val systemPlan = requireNotNull(RuntimeLinker.plan(resolution, systemTarget).plan)
        assertTrue(RuntimeHelperCatalogue.validate(listOf("__cplus_format"), systemPlan).any { it.code == "RUNTIME002" })
        assertTrue(RuntimeHelperCatalogue.validate(listOf("__cplus_unknown"), cplusPlan).any { it.code == "RUNTIME001" })
    }

    @Test
    fun normalTerminationRunsHandlersInReverseRegistrationOrder() {
        assumeTrue(System.getProperty("os.name").contains("linux", ignoreCase = true))
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
                listOf("-I", resolution.layout.libcInclude.toString(), "-I", resolution.layout.runtimeInclude.toString()) +
                listOf(source.toString()) + plan.runtimeSources.map { it.toString() } +
                plan.startupSources.map { it.toString() } + plan.linkerFlags + listOf("-o", executable.toString())
        ).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()

        assertEquals(0, process.waitFor(), output)
        assertEquals(21, ProcessBuilder(executable.toString()).start().waitFor())

        val descriptor = requireNotNull(TargetRegistry.load(
            SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize().parent!!.parent!!.resolve("abi/linux-x86_64.toml")
        ).descriptor)
        val audit = RuntimeDependencyAuditor.inspect(executable, descriptor, BuildProfile())
        assertTrue(audit.isSuccessful, audit.diagnostics.joinToString())
    }
}
