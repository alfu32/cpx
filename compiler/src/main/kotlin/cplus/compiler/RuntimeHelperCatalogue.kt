package cplus.compiler

import cplus.core.Diagnostic
import cplus.core.DiagnosticSeverity
import java.nio.file.Files

/**
 * Compiler-owned symbols are part of the selected runtime contract. Keeping
 * the catalogue explicit prevents a generated helper from becoming an
 * accidental unresolved linker dependency.
 */
data class RuntimeHelperBinding(
    val symbol: String,
    val sourceFile: String
)

object RuntimeHelperCatalogue {
    private val bindings = listOf(
        RuntimeHelperBinding("__cplus_format", "format.c"),
        RuntimeHelperBinding("__cplus_test_report_truth", "test_reporting.c"),
        RuntimeHelperBinding("__cplus_test_report_equality", "test_reporting.c"),
        RuntimeHelperBinding("__cplus_test_begin", "test_reporting.c"),
        RuntimeHelperBinding("__cplus_test_finish", "test_reporting.c")
    ).associateBy(RuntimeHelperBinding::symbol)

    fun validate(symbols: Iterable<String>, plan: RuntimeLinkPlan): List<Diagnostic> = buildList {
        symbols.distinct().sorted().forEach { symbol ->
            val binding = bindings[symbol]
            if (binding == null) {
                add(Diagnostic(
                    DiagnosticSeverity.ERROR,
                    "compiler-generated runtime helper '$symbol' is not provided by the SDK",
                    null,
                    "RUNTIME001"
                ))
            } else if (plan.runtimeSources.none { it.fileName.toString() == binding.sourceFile && Files.isRegularFile(it) }) {
                add(Diagnostic(
                    DiagnosticSeverity.ERROR,
                    "runtime helper '$symbol' requires missing SDK source '${binding.sourceFile}'",
                    null,
                    "RUNTIME002"
                ))
            }
        }
    }
}
