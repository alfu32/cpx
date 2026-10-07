package cplus.backend

import cplus.core.*
import cplus.semantic.*

data class LoweredCResult(
    val unit: CTranslationUnit,
    val diagnostics: List<Diagnostic>
)

class CLowerer(
    private val semantic: SemanticModel,
    private val names: CNameMangler = DefaultCNameMangler()
) {
    private val diagnostics = DiagnosticBag()

    fun lower(program: AstProgram): LoweredCResult {
        val requiresStringTemplateRuntime = program.declarations.any(::containsStringTemplate)
        val includes = CDependencyCollector().collect(program, semantic, requiresStringTemplateRuntime)
        val structs = program.declarations.filterIsInstance<AstStruct>().map { declaration ->
            CStructDeclaration(
                declaration.name,
                declaration.fields.map { field ->
                    CField(type(field.type), field.name, field.origin, field.arrayDimensions)
                },
                declaration.origin,
                declaration.isPublic
            )
        }
        val unions = program.declarations.filterIsInstance<AstUnion>().map { declaration ->
            CUnionDeclaration(
                declaration.name,
                declaration.fields.map { field -> CField(type(field.type), field.name, field.origin, field.arrayDimensions) },
                declaration.origin,
                declaration.isPublic
            )
        }
        val enums = program.declarations.filterIsInstance<AstEnum>().map { declaration ->
            CEnumDeclaration(
                declaration.name,
                declaration.values.map { value -> CEnumValue(value.name, value.value, value.origin) },
                declaration.origin,
                declaration.isPublic
            )
        }
        val aliases = program.declarations.filterIsInstance<AstAlias>().map { declaration ->
            CAliasDeclaration(
                declaration.name,
                type(declaration.target),
                declaration.arrayDimensions,
                declaration.origin,
                declaration.isPublic
            )
        }
        val aggregatesInSourceOrder = buildList<CAggregateDeclaration> {
            program.declarations.forEach { declaration ->
                when (declaration) {
                    is AstStruct -> add(structs.first { it.name == declaration.name })
                    is AstUnion -> add(unions.first { it.name == declaration.name })
                    else -> Unit
                }
            }
        }
        fun aggregateKey(aggregate: CAggregateDeclaration): String = when (aggregate) {
            is CStructDeclaration -> "struct:${aggregate.name}"
            is CUnionDeclaration -> "union:${aggregate.name}"
        }
        fun byValueDependency(type: CType): String? = when (type) {
            is CType.Struct -> if (type.pointerDepth == 0) "struct:${type.name}" else null
            is CType.Union -> if (type.pointerDepth == 0) "union:${type.name}" else null
            else -> null
        }
        val aggregateByKey = aggregatesInSourceOrder.associateBy(::aggregateKey)
        val aggregateDependencies = aggregatesInSourceOrder.associate { aggregate ->
            aggregateKey(aggregate) to aggregate.fields.mapNotNull { byValueDependency(it.type) }
                .filter { it in aggregateByKey }
                .toSet()
        }
        val pendingAggregates = aggregatesInSourceOrder.toMutableList()
        val emittedAggregateKeys = mutableSetOf<String>()
        val orderedAggregates = mutableListOf<CAggregateDeclaration>()
        while (pendingAggregates.isNotEmpty()) {
            val next = pendingAggregates.firstOrNull { aggregate ->
                aggregateDependencies.getValue(aggregateKey(aggregate)).all { it in emittedAggregateKeys }
            }
            if (next == null) {
                val cycle = pendingAggregates.first()
                diagnostics.error(
                    "cyclic by-value aggregate dependency involving '${cycle.name}'",
                    cycle.origin.primaryRange,
                    "LOW102"
                )
                orderedAggregates += pendingAggregates
                break
            }
            pendingAggregates.remove(next)
            orderedAggregates += next
            emittedAggregateKeys += aggregateKey(next)
        }
        val aggregateIndexes = orderedAggregates.mapIndexed { index, aggregate -> aggregateKey(aggregate) to index }.toMap()
        val aggregateOrigins = orderedAggregates.associate { aggregateKey(it) to it.origin }
        val forwardNames = linkedSetOf<Pair<CTagKind, String>>()

        fun collectForward(type: CType, ownerIndex: Int) {
            when (type) {
                is CType.Struct -> {
                    val targetIndex = aggregateIndexes["struct:${type.name}"]
                    if (type.pointerDepth > 0 && targetIndex != null && targetIndex > ownerIndex) {
                        forwardNames += CTagKind.STRUCT to type.name
                    }
                }
                is CType.Union -> {
                    val targetIndex = aggregateIndexes["union:${type.name}"]
                    if (type.pointerDepth > 0 && targetIndex != null && targetIndex > ownerIndex) {
                        forwardNames += CTagKind.UNION to type.name
                    }
                }
                else -> Unit
            }
        }

        orderedAggregates.forEachIndexed { index, declaration ->
            declaration.fields.forEach { field -> collectForward(field.type, index) }
        }
        val forwardDeclarations = forwardNames
            .sortedWith(compareBy<Pair<CTagKind, String>> { it.first.ordinal }.thenBy { it.second })
            .map { (kind, name) ->
                CForwardDeclaration(kind, name, aggregateOrigins.getValue("${kind.name.lowercase()}:$name"))
            }
        val programGlobals = program.declarations.filterIsInstance<AstGlobalVariable>().map { declaration ->
            CGlobalDeclaration(
                type(declaration.type),
                declaration.name,
                declaration.initializer?.let(::expression),
                declaration.origin,
                declaration.arrayDimensions,
                isPublic = declaration.isPublic
            )
        }
        val declaredGlobalNames = programGlobals.mapTo(mutableSetOf()) { it.name }
        val foreignGlobals = semantic.foreignGlobals.values
            .filter {
                it.kind == SymbolKind.FOREIGN_GLOBAL &&
                    it.moduleName?.startsWith("c.source.") == true &&
                    (it.externalName ?: it.name) !in declaredGlobalNames
            }
            .map { symbol ->
                CGlobalDeclaration(
                    foreignType(symbol.type),
                    symbol.externalName ?: symbol.name,
                    null,
                    symbol.origin,
                    isExtern = true
                )
            }
        val globals = programGlobals + foreignGlobals
        val programFunctions = program.declarations.filterIsInstance<AstFunction>().map { declaration ->
            CFunction(
                type(declaration.returnType),
                semantic.functions[declaration.name]?.symbol?.let { it.externalName ?: names.nameOf(it) } ?: declaration.name,
                declaration.parameters.map { CParameter(type(it.type), it.name, it.origin, it.arrayDimensions) },
                declaration.body?.let { lowerBody(it, declaration.ownerName, declaration.isMethod) },
                declaration.origin,
                isPublic = declaration.isPublic
            )
        } + program.declarations.filterIsInstance<AstStruct>().flatMap { structure ->
            structure.methods.map { method -> lowerMethod(structure.name, method).copy(isPublic = structure.isPublic || method.isPublic) }
        }
        val declaredFunctionNames = programFunctions.mapTo(mutableSetOf()) { it.name }
        val foreignFunctions = semantic.functions.values
            .filter {
                it.symbol.kind == SymbolKind.FOREIGN &&
                    it.symbol.moduleName?.startsWith("c.source.") == true &&
                    it.symbol.name !in declaredFunctionNames
            }
            .map { function ->
                CFunction(
                    foreignType(function.returnType),
                    names.nameOf(function.symbol),
                    function.parameters.map { parameter ->
                        CParameter(foreignType(parameter.type), parameter.name, parameter.origin)
                    },
                    null,
                    function.symbol.origin,
                    function.isVariadic
                )
            }
        val functions = programFunctions + foreignFunctions
        functions.groupBy { it.name }
            .filterValues { it.size > 1 }
            .forEach { (name, declarations) ->
                diagnostics.error(
                    "multiple declarations lower to C symbol '$name'",
                    declarations.first().origin.primaryRange,
                    "LOW401"
                )
            }
        val unit = CTranslationUnit(
                includes,
                structs,
                unions,
                enums,
                aliases,
                globals,
                functions,
                requiresStringTemplateRuntime,
                forwardDeclarations,
                orderedAggregates
            )
        val unitWithDependencies = unit.copy(
            publicIncludes = CDependencyCollector().collectPublic(program, unit),
            runtimeDependencies = CRuntimeDependencyCatalogue.collect(unit)
        )
        diagnostics.addAll(CSubsetValidator().validate(unitWithDependencies))
        return LoweredCResult(
            unitWithDependencies,
            diagnostics.diagnostics
        )
    }

    private fun containsStringTemplate(declaration: AstDeclaration): Boolean = when (declaration) {
        is AstGlobalVariable -> declaration.initializer?.let(::containsStringTemplate) == true
        is AstFunction -> declaration.body?.let(::containsStringTemplate) == true
        is AstStruct -> declaration.methods.any { it.body?.let(::containsStringTemplate) == true }
        else -> false
    }

    private fun containsStringTemplate(statement: AstStatement): Boolean = when (statement) {
        is AstBlock -> statement.statements.any(::containsStringTemplate)
        is AstReturn -> statement.expression?.let(::containsStringTemplate) == true
        is AstExpressionStatement -> containsStringTemplate(statement.expression)
        is AstDefer -> containsStringTemplate(statement.expression)
        is AstIf -> containsStringTemplate(statement.condition) ||
            containsStringTemplate(statement.thenBranch) ||
            (statement.elseBranch?.let(::containsStringTemplate) == true)
        is AstWhile -> containsStringTemplate(statement.condition) || containsStringTemplate(statement.body)
        is AstFor -> (statement.initializer?.let(::containsStringTemplate) == true) ||
            (statement.condition?.let(::containsStringTemplate) == true) ||
            (statement.increment?.let(::containsStringTemplate) == true) ||
            containsStringTemplate(statement.body)
        is AstBreak, is AstContinue, is AstInnerFunction -> false
        is AstVariableDeclaration -> statement.initializer?.let(::containsStringTemplate) == true
    }

    private fun containsStringTemplate(expression: AstExpression): Boolean = when (expression) {
        is AstStringTemplate -> true
        is AstUnary -> containsStringTemplate(expression.operand)
        is AstBinary -> containsStringTemplate(expression.left) || containsStringTemplate(expression.right)
        is AstConditional -> containsStringTemplate(expression.condition) ||
            containsStringTemplate(expression.thenBranch) ||
            containsStringTemplate(expression.elseBranch)
        is AstUpdate -> containsStringTemplate(expression.operand)
        is AstSizeOf -> expression.operand?.let(::containsStringTemplate) == true
        is AstAbiQuery -> expression.operand?.let(::containsStringTemplate) == true
        is AstCast -> containsStringTemplate(expression.operand)
        is AstCall -> containsStringTemplate(expression.callee) || expression.arguments.any(::containsStringTemplate)
        is AstMemberAccess -> containsStringTemplate(expression.receiver)
        is AstIndexAccess -> containsStringTemplate(expression.receiver) || containsStringTemplate(expression.index)
        is AstParenthesized -> containsStringTemplate(expression.expression)
        else -> false
    }

    private fun foreignType(type: cplus.semantic.CType): CType {
        var current = type
        var pointerDepth = 0
        while (current is PointerType) {
            pointerDepth++
            current = current.pointee
        }
        val base = when (current) {
            is PrimitiveType -> CType.Primitive(current.name)
            is ForeignType -> CType.Named(current.externalName)
            is AliasType -> foreignType(current.target)
            is FunctionType -> CType.FunctionPointer(
                foreignType(current.returnType),
                current.parameterTypes.map(::foreignType),
                current.isVariadic
            )
            is StructType -> CType.Struct(current.name)
            is UnionType -> CType.Union(current.name)
            is EnumType -> CType.Enum(current.name)
            else -> CType.Unknown
        }
        return when (base) {
            is CType.Primitive -> base.copy(pointerDepth = base.pointerDepth + pointerDepth)
            is CType.Named -> base.copy(pointerDepth = base.pointerDepth + pointerDepth)
            is CType.Struct -> base.copy(pointerDepth = base.pointerDepth + pointerDepth)
            is CType.Union -> base.copy(pointerDepth = base.pointerDepth + pointerDepth)
            is CType.Enum -> base.copy(pointerDepth = base.pointerDepth + pointerDepth)
            is CType.FunctionPointer -> base.copy(pointerDepth = base.pointerDepth + pointerDepth)
            CType.Unknown -> CType.Unknown
        }
    }

    private fun lowerMethod(ownerName: String, method: AstFunction): CFunction {
        val instance = method.parameters.any { it.isReceiver }
        val parameters = buildList {
            if (instance) add(CParameter(CType.Struct(ownerName, pointerDepth = 1), "self", method.origin))
            method.parameters.filterNot { it.isReceiver }.forEach {
                add(CParameter(type(it.type), it.name, it.origin, it.arrayDimensions))
            }
        }
        return CFunction(
            type(method.returnType),
            semantic.methods[ownerName]?.get(method.name)?.let { names.methodName(it.owner, it) }
                ?: "${ownerName}__${method.name}",
            parameters,
            method.body?.let { lowerBody(it, ownerName, instance) },
            method.origin
        )
    }

    private fun type(reference: AstTypeRef): CType {
        val functionParameters = reference.functionParameters
        val result = when {
            functionParameters != null -> CType.FunctionPointer(
                type(
                    reference.copy(
                        functionParameters = null,
                        functionVariadic = false,
                        functionPointerDepth = 0,
                        functionPointerQualifiers = emptyList()
                    )
                ),
                functionParameters.map { type(it.type) },
                reference.functionVariadic,
                reference.functionPointerDepth,
                reference.functionPointerQualifiers
            )
            semantic.aliases.containsKey(reference.name) -> CType.Named(
                reference.name,
                reference.pointerDepth,
                reference.qualifiers,
                reference.pointerQualifiers
            )
            semantic.foreignTypes.containsKey(reference.name) -> CType.Named(
                reference.name,
                reference.pointerDepth,
                reference.qualifiers,
                reference.pointerQualifiers
            )
            reference.declarationKind == "union" || semantic.unions.containsKey(reference.name) -> {
                CType.Union(reference.name, reference.pointerDepth, reference.qualifiers, reference.pointerQualifiers)
            }
            reference.declarationKind == "enum" || semantic.enums.containsKey(reference.name) -> {
                CType.Enum(reference.name, reference.pointerDepth, reference.qualifiers, reference.pointerQualifiers)
            }
            reference.isStruct || semantic.structs.containsKey(reference.name) -> {
                CType.Struct(reference.name, reference.pointerDepth, reference.qualifiers, reference.pointerQualifiers)
            }
            reference.name in primitiveNames -> CType.Primitive(
                reference.name,
                reference.pointerDepth,
                reference.qualifiers,
                reference.pointerQualifiers
            )
            else -> {
                diagnostics.error("cannot lower unknown type '${reference.name}'", reference.origin.primaryRange, "LOW101")
                CType.Unknown
            }
        }
        return result
    }

    private data class LoweredStatements(
        val statements: List<CStatement>,
        val fallsThrough: Boolean
    )

    private class LoweringContext {
        val cleanupScopes = mutableListOf<MutableList<CExpression>>()
        val loopBoundaries = mutableListOf<Int>()
    }

    private fun lowerBody(node: AstStatement, ownerName: String?, instanceMethod: Boolean): CStatement {
        val context = LoweringContext()
        return asStatement(lowerStatement(node, ownerName, instanceMethod, context).statements, node.origin)
    }

    private fun lowerStatement(
        node: AstStatement,
        ownerName: String?,
        instanceMethod: Boolean,
        context: LoweringContext
    ): LoweredStatements = when (node) {
        is AstBlock -> lowerBlock(node, ownerName, instanceMethod, context)
        is AstReturn -> {
            val cleanup = cleanupForReturn(context, node.origin)
            LoweredStatements(
                cleanup + CReturn(node.expression?.let { expression(it, ownerName, instanceMethod) }, node.origin),
                fallsThrough = false
            )
        }
        is AstExpressionStatement -> LoweredStatements(
            listOf(CExpressionStatement(expression(node.expression, ownerName, instanceMethod), node.origin)),
            fallsThrough = true
        )
        is AstDefer -> {
            val scope = context.cleanupScopes.lastOrNull()
            if (scope == null) {
                diagnostics.error("defer must be lowered inside a lexical block", node.origin.primaryRange, "LOW301")
            } else {
                scope += expression(node.expression, ownerName, instanceMethod)
            }
            LoweredStatements(emptyList(), fallsThrough = true)
        }
        is AstIf -> {
            val thenBranch = lowerStatement(node.thenBranch, ownerName, instanceMethod, context)
            val elseBranch = node.elseBranch?.let { lowerStatement(it, ownerName, instanceMethod, context) }
            val cIf = CIf(
                expression(node.condition, ownerName, instanceMethod),
                asStatement(thenBranch.statements, node.thenBranch.origin),
                elseBranch?.let { asStatement(it.statements, node.elseBranch!!.origin) },
                node.origin
            )
            LoweredStatements(
                listOf(cIf),
                fallsThrough = thenBranch.fallsThrough || elseBranch?.fallsThrough ?: true
            )
        }
        is AstWhile -> {
            val boundary = context.cleanupScopes.size
            context.loopBoundaries += boundary
            val body = lowerStatement(node.body, ownerName, instanceMethod, context)
            context.loopBoundaries.removeAt(context.loopBoundaries.lastIndex)
            LoweredStatements(
                listOf(CWhile(expression(node.condition, ownerName, instanceMethod), asStatement(body.statements, node.body.origin), node.origin)),
                fallsThrough = true
            )
        }
        is AstFor -> {
            val initializer = node.initializer?.let {
                val loweredInitializer = lowerStatement(it, ownerName, instanceMethod, context).statements
                asStatement(loweredInitializer, it.origin)
            }
            val boundary = context.cleanupScopes.size
            context.loopBoundaries += boundary
            val body = lowerStatement(node.body, ownerName, instanceMethod, context)
            context.loopBoundaries.removeAt(context.loopBoundaries.lastIndex)
            LoweredStatements(
                listOf(
                    CFor(
                        initializer,
                        node.condition?.let { expression(it, ownerName, instanceMethod) },
                        node.increment?.let { expression(it, ownerName, instanceMethod) },
                        asStatement(body.statements, node.body.origin),
                        node.origin
                    )
                ),
                fallsThrough = true
            )
        }
        is AstBreak -> {
            val boundary = context.loopBoundaries.lastOrNull()
            if (boundary == null) {
                diagnostics.error("break is outside a lowered loop", node.origin.primaryRange, "LOW302")
                LoweredStatements(emptyList(), fallsThrough = false)
            } else {
                LoweredStatements(cleanupForExit(context, boundary, node.origin) + CBreak(node.origin), fallsThrough = false)
            }
        }
        is AstContinue -> {
            val boundary = context.loopBoundaries.lastOrNull()
            if (boundary == null) {
                diagnostics.error("continue is outside a lowered loop", node.origin.primaryRange, "LOW303")
                LoweredStatements(emptyList(), fallsThrough = false)
            } else {
                LoweredStatements(cleanupForExit(context, boundary, node.origin) + CContinue(node.origin), fallsThrough = false)
            }
        }
        is AstVariableDeclaration -> LoweredStatements(
            listOf(
                CVariableDeclaration(
                    type(node.type),
                    node.name,
                    node.initializer?.let { expression(it, ownerName, instanceMethod) },
                    node.origin,
                    node.arrayDimensions
                )
            ),
            fallsThrough = true
        )
        is AstInnerFunction -> {
            diagnostics.error(
                "inner function reached C lowering without closure transformation",
                node.origin.primaryRange,
                "LOW501"
            )
            LoweredStatements(emptyList(), fallsThrough = true)
        }
    }

    private fun lowerBlock(
        block: AstBlock,
        ownerName: String?,
        instanceMethod: Boolean,
        context: LoweringContext
    ): LoweredStatements {
        context.cleanupScopes.add(mutableListOf())
        val lowered = mutableListOf<CStatement>()
        var fallsThrough = true
        for (statement in block.statements) {
            if (!fallsThrough) break
            val result = lowerStatement(statement, ownerName, instanceMethod, context)
            lowered += result.statements
            fallsThrough = result.fallsThrough
        }
        if (fallsThrough) {
            lowered += cleanupForExit(context, context.cleanupScopes.lastIndex, block.origin)
        }
        context.cleanupScopes.removeAt(context.cleanupScopes.lastIndex)
        return LoweredStatements(listOf(CBlock(lowered, block.origin)), fallsThrough)
    }

    private fun cleanupForReturn(context: LoweringContext, origin: Origin): List<CStatement> =
        cleanupForExit(context, 0, origin)

    private fun cleanupForExit(context: LoweringContext, boundary: Int, origin: Origin): List<CStatement> {
        if (context.cleanupScopes.isEmpty()) return emptyList()
        return context.cleanupScopes
            .asReversed()
            .take(context.cleanupScopes.size - boundary)
            .flatMap { scope -> scope.asReversed() }
            .map { cleanup -> CExpressionStatement(cleanup, origin) }
    }

    private fun asStatement(statements: List<CStatement>, origin: Origin): CStatement = when (statements.size) {
        0 -> CBlock(emptyList(), origin)
        1 -> statements.single()
        else -> CBlock(statements, origin)
    }

    private fun expression(node: AstExpression, ownerName: String? = null, instanceMethod: Boolean = false): CExpression = when (node) {
        is AstIntegerLiteral -> CIntegerLiteral(node.text, node.origin)
        is AstBooleanLiteral -> CIntegerLiteral(if (node.text == "true") "1" else "0", node.origin)
        is AstFloatLiteral -> CFloatLiteral(node.text, node.origin)
        is AstStringLiteral -> CStringLiteral(node.text, node.origin)
        is AstStringTemplate -> lowerStringTemplate(node, ownerName, instanceMethod)
        is AstCharacterLiteral -> CCharacterLiteral(node.text, node.origin)
        is AstIdentifier -> CIdentifier(node.name, node.origin)
        is AstUnary -> CUnary(node.operator, expression(node.operand, ownerName, instanceMethod), node.origin)
        is AstBinary -> {
            val left = expression(node.left, ownerName, instanceMethod)
            val right = expression(node.right, ownerName, instanceMethod)
            if (node.operator in assignmentOperators) CAssignment(left, node.operator, right, node.origin)
            else CBinary(left, node.operator, right, node.origin)
        }
        is AstConditional -> CConditional(
            expression(node.condition, ownerName, instanceMethod),
            expression(node.thenBranch, ownerName, instanceMethod),
            expression(node.elseBranch, ownerName, instanceMethod),
            node.origin
        )
        is AstUpdate -> CUpdate(expression(node.operand, ownerName, instanceMethod), node.operator, node.prefix, node.origin)
        is AstSizeOf -> CSizeOf(
            node.operand?.let { expression(it, ownerName, instanceMethod) },
            node.targetType?.let(::type),
            node.origin
        )
        is AstAbiQuery -> CAbiQuery(
            node.query,
            node.operand?.let { expression(it, ownerName, instanceMethod) },
            node.targetType?.let(::type),
            node.fieldName,
            node.origin
        )
        is AstCast -> CCast(type(node.target), expression(node.operand, ownerName, instanceMethod), node.origin)
        is AstCall -> lowerCall(node, ownerName, instanceMethod)
        is AstMemberAccess -> {
            val receiver = node.receiver
            CMemberAccess(
                expression(receiver, ownerName, instanceMethod),
                node.member,
                pointerReceiver = instanceMethod && receiver is AstIdentifier && receiver.name == "self" || isPointerReceiver(receiver),
                node.origin
            )
        }
        is AstIndexAccess -> CIndexAccess(
            expression(node.receiver, ownerName, instanceMethod),
            expression(node.index, ownerName, instanceMethod),
            node.origin
        )
        is AstParenthesized -> CParenthesized(expression(node.expression, ownerName, instanceMethod), node.origin)
        is AstErrorExpression -> {
            diagnostics.error(
                "cannot lower a syntax-error expression to C",
                node.origin.primaryRange,
                "LOW408"
            )
            // Keep lowering structurally total so callers can inspect the
            // partial C AST, while the diagnostic prevents it from being
            // emitted as a successful translation unit.
            CIntegerLiteral("0", node.origin)
        }
    }

    private fun lowerStringTemplate(node: AstStringTemplate, ownerName: String?, instanceMethod: Boolean): CExpression {
        val format = buildString {
            node.parts.forEach { part ->
                when (part) {
                    is AstStringTextPart -> append(part.text.replace("%", "%%"))
                    is AstStringExpressionPart -> append(templateSpecifier(part.expression))
                }
            }
        }
        val arguments = buildList {
            add(CStringLiteral("\"$format\"", node.origin))
            node.parts.filterIsInstance<AstStringExpressionPart>().forEach { part ->
                add(expression(part.expression, ownerName, instanceMethod))
            }
        }
        return CCall(CIdentifier("__cplus_format", node.origin), arguments, node.origin)
    }

    private fun templateSpecifier(expression: AstExpression): String = templateSpecifier(semantic.expressionTypes[expression])

    private fun templateSpecifier(type: cplus.semantic.CType?): String = when (val resolved = unwrapAlias(type)) {
        is cplus.semantic.PointerType -> {
            val pointee = unwrapAlias(resolved.pointee)
            if (pointee is cplus.semantic.PrimitiveType && pointee.name == "char") "%s" else "%p"
        }
        is cplus.semantic.PrimitiveType -> when (resolved.name) {
            "char" -> "%c"
            "float", "double" -> "%g"
            else -> "%d"
        }
        else -> "%p"
    }

    private fun unwrapAlias(type: cplus.semantic.CType?): cplus.semantic.CType? = when (type) {
        is cplus.semantic.AliasType -> unwrapAlias(type.target)
        else -> type
    }

    private val assignmentOperators = setOf("=", "+=", "-=", "*=", "/=", "%=")

    private fun lowerCall(node: AstCall, ownerName: String?, instanceMethod: Boolean): CExpression {
        val member = node.callee as? AstMemberAccess
        if (member == null) {
            val directFunction = (node.callee as? AstIdentifier)?.let { semantic.resolveFunction(it.name) }
            val callee = directFunction?.let { CIdentifier(names.nameOf(it.symbol), node.callee.origin) }
                ?: expression(node.callee, ownerName, instanceMethod)
            return CCall(
                callee,
                node.arguments.map { expression(it, ownerName, instanceMethod) },
                node.origin
            )
        }

        val qualifiedName = (member.receiver as? AstIdentifier)?.let { receiver ->
            "${receiver.name}.${member.member}"
        }
        if (qualifiedName != null && qualifiedName in semantic.qualifiedFunctionNames) {
            val qualifiedFunction = semantic.resolveFunction(qualifiedName)
            return CCall(
                CIdentifier(qualifiedFunction?.let { names.nameOf(it.symbol) } ?: member.member, node.origin),
                node.arguments.map { expression(it, ownerName, instanceMethod) },
                node.origin
            )
        }

        val receiverType = semantic.expressionTypes[member.receiver]
        val owner = ownerStruct(member.receiver, receiverType)
        val method = owner?.methods?.firstOrNull { it.symbol.name == member.member }
        if (method == null) {
            diagnostics.error("cannot lower unknown method '${member.member}'", member.origin.primaryRange, "LOW201")
            return CCall(
                expression(node.callee, ownerName, instanceMethod),
                node.arguments.map { expression(it, ownerName, instanceMethod) },
                node.origin
            )
        }
        val target = CIdentifier(names.methodName(owner, method), node.origin)
        val arguments = buildList {
            if (method.receiverKind == cplus.semantic.ReceiverKind.INSTANCE) {
                val receiver = member.receiver
                if (instanceMethod && receiver is AstIdentifier && receiver.name == "self") {
                    add(CIdentifier("self", receiver.origin))
                } else if (isPointerReceiver(receiver)) {
                    add(expression(receiver, ownerName, instanceMethod))
                } else {
                    add(CUnary("&", expression(receiver, ownerName, instanceMethod), receiver.origin))
                }
            }
            node.arguments.forEach { add(expression(it, ownerName, instanceMethod)) }
        }
        return CCall(target, arguments, node.origin)
    }

    private fun ownerStruct(receiver: AstExpression, receiverType: cplus.semantic.CType?): cplus.semantic.StructType? = when (receiver) {
        is AstIdentifier -> semantic.structs[receiver.name] ?: aggregateStruct(receiverType)?.let { semantic.structs[it.name] ?: it }
        else -> aggregateStruct(receiverType)?.let { semantic.structs[it.name] ?: it }
    }

    private fun aggregateStruct(type: cplus.semantic.CType?): cplus.semantic.StructType? = when (type) {
        is cplus.semantic.AliasType -> aggregateStruct(type.target)
        is cplus.semantic.PointerType -> aggregateStruct(type.pointee)
        is cplus.semantic.StructType -> type
        else -> null
    }

    private fun isPointerReceiver(expression: AstExpression): Boolean = when (val type = semantic.expressionTypes[expression]) {
        is cplus.semantic.AliasType -> isPointerType(type.target)
        else -> isPointerType(type)
    }

    private fun isPointerType(type: cplus.semantic.CType?): Boolean = when (type) {
        is cplus.semantic.PointerType -> true
        is cplus.semantic.AliasType -> isPointerType(type.target)
        else -> false
    }

    companion object {
        private val primitiveNames = setOf(
            "void", "bool", "char", "short", "int", "long", "float", "double", "signed", "unsigned",
            "signed char", "unsigned char", "signed short", "unsigned short",
            "signed int", "unsigned int", "long long", "unsigned long", "unsigned long long",
            "size_t", "ptrdiff_t", "max_align_t"
        )
    }
}

class CEmitter {
    fun emit(unit: CTranslationUnit): GeneratedCUnit {
        val output = StringBuilder()
        val mappings = mutableListOf<SourceMapping>()
        var line = 1
        var byteOffset = 0

        fun append(text: String) {
            output.append(text)
            line += text.count { it == '\n' }
            byteOffset += text.toByteArray(Charsets.UTF_8).size
        }

        fun appendLine(text: String = "", origin: Origin? = null) {
            if (origin != null) {
                val lineBytes = text.toByteArray(Charsets.UTF_8).size
                mappings += SourceMapping(line, origin, byteOffset, byteOffset + lineBytes)
            }
            append(text)
            append("\n")
        }

        if (usesBool(unit)) {
            appendLine("#include <stdbool.h>")
        }
        unit.includes.sorted().forEach { include -> appendLine("#include <$include>") }
        if (usesBool(unit) || unit.includes.isNotEmpty()) appendLine()

        unit.forwardDeclarations.forEach { declaration ->
            val keyword = declaration.kind.name.lowercase()
            appendLine("$keyword ${declaration.name};", declaration.origin)
        }
        if (unit.forwardDeclarations.isNotEmpty()) appendLine()

        val aggregateDefinitions: List<CAggregateDeclaration> = if (unit.aggregateDeclarations.isNotEmpty()) {
            unit.aggregateDeclarations
        } else {
            buildList {
                addAll(unit.structs)
                addAll(unit.unions)
            }
        }
        aggregateDefinitions.forEachIndexed { index, aggregate ->
            when (aggregate) {
                is CStructDeclaration -> {
                    appendLine("struct ${aggregate.name} {", aggregate.origin)
                    aggregate.fields.forEach { field ->
                        appendLine("    ${field.type.renderDeclaration(field.name)}${arraySuffix(field.arrayDimensions)};", field.origin)
                    }
                    appendLine("};", aggregate.origin)
                }
                is CUnionDeclaration -> {
                    appendLine("union ${aggregate.name} {", aggregate.origin)
                    aggregate.fields.forEach { field ->
                        appendLine("    ${field.type.render()} ${field.name}${arraySuffix(field.arrayDimensions)};", field.origin)
                    }
                    appendLine("};", aggregate.origin)
                }
            }
            if (index != aggregateDefinitions.lastIndex || unit.enums.isNotEmpty() || unit.globals.isNotEmpty() || unit.functions.isNotEmpty()) appendLine()
        }

        unit.enums.forEachIndexed { index, enum ->
            appendLine("enum ${enum.name} {", enum.origin)
            enum.values.forEach { value ->
                val assigned = value.value?.let { " = $it" }.orEmpty()
                appendLine("    ${value.name}$assigned,", value.origin)
            }
            appendLine("};", enum.origin)
            if (index != unit.enums.lastIndex || unit.aliases.isNotEmpty() || unit.globals.isNotEmpty() || unit.functions.isNotEmpty()) appendLine()
        }

        unit.aliases.forEach { alias ->
            appendLine("typedef ${alias.target.renderDeclaration(alias.name)}${arraySuffix(alias.arrayDimensions)};", alias.origin)
        }
        if (unit.aliases.isNotEmpty() && (unit.globals.isNotEmpty() || unit.functions.isNotEmpty())) appendLine()

        if (unit.requiresStringTemplateRuntime) {
            appendLine("const char* __cplus_format(const char* format, ...);")
            if (unit.globals.isNotEmpty() || unit.functions.isNotEmpty()) appendLine()
        }

        unit.globals.forEach { global ->
            val initializer = global.initializer?.let { " = ${expression(it)}" } ?: ""
            val storage = if (global.isExtern) "extern " else ""
            appendLine("$storage${global.type.renderDeclaration(global.name)}${arraySuffix(global.arrayDimensions)}$initializer;", global.origin)
        }
        if (unit.globals.isNotEmpty() && unit.functions.isNotEmpty()) appendLine()

        unit.functions.forEach { function ->
            appendLine("${function.returnType.render()} ${function.name}(${parameters(function.parameters, function.isVariadic)});", function.origin)
        }
        val definitions = unit.functions.filter { it.body != null }
        if (definitions.isNotEmpty()) appendLine()
        definitions.forEachIndexed { index, function ->
            emitFunction(function, ::appendLine, ::append)
            if (index != definitions.lastIndex) appendLine()
        }

        return GeneratedCUnit(output.toString(), mappings)
    }

    private fun usesBool(unit: CTranslationUnit): Boolean {
        fun typeUsesBool(type: CType): Boolean = type is CType.Primitive && type.name == "bool"
        fun statementUsesBool(statement: CStatement): Boolean = when (statement) {
            is CBlock -> statement.statements.any(::statementUsesBool)
            is CReturn -> false
            is CExpressionStatement -> false
            is CVariableDeclaration -> typeUsesBool(statement.type)
            is CIf -> statementUsesBool(statement.thenBranch) || statement.elseBranch?.let(::statementUsesBool) == true
            is CWhile -> statementUsesBool(statement.body)
            is CFor -> (statement.initializer?.let(::statementUsesBool) == true) || statementUsesBool(statement.body)
            is CBreak, is CContinue -> false
        }
        return unit.structs.any { structure -> structure.fields.any { typeUsesBool(it.type) } } ||
            unit.unions.any { union -> union.fields.any { typeUsesBool(it.type) } } ||
            unit.globals.any { typeUsesBool(it.type) } ||
            unit.functions.any { function ->
                typeUsesBool(function.returnType) ||
                    function.parameters.any { typeUsesBool(it.type) } ||
                    function.body?.let(::statementUsesBool) == true
            }
    }

    private fun emitFunction(
        function: CFunction,
        appendLine: (String, Origin?) -> Unit,
        append: (String) -> Unit
    ) {
        appendLine("${function.returnType.render()} ${function.name}(${parameters(function.parameters, function.isVariadic)}) {", function.origin)
        when (val body = function.body) {
            null -> Unit
            is CBlock -> body.statements.forEach { emitStatement(it, 1, appendLine) }
            else -> emitStatement(body, 1, appendLine)
        }
        appendLine("}", function.origin)
    }

    private fun emitStatement(statement: CStatement, indentation: Int, appendLine: (String, Origin?) -> Unit) {
        val prefix = "    ".repeat(indentation)
        when (statement) {
            is CBlock -> {
                appendLine("${prefix}{", statement.origin)
                statement.statements.forEach { emitStatement(it, indentation + 1, appendLine) }
                appendLine("$prefix}", statement.origin)
            }
            is CReturn -> appendLine("$prefix${if (statement.expression == null) "return" else "return ${expression(statement.expression)}"};", statement.origin)
            is CExpressionStatement -> appendLine("$prefix${expression(statement.expression)};", statement.origin)
            is CVariableDeclaration -> {
                val initializer = statement.initializer?.let { " = ${expression(it)}" } ?: ""
                appendLine("$prefix${statement.type.renderDeclaration(statement.name)}${arraySuffix(statement.arrayDimensions)}$initializer;", statement.origin)
            }
            is CIf -> {
                appendLine("${prefix}if (${expression(statement.condition)})", statement.origin)
                emitStatement(statement.thenBranch, indentation, appendLine)
                statement.elseBranch?.let {
                    appendLine("${prefix}else", it.origin)
                    emitStatement(it, indentation, appendLine)
                }
            }
            is CWhile -> {
                appendLine("${prefix}while (${expression(statement.condition)})", statement.origin)
                emitStatement(statement.body, indentation, appendLine)
            }
            is CFor -> {
                val initializer = statement.initializer?.let(::forInitializer).orEmpty()
                val condition = statement.condition?.let(::expression).orEmpty()
                val increment = statement.increment?.let(::expression).orEmpty()
                appendLine("${prefix}for ($initializer; $condition; $increment)", statement.origin)
                emitStatement(statement.body, indentation, appendLine)
            }
            is CBreak -> appendLine("${prefix}break;", statement.origin)
            is CContinue -> appendLine("${prefix}continue;", statement.origin)
        }
    }

    private fun forInitializer(statement: CStatement): String = when (statement) {
        is CVariableDeclaration -> {
            val initializer = statement.initializer?.let { " = ${expression(it)}" }.orEmpty()
            "${statement.type.renderDeclaration(statement.name)}${arraySuffix(statement.arrayDimensions)}$initializer"
        }
        is CExpressionStatement -> expression(statement.expression)
        is CBlock -> statement.statements.joinToString(" ") { forInitializer(it) }
        else -> ""
    }

    private fun parameters(parameters: List<CParameter>, isVariadic: Boolean = false): String = buildList {
        addAll(parameters.map { "${it.type.renderDeclaration(it.name)}${arraySuffix(it.arrayDimensions)}" })
        if (isVariadic) add("...")
    }.joinToString(", ")

    private fun arraySuffix(dimensions: List<String>): String = dimensions.joinToString(separator = "") { "[$it]" }

    private fun expression(expression: CExpression): String = when (expression) {
        is CIntegerLiteral -> expression.text
        is CFloatLiteral -> expression.text
        is CStringLiteral -> expression.text
        is CCharacterLiteral -> expression.text
        is CIdentifier -> expression.name
        is CUnary -> "${expression.operator}${parenthesizeIfBinary(expression.operand)}"
        is CBinary -> "(${expression(expression.left)} ${expression.operator} ${expression(expression.right)})"
        is CAssignment -> "(${expression(expression.left)} ${expression.operator} ${expression(expression.right)})"
        is CConditional -> "(${expression(expression.condition)} ? ${expression(expression.thenBranch)} : ${expression(expression.elseBranch)})"
        is CUpdate -> if (expression.prefix) {
            "${expression.operator}${expression(expression.operand)}"
        } else {
            "${expression(expression.operand)}${expression.operator}"
        }
        is CSizeOf -> expression.targetType?.let { "sizeof(${it.render()})" }
            ?: "sizeof(${expression(expression.operand!!)})"
        is CAbiQuery -> when (expression.query) {
            "alignof" -> expression.targetType?.let { "_Alignof(${it.render()})" }
                ?: "_Alignof(${expression(expression.operand!!)})"
            "offsetof" -> "offsetof(${expression.targetType?.render() ?: "int"}, ${expression.fieldName ?: "__invalid_field"})"
            // The compiler API exposes the complete AbiLayout; this scalar
            // view keeps layoutof usable in ordinary C expressions.
            "layoutof" -> "sizeof(${expression.targetType?.render() ?: "int"})"
            else -> "sizeof(${expression.targetType?.render() ?: "int"})"
        }
        is CCast -> "(${expression.target.render()})${expression(expression.operand)}"
        is CCall -> "${expression(expression.callee)}(${expression.arguments.joinToString(", ") { argument -> expression(argument) }})"
        is CMemberAccess -> "${expression(expression.receiver)}${if (expression.pointerReceiver) "->" else "."}${expression.member}"
        is CIndexAccess -> "${expression(expression.receiver)}[${expression(expression.index)}]"
        is CParenthesized -> "(${expression(expression.expression)})"
    }

    private fun parenthesizeIfBinary(expression: CExpression): String = when (expression) {
        is CBinary -> "(${expression(expression)})"
        else -> expression(expression)
    }
}
