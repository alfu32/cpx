package cplus.compiler

import cplus.semantic.SemanticAnalyzer
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HeaderEnvironmentTest {
    @Test
    fun compileAndIncrementalRequestsExposeTheirOwnHeaderEnvironment() {
        val root = Files.createTempDirectory("cplus-header-environment")
        val sdkManifest = SdkManifestLocator.defaultManifestPath()
        val includeRoot = Files.createDirectories(root.resolve("custom include"))
        val sysroot = Files.createDirectories(root.resolve("sysroot"))
        val main = root.resolve("main.cp")
        val helper = root.resolve("helper.cp")
        Files.writeString(main, "import { helper } from \"./helper.cp\";\nint main() { return helper(); }\n")
        Files.writeString(helper, "pub int helper() { return 0; }\n")

        val request = CompileRequest(
            sources = listOf(main, helper),
            cIncludeDirectories = listOf(includeRoot),
            sdkManifest = sdkManifest,
            externalSysroot = sysroot,
            cCompiler = "custom-c-driver"
        )
        val direct = CPlusCompiler().compile(request)
        val directEnvironment = assertNotNull(direct.sdkResolution?.headerEnvironment)
        assertTrue(direct.isSuccessful, direct.diagnostics.joinToString())
        assertEquals("custom-c-driver", directEnvironment.cCompiler)
        assertEquals(
            listOf(
                directEnvironment.sdkLibcInclude.toAbsolutePath().normalize(),
                directEnvironment.sdkRuntimeInclude.toAbsolutePath().normalize(),
                includeRoot.toAbsolutePath().normalize()
            ),
            directEnvironment.includeSearchRoots
        )
        assertEquals(sysroot.toAbsolutePath().normalize(), directEnvironment.externalSysroot)

        val incremental = IncrementalCompiler().compile(request)
        val incrementalEnvironment = assertNotNull(incremental.result.sdkResolution?.headerEnvironment)
        assertTrue(incremental.isSuccessful, incremental.result.diagnostics.joinToString())
        assertEquals(directEnvironment, incrementalEnvironment)
    }

    @Test
    fun textWorkspacePassesResolvedEnvironmentToProvisionalAndFinalAnalysis() {
        val root = Files.createTempDirectory("cplus-header-text")
        val sourceA = root.resolve("main.cp")
        val sourceB = root.resolve("helper.cp")
        val observed = mutableListOf<HeaderEnvironment>()
        val includeRoot = Files.createDirectories(root.resolve("text include"))
        val sysroot = Files.createDirectories(root.resolve("text sysroot"))
        val context = CompilerContext(
            semanticAnalyzerFactory = { environment ->
                observed += environment
                SemanticAnalyzer()
            }
        )
        val result = CPlusCompiler(context).compileTextWorkspace(
            listOf(
                TextSource(sourceA, "import { helper } from \"./helper.cp\";\nint main() { return helper(); }\n"),
                TextSource(sourceB, "pub int helper() { return 0; }\n")
            ),
            sdkManifest = SdkManifestLocator.defaultManifestPath(),
            cCompiler = "text-c-driver",
            cIncludeDirectories = listOf(includeRoot),
            externalSysroot = sysroot
        )

        val resolved = assertNotNull(result.sdkResolution?.headerEnvironment)
        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        assertEquals("text-c-driver", resolved.cCompiler)
        assertTrue(observed.size >= 3, "expected per-source provisional analysis and workspace analysis")
        assertTrue(observed.all { it == resolved }, "every analysis must receive the same immutable request environment")
        assertEquals(listOf(includeRoot.toAbsolutePath().normalize()), resolved.includeDirectories)
        assertEquals(sysroot.toAbsolutePath().normalize(), resolved.externalSysroot)
    }

    @Test
    fun compilerAndSdkChangesProduceIndependentEnvironments() {
        val root = Files.createTempDirectory("cplus-header-isolation")
        val sdk = assertNotNull(SdkManifestLoader.load(SdkManifestLocator.defaultManifestPath()).manifest)
        val linux = assertNotNull(SdkResolver.resolve(sdk, TargetInfo()).resolution)
        val linuxAbi = assertNotNull(TargetRegistry.load(linux.layout.abiDescriptor).descriptor)
        val linuxRequest = CompileRequest(emptyList(), cCompiler = "driver-one")
        val first = HeaderEnvironment.create(linuxRequest, linux, linuxAbi)

        val windowsTarget = TargetInfo(targetTriple = "windows-x86_64")
        val windowsSdk = assertNotNull(SdkResolver.resolve(sdk, windowsTarget).resolution)
        val windowsAbi = assertNotNull(TargetRegistry.load(windowsSdk.layout.abiDescriptor).descriptor)
        val include = Files.createDirectories(root.resolve("include"))
        val second = HeaderEnvironment.create(
            CompileRequest(
                emptyList(),
                target = windowsTarget,
                cIncludeDirectories = listOf(include),
                cCompiler = "driver-two"
            ),
            windowsSdk,
            windowsAbi
        )

        assertEquals("driver-one", first.cCompiler)
        assertEquals("driver-two", second.cCompiler)
        assertEquals("linux-x86_64", first.abi.targetTriple)
        assertEquals("windows-x86_64", second.abi.targetTriple)
        assertTrue(include.toAbsolutePath().normalize() !in first.includeSearchRoots)
        assertTrue(include.toAbsolutePath().normalize() in second.includeSearchRoots)
        assertTrue(first !== second)
    }
}
