package cplus.semantic

import cplus.core.*

@JvmInline
value class SymbolId(val value: Int)

@JvmInline
value class TypeId(val value: Int)

enum class SymbolKind {
    STRUCT,
    FIELD,
    FUNCTION,
    VARIABLE,
    PARAMETER
}

enum class Visibility {
    PRIVATE,
    PUBLIC
}

sealed interface CType {
    val id: TypeId
    val name: String
}

data class PrimitiveType(
    override val id: TypeId,
    override val name: String
) : CType

data class StructType(
    override val id: TypeId,
    override val name: String,
    val fields: List<FieldSymbol>
) : CType

data class PointerType(
    override val id: TypeId,
    val pointee: CType
) : CType {
    override val name: String = "${pointee.name}*"
}

data class UnknownType(
    override val id: TypeId,
    override val name: String = "<unknown>"
) : CType

data class Symbol(
    val id: SymbolId,
    val name: String,
    val kind: SymbolKind,
    val type: CType,
    val origin: Origin,
    val visibility: Visibility = Visibility.PRIVATE
)

data class FieldSymbol(
    val symbol: Symbol,
    val owner: StructType
)

data class FunctionSymbol(
    val symbol: Symbol,
    val returnType: CType,
    val parameters: List<Symbol>
)

data class SemanticModel(
    val program: AstProgram,
    val symbols: List<Symbol>,
    val types: List<CType>,
    val functions: Map<String, FunctionSymbol>,
    val structs: Map<String, StructType>,
    val expressionTypes: Map<AstExpression, CType>
) {
    fun symbolNamed(name: String): Symbol? = symbols.firstOrNull { it.name == name }
}

data class SemanticResult(
    val model: SemanticModel?,
    val diagnostics: List<Diagnostic>
) {
    val isSuccessful: Boolean
        get() = model != null && diagnostics.none { it.severity == DiagnosticSeverity.ERROR }
}

class SemanticAnalyzer {
    private val nextSymbolId = generateSequence(1) { it + 1 }.iterator()
    private val nextTypeId = generateSequence(1) { it + 1 }.iterator()

    fun analyze(program: AstProgram): SemanticResult {
        val diagnostics = DiagnosticBag()
        val symbols = mutableListOf<Symbol>()
        val types = mutableListOf<CType>()
        val structs = linkedMapOf<String, StructType>()
        val functions = linkedMapOf<String, FunctionSymbol>()
        val globals = linkedMapOf<String, Symbol>()
        val primitiveTypes = linkedMapOf<String, PrimitiveType>()
        val expressionTypes = linkedMapOf<AstExpression, CType>()

        fun primitive(name: String): PrimitiveType = primitiveTypes.getOrPut(name) {
            PrimitiveType(TypeId(nextTypeId.next()), name).also(types::add)
        }

        fun newSymbol(name: String, kind: SymbolKind, type: CType, origin: Origin): Symbol = Symbol(
            SymbolId(nextSymbolId.next()), name, kind, type, origin
        ).also(symbols::add)

        program.declarations.forEach { declaration ->
            when (declaration) {
                is AstStruct -> {
                    if (structs.containsKey(declaration.name)) {
                        diagnostics.error("duplicate structure '${declaration.name}'", rangeOf(declaration.origin), "SEM001")
                    } else {
                        val type = StructType(TypeId(nextTypeId.next()), declaration.name, emptyList())
                        structs[declaration.name] = type
                        types += type
                        newSymbol(declaration.name, SymbolKind.STRUCT, type, declaration.origin)
                    }
                }
                is AstFunction -> {
                    if (functions.containsKey(declaration.name)) {
                        diagnostics.error("duplicate function '${declaration.name}'", rangeOf(declaration.origin), "SEM002")
                    } else {
                        val returnType = resolveType(declaration.returnType, structs, ::primitive, diagnostics)
                        val parameterSymbols = declaration.parameters.map { parameter ->
                            val type = resolveType(parameter.type, structs, ::primitive, diagnostics)
                            newSymbol(parameter.name, SymbolKind.PARAMETER, type, parameter.origin)
                        }
                        val functionSymbol = newSymbol(declaration.name, SymbolKind.FUNCTION, returnType, declaration.origin)
                        functions[declaration.name] = FunctionSymbol(functionSymbol, returnType, parameterSymbols)
                    }
                }
                is AstGlobalVariable -> {
                    if (globals.containsKey(declaration.name)) {
                        diagnostics.error("duplicate global '${declaration.name}'", rangeOf(declaration.origin), "SEM003")
                    } else {
                        val type = resolveType(declaration.type, structs, ::primitive, diagnostics)
                        globals[declaration.name] = newSymbol(declaration.name, SymbolKind.VARIABLE, type, declaration.origin)
                    }
                }
            }
        }

        program.declarations.filterIsInstance<AstStruct>().forEach { declaration ->
            val struct = structs[declaration.name] ?: return@forEach
            val fields = declaration.fields.map { field ->
                val type = resolveType(field.type, structs, ::primitive, diagnostics)
                val symbol = newSymbol(field.name, SymbolKind.FIELD, type, field.origin)
                FieldSymbol(symbol, struct)
            }
            structs[declaration.name] = struct.copy(fields = fields)
        }

        program.declarations.filterIsInstance<AstFunction>().forEach { declaration ->
            val function = functions[declaration.name] ?: return@forEach
            val locals = linkedMapOf<String, Symbol>()
            function.parameters.forEach { locals[it.name] = it }
            declaration.body?.let { statement ->
                validateStatement(statement, function.returnType, locals, functions, globals, structs, expressionTypes, diagnostics, ::primitive)
            }
        }

        val model = SemanticModel(program, symbols, types, functions, structs, expressionTypes)
        return SemanticResult(model, diagnostics.diagnostics)
    }

    private fun resolveType(
        reference: AstTypeRef,
        structs: Map<String, StructType>,
        primitive: (String) -> PrimitiveType,
        diagnostics: DiagnosticBag
    ): CType {
        val base = if (reference.isStruct || reference.name in knownPrimitiveNames) {
            if (reference.isStruct) {
                structs[reference.name] ?: run {
                    diagnostics.error("unknown structure type '${reference.name}'", rangeOf(reference.origin), "SEM101")
                    UnknownType(TypeId(-1))
                }
            } else {
                primitive(reference.name)
            }
        } else {
            structs[reference.name] ?: run {
                diagnostics.error("unknown type '${reference.name}'", rangeOf(reference.origin), "SEM102")
                UnknownType(TypeId(-1))
            }
        }
        var resolved = base
        repeat(reference.pointerDepth) {
            resolved = PointerType(TypeId(-1 - it), resolved)
        }
        return resolved
    }

    private fun validateStatement(
        statement: AstStatement,
        expectedReturn: CType,
        locals: MutableMap<String, Symbol>,
        functions: Map<String, FunctionSymbol>,
        globals: Map<String, Symbol>,
        structs: Map<String, StructType>,
        expressionTypes: MutableMap<AstExpression, CType>,
        diagnostics: DiagnosticBag,
        primitive: (String) -> PrimitiveType
    ) {
        when (statement) {
            is AstBlock -> statement.statements.forEach {
                validateStatement(it, expectedReturn, locals, functions, globals, structs, expressionTypes, diagnostics, primitive)
            }
            is AstReturn -> {
                val returnExpression = statement.expression
                if (returnExpression == null) {
                    if (expectedReturn.name != "void") {
                        diagnostics.error("non-void function must return a value", rangeOf(statement.origin), "SEM201")
                    }
                } else {
                    val actual = validateExpression(returnExpression, locals, functions, globals, structs, expressionTypes, diagnostics, primitive)
                    if (expectedReturn.name == "void") {
                        diagnostics.error("void function cannot return a value", rangeOf(statement.origin), "SEM202")
                    } else if (actual.name != "<unknown>" && expectedReturn.name != actual.name) {
                        diagnostics.error(
                            "return type '${actual.name}' does not match '${expectedReturn.name}'",
                            rangeOf(statement.origin),
                            "SEM203"
                        )
                    }
                }
            }
            is AstExpressionStatement -> validateExpression(statement.expression, locals, functions, globals, structs, expressionTypes, diagnostics, primitive)
            is AstVariableDeclaration -> {
                val type = resolveType(statement.type, structs, primitive, diagnostics)
                val symbol = Symbol(SymbolId(-locals.size - 1), statement.name, SymbolKind.VARIABLE, type, statement.origin)
                if (locals.containsKey(statement.name)) {
                    diagnostics.error("duplicate local '${statement.name}'", rangeOf(statement.origin), "SEM204")
                } else {
                    locals[statement.name] = symbol
                }
                statement.initializer?.let {
                    validateExpression(it, locals, functions, globals, structs, expressionTypes, diagnostics, primitive)
                }
            }
        }
    }

    private fun validateExpression(
        expression: AstExpression,
        locals: Map<String, Symbol>,
        functions: Map<String, FunctionSymbol>,
        globals: Map<String, Symbol>,
        structs: Map<String, StructType>,
        expressionTypes: MutableMap<AstExpression, CType>,
        diagnostics: DiagnosticBag,
        primitive: (String) -> PrimitiveType
    ): CType {
        val type = when (expression) {
            is AstIntegerLiteral -> primitive("int")
            is AstStringLiteral -> PointerType(TypeId(-1), primitive("char"))
            is AstCharacterLiteral -> primitive("char")
            is AstIdentifier -> {
                locals[expression.name]?.type
                    ?: globals[expression.name]?.type
                    ?: functions[expression.name]?.returnType
                    ?: run {
                        diagnostics.error("unknown identifier '${expression.name}'", rangeOf(expression.origin), "SEM301")
                        UnknownType(TypeId(-1))
                    }
            }
            is AstUnary -> validateExpression(expression.operand, locals, functions, globals, structs, expressionTypes, diagnostics, primitive)
            is AstBinary -> {
                val left = validateExpression(expression.left, locals, functions, globals, structs, expressionTypes, diagnostics, primitive)
                validateExpression(expression.right, locals, functions, globals, structs, expressionTypes, diagnostics, primitive)
                left
            }
            is AstCall -> {
                val function = (expression.callee as? AstIdentifier)?.let { functions[it.name] }
                if (function == null) {
                    diagnostics.error("call target is not a known function", rangeOf(expression.callee.origin), "SEM302")
                    expression.arguments.forEach {
                        validateExpression(it, locals, functions, globals, structs, expressionTypes, diagnostics, primitive)
                    }
                    UnknownType(TypeId(-1))
                } else {
                    if (function.parameters.size != expression.arguments.size) {
                        diagnostics.error(
                            "function '${function.symbol.name}' expects ${function.parameters.size} arguments but received ${expression.arguments.size}",
                            rangeOf(expression.origin),
                            "SEM303"
                        )
                    }
                    expression.arguments.forEach {
                        validateExpression(it, locals, functions, globals, structs, expressionTypes, diagnostics, primitive)
                    }
                    function.returnType
                }
            }
            is AstMemberAccess -> {
                validateExpression(expression.receiver, locals, functions, globals, structs, expressionTypes, diagnostics, primitive)
            }
            is AstParenthesized -> validateExpression(expression.expression, locals, functions, globals, structs, expressionTypes, diagnostics, primitive)
            is AstErrorExpression -> UnknownType(TypeId(-1))
        }
        expressionTypes[expression] = type
        return type
    }

    private fun rangeOf(origin: Origin): SourceRange? = origin.primaryRange

    companion object {
        private val knownPrimitiveNames = setOf(
            "void", "bool", "char", "short", "int", "long", "float", "double", "signed", "unsigned"
        )
    }
}
