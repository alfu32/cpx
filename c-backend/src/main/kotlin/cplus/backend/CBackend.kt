package cplus.backend

import cplus.core.*
import cplus.semantic.SemanticModel

data class LoweredCResult(
    val unit: CTranslationUnit,
    val diagnostics: List<Diagnostic>
)

class CLowerer(private val semantic: SemanticModel) {
    private val diagnostics = DiagnosticBag()

    fun lower(program: AstProgram): LoweredCResult {
        val includes = program.declarations
            .filterIsInstance<AstImport>()
            .flatMap { import ->
                when (import.module) {
                    "c.stdio" -> listOf("stdio.h")
                    "c.stddef" -> listOf("stddef.h")
                    "c.stdlib" -> listOf("stdlib.h")
                    "c.math" -> listOf("math.h")
                    else -> emptyList()
                }
            }
            .distinct()
            .sorted()
        val structs = program.declarations.filterIsInstance<AstStruct>().map { declaration ->
            CStructDeclaration(
                declaration.name,
                declaration.fields.map { field ->
                    CField(type(field.type), field.name, field.origin, field.arrayDimensions)
                },
                declaration.origin
            )
        }
        val unions = program.declarations.filterIsInstance<AstUnion>().map { declaration ->
            CUnionDeclaration(
                declaration.name,
                declaration.fields.map { field -> CField(type(field.type), field.name, field.origin, field.arrayDimensions) },
                declaration.origin
            )
        }
        val enums = program.declarations.filterIsInstance<AstEnum>().map { declaration ->
            CEnumDeclaration(
                declaration.name,
                declaration.values.map { value -> CEnumValue(value.name, value.value, value.origin) },
                declaration.origin
            )
        }
        val aliases = program.declarations.filterIsInstance<AstAlias>().map { declaration ->
            CAliasDeclaration(
                declaration.name,
                type(declaration.target),
                declaration.arrayDimensions,
                declaration.origin
            )
        }
        val globals = program.declarations.filterIsInstance<AstGlobalVariable>().map { declaration ->
            CGlobalDeclaration(
                type(declaration.type),
                declaration.name,
                declaration.initializer?.let(::expression),
                declaration.origin,
                declaration.arrayDimensions
            )
        }
        val functions = program.declarations.filterIsInstance<AstFunction>().map { declaration ->
            CFunction(
                type(declaration.returnType),
                declaration.name,
                declaration.parameters.map { CParameter(type(it.type), it.name, it.origin, it.arrayDimensions) },
                declaration.body?.let { lowerBody(it, declaration.ownerName, declaration.isMethod) },
                declaration.origin
            )
        } + program.declarations.filterIsInstance<AstStruct>().flatMap { structure ->
            structure.methods.map { method -> lowerMethod(structure.name, method) }
        }
        return LoweredCResult(CTranslationUnit(includes, structs, unions, enums, aliases, globals, functions), diagnostics.diagnostics)
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
            "${ownerName}__${method.name}",
            parameters,
            method.body?.let { lowerBody(it, ownerName, instance) },
            method.origin
        )
    }

    private fun type(reference: AstTypeRef): CType {
        val result = when {
            semantic.aliases.containsKey(reference.name) -> CType.Named(reference.name, reference.pointerDepth)
            semantic.foreignTypes.containsKey(reference.name) -> CType.Named(reference.name, reference.pointerDepth)
            reference.declarationKind == "union" || semantic.unions.containsKey(reference.name) -> {
                CType.Union(reference.name, reference.pointerDepth)
            }
            reference.declarationKind == "enum" || semantic.enums.containsKey(reference.name) -> {
                CType.Enum(reference.name, reference.pointerDepth)
            }
            reference.isStruct || semantic.structs.containsKey(reference.name) -> {
                CType.Struct(reference.name, reference.pointerDepth)
            }
            reference.name in primitiveNames -> CType.Primitive(reference.name, reference.pointerDepth)
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
        is AstStringLiteral -> CStringLiteral(node.text, node.origin)
        is AstCharacterLiteral -> CCharacterLiteral(node.text, node.origin)
        is AstIdentifier -> CIdentifier(node.name, node.origin)
        is AstUnary -> CUnary(node.operator, expression(node.operand, ownerName, instanceMethod), node.origin)
        is AstBinary -> CBinary(
            expression(node.left, ownerName, instanceMethod),
            node.operator,
            expression(node.right, ownerName, instanceMethod),
            node.origin
        )
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
        is AstParenthesized -> CParenthesized(expression(node.expression, ownerName, instanceMethod), node.origin)
        is AstErrorExpression -> CIntegerLiteral("0", node.origin)
    }

    private fun lowerCall(node: AstCall, ownerName: String?, instanceMethod: Boolean): CExpression {
        val member = node.callee as? AstMemberAccess
        if (member == null) {
            return CCall(
                expression(node.callee, ownerName, instanceMethod),
                node.arguments.map { expression(it, ownerName, instanceMethod) },
                node.origin
            )
        }

        val qualifiedName = (member.receiver as? AstIdentifier)?.let { receiver ->
            "${receiver.name}.${member.member}"
        }
        if (qualifiedName != null && qualifiedName in semantic.qualifiedFunctionNames) {
            return CCall(
                CIdentifier(member.member, node.origin),
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
        val target = CIdentifier("${owner.name}__${member.member}", node.origin)
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
            "void", "bool", "char", "short", "int", "long", "float", "double", "signed", "unsigned"
        )
    }
}

class CEmitter {
    fun emit(unit: CTranslationUnit): GeneratedCUnit {
        val output = StringBuilder()
        val mappings = mutableListOf<SourceMapping>()
        var line = 1

        fun append(text: String) {
            output.append(text)
            line += text.count { it == '\n' }
        }

        fun appendLine(text: String = "", origin: Origin? = null) {
            if (origin != null) mappings += SourceMapping(line, origin)
            append(text)
            append("\n")
        }

        if (usesBool(unit)) {
            appendLine("#include <stdbool.h>")
        }
        unit.includes.sorted().forEach { include -> appendLine("#include <$include>") }
        if (usesBool(unit) || unit.includes.isNotEmpty()) appendLine()

        unit.structs.forEachIndexed { index, structure ->
            appendLine("struct ${structure.name} {", structure.origin)
            structure.fields.forEach { field ->
                appendLine("    ${field.type.render()} ${field.name}${arraySuffix(field.arrayDimensions)};", field.origin)
            }
            appendLine("};", structure.origin)
            if (index != unit.structs.lastIndex || unit.unions.isNotEmpty() || unit.enums.isNotEmpty() || unit.globals.isNotEmpty() || unit.functions.isNotEmpty()) appendLine()
        }

        unit.unions.forEachIndexed { index, union ->
            appendLine("union ${union.name} {", union.origin)
            union.fields.forEach { field ->
                appendLine("    ${field.type.render()} ${field.name}${arraySuffix(field.arrayDimensions)};", field.origin)
            }
            appendLine("};", union.origin)
            if (index != unit.unions.lastIndex || unit.enums.isNotEmpty() || unit.globals.isNotEmpty() || unit.functions.isNotEmpty()) appendLine()
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
            appendLine("typedef ${alias.target.render()} ${alias.name}${arraySuffix(alias.arrayDimensions)};", alias.origin)
        }
        if (unit.aliases.isNotEmpty() && (unit.globals.isNotEmpty() || unit.functions.isNotEmpty())) appendLine()

        unit.globals.forEach { global ->
            val initializer = global.initializer?.let { " = ${expression(it)}" } ?: ""
            appendLine("${global.type.render()} ${global.name}${arraySuffix(global.arrayDimensions)}$initializer;", global.origin)
        }
        if (unit.globals.isNotEmpty() && unit.functions.isNotEmpty()) appendLine()

        unit.functions.forEach { function ->
            appendLine("${function.returnType.render()} ${function.name}(${parameters(function.parameters)});", function.origin)
        }
        if (unit.functions.isNotEmpty()) appendLine()
        unit.functions.forEachIndexed { index, function ->
            emitFunction(function, ::appendLine, ::append)
            if (index != unit.functions.lastIndex) appendLine()
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
        appendLine("${function.returnType.render()} ${function.name}(${parameters(function.parameters)}) {", function.origin)
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
                appendLine("$prefix${statement.type.render()} ${statement.name}${arraySuffix(statement.arrayDimensions)}$initializer;", statement.origin)
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
            "${statement.type.render()} ${statement.name}${arraySuffix(statement.arrayDimensions)}$initializer"
        }
        is CExpressionStatement -> expression(statement.expression)
        is CBlock -> statement.statements.joinToString(" ") { forInitializer(it) }
        else -> ""
    }

    private fun parameters(parameters: List<CParameter>): String = parameters.joinToString(", ") {
        "${it.type.render()} ${it.name}${arraySuffix(it.arrayDimensions)}"
    }

    private fun arraySuffix(dimensions: List<String>): String = dimensions.joinToString(separator = "") { "[$it]" }

    private fun expression(expression: CExpression): String = when (expression) {
        is CIntegerLiteral -> expression.text
        is CStringLiteral -> expression.text
        is CCharacterLiteral -> expression.text
        is CIdentifier -> expression.name
        is CUnary -> "${expression.operator}${parenthesizeIfBinary(expression.operand)}"
        is CBinary -> "(${expression(expression.left)} ${expression.operator} ${expression(expression.right)})"
        is CCall -> "${expression(expression.callee)}(${expression.arguments.joinToString(", ") { argument -> expression(argument) }})"
        is CMemberAccess -> "${expression(expression.receiver)}${if (expression.pointerReceiver) "->" else "."}${expression.member}"
        is CParenthesized -> "(${expression(expression.expression)})"
    }

    private fun parenthesizeIfBinary(expression: CExpression): String = when (expression) {
        is CBinary -> "(${expression(expression)})"
        else -> expression(expression)
    }
}
