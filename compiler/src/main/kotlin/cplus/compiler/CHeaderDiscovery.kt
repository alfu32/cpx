package cplus.compiler

import cplus.core.Diagnostic
import cplus.core.DiagnosticSeverity
import java.nio.file.Files
import java.nio.file.Path

data class CHeaderDiscoveryResult(
    val module: String,
    val header: Path?,
    val preprocessed: CHeaderPreprocessResult?,
    val diagnostics: List<Diagnostic>
) {
    val isSuccessful: Boolean get() = diagnostics.none { it.severity == DiagnosticSeverity.ERROR }
}

/** Resolves a C import against the ordered include roots selected for its compilation. */
class CHeaderDiscovery(
    private val preprocessor: CHeaderPreprocessor = CHeaderPreprocessor()
) {
    fun discover(module: String, environment: HeaderEnvironment): CHeaderDiscoveryResult {
        val relative = headerRelativePath(module)
            ?: return failure(module, "unsupported C header module path '" + module + "'", "CIMP018")
        val header = environment.includeSearchRoots
            .asSequence()
            .map { it.resolve(relative).toAbsolutePath().normalize() }
            .firstOrNull(Files::isRegularFile)
            ?: return failure(
                module,
                "C header for module '" + module + "' was not found in the configured include roots",
                "CIMP019"
            )
        val processed = preprocessor.preprocess(header, environment)
        return CHeaderDiscoveryResult(module, header, processed, processed.diagnostics)
    }

    internal fun headerRelativePath(module: String): Path? {
        val logical = when {
            module.startsWith("c.") -> module.removePrefix("c.").replace('.', '/')
            module.startsWith("c/") -> module.removePrefix("c/")
            else -> return null
        }
        val segments = logical.split('/')
        if (segments.isEmpty() || segments.any { !SEGMENT.matches(it) || it in setOf(".", "..") }) return null
        return Path.of(segments.dropLast(1).joinToString("/"), segments.last() + ".h")
    }

    private fun failure(module: String, message: String, code: String) = CHeaderDiscoveryResult(
        module,
        null,
        null,
        listOf(Diagnostic(DiagnosticSeverity.ERROR, message, null, code))
    )

    companion object {
        private val SEGMENT = Regex("[A-Za-z_][A-Za-z0-9_-]*")
    }
}
