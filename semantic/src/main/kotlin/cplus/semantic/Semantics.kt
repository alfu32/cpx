package cplus.semantic

import cplus.core.*
import java.util.IdentityHashMap

@JvmInline
value class SymbolId(val value: Int)

@JvmInline
value class TypeId(val value: Int)

@JvmInline
value class QualifiedName(val value: String)

enum class SymbolKind {
    STRUCT,
    UNION,
    ENUM,
    ALIAS,
    FOREIGN_TYPE,
    FOREIGN_ENUM_VALUE,
    ENUM_VALUE,
    FIELD,
    FUNCTION,
    METHOD,
    FOREIGN,
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
    val fields: List<FieldSymbol>,
    val methods: List<MethodSymbol> = emptyList()
) : CType

data class UnionType(
    override val id: TypeId,
    override val name: String,
    val fields: List<FieldSymbol>
) : CType

data class EnumType(
    override val id: TypeId,
    override val name: String,
    val values: List<String>
) : CType

data class PointerType(
    override val id: TypeId,
    val pointee: CType
) : CType {
    override val name: String = "${pointee.name}*"
}

data class ArrayType(
    override val id: TypeId,
    val element: CType,
    val dimensions: List<String>
) : CType {
    override val name: String = element.name + dimensions.joinToString(separator = "") { "[$it]" }
}

data class FunctionType(
    override val id: TypeId,
    val returnType: CType,
    val parameterTypes: List<CType>,
    val isVariadic: Boolean = false
) : CType {
    override val name: String = buildString {
        append("fn(")
        append(parameterTypes.joinToString(", ") { it.name })
        if (isVariadic) {
            if (parameterTypes.isNotEmpty()) append(", ")
            append("...")
        }
        append(")->")
        append(returnType.name)
    }
}

data class AliasType(
    override val id: TypeId,
    override val name: String,
    val target: CType
) : CType

data class ForeignType(
    override val id: TypeId,
    override val name: String,
    val externalName: String = name
) : CType

private fun canonicalTypeKey(type: CType): String = when (type) {
    is PrimitiveType -> "primitive:${type.name}"
    is StructType -> "struct:${type.name}"
    is UnionType -> "union:${type.name}"
    is EnumType -> "enum:${type.name}"
    is PointerType -> "pointer:${canonicalTypeKey(type.pointee)}"
    is ArrayType -> "array:${canonicalTypeKey(type.element)}:${type.dimensions.joinToString(",")}" 
    is FunctionType -> "function:${canonicalTypeKey(type.returnType)}:${type.parameterTypes.joinToString(",") { canonicalTypeKey(it) }}:${type.isVariadic}"
    is AliasType -> canonicalTypeKey(type.target)
    is ForeignType -> "foreign:${type.externalName}"
    is UnknownType -> "unknown:${type.id.value}"
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
    val visibility: Visibility = Visibility.PRIVATE,
    val moduleName: String? = null,
    val qualifiedName: QualifiedName = QualifiedName(name),
    val externalName: String? = null
)

data class FieldSymbol(
    val symbol: Symbol,
    val owner: CType
)

data class FunctionSymbol(
    val symbol: Symbol,
    val returnType: CType,
    val parameters: List<Symbol>,
    val isVariadic: Boolean = false,
    val signature: FunctionType
)

enum class ReceiverKind {
    INSTANCE,
    STATIC
}

data class MethodSymbol(
    val symbol: Symbol,
    val owner: StructType,
    val receiverKind: ReceiverKind,
    val returnType: CType,
    val parameters: List<Symbol>,
    val signature: FunctionType
)

data class SemanticModel(
    val program: AstProgram,
    val symbols: List<Symbol>,
    val types: List<CType>,
    val functions: Map<String, FunctionSymbol>,
    val structs: Map<String, StructType>,
    val methods: Map<String, Map<String, MethodSymbol>>,
    val scopes: ScopeTable,
    val expressionTypes: Map<AstExpression, CType>,
    val moduleFunctions: Map<String, Map<String, FunctionSymbol>> = emptyMap(),
    val qualifiedFunctionNames: Set<String> = emptySet(),
    val unions: Map<String, UnionType> = emptyMap(),
    val enums: Map<String, EnumType> = emptyMap(),
    val aliases: Map<String, AliasType> = emptyMap(),
    val foreignTypes: Map<String, ForeignType> = emptyMap(),
    val canonicalTypeIds: Map<String, TypeId> = emptyMap(),
    val modulePackages: Map<String, String> = emptyMap(),
    val packageModules: Map<String, Set<String>> = emptyMap(),
    val foreignGlobals: Map<String, Symbol> = emptyMap()
) {
    fun symbolNamed(name: String): Symbol? = symbols.firstOrNull { it.name == name }

    fun canonicalTypeId(type: CType): TypeId = canonicalTypeIds[canonicalTypeKey(type)] ?: type.id
}

data class SemanticResult(
    val model: SemanticModel?,
    val diagnostics: List<Diagnostic>
) {
    val isSuccessful: Boolean
        get() = model != null && diagnostics.none { it.severity == DiagnosticSeverity.ERROR }
}

class SemanticAnalyzer(
    private val headerImportService: CHeaderImportService = CHeaderImportService()
) {
    private val nextSymbolId = generateSequence(1) { it + 1 }.iterator()
    private val nextTypeId = generateSequence(1) { it + 1 }.iterator()

    fun analyze(program: AstProgram, knownModules: Set<String> = emptySet()): SemanticResult {
        val diagnostics = DiagnosticBag()
        val symbols = mutableListOf<Symbol>()
        val types = mutableListOf<CType>()
        val structs = linkedMapOf<String, StructType>()
        val unions = linkedMapOf<String, UnionType>()
        val enums = linkedMapOf<String, EnumType>()
        val aliases = linkedMapOf<String, AliasType>()
        val foreignTypes = linkedMapOf<String, ForeignType>()
        val aliasDeclarations = program.declarations
            .filterIsInstance<AstAlias>()
            .groupBy { it.name }
        val resolvingAliases = mutableSetOf<String>()
        val functions = linkedMapOf<String, FunctionSymbol>()
        val methods = linkedMapOf<String, Map<String, MethodSymbol>>()
        val globals = linkedMapOf<String, Symbol>()
        val foreignGlobals = linkedMapOf<String, Symbol>()
        val moduleFunctions = linkedMapOf<String, LinkedHashMap<String, FunctionSymbol>>()
        val declarationModules = IdentityHashMap<AstDeclaration, String>()
        val defaultModule = "<main>"
        if (program.modules.isEmpty()) {
            program.declarations.forEach { declarationModules[it] = defaultModule }
        } else {
            program.modules.forEach { module ->
                module.declarations.forEach { declarationModules[it] = module.name }
            }
        }
        val modulePackages = linkedMapOf<String, String>()
        if (program.modules.isEmpty()) {
            program.declarations.filterIsInstance<AstPackage>().firstOrNull()?.let { modulePackages[defaultModule] = it.name }
        } else {
            program.modules.forEach { module ->
                module.declarations.filterIsInstance<AstPackage>().firstOrNull()?.let { modulePackages[module.name] = it.name }
            }
        }
        val scopes = ScopeTable()
        val rootScope = scopes.create(ScopeKind.PACKAGE)
        val primitiveTypes = linkedMapOf<String, PrimitiveType>()
        val expressionTypes = linkedMapOf<AstExpression, CType>()

        fun primitive(name: String): PrimitiveType = primitiveTypes.getOrPut(name) {
            PrimitiveType(TypeId(nextTypeId.next()), name).also(types::add)
        }

        lateinit var resolveAlias: (String) -> AliasType?

        fun resolve(reference: AstTypeRef, dimensions: List<String> = emptyList()): CType {
            if (reference.name in aliasDeclarations && reference.name !in aliases) {
                resolveAlias(reference.name)
            }
            return resolveType(reference, structs, unions, enums, aliases, foreignTypes, ::primitive, diagnostics, dimensions).also { resolved ->
                if (resolved !is UnknownType && types.none { it.id == resolved.id }) types += resolved
            }
        }

        fun newSymbol(
            name: String,
            kind: SymbolKind,
            type: CType,
            origin: Origin,
            moduleName: String? = null,
            visibility: Visibility = Visibility.PRIVATE,
            externalName: String? = null
        ): Symbol = Symbol(
            SymbolId(nextSymbolId.next()),
            name,
            kind,
            type,
            origin,
            moduleName = moduleName,
            visibility = visibility,
            externalName = externalName,
            qualifiedName = QualifiedName(
                listOfNotNull(moduleName?.let { modulePackages[it] }, moduleName, name).joinToString("::")
            )
        ).also(symbols::add)

        fun registerForeignType(name: String, moduleName: String, origin: Origin) {
            if (foreignTypes.containsKey(name)) return
            val type = ForeignType(TypeId(nextTypeId.next()), name)
            foreignTypes[name] = type
            types += type
            val symbol = newSymbol(name, SymbolKind.FOREIGN_TYPE, type, origin, moduleName, Visibility.PUBLIC)
            scopes.define(rootScope, name, symbol.id)
        }

        fun registerForeignConstant(name: String, moduleName: String, origin: Origin, kind: SymbolKind) {
            if (foreignGlobals.containsKey(name)) return
            val symbol = newSymbol(name, kind, primitive("int"), origin, moduleName, Visibility.PUBLIC, name)
            globals[name] = symbol
            foreignGlobals[name] = symbol
            scopes.define(rootScope, name, symbol.id)
        }

        fun foreignTypeFromName(typeName: String, moduleName: String, origin: Origin): CType {
            val normalized = typeName.trim().removePrefix("const ").trim()
            val pointerDepth = normalized.count { it == '*' }
            val baseName = normalized.replace("*", "").trim()
            var type = when {
                baseName in knownPrimitiveNames -> primitive(baseName)
                baseName == "size_t" -> {
                    registerForeignType(baseName, "c.stddef", origin)
                    foreignTypes.getValue(baseName)
                }
                foreignTypes[baseName] != null -> foreignTypes.getValue(baseName)
                else -> {
                    diagnostics.error("unsupported foreign type '$baseName' from $moduleName", rangeOf(origin), "SEM407")
                    UnknownType(TypeId(-1))
                }
            }
            repeat(pointerDepth) {
                type = PointerType(TypeId(nextTypeId.next()), type)
            }
            return type
        }

        fun registerHeaderDeclaration(declaration: CHeaderDeclaration, moduleName: String, origin: Origin): Boolean {
            when (declaration.kind) {
                ForeignDeclarationKind.TYPE -> registerForeignType(declaration.name, moduleName, origin)
                ForeignDeclarationKind.FUNCTION -> {
                    val returnType = foreignTypeFromName(declaration.typeName ?: "void", moduleName, origin)
                    val parameterSymbols = declaration.parameterTypes.mapIndexed { index, parameterType ->
                        val type = foreignTypeFromName(parameterType, moduleName, origin)
                        newSymbol("${declaration.name}_arg$index", SymbolKind.PARAMETER, type, origin, moduleName, Visibility.PUBLIC)
                    }
                    val signature = FunctionType(
                        TypeId(nextTypeId.next()),
                        returnType,
                        parameterSymbols.map { it.type },
                        declaration.isVariadic
                    ).also(types::add)
                    val symbol = newSymbol(
                        declaration.name,
                        SymbolKind.FOREIGN,
                        signature,
                        origin,
                        moduleName,
                        Visibility.PUBLIC,
                        declaration.name
                    )
                    val function = FunctionSymbol(symbol, returnType, parameterSymbols, declaration.isVariadic, signature)
                    functions[declaration.name] = function
                    moduleFunctions.getOrPut(moduleName) { linkedMapOf() }[declaration.name] = function
                    scopes.define(rootScope, declaration.name, symbol.id)
                }
                ForeignDeclarationKind.GLOBAL -> {
                    val type = foreignTypeFromName(declaration.typeName ?: "int", moduleName, origin)
                    val symbol = newSymbol(
                        declaration.name,
                        SymbolKind.FOREIGN,
                        type,
                        origin,
                        moduleName,
                        Visibility.PUBLIC,
                        declaration.name
                    )
                    globals[declaration.name] = symbol
                    foreignGlobals[declaration.name] = symbol
                    scopes.define(rootScope, declaration.name, symbol.id)
                }
                ForeignDeclarationKind.ENUM_VALUE -> registerForeignConstant(declaration.name, moduleName, origin, SymbolKind.FOREIGN_ENUM_VALUE)
            }
            return true
        }

        fun declarationVisibility(declaration: AstDeclaration): Visibility =
            if (declaration.isPublic) Visibility.PUBLIC else Visibility.PRIVATE

        resolveAlias = { name ->
            aliases[name] ?: run {
                val declaration = aliasDeclarations[name]?.singleOrNull() ?: return@run null
                if (!resolvingAliases.add(name)) {
                    diagnostics.error("cyclic type alias '$name'", rangeOf(declaration.origin), "SEM110")
                    return@run null
                }
                val target = resolve(declaration.target, declaration.arrayDimensions)
                resolvingAliases.remove(name)
                AliasType(TypeId(nextTypeId.next()), name, target).also { type ->
                    aliases[name] = type
                    types += type
                    val symbol = newSymbol(
                        name,
                        SymbolKind.ALIAS,
                        type,
                        declaration.origin,
                        declarationModules[declaration] ?: defaultModule,
                        if (declaration.isPublic) Visibility.PUBLIC else Visibility.PRIVATE
                    )
                    scopes.define(rootScope, symbol.name, symbol.id)
                }
            }
        }

        program.declarations.forEach { declaration ->
            val moduleName = declarationModules[declaration] ?: defaultModule
            when (declaration) {
                is AstPackage -> Unit
                is AstAlias -> {
                    if (aliasDeclarations[declaration.name]?.singleOrNull() !== declaration) {
                        diagnostics.error("duplicate type alias '${declaration.name}'", rangeOf(declaration.origin), "SEM008")
                    } else {
                        resolveAlias(declaration.name)
                    }
                }
                is AstUnion -> {
                    if (unions.containsKey(declaration.name)) {
                        diagnostics.error("duplicate union '${declaration.name}'", rangeOf(declaration.origin), "SEM005")
                    } else {
                        val type = UnionType(TypeId(nextTypeId.next()), declaration.name, emptyList())
                        unions[declaration.name] = type
                        types += type
                        val symbol = newSymbol(declaration.name, SymbolKind.UNION, type, declaration.origin, moduleName, declarationVisibility(declaration))
                        scopes.define(rootScope, symbol.name, symbol.id)
                        scopes.create(ScopeKind.TYPE, rootScope, symbol.id)
                    }
                }
                is AstEnum -> {
                    if (enums.containsKey(declaration.name)) {
                        diagnostics.error("duplicate enum '${declaration.name}'", rangeOf(declaration.origin), "SEM006")
                    } else {
                        val type = EnumType(TypeId(nextTypeId.next()), declaration.name, declaration.values.map { it.name })
                        enums[declaration.name] = type
                        types += type
                        val symbol = newSymbol(declaration.name, SymbolKind.ENUM, type, declaration.origin, moduleName, declarationVisibility(declaration))
                        scopes.define(rootScope, symbol.name, symbol.id)
                        scopes.create(ScopeKind.TYPE, rootScope, symbol.id)
                    }
                }
                is AstStruct -> {
                    if (structs.containsKey(declaration.name)) {
                        diagnostics.error("duplicate structure '${declaration.name}'", rangeOf(declaration.origin), "SEM001")
                    } else {
                        val type = StructType(TypeId(nextTypeId.next()), declaration.name, emptyList())
                        structs[declaration.name] = type
                        types += type
                        val symbol = newSymbol(declaration.name, SymbolKind.STRUCT, type, declaration.origin, moduleName, declarationVisibility(declaration))
                        scopes.define(rootScope, symbol.name, symbol.id)
                        scopes.create(ScopeKind.TYPE, rootScope, symbol.id)
                    }
                }
                is AstFunction -> {
                    if (functions.containsKey(declaration.name)) {
                        diagnostics.error("duplicate function '${declaration.name}'", rangeOf(declaration.origin), "SEM002")
                    } else {
                        val returnType = resolve(declaration.returnType)
                        val parameterSymbols = declaration.parameters.map { parameter ->
                            val type = resolve(parameter.type, parameter.arrayDimensions)
                            newSymbol(parameter.name, SymbolKind.PARAMETER, type, parameter.origin, moduleName)
                        }
                        val signature = FunctionType(
                            TypeId(nextTypeId.next()),
                            returnType,
                            parameterSymbols.map { it.type }
                        ).also(types::add)
                        val functionSymbol = newSymbol(declaration.name, SymbolKind.FUNCTION, signature, declaration.origin, moduleName, declarationVisibility(declaration))
                        val function = FunctionSymbol(functionSymbol, returnType, parameterSymbols, signature = signature)
                        functions[declaration.name] = function
                        moduleFunctions.getOrPut(moduleName) { linkedMapOf() }[declaration.name] = function
                        val functionScope = scopes.create(ScopeKind.FUNCTION, rootScope, functionSymbol.id)
                        scopes.define(rootScope, functionSymbol.name, functionSymbol.id)
                        parameterSymbols.forEach { scopes.define(functionScope, it.name, it.id) }
                    }
                }
                is AstGlobalVariable -> {
                    if (globals.containsKey(declaration.name)) {
                        diagnostics.error("duplicate global '${declaration.name}'", rangeOf(declaration.origin), "SEM003")
                    } else {
                        val type = resolve(declaration.type, declaration.arrayDimensions)
                        val symbol = newSymbol(declaration.name, SymbolKind.VARIABLE, type, declaration.origin, moduleName, declarationVisibility(declaration))
                        globals[declaration.name] = symbol
                        scopes.define(rootScope, symbol.name, symbol.id)
                    }
                }
                is AstImport -> {
                    headerImportService.unsupportedPreprocessorLines(declaration.module).forEach { line ->
                        diagnostics.error(
                            "unsupported preprocessor construct in ${declaration.module}: $line",
                            rangeOf(declaration.origin),
                            "SEM409"
                        )
                    }
                    if (declaration.module == "c.stdio") {
                        declaration.names.forEach { name ->
                            when (name) {
                                "printf" -> {
                                    val returnType = primitive("int")
                                    val signature = FunctionType(TypeId(nextTypeId.next()), returnType, emptyList(), isVariadic = true).also(types::add)
                                    val symbol = newSymbol(
                                        name,
                                        SymbolKind.FOREIGN,
                                        signature,
                                        declaration.origin,
                                        declaration.module,
                                        Visibility.PUBLIC,
                                        name
                                    )
                                    val function = FunctionSymbol(symbol, returnType, emptyList(), isVariadic = true, signature = signature)
                                    functions[name] = function
                                    moduleFunctions.getOrPut(declaration.module) { linkedMapOf() }[name] = function
                                    scopes.define(rootScope, name, symbol.id)
                                }
                                "FILE" -> registerForeignType(name, declaration.module, declaration.origin)
                                "EOF" -> registerForeignConstant(name, declaration.module, declaration.origin, SymbolKind.FOREIGN)
                                "SEEK_SET", "SEEK_CUR", "SEEK_END" -> registerForeignConstant(name, declaration.module, declaration.origin, SymbolKind.FOREIGN_ENUM_VALUE)
                                else -> headerImportService.declarations(declaration.module)[name]?.let {
                                    registerHeaderDeclaration(it, declaration.module, declaration.origin)
                                } ?: diagnostics.error("unsupported imported C symbol '$name' from c.stdio", rangeOf(declaration.origin), "SEM401")
                            }
                        }
                    } else if (declaration.module == "c.stddef") {
                        declaration.names.forEach { name ->
                            if (name == "size_t") {
                                registerForeignType(name, declaration.module, declaration.origin)
                            } else {
                                headerImportService.declarations(declaration.module)[name]?.let {
                                    registerHeaderDeclaration(it, declaration.module, declaration.origin)
                                } ?: diagnostics.error("unsupported imported C symbol '$name' from c.stddef", rangeOf(declaration.origin), "SEM402")
                            }
                        }
                    } else if (declaration.module.startsWith("c.")) {
                        val declarations = headerImportService.declarations(declaration.module)
                        declaration.names.forEach { name ->
                            declarations[name]?.let { registerHeaderDeclaration(it, declaration.module, declaration.origin) }
                                ?: diagnostics.error("unsupported imported C symbol '$name' from ${declaration.module}", rangeOf(declaration.origin), "SEM408")
                        }
                    }
                }
                is AstComptimeFunction, is AstCpxInvocation -> Unit
            }
        }

        program.declarations.filterIsInstance<AstStruct>().forEach { declaration ->
            val struct = structs[declaration.name] ?: return@forEach
            val fields = declaration.fields.map { field ->
                val type = resolve(field.type, field.arrayDimensions)
                val symbol = newSymbol(field.name, SymbolKind.FIELD, type, field.origin, declarationModules[declaration] ?: defaultModule)
                FieldSymbol(symbol, struct)
            }
            val methodSymbols = declaration.methods.associate { method ->
                val returnType = resolve(method.returnType)
                val parameterSymbols = method.parameters.filterNot { it.isReceiver }.map { parameter ->
                    val parameterType = resolve(parameter.type, parameter.arrayDimensions)
                    newSymbol(parameter.name, SymbolKind.PARAMETER, parameterType, parameter.origin, declarationModules[declaration] ?: defaultModule)
                }
                val receiverKind = if (method.parameters.any { it.isReceiver }) ReceiverKind.INSTANCE else ReceiverKind.STATIC
                val signature = FunctionType(
                    TypeId(nextTypeId.next()),
                    returnType,
                    parameterSymbols.map { it.type }
                ).also(types::add)
                val methodSymbol = newSymbol(method.name, SymbolKind.METHOD, signature, method.origin, declarationModules[declaration] ?: defaultModule)
                method.name to MethodSymbol(methodSymbol, struct, receiverKind, returnType, parameterSymbols, signature)
            }
            val updatedStruct = struct.copy(fields = fields, methods = methodSymbols.values.toList())
            structs[declaration.name] = updatedStruct
            methods[declaration.name] = methodSymbols
        }

        program.declarations.filterIsInstance<AstUnion>().forEach { declaration ->
            val union = unions[declaration.name] ?: return@forEach
            val fields = declaration.fields.map { field ->
                val type = resolve(field.type, field.arrayDimensions)
                val symbol = newSymbol(field.name, SymbolKind.FIELD, type, field.origin, declarationModules[declaration] ?: defaultModule)
                FieldSymbol(symbol, union)
            }
            unions[declaration.name] = union.copy(fields = fields)
        }

        program.declarations.filterIsInstance<AstEnum>().forEach { declaration ->
            val enum = enums[declaration.name] ?: return@forEach
            declaration.values.forEach { value ->
                if (globals.containsKey(value.name)) {
                    diagnostics.error("duplicate enum value '${value.name}'", rangeOf(value.origin), "SEM007")
                } else {
                    val symbol = newSymbol(value.name, SymbolKind.ENUM_VALUE, enum, value.origin, declarationModules[declaration] ?: defaultModule)
                    globals[value.name] = symbol
                    scopes.define(rootScope, value.name, symbol.id)
                }
            }
        }

        val visibleFunctions = resolveImportedFunctions(program, moduleFunctions, foreignTypes, foreignGlobals, diagnostics, knownModules)

        program.declarations.filterIsInstance<AstFunction>().forEach { declaration ->
            val function = functions[declaration.name] ?: return@forEach
            val moduleName = declarationModules[declaration] ?: defaultModule
            val availableFunctions = visibleFunctions[moduleName] ?: functions
            val locals = linkedMapOf<String, Symbol>()
            function.parameters.forEach { locals[it.name] = it }
            declaration.body?.let { statement ->
                validateStatement(statement, function.returnType, locals, availableFunctions, globals, structs, unions, enums, aliases, foreignTypes, methods, expressionTypes, diagnostics, ::primitive)
            }
        }

        program.declarations.filterIsInstance<AstStruct>().forEach { declaration ->
            val owner = structs[declaration.name] ?: return@forEach
            val availableFunctions = visibleFunctions[declarationModules[declaration] ?: defaultModule] ?: functions
            declaration.methods.forEach { method ->
                val methodSymbol = methods[owner.name]?.get(method.name) ?: return@forEach
                val locals = linkedMapOf<String, Symbol>()
                method.parameters.forEach { parameter ->
                    val type = if (parameter.isReceiver) owner else resolve(parameter.type, parameter.arrayDimensions)
                    locals[parameter.name] = newSymbol(parameter.name, SymbolKind.PARAMETER, type, parameter.origin)
                }
                method.body?.let { statement ->
                    validateStatement(statement, methodSymbol.returnType, locals, availableFunctions, globals, structs, unions, enums, aliases, foreignTypes, methods, expressionTypes, diagnostics, ::primitive)
                }
            }
        }

        val model = SemanticModel(
            program,
            symbols,
            types,
            functions,
            structs,
            methods,
            scopes,
            expressionTypes,
            visibleFunctions,
            visibleFunctions.values.flatMap { it.keys }.filter { '.' in it }.toSet(),
            unions,
            enums,
            aliases,
            foreignTypes,
            types.fold(linkedMapOf<String, TypeId>()) { ids, type ->
                ids.putIfAbsent(canonicalTypeKey(type), type.id)
                ids
            },
            modulePackages,
            modulePackages.entries.groupBy({ it.value }, { it.key }).mapValues { (_, modules) -> modules.toSet() },
            foreignGlobals
        )
        return SemanticResult(model, diagnostics.diagnostics)
    }

    private fun resolveImportedFunctions(
        program: AstProgram,
        moduleFunctions: Map<String, Map<String, FunctionSymbol>>,
        foreignTypes: Map<String, ForeignType>,
        foreignGlobals: Map<String, Symbol>,
        diagnostics: DiagnosticBag,
        knownModules: Set<String>
    ): Map<String, Map<String, FunctionSymbol>> {
        val moduleDeclarations = if (program.modules.isEmpty()) {
            mapOf("<main>" to program.declarations)
        } else {
            program.modules.associate { it.name to it.declarations }
        }
        return moduleDeclarations.mapValues { (moduleName, declarations) ->
            val visible = linkedMapOf<String, FunctionSymbol>()
            moduleFunctions[moduleName].orEmpty().forEach { (name, function) -> visible[name] = function }
            declarations.filterIsInstance<AstImport>().forEach { import ->
                val targetName = import.module.substringAfterLast('.')
                val targetFunctions = moduleFunctions[import.module] ?: moduleFunctions[targetName]
                val exportedFunctions = targetFunctions?.filterValues {
                    it.symbol.visibility == Visibility.PUBLIC || it.symbol.kind == SymbolKind.FOREIGN
                }.orEmpty()
                if (targetFunctions == null) {
                    if (import.module !in setOf("c.stdio", "c.stddef", "c.stdlib", "c.math") && targetName !in knownModules) {
                        diagnostics.error("module import '${import.module}' cannot be resolved", rangeOf(import.origin), "SEM402")
                    } else if (import.names.isNotEmpty()) {
                        import.names.forEach { name ->
                            if (name !in foreignTypes && name !in foreignGlobals) {
                                diagnostics.error("imported function '$name' is not declared in module '${import.module}'", rangeOf(import.origin), "SEM404")
                            }
                        }
                    }
                    return@forEach
                }
                if (import.names.isEmpty()) {
                    import.alias?.let { alias ->
                        exportedFunctions.forEach { (name, function) -> visible["$alias.$name"] = function }
                    }
                    return@forEach
                }
                import.names.forEach { name ->
                    if (name in foreignTypes || name in foreignGlobals) return@forEach
                    val function = targetFunctions[name]
                    if (function == null) {
                        diagnostics.error("imported function '$name' is not declared in module '${import.module}'", rangeOf(import.origin), "SEM404")
                    } else if (function.symbol.visibility != Visibility.PUBLIC && function.symbol.kind != SymbolKind.FOREIGN) {
                        diagnostics.error("imported function '$name' is not public in module '${import.module}'", rangeOf(import.origin), "SEM406")
                    } else if (import.alias != null) {
                        visible["${import.alias}.$name"] = function
                    } else if (visible.putIfAbsent(name, function) != null) {
                        diagnostics.error("imported name '$name' conflicts in module '$moduleName'", rangeOf(import.origin), "SEM405")
                    }
                }
            }
            visible
        }
    }

    private fun resolveType(
        reference: AstTypeRef,
        structs: Map<String, StructType>,
        unions: Map<String, UnionType>,
        enums: Map<String, EnumType>,
        aliases: Map<String, AliasType>,
        foreignTypes: Map<String, ForeignType>,
        primitive: (String) -> PrimitiveType,
        diagnostics: DiagnosticBag,
        arrayDimensions: List<String> = emptyList()
    ): CType {
        val base = aliases[reference.name] ?: foreignTypes[reference.name] ?: when (reference.declarationKind) {
            "struct" -> structs[reference.name] ?: run {
                diagnostics.error("unknown structure type '${reference.name}'", rangeOf(reference.origin), "SEM101")
                UnknownType(TypeId(-1))
            }
            "union" -> unions[reference.name] ?: run {
                diagnostics.error("unknown union type '${reference.name}'", rangeOf(reference.origin), "SEM105")
                UnknownType(TypeId(-1))
            }
            "enum" -> enums[reference.name] ?: run {
                diagnostics.error("unknown enum type '${reference.name}'", rangeOf(reference.origin), "SEM106")
                UnknownType(TypeId(-1))
            }
            else -> when {
                reference.name in knownPrimitiveNames -> primitive(reference.name)
                structs[reference.name] != null -> structs.getValue(reference.name)
                unions[reference.name] != null -> unions.getValue(reference.name)
                enums[reference.name] != null -> enums.getValue(reference.name)
                aliases[reference.name] != null -> aliases.getValue(reference.name)
                foreignTypes[reference.name] != null -> foreignTypes.getValue(reference.name)
                else -> {
                    diagnostics.error("unknown type '${reference.name}'", rangeOf(reference.origin), "SEM102")
                    UnknownType(TypeId(-1))
                }
            }
        }
        var resolved = base
        repeat(reference.pointerDepth) {
            resolved = PointerType(TypeId(nextTypeId.next()), resolved)
        }
        if (arrayDimensions.isNotEmpty()) {
            resolved = ArrayType(TypeId(nextTypeId.next()), resolved, arrayDimensions)
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
        unions: Map<String, UnionType>,
        enums: Map<String, EnumType>,
        aliases: Map<String, AliasType>,
        foreignTypes: Map<String, ForeignType>,
        methods: Map<String, Map<String, MethodSymbol>>,
        expressionTypes: MutableMap<AstExpression, CType>,
        diagnostics: DiagnosticBag,
        primitive: (String) -> PrimitiveType,
        loopDepth: Int = 0
    ) {
        when (statement) {
            is AstBlock -> statement.statements.forEach {
                validateStatement(it, expectedReturn, locals, functions, globals, structs, unions, enums, aliases, foreignTypes, methods, expressionTypes, diagnostics, primitive, loopDepth)
            }
            is AstReturn -> {
                val returnExpression = statement.expression
                if (returnExpression == null) {
                    if (expectedReturn.name != "void") {
                        diagnostics.error("non-void function must return a value", rangeOf(statement.origin), "SEM201")
                    }
                } else {
                    val actual = validateExpression(returnExpression, locals, functions, globals, structs, methods, expressionTypes, diagnostics, primitive)
                    if (expectedReturn.name == "void") {
                        diagnostics.error("void function cannot return a value", rangeOf(statement.origin), "SEM202")
                } else if (actual.name != "<unknown>" && !equivalentTypes(expectedReturn, actual)) {
                        diagnostics.error(
                            "return type '${actual.name}' does not match '${expectedReturn.name}'",
                            rangeOf(statement.origin),
                            "SEM203"
                        )
                    }
                }
            }
            is AstExpressionStatement -> validateExpression(statement.expression, locals, functions, globals, structs, methods, expressionTypes, diagnostics, primitive)
            is AstDefer -> validateExpression(statement.expression, locals, functions, globals, structs, methods, expressionTypes, diagnostics, primitive)
            is AstIf -> {
                validateExpression(statement.condition, locals, functions, globals, structs, methods, expressionTypes, diagnostics, primitive)
                validateStatement(statement.thenBranch, expectedReturn, LinkedHashMap(locals), functions, globals, structs, unions, enums, aliases, foreignTypes, methods, expressionTypes, diagnostics, primitive, loopDepth)
                statement.elseBranch?.let {
                    validateStatement(it, expectedReturn, LinkedHashMap(locals), functions, globals, structs, unions, enums, aliases, foreignTypes, methods, expressionTypes, diagnostics, primitive, loopDepth)
                }
            }
            is AstWhile -> {
                validateExpression(statement.condition, locals, functions, globals, structs, methods, expressionTypes, diagnostics, primitive)
                validateStatement(statement.body, expectedReturn, LinkedHashMap(locals), functions, globals, structs, unions, enums, aliases, foreignTypes, methods, expressionTypes, diagnostics, primitive, loopDepth + 1)
            }
            is AstFor -> {
                val loopLocals = LinkedHashMap(locals)
                statement.initializer?.let {
                    validateStatement(it, expectedReturn, loopLocals, functions, globals, structs, unions, enums, aliases, foreignTypes, methods, expressionTypes, diagnostics, primitive, loopDepth)
                }
                statement.condition?.let {
                    validateExpression(it, loopLocals, functions, globals, structs, methods, expressionTypes, diagnostics, primitive)
                }
                validateStatement(statement.body, expectedReturn, loopLocals, functions, globals, structs, unions, enums, aliases, foreignTypes, methods, expressionTypes, diagnostics, primitive, loopDepth + 1)
                statement.increment?.let {
                    validateExpression(it, loopLocals, functions, globals, structs, methods, expressionTypes, diagnostics, primitive)
                }
            }
            is AstBreak -> if (loopDepth == 0) {
                diagnostics.error("break is only valid inside a loop", rangeOf(statement.origin), "SEM205")
            }
            is AstContinue -> if (loopDepth == 0) {
                diagnostics.error("continue is only valid inside a loop", rangeOf(statement.origin), "SEM206")
            }
            is AstVariableDeclaration -> {
                val type = resolveType(statement.type, structs, unions, enums, aliases, foreignTypes, primitive, diagnostics, statement.arrayDimensions)
                val symbol = Symbol(SymbolId(-locals.size - 1), statement.name, SymbolKind.VARIABLE, type, statement.origin)
                if (locals.containsKey(statement.name)) {
                    diagnostics.error("duplicate local '${statement.name}'", rangeOf(statement.origin), "SEM204")
                } else {
                    locals[statement.name] = symbol
                }
                statement.initializer?.let {
                    validateExpression(it, locals, functions, globals, structs, methods, expressionTypes, diagnostics, primitive)
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
        methods: Map<String, Map<String, MethodSymbol>>,
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
                    ?: functions[expression.name]?.signature
                    ?: structs[expression.name]
                    ?: run {
                        diagnostics.error("unknown identifier '${expression.name}'", rangeOf(expression.origin), "SEM301")
                        UnknownType(TypeId(-1))
                    }
            }
            is AstUnary -> validateExpression(expression.operand, locals, functions, globals, structs, methods, expressionTypes, diagnostics, primitive)
            is AstBinary -> {
                val left = validateExpression(expression.left, locals, functions, globals, structs, methods, expressionTypes, diagnostics, primitive)
                validateExpression(expression.right, locals, functions, globals, structs, methods, expressionTypes, diagnostics, primitive)
                left
            }
            is AstCall -> {
                val function = (expression.callee as? AstIdentifier)?.let { functions[it.name] }
                val methodCall = expression.callee as? AstMemberAccess
                val qualifiedFunction = methodCall?.let { member ->
                    (member.receiver as? AstIdentifier)?.let { receiver -> functions["${receiver.name}.${member.member}"] }
                }
                val resolvedMethod = if (methodCall != null && qualifiedFunction == null) {
                    val receiverType = validateExpression(
                        methodCall.receiver,
                        locals,
                        functions,
                        globals,
                        structs,
                        methods,
                        expressionTypes,
                        diagnostics,
                        primitive
                    )
                    val owner = ownerStruct(methodCall.receiver, receiverType, structs)
                    owner?.methods?.firstOrNull { it.symbol.name == methodCall.member }
                } else null
                when {
                    function != null -> {
                        validateCallArguments(function.symbol.name, function.parameters, function.isVariadic, expression, locals, functions, globals, structs, methods, expressionTypes, diagnostics, primitive)
                        function.returnType
                    }
                    qualifiedFunction != null -> {
                        validateCallArguments(qualifiedFunction.symbol.name, qualifiedFunction.parameters, qualifiedFunction.isVariadic, expression, locals, functions, globals, structs, methods, expressionTypes, diagnostics, primitive)
                        qualifiedFunction.returnType
                    }
                    resolvedMethod != null -> {
                        validateCallArguments(resolvedMethod.symbol.name, resolvedMethod.parameters, false, expression, locals, functions, globals, structs, methods, expressionTypes, diagnostics, primitive)
                        resolvedMethod.returnType
                    }
                    else -> {
                        diagnostics.error("call target is not a known function or method", rangeOf(expression.callee.origin), "SEM302")
                        expression.arguments.forEach { validateExpression(it, locals, functions, globals, structs, methods, expressionTypes, diagnostics, primitive) }
                        UnknownType(TypeId(-1))
                    }
                }
            }
            is AstMemberAccess -> {
                val qualifiedFunction = (expression.receiver as? AstIdentifier)?.let { receiver ->
                    functions["${receiver.name}.${expression.member}"]
                }
                if (qualifiedFunction != null) {
                    qualifiedFunction.signature
                } else {
                    val receiver = validateExpression(expression.receiver, locals, functions, globals, structs, methods, expressionTypes, diagnostics, primitive)
                    val fields = aggregateFields(receiver)
                    val field = fields.firstOrNull { it.symbol.name == expression.member }
                    if (field != null) field.symbol.type else {
                        val owner = aggregateStruct(receiver)
                        if (owner == null || owner.methods.none { it.symbol.name == expression.member }) {
                            diagnostics.error("unknown member '${expression.member}'", rangeOf(expression.origin), "SEM304")
                        }
                        UnknownType(TypeId(-1))
                    }
                }
            }
            is AstParenthesized -> validateExpression(expression.expression, locals, functions, globals, structs, methods, expressionTypes, diagnostics, primitive)
            is AstErrorExpression -> UnknownType(TypeId(-1))
        }
        expressionTypes[expression] = type
        return type
    }

    private fun validateCallArguments(
        name: String,
        parameters: List<Symbol>,
        isVariadic: Boolean,
        call: AstCall,
        locals: Map<String, Symbol>,
        functions: Map<String, FunctionSymbol>,
        globals: Map<String, Symbol>,
        structs: Map<String, StructType>,
        methods: Map<String, Map<String, MethodSymbol>>,
        expressionTypes: MutableMap<AstExpression, CType>,
        diagnostics: DiagnosticBag,
        primitive: (String) -> PrimitiveType
    ) {
        if ((!isVariadic && parameters.size != call.arguments.size) || call.arguments.size < parameters.size) {
            diagnostics.error(
                "function '$name' expects ${parameters.size} arguments but received ${call.arguments.size}",
                rangeOf(call.origin),
                "SEM303"
            )
        }
        val actualTypes = call.arguments.map {
            validateExpression(it, locals, functions, globals, structs, methods, expressionTypes, diagnostics, primitive)
        }
        parameters.zip(actualTypes).forEach { (parameter, actual) ->
            if (actual !is UnknownType && !argumentCompatible(parameter.type, actual)) {
                diagnostics.error(
                    "argument for '${parameter.name}' has type '${actual.name}', expected '${parameter.type.name}'",
                    rangeOf(call.origin),
                    "SEM306"
                )
            }
        }
    }

    private fun equivalentTypes(left: CType, right: CType): Boolean = canonicalTypeKey(left) == canonicalTypeKey(right)

    private fun argumentCompatible(expected: CType, actual: CType): Boolean = when {
        expected is ArrayType && actual is ArrayType -> equivalentTypes(expected.element, actual.element)
        else -> equivalentTypes(expected, actual)
    }

    private fun canonicalType(type: CType): CType = when (type) {
        is AliasType -> canonicalType(type.target)
        else -> type
    }

    private fun aggregateFields(type: CType): List<FieldSymbol> = when (val canonical = aggregateType(type)) {
        is StructType -> canonical.fields
        is UnionType -> canonical.fields
        else -> emptyList()
    }

    private fun aggregateStruct(type: CType): StructType? = aggregateType(type) as? StructType

    private fun aggregateType(type: CType): CType = when (type) {
        is AliasType -> aggregateType(type.target)
        is PointerType -> aggregateType(type.pointee)
        else -> type
    }

    private fun ownerStruct(receiver: AstExpression, receiverType: CType, structs: Map<String, StructType>): StructType? = when (receiver) {
        is AstIdentifier -> structs[receiver.name] ?: aggregateStruct(receiverType)?.let { structs[it.name] ?: it }
        else -> aggregateStruct(receiverType)?.let { structs[it.name] ?: it }
    }

    private fun rangeOf(origin: Origin): SourceRange? = origin.primaryRange

    companion object {
        private val knownPrimitiveNames = setOf(
            "void", "bool", "char", "short", "int", "long", "float", "double", "signed", "unsigned"
        )
    }
}
