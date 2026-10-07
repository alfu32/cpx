package cplus.compiler

import cplus.backend.GeneratedCUnit
import cplus.core.Diagnostic
import cplus.core.DiagnosticSeverity
import cplus.core.Origin
import cplus.core.SourceFile
import cplus.core.SourceRange
import cplus.core.SourceRepository
import java.nio.file.Path

data class GeneratedCPosition(
    val path: Path,
    val line: Int,
    val column: Int
)

data class RemappedCCompilerDiagnostic(
    val severity: DiagnosticSeverity,
    val message: String,
    val generated: GeneratedCPosition,
    val origin: Origin?,
    val source: SourceFile?,
    val raw: String
) {
    val sourceRange: SourceRange?
        get() = origin?.primaryRange

    fun asDiagnostic(): Diagnostic = Diagnostic(
        severity = severity,
        message = message,
        range = sourceRange,
        code = if (origin == null) "CCOMP002" else "CCOMP001"
    )
}

/** Maps GCC/Clang and MSVC-style diagnostics through generated C ranges. */
class CCompilerDiagnosticRemapper(
    private val sources: SourceRepository
) {
    fun remap(output: String, generatedPath: Path, generated: GeneratedCUnit): List<RemappedCCompilerDiagnostic> {
        val normalizedGeneratedPath = generatedPath.toAbsolutePath().normalize()
        return output.lineSequence().mapNotNull { line ->
            val match = parse(line.trimEnd()) ?: return@mapNotNull null
            val path = Path.of(match.path.replace('\\', '/')).toAbsolutePath().normalize()
            val severity = severity(match.label.lowercase())
            val isGenerated = path == normalizedGeneratedPath ||
                path.fileName == normalizedGeneratedPath.fileName ||
                match.path.replace('\\', '/').substringAfterLast('/') == normalizedGeneratedPath.fileName.toString()
            val origin = if (isGenerated) {
                generated.mappingsForGeneratedLine(match.line).firstOrNull()?.origin
            } else {
                null
            }
            val source = origin?.primaryRange?.file?.let { file ->
                runCatching { sources.get(file) }.getOrNull()
            }
            RemappedCCompilerDiagnostic(
                severity,
                match.message,
                GeneratedCPosition(path, match.line, match.column),
                origin,
                source,
                line.trimEnd()
            )
        }.toList()
    }

    private fun parse(line: String): ParsedDiagnostic? {
        gccPattern.matchEntire(line)?.let { match ->
            return ParsedDiagnostic(
                match.groupValues[1],
                match.groupValues[2].toIntOrNull() ?: return null,
                match.groupValues[3].toIntOrNull() ?: return null,
                match.groupValues[4],
                match.groupValues[5]
            )
        }
        msvcPattern.matchEntire(line)?.let { match ->
            return ParsedDiagnostic(
                match.groupValues[1],
                match.groupValues[2].toIntOrNull() ?: return null,
                match.groupValues[3].toIntOrNull() ?: return null,
                match.groupValues[4],
                match.groupValues[5]
            )
        }
        return null
    }

    private fun severity(label: String): DiagnosticSeverity = when (label) {
        "warning" -> DiagnosticSeverity.WARNING
        "note" -> DiagnosticSeverity.INFO
        else -> DiagnosticSeverity.ERROR
    }

    private data class ParsedDiagnostic(
        val path: String,
        val line: Int,
        val column: Int,
        val label: String,
        val message: String
    )

    companion object {
        private val gccPattern = Regex(
            "^(.+):(\\d+):(\\d+):\\s*(fatal error|error|warning|note):\\s*(.*)$"
        )
        private val msvcPattern = Regex(
            "^(.+)\\((\\d+),(\\d+)\\):\\s*(fatal error|error|warning|note)\\s*(?:[A-Z]\\d+:\\s*)?(.*)$",
            RegexOption.IGNORE_CASE
        )
    }
}
