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
    val cCompiler: String = "cc"
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
        val includes = request.includeDirectories.distinct().flatMap { listOf("-I", it.toAbsolutePath().normalize().toString()) }
        val libraries = request.libraries.map { dependency ->
            when (dependency.kind) {
                CLinkDependencyKind.LOCAL -> dependency.value
                CLinkDependencyKind.FOREIGN -> "-l${dependency.value}"
            }
        }
        return listOf(request.cCompiler, "-std=${request.target.cDialect}") +
            plan.compilerFlags + includes +
            listOf(request.generatedSource.toString()) +
            plan.runtimeSources.map(Path::toString) +
            plan.startupSources.map(Path::toString) +
            request.sourceDependencies.map(Path::toString) +
            libraries + plan.linkerFlags + listOf("-o", request.output.toString())
    }

    fun link(request: LinkRequest, plan: RuntimeLinkPlan): LinkResult {
        request.generatedSource.parent?.let { Files.createDirectories(it) }
        request.output.parent?.let { Files.createDirectories(it) }
        val command = command(request, plan)
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        return LinkResult(command, process.waitFor(), output)
    }
}
