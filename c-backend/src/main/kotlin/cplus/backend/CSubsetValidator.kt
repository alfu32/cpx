package cplus.backend

import cplus.core.Diagnostic
import cplus.core.DiagnosticBag
import cplus.core.Origin
import cplus.semantic.*

class CSubsetValidator {
    fun validate(unit: CTranslationUnit): List<Diagnostic> {
        val diagnostics = DiagnosticBag()
        val aggregates = if (unit.aggregateDeclarations.isNotEmpty()) {
            unit.aggregateDeclarations
        } else {
            buildList<CAggregateDeclaration> {
                addAll(unit.structs)
                addAll(unit.unions)
            }
        }
        aggregates.forEach { aggregate ->
            aggregate.fields.forEach { field ->
                validateType(field.type, field.origin, diagnostics)
                validateIdentifier(field.name, field.origin, diagnostics)
            }
        }
        unit.forwardDeclarations.forEach { declaration ->
            validateIdentifier(declaration.name, declaration.origin, diagnostics)
        }
        unit.enums.forEach { enumeration ->
            validateIdentifier(enumeration.name, enumeration.origin, diagnostics)
            enumeration.values.forEach { value ->
                validateIdentifier(value.name, value.origin, diagnostics)
            }
        }
        unit.aliases.forEach { alias ->
            validateType(alias.target, alias.origin, diagnostics, allowVoid = true)
            validateIdentifier(alias.name, alias.origin, diagnostics)
        }
        unit.globals.forEach { global ->
            validateType(global.type, global.origin, diagnostics)
            validateIdentifier(global.name, global.origin, diagnostics, allowReserved = global.isExtern)
            global.initializer?.let { validateExpression(it, diagnostics) }
            if (global.isExtern && global.initializer != null) {
                diagnostics.error("extern global '${global.name}' cannot have an initializer", global.origin.primaryRange, "LOW403")
            }
        }
        unit.functions.forEach { function ->
            validateType(function.returnType, function.origin, diagnostics, allowVoid = true)
            validateIdentifier(function.name, function.origin, diagnostics, allowReserved = function.body == null)
            function.parameters.forEach { parameter ->
                validateType(parameter.type, parameter.origin, diagnostics)
                validateIdentifier(parameter.name, parameter.origin, diagnostics)
            }
            function.body?.let { validateStatement(it, diagnostics) }
        }
        validateGeneratedNamespaces(unit, diagnostics)
        return diagnostics.diagnostics
    }

    private fun validateGeneratedNamespaces(unit: CTranslationUnit, diagnostics: DiagnosticBag) {
        val generatedNames = buildSet {
            if (unit.requiresStringTemplateRuntime) add("__cplus_format")
        }
        if (generatedNames.isEmpty()) return

        val declaredNames = buildList {
            addAll(unit.functions.map { it.name })
            addAll(unit.globals.map { it.name })
            addAll(unit.aliases.map { it.name })
            addAll(unit.enums.flatMap { enumeration -> enumeration.values.map { it.name } })
        }
        declaredNames.filter { it in generatedNames }.distinct().forEach { name ->
            diagnostics.error(
                "user declaration collides with generated C helper '$name'",
                unit.functions.firstOrNull { it.name == name }?.origin?.primaryRange
                    ?: unit.globals.firstOrNull { it.name == name }?.origin?.primaryRange
                    ?: unit.aliases.firstOrNull { it.name == name }?.origin?.primaryRange
                    ?: unit.enums.flatMap { it.values }.firstOrNull { it.name == name }?.origin?.primaryRange,
                "LOW407"
            )
        }
    }

    private fun validateType(type: CType, origin: Origin, diagnostics: DiagnosticBag, allowVoid: Boolean = false) {
        when (type) {
            CType.Unknown -> diagnostics.error("unknown C type reached backend validation", origin.primaryRange, "LOW402")
            is CType.Primitive -> if (type.name == "void" && type.pointerDepth == 0 && !allowVoid) {
                diagnostics.error("void is not a valid object type", origin.primaryRange, "LOW402")
            }
            is CType.Named -> validateIdentifier(type.name, origin, diagnostics)
            is CType.Struct -> validateIdentifier(type.name, origin, diagnostics)
            is CType.Union -> validateIdentifier(type.name, origin, diagnostics)
            is CType.Enum -> validateIdentifier(type.name, origin, diagnostics)
        }
    }

    private fun validateIdentifier(
        name: String,
        origin: Origin,
        diagnostics: DiagnosticBag,
        allowReserved: Boolean = false
    ) {
        if (!identifierPattern.matches(name)) {
            diagnostics.error("invalid C identifier '$name' reached backend validation", origin.primaryRange, "LOW402")
        } else if (!allowReserved && name !in cKeywords && !name.startsWith("__cplus_") && reservedIdentifierPattern.matches(name)) {
            diagnostics.error("reserved C identifier '$name' reached backend validation", origin.primaryRange, "LOW406")
        } else if (!allowReserved && name in cKeywords && !name.startsWith("__cplus_")) {
            diagnostics.error("C keyword '$name' cannot be emitted as an identifier", origin.primaryRange, "LOW406")
        }
    }

    private fun validateStatement(statement: CStatement, diagnostics: DiagnosticBag) {
        when (statement) {
            is CBlock -> statement.statements.forEach { validateStatement(it, diagnostics) }
            is CReturn -> statement.expression?.let { validateExpression(it, diagnostics) }
            is CExpressionStatement -> validateExpression(statement.expression, diagnostics)
            is CVariableDeclaration -> {
                validateType(statement.type, statement.origin, diagnostics)
                validateIdentifier(statement.name, statement.origin, diagnostics)
                statement.initializer?.let { validateExpression(it, diagnostics) }
            }
            is CIf -> {
                validateExpression(statement.condition, diagnostics)
                validateStatement(statement.thenBranch, diagnostics)
                statement.elseBranch?.let { validateStatement(it, diagnostics) }
            }
            is CWhile -> {
                validateExpression(statement.condition, diagnostics)
                validateStatement(statement.body, diagnostics)
            }
            is CFor -> {
                statement.initializer?.let { validateStatement(it, diagnostics) }
                statement.condition?.let { validateExpression(it, diagnostics) }
                statement.increment?.let { validateExpression(it, diagnostics) }
                validateStatement(statement.body, diagnostics)
            }
            is CBreak, is CContinue -> Unit
        }
    }

    private fun validateExpression(expression: CExpression, diagnostics: DiagnosticBag) {
        when (expression) {
            is CIntegerLiteral, is CFloatLiteral, is CStringLiteral, is CCharacterLiteral -> Unit
            is CIdentifier -> validateIdentifier(
                expression.name,
                expression.origin,
                diagnostics,
                allowReserved = expression.name.startsWith("__cplus_")
            )
            is CUnary -> {
                if (expression.operator !in unaryOperators) {
                    diagnostics.error("unsupported C unary operator '${expression.operator}'", expression.origin.primaryRange, "LOW404")
                }
                validateExpression(expression.operand, diagnostics)
            }
            is CBinary -> {
                if (expression.operator !in binaryOperators) {
                    diagnostics.error("unsupported C binary operator '${expression.operator}'", expression.origin.primaryRange, "LOW404")
                }
                validateExpression(expression.left, diagnostics)
                validateExpression(expression.right, diagnostics)
            }
            is CAssignment -> {
                if (expression.operator !in assignmentOperators) {
                    diagnostics.error("unsupported C assignment operator '${expression.operator}'", expression.origin.primaryRange, "LOW404")
                }
                validateExpression(expression.left, diagnostics)
                validateExpression(expression.right, diagnostics)
            }
            is CConditional -> {
                validateExpression(expression.condition, diagnostics)
                validateExpression(expression.thenBranch, diagnostics)
                validateExpression(expression.elseBranch, diagnostics)
            }
            is CUpdate -> {
                if (expression.operator !in updateOperators) {
                    diagnostics.error("unsupported C update operator '${expression.operator}'", expression.origin.primaryRange, "LOW404")
                }
                validateExpression(expression.operand, diagnostics)
            }
            is CSizeOf -> {
                expression.targetType?.let { validateType(it, expression.origin, diagnostics) }
                expression.operand?.let { validateExpression(it, diagnostics) }
                if (expression.targetType == null && expression.operand == null) {
                    diagnostics.error("sizeof requires an operand or type", expression.origin.primaryRange, "LOW405")
                }
            }
            is CCast -> {
                validateType(expression.target, expression.origin, diagnostics)
                validateExpression(expression.operand, diagnostics)
            }
            is CCall -> {
                validateExpression(expression.callee, diagnostics)
                expression.arguments.forEach { validateExpression(it, diagnostics) }
            }
            is CMemberAccess -> {
                validateExpression(expression.receiver, diagnostics)
                validateIdentifier(expression.member, expression.origin, diagnostics)
            }
            is CIndexAccess -> {
                validateExpression(expression.receiver, diagnostics)
                validateExpression(expression.index, diagnostics)
            }
            is CParenthesized -> validateExpression(expression.expression, diagnostics)
        }
    }

    companion object {
        private val identifierPattern = Regex("[A-Za-z_][A-Za-z0-9_]*")
        private val reservedIdentifierPattern = Regex("(?:__|_[A-Z]).*")
        private val cKeywords = setOf(
            "auto", "break", "case", "char", "const", "continue", "default", "do", "double",
            "else", "enum", "extern", "float", "for", "goto", "if", "inline", "int", "long",
            "register", "restrict", "return", "short", "signed", "sizeof", "static", "struct",
            "switch", "typedef", "union", "unsigned", "void", "volatile", "while",
            "_Alignas", "_Alignof", "_Atomic", "_Bool", "_Complex", "_Generic", "_Imaginary",
            "_Noreturn", "_Static_assert", "_Thread_local"
        )
        private val unaryOperators = setOf("-", "!", "~", "&", "*")
        private val binaryOperators = setOf(
            "+", "-", "*", "/", "%", "|", "^", "&", "<<", ">>",
            "==", "!=", "<", "<=", ">", ">=", "&&", "||"
        )
        private val assignmentOperators = setOf("=", "+=", "-=", "*=", "/=", "%=")
        private val updateOperators = setOf("++", "--")
    }
}
