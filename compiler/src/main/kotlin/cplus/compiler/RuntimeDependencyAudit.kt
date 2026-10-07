package cplus.compiler

import java.nio.file.Files
import java.nio.file.Path

data class RuntimeDependencyAudit(
    val binary: Path,
    val allowed: Set<String>,
    val observed: Set<String>,
    val unexpected: Set<String>,
    val unresolvedCompilerRuntime: Set<String>,
    val diagnostics: List<String>
) {
    val isSuccessful: Boolean get() = diagnostics.isEmpty()
}

/** Audits ELF/PE/Mach-O output without making host linker output authoritative. */
object RuntimeDependencyAuditor {
    fun inspect(binary: Path, target: TargetAbiDescriptor, profile: BuildProfile): RuntimeDependencyAudit {
        if (!Files.isRegularFile(binary)) {
            return RuntimeDependencyAudit(binary, target.systemLibraries, emptySet(), emptySet(), emptySet(), listOf("binary does not exist: $binary"))
        }
        val observed = mutableSetOf<String>()
        val unresolved = mutableSetOf<String>()
        val diagnostics = mutableListOf<String>()
        val dynamic = runTool(listOf("ldd", binary.toString()))
        dynamic.lines().forEach { line ->
            Regex("(?:=>\\s*)?([A-Za-z0-9_.+-]+(?:\\.so(?:\\.[0-9]+)*)?)").find(line)?.groupValues?.get(1)?.let(observed::add)
        }
        runTool(listOf("nm", "-u", binary.toString())).lines().forEach { line ->
            val symbol = line.trim().substringAfterLast(' ').takeIf { it.startsWith("__") || it.startsWith("_Unwind") }
            if (symbol != null && symbol.startsWith("__cplus_")) unresolved += symbol
        }
        if (profile.runtime != RuntimeProfile.SYSTEM) {
            val unexpected = observed.filter { it.contains("libc") || it.contains("libm") || it.contains("libstdc") || it.contains("ucrt", true) || it.contains("msvcrt", true) }.toSet()
            if (unexpected.isNotEmpty()) diagnostics += "self-hosted runtime has unexpected host dependency: ${unexpected.sorted().joinToString()}"
            if (unresolved.isNotEmpty()) diagnostics += "unresolved compiler-runtime symbols: ${unresolved.sorted().joinToString()}"
        }
        return RuntimeDependencyAudit(binary, target.systemLibraries, observed, observed - target.systemLibraries, unresolved, diagnostics)
    }

    private fun runTool(command: List<String>): String = runCatching {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        output
    }.getOrDefault("")
}
