package cplus.compiler

data class PlatformAbiProfile(
    val target: String,
    val objectFormat: String,
    val startupEntry: String,
    val systemLibraries: Set<String>,
    val syscallMode: String,
    val supportedServices: Set<String>
)

object PlatformAbiRegistry {
    fun profile(descriptor: TargetAbiDescriptor): PlatformAbiProfile = when (descriptor.os) {
        "linux" -> PlatformAbiProfile(
            descriptor.targetTriple,
            descriptor.objectFormat,
            descriptor.startupEntry,
            descriptor.systemLibraries,
            "direct-syscall",
            setOf("memory", "file", "process", "time", "threads")
        )
        "windows" -> PlatformAbiProfile(
            descriptor.targetTriple,
            descriptor.objectFormat,
            descriptor.startupEntry,
            descriptor.systemLibraries,
            "declared-dll",
            setOf("memory", "file", "process", "sync")
        )
        "darwin" -> PlatformAbiProfile(
            descriptor.targetTriple,
            descriptor.objectFormat,
            descriptor.startupEntry,
            descriptor.systemLibraries,
            "system-userspace",
            setOf("memory", "file", "process", "time", "threads")
        )
        else -> PlatformAbiProfile(descriptor.targetTriple, descriptor.objectFormat, descriptor.startupEntry, descriptor.systemLibraries, "unsupported", emptySet())
    }
}

data class SyscallDefinition(val name: String, val number: Long, val resultConvention: String)

object LinuxSyscallCatalogue {
    private val common = listOf(
        SyscallDefinition("read", 0, "negative-errno"),
        SyscallDefinition("write", 1, "negative-errno"),
        SyscallDefinition("close", 3, "negative-errno"),
        SyscallDefinition("mmap", 9, "negative-errno"),
        SyscallDefinition("munmap", 11, "negative-errno"),
        SyscallDefinition("exit", 60, "never-returns")
    )

    fun forTarget(descriptor: TargetAbiDescriptor): List<SyscallDefinition> =
        if (descriptor.os == "linux" && descriptor.architecture in setOf("x86_64", "aarch64")) common else emptyList()
}
