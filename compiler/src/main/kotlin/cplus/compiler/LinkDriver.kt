package cplus.compiler

import java.nio.file.Files
import java.nio.file.Path

data class LinkRequest(
    val generatedSource: Path,
    val output: Path,
    val target: TargetInfo,
    val sdk: SdkResolution,
    val includeDirectories: List<Path> = emptyList(),
    val sourceDependencies: List<Path> = emptyList(),
    val libraries: List<CLinkDependency> = emptyList(),
    val cCompiler: String? = null
)

data class LinkResult(
    val command: List<String>,
    val exitCode: Int,
    val output: String
) {
    val isSuccessful: Boolean get() = exitCode == 0
}

/**
 * Target-aware C compiler invocation.  The compiler remains an interchangeable
 * downstream tool; startup/default-library policy comes from RuntimeLinker.
 */
object LinkDriver {
    fun command(request: LinkRequest, plan: RuntimeLinkPlan): List<String> {
        val compiler = CCompilerToolchains.resolve(request.target, request.cCompiler)
        return if (CCompilerToolchains.isMsvcStyle(compiler)) {
            msvcCommand(request, plan, compiler)
        } else {
            gccLikeCommand(request, plan, compiler)
        }
    }

    fun link(request: LinkRequest, plan: RuntimeLinkPlan): LinkResult {
        val command = command(request, plan)
        val descriptor = request.sdk.targetDescriptor
            ?: TargetRegistry.load(request.sdk.layout.abiDescriptor).descriptor
        val featureDiagnostics = descriptor?.let {
            CCompilerToolchains.validateTargetFeatures(it, command.first())
        }.orEmpty()
        if (featureDiagnostics.isNotEmpty()) {
            return LinkResult(command, 1, featureDiagnostics.joinToString("\n"))
        }
        request.generatedSource.parent?.let { Files.createDirectories(it) }
        request.output.parent?.let { Files.createDirectories(it) }
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        return LinkResult(command, process.waitFor(), output)
    }

    private fun gccLikeCommand(request: LinkRequest, plan: RuntimeLinkPlan, compiler: String): List<String> {
        val includes = (listOf(request.sdk.layout.libcInclude, request.sdk.layout.runtimeInclude) + request.includeDirectories).distinct().flatMap {
            listOf("-I", it.toAbsolutePath().normalize().toString())
        }
        val libraries = request.libraries.map(::gnuLibrary)
        return listOf(compiler, "-std=${request.target.cDialect}") +
            CCompilerToolchains.targetFlags(request.target, compiler) + targetAbiFlags(request, compiler) +
            plan.compilerFlags + includes +
            listOf(request.generatedSource.toString()) +
            plan.runtimeSources.map(Path::toString) +
            plan.startupSources.map(Path::toString) +
            request.sourceDependencies.map(Path::toString) +
            libraries + plan.linkerFlags + listOf("-o", request.output.toString())
    }

    private fun msvcCommand(request: LinkRequest, plan: RuntimeLinkPlan, compiler: String): List<String> {
        val includes = (listOf(request.sdk.layout.libcInclude, request.sdk.layout.runtimeInclude) + request.includeDirectories).distinct().flatMap {
            listOf("/I${it.toAbsolutePath().normalize()}")
        }
        val sources = listOf(request.generatedSource) + plan.runtimeSources + plan.startupSources + request.sourceDependencies
        val libraries = request.libraries.map(::msvcLibrary)
        val runtimeLibraries = plan.linkerFlags.mapNotNull { flag ->
            when {
                flag == "-lkernel32" -> "kernel32.lib"
                else -> null
            }
        }
        val clangFlags = if (CCompilerToolchains.classify(compiler).kind == CCompilerKind.CLANG_CL) {
            listOf("/clang:-ffreestanding", "/clang:-fno-builtin", "/clang:-fno-stack-protector", "/clang:-nostdlib")
        } else {
            emptyList()
        }
        return listOf(compiler, "/nologo", "/std:c17", "/GS-", "/Oi-", "/DCPLUS_RUNTIME_NO_WEAK") +
            targetAbiFlags(request, compiler) +
            clangFlags + includes + sources.map(Path::toString) +
            listOf("/link", "/NODEFAULTLIB", "/ENTRY:mainCRTStartup", "/SUBSYSTEM:CONSOLE", "/OUT:${request.output}") +
            runtimeLibraries + libraries
    }

    private fun gnuLibrary(dependency: CLinkDependency): String = when (dependency.kind) {
        CLinkDependencyKind.LOCAL -> dependency.value
        CLinkDependencyKind.FOREIGN -> "-l${dependency.value}"
    }

    private fun msvcLibrary(dependency: CLinkDependency): String = when (dependency.kind) {
        CLinkDependencyKind.LOCAL -> dependency.value
        CLinkDependencyKind.FOREIGN -> if (dependency.value.endsWith(".lib", ignoreCase = true)) dependency.value else "${dependency.value}.lib"
    }

    private fun targetAbiFlags(request: LinkRequest, compiler: String): List<String> {
        val descriptor = request.sdk.targetDescriptor
            ?: TargetRegistry.load(request.sdk.layout.abiDescriptor).descriptor
            ?: return emptyList()
        return CCompilerToolchains.targetAbiFlags(descriptor, compiler)
    }
}
