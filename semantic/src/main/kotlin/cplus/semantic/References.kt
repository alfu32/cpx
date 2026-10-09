package cplus.semantic

import cplus.core.*

enum class ReferenceKind {
    READ,
    WRITE,
    CALL,
    TYPE,
    MEMBER
}

data class SymbolReference(
    val symbol: SymbolId,
    val node: NodeId,
    val origin: Origin,
    val kind: ReferenceKind
)

/** Mutable, rebuildable index of resolved AST references. */
class ReferenceIndex(references: Iterable<SymbolReference> = emptyList()) {
    private val byNode = linkedMapOf<NodeId, SymbolReference>()

    init {
        rebuild(references)
    }

    fun rebuild(references: Iterable<SymbolReference>) {
        byNode.clear()
        references.forEach { byNode[it.node] = it }
    }

    fun referencesTo(symbol: SymbolId): List<SymbolReference> = byNode.values
        .filter { it.symbol == symbol }

    fun referenceAt(node: NodeId): SymbolReference? = byNode[node]

    fun all(): List<SymbolReference> = byNode.values.toList()

    fun invalidate(nodes: Set<NodeId>) {
        nodes.forEach(byNode::remove)
    }
}

data class ResolvedAst(
    val program: AstProgram,
    val nodes: Map<NodeId, AstNode>,
    val referenceIndex: ReferenceIndex
) {
    fun symbolFor(node: NodeId): SymbolId? = referenceIndex.referenceAt(node)?.symbol
}

internal object ReferenceCollector {
    fun collect(model: SemanticModel, arena: AstArena = AstArena()): ResolvedAst {
        val nodes = linkedMapOf<NodeId, AstNode>()
        val references = mutableListOf<SymbolReference>()
        var nextLocalId = -1

        fun id(node: AstNode): NodeId = arena.add(node).also { nodes[it] = node }

        fun symbolFor(name: String, locals: Map<String, SymbolId>): SymbolId? =
            locals[name]
                ?: model.resolveFunction(name)?.symbol?.id
                ?: model.foreignGlobals[name]?.id
                ?: model.symbols.lastOrNull { it.name == name }?.id

        fun addReference(node: AstNode, locals: Map<String, SymbolId>, kind: ReferenceKind) {
            val expression = node as? AstIdentifier ?: return
            symbolFor(expression.name, locals)?.let { symbol ->
                references += SymbolReference(symbol, id(node), node.origin, kind)
            }
        }

        fun collectType(type: AstTypeRef) {
            id(type)
            model.symbolNamed(type.name)?.let { symbol ->
                references += SymbolReference(symbol.id, id(type), type.origin, ReferenceKind.TYPE)
            }
        }

        fun collectExpression(expression: AstExpression, locals: Map<String, SymbolId>, kind: ReferenceKind = ReferenceKind.READ) {
            id(expression)
            when (expression) {
                is AstIdentifier -> addReference(expression, locals, kind)
                is AstStringTemplate -> expression.parts.filterIsInstance<AstStringExpressionPart>()
                    .forEach { collectExpression(it.expression, locals) }
                is AstUnary -> collectExpression(expression.operand, locals)
                is AstBinary -> {
                    collectExpression(expression.left, locals)
                    collectExpression(expression.right, locals)
                }
                is AstConditional -> {
                    collectExpression(expression.condition, locals)
                    collectExpression(expression.thenBranch, locals)
                    collectExpression(expression.elseBranch, locals)
                }
                is AstUpdate -> collectExpression(expression.operand, locals, ReferenceKind.WRITE)
                is AstSizeOf -> {
                    expression.operand?.let { collectExpression(it, locals) }
                    expression.targetType?.let(::collectType)
                }
                is AstAbiQuery -> {
                    expression.operand?.let { collectExpression(it, locals) }
                    expression.targetType?.let(::collectType)
                }
                is AstCast -> {
                    collectType(expression.target)
                    collectExpression(expression.operand, locals)
                }
                is AstCall -> {
                    val member = expression.callee as? AstMemberAccess
                    val resolvedMethod = model.resolvedMethodCalls[expression]?.method
                    if (member != null && resolvedMethod?.isExtension == true) {
                        collectExpression(member.receiver, locals)
                        val sourceRange = member.origin.primaryRange
                        val memberOrigin = if (sourceRange != null && member.origin is Origin.Direct) {
                            Origin.Direct(
                                SourceRange(
                                    sourceRange.file,
                                    sourceRange.endOffset - member.member.length,
                                    sourceRange.endOffset
                                )
                            )
                        } else {
                            member.origin
                        }
                        references += SymbolReference(
                            resolvedMethod.symbol.id,
                            id(member),
                            memberOrigin,
                            ReferenceKind.CALL
                        )
                    } else {
                        collectExpression(expression.callee, locals, ReferenceKind.CALL)
                    }
                    expression.arguments.forEach { collectExpression(it, locals) }
                }
                is AstMemberAccess -> {
                    collectExpression(expression.receiver, locals)
                    model.symbolNamed(expression.member)?.let { symbol ->
                        references += SymbolReference(symbol.id, id(expression), expression.origin, ReferenceKind.MEMBER)
                    }
                }
                is AstIndexAccess -> {
                    collectExpression(expression.receiver, locals)
                    collectExpression(expression.index, locals)
                }
                is AstParenthesized -> collectExpression(expression.expression, locals)
                is AstIntegerLiteral,
                is AstBooleanLiteral,
                is AstFloatLiteral,
                is AstStringLiteral,
                is AstCharacterLiteral,
                is AstErrorExpression -> Unit
            }
        }

        fun collectStatement(statement: AstStatement, incoming: Map<String, SymbolId>) {
            id(statement)
            val locals = incoming.toMutableMap()
            when (statement) {
                is AstBlock -> statement.statements.forEach { collectStatement(it, locals) }
                is AstReturn -> statement.expression?.let { collectExpression(it, locals) }
                is AstExpressionStatement -> collectExpression(statement.expression, locals)
                is AstAssertion -> {
                    statement.description?.let { collectExpression(it, locals) }
                    statement.operands.forEach { collectExpression(it, locals) }
                }
                is AstDefer -> collectExpression(statement.expression, locals)
                is AstIf -> {
                    collectExpression(statement.condition, locals)
                    collectStatement(statement.thenBranch, locals.toMap())
                    statement.elseBranch?.let { collectStatement(it, locals.toMap()) }
                }
                is AstWhile -> {
                    collectExpression(statement.condition, locals)
                    collectStatement(statement.body, locals.toMap())
                }
                is AstFor -> {
                    statement.initializer?.let { collectStatement(it, locals) }
                    statement.condition?.let { collectExpression(it, locals) }
                    statement.increment?.let { collectExpression(it, locals) }
                    collectStatement(statement.body, locals.toMap())
                }
                is AstVariableDeclaration -> {
                    collectType(statement.type)
                    statement.initializer?.let { collectExpression(it, locals) }
                    locals[statement.name] = SymbolId(nextLocalId--)
                }
                is AstInnerFunction -> {
                    val nestedLocals = locals.toMutableMap()
                    statement.function.parameters.forEach { parameter ->
                        id(parameter)
                        collectType(parameter.type)
                        nestedLocals[parameter.name] = SymbolId(nextLocalId--)
                    }
                    statement.function.body?.let { collectStatement(it, nestedLocals) }
                }
                is AstBreak, is AstContinue -> Unit
            }
        }

        fun collectFunction(function: AstFunction, owner: String? = null, extensionMethod: MethodSymbol? = null) {
            id(function)
            collectType(function.returnType)
            val semanticFunction = extensionMethod?.let { method ->
                FunctionSymbol(method.symbol, method.returnType, method.parameters, signature = method.signature)
            } ?: if (owner == null) model.functions[function.name] else {
                model.methods[owner]?.get(function.name)?.let { method ->
                    FunctionSymbol(method.symbol, method.returnType, method.parameters, signature = method.signature)
                }
            }
            val locals = linkedMapOf<String, SymbolId>()
            semanticFunction?.parameters?.forEach { parameter -> locals[parameter.name] = parameter.id }
            function.parameters.forEach { parameter ->
                id(parameter)
                collectType(parameter.type)
                if (parameter.isReceiver || parameter.name !in locals) {
                    locals[parameter.name] = SymbolId(nextLocalId--)
                }
            }
            function.body?.let { collectStatement(it, locals) }
        }

        model.program.declarations.forEach { declaration ->
            id(declaration)
            when (declaration) {
                is AstAlias -> collectType(declaration.target)
                is AstUnion -> declaration.fields.forEach { field -> id(field); collectType(field.type) }
                is AstEnum -> declaration.values.forEach(::id)
                is AstStruct -> {
                    declaration.fields.forEach { field -> id(field); collectType(field.type) }
                    declaration.methods.forEach { collectFunction(it, declaration.name) }
                }
                is AstTrait -> {
                    declaration.methods.forEach { method ->
                        val extension = model.extensionMethodFor(declaration, method)
                        collectFunction(method, extensionMethod = extension)
                    }
                }
                is AstGlobalVariable -> {
                    collectType(declaration.type)
                    declaration.initializer?.let { collectExpression(it, emptyMap()) }
                }
                is AstFunction -> collectFunction(declaration)
                is AstTestFixture,
                is AstPackage,
                is AstComptimeFunction,
                is AstCpxInvocation,
                is AstImport -> Unit
            }
        }

        return ResolvedAst(model.program, nodes, ReferenceIndex(references))
    }

    fun collectFragment(
        model: SemanticModel,
        node: AstNode,
        arena: AstArena,
        containingFunctionName: String? = null
    ): Map<NodeId, SymbolId> {
        arena.add(node)
        val origin = node.origin
        val functionName = containingFunctionName ?: "__cpx_fragment"
        val voidType = AstTypeRef("void", false, 0, origin)
        val wrapped = when (node) {
            is AstExpression -> AstProgram(
                listOf(
                    AstFunction(
                        voidType,
                        functionName,
                        emptyList(),
                        AstBlock(listOf(AstReturn(node, origin)), origin),
                        origin = origin
                    )
                ),
                origin
            )
            is AstStatement -> AstProgram(
                listOf(
                    AstFunction(
                        voidType,
                        functionName,
                        emptyList(),
                        AstBlock(listOf(node), origin),
                        origin = origin
                    )
                ),
                origin
            )
            is AstField -> AstProgram(
                listOf(AstStruct("__cpx_fragment", listOf(node), origin = origin)),
                origin
            )
            is AstDeclaration -> AstProgram(listOf(node), origin)
            is AstProgram -> node
            else -> AstProgram(emptyList(), origin)
        }
        return collect(model.copy(program = wrapped), arena).referenceIndex.all()
            .associate { it.node to it.symbol }
    }
}
