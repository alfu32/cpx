package cplus.compiler

import cplus.core.Diagnostic
import cplus.core.DiagnosticSeverity
import java.nio.file.Files
import java.nio.file.Path

/** A target-independent declaration of a compiler-owned operation. */
data class IntrinsicDefinition(
    val name: String,
    val family: String,
    val arity: Int,
    val lowering: String,
    val targets: Set<String>,
    val requiredFeatures: Set<String> = emptySet()
)

data class IntrinsicRegistryResult(
    val definitions: List<IntrinsicDefinition>,
    val diagnostics: List<Diagnostic>
) {
    val isSuccessful: Boolean
        get() = diagnostics.none { it.severity == DiagnosticSeverity.ERROR }
}

/**
 * Reads the SDK intrinsic catalogue.  The catalogue is intentionally data,
 * not a Kotlin switch, so cross-target SDK packages can add operations
 * without changing compiler logic.
 */
object IntrinsicRegistry {
    private val assignment = Regex("^([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(.*)$")

    fun load(path: Path): IntrinsicRegistryResult {
        if (!Files.isRegularFile(path)) return failure("intrinsic catalogue does not exist: $path", "INTR001")
        val diagnostics = mutableListOf<Diagnostic>()
        val definitions = mutableListOf<IntrinsicDefinition>()
        var fields = linkedMapOf<String, String>()
        fun finish() {
            if (fields.isEmpty()) return
            val name = fields["name"]
            val family = fields["family"]
            val arity = fields["arity"]?.toIntOrNull()
            val lowering = fields["lowering"]
            if (name == null || family == null || arity == null || lowering == null) {
                diagnostics += diagnostic("intrinsic entry is missing name, family, arity, or lowering", "INTR002")
            } else {
                definitions += IntrinsicDefinition(
                    name,
                    family,
                    arity,
                    lowering,
                    parseArray(fields["targets"]),
                    parseArray(fields["features"])
                )
            }
            fields = linkedMapOf()
        }
        runCatching { Files.readAllLines(path) }.getOrElse { error ->
            return failure("unable to read intrinsic catalogue '$path': ${error.message}", "INTR001")
        }.forEachIndexed { index, raw ->
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) return@forEachIndexed
            if (line == "[[intrinsic]]") {
                finish()
                return@forEachIndexed
            }
            val match = assignment.matchEntire(line)
            if (match == null) {
                diagnostics += diagnostic("invalid intrinsic catalogue entry on line ${index + 1}", "INTR003")
            } else {
                fields[match.groupValues[1]] = match.groupValues[2].trim().removeSurrounding("\"")
            }
        }
        finish()
        val duplicateNames = definitions.groupBy { it.name }.filterValues { it.size > 1 }.keys
        duplicateNames.forEach { diagnostics += diagnostic("duplicate intrinsic '$it'", "INTR004") }
        return IntrinsicRegistryResult(definitions.sortedBy { it.name }, diagnostics)
    }

    fun verify(definition: IntrinsicDefinition, target: TargetAbiDescriptor): Diagnostic? {
        if (definition.targets.isNotEmpty() && target.architecture !in definition.targets && target.targetTriple !in definition.targets) {
            return diagnostic("intrinsic '${definition.name}' is unavailable for target '${target.targetTriple}'", "INTR005")
        }
        val unsupported = definition.requiredFeatures - target.features
        if (unsupported.isNotEmpty()) {
            return diagnostic("intrinsic '${definition.name}' requires unsupported feature(s): ${unsupported.sorted().joinToString()}", "INTR006")
        }
        return null
    }

    private fun parseArray(value: String?): Set<String> = value
        ?.removePrefix("[")
        ?.removeSuffix("]")
        ?.split(',')
        ?.map { it.trim().removeSurrounding("\"") }
        ?.filter(String::isNotEmpty)
        ?.toSet()
        .orEmpty()

    private fun failure(message: String, code: String) = IntrinsicRegistryResult(emptyList(), listOf(diagnostic(message, code)))
    private fun diagnostic(message: String, code: String) = Diagnostic(DiagnosticSeverity.ERROR, message, null, code)
}
