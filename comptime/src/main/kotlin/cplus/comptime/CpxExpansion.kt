package cplus.comptime

import cplus.core.*
import java.nio.file.Path
import java.util.ArrayDeque

enum class CpxPhase {
    STRUCTURAL,
    REFLECTIVE
}

data class ExpansionKey(
    val functionName: String,
    val arguments: List<String>
) {
    val canonical: String
        get() = "$functionName(${arguments.joinToString(",")})"
}

data class ExpansionTask(
    val invocation: SyntaxCpxInvocation,
    val definition: SyntaxComptimeFunction,
    val key: ExpansionKey,
    val ancestors: List<ExpansionKey> = emptyList(),
    val phase: CpxPhase = CpxPhase.STRUCTURAL
)

/**
 * Work-queue scheduler for compile-time expansion.
 *
 * Tasks communicate through explicit queued expansion results. A generated
 * invocation can enqueue another task without recursively calling the
 * evaluator, which leaves room for dependency-driven fixed-point execution
 * and cycle diagnostics when channels point back to an earlier phase.
 */
class ComptimeScheduler {
    private val pending = ArrayDeque<ExpansionTask>()
    private val expanded = linkedSetOf<ExpansionKey>()

    fun enqueue(task: ExpansionTask) {
        pending.addLast(task)
    }

    fun next(): ExpansionTask? = if (pending.isEmpty()) null else pending.removeFirst()

    fun wasExpanded(key: ExpansionKey): Boolean = key in expanded

    fun markExpanded(key: ExpansionKey) {
        expanded += key
    }

    val hasPending: Boolean
        get() = pending.isNotEmpty()

    val expandedKeys: Set<ExpansionKey>
        get() = expanded.toSet()
}

data class CpxExpansionResult(
    val program: SyntaxProgram,
    val diagnostics: List<Diagnostic>,
    val expandedKeys: Set<ExpansionKey>
)

class CpxExpander(
    private val lexer: Lexer = Lexer()
) {
    fun expand(source: SourceFile, program: SyntaxProgram): CpxExpansionResult {
        val diagnostics = DiagnosticBag()
        val definitions = program.declarations
            .filterIsInstance<SyntaxComptimeFunction>()
            .associateBy { it.name }
        val scheduler = ComptimeScheduler()
        program.declarations.filterIsInstance<SyntaxCpxInvocation>().forEach { invocation ->
            val definition = definitions[invocation.name]
            if (definition == null) {
                diagnostics.error("unknown compile-time function '${invocation.name}'", invocation.origin.primaryRange, "CPX001")
            } else {
                scheduler.enqueue(taskFor(invocation, definition))
            }
        }

        val generated = mutableListOf<SyntaxDeclaration>()
        var generatedFileIndex = 0
        while (scheduler.hasPending) {
            val task = scheduler.next() ?: break
            if (task.key in task.ancestors) {
                diagnostics.error("compile-time expansion cycle detected at ${task.key.canonical}", task.invocation.origin.primaryRange, "CPX002")
                continue
            }
            if (scheduler.wasExpanded(task.key)) continue
            scheduler.markExpanded(task.key)
            if (task.definition.category != "decl" && task.definition.category != "unit") {
                diagnostics.error(
                    "initial structural expansion requires a declaration or unit CPX, got '${task.definition.category}'",
                    task.definition.origin.primaryRange,
                    "CPX003"
                )
                continue
            }
            if (task.definition.parameters.size != task.invocation.arguments.size) {
                diagnostics.error(
                    "CPX '${task.definition.name}' expects ${task.definition.parameters.size} arguments but received ${task.invocation.arguments.size}",
                    task.invocation.origin.primaryRange,
                    "CPX004"
                )
                continue
            }
            if (task.definition.parameters.any { it.kind != "type" }) {
                diagnostics.error("only type CPX parameters are implemented in the initial structural slice", task.definition.origin.primaryRange, "CPX005")
                continue
            }

            val instantiated = instantiate(task.definition.template, task.definition.parameters, task.invocation.arguments)
            val generatedFile = SourceFile(
                SourceFileId(-(++generatedFileIndex)),
                source.path.resolveSibling("<${task.key.canonical}>"),
                instantiated,
                source.version
            )
            val parsed = Parser(lexer.lex(generatedFile)).parse()
            diagnostics.addAll(parsed.diagnostics)
            val expansionOrigin = Origin.Expansion(
                task.definition.origin,
                task.invocation.origin,
                task.ancestors.lastOrNull()?.let { Origin.Synthetic(null) },
                task.key.canonical
            )
            val declarations = parsed.syntax.declarations.map { reorigin(it, expansionOrigin) }
            generated += declarations.filterNot { it is SyntaxComptimeFunction || it is SyntaxCpxInvocation }
            declarations.filterIsInstance<SyntaxCpxInvocation>().forEach { invocation ->
                val definition = definitions[invocation.name]
                if (definition == null) {
                    diagnostics.error("unknown compile-time function '${invocation.name}'", invocation.origin.primaryRange, "CPX001")
                } else {
                    scheduler.enqueue(taskFor(invocation, definition, task.ancestors + task.key))
                }
            }
        }

        val retained = program.declarations.filterNot { it is SyntaxComptimeFunction || it is SyntaxCpxInvocation }
        val range = program.range
        return CpxExpansionResult(
            SyntaxProgram(retained + generated, range, program.origin),
            diagnostics.diagnostics,
            scheduler.expandedKeys
        )
    }

    private fun taskFor(
        invocation: SyntaxCpxInvocation,
        definition: SyntaxComptimeFunction,
        ancestors: List<ExpansionKey> = emptyList()
    ): ExpansionTask = ExpansionTask(
        invocation,
        definition,
        ExpansionKey(definition.name, invocation.arguments.map(::canonicalArgument)),
        ancestors
    )

    private fun instantiate(
        template: String,
        parameters: List<SyntaxComptimeParameter>,
        arguments: List<String>
    ): String {
        var result = template
        parameters.zip(arguments).forEach { (parameter, argument) ->
            val raw = argument.trim()
            val identifier = raw.removePrefix("struct ").trim()
            result = result.replace(
                Regex("\\{\\s*${Regex.escape(parameter.name)}\\s*}"),
                identifier
            )
            result = result.replace(
                Regex("(?<![A-Za-z0-9_])${Regex.escape(parameter.name)}(?![A-Za-z0-9_])"),
                raw
            )
        }
        return result
    }

    private fun canonicalArgument(argument: String): String = argument
        .trim()
        .replace(Regex("\\s+"), " ")

    private fun reorigin(declaration: SyntaxDeclaration, origin: Origin): SyntaxDeclaration = when (declaration) {
        is SyntaxStruct -> declaration.copy(
            fields = declaration.fields.map { it.copy(type = reorigin(it.type, origin), origin = origin) },
            methods = declaration.methods.map { reorigin(it, origin) as SyntaxFunction },
            origin = origin
        )
        is SyntaxGlobalVariable -> declaration.copy(
            type = reorigin(declaration.type, origin),
            initializer = declaration.initializer?.let { reorigin(it, origin) },
            origin = origin
        )
        is SyntaxFunction -> declaration.copy(
            returnType = reorigin(declaration.returnType, origin),
            parameters = declaration.parameters.map { it.copy(type = reorigin(it.type, origin), origin = origin) },
            body = declaration.body?.let { reorigin(it, origin) },
            origin = origin
        )
        is SyntaxComptimeFunction -> declaration.copy(origin = origin)
        is SyntaxCpxInvocation -> declaration.copy(origin = origin)
    }

    private fun reorigin(type: TypeSyntax, origin: Origin): TypeSyntax = type.copy(origin = origin)

    private fun reorigin(statement: SyntaxStatement, origin: Origin): SyntaxStatement = when (statement) {
        is SyntaxBlock -> statement.copy(statements = statement.statements.map { reorigin(it, origin) }, origin = origin)
        is SyntaxReturn -> statement.copy(expression = statement.expression?.let { reorigin(it, origin) }, origin = origin)
        is SyntaxExpressionStatement -> statement.copy(expression = reorigin(statement.expression, origin), origin = origin)
        is SyntaxVariableDeclaration -> statement.copy(
            type = reorigin(statement.type, origin),
            initializer = statement.initializer?.let { reorigin(it, origin) },
            origin = origin
        )
    }

    private fun reorigin(expression: SyntaxExpression, origin: Origin): SyntaxExpression = when (expression) {
        is SyntaxIntegerLiteral -> expression.copy(origin = origin)
        is SyntaxStringLiteral -> expression.copy(origin = origin)
        is SyntaxCharacterLiteral -> expression.copy(origin = origin)
        is SyntaxIdentifier -> expression.copy(origin = origin)
        is SyntaxUnary -> expression.copy(operand = reorigin(expression.operand, origin), origin = origin)
        is SyntaxBinary -> expression.copy(left = reorigin(expression.left, origin), right = reorigin(expression.right, origin), origin = origin)
        is SyntaxCall -> expression.copy(callee = reorigin(expression.callee, origin), arguments = expression.arguments.map { reorigin(it, origin) }, origin = origin)
        is SyntaxMemberAccess -> expression.copy(receiver = reorigin(expression.receiver, origin), origin = origin)
        is SyntaxParenthesized -> expression.copy(expression = reorigin(expression.expression, origin), origin = origin)
        is SyntaxErrorExpression -> expression.copy(origin = origin)
    }
}
