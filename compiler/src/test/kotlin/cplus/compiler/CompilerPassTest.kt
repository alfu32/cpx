package cplus.compiler

import cplus.core.AstProgram
import cplus.core.Diagnostic
import cplus.core.DiagnosticSeverity
import cplus.core.Origin
import cplus.core.SourceFileId
import cplus.core.SourceRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame

class CompilerPassTest {
    @Test
    fun passesReceiveExplicitContextAndReturnDiagnostics() {
        val range = SourceRange(SourceFileId(30), 0, 0)
        val program = AstProgram(emptyList(), Origin.Direct(range))
        val context = CompilerContext(target = TargetInfo("c11"))
        val diagnostic = Diagnostic(DiagnosticSeverity.WARNING, "pass warning", range, "PASS001")
        var received: CompilerContext? = null
        val pass = object : CompilerPass<AstProgram> {
            override val name: String = "context-probe"

            override fun run(program: AstProgram, context: CompilerContext): PassResult<AstProgram> {
                received = context
                return PassResult(program, listOf(diagnostic))
            }
        }

        val result = pass.run(program, context)

        assertEquals("context-probe", pass.name)
        assertSame(context, received)
        assertSame(program, result.program)
        assertEquals(listOf(diagnostic), result.diagnostics)
        assertFalse(!result.isSuccessful)
        assertEquals("c11", context.target.cDialect)
    }
}
