package cplus.compiler

enum class ConformanceArea { NATIVE_STD, LIBC, ABI, PLATFORM, RUNTIME }

data class ConformanceCase(
    val id: String,
    val area: ConformanceArea,
    val target: String,
    val profile: String,
    val status: String,
    val notes: String
)

data class ConformanceReport(val cases: List<ConformanceCase>) {
    val passed: List<ConformanceCase> get() = cases.filter { it.status == "pass" }
    val failed: List<ConformanceCase> get() = cases.filter { it.status == "fail" }
    val planned: List<ConformanceCase> get() = cases.filter { it.status == "planned" }
    val unsupported: List<ConformanceCase> get() = cases.filter { it.status == "unsupported" }
    val isComplete: Boolean get() = failed.isEmpty() && planned.isEmpty() && unsupported.isEmpty()
}

object ConformanceMatrix {
    fun initial(target: TargetAbiDescriptor, profile: BuildProfile): ConformanceReport = ConformanceReport(
        listOf(
            ConformanceCase("runtime.startup", ConformanceArea.RUNTIME, target.targetTriple, profile.runtime.name.lowercase(), if (target.os == "linux" && target.architecture == "x86_64") "pass" else "planned", "self-hosted Linux startup is executable-tested"),
            ConformanceCase("abi.layout", ConformanceArea.ABI, target.targetTriple, "c", "pass", "descriptor-driven struct/union layout"),
            ConformanceCase("std.memory", ConformanceArea.NATIVE_STD, target.targetTriple, "core", "pass", "copy/move/set/compare source primitives"),
            ConformanceCase("libc.c17.headers", ConformanceArea.LIBC, target.targetTriple, "c17", "planned", "remaining C17 header families are delivered incrementally"),
            ConformanceCase("platform.services", ConformanceArea.PLATFORM, target.targetTriple, "pal", if (target.os in setOf("linux", "windows", "darwin")) "planned" else "fail", "platform service contracts are target-isolated")
        )
    )
}
