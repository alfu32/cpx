package cplus.compiler

import cplus.core.Diagnostic
import cplus.core.DiagnosticSeverity
import java.nio.file.Files
import java.nio.file.Path

data class RuntimeLinkPlan(
    val profile: RuntimeProfile,
    val startupSources: List<Path>,
    val runtimeSources: List<Path>,
    val compilerFlags: List<String>,
    val linkerFlags: List<String>
)

data class RuntimeLinkPlanResult(
    val plan: RuntimeLinkPlan?,
    val diagnostics: List<Diagnostic>
) {
    val isSuccessful: Boolean
        get() = plan != null && diagnostics.none { it.severity == DiagnosticSeverity.ERROR }
}

object RuntimeLinker {
    fun plan(resolution: SdkResolution, target: TargetInfo): RuntimeLinkPlanResult {
        return when (target.buildProfile.runtime) {
            RuntimeProfile.SYSTEM -> RuntimeLinkPlanResult(
                RuntimeLinkPlan(RuntimeProfile.SYSTEM, emptyList(), emptyList(), emptyList(), emptyList()),
                emptyList()
            )
            RuntimeProfile.CPLUS, RuntimeProfile.FREESTANDING -> {
                val startup = resolution.layout.startupSource.resolve("start.S")
                val runtime = resolution.layout.runtimeSource.resolve("startup.c")
                val missing = listOf(startup, runtime).filterNot(Files::isRegularFile)
                if (missing.isNotEmpty()) {
                    RuntimeLinkPlanResult(
                        null,
                        missing.map { path ->
                            Diagnostic(
                                DiagnosticSeverity.ERROR,
                                "runtime startup component is missing: $path",
                                null,
                                "SDK012"
                            )
                        }
                    )
                } else {
                    RuntimeLinkPlanResult(
                        RuntimeLinkPlan(
                            target.buildProfile.runtime,
                            listOf(startup),
                            listOf(runtime),
                            listOf("-nostdlib", "-nodefaultlibs", "-nostartfiles"),
                            emptyList()
                        ),
                        emptyList()
                    )
                }
            }
        }
    }
}
