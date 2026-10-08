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

internal data class RuntimeAuditToolOutput(val text: String, val exitCode: Int)

/**
 * Audits the output format that belongs to the selected target, rather than
 * assuming that the host is Linux/ELF. A self-hosted build may use the host C
 * driver as a syntax/object tool, but it must not inherit the host C runtime.
 */
object RuntimeDependencyAuditor {
    fun inspect(binary: Path, target: TargetAbiDescriptor, profile: BuildProfile): RuntimeDependencyAudit =
        inspect(binary, target, profile, ::runTool)

    internal fun inspect(
        binary: Path,
        target: TargetAbiDescriptor,
        profile: BuildProfile,
        toolRunner: (List<String>) -> RuntimeAuditToolOutput?
    ): RuntimeDependencyAudit {
        if (!Files.isRegularFile(binary)) {
            return RuntimeDependencyAudit(binary, target.systemLibraries, emptySet(), emptySet(), emptySet(), listOf("binary does not exist: $binary"))
        }

        val observed = linkedSetOf<String>()
        val unresolved = linkedSetOf<String>()
        val diagnostics = mutableListOf<String>()
        when (target.objectFormat) {
            "elf" -> inspectElf(binary, target, observed, diagnostics, toolRunner)
            "pe-coff" -> inspectPe(binary, observed, diagnostics, toolRunner)
            "mach-o" -> inspectMachO(binary, observed, diagnostics, toolRunner)
            else -> diagnostics += "no runtime dependency inspector for object format '${target.objectFormat}'"
        }

        if (target.objectFormat != "pe-coff") {
            val symbols = toolRunner(listOf("nm", "-u", binary.toString()))
            if (symbols == null || symbols.exitCode != 0) {
                diagnostics += "unable to inspect unresolved symbols; install a working nm tool"
            } else {
                symbols.text.lineSequence().forEach { line ->
                    val symbol = line.trim().substringAfterLast(' ').takeIf { it.isNotBlank() }
                    if (symbol != null && isCompilerRuntimeSymbol(symbol)) unresolved += symbol
                }
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

    private fun inspectElf(
        binary: Path,
        target: TargetAbiDescriptor,
        observed: MutableSet<String>,
        diagnostics: MutableList<String>,
        toolRunner: (List<String>) -> RuntimeAuditToolOutput?
    ) {
        val header = toolRunner(listOf("readelf", "-h", binary.toString()))
        if (header == null || header.exitCode != 0 || "ELF Header:" !in header.text) {
            diagnostics += "unable to verify ELF target format; install a working readelf tool"
            return
        }
        val expectedClass = "ELF${target.pointerBits}"
        val actualClass = header.text.lineSequence()
            .firstOrNull { it.trimStart().startsWith("Class:") }
            ?.substringAfter(':')
            ?.trim()
        val machine = header.text.lineSequence()
            .firstOrNull { it.trimStart().startsWith("Machine:") }
            ?.substringAfter(':')
            ?.trim()
            ?.lowercase()
        val expectedMachine = when (target.architecture) {
            "x86_64" -> "x86-64"
            "aarch64" -> "aarch64"
            "i386", "i686" -> "80386"
            "riscv64" -> "risc-v"
            else -> null
        }
        if (actualClass != expectedClass || expectedMachine == null || machine?.contains(expectedMachine) != true) {
            diagnostics += "ELF binary does not match target '${target.targetTriple}'"
            return
        }
        val dynamic = toolRunner(listOf("readelf", "-d", binary.toString()))
        if (dynamic == null || dynamic.exitCode != 0) {
            diagnostics += "unable to inspect ELF dynamic dependencies; install a working readelf tool"
        } else {
            dynamic.text.lineSequence().forEach { line ->
                Regex("Shared library: \\[([^]]+)]").find(line)?.groupValues?.get(1)?.let(observed::add)
            }
        }
        val programHeaders = toolRunner(listOf("readelf", "-l", binary.toString()))
        if (programHeaders == null || programHeaders.exitCode != 0) {
            diagnostics += "unable to inspect ELF program headers; install a working readelf tool"
            return
        }
        programHeaders.text.lineSequence().firstNotNullOfOrNull { line ->
            Regex("Requesting program interpreter: ([^]]+)").find(line)?.groupValues?.get(1)
        }?.let { interpreter ->
            observed += interpreter
            diagnostics += "self-hosted ELF has a dynamic program interpreter: $interpreter"
        }
    }

    private fun inspectPe(
        binary: Path,
        observed: MutableSet<String>,
        diagnostics: MutableList<String>,
        toolRunner: (List<String>) -> RuntimeAuditToolOutput?
    ) {
        val commands = listOf(
            listOf("objdump", "-p", binary.toString()),
            listOf("llvm-objdump", "-p", binary.toString()),
            listOf("dumpbin", "/DEPENDENTS", binary.toString())
        )
        val output = commands.firstNotNullOfOrNull { command ->
            toolRunner(command)?.takeIf { result ->
                result.exitCode == 0 && isRecognizedPeOutput(command.first(), result.text)
            }
        }
        if (output == null) {
            diagnostics += "unable to verify PE/COFF target format or inspect imports; install objdump, llvm-objdump, or dumpbin"
            return
        }
        output.text.lineSequence().forEach { line ->
            Regex("DLL Name:\\s*([^\\s]+)", RegexOption.IGNORE_CASE).find(line)?.groupValues?.get(1)?.let(observed::add)
            line.trim().takeIf { it.matches(Regex("[A-Za-z0-9_.-]+\\.dll", RegexOption.IGNORE_CASE)) }?.let(observed::add)
        }
    }

    private fun inspectMachO(
        binary: Path,
        observed: MutableSet<String>,
        diagnostics: MutableList<String>,
        toolRunner: (List<String>) -> RuntimeAuditToolOutput?
    ) {
        val header = toolRunner(listOf("otool", "-hv", binary.toString()))
        if (header == null || header.exitCode != 0 || "Mach header" !in header.text || "MH_" !in header.text) {
            diagnostics += "unable to verify Mach-O target format; install a working otool tool"
            return
        }
        val output = toolRunner(listOf("otool", "-L", binary.toString()))
        if (output == null || output.exitCode != 0) {
            diagnostics += "unable to inspect Mach-O dependencies; install otool"
            return
        }
        val lines = output.text.lineSequence().toList()
        if (lines.firstOrNull()?.trim()?.endsWith(':') != true) {
            diagnostics += "unable to parse Mach-O dependency output from otool"
            return
        }
        lines.drop(1).forEach { line ->
            line.trim().substringBefore(' ').takeIf(String::isNotBlank)?.let(observed::add)
        }
    }

    private fun isRecognizedPeOutput(tool: String, text: String): Boolean = when {
        tool.equals("dumpbin", ignoreCase = true) ->
            Regex("(?im)^\\s*Dump of file:").containsMatchIn(text) &&
                Regex("(?im)^\\s*(Image has the following dependencies:|There are no imports in this file\\.)")
                    .containsMatchIn(text)
        else -> Regex("(?im)^.*file format pei-[a-z0-9_-]+\\s*$").containsMatchIn(text)
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

    private fun isCompilerRuntimeSymbol(symbol: String): Boolean = when {
        symbol == "__cplus_flush_streams" -> false
        symbol.startsWith("__cplus_") -> true
        symbol.startsWith("__emutls_") -> true
        symbol.startsWith("__cxa_") -> true
        symbol.startsWith("_Unwind_") -> true
        symbol.startsWith("__atomic_") -> true
        symbol == "__gxx_personality_v0" -> true
        symbol in setOf("__udivti3", "__umodti3", "__divti3", "__modti3", "__multi3", "__muloti4") -> true
        else -> false
    }

    private fun runTool(command: List<String>): RuntimeAuditToolOutput? = runCatching {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        RuntimeAuditToolOutput(output, process.waitFor())
    }.getOrNull()
}
