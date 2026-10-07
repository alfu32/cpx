package cplus.compiler

import cplus.core.*
import cplus.semantic.CType
import cplus.semantic.Symbol
import cplus.semantic.SymbolId
import cplus.semantic.SymbolKind
import java.util.LinkedHashSet

enum class CaptureMode {
    REFERENCE,
    VALUE,
    MOVE
}

data class Capture(
    val symbol: SymbolId,
    val name: String,
    val type: CType,
    val mode: CaptureMode,
    val origin: Origin
)

data class ClosureEnvironmentField(
    val name: String,
    val type: CType,
    val mode: CaptureMode,
    val origin: Origin
)

data class ClosureEnvironment(
    val name: String,
    val fields: List<ClosureEnvironmentField>,
    val origin: Origin
)

data class HoistedClosureFunction(
    val name: String,
    val environmentName: String?,
    val environmentParameterName: String?,
    val original: AstFunction,
    val captures: List<Capture>,
    val origin: Origin
)

data class ClosurePlan(
    val environment: ClosureEnvironment?,
    val hoistedFunction: HoistedClosureFunction,
    val diagnostics: List<Diagnostic>
) {
    val isSuccessful: Boolean
        get() = diagnostics.none { it.severity == DiagnosticSeverity.ERROR }
}

/** Plans capture lowering without mutating the source AST. */
class ClosurePlanner {
    fun plan(
        enclosingName: String,
        inner: AstFunction,
        enclosingBindings: Map<String, Symbol>,
        mutableBindings: Set<String> = emptySet(),
        escapesScope: Boolean = false
    ): ClosurePlan {
        val localNames = linkedSetOf<String>().apply {
            addAll(inner.parameters.map { it.name })
            inner.body?.let { addAll(collectLocalNames(it)) }
        }
        val referencedNames = LinkedHashSet<String>().apply {
            inner.body?.let { addAll(collectReferencedNames(it)) }
        }
        val captures = referencedNames
            .asSequence()
            .filter { it !in localNames }
            .mapNotNull { name -> enclosingBindings[name]?.let { symbol ->
                Capture(
                    symbol.id,
                    name,
                    symbol.type,
                    if (name in mutableBindings) CaptureMode.REFERENCE else CaptureMode.VALUE,
                    inner.origin
                )
            } }
            .toList()
        val environmentName = if (captures.isEmpty()) null else "${enclosingName}__${inner.name}__env_t"
        val environment = environmentName?.let { name ->
            ClosureEnvironment(
                name,
                captures.map { capture ->
                    ClosureEnvironmentField(capture.name, capture.type, capture.mode, capture.origin)
                },
                inner.origin
            )
        }
        val hoisted = HoistedClosureFunction(
            "${enclosingName}__${inner.name}",
            environmentName,
            environmentName?.let { "env" },
            inner,
            captures,
            inner.origin
        )
        val diagnostics = if (escapesScope && captures.any { it.mode == CaptureMode.REFERENCE }) {
            listOf(
                Diagnostic(
                    DiagnosticSeverity.ERROR,
                    "closure '${inner.name}' captures mutable enclosing state by reference but escapes its scope",
                    inner.origin.primaryRange,
                    "CLOSURE001"
                )
            )
        } else {
            emptyList()
        }
        return ClosurePlan(environment, hoisted, diagnostics)
    }

    private fun collectLocalNames(statement: AstStatement): Set<String> {
        val names = linkedSetOf<String>()
        fun visit(node: AstStatement) {
            when (node) {
                is AstBlock -> node.statements.forEach(::visit)
                is AstIf -> {
                    visit(node.thenBranch)
                    node.elseBranch?.let(::visit)
                }
                is AstWhile -> visit(node.body)
                is AstFor -> {
                    node.initializer?.let(::visit)
                    visit(node.body)
                }
                is AstVariableDeclaration -> names += node.name
                is AstReturn,
                is AstExpressionStatement,
                is AstDefer,
                is AstBreak,
                is AstContinue,
                is AstInnerFunction -> Unit
            }
        }
        visit(statement)
        return names
    }

    private fun collectReferencedNames(statement: AstStatement): Set<String> {
        val names = linkedSetOf<String>()
        fun expression(node: AstExpression) {
            when (node) {
                is AstIdentifier -> names += node.name
                is AstStringTemplate -> node.parts.filterIsInstance<AstStringExpressionPart>().forEach { expression(it.expression) }
                is AstUnary -> expression(node.operand)
                is AstBinary -> { expression(node.left); expression(node.right) }
                is AstConditional -> { expression(node.condition); expression(node.thenBranch); expression(node.elseBranch) }
                is AstUpdate -> expression(node.operand)
                is AstSizeOf -> node.operand?.let(::expression)
                is AstCast -> expression(node.operand)
                is AstCall -> { expression(node.callee); node.arguments.forEach(::expression) }
                is AstMemberAccess -> expression(node.receiver)
                is AstIndexAccess -> { expression(node.receiver); expression(node.index) }
                is AstParenthesized -> expression(node.expression)
                is AstIntegerLiteral,
                is AstBooleanLiteral,
                is AstFloatLiteral,
                is AstStringLiteral,
                is AstCharacterLiteral,
                is AstErrorExpression -> Unit
            }
        }
        fun visit(node: AstStatement) {
            when (node) {
                is AstBlock -> node.statements.forEach(::visit)
                is AstReturn -> node.expression?.let(::expression)
                is AstExpressionStatement -> expression(node.expression)
                is AstDefer -> expression(node.expression)
                is AstIf -> { expression(node.condition); visit(node.thenBranch); node.elseBranch?.let(::visit) }
                is AstWhile -> { expression(node.condition); visit(node.body) }
                is AstFor -> {
                    node.initializer?.let(::visit)
                    node.condition?.let(::expression)
                    node.increment?.let(::expression)
                    visit(node.body)
                }
                is AstVariableDeclaration -> node.initializer?.let(::expression)
                is AstBreak, is AstContinue -> Unit
                is AstInnerFunction -> node.function.body?.let(::visit)
            }
        }
        visit(statement)
        return names
    }
}

data class AstClosureLoweringResult(
    val program: AstProgram,
    val diagnostics: List<Diagnostic>
) {
    val isSuccessful: Boolean
        get() = diagnostics.none { it.severity == DiagnosticSeverity.ERROR }
}

/**
 * Hoists parsed inner functions into ordinary top-level AST declarations.
 * The resulting AST is consumable by the existing semantic and C lowering
 * passes; no nested-function construct reaches the backend.
 */
class AstClosureLowerer {
    private data class Binding(val type: AstTypeRef, val mutable: Boolean)

    private data class Callable(
        val hoistedName: String,
        val environmentVariable: String?,
        val captures: List<Capture>
    )

    private val generated = mutableListOf<AstDeclaration>()
    private val diagnostics = DiagnosticBag()

    fun lower(program: AstProgram): AstClosureLoweringResult {
        generated.clear()
        diagnostics.clear()
        val declarations = program.declarations.map { declaration ->
            when (declaration) {
                is AstFunction -> lowerFunction(declaration)
                is AstStruct -> declaration.copy(
                    methods = declaration.methods.map { lowerFunction(it, declaration.name) }
                )
                else -> declaration
            }
        }
        return AstClosureLoweringResult(
            AstProgram(generated + declarations, program.origin, program.modules),
            diagnostics.diagnostics
        )
    }

    private fun lowerFunction(function: AstFunction, ownerName: String? = null): AstFunction {
        val qualifiedName = listOfNotNull(ownerName, function.name).joinToString("__")
        val bindings = linkedMapOf<String, Binding>()
        function.parameters.forEach { bindings[it.name] = Binding(it.type, mutable = true) }
        return function.copy(
            body = function.body?.let {
                lowerStatement(it, bindings, emptyMap(), emptyMap(), qualifiedName, allowInner = true)
            }
        )
    }

    private fun lowerStatement(
        statement: AstStatement,
        bindings: MutableMap<String, Binding>,
        callables: Map<String, Callable>,
        rewrites: Map<String, AstExpression>,
        enclosingName: String,
        allowInner: Boolean
    ): AstStatement = when (statement) {
        is AstBlock -> lowerBlock(statement, bindings, callables, rewrites, enclosingName, allowInner)
        is AstReturn -> statement.copy(expression = statement.expression?.let { lowerExpression(it, callables, rewrites) })
        is AstExpressionStatement -> statement.copy(expression = lowerExpression(statement.expression, callables, rewrites))
        is AstDefer -> statement.copy(expression = lowerExpression(statement.expression, callables, rewrites))
        is AstIf -> statement.copy(
            condition = lowerExpression(statement.condition, callables, rewrites),
            thenBranch = lowerStatement(statement.thenBranch, bindings.toMutableMap(), callables, rewrites, enclosingName, allowInner),
            elseBranch = statement.elseBranch?.let { lowerStatement(it, bindings.toMutableMap(), callables, rewrites, enclosingName, allowInner) }
        )
        is AstWhile -> statement.copy(
            condition = lowerExpression(statement.condition, callables, rewrites),
            body = lowerStatement(statement.body, bindings.toMutableMap(), callables, rewrites, enclosingName, allowInner)
        )
        is AstFor -> statement.copy(
            initializer = statement.initializer?.let { lowerStatement(it, bindings, callables, rewrites, enclosingName, allowInner) },
            condition = statement.condition?.let { lowerExpression(it, callables, rewrites) },
            increment = statement.increment?.let { lowerExpression(it, callables, rewrites) },
            body = lowerStatement(statement.body, bindings.toMutableMap(), callables, rewrites, enclosingName, allowInner)
        )
        is AstVariableDeclaration -> statement.copy(
            initializer = statement.initializer?.let { lowerExpression(it, callables, rewrites) }
        ).also { bindings[statement.name] = Binding(statement.type, mutable = true) }
        is AstInnerFunction -> {
            if (allowInner) {
                diagnostics.error("inner function must be declared inside a block", statement.origin.primaryRange, "CLOSURE002")
            } else {
                diagnostics.error("inner function declarations must be block statements for closure lowering", statement.origin.primaryRange, "CLOSURE003")
            }
            AstBlock(emptyList(), statement.origin)
        }
        is AstBreak, is AstContinue -> statement
    }

    private fun lowerBlock(
        block: AstBlock,
        incomingBindings: MutableMap<String, Binding>,
        incomingCallables: Map<String, Callable>,
        incomingRewrites: Map<String, AstExpression>,
        enclosingName: String,
        allowInner: Boolean
    ): AstBlock {
        val bindings = incomingBindings.toMutableMap()
        val callables = incomingCallables.toMutableMap()
        val lowered = mutableListOf<AstStatement>()
        block.statements.forEach { statement ->
            if (statement is AstInnerFunction) {
                val callable = planInner(enclosingName, statement.function, bindings, callables)
                callables[statement.function.name] = callable
                callable.environmentVariable?.let { environmentVariable ->
                    val environmentOrigin = Origin.Generated(statement.origin)
                    val environmentType = AstTypeRef(
                        callable.hoistedName + "__env_t",
                        isStruct = true,
                        pointerDepth = 0,
                        environmentOrigin,
                        "struct"
                    )
                    lowered += AstVariableDeclaration(environmentType, environmentVariable, null, environmentOrigin)
                    callable.captures.forEach { capture ->
                        val left = AstMemberAccess(AstIdentifier(environmentVariable, environmentOrigin), capture.name, environmentOrigin)
                        val source = lowerExpression(
                            AstIdentifier(capture.name, capture.origin),
                            callables,
                            incomingRewrites
                        )
                        val value = if (capture.mode == CaptureMode.REFERENCE) {
                            AstUnary("&", source, capture.origin)
                        } else {
                            source
                        }
                        lowered += AstExpressionStatement(AstBinary(left, "=", value, capture.origin), capture.origin)
                    }
                }
            } else {
                lowered += lowerStatement(statement, bindings, callables, incomingRewrites, enclosingName, allowInner)
            }
        }
        return block.copy(statements = lowered)
    }

    private fun planInner(
        enclosingName: String,
        inner: AstFunction,
        bindings: Map<String, Binding>,
        callables: Map<String, Callable> = emptyMap()
    ): Callable {
        val localNames = linkedSetOf<String>().apply {
            addAll(inner.parameters.map { it.name })
            inner.body?.let { addAll(localNames(it)) }
        }
        val captures = inner.body?.let { referencedNames(it) }
            .orEmpty()
            .filter { it !in localNames }
            .mapNotNull { name -> bindings[name]?.let { binding ->
                Capture(
                    SymbolId(name.hashCode()),
                    name,
                    typeFrom(binding.type),
                    if (binding.mutable) CaptureMode.REFERENCE else CaptureMode.VALUE,
                    inner.origin
                )
            } }
        val hoistedName = "${enclosingName}__${inner.name}"
        val environmentName = if (captures.isEmpty()) null else "${hoistedName}__env_t"
        if (environmentName != null) {
            val origin = Origin.Generated(inner.origin)
            generated += AstStruct(
                environmentName,
                captures.map { capture ->
                    val binding = bindings.getValue(capture.name)
                    val type = if (capture.mode == CaptureMode.REFERENCE) binding.type.copy(pointerDepth = binding.type.pointerDepth + 1, origin = origin) else binding.type.copy(origin = origin)
                    AstField(type, capture.name, origin)
                },
                origin = origin
            )
        }
        val rewrites = captures.associate { capture ->
            val envOrigin = Origin.Generated(inner.origin)
            val receiver = AstParenthesized(
                AstUnary("*", AstIdentifier("env", envOrigin), envOrigin),
                envOrigin
            )
            val member = AstMemberAccess(receiver, capture.name, envOrigin)
            capture.name to if (capture.mode == CaptureMode.REFERENCE) AstUnary("*", member, envOrigin) else member
        }
        val innerBindings = linkedMapOf<String, Binding>()
        inner.parameters.forEach { innerBindings[it.name] = Binding(it.type, mutable = true) }
        captures.forEach { capture ->
            bindings[capture.name]?.let { binding -> innerBindings[capture.name] = binding }
        }
        val body = inner.body?.let {
            lowerStatement(
                it,
                innerBindings,
                callables,
                rewrites,
                hoistedName,
                allowInner = true
            )
        }
        val origin = Origin.Generated(inner.origin)
        val parameters = buildList {
            if (environmentName != null) add(
                AstParameter(
                    AstTypeRef(environmentName, true, 1, origin, "struct"),
                    "env",
                    origin = origin
                )
            )
            addAll(inner.parameters)
        }
        generated += AstFunction(
            inner.returnType,
            hoistedName,
            parameters,
            body,
            origin = origin
        )
        return Callable(hoistedName, environmentName?.let { "${hoistedName}__env" }, captures)
    }

    private fun typeFrom(type: AstTypeRef): CType = when (type.name) {
        "void", "bool", "char", "short", "int", "long", "float", "double" -> cplus.semantic.PrimitiveType(cplus.semantic.TypeId(type.hashCode()), type.name)
        else -> cplus.semantic.ForeignType(cplus.semantic.TypeId(type.hashCode()), type.name)
    }

    private fun lowerExpression(
        expression: AstExpression,
        callables: Map<String, Callable>,
        rewrites: Map<String, AstExpression>
    ): AstExpression = when (expression) {
        is AstIdentifier -> rewrites[expression.name] ?: expression
        is AstStringTemplate -> expression.copy(parts = expression.parts.map { part ->
            when (part) {
                is AstStringTextPart -> part
                is AstStringExpressionPart -> part.copy(expression = lowerExpression(part.expression, callables, rewrites))
            }
        })
        is AstUnary -> expression.copy(operand = lowerExpression(expression.operand, callables, rewrites))
        is AstBinary -> expression.copy(
            left = lowerExpression(expression.left, callables, rewrites),
            right = lowerExpression(expression.right, callables, rewrites)
        )
        is AstConditional -> expression.copy(
            condition = lowerExpression(expression.condition, callables, rewrites),
            thenBranch = lowerExpression(expression.thenBranch, callables, rewrites),
            elseBranch = lowerExpression(expression.elseBranch, callables, rewrites)
        )
        is AstUpdate -> expression.copy(operand = lowerExpression(expression.operand, callables, rewrites))
        is AstSizeOf -> expression.copy(operand = expression.operand?.let { lowerExpression(it, callables, rewrites) })
        is AstCast -> expression.copy(operand = lowerExpression(expression.operand, callables, rewrites))
        is AstCall -> {
            val callee = lowerExpression(expression.callee, callables, rewrites)
            val callable = (expression.callee as? AstIdentifier)?.let { callables[it.name] }
            val arguments = expression.arguments.map { lowerExpression(it, callables, rewrites) }.toMutableList()
            if (callable?.environmentVariable != null) {
                arguments.add(0, AstUnary("&", AstIdentifier(callable.environmentVariable, expression.origin), expression.origin))
            }
            expression.copy(
                callee = if (callable == null) callee else AstIdentifier(callable.hoistedName, expression.origin),
                arguments = arguments
            )
        }
        is AstMemberAccess -> expression.copy(receiver = lowerExpression(expression.receiver, callables, rewrites))
        is AstIndexAccess -> expression.copy(
            receiver = lowerExpression(expression.receiver, callables, rewrites),
            index = lowerExpression(expression.index, callables, rewrites)
        )
        is AstParenthesized -> expression.copy(expression = lowerExpression(expression.expression, callables, rewrites))
        is AstIntegerLiteral,
        is AstBooleanLiteral,
        is AstFloatLiteral,
        is AstStringLiteral,
        is AstCharacterLiteral,
        is AstErrorExpression -> expression
    }

    private fun localNames(statement: AstStatement): Set<String> = linkedSetOf<String>().also { result ->
        fun visit(node: AstStatement) {
            when (node) {
                is AstBlock -> node.statements.forEach(::visit)
                is AstIf -> { visit(node.thenBranch); node.elseBranch?.let(::visit) }
                is AstWhile -> visit(node.body)
                is AstFor -> { node.initializer?.let(::visit); visit(node.body) }
                is AstVariableDeclaration -> result += node.name
                is AstReturn, is AstExpressionStatement, is AstDefer, is AstBreak, is AstContinue, is AstInnerFunction -> Unit
            }
        }
        visit(statement)
    }

    private fun referencedNames(statement: AstStatement): Set<String> = linkedSetOf<String>().also { result ->
        fun expression(node: AstExpression) {
            when (node) {
                is AstIdentifier -> result += node.name
                is AstStringTemplate -> node.parts.filterIsInstance<AstStringExpressionPart>().forEach { expression(it.expression) }
                is AstUnary -> expression(node.operand)
                is AstBinary -> { expression(node.left); expression(node.right) }
                is AstConditional -> { expression(node.condition); expression(node.thenBranch); expression(node.elseBranch) }
                is AstUpdate -> expression(node.operand)
                is AstSizeOf -> node.operand?.let(::expression)
                is AstCast -> expression(node.operand)
                is AstCall -> { expression(node.callee); node.arguments.forEach(::expression) }
                is AstMemberAccess -> expression(node.receiver)
                is AstIndexAccess -> { expression(node.receiver); expression(node.index) }
                is AstParenthesized -> expression(node.expression)
                is AstIntegerLiteral, is AstBooleanLiteral, is AstFloatLiteral, is AstStringLiteral, is AstCharacterLiteral, is AstErrorExpression -> Unit
            }
        }
        fun visit(node: AstStatement) {
            when (node) {
                is AstBlock -> node.statements.forEach(::visit)
                is AstReturn -> node.expression?.let(::expression)
                is AstExpressionStatement -> expression(node.expression)
                is AstDefer -> expression(node.expression)
                is AstIf -> { expression(node.condition); visit(node.thenBranch); node.elseBranch?.let(::visit) }
                is AstWhile -> { expression(node.condition); visit(node.body) }
                is AstFor -> { node.initializer?.let(::visit); node.condition?.let(::expression); node.increment?.let(::expression); visit(node.body) }
                is AstVariableDeclaration -> node.initializer?.let(::expression)
                is AstBreak, is AstContinue -> Unit
                is AstInnerFunction -> node.function.body?.let(::visit)
            }
        }
        visit(statement)
    }
}
