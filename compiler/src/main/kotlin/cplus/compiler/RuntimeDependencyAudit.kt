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

/**
 * Audits the output format that belongs to the selected target, rather than
 * assuming that the host is Linux/ELF. A self-hosted build may use the host C
 * driver as a syntax/object tool, but it must not inherit the host C runtime.
 */
object RuntimeDependencyAuditor {
    fun inspect(binary: Path, target: TargetAbiDescriptor, profile: BuildProfile): RuntimeDependencyAudit {
        if (!Files.isRegularFile(binary)) {
            return RuntimeDependencyAudit(binary, target.systemLibraries, emptySet(), emptySet(), emptySet(), listOf("binary does not exist: $binary"))
        }

        val observed = linkedSetOf<String>()
        val unresolved = linkedSetOf<String>()
        val diagnostics = mutableListOf<String>()
        val expectedFormat = when (target.objectFormat) {
            "elf" -> "ELF"
            "pe-coff" -> "PE"
            "mach-o" -> "Mach-O"
            else -> null
        }
        val fileDescription = runTool(listOf("file", binary.toString()))?.text.orEmpty()
        if (expectedFormat != null && fileDescription.isNotBlank() && !fileDescription.contains(expectedFormat, ignoreCase = true)) {
            diagnostics += "binary format does not match target '${target.targetTriple}': expected $expectedFormat, observed $fileDescription"
        }

        when (target.objectFormat) {
            "elf" -> inspectElf(binary, observed, diagnostics)
            "pe-coff" -> inspectPe(binary, observed, diagnostics)
            "mach-o" -> inspectMachO(binary, observed, diagnostics)
            else -> diagnostics += "no runtime dependency inspector for object format '${target.objectFormat}'"
        }

        runTool(listOf("nm", "-u", binary.toString()))?.text.orEmpty().lineSequence().forEach { line ->
            val symbol = line.trim().substringAfterLast(' ').takeIf { it.isNotBlank() }
            if (symbol != null && symbol.startsWith("__cplus_") && symbol !in setOf("__cplus_flush_streams")) {
                unresolved += symbol
            }
        }

        val normalizedAllowed = target.systemLibraries.map(::normalizeLibrary).toSet()
        val unexpected = observed.filter { normalizeLibrary(it) !in normalizedAllowed }.toSet()
        if (profile.runtime != RuntimeProfile.SYSTEM) {
            val forbidden = unexpected.filter(::isForbiddenHostDependency).toSet()
            if (forbidden.isNotEmpty()) {
                diagnostics += "self-hosted runtime has unexpected host dependency: ${forbidden.sorted().joinToString()}"
            }
            val undeclared = unexpected - forbidden
            if (undeclared.isNotEmpty()) {
                diagnostics += "self-hosted runtime has undeclared target dependency: ${undeclared.sorted().joinToString()}"
            }
            if (unresolved.isNotEmpty()) {
                diagnostics += "unresolved compiler-runtime symbols: ${unresolved.sorted().joinToString()}"
            }
        }
        return RuntimeDependencyAudit(binary, target.systemLibraries, observed, unexpected, unresolved, diagnostics)
    }

    private fun inspectElf(binary: Path, observed: MutableSet<String>, diagnostics: MutableList<String>) {
        runTool(listOf("ldd", binary.toString()))?.text?.lineSequence()?.forEach { line ->
            Regex("([A-Za-z0-9_./+-]+\\.so(?:\\.[0-9]+)*)").find(line)?.groupValues?.get(1)?.let(observed::add)
        }
        runTool(listOf("readelf", "-l", binary.toString()))?.text?.lineSequence()?.firstNotNullOfOrNull { line ->
            Regex("Requesting program interpreter: ([^]]+)").find(line)?.groupValues?.get(1)
        }?.let { interpreter ->
            observed += interpreter
            diagnostics += "self-hosted ELF has a dynamic program interpreter: $interpreter"
        }
    }

    private fun inspectPe(binary: Path, observed: MutableSet<String>, diagnostics: MutableList<String>) {
        val output = runTool(listOf("objdump", "-p", binary.toString()))
            ?: runTool(listOf("llvm-objdump", "-p", binary.toString()))
        if (output == null) {
            diagnostics += "unable to inspect PE/COFF imports; install objdump or llvm-objdump"
            return
        }
        output.text.lineSequence().forEach { line ->
            Regex("DLL Name:\\s*([^\\s]+)", RegexOption.IGNORE_CASE).find(line)?.groupValues?.get(1)?.let(observed::add)
        }
    }

    private fun inspectMachO(binary: Path, observed: MutableSet<String>, diagnostics: MutableList<String>) {
        val output = runTool(listOf("otool", "-L", binary.toString()))
        if (output == null) {
            diagnostics += "unable to inspect Mach-O dependencies; install otool"
            return
        }
        output.text.lineSequence().drop(1).forEach { line ->
            line.trim().substringBefore(' ').takeIf(String::isNotBlank)?.let(observed::add)
        }
    }

    private fun normalizeLibrary(value: String): String {
        val name = value.substringAfterLast('/').substringAfterLast('\\').lowercase()
        return name
            .removeSuffix(".dll")
            .replace(Regex("\\.so(?:\\.[0-9]+)*$"), "")
            .removeSuffix(".dylib")
    }

    private fun isForbiddenHostDependency(value: String): Boolean {
        val name = normalizeLibrary(value)
        return name.contains("libc") ||
            name.contains("libm") ||
            name.contains("libstdc") ||
            name.contains("libgcc") ||
            name.contains("ucrt") ||
            name.contains("msvcrt") ||
            name.contains("vcruntime") ||
            name.contains("api-ms-win-crt") ||
            name.contains("mingw") ||
            name.contains("cygwin")
    }

    private data class ToolOutput(val text: String)

    private fun runTool(command: List<String>): ToolOutput? = runCatching {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        ToolOutput(output)
    }.getOrNull()
}
