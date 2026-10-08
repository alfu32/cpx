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
                val time = resolution.layout.runtimeSource.resolve("time.c")
                val math = resolution.layout.runtimeSource.resolve("math.c")
                val complexArithmetic = resolution.layout.runtimeSource.resolve("complex_arithmetic.c")
                val ctype = resolution.layout.runtimeSource.resolve("ctype.c")
                val locale = resolution.layout.runtimeSource.resolve("locale.c")
                val signal = resolution.layout.runtimeSource.resolve("signal.c")
                val wide = resolution.layout.runtimeSource.resolve("wide.c")
                val wctype = resolution.layout.runtimeSource.resolve("wctype.c")
                val filesystem = resolution.layout.runtimeSource.resolve("fs.c")
                val synchronization = resolution.layout.runtimeSource.resolve("sync.c")
                val synchronizationFacade = resolution.layout.runtimeSource.resolve("sync_std.c")
                val atomics = resolution.layout.runtimeSource.resolve("atomic.c")
                val process = resolution.layout.runtimeSource.resolve("process.c")
                val thread = resolution.layout.runtimeSource.resolve("thread.c")
                val networkAddress = resolution.layout.runtimeSource.resolve("net_address.c")
                val networkFacade = resolution.layout.runtimeSource.resolve("net.c")
                val platformRuntime = resolution.layout.platformSource.resolve("runtime.c")
                val windowsEmulatedTls = resolution.layout.runtimeSource.resolve("emutls_windows.c")
                val windowsStaticTls = resolution.layout.runtimeSource.resolve("tls_windows.c")
                val setjmp = when {
                    descriptor.os == "linux" && descriptor.architecture == "x86_64" ->
                        listOf(resolution.layout.runtimeSource.resolve("setjmp-x86_64.S"))
                    descriptor.os == "linux" && descriptor.architecture == "aarch64" ->
                        listOf(resolution.layout.runtimeSource.resolve("setjmp-aarch64.S"))
                    descriptor.os == "windows" && descriptor.architecture == "x86_64" ->
                        listOf(resolution.layout.runtimeSource.resolve("setjmp-windows-x86_64.S"))
                    else -> emptyList()
                }
                val threadStartup = if (descriptor.os == "linux") {
                    listOf(resolution.layout.platformSource.resolve("thread-${descriptor.architecture}.S"))
                } else {
                    emptyList()
                }
                val platformNetworkRuntime = when (descriptor.os) {
                    "linux" -> listOf(resolution.layout.platformSource.resolve("network_dns.c"))
                    "windows" -> listOf(resolution.layout.platformSource.resolve("network.c"))
                    else -> emptyList()
                }
                val threadTlsScript = if (descriptor.os == "linux") {
                    listOf(resolution.layout.platformSource.resolve("thread-tls.ld"))
                } else {
                    emptyList()
                }
                val targetSpecificRuntime = if (descriptor.os == "windows") {
                    listOf(windowsEmulatedTls, windowsStaticTls)
                } else {
                    emptyList()
                }
                val commonRuntime = listOf(
                    startup, runtime, compilerRuntime, allocator, formatter, stdio, libcCore, time,
                    math, ctype, locale, signal, wide, wctype, filesystem, synchronization,
                    synchronizationFacade, atomics, process, thread, networkAddress, networkFacade,
                    platformRuntime
                ) + targetSpecificRuntime +
                    if ("c17_complex" in descriptor.features) listOf(complexArithmetic) else emptyList()
                val missing = (commonRuntime + setjmp + threadStartup + platformNetworkRuntime + threadTlsScript)
                    .filterNot(Files::isRegularFile)
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
                            commonRuntime.drop(1) + setjmp + threadStartup + platformNetworkRuntime,
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
                                "linux" -> buildList {
                                    addAll(listOf("-Wl,-e,_start", "-Wl,--build-id=none", "-static"))
                                    if (descriptor.architecture == "x86_64") add("-no-pie")
                                    addAll(threadTlsScript.map { "-Wl,-T,${it.toAbsolutePath().normalize()}" })
                                }
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
