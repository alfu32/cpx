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

    fun run(program: P, context: CompilerContext): PassResult<P>
}

data class PassResult<P>(
    val program: P,
    val diagnostics: List<Diagnostic> = emptyList()
) {
    val isSuccessful: Boolean
        get() = diagnostics.none { it.severity == cplus.core.DiagnosticSeverity.ERROR }
}
