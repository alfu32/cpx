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

/** Maps standard GCC/Clang file:line:column diagnostics through generated C ranges. */
class CCompilerDiagnosticRemapper(
    private val sources: SourceRepository
) {
    fun remap(output: String, generatedPath: Path, generated: GeneratedCUnit): List<RemappedCCompilerDiagnostic> {
        val normalizedGeneratedPath = generatedPath.toAbsolutePath().normalize()
        return output.lineSequence().mapNotNull { line ->
            val match = diagnosticPattern.matchEntire(line.trimEnd()) ?: return@mapNotNull null
            val path = Path.of(match.groupValues[1]).toAbsolutePath().normalize()
            val sourceLine = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
            val sourceColumn = match.groupValues[3].toIntOrNull() ?: return@mapNotNull null
            val severity = severity(match.groupValues[4])
            val message = match.groupValues[5]
            val isGenerated = path == normalizedGeneratedPath || path.fileName == normalizedGeneratedPath.fileName
            val origin = if (isGenerated) {
                generated.mappingsForGeneratedLine(sourceLine).firstOrNull()?.origin
            } else {
                null
            }
            val source = origin?.primaryRange?.file?.let { file ->
                runCatching { sources.get(file) }.getOrNull()
            }
            RemappedCCompilerDiagnostic(
                severity,
                message,
                GeneratedCPosition(path, sourceLine, sourceColumn),
                origin,
                source,
                line.trimEnd()
            )
        }.toList()
    }

    private fun severity(label: String): DiagnosticSeverity = when (label) {
        "warning" -> DiagnosticSeverity.WARNING
        "note" -> DiagnosticSeverity.INFO
        else -> DiagnosticSeverity.ERROR
    }

    companion object {
        private val diagnosticPattern = Regex(
            "^(.+):(\\d+):(\\d+):\\s*(fatal error|error|warning|note):\\s*(.*)$"
        )
    }
}
