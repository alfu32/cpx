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
            setOf("memory", "file", "process", "time", "threads", "socket-transport")
        )
        "windows" -> PlatformAbiProfile(
            descriptor.targetTriple,
            descriptor.objectFormat,
            descriptor.startupEntry,
            descriptor.systemLibraries,
            "declared-dll",
            setOf("memory", "file", "process", "sync", "socket-transport")
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
    private val x86_64Base = listOf(
        SyscallDefinition("read", 0, "negative-errno"),
        SyscallDefinition("write", 1, "negative-errno"),
        SyscallDefinition("close", 3, "negative-errno"),
        SyscallDefinition("mmap", 9, "negative-errno"),
        SyscallDefinition("munmap", 11, "negative-errno"),
        SyscallDefinition("exit", 60, "never-returns")
    )
    private val aarch64Base = listOf(
        SyscallDefinition("read", 63, "negative-errno"),
        SyscallDefinition("write", 64, "negative-errno"),
        SyscallDefinition("close", 57, "negative-errno"),
        SyscallDefinition("mmap", 222, "negative-errno"),
        SyscallDefinition("munmap", 215, "negative-errno"),
        SyscallDefinition("exit", 93, "never-returns")
    )

    fun forTarget(descriptor: TargetAbiDescriptor): List<SyscallDefinition> =
        when {
            descriptor.os != "linux" -> emptyList()
            descriptor.architecture == "x86_64" -> x86_64Base + listOf(
                SyscallDefinition("socket", 41, "negative-errno"),
                SyscallDefinition("connect", 42, "negative-errno"),
                SyscallDefinition("accept", 43, "negative-errno"),
                SyscallDefinition("sendto", 44, "negative-errno"),
                SyscallDefinition("recvfrom", 45, "negative-errno"),
                SyscallDefinition("shutdown", 48, "negative-errno"),
                SyscallDefinition("bind", 49, "negative-errno"),
                SyscallDefinition("listen", 50, "negative-errno"),
                SyscallDefinition("getsockname", 51, "negative-errno"),
                SyscallDefinition("getpeername", 52, "negative-errno"),
                SyscallDefinition("accept4", 288, "negative-errno")
            )
            descriptor.architecture == "aarch64" -> aarch64Base + listOf(
                SyscallDefinition("socket", 198, "negative-errno"),
                SyscallDefinition("bind", 200, "negative-errno"),
                SyscallDefinition("listen", 201, "negative-errno"),
                SyscallDefinition("accept", 202, "negative-errno"),
                SyscallDefinition("connect", 203, "negative-errno"),
                SyscallDefinition("getsockname", 204, "negative-errno"),
                SyscallDefinition("getpeername", 205, "negative-errno"),
                SyscallDefinition("sendto", 206, "negative-errno"),
                SyscallDefinition("recvfrom", 207, "negative-errno"),
                SyscallDefinition("shutdown", 210, "negative-errno"),
                SyscallDefinition("accept4", 242, "negative-errno")
            )
            else -> emptyList()
        }
}
