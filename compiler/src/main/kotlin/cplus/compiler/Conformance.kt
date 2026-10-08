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
            ConformanceCase("runtime.startup", ConformanceArea.RUNTIME, target.targetTriple, profile.runtime.name.lowercase(), "planned", "requires target-specific executable evidence"),
            ConformanceCase("abi.layout", ConformanceArea.ABI, target.targetTriple, "c", "planned", "descriptor-driven layout requires a target-specific conformance result"),
            ConformanceCase("std.memory", ConformanceArea.NATIVE_STD, target.targetTriple, "core", "planned", "source primitives require executable conformance evidence"),
            ConformanceCase("libc.c17.headers", ConformanceArea.LIBC, target.targetTriple, "c17", "planned", "header presence and behavior are reported by the C17 conformance runner"),
            ConformanceCase("platform.services", ConformanceArea.PLATFORM, target.targetTriple, "pal", if (target.os in setOf("linux", "windows", "darwin")) "planned" else "unsupported", "platform service capability requires a target-specific runtime report")
        )
    )
}
