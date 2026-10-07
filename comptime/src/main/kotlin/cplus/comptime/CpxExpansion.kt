package cplus.comptime

import cplus.core.*
import java.nio.file.Path
import java.util.ArrayDeque

enum class CpxPhase {
    STRUCTURAL,
    REFLECTIVE
}

enum class CpxCategory {
    UNIT,
    DECLARATION,
    MEMBER,
    STATEMENT,
    EXPRESSION,
    TYPE
}

sealed interface ComptimeValue {
    data class CtType(
        val sourceText: String,
        val identifierText: String = sourceText.removePrefix("struct ").trim()
    ) : ComptimeValue

    data class CtIdentifier(val text: String) : ComptimeValue
    data class CtString(val text: String) : ComptimeValue
}

sealed interface TemplateNode {
    data class Literal(val text: String) : TemplateNode
    data class Binding(val name: String, val explicit: Boolean) : TemplateNode
}

data class CpxTemplate(
    val category: CpxCategory,
    val nodes: List<TemplateNode>,
    val origin: Origin
) {
    fun render(bindings: Map<String, ComptimeValue>): String = buildString {
        nodes.forEach { node ->
            when (node) {
                is TemplateNode.Literal -> append(node.text)
                is TemplateNode.Binding -> {
                    val value = bindings[node.name]
                    when (value) {
                        is ComptimeValue.CtType -> append(if (node.explicit) value.identifierText else value.sourceText)
                        is ComptimeValue.CtIdentifier -> append(value.text)
                        is ComptimeValue.CtString -> append(value.text)
                        null -> append(node.name)
                    }
                }
            }
        }
    }
}

class CpxTemplateParser {
    fun parse(
        text: String,
        category: CpxCategory,
        origin: Origin,
        bindingNames: Set<String>
    ): CpxTemplate {
        val nodes = mutableListOf<TemplateNode>()
        if (bindingNames.isEmpty()) return CpxTemplate(category, listOf(TemplateNode.Literal(text)), origin)
        val explicitPattern = Regex("\\{\\s*(${bindingNames.joinToString("|") { Regex.escape(it) }})\\s*}")
        var cursor = 0
        explicitPattern.findAll(text).forEach { match ->
            if (match.range.first > cursor) nodes += directBindings(text.substring(cursor, match.range.first), bindingNames)
            nodes += TemplateNode.Binding(match.groupValues[1], explicit = true)
            cursor = match.range.last + 1
        }
        if (cursor < text.length) nodes += directBindings(text.substring(cursor), bindingNames)
        return CpxTemplate(category, nodes, origin)
    }

    private fun directBindings(text: String, bindingNames: Set<String>): List<TemplateNode> {
        if (text.isEmpty() || bindingNames.isEmpty()) return if (text.isEmpty()) emptyList() else listOf(TemplateNode.Literal(text))
        val pattern = Regex("(?<![A-Za-z0-9_])(${bindingNames.joinToString("|") { Regex.escape(it) }})(?![A-Za-z0-9_])")
        val result = mutableListOf<TemplateNode>()
        var cursor = 0
        pattern.findAll(text).forEach { match ->
            if (match.range.first > cursor) result += TemplateNode.Literal(text.substring(cursor, match.range.first))
            result += TemplateNode.Binding(match.value, explicit = false)
            cursor = match.range.last + 1
        }
        if (cursor < text.length) result += TemplateNode.Literal(text.substring(cursor))
        return result
    }
}

data class ExpansionKey(
    val functionName: String,
    val arguments: List<String>
) {
    val canonical: String
        get() = "$functionName(${arguments.joinToString(",")})"
}

sealed interface ComptimeDependency {
    data class Expansion(val key: ExpansionKey) : ComptimeDependency
    data object StableTypeUniverse : ComptimeDependency
}

enum class ComptimeTaskState {
    PENDING,
    READY,
    RUNNING,
    EXPANDED,
    BLOCKED,
    FAILED
}

data class ExpansionTask(
    val invocation: SyntaxCpxInvocation,
    val definition: SyntaxComptimeFunction,
    val key: ExpansionKey,
    val ancestors: List<ExpansionKey> = emptyList(),
    val phase: CpxPhase = CpxPhase.STRUCTURAL,
    val dependencies: Set<ComptimeDependency> = emptySet()
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
    private val states = linkedMapOf<ExpansionKey, ComptimeTaskState>()

    fun enqueue(task: ExpansionTask) {
        pending.addLast(task)
        states.putIfAbsent(task.key, ComptimeTaskState.PENDING)
    }

    fun next(): ExpansionTask? {
        if (pending.isEmpty()) return null
        val count = pending.size
        repeat(count) {
            val task = pending.removeFirst()
            if (dependenciesReady(task)) {
                states[task.key] = ComptimeTaskState.READY
                states[task.key] = ComptimeTaskState.RUNNING
                return task
            }
            pending.addLast(task)
        }
        return null
    }

    fun wasExpanded(key: ExpansionKey): Boolean = key in expanded

    fun markExpanded(key: ExpansionKey) {
        expanded += key
        states[key] = ComptimeTaskState.EXPANDED
    }

    fun markFailed(key: ExpansionKey) {
        states[key] = ComptimeTaskState.FAILED
    }

    fun markBlocked(key: ExpansionKey) {
        states[key] = ComptimeTaskState.BLOCKED
    }

    fun state(key: ExpansionKey): ComptimeTaskState? = states[key]

    fun pendingKeys(): Set<ExpansionKey> = pending.map { it.key }.toSet()

    private fun dependenciesReady(task: ExpansionTask): Boolean = task.dependencies.all { dependency ->
        when (dependency) {
            is ComptimeDependency.Expansion -> dependency.key in expanded
            ComptimeDependency.StableTypeUniverse -> false
        }
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
    private val lexer: Lexer = Lexer(),
    private val templateParser: CpxTemplateParser = CpxTemplateParser()
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
            val task = scheduler.next()
            if (task == null) {
                scheduler.pendingKeys().forEach(scheduler::markBlocked)
                diagnostics.error(
                    "compile-time dependency cycle or unsatisfied phase barrier: ${scheduler.pendingKeys().joinToString { it.canonical }}",
                    program.origin.primaryRange,
                    "CPX006"
                )
                break
            }
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

            val category = parseCategory(task.definition.category)
            val template = templateParser.parse(
                task.definition.template,
                category,
                task.definition.origin,
                task.definition.parameters.map { it.name }.toSet()
            )
            val bindings = task.definition.parameters.zip(task.invocation.arguments).associate { (parameter, argument) ->
                parameter.name to ComptimeValue.CtType(argument.trim())
            }
            val instantiated = template.render(bindings)
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
    ).let { task ->
        task.copy(
            dependencies = ancestors.lastOrNull()?.let { setOf(ComptimeDependency.Expansion(it)) }.orEmpty()
        )
    }

    private fun parseCategory(value: String): CpxCategory = when (value.lowercase()) {
        "unit" -> CpxCategory.UNIT
        "member" -> CpxCategory.MEMBER
        "stmt", "statement" -> CpxCategory.STATEMENT
        "expr", "expression" -> CpxCategory.EXPRESSION
        "type" -> CpxCategory.TYPE
        else -> CpxCategory.DECLARATION
    }

    private fun canonicalArgument(argument: String): String = argument
        .trim()
        .replace(Regex("\\s+"), " ")

    private fun reorigin(declaration: SyntaxDeclaration, origin: Origin): SyntaxDeclaration = when (declaration) {
        is SyntaxPackage -> declaration.copy(origin = origin)
        is SyntaxAlias -> declaration.copy(target = reorigin(declaration.target, origin), origin = origin)
        is SyntaxUnion -> declaration.copy(
            fields = declaration.fields.map { it.copy(type = reorigin(it.type, origin), origin = origin) },
            origin = origin
        )
        is SyntaxEnum -> declaration.copy(
            values = declaration.values.map { it.copy(origin = origin) },
            origin = origin
        )
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
        is SyntaxImport -> declaration.copy(origin = origin)
    }

    private fun reorigin(type: TypeSyntax, origin: Origin): TypeSyntax = type.copy(origin = origin)

    private fun reorigin(statement: SyntaxStatement, origin: Origin): SyntaxStatement = when (statement) {
        is SyntaxBlock -> statement.copy(statements = statement.statements.map { reorigin(it, origin) }, origin = origin)
        is SyntaxReturn -> statement.copy(expression = statement.expression?.let { reorigin(it, origin) }, origin = origin)
        is SyntaxExpressionStatement -> statement.copy(expression = reorigin(statement.expression, origin), origin = origin)
        is SyntaxDefer -> statement.copy(expression = reorigin(statement.expression, origin), origin = origin)
        is SyntaxIf -> statement.copy(
            condition = reorigin(statement.condition, origin),
            thenBranch = reorigin(statement.thenBranch, origin),
            elseBranch = statement.elseBranch?.let { reorigin(it, origin) },
            origin = origin
        )
        is SyntaxWhile -> statement.copy(
            condition = reorigin(statement.condition, origin),
            body = reorigin(statement.body, origin),
            origin = origin
        )
        is SyntaxFor -> statement.copy(
            initializer = statement.initializer?.let { reorigin(it, origin) },
            condition = statement.condition?.let { reorigin(it, origin) },
            increment = statement.increment?.let { reorigin(it, origin) },
            body = reorigin(statement.body, origin),
            origin = origin
        )
        is SyntaxBreak -> statement.copy(origin = origin)
        is SyntaxContinue -> statement.copy(origin = origin)
        is SyntaxVariableDeclaration -> statement.copy(
            type = reorigin(statement.type, origin),
            initializer = statement.initializer?.let { reorigin(it, origin) },
            origin = origin
        )
    }

    private fun reorigin(expression: SyntaxExpression, origin: Origin): SyntaxExpression = when (expression) {
        is SyntaxIntegerLiteral -> expression.copy(origin = origin)
        is SyntaxFloatLiteral -> expression.copy(origin = origin)
        is SyntaxStringLiteral -> expression.copy(origin = origin)
        is SyntaxStringTemplate -> expression.copy(
            parts = expression.parts.map { part ->
                when (part) {
                    is SyntaxStringTextPart -> part
                    is SyntaxStringExpressionPart -> part.copy(expression = reorigin(part.expression, origin))
                }
            },
            origin = origin
        )
        is SyntaxCharacterLiteral -> expression.copy(origin = origin)
        is SyntaxIdentifier -> expression.copy(origin = origin)
        is SyntaxUnary -> expression.copy(operand = reorigin(expression.operand, origin), origin = origin)
        is SyntaxBinary -> expression.copy(left = reorigin(expression.left, origin), right = reorigin(expression.right, origin), origin = origin)
        is SyntaxConditional -> expression.copy(
            condition = reorigin(expression.condition, origin),
            thenBranch = reorigin(expression.thenBranch, origin),
            elseBranch = reorigin(expression.elseBranch, origin),
            origin = origin
        )
        is SyntaxUpdate -> expression.copy(operand = reorigin(expression.operand, origin), origin = origin)
        is SyntaxCall -> expression.copy(callee = reorigin(expression.callee, origin), arguments = expression.arguments.map { reorigin(it, origin) }, origin = origin)
        is SyntaxMemberAccess -> expression.copy(receiver = reorigin(expression.receiver, origin), origin = origin)
        is SyntaxIndexAccess -> expression.copy(receiver = reorigin(expression.receiver, origin), index = reorigin(expression.index, origin), origin = origin)
        is SyntaxParenthesized -> expression.copy(expression = reorigin(expression.expression, origin), origin = origin)
        is SyntaxErrorExpression -> expression.copy(origin = origin)
    }
}
