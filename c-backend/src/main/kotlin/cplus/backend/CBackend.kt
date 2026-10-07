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
        val structs = program.declarations.filterIsInstance<AstStruct>().map { declaration ->
            CStructDeclaration(
                declaration.name,
                declaration.fields.map { field ->
                    CField(type(field.type), field.name, field.origin)
                },
                declaration.origin
            )
        }
        val globals = program.declarations.filterIsInstance<AstGlobalVariable>().map { declaration ->
            CGlobalDeclaration(type(declaration.type), declaration.name, declaration.initializer?.let(::expression), declaration.origin)
        }
        val functions = program.declarations.filterIsInstance<AstFunction>().map { declaration ->
            CFunction(
                type(declaration.returnType),
                declaration.name,
                declaration.parameters.map { CParameter(type(it.type), it.name, it.origin) },
                declaration.body?.let { statement(it, declaration.ownerName, declaration.isMethod) },
                declaration.origin
            )
        } + program.declarations.filterIsInstance<AstStruct>().flatMap { structure ->
            structure.methods.map { method -> lowerMethod(structure.name, method) }
        }
        return LoweredCResult(CTranslationUnit(structs, globals, functions), diagnostics.diagnostics)
    }

    private fun lowerMethod(ownerName: String, method: AstFunction): CFunction {
        val instance = method.parameters.any { it.isReceiver }
        val parameters = buildList {
            if (instance) add(CParameter(CType.Struct(ownerName, pointerDepth = 1), "self", method.origin))
            method.parameters.filterNot { it.isReceiver }.forEach {
                add(CParameter(type(it.type), it.name, it.origin))
            }
        }
        return CFunction(
            type(method.returnType),
            "${ownerName}__${method.name}",
            parameters,
            method.body?.let { statement(it, ownerName, instance) },
            method.origin
        )
    }

    private fun type(reference: AstTypeRef): CType {
        val result = if (reference.isStruct || semantic.structs.containsKey(reference.name)) {
            CType.Struct(reference.name, reference.pointerDepth)
        } else if (reference.name in primitiveNames) {
            CType.Primitive(reference.name, reference.pointerDepth)
        } else {
            diagnostics.error("cannot lower unknown type '${reference.name}'", reference.origin.primaryRange, "LOW101")
            CType.Unknown
        }
        return result
    }

    private fun statement(node: AstStatement, ownerName: String? = null, instanceMethod: Boolean = false): CStatement = when (node) {
        is AstBlock -> CBlock(node.statements.map { statement(it, ownerName, instanceMethod) }, node.origin)
        is AstReturn -> CReturn(node.expression?.let { expression(it, ownerName, instanceMethod) }, node.origin)
        is AstExpressionStatement -> CExpressionStatement(expression(node.expression, ownerName, instanceMethod), node.origin)
        is AstVariableDeclaration -> CVariableDeclaration(
            type(node.type),
            node.name,
            node.initializer?.let { expression(it, ownerName, instanceMethod) },
            node.origin
        )
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
                pointerReceiver = instanceMethod && receiver is AstIdentifier && receiver.name == "self",
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

        val receiverType = semantic.expressionTypes[member.receiver]
        val owner = when (val receiver = member.receiver) {
            is AstIdentifier -> semantic.structs[receiver.name] ?: receiverType as? cplus.semantic.StructType
            else -> receiverType as? cplus.semantic.StructType
        }
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
                } else {
                    add(CUnary("&", expression(receiver, ownerName, instanceMethod), receiver.origin))
                }
            }
            node.arguments.forEach { add(expression(it, ownerName, instanceMethod)) }
        }
        return CCall(target, arguments, node.origin)
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
            appendLine()
        }

        unit.structs.forEachIndexed { index, structure ->
            appendLine("struct ${structure.name} {", structure.origin)
            structure.fields.forEach { field ->
                appendLine("    ${field.type.render()} ${field.name};", field.origin)
            }
            appendLine("};", structure.origin)
            if (index != unit.structs.lastIndex || unit.globals.isNotEmpty() || unit.functions.isNotEmpty()) appendLine()
        }

        unit.globals.forEach { global ->
            val initializer = global.initializer?.let { " = ${expression(it)}" } ?: ""
            appendLine("${global.type.render()} ${global.name}$initializer;", global.origin)
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
        }
        return unit.structs.any { structure -> structure.fields.any { typeUsesBool(it.type) } } ||
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
                appendLine("$prefix${statement.type.render()} ${statement.name}$initializer;", statement.origin)
            }
        }
    }

    private fun parameters(parameters: List<CParameter>): String = parameters.joinToString(", ") {
        "${it.type.render()} ${it.name}"
    }

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
