package cplus.core

enum class DiagnosticSeverity {
    ERROR,
    WARNING,
    INFO
}

data class Diagnostic(
    val severity: DiagnosticSeverity,
    val message: String,
    val range: SourceRange? = null,
    val code: String? = null
)

class DiagnosticBag {
    private val entries = mutableListOf<Diagnostic>()

    val diagnostics: List<Diagnostic>
        get() = entries.toList()

    val hasErrors: Boolean
        get() = entries.any { it.severity == DiagnosticSeverity.ERROR }

    fun add(diagnostic: Diagnostic) {
        entries += diagnostic
    }

    fun addAll(diagnostics: Iterable<Diagnostic>) {
        entries += diagnostics
    }

    fun error(message: String, range: SourceRange? = null, code: String? = null) {
        add(Diagnostic(DiagnosticSeverity.ERROR, message, range, code))
    }

    fun warning(message: String, range: SourceRange? = null, code: String? = null) {
        add(Diagnostic(DiagnosticSeverity.WARNING, message, range, code))
    }
}
