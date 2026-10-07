package cplus.compiler

import cplus.core.AstProgram
import cplus.core.DiagnosticSeverity
import cplus.core.NodeId
import cplus.core.Origin
import cplus.core.SourceFileId
import cplus.core.SourceRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompilerPassInvariantTest {
    @Test
    fun runnerReportsPreconditionFailureWithPassInvariantAndNode() {
        val range = SourceRange(SourceFileId(33), 0, 1)
        val program = AstProgram(emptyList(), Origin.Direct(range))
        val offending = NodeId(17)
        val pass = object : CompilerPass<AstProgram> {
            override val name = "precondition-probe"
            override val preconditions = listOf(
                namedInvariant("has-resolved-root") {
                    listOf(PassInvariantViolation("root is unresolved", offending))
                }
            )

            override fun run(program: AstProgram, context: CompilerContext): PassResult<AstProgram> =
                PassResult(program)
        }

        val result = CompilerPassRunner(validateInvariants = true).run(pass, program, CompilerContext())

        assertEquals(1, result.diagnostics.size)
        assertEquals(DiagnosticSeverity.ERROR, result.diagnostics.single().severity)
        assertEquals("PASSINV001", result.diagnostics.single().code)
        assertTrue(result.diagnostics.single().message.contains("precondition-probe"))
        assertTrue(result.diagnostics.single().message.contains("has-resolved-root"))
        assertTrue(result.diagnostics.single().message.contains("node 17"))
    }

    @Test
    fun runnerAppendsPostconditionFailuresToPassDiagnostics() {
        val range = SourceRange(SourceFileId(34), 0, 1)
        val program = AstProgram(emptyList(), Origin.Direct(range))
        var ran = false
        val pass = object : CompilerPass<AstProgram> {
            override val name = "postcondition-probe"
            override val postconditions = listOf(
                namedInvariant("has-lowered-output") {
                    listOf(PassInvariantViolation("output is still high-level"))
                }
            )

            override fun run(program: AstProgram, context: CompilerContext): PassResult<AstProgram> {
                ran = true
                return PassResult(program)
            }
        }

        val result = CompilerPassRunner(validateInvariants = true).run(pass, program, CompilerContext())

        assertTrue(ran)
        assertEquals("PASSINV001", result.diagnostics.single().code)
        assertTrue(result.diagnostics.single().message.contains("postcondition-probe"))
    }

    private fun namedInvariant(
        name: String,
        check: (AstProgram) -> List<PassInvariantViolation>
    ) = object : PassInvariant<AstProgram> {
        override val name: String = name

        override fun check(program: AstProgram): List<PassInvariantViolation> = check(program)
    }
}
