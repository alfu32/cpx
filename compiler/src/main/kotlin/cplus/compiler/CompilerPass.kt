package cplus.compiler

import cplus.core.Diagnostic

/**
 * A transformation boundary in the compiler pipeline.
 *
 * The program representation is generic because front-end, semantic, and
 * backend passes operate on different models. Every pass receives all shared
 * services explicitly through [CompilerContext] and returns its transformed
 * representation together with diagnostics produced during the pass.
 */
interface CompilerPass<P> {
    val name: String

    val preconditions: List<PassInvariant<P>>
        get() = emptyList()

    val postconditions: List<PassInvariant<P>>
        get() = emptyList()

    fun run(program: P, context: CompilerContext): PassResult<P>
}

interface PassInvariant<P> {
    val name: String

    fun check(program: P): List<PassInvariantViolation>
}

data class PassInvariantViolation(
    val message: String,
    val node: cplus.core.NodeId? = null
)

data class PassResult<P>(
    val program: P,
    val diagnostics: List<Diagnostic> = emptyList()
) {
    val isSuccessful: Boolean
        get() = diagnostics.none { it.severity == cplus.core.DiagnosticSeverity.ERROR }
}

/** Executes a pass and optionally validates its declared invariants. */
class CompilerPassRunner(
    private val validateInvariants: Boolean = false
) {
    fun <P> run(pass: CompilerPass<P>, program: P, context: CompilerContext): PassResult<P> {
        if (!validateInvariants) return pass.run(program, context)

        val preconditionDiagnostics = diagnosticsFor(pass, "precondition", pass.preconditions, program)
        if (preconditionDiagnostics.isNotEmpty()) {
            return PassResult(program, preconditionDiagnostics)
        }

        val result = pass.run(program, context)
        val postconditionDiagnostics = diagnosticsFor(pass, "postcondition", pass.postconditions, result.program)
        return if (postconditionDiagnostics.isEmpty()) {
            result
        } else {
            result.copy(diagnostics = result.diagnostics + postconditionDiagnostics)
        }
    }

    private fun <P> diagnosticsFor(
        pass: CompilerPass<P>,
        kind: String,
        invariants: List<PassInvariant<P>>,
        program: P
    ): List<Diagnostic> = invariants.flatMap { invariant ->
        invariant.check(program).map { violation ->
            val node = violation.node?.let { " at node ${it.value}" }.orEmpty()
            Diagnostic(
                cplus.core.DiagnosticSeverity.ERROR,
                "pass '${pass.name}' $kind invariant '${invariant.name}' failed$node: ${violation.message}",
                code = "PASSINV001"
            )
        }
    }
}
