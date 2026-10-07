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
                val descriptorResult = resolution.targetDescriptor?.let {
                    TargetDescriptorResult(it, emptyList())
                } ?: TargetRegistry.load(resolution.layout.abiDescriptor)
                if (!descriptorResult.isSuccessful) {
                    return RuntimeLinkPlanResult(null, descriptorResult.diagnostics)
                }
                val descriptor = descriptorResult.descriptor!!
                if (descriptor.targetTriple != target.targetTriple) {
                    return RuntimeLinkPlanResult(
                        null,
                        listOf(Diagnostic(
                            DiagnosticSeverity.ERROR,
                            "SDK target '${descriptor.targetTriple}' does not match requested target '${target.targetTriple}'",
                            null,
                            "SDK013"
                        ))
                    )
                }
                val startupName = when {
                    descriptor.os == "linux" && descriptor.architecture in setOf("x86_64", "aarch64") -> "start.S"
                    descriptor.os == "windows" && descriptor.architecture in setOf("x86_64", "aarch64") -> "start.c"
                    else -> null
                }
                if (startupName == null) {
                    return RuntimeLinkPlanResult(
                        null,
                        listOf(Diagnostic(
                            DiagnosticSeverity.ERROR,
                            "self-hosted runtime startup is not available for target '${target.targetTriple}'",
                            null,
                            "SDK013"
                        ))
                    )
                }
                val startup = resolution.layout.startupSource.resolve(startupName)
                val runtime = resolution.layout.runtimeSource.resolve("startup.c")
                val compilerRuntime = resolution.layout.runtimeSource.resolve("memory.c")
                val allocator = resolution.layout.runtimeSource.resolve("allocator.c")
                val formatter = resolution.layout.runtimeSource.resolve("format.c")
                val stdio = resolution.layout.runtimeSource.resolve("stdio.c")
                val libcCore = resolution.layout.runtimeSource.resolve("libc_core.c")
                val filesystem = resolution.layout.runtimeSource.resolve("fs.c")
                val platformRuntime = resolution.layout.platformSource.resolve("runtime.c")
                val missing = listOf(startup, runtime, compilerRuntime, allocator, formatter, stdio, libcCore, filesystem, platformRuntime).filterNot(Files::isRegularFile)
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
                            listOf(runtime, compilerRuntime, allocator, formatter, stdio, libcCore, filesystem, platformRuntime),
                            buildList {
                                addAll(
                                    listOf(
                                        "-nostdlib",
                                        "-nodefaultlibs",
                                        "-nostartfiles",
                                        "-ffreestanding",
                                        "-fno-builtin",
                                        "-fno-stack-protector",
                                        "-DCPLUS_RUNTIME_NO_WEAK"
                                    )
                                )
                                if (descriptor.os == "linux") {
                                    add("-fno-pie")
                                }
                            },
                            when (descriptor.os) {
                                "linux" -> listOf("-Wl,-e,_start", "-Wl,--build-id=none", "-no-pie")
                                "windows" -> listOf("-Wl,--entry,mainCRTStartup", "-Wl,--subsystem,console", "-lkernel32")
                                else -> emptyList()
                            }
                        ),
                        emptyList()
                    )
                }
            }
        }
    }
}
