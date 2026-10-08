package cplus.semantic

import cplus.core.*
import java.util.IdentityHashMap

@JvmInline
value class SymbolId(val value: Int)

@JvmInline
value class TypeId(val value: Int)

data class SemanticTypeIdentity(
    val typeId: TypeId,
    val canonicalTypeId: TypeId,
    val canonicalText: String
)

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
    FOREIGN_GLOBAL,
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
    var fields: List<FieldSymbol>,
    var methods: List<MethodSymbol> = emptyList(),
    val moduleName: String? = null
) : CType

data class UnionType(
    override val id: TypeId,
    override val name: String,
    var fields: List<FieldSymbol>,
    val moduleName: String? = null
) : CType

data class EnumType(
    override val id: TypeId,
    override val name: String,
    val values: List<String>,
    val moduleName: String? = null
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
    val isVariadic: Boolean = false,
    val abi: AbiKind = AbiKind.C
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
    val externalName: String = name,
    var underlyingType: CType? = null,
    val moduleName: String? = null
) : CType

private fun canonicalTypeKey(type: CType): String = when (type) {
    is PrimitiveType -> "primitive:${CPrimitiveTypes.canonicalName(type.name) ?: type.name}"
    is StructType -> "struct:${type.id.value}"
    is UnionType -> "union:${type.id.value}"
    is EnumType -> "enum:${type.id.value}"
    is PointerType -> "pointer:${canonicalTypeKey(type.pointee)}"
    is ArrayType -> "array:${canonicalTypeKey(type.element)}:${type.dimensions.joinToString(",")}" 
    is FunctionType -> "function:${type.abi}:${canonicalTypeKey(type.returnType)}:${type.parameterTypes.joinToString(",") { canonicalTypeKey(it) }}:${type.isVariadic}"
    is AliasType -> canonicalTypeKey(type.target)
    is ForeignType -> "foreign:${type.id.value}:${type.externalName}"
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
    val externalName: String? = null,
    val abi: AbiKind = AbiKind.C,
    val constantExpression: String? = null,
    val externalSource: java.nio.file.Path? = null,
    val externalLine: Int? = null
)

data class DeclarationCatalogueEntry(
    val name: String,
    val kind: String,
    val symbol: SymbolId?,
    val scope: ScopeId,
    val origin: Origin,
    val parameters: List<String> = emptyList(),
    val compileTime: Boolean = false
)

data class DeclarationCatalogue(
    val entries: List<DeclarationCatalogueEntry>
) {
    private val byName: Map<String, List<DeclarationCatalogueEntry>> = entries.groupBy { it.name }

    fun named(name: String): List<DeclarationCatalogueEntry> = byName[name].orEmpty()

    fun contains(name: String): Boolean = name in byName
}

data class FieldSymbol(
    val symbol: Symbol,
    val owner: CType
)

data class FunctionSymbol(
    val symbol: Symbol,
    val returnType: CType,
    val parameters: List<Symbol>,
    val isVariadic: Boolean = false,
    val signature: FunctionType,
    val abi: AbiKind = AbiKind.C
)

enum class ReceiverKind {
    INSTANCE,
    STATIC
}

enum class ReceiverAdaptation {
    VALUE,
    ADDRESS,
    POINTER,
    NONE
}

data class ResolvedMethodCall(
    val method: MethodSymbol,
    val receiver: AstExpression,
    val adaptation: ReceiverAdaptation
)

data class MethodSymbol(
    val symbol: Symbol,
    val owner: CType,
    val receiverIdentity: ReceiverIdentity,
    val definingModule: String,
    val receiverKind: ReceiverKind,
    val returnType: CType,
    val parameters: List<Symbol>,
    val signature: FunctionType,
    val receiverType: CType,
    val abi: AbiKind = AbiKind.C,
    val isExtension: Boolean = false
)

/** Canonical identity of the receiver type, independent of its source spelling. */
data class ReceiverIdentity(val canonicalTypeId: TypeId) {
    companion object {
        fun of(type: CType): ReceiverIdentity {
            fun canonical(current: CType): CType = when (current) {
                is AliasType -> canonical(current.target)
                is PointerType -> canonical(current.pointee)
                is ForeignType -> current.underlyingType?.let(::canonical) ?: current
                else -> current
            }
            return ReceiverIdentity(canonical(type).id)
        }
    }
}

data class MethodLookupKey(val receiver: ReceiverIdentity, val name: String)

/** Immutable, receiver-keyed method set shared by native and extension lookup. */
class MethodRegistry private constructor(
    private val methodsByKey: Map<MethodLookupKey, List<MethodSymbol>>
) {
    val allMethods: List<MethodSymbol>
        get() = methodsByKey.values.flatten()

    val extensionModules: Set<String> = methodsByKey.values.flatten()
        .filter(MethodSymbol::isExtension)
        .mapTo(linkedSetOf(), MethodSymbol::definingModule)

    fun lookup(
        receiver: ReceiverIdentity,
        name: String,
        visibleExtensionModules: Set<String> = emptySet(),
        requestingModule: String? = null
    ): List<MethodSymbol> = methodsByKey[MethodLookupKey(receiver, name)].orEmpty().filter { method ->
        !method.isExtension || method.definingModule == requestingModule ||
            (method.symbol.visibility == Visibility.PUBLIC && method.definingModule in visibleExtensionModules)
    }

    fun forReceiver(
        receiver: ReceiverIdentity,
        visibleExtensionModules: Set<String> = emptySet(),
        requestingModule: String? = null
    ): List<MethodSymbol> = methodsByKey.asSequence()
        .filter { (key, _) -> key.receiver == receiver }
        .flatMap { (_, methods) -> methods.asSequence() }
        .filter { method ->
            !method.isExtension || method.definingModule == requestingModule ||
                (method.symbol.visibility == Visibility.PUBLIC && method.definingModule in visibleExtensionModules)
        }
        .toList()

    class Builder {
        private val methodsByKey = linkedMapOf<MethodLookupKey, MutableList<MethodSymbol>>()

        /** Returns false rather than replacing an existing receiver/name entry. */
        fun add(method: MethodSymbol): Boolean {
            val entries = methodsByKey.getOrPut(MethodLookupKey(method.receiverIdentity, method.symbol.name)) { mutableListOf() }
            if (entries.any { existing ->
                    !existing.isExtension && !method.isExtension ||
                        existing.isExtension && method.isExtension && existing.definingModule == method.definingModule
                }
            ) return false
            entries += method
            return true
        }

        fun build(): MethodRegistry = MethodRegistry(methodsByKey.mapValues { (_, methods) -> methods.toList() })
    }

    companion object {
        fun from(methods: Iterable<MethodSymbol>): MethodRegistry = Builder().let { builder ->
            methods.forEach(builder::add)
            builder.build()
        }
    }
}

data class SourceTypeCatalogue(
    val declarationsByModule: Map<String, Map<String, Symbol>>,
    val exportsByModule: Map<String, Map<String, Symbol>>
) {
    companion object {
        fun from(symbols: List<Symbol>): SourceTypeCatalogue {
            val sourceTypeKinds = setOf(SymbolKind.ALIAS, SymbolKind.STRUCT, SymbolKind.UNION, SymbolKind.ENUM)
            val declarationsByModule = symbols.asSequence()
                .filter { it.kind in sourceTypeKinds }
                .mapNotNull { symbol -> symbol.moduleName?.let { it to symbol } }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, declarations) -> declarations.associateBy(Symbol::name) }
            val exportsByModule = declarationsByModule.mapValues { (_, declarations) ->
                declarations.filterValues { it.visibility == Visibility.PUBLIC }
            }
            return SourceTypeCatalogue(declarationsByModule, exportsByModule)
        }
    }
}

data class SourceTypeBinding(
    val declarationName: String,
    val ownerModule: String,
    val symbol: Symbol
)

private data class SourceTypeBindingRef(
    val declarationName: String,
    val ownerModule: String
)

private data class ModuleTypeEnvironment(
    val structs: Map<String, StructType>,
    val unions: Map<String, UnionType>,
    val enums: Map<String, EnumType>,
    val aliases: Map<String, AliasType>
) {
    val knownTypes: Map<String, CType>
        get() = buildMap {
            putAll(structs)
            putAll(unions)
            putAll(enums)
            putAll(aliases)
        }
}

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
    val foreignGlobals: Map<String, Symbol> = emptyMap(),
    val nodeIds: Map<NodeId, AstNode> = emptyMap(),
    val referenceIndex: ReferenceIndex = ReferenceIndex(),
    val declarationCatalogue: DeclarationCatalogue = DeclarationCatalogue(emptyList()),
    val moduleTypeBindings: Map<String, Map<String, SourceTypeBinding>> = emptyMap(),
    val moduleTypeAliases: Map<String, Map<String, String>> = emptyMap(),
    val methodRegistry: MethodRegistry = MethodRegistry.from(methods.values.flatMap { it.values }),
    val resolvedMethodCalls: Map<AstCall, ResolvedMethodCall> = emptyMap(),
    val extensionModuleImports: Map<String, Set<String>> = emptyMap()
) {
    val sourceTypeCatalogue: SourceTypeCatalogue
        get() = SourceTypeCatalogue.from(symbols)

    val foreignFunctions: Map<String, FunctionSymbol>
        get() = functions.filterValues { it.symbol.kind == SymbolKind.FOREIGN }

    val foreignSymbols: List<Symbol>
        get() = symbols.filter {
            it.kind == SymbolKind.FOREIGN ||
                it.kind == SymbolKind.FOREIGN_GLOBAL ||
                it.kind == SymbolKind.FOREIGN_TYPE ||
                it.kind == SymbolKind.FOREIGN_ENUM_VALUE
        }

    fun symbolNamed(name: String): Symbol? = symbols.firstOrNull { it.name == name }

    fun lookup(name: String): Symbol? = symbols.firstOrNull {
        it.name == name || it.qualifiedName.value == name
    }

    fun functionSignature(name: String): FunctionType? = functions[name]?.signature

    fun resolveFunction(name: String): FunctionSymbol? = functions[name]
        ?: moduleFunctions.values.asSequence().mapNotNull { it[name] }.firstOrNull()

    fun canonicalTypeId(type: CType): TypeId = canonicalTypeIds[canonicalTypeKey(type)] ?: type.id

    fun lookupMethods(receiver: CType, name: String, requestingModule: String = "<main>"): List<MethodSymbol> =
        methodRegistry.lookup(
            ReceiverIdentity.of(receiver),
            name,
            extensionModuleImports[requestingModule].orEmpty(),
            requestingModule
        )

    fun resolveComptimeReferences(
        node: AstNode,
        arena: AstArena,
        containingFunctionName: String? = null
    ): Map<NodeId, SymbolId> = ReferenceCollector.collectFragment(this, node, arena, containingFunctionName)

    fun resolveComptimeTypeIdentity(sourceText: String): SemanticTypeIdentity? {
        val normalized = sourceText.trim().replace(Regex("\\s+"), " ").removePrefix("const ").trim()
        if (normalized.isEmpty()) return null
        val declarationKind = when {
            normalized.startsWith("struct ") -> "struct"
            normalized.startsWith("union ") -> "union"
            normalized.startsWith("enum ") -> "enum"
            else -> ""
        }
        val baseText = normalized
            .removePrefix("struct ")
            .removePrefix("union ")
            .removePrefix("enum ")
            .substringBefore('*')
            .substringBefore('[')
            .trim()
        val base = when (declarationKind) {
            "struct" -> structs[baseText]
            "union" -> unions[baseText]
            "enum" -> enums[baseText]
            else -> {
                val canonicalPrimitiveName = CPrimitiveTypes.canonicalName(baseText)
                structs[baseText] ?: unions[baseText] ?: enums[baseText] ?: aliases[baseText] ?:
                    foreignTypes[baseText] ?: types.firstOrNull {
                        it is PrimitiveType && it.name == (canonicalPrimitiveName ?: baseText)
                    }
            }
        } ?: return null
        val pointerDepth = normalized.count { it == '*' }
        val dimensions = Regex("\\[([^]]*)]").findAll(normalized).map { it.groupValues[1] }.toList()
        var candidate: CType = base
        repeat(pointerDepth) { candidate = PointerType(TypeId(Int.MIN_VALUE), candidate) }
        if (dimensions.isNotEmpty()) candidate = ArrayType(TypeId(Int.MIN_VALUE), candidate, dimensions)
        val canonicalKey = canonicalTypeKey(candidate)
        val canonicalId = canonicalTypeIds[canonicalKey]
            ?: types.firstOrNull { canonicalTypeKey(it) == canonicalKey }?.id
            ?: return null
        val canonicalText = types.firstOrNull { it.id == canonicalId }?.name ?: base.name
        val declaredId = if (pointerDepth == 0 && dimensions.isEmpty()) {
            base.id
        } else {
            types.firstOrNull { canonicalTypeKey(it) == canonicalKey }?.id ?: canonicalId
        }
        return SemanticTypeIdentity(declaredId, canonicalId, canonicalText)
    }

    val resolvedAst: ResolvedAst
        get() = ResolvedAst(program, nodeIds, referenceIndex)
}

data class SemanticResult(
    val model: SemanticModel?,
    val diagnostics: List<Diagnostic>
) {
    val isSuccessful: Boolean
        get() = model != null && diagnostics.none { it.severity == DiagnosticSeverity.ERROR }
}

fun buildDeclarationCatalogue(
    program: AstProgram,
    model: SemanticModel,
    declarationModules: Map<AstDeclaration, String>,
    moduleScopes: Map<String, ScopeId>
): DeclarationCatalogue {
    fun moduleScope(declaration: AstDeclaration): ScopeId =
        moduleScopes[declarationModules[declaration]]
            ?: moduleScopes.values.firstOrNull()
            ?: ScopeId(1)

    fun symbolFor(name: String, origin: Origin, kind: SymbolKind? = null): Symbol? = model.symbols.firstOrNull {
        it.name == name && it.origin == origin && (kind == null || it.kind == kind)
    }

    fun symbolScope(symbol: Symbol?, fallback: ScopeId, scopeKind: ScopeKind): ScopeId = symbol?.id?.let { owner ->
        model.scopes.all.firstOrNull { it.owner == owner && it.kind == scopeKind }?.id
    } ?: fallback

    val entries = mutableListOf<DeclarationCatalogueEntry>()
    fun add(
        name: String,
        kind: String,
        origin: Origin,
        scope: ScopeId,
        symbol: Symbol? = null,
        parameters: List<String> = emptyList(),
        compileTime: Boolean = false
    ) {
        entries += DeclarationCatalogueEntry(name, kind, symbol?.id, scope, origin, parameters, compileTime)
    }

    program.declarations.forEach { declaration ->
        val moduleScope = moduleScope(declaration)
        when (declaration) {
            is AstPackage -> add(declaration.name, "package", declaration.origin, moduleScope)
            is AstAlias -> add(
                declaration.name,
                "alias",
                declaration.origin,
                moduleScope,
                symbolFor(declaration.name, declaration.origin, SymbolKind.ALIAS)
            )
            is AstUnion -> {
                val symbol = symbolFor(declaration.name, declaration.origin, SymbolKind.UNION)
                val typeScope = symbolScope(symbol, moduleScope, ScopeKind.TYPE)
                add(declaration.name, "union", declaration.origin, moduleScope, symbol)
                declaration.fields.forEach { field ->
                    add(
                        field.name,
                        "field",
                        field.origin,
                        typeScope,
                        symbolFor(field.name, field.origin, SymbolKind.FIELD)
                    )
                }
            }
            is AstEnum -> {
                val symbol = symbolFor(declaration.name, declaration.origin, SymbolKind.ENUM)
                add(declaration.name, "enum", declaration.origin, moduleScope, symbol)
                declaration.values.forEach { value ->
                    add(
                        value.name,
                        "enumValue",
                        value.origin,
                        moduleScope,
                        symbolFor(value.name, value.origin, SymbolKind.ENUM_VALUE)
                    )
                }
            }
            is AstStruct -> {
                val symbol = symbolFor(declaration.name, declaration.origin, SymbolKind.STRUCT)
                val typeScope = symbolScope(symbol, moduleScope, ScopeKind.TYPE)
                add(declaration.name, "struct", declaration.origin, moduleScope, symbol)
                declaration.fields.forEach { field ->
                    add(
                        field.name,
                        "field",
                        field.origin,
                        typeScope,
                        symbolFor(field.name, field.origin, SymbolKind.FIELD)
                    )
                }
                declaration.methods.forEach { method ->
                    val methodSymbol = symbolFor(method.name, method.origin, SymbolKind.METHOD)
                    val methodScope = symbolScope(methodSymbol, typeScope, ScopeKind.FUNCTION)
                    add(
                        method.name,
                        "method",
                        method.origin,
                        typeScope,
                        methodSymbol,
                        method.parameters.map { it.name }
                    )
                    method.parameters.forEach { parameter ->
                        add(
                            parameter.name,
                            "parameter",
                            parameter.origin,
                            methodScope,
                            symbolFor(parameter.name, parameter.origin, SymbolKind.PARAMETER)
                        )
                    }
                }
            }
            is AstGlobalVariable -> add(
                declaration.name,
                "global",
                declaration.origin,
                moduleScope,
                symbolFor(declaration.name, declaration.origin, SymbolKind.VARIABLE)
            )
            is AstFunction -> {
                val symbol = symbolFor(declaration.name, declaration.origin, if (declaration.isMethod) SymbolKind.METHOD else SymbolKind.FUNCTION)
                val functionScope = symbolScope(symbol, moduleScope, ScopeKind.FUNCTION)
                add(
                    declaration.name,
                    if (declaration.isMethod) "method" else "function",
                    declaration.origin,
                    moduleScope,
                    symbol,
                    declaration.parameters.map { it.name }
                )
                declaration.parameters.forEach { parameter ->
                    add(
                        parameter.name,
                        "parameter",
                        parameter.origin,
                        functionScope,
                        symbolFor(parameter.name, parameter.origin, SymbolKind.PARAMETER)
                    )
                }
            }
            is AstComptimeFunction -> add(
                declaration.name,
                "comptime",
                declaration.origin,
                moduleScope,
                parameters = declaration.parameters,
                compileTime = true
            )
            is AstCpxInvocation -> add(
                declaration.name,
                "cpxInvocation",
                declaration.origin,
                moduleScope,
                parameters = declaration.arguments,
                compileTime = true
            )
            is AstTrait -> declaration.methods.forEach { method ->
                val symbol = symbolFor(method.name, method.origin, SymbolKind.METHOD)
                val methodScope = symbolScope(symbol, moduleScope, ScopeKind.FUNCTION)
                add(
                    method.name,
                    "extensionMethod",
                    method.origin,
                    moduleScope,
                    symbol,
                    method.parameters.map { it.name }
                )
                method.parameters.forEach { parameter ->
                    add(
                        parameter.name,
                        "parameter",
                        parameter.origin,
                        methodScope,
                        symbolFor(parameter.name, parameter.origin, SymbolKind.PARAMETER)
                    )
                }
            }
            is AstImport -> add(declaration.alias ?: declaration.module, "import", declaration.origin, moduleScope)
        }
    }
    return DeclarationCatalogue(entries)
}

class SemanticAnalyzer(
    private val headerImportService: CHeaderImportService = CHeaderImportService()
) {
    private var nextSymbolId = generateSequence(1) { it + 1 }.iterator()
    private var nextTypeId = generateSequence(1) { it + 1 }.iterator()
    private var activeTypeEnvironment: Map<String, CType> = emptyMap()
    private var activeMethodRegistry: MethodRegistry = MethodRegistry.from(emptyList())
    private var activeModuleName: String = "<main>"
    private var activeVisibleExtensionModules: Set<String> = emptySet()
    private var resolvedMethodCalls: MutableMap<AstCall, ResolvedMethodCall> = IdentityHashMap()
    private val incompleteForeignTypes = mutableSetOf<String>()

    fun analyze(
        program: AstProgram,
        knownModules: Set<String> = emptySet(),
        foreignSources: List<CSourceUnit> = emptyList(),
        targetFeatures: Set<String> = emptySet(),
        targetName: String = "selected target"
    ): SemanticResult {
        nextSymbolId = generateSequence(1) { it + 1 }.iterator()
        nextTypeId = generateSequence(1) { it + 1 }.iterator()
        activeTypeEnvironment = emptyMap()
        activeMethodRegistry = MethodRegistry.from(emptyList())
        activeModuleName = "<main>"
        activeVisibleExtensionModules = emptySet()
        resolvedMethodCalls = IdentityHashMap()
        incompleteForeignTypes.clear()
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
        val aliasDeclarationsByModule = linkedMapOf<String, MutableMap<String, AstAlias>>()
        val sourceTypeDeclarationsByModule = linkedMapOf<String, MutableMap<String, AstDeclaration>>()
        val resolvingAliases = mutableSetOf<String>()
        val functions = linkedMapOf<String, FunctionSymbol>()
        val methods = linkedMapOf<String, Map<String, MethodSymbol>>()
        val globals = linkedMapOf<String, Symbol>()
        val foreignGlobals = linkedMapOf<String, Symbol>()
        val foreignSourceFunctions = linkedMapOf<String, FunctionSymbol>()
        val moduleFunctions = linkedMapOf<String, LinkedHashMap<String, FunctionSymbol>>()
        val foreignDeclarationsByModule = foreignSources.associate { sourceUnit ->
            sourceUnit.moduleName to headerImportService.sourceDeclarations(
                sourceUnit.source.text,
                sourceUnit.macros,
                sourceUnit.sourceLineOrigins
            )
        }
        val declarationModules = IdentityHashMap<AstDeclaration, String>()
        val defaultModule = "<main>"
        if (program.modules.isEmpty()) {
            program.declarations.forEach { declarationModules[it] = defaultModule }
        } else {
            program.modules.forEach { module ->
                module.declarations.forEach { declarationModules[it] = module.name }
            }
        }
        program.declarations.forEach { declaration ->
            val moduleName = declarationModules[declaration] ?: defaultModule
            when (declaration) {
                is AstAlias -> {
                    aliasDeclarationsByModule.getOrPut(moduleName) { linkedMapOf() }
                        .putIfAbsent(declaration.name, declaration)
                    sourceTypeDeclarationsByModule.getOrPut(moduleName) { linkedMapOf() }
                        .putIfAbsent(declaration.name, declaration)
                }
                is AstStruct -> {
                    sourceTypeDeclarationsByModule.getOrPut(moduleName) { linkedMapOf() }
                        .putIfAbsent(declaration.name, declaration)
                }
                is AstUnion -> {
                    sourceTypeDeclarationsByModule.getOrPut(moduleName) { linkedMapOf() }
                        .putIfAbsent(declaration.name, declaration)
                }
                is AstEnum -> {
                    sourceTypeDeclarationsByModule.getOrPut(moduleName) { linkedMapOf() }
                        .putIfAbsent(declaration.name, declaration)
                }
                else -> Unit
            }
        }
        val moduleTypeBindingRefs = linkedMapOf<String, MutableMap<String, SourceTypeBindingRef>>()
        val moduleDeclarationLists = if (program.modules.isEmpty()) {
            mapOf(defaultModule to program.declarations)
        } else {
            program.modules.associate { it.name to it.declarations }
        }
        val sourceExtensionNamesByModule = moduleDeclarationLists.mapValues { (_, declarations) ->
            declarations.filterIsInstance<AstTrait>()
                .filter(AstTrait::isPublic)
                .flatMap { trait -> trait.methods.map(AstFunction::name) }
                .toSet()
        }
        val moduleExtensionImports = moduleDeclarationLists.mapValues { (_, declarations) ->
            declarations.filterIsInstance<AstImport>()
                .asSequence()
                .filterNot { it.module.startsWith("c.") }
                .mapNotNull { import -> moduleTargetNames(import.module).firstOrNull { it in moduleDeclarationLists } }
                .toSet()
        }
        val fixedWidthAliasNames = setOf("i128", "u128")
        val fixedWidthBaseAliasNames = setOf(
            "i8", "i16", "i32", "i64", "u8", "u16", "u32", "u64"
        )
        val fixedWidthModules = moduleDeclarationLists.filterValues { declarations ->
            declarations.filterIsInstance<AstPackage>().any { it.name == "std" } &&
                declarations.filterIsInstance<AstAlias>().mapTo(mutableSetOf()) { it.name }
                    .containsAll(fixedWidthBaseAliasNames)
        }.keys
        val supportsInt128 = "int128" in targetFeatures
        val supportsC17Complex = "c17_complex" in targetFeatures

        fun isUnavailableFixedWidthAlias(moduleName: String, aliasName: String): Boolean =
            !supportsInt128 && moduleName in fixedWidthModules && aliasName in fixedWidthAliasNames

        val moduleTypeAliases = linkedMapOf<String, MutableMap<String, String>>()
        moduleDeclarationLists.forEach { (moduleName, declarations) ->
            declarations.filterIsInstance<AstImport>().forEach { import ->
                val alias = import.alias ?: return@forEach
                if (import.names.isNotEmpty()) return@forEach
                val targetModule = moduleTargetNames(import.module)
                    .firstOrNull { it in moduleDeclarationLists }
                    ?: return@forEach
                val localTypeNames = sourceTypeDeclarationsByModule[moduleName].orEmpty().keys
                val localFunctionNames = declarations.filterIsInstance<AstFunction>().mapTo(mutableSetOf()) { it.name }
                val localValueNames = declarations.filterIsInstance<AstGlobalVariable>().mapTo(mutableSetOf()) { it.name } +
                    declarations.filterIsInstance<AstEnum>().flatMap { enum -> enum.values.map { it.name } }
                val aliases = moduleTypeAliases.getOrPut(moduleName) { linkedMapOf() }
                if (alias in localTypeNames || alias in localFunctionNames || alias in localValueNames || alias in aliases) {
                    diagnostics.error(
                        "module alias '$alias' conflicts in module '$moduleName'",
                        rangeOf(import.origin),
                        "SEM405"
                    )
                } else {
                    aliases[alias] = targetModule
                }
            }
        }
        moduleDeclarationLists.forEach { (moduleName, declarations) ->
            declarations.filterIsInstance<AstImport>().forEach { import ->
                if (import.module.startsWith("c.") || import.names.isEmpty()) return@forEach
                val targetNames = moduleTargetNames(import.module)
                val targetModule = targetNames.firstOrNull { it in sourceTypeDeclarationsByModule }
                    ?: return@forEach
                val targetTypes = sourceTypeDeclarationsByModule[targetModule].orEmpty()
                val localFunctionNames = declarations.filterIsInstance<AstFunction>().mapTo(mutableSetOf()) { it.name }
                val localValueNames = declarations.filterIsInstance<AstGlobalVariable>().mapTo(mutableSetOf()) { it.name } +
                    declarations.filterIsInstance<AstEnum>().flatMap { enum -> enum.values.map { it.name } }
                import.names.forEach { importedName ->
                    val declaration = targetTypes[importedName] ?: return@forEach
                    val localName = import.nameAliases[importedName] ?: importedName
                    val localTypeExists = sourceTypeDeclarationsByModule[moduleName]?.containsKey(localName) == true ||
                        moduleTypeAliases[moduleName]?.containsKey(localName) == true ||
                        localFunctionNames.contains(localName) || localValueNames.contains(localName)
                    val bindings = moduleTypeBindingRefs.getOrPut(moduleName) { linkedMapOf() }
                    if (localTypeExists || localName in bindings) {
                        diagnostics.error(
                            "imported type name '$localName' conflicts in module '$moduleName'",
                            rangeOf(import.origin),
                            "SEM405"
                        )
                    } else {
                        if (!declaration.isPublic) {
                            diagnostics.error(
                                "imported type '$importedName' is not public in module '${import.module}'",
                                rangeOf(import.origin),
                                "SEM406"
                            )
                        }
                        bindings[localName] = SourceTypeBindingRef(importedName, targetModule)
                    }
                }
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
        val moduleScopes = linkedMapOf<String, ScopeId>()
        val typeScopes = linkedMapOf<SymbolId, ScopeId>()
        val functionScopes = linkedMapOf<SymbolId, ScopeId>()

        fun moduleScope(name: String): ScopeId = moduleScopes.getOrPut(name) {
            scopes.create(ScopeKind.MODULE, rootScope)
        }

        fun defineBinding(moduleName: String?, name: String, symbol: SymbolId) {
            scopes.define(rootScope, name, symbol)
            moduleName?.let { scopes.define(moduleScope(it), name, symbol) }
        }

        fun defineTypeBinding(moduleName: String, name: String, symbol: SymbolId) {
            scopes.define(moduleScope(moduleName), name, symbol)
        }

        (declarationModules.values + defaultModule).distinct().forEach(::moduleScope)
        val primitiveTypes = linkedMapOf<String, PrimitiveType>()
        val expressionTypes = linkedMapOf<AstExpression, CType>()

        fun primitive(name: String): PrimitiveType {
            val canonicalName = CPrimitiveTypes.canonicalName(name) ?: name
            return primitiveTypes.getOrPut(canonicalName) {
                PrimitiveType(TypeId(nextTypeId.next()), canonicalName).also(types::add)
            }
        }

        CPrimitiveTypes.types
            .filter { supportsInt128 || it.rank != CIntegerRank.INT128 }
            .forEach { primitive(it.name) }

        fun abiOf(attributes: Map<String, String>, origin: Origin): AbiKind {
            val value = attributes["abi"]?.lowercase() ?: return AbiKind.C
            val abi = when (value) {
                "c" -> AbiKind.C
                "system" -> AbiKind.SYSTEM
                "cplus" -> AbiKind.CPLUS
                "intrinsic" -> AbiKind.INTRINSIC
                "runtime" -> AbiKind.RUNTIME
                else -> {
                    diagnostics.error("unsupported ABI '$value'", rangeOf(origin), "SEM314")
                    AbiKind.C
                }
            }
            if (attributes.keys.any { it !in setOf("abi", "library", "link_name", "export_name", "noreturn", "weak") }) {
                diagnostics.error("unsupported function attribute", rangeOf(origin), "SEM315")
            }
            return abi
        }

        lateinit var resolveAlias: (String, String) -> AliasType?

        fun sourceType(reference: SourceTypeBindingRef): CType? = when (
            val declaration = sourceTypeDeclarationsByModule[reference.ownerModule]?.get(reference.declarationName)
        ) {
            is AstAlias -> if (isUnavailableFixedWidthAlias(reference.ownerModule, reference.declarationName)) {
                null
            } else {
                resolveAlias(reference.declarationName, reference.ownerModule)
            }
            is AstStruct -> structs[reference.declarationName]
            is AstUnion -> unions[reference.declarationName]
            is AstEnum -> enums[reference.declarationName]
            else -> null
        }

        fun resolve(
            reference: AstTypeRef,
            moduleName: String = defaultModule,
            dimensions: List<String> = emptyList()
        ): CType {
            val qualifiedParts = reference.name.split('.', limit = 2)
            val qualifiedBinding = if (qualifiedParts.size == 2) {
                moduleTypeAliases[moduleName]?.get(qualifiedParts[0])
                    ?.let { ownerModule ->
                        sourceTypeDeclarationsByModule[ownerModule]?.get(qualifiedParts[1])
                            ?.let { SourceTypeBindingRef(qualifiedParts[1], ownerModule) }
                    }
            } else null
            val importedType = qualifiedBinding ?: moduleTypeBindingRefs[moduleName]?.get(reference.name)
            val baseReference = reference.copy(pointerDepth = 0, functionParameters = null, functionPointerDepth = 0)
            val canonicalPrimitive = CPrimitiveTypes.canonicalName(reference.name)
            val unavailablePrimitiveInt128 = !supportsInt128 &&
                CPrimitiveTypes.typeInfo(canonicalPrimitive ?: "")?.rank == CIntegerRank.INT128
            val unavailablePrimitiveComplex = !supportsC17Complex &&
                CPrimitiveTypes.typeInfo(canonicalPrimitive ?: "")?.kind == CPrimitiveKind.COMPLEX
            val unavailableImportedAlias = importedType?.let {
                isUnavailableFixedWidthAlias(it.ownerModule, it.declarationName)
            } == true
            val unavailableUnimportedAlias = !supportsInt128 &&
                reference.name in fixedWidthAliasNames &&
                (moduleName in fixedWidthModules || (
                    importedType == null &&
                        reference.name !in sourceTypeDeclarationsByModule[moduleName].orEmpty() &&
                        fixedWidthModules.isNotEmpty()
                    ))
            var resolved = when {
                unavailablePrimitiveInt128 || unavailableImportedAlias || unavailableUnimportedAlias -> {
                    diagnostics.error(
                        "128-bit integer type '${reference.name}' is unavailable for target '$targetName'",
                        rangeOf(reference.origin),
                        "SEM411"
                    )
                    UnknownType(TypeId(-1))
                }
                unavailablePrimitiveComplex -> {
                    diagnostics.error(
                        "C17 complex type '${reference.name}' is unavailable for target '$targetName'",
                        rangeOf(reference.origin),
                        "SEM412"
                    )
                    UnknownType(TypeId(-1))
                }
                qualifiedParts.size == 2 -> importedType?.let(::sourceType) ?: UnknownType(TypeId(-1))
                importedType != null -> sourceType(importedType) ?: UnknownType(TypeId(-1))
                else -> {
                    if (reference.name in aliasDeclarationsByModule[moduleName].orEmpty() && reference.name !in aliases) {
                        resolveAlias(reference.name, moduleName)
                    }
                    resolveType(baseReference, structs, unions, enums, aliases, foreignTypes, ::primitive, diagnostics)
                }
            }
            val incompleteForeignBase = canonicalType(resolved) as? ForeignType
            if (reference.pointerDepth == 0 && incompleteForeignBase != null && incompleteForeignBase.name in incompleteForeignTypes) {
                diagnostics.error(
                    "incomplete C type '${incompleteForeignBase.name}' cannot be used by value",
                    rangeOf(reference.origin),
                    "SEM414"
                )
            }
            repeat(reference.pointerDepth) {
                resolved = PointerType(TypeId(nextTypeId.next()), resolved)
            }
            reference.functionParameters?.let { parameters ->
                val parameterTypes = parameters.map { parameter ->
                    resolve(parameter.type, moduleName, parameter.arrayDimensions)
                }
                var functionType: CType = FunctionType(
                    TypeId(-1),
                    resolved,
                    parameterTypes,
                    reference.functionVariadic
                )
                repeat(reference.functionPointerDepth) {
                    functionType = PointerType(TypeId(nextTypeId.next()), functionType)
                }
                resolved = functionType
            }
            if (dimensions.isNotEmpty()) {
                resolved = ArrayType(TypeId(nextTypeId.next()), resolved, dimensions)
            }
            return resolved.also { type ->
                if (type !is UnknownType && types.none { it.id == type.id }) types += type
            }
        }

        fun newSymbol(
            name: String,
            kind: SymbolKind,
            type: CType,
            origin: Origin,
            moduleName: String? = null,
            visibility: Visibility = Visibility.PRIVATE,
            externalName: String? = null,
            abi: AbiKind = AbiKind.C,
            constantExpression: String? = null,
            externalSource: java.nio.file.Path? = null,
            externalLine: Int? = null
        ): Symbol = Symbol(
            SymbolId(nextSymbolId.next()),
            name,
            kind,
            type,
            origin,
            moduleName = moduleName,
            visibility = visibility,
            externalName = externalName,
            abi = abi,
            constantExpression = constantExpression,
            externalSource = externalSource,
            externalLine = externalLine,
            qualifiedName = QualifiedName(
                listOfNotNull(moduleName?.let { modulePackages[it] }, moduleName, name).joinToString("::")
            )
        ).also(symbols::add)

        fun registerForeignType(
            name: String,
            moduleName: String,
            origin: Origin,
            underlyingType: CType? = null,
            externalSource: java.nio.file.Path? = null,
            externalLine: Int? = null
        ) {
            foreignTypes[name]?.let { existing ->
                if (existing.underlyingType == null && underlyingType != null) existing.underlyingType = underlyingType
                return
            }
            val type = ForeignType(TypeId(nextTypeId.next()), name, underlyingType = underlyingType, moduleName = moduleName)
            foreignTypes[name] = type
            types += type
            val symbol = newSymbol(
                name,
                SymbolKind.FOREIGN_TYPE,
                type,
                origin,
                moduleName,
                Visibility.PUBLIC,
                externalSource = externalSource,
                externalLine = externalLine
            )
            defineBinding(moduleName, name, symbol.id)
        }

        fun registerForeignConstant(
            name: String,
            moduleName: String,
            origin: Origin,
            kind: SymbolKind,
            typeName: String = "int",
            constantExpression: String? = null,
            externalSource: java.nio.file.Path? = null,
            externalLine: Int? = null
        ) {
            if (foreignGlobals.containsKey(name)) return
            val normalizedTypeName = typeName.removePrefix("const ").trim()
            val pointer = normalizedTypeName.endsWith('*')
            val constantType = if (pointer) {
                PointerType(TypeId(nextTypeId.next()), primitive(normalizedTypeName.removeSuffix("*").trim()))
            } else {
                primitive(normalizedTypeName)
            }
            val symbol = newSymbol(
                name,
                kind,
                constantType,
                origin,
                moduleName,
                Visibility.PUBLIC,
                name,
                constantExpression = constantExpression,
                externalSource = externalSource,
                externalLine = externalLine
            )
            globals[name] = symbol
            foreignGlobals[name] = symbol
            defineBinding(moduleName, name, symbol.id)
        }

        fun foreignTypeModule(name: String): String? = foreignDeclarationsByModule.entries
            .firstOrNull { (_, declarations) -> declarations[name]?.kind == ForeignDeclarationKind.TYPE }
            ?.key

        fun foreignTypeFromName(
            typeName: String,
            moduleName: String,
            origin: Origin,
            allowIncomplete: Boolean = false
        ): CType {
            val normalized = typeName.trim().replace(Regex("\\s+"), " ")
            val pointerDepth = normalized.count { it == '*' }
            val writtenBaseName = normalized
                .replace("*", " ")
                .split(' ')
                .filter { it.isNotEmpty() && it !in setOf("const", "volatile", "restrict") }
                .joinToString(" ")
            val taggedAggregate = Regex("^(struct|union|enum)\\s+([A-Za-z_][A-Za-z0-9_]*)$").matchEntire(writtenBaseName)
            val baseName = taggedAggregate?.groupValues?.get(2) ?: writtenBaseName
            var type = when {
                baseName == "__builtin_va_list" -> PointerType(TypeId(nextTypeId.next()), primitive("void"))
                taggedAggregate != null -> {
                    foreignTypes[baseName] ?: run {
                        registerForeignType(baseName, moduleName, origin)
                        foreignTypes.getValue(baseName)
                    }
                }
                foreignTypeModule(baseName) != null -> {
                    val ownerModule = foreignTypeModule(baseName)!!
                    val declaration = foreignDeclarationsByModule[ownerModule]?.get(baseName)
                    val underlying = if (baseName in CPrimitiveTypes.standardIntegerTypedefNames) {
                        primitive(baseName)
                    } else {
                        declaration?.typeName?.let { foreignTypeFromName(it, ownerModule, origin) }
                    }
                    registerForeignType(baseName, ownerModule, origin, underlying)
                    foreignTypes.getValue(baseName)
                }
                foreignTypes[baseName] != null -> foreignTypes.getValue(baseName)
                CPrimitiveTypes.typeInfo(baseName) != null -> {
                    if (!supportsInt128 && CPrimitiveTypes.typeInfo(baseName)?.rank == CIntegerRank.INT128) {
                        diagnostics.error(
                            "128-bit integer type '$baseName' is unavailable for target '$targetName'",
                            rangeOf(origin),
                            "SEM411"
                        )
                        UnknownType(TypeId(-1))
                    } else if (!supportsC17Complex && CPrimitiveTypes.isComplex(baseName)) {
                        diagnostics.error(
                            "C17 complex type '$baseName' is unavailable for target '$targetName'",
                            rangeOf(origin),
                            "SEM412"
                        )
                        UnknownType(TypeId(-1))
                    } else {
                        primitive(baseName)
                    }
                }
                baseName in CPrimitiveTypes.standardTypedefNames -> primitive(baseName)
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

        fun registerHeaderDeclaration(
            declaration: CHeaderDeclaration,
            moduleName: String,
            origin: Origin,
            exposeGlobally: Boolean = false
        ): Boolean {
            declaration.unsupportedReason?.let { reason ->
                diagnostics.error(reason, rangeOf(origin), "SEM413")
                return false
            }
            when (declaration.kind) {
                ForeignDeclarationKind.TYPE -> {
                    val isAggregate = declaration.aggregateKind == CHeaderAggregateKind.STRUCT ||
                        declaration.aggregateKind == CHeaderAggregateKind.UNION ||
                        declaration.aggregateKind == CHeaderAggregateKind.ENUM
                    if (isAggregate && !declaration.aggregateComplete) {
                        incompleteForeignTypes += declaration.name
                            registerForeignType(
                                declaration.name,
                                moduleName,
                                origin,
                                externalSource = declaration.externalSource,
                                externalLine = declaration.externalLine
                            )
                    } else {
                        incompleteForeignTypes -= declaration.name
                        registerForeignType(
                            declaration.name,
                            moduleName,
                            origin,
                            underlyingType = if (isAggregate) {
                                if (declaration.aggregateKind == CHeaderAggregateKind.STRUCT) {
                                    val owner = ForeignType(TypeId(nextTypeId.next()), declaration.name, moduleName = moduleName)
                                    val structure = StructType(owner.id, declaration.name, mutableListOf(), moduleName = moduleName)
                                    declaration.fields.forEach { field ->
                                        val fieldType = foreignTypeFromName(field.typeName, moduleName, origin)
                                        val fieldSymbol = newSymbol(
                                            field.name,
                                            SymbolKind.FIELD,
                                            fieldType,
                                            origin,
                                            moduleName,
                                            Visibility.PUBLIC
                                        )
                                        structure.fields = structure.fields + FieldSymbol(fieldSymbol, structure)
                                    }
                                    types += structure
                                    structure
                                } else if (declaration.aggregateKind == CHeaderAggregateKind.UNION) {
                                    val owner = ForeignType(TypeId(nextTypeId.next()), declaration.name, moduleName = moduleName)
                                    val union = UnionType(owner.id, declaration.name, mutableListOf(), moduleName = moduleName)
                                    declaration.fields.forEach { field ->
                                        val fieldType = foreignTypeFromName(field.typeName, moduleName, origin)
                                        val fieldSymbol = newSymbol(
                                            field.name,
                                            SymbolKind.FIELD,
                                            fieldType,
                                            origin,
                                            moduleName,
                                            Visibility.PUBLIC
                                        )
                                        union.fields = union.fields + FieldSymbol(fieldSymbol, union)
                                    }
                                    types += union
                                    union
                                } else {
                                    primitive("int")
                                }
                            } else if (
                                declaration.typeName?.let { typeName ->
                                    Regex("^(?:struct|union|enum)\\s+${Regex.escape(declaration.name)}$")
                                        .matches(typeName.trim())
                                } == true
                            ) {
                                null
                            } else if (declaration.functionPointerType != null) {
                                val callback = declaration.functionPointerType
                                val returnType = foreignTypeFromName(callback.returnType, moduleName, origin)
                                val parameterTypes = callback.parameterTypes.map { parameterType ->
                                    foreignTypeFromName(parameterType, moduleName, origin)
                                }
                                PointerType(
                                    TypeId(nextTypeId.next()),
                                    FunctionType(
                                        TypeId(nextTypeId.next()),
                                        returnType,
                                        parameterTypes,
                                        callback.isVariadic
                                    ).also(types::add)
                                )
                            } else if (declaration.name in CPrimitiveTypes.standardIntegerTypedefNames) {
                                primitive(declaration.name)
                            } else {
                                declaration.typeName?.let { foreignTypeFromName(it, moduleName, origin, allowIncomplete = true) }
                            },
                            externalSource = declaration.externalSource,
                            externalLine = declaration.externalLine
                        )
                    }
                }
                ForeignDeclarationKind.FUNCTION -> {
                    val existing = functions[declaration.name]
                    if (existing?.symbol?.kind == SymbolKind.FOREIGN) {
                        if (exposeGlobally) foreignSourceFunctions.putIfAbsent(declaration.name, existing)
                        return true
                    }
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
                        declaration.name,
                        externalSource = declaration.externalSource,
                        externalLine = declaration.externalLine
                    )
                    val function = FunctionSymbol(symbol, returnType, parameterSymbols, declaration.isVariadic, signature)
                    functions[declaration.name] = function
                    moduleFunctions.getOrPut(moduleName) { linkedMapOf() }[declaration.name] = function
                    if (exposeGlobally) foreignSourceFunctions[declaration.name] = function
                    defineBinding(moduleName, declaration.name, symbol.id)
                }
                ForeignDeclarationKind.GLOBAL -> {
                    val type = foreignTypeFromName(declaration.typeName ?: "int", moduleName, origin)
                    val symbol = newSymbol(
                        declaration.name,
                        SymbolKind.FOREIGN_GLOBAL,
                        type,
                        origin,
                        moduleName,
                        Visibility.PUBLIC,
                        declaration.name,
                        externalSource = declaration.externalSource,
                        externalLine = declaration.externalLine
                    )
                    globals[declaration.name] = symbol
                    foreignGlobals[declaration.name] = symbol
                    defineBinding(moduleName, declaration.name, symbol.id)
                }
                ForeignDeclarationKind.ENUM_VALUE -> registerForeignConstant(
                    declaration.name,
                    moduleName,
                    origin,
                    SymbolKind.FOREIGN_ENUM_VALUE,
                    declaration.typeName ?: "int",
                    declaration.constantExpression,
                    declaration.externalSource,
                    declaration.externalLine
                )
            }
            return true
        }

        fun declarationVisibility(declaration: AstDeclaration): Visibility =
            if (declaration.isPublic) Visibility.PUBLIC else Visibility.PRIVATE

        resolveAlias = { name, ownerModule ->
            aliases[name] ?: run {
                val declaration = aliasDeclarationsByModule[ownerModule]?.get(name)
                    ?: aliasDeclarations[name]?.singleOrNull()
                    ?: return@run null
                val resolvingKey = "$ownerModule::$name"
                if (!resolvingAliases.add(resolvingKey)) {
                    diagnostics.error("cyclic type alias '$name'", rangeOf(declaration.origin), "SEM110")
                    return@run null
                }
                val target = resolve(declaration.target, ownerModule, declaration.arrayDimensions)
                resolvingAliases.remove(resolvingKey)
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
                    defineTypeBinding(declarationModules[declaration] ?: defaultModule, symbol.name, symbol.id)
                }
            }
        }

        foreignSources.forEach { sourceUnit ->
            foreignDeclarationsByModule[sourceUnit.moduleName].orEmpty().values.forEach { declaration ->
                val origin = declaration.sourceRange?.let { range ->
                    Origin.Direct(SourceRange(sourceUnit.source.id, range.first, range.last + 1))
                } ?: Origin.Synthetic(null)
                registerHeaderDeclaration(
                    declaration,
                    sourceUnit.moduleName,
                    origin,
                    exposeGlobally = !sourceUnit.moduleName.startsWith("c.") || sourceUnit.moduleName.startsWith("c.source.")
                )
            }
        }

        // Register foreign header imports before source aliases, function
        // signatures, and aggregate fields are resolved. A consumer module can
        // appear before the provider module that imports the C types underlying
        // its exported source aliases.
        program.declarations.filterIsInstance<AstImport>().forEach { declaration ->
            if (declaration.module.startsWith("c.") && declaration.module !in foreignDeclarationsByModule) {
                headerImportService.unsupportedPreprocessorLines(declaration.module).forEach { line ->
                    diagnostics.error(
                        "unsupported preprocessor construct in ${declaration.module}: $line",
                        rangeOf(declaration.origin),
                        "SEM409"
                    )
                }
            }
            if (declaration.module.startsWith("c.")) {
                val declarations = foreignDeclarationsByModule[declaration.module]
                    ?: headerImportService.declarations(declaration.module)
                declaration.names.forEach { name ->
                    declarations[name]?.let { registerHeaderDeclaration(it, declaration.module, declaration.origin) }
                        ?: diagnostics.error("unsupported imported C symbol '$name' from ${declaration.module}", rangeOf(declaration.origin), "SEM408")
                }
            }
        }

        // Catalogue aggregate shells before resolving aliases, signatures, or fields so
        // references are independent of source-file declaration order.
        program.declarations.forEach { declaration ->
            val moduleName = declarationModules[declaration] ?: defaultModule
            when (declaration) {
                is AstUnion -> if (unions.containsKey(declaration.name)) {
                    diagnostics.error("duplicate union '${declaration.name}'", rangeOf(declaration.origin), "SEM005")
                } else {
                    val type = UnionType(TypeId(nextTypeId.next()), declaration.name, emptyList(), moduleName)
                    unions[declaration.name] = type
                    types += type
                    val symbol = newSymbol(declaration.name, SymbolKind.UNION, type, declaration.origin, moduleName, declarationVisibility(declaration))
                    defineTypeBinding(moduleName, symbol.name, symbol.id)
                    typeScopes[symbol.id] = scopes.create(ScopeKind.TYPE, moduleScope(moduleName), symbol.id)
                }
                is AstEnum -> if (enums.containsKey(declaration.name)) {
                    diagnostics.error("duplicate enum '${declaration.name}'", rangeOf(declaration.origin), "SEM006")
                } else {
                    val type = EnumType(TypeId(nextTypeId.next()), declaration.name, declaration.values.map { it.name }, moduleName)
                    enums[declaration.name] = type
                    types += type
                    val symbol = newSymbol(declaration.name, SymbolKind.ENUM, type, declaration.origin, moduleName, declarationVisibility(declaration))
                    defineTypeBinding(moduleName, symbol.name, symbol.id)
                    typeScopes[symbol.id] = scopes.create(ScopeKind.TYPE, moduleScope(moduleName), symbol.id)
                }
                is AstStruct -> if (structs.containsKey(declaration.name)) {
                    diagnostics.error("duplicate structure '${declaration.name}'", rangeOf(declaration.origin), "SEM001")
                } else {
                    val type = StructType(TypeId(nextTypeId.next()), declaration.name, emptyList(), moduleName = moduleName)
                    structs[declaration.name] = type
                    types += type
                    val symbol = newSymbol(declaration.name, SymbolKind.STRUCT, type, declaration.origin, moduleName, declarationVisibility(declaration))
                    defineTypeBinding(moduleName, symbol.name, symbol.id)
                    typeScopes[symbol.id] = scopes.create(ScopeKind.TYPE, moduleScope(moduleName), symbol.id)
                }
                else -> Unit
            }
        }

        program.declarations.forEach { declaration ->
            val moduleName = declarationModules[declaration] ?: defaultModule
            when (declaration) {
                is AstPackage -> Unit
                is AstAlias -> {
                    if (aliasDeclarations[declaration.name]?.singleOrNull() !== declaration) {
                        diagnostics.error("duplicate type alias '${declaration.name}'", rangeOf(declaration.origin), "SEM008")
                    } else if (isUnavailableFixedWidthAlias(moduleName, declaration.name)) {
                        // Keep the source declarations discoverable for import diagnostics, but do
                        // not materialize aliases whose representation is unavailable on this target.
                    } else {
                        resolveAlias(declaration.name, moduleName)
                    }
                }
                is AstUnion, is AstEnum, is AstStruct -> Unit
                is AstFunction -> {
                    val existing = functions[declaration.name]
                    if (existing != null && !(existing.symbol.kind == SymbolKind.FOREIGN && declaration.body == null)) {
                        diagnostics.error("duplicate function '${declaration.name}'", rangeOf(declaration.origin), "SEM002")
                    } else if (existing == null) {
                        val abi = abiOf(declaration.attributes, declaration.origin)
                        val returnType = resolve(declaration.returnType, moduleName)
                        val parameterSymbols = declaration.parameters.map { parameter ->
                            val type = resolve(parameter.type, moduleName, parameter.arrayDimensions)
                            newSymbol(parameter.name, SymbolKind.PARAMETER, type, parameter.origin, moduleName)
                        }
                        val signature = FunctionType(
                            TypeId(nextTypeId.next()),
                            returnType,
                            parameterSymbols.map { it.type },
                            abi = abi
                        ).also(types::add)
                        val functionSymbol = newSymbol(
                            declaration.name,
                            SymbolKind.FUNCTION,
                            signature,
                            declaration.origin,
                            moduleName,
                            if (declaration.attributes.containsKey("export_name")) Visibility.PUBLIC else declarationVisibility(declaration),
                            declaration.attributes["link_name"] ?: declaration.attributes["export_name"],
                            abi
                        )
                        val function = FunctionSymbol(functionSymbol, returnType, parameterSymbols, signature = signature, abi = abi)
                        functions[declaration.name] = function
                        moduleFunctions.getOrPut(moduleName) { linkedMapOf() }[declaration.name] = function
                        val functionScope = scopes.create(ScopeKind.FUNCTION, moduleScope(moduleName), functionSymbol.id)
                        functionScopes[functionSymbol.id] = functionScope
                        defineBinding(moduleName, functionSymbol.name, functionSymbol.id)
                        parameterSymbols.forEach { scopes.define(functionScope, it.name, it.id) }
                    }
                }
                is AstGlobalVariable -> {
                    if (globals.containsKey(declaration.name)) {
                        diagnostics.error("duplicate global '${declaration.name}'", rangeOf(declaration.origin), "SEM003")
                    } else {
                        val type = resolve(declaration.type, moduleName, declaration.arrayDimensions)
                        val symbol = newSymbol(declaration.name, SymbolKind.VARIABLE, type, declaration.origin, moduleName, declarationVisibility(declaration))
                        globals[declaration.name] = symbol
                        defineBinding(moduleName, symbol.name, symbol.id)
                    }
                }
                is AstImport -> Unit
                is AstTrait -> Unit
                is AstComptimeFunction, is AstCpxInvocation -> Unit
            }
        }

        program.declarations.filterIsInstance<AstStruct>().forEach { declaration ->
            val struct = structs[declaration.name] ?: return@forEach
            val moduleName = declarationModules[declaration] ?: defaultModule
            val ownerSymbol = symbols.firstOrNull {
                it.kind == SymbolKind.STRUCT && it.name == declaration.name && it.moduleName == moduleName
            }
            val ownerScope = ownerSymbol?.id?.let(typeScopes::get)
            val fields = declaration.fields.map { field ->
                val type = resolve(field.type, moduleName, field.arrayDimensions)
                val symbol = newSymbol(field.name, SymbolKind.FIELD, type, field.origin, moduleName)
                ownerScope?.let { scopes.define(it, field.name, symbol.id) }
                FieldSymbol(symbol, struct)
            }
            val methodSymbols = linkedMapOf<String, MethodSymbol>()
            declaration.methods.forEach { method ->
                if (method.name in methodSymbols) {
                    diagnostics.error(
                        "duplicate method '${method.name}' for '${declaration.name}'",
                        rangeOf(method.origin),
                        "SEM416"
                    )
                    return@forEach
                }
                val abi = abiOf(method.attributes, method.origin)
                val returnType = resolve(method.returnType, moduleName)
                val parameterSymbols = method.parameters.filterNot { it.isReceiver }.map { parameter ->
                    val parameterType = resolve(parameter.type, moduleName, parameter.arrayDimensions)
                    newSymbol(parameter.name, SymbolKind.PARAMETER, parameterType, parameter.origin, moduleName)
                }
                val receiverKind = if (method.parameters.any { it.isReceiver }) ReceiverKind.INSTANCE else ReceiverKind.STATIC
                val signature = FunctionType(
                    TypeId(nextTypeId.next()),
                    returnType,
                    parameterSymbols.map { it.type },
                    abi = abi
                ).also(types::add)
                val methodSymbol = newSymbol(method.name, SymbolKind.METHOD, signature, method.origin, moduleName, externalName = method.attributes["link_name"] ?: method.attributes["export_name"], abi = abi)
                ownerScope?.let { scopes.define(it, method.name, methodSymbol.id) }
                val methodScope = scopes.create(
                    ScopeKind.FUNCTION,
                    ownerScope ?: moduleScope(moduleName),
                    methodSymbol.id
                )
                functionScopes[methodSymbol.id] = methodScope
                parameterSymbols.forEach { parameter -> scopes.define(methodScope, parameter.name, parameter.id) }
                val receiver = method.parameters.firstOrNull { it.isReceiver }
                val receiverType = if (receiver?.isPointerReceiver == true) {
                    PointerType(TypeId(nextTypeId.next()), struct).also(types::add)
                } else {
                    struct
                }
                methodSymbols[method.name] = MethodSymbol(
                    methodSymbol,
                    struct,
                    ReceiverIdentity.of(struct),
                    moduleName,
                    receiverKind,
                    returnType,
                    parameterSymbols,
                    signature,
                    receiverType,
                    abi
                )
            }
            struct.fields = fields
            struct.methods = methodSymbols.values.toList()
            methods[declaration.name] = methodSymbols.toMap()
        }

        program.declarations.filterIsInstance<AstUnion>().forEach { declaration ->
            val union = unions[declaration.name] ?: return@forEach
            val moduleName = declarationModules[declaration] ?: defaultModule
            val ownerSymbol = symbols.firstOrNull {
                it.kind == SymbolKind.UNION && it.name == declaration.name && it.moduleName == moduleName
            }
            val ownerScope = ownerSymbol?.id?.let(typeScopes::get)
            val fields = declaration.fields.map { field ->
                val type = resolve(field.type, moduleName, field.arrayDimensions)
                val symbol = newSymbol(field.name, SymbolKind.FIELD, type, field.origin, moduleName)
                ownerScope?.let { scopes.define(it, field.name, symbol.id) }
                FieldSymbol(symbol, union)
            }
            union.fields = fields
        }

        program.declarations.filterIsInstance<AstEnum>().forEach { declaration ->
            val enum = enums[declaration.name] ?: return@forEach
            declaration.values.forEach { value ->
                if (globals.containsKey(value.name)) {
                    diagnostics.error("duplicate enum value '${value.name}'", rangeOf(value.origin), "SEM007")
                } else {
                    val symbol = newSymbol(value.name, SymbolKind.ENUM_VALUE, enum, value.origin, declarationModules[declaration] ?: defaultModule)
                    globals[value.name] = symbol
                    defineBinding(declarationModules[declaration] ?: defaultModule, value.name, symbol.id)
                }
            }
        }

        val visibleFunctions = resolveImportedFunctions(
            program,
            moduleFunctions,
            sourceTypeDeclarationsByModule,
            sourceExtensionNamesByModule,
            foreignTypes,
            foreignGlobals,
            foreignSourceFunctions,
            diagnostics,
            knownModules
        )
        moduleTypeBindingRefs.forEach { (moduleName, bindings) ->
            bindings.keys.forEach { localName ->
                if (visibleFunctions[moduleName]?.containsKey(localName) == true) {
                    val importOrigin = moduleDeclarationLists[moduleName]
                        .orEmpty()
                        .filterIsInstance<AstImport>()
                        .firstOrNull { import ->
                            import.nameAliases.values.any { it == localName } ||
                                (localName in import.names && localName !in import.nameAliases)
                        }
                        ?.origin
                    diagnostics.error(
                        "imported type name '$localName' conflicts in module '$moduleName'",
                        importOrigin?.let(::rangeOf),
                        "SEM405"
                    )
                }
            }
        }
        moduleTypeAliases.forEach { (moduleName, aliases) ->
            aliases.keys.forEach { alias ->
                if (visibleFunctions[moduleName]?.containsKey(alias) == true) {
                    val importOrigin = moduleDeclarationLists[moduleName]
                        .orEmpty()
                        .filterIsInstance<AstImport>()
                        .firstOrNull { it.alias == alias }
                        ?.origin
                    diagnostics.error(
                        "module alias '$alias' conflicts in module '$moduleName'",
                        importOrigin?.let(::rangeOf),
                        "SEM405"
                    )
                }
            }
        }
        val moduleTypeEnvironments = moduleDeclarationLists.keys.associateWith { moduleName ->
            val moduleStructs = structs.toMutableMap()
            val moduleUnions = unions.toMutableMap()
            val moduleEnums = enums.toMutableMap()
            val moduleAliases = aliases.toMutableMap()
            moduleTypeBindingRefs[moduleName].orEmpty().forEach { (localName, binding) ->
                when (val type = sourceType(binding)) {
                    is StructType -> moduleStructs[localName] = type
                    is UnionType -> moduleUnions[localName] = type
                    is EnumType -> moduleEnums[localName] = type
                    is AliasType -> moduleAliases[localName] = type
                    else -> Unit
                }
                symbols.firstOrNull {
                    it.name == binding.declarationName && it.moduleName == binding.ownerModule &&
                        it.kind in setOf(SymbolKind.ALIAS, SymbolKind.STRUCT, SymbolKind.UNION, SymbolKind.ENUM)
                }?.let { symbol -> defineTypeBinding(moduleName, localName, symbol.id) }
            }
            moduleTypeAliases[moduleName].orEmpty().forEach { (moduleAlias, targetModule) ->
                sourceTypeDeclarationsByModule[targetModule].orEmpty().forEach { (typeName, declaration) ->
                    if (!declaration.isPublic) return@forEach
                    val type = sourceType(SourceTypeBindingRef(typeName, targetModule))
                    val qualifiedName = "$moduleAlias.$typeName"
                    when (type) {
                        is StructType -> moduleStructs[qualifiedName] = type
                        is UnionType -> moduleUnions[qualifiedName] = type
                        is EnumType -> moduleEnums[qualifiedName] = type
                        is AliasType -> moduleAliases[qualifiedName] = type
                        else -> Unit
                    }
                    symbols.firstOrNull {
                        it.name == typeName && it.moduleName == targetModule &&
                            it.kind in setOf(SymbolKind.ALIAS, SymbolKind.STRUCT, SymbolKind.UNION, SymbolKind.ENUM)
                    }?.let { symbol -> defineTypeBinding(moduleName, qualifiedName, symbol.id) }
                }
            }
            ModuleTypeEnvironment(moduleStructs, moduleUnions, moduleEnums, moduleAliases)
        }

        val methodRegistryBuilder = MethodRegistry.Builder()
        methods.values.flatMap { it.values }.forEach(methodRegistryBuilder::add)
        val extensionMethods = mutableListOf<Pair<AstFunction, MethodSymbol>>()
        val extensionMethodLocals = IdentityHashMap<AstFunction, Map<String, Symbol>>()

        fun checkExportedTypeReference(moduleName: String, reference: AstTypeRef, visited: MutableSet<String> = mutableSetOf()) {
            val parts = reference.name.split('.', limit = 2)
            val binding = if (parts.size == 2) {
                moduleTypeAliases[moduleName]?.get(parts[0])?.let { SourceTypeBindingRef(parts[1], it) }
            } else {
                moduleTypeBindingRefs[moduleName]?.get(reference.name)
            }
            val typeModule = binding?.ownerModule ?: moduleName
            val typeName = binding?.declarationName ?: if (parts.size == 2) parts[1] else reference.name
            val declaration = sourceTypeDeclarationsByModule[typeModule]?.get(typeName) ?: return
            val identity = "$typeModule::$typeName"
            if (!visited.add(identity)) return
            if (!declaration.isPublic) {
                diagnostics.error(
                    "public extension method exposes non-public type '$typeName' from module '$typeModule'",
                    rangeOf(reference.origin),
                    "SEM421"
                )
                return
            }
            if (declaration is AstAlias) checkExportedTypeReference(typeModule, declaration.target, visited)
        }

        program.declarations.filterIsInstance<AstTrait>().forEach { trait ->
            val moduleName = declarationModules[trait] ?: defaultModule
            activeTypeEnvironment = moduleTypeEnvironments[moduleName]?.knownTypes.orEmpty()
            if (trait.isPublic) {
                checkExportedTypeReference(moduleName, AstTypeRef(trait.targetName, false, 0, trait.targetOrigin))
                trait.methods.forEach { method ->
                    checkExportedTypeReference(moduleName, method.returnType)
                    method.parameters.filterNot { it.isReceiver }.forEach { parameter ->
                        checkExportedTypeReference(moduleName, parameter.type)
                    }
                }
            }
            val declaredTarget = resolve(
                AstTypeRef(trait.targetName, false, 0, trait.targetOrigin),
                moduleName
            )
            val target = canonicalType(declaredTarget)
            if (target !is StructType && target !is UnionType && target !is EnumType && target !is PrimitiveType ||
                target is PrimitiveType && target.name == "void"
            ) {
                if (target !is UnknownType) diagnostics.error(
                    "compile-time trait target '${trait.targetName}' must be a complete struct, union, enum, or non-void primitive",
                    rangeOf(trait.targetOrigin),
                    "SEM417"
                )
                return@forEach
            }
            trait.methods.forEach methodLoop@{ method ->
                val receiver = method.parameters.firstOrNull { it.isReceiver }
                if (receiver == null) return@methodLoop
                val receiverIdentity = ReceiverIdentity.of(target)
                val nativeCollision = methodRegistryBuilder
                    .build()
                    .lookup(receiverIdentity, method.name, requestingModule = moduleName)
                    .firstOrNull { !it.isExtension }
                if (nativeCollision != null) {
                    diagnostics.error(
                        "extension method '${method.name}' conflicts with a native method for '${target.name}'",
                        rangeOf(method.origin),
                        "SEM416"
                    )
                    return@methodLoop
                }
                val collidingFields = when (target) {
                    is StructType -> target.fields.map { it.symbol.name }
                    is UnionType -> target.fields.map { it.symbol.name }
                    else -> emptyList()
                }
                if (method.name in collidingFields) {
                    diagnostics.error(
                        "extension method '${method.name}' conflicts with a field of '${target.name}'",
                        rangeOf(method.origin),
                        "SEM420"
                    )
                    return@methodLoop
                }
                val returnType = resolve(method.returnType, moduleName)
                val parameters = method.parameters.filterNot { it.isReceiver }.map { parameter ->
                    val parameterType = resolve(parameter.type, moduleName, parameter.arrayDimensions)
                    newSymbol(parameter.name, SymbolKind.PARAMETER, parameterType, parameter.origin, moduleName)
                }
                val signature = FunctionType(
                    TypeId(nextTypeId.next()),
                    returnType,
                    parameters.map { it.type },
                    isVariadic = method.isVariadic,
                    abi = abiOf(method.attributes, method.origin)
                ).also(types::add)
                val methodSymbol = newSymbol(
                    method.name,
                    SymbolKind.METHOD,
                    signature,
                    method.origin,
                    moduleName,
                    if (trait.isPublic) Visibility.PUBLIC else Visibility.PRIVATE,
                    method.attributes["link_name"] ?: method.attributes["export_name"],
                    signature.abi
                )
                val receiverType = if (receiver.isPointerReceiver) {
                    PointerType(TypeId(nextTypeId.next()), target).also(types::add)
                } else target
                val symbol = MethodSymbol(
                    methodSymbol,
                    target,
                    receiverIdentity,
                    moduleName,
                    ReceiverKind.INSTANCE,
                    returnType,
                    parameters,
                    signature,
                    receiverType,
                    signature.abi,
                    isExtension = true
                )
                if (!methodRegistryBuilder.add(symbol)) {
                    diagnostics.error(
                        "duplicate extension method '${method.name}' for '${target.name}' in module '$moduleName'",
                        rangeOf(method.origin),
                        "SEM416"
                    )
                    return@methodLoop
                }
                val methodScope = scopes.create(ScopeKind.FUNCTION, moduleScope(moduleName), methodSymbol.id)
                functionScopes[methodSymbol.id] = methodScope
                val locals = linkedMapOf<String, Symbol>()
                val parametersByName = parameters.associateBy(Symbol::name)
                method.parameters.forEach { parameter ->
                    val local = if (parameter.isReceiver) {
                        val type = if (parameter.isPointerReceiver) receiverType else target
                        newSymbol(parameter.name, SymbolKind.PARAMETER, type, parameter.origin, moduleName)
                    } else {
                        parametersByName.getValue(parameter.name)
                    }
                    locals[parameter.name] = local
                    scopes.define(methodScope, parameter.name, local.id)
                }
                extensionMethods += method to symbol
                extensionMethodLocals[method] = locals
            }
        }
        activeMethodRegistry = methodRegistryBuilder.build()

        program.declarations.filterIsInstance<AstGlobalVariable>().forEach { declaration ->
            val initializer = declaration.initializer ?: return@forEach
            val moduleName = declarationModules[declaration] ?: defaultModule
            activeModuleName = moduleName
            activeVisibleExtensionModules = moduleExtensionImports[moduleName].orEmpty()
            activeTypeEnvironment = moduleTypeEnvironments[moduleName]?.knownTypes.orEmpty()
            val availableFunctions = visibleFunctions[moduleName] ?: functions
            val actual = validateExpression(
                initializer,
                emptyMap(),
                availableFunctions,
                globals,
                structs,
                methods,
                expressionTypes,
                diagnostics,
                ::primitive
            )
            val expected = globals[declaration.name]?.type
            if (expected != null && actual !is UnknownType && !argumentCompatible(expected, actual)) {
                diagnostics.error(
                    "global initializer for '${declaration.name}' has type '${actual.name}', expected '${expected.name}'",
                    rangeOf(declaration.origin),
                    "SEM308"
                )
            }
        }

        program.declarations.filterIsInstance<AstFunction>().forEach { declaration ->
            val function = functions[declaration.name] ?: return@forEach
            val moduleName = declarationModules[declaration] ?: defaultModule
            activeModuleName = moduleName
            activeVisibleExtensionModules = moduleExtensionImports[moduleName].orEmpty()
            val availableFunctions = visibleFunctions[moduleName] ?: functions
            val typeEnvironment = moduleTypeEnvironments[moduleName]
                ?: ModuleTypeEnvironment(structs, unions, enums, aliases)
            activeTypeEnvironment = typeEnvironment.knownTypes
            val locals = linkedMapOf<String, Symbol>()
            function.parameters.forEach { locals[it.name] = it }
            declaration.body?.let { statement ->
                validateStatement(
                    statement,
                    function.returnType,
                    locals,
                    availableFunctions,
                    globals,
                    typeEnvironment.structs,
                    typeEnvironment.unions,
                    typeEnvironment.enums,
                    typeEnvironment.aliases,
                    foreignTypes,
                    methods,
                    expressionTypes,
                    diagnostics,
                    ::primitive,
                    functionScopes[function.symbol.id] ?: moduleScope(moduleName),
                    scopes
                )
            }
        }

        program.declarations.filterIsInstance<AstStruct>().forEach { declaration ->
            val owner = structs[declaration.name] ?: return@forEach
            val moduleName = declarationModules[declaration] ?: defaultModule
            activeModuleName = moduleName
            activeVisibleExtensionModules = moduleExtensionImports[moduleName].orEmpty()
            val availableFunctions = visibleFunctions[moduleName] ?: functions
            val typeEnvironment = moduleTypeEnvironments[moduleName]
                ?: ModuleTypeEnvironment(structs, unions, enums, aliases)
            activeTypeEnvironment = typeEnvironment.knownTypes
            declaration.methods.forEach { method ->
                val methodSymbol = methods[owner.name]?.get(method.name) ?: return@forEach
                val locals = linkedMapOf<String, Symbol>()
                method.parameters.forEach { parameter ->
                    val type = if (parameter.isReceiver) {
                        methods[owner.name]?.get(method.name)?.receiverType ?: owner
                    } else {
                        resolve(parameter.type, moduleName, parameter.arrayDimensions)
                    }
                    locals[parameter.name] = newSymbol(parameter.name, SymbolKind.PARAMETER, type, parameter.origin)
                }
                functionScopes[methodSymbol.symbol.id]?.let { methodScope ->
                    locals.values.forEach { parameter -> scopes.define(methodScope, parameter.name, parameter.id) }
                }
                method.body?.let { statement ->
                    validateStatement(
                        statement,
                        methodSymbol.returnType,
                        locals,
                        availableFunctions,
                        globals,
                        typeEnvironment.structs,
                        typeEnvironment.unions,
                        typeEnvironment.enums,
                        typeEnvironment.aliases,
                        foreignTypes,
                        methods,
                        expressionTypes,
                        diagnostics,
                        ::primitive,
                        functionScopes[methodSymbol.symbol.id] ?: moduleScope(moduleName),
                        scopes
                    )
                }
            }
        }

        extensionMethods.forEach { (method, methodSymbol) ->
            val moduleName = methodSymbol.definingModule
            activeModuleName = moduleName
            activeVisibleExtensionModules = moduleExtensionImports[moduleName].orEmpty()
            val availableFunctions = visibleFunctions[moduleName] ?: functions
            val typeEnvironment = moduleTypeEnvironments[moduleName]
                ?: ModuleTypeEnvironment(structs, unions, enums, aliases)
            activeTypeEnvironment = typeEnvironment.knownTypes
            method.body?.let { statement ->
                validateStatement(
                    statement,
                    methodSymbol.returnType,
                    LinkedHashMap(extensionMethodLocals.getValue(method)),
                    availableFunctions,
                    globals,
                    typeEnvironment.structs,
                    typeEnvironment.unions,
                    typeEnvironment.enums,
                    typeEnvironment.aliases,
                    foreignTypes,
                    methods,
                    expressionTypes,
                    diagnostics,
                    ::primitive,
                    functionScopes.getValue(methodSymbol.symbol.id),
                    scopes
                )
            }
        }

        // Compile-time declarations have their own lexical boundary even
        // though the expanded AST normally removes them before this phase.
        program.declarations.filterIsInstance<AstComptimeFunction>().forEach { declaration ->
            val parent = moduleScope(declarationModules[declaration] ?: defaultModule)
            val comptimeScope = scopes.create(ScopeKind.COMPTIME, parent)
            scopes.create(ScopeKind.CPX_TEMPLATE, comptimeScope)
        }
        program.declarations.filterIsInstance<AstCpxInvocation>().forEach { declaration ->
            scopes.create(ScopeKind.CPX_TEMPLATE, moduleScope(declarationModules[declaration] ?: defaultModule))
        }

        val sourceTypeOwners = SourceTypeCatalogue.from(symbols).declarationsByModule
            .flatMap { (moduleName, declarations) -> declarations.keys.map { it to moduleName } }
            .toMap()
        ModuleTypeReferenceCollector.collect(program).forEach { reference ->
            val qualifiedParts = reference.type.name.split('.', limit = 2)
            if (qualifiedParts.size == 2) {
                val targetModule = moduleTypeAliases[reference.moduleName]?.get(qualifiedParts[0]) ?: return@forEach
                val declaration = sourceTypeDeclarationsByModule[targetModule]?.get(qualifiedParts[1])
                when {
                    declaration == null -> diagnostics.error(
                        "imported type '${reference.type.name}' is not declared in module '$targetModule'",
                        rangeOf(reference.type.origin),
                        "SEM404"
                    )
                    !declaration.isPublic -> diagnostics.error(
                        "imported type '${reference.type.name}' is not public in module '$targetModule'",
                        rangeOf(reference.type.origin),
                        "SEM406"
                    )
                }
            } else {
                val ownerModule = sourceTypeOwners[reference.type.name]
                val imported = moduleTypeBindingRefs[reference.moduleName]?.containsKey(reference.type.name) == true
                if (ownerModule != null && ownerModule != reference.moduleName && !imported) {
                    diagnostics.error(
                        "type '${reference.type.name}' belongs to module '$ownerModule' and is not imported into module '${reference.moduleName}'",
                        rangeOf(reference.type.origin),
                        "SEM410"
                    )
                }
            }
        }

        val resolvedModuleTypeBindings = moduleTypeBindingRefs.mapValues { (moduleName, bindings) ->
            bindings.mapNotNull { (localName, binding) ->
                val symbol = symbols.firstOrNull {
                    it.name == binding.declarationName && it.moduleName == binding.ownerModule &&
                        it.kind in setOf(SymbolKind.ALIAS, SymbolKind.STRUCT, SymbolKind.UNION, SymbolKind.ENUM)
                }
                symbol?.let {
                    localName to SourceTypeBinding(binding.declarationName, binding.ownerModule, it)
                }
            }.toMap()
        }

        val initialModel = SemanticModel(
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
            foreignGlobals,
            moduleTypeBindings = resolvedModuleTypeBindings,
            moduleTypeAliases = moduleTypeAliases.mapValues { (_, aliases) -> aliases.toMap() },
            methodRegistry = activeMethodRegistry,
            resolvedMethodCalls = resolvedMethodCalls,
            extensionModuleImports = moduleExtensionImports
        )
        val cataloguedModel = initialModel.copy(
            declarationCatalogue = buildDeclarationCatalogue(
                program,
                initialModel,
                declarationModules,
                moduleScopes
            )
        )
        val resolvedAst = ReferenceCollector.collect(cataloguedModel)
        val model = cataloguedModel.copy(
            nodeIds = resolvedAst.nodes,
            referenceIndex = resolvedAst.referenceIndex
        )
        return SemanticResult(model, diagnostics.diagnostics)
    }

    private fun resolveImportedFunctions(
        program: AstProgram,
        moduleFunctions: Map<String, Map<String, FunctionSymbol>>,
        sourceTypeDeclarationsByModule: Map<String, Map<String, AstDeclaration>>,
        sourceExtensionNamesByModule: Map<String, Set<String>>,
        foreignTypes: Map<String, ForeignType>,
        foreignGlobals: Map<String, Symbol>,
        foreignSourceFunctions: Map<String, FunctionSymbol>,
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
            foreignSourceFunctions.forEach { (name, function) -> visible.putIfAbsent(name, function) }
            declarations.filterIsInstance<AstImport>().forEach { import ->
                val targetNames = moduleTargetNames(import.module)
                val targetSourceTypes = targetNames.asSequence()
                    .mapNotNull(sourceTypeDeclarationsByModule::get)
                    .firstOrNull()
                    .orEmpty()
                val targetExtensionNames = targetNames.asSequence()
                    .mapNotNull(sourceExtensionNamesByModule::get)
                    .firstOrNull()
                    .orEmpty()
                val targetEnumValues = targetNames.asSequence()
                    .mapNotNull(moduleDeclarations::get)
                    .firstOrNull()
                    .orEmpty()
                    .filterIsInstance<AstEnum>()
                    .filter(AstEnum::isPublic)
                    .flatMap { it.values.map(AstEnumValue::name) }
                    .toSet()
                val targetFunctions = targetNames.asSequence()
                    .mapNotNull(moduleFunctions::get)
                    .firstOrNull()
                val exportedFunctions = targetFunctions?.filterValues {
                    it.symbol.visibility == Visibility.PUBLIC || it.symbol.kind == SymbolKind.FOREIGN
                }.orEmpty()
                if (targetFunctions == null) {
                    if (!headerImportService.isKnownModule(import.module) &&
                        targetNames.none { it in knownModules }
                    ) {
                        diagnostics.error("module import '${import.module}' cannot be resolved", rangeOf(import.origin), "SEM402")
                    } else if (import.names.isNotEmpty()) {
                        import.names.forEach { name ->
                            if (name !in foreignTypes && name !in foreignGlobals &&
                                name !in targetSourceTypes && name !in targetEnumValues && name !in targetExtensionNames
                            ) {
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
                    if (name in targetSourceTypes || name in targetEnumValues ||
                        name in targetExtensionNames && name !in targetFunctions.orEmpty()
                    ) return@forEach
                    if (name in foreignTypes || name in foreignGlobals) return@forEach
                    val function = targetFunctions[name]
                    if (function == null) {
                        diagnostics.error("imported function '$name' is not declared in module '${import.module}'", rangeOf(import.origin), "SEM404")
                    } else if (function.symbol.visibility != Visibility.PUBLIC && function.symbol.kind != SymbolKind.FOREIGN) {
                        diagnostics.error("imported function '$name' is not public in module '${import.module}'", rangeOf(import.origin), "SEM406")
                    } else if (import.alias != null) {
                        val localName = import.nameAliases[name] ?: name
                        visible["${import.alias}.$localName"] = function
                    } else {
                        val localName = import.nameAliases[name] ?: name
                        if (visible.putIfAbsent(localName, function) != null) {
                            diagnostics.error("imported name '$localName' conflicts in module '$moduleName'", rangeOf(import.origin), "SEM405")
                        }
                    }
                }
            }
            visible
        }
    }

    private fun moduleTargetNames(module: String): List<String> {
        val normalized = module.trim().removeSurrounding("\"")
        val basename = normalized.substringAfterLast('/')
        val withoutExtension = basename.removeSuffix(".cp")
        val dottedName = normalized.substringAfterLast('.')
        return linkedSetOf(normalized, basename, withoutExtension, dottedName).toList()
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
                CPrimitiveTypes.isKnownTypeName(reference.name) -> primitive(reference.name)
                structs[reference.name] != null -> structs.getValue(reference.name)
                unions[reference.name] != null -> unions.getValue(reference.name)
                enums[reference.name] != null -> enums.getValue(reference.name)
                aliases[reference.name] != null -> aliases.getValue(reference.name)
                foreignTypes[reference.name] != null -> foreignTypes.getValue(reference.name)
                '.' in reference.name -> UnknownType(TypeId(-1))
                else -> {
                    diagnostics.error("unknown type '${reference.name}'", rangeOf(reference.origin), "SEM102")
                    UnknownType(TypeId(-1))
                }
            }
        }
        var resolved: CType = base
        repeat(reference.pointerDepth) {
            resolved = PointerType(TypeId(nextTypeId.next()), resolved)
        }
        val functionParameters = reference.functionParameters
        if (functionParameters != null) {
            val parameterTypes = functionParameters.map { parameter ->
                resolveType(
                    parameter.type,
                    structs,
                    unions,
                    enums,
                    aliases,
                    foreignTypes,
                    primitive,
                    diagnostics,
                    parameter.arrayDimensions
                )
            }
            var function: CType = FunctionType(
                TypeId(-1),
                resolved,
                parameterTypes,
                reference.functionVariadic
            )
            repeat(reference.functionPointerDepth) {
                function = PointerType(TypeId(-1), function)
            }
            resolved = function
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
        scopeId: ScopeId,
        scopes: ScopeTable,
        loopDepth: Int = 0
    ) {
        when (statement) {
            is AstBlock -> {
                val blockScope = scopes.create(ScopeKind.BLOCK, scopeId)
                val blockLocals = LinkedHashMap(locals)
                statement.statements.forEach {
                    validateStatement(
                        it,
                        expectedReturn,
                        blockLocals,
                        functions,
                        globals,
                        structs,
                        unions,
                        enums,
                        aliases,
                        foreignTypes,
                        methods,
                        expressionTypes,
                        diagnostics,
                        primitive,
                        blockScope,
                        scopes,
                        loopDepth
                    )
                }
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
                } else if (actual.name != "<unknown>" && !argumentCompatible(expectedReturn, actual)) {
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
                validateStatement(statement.thenBranch, expectedReturn, LinkedHashMap(locals), functions, globals, structs, unions, enums, aliases, foreignTypes, methods, expressionTypes, diagnostics, primitive, scopeId, scopes, loopDepth)
                statement.elseBranch?.let {
                    validateStatement(it, expectedReturn, LinkedHashMap(locals), functions, globals, structs, unions, enums, aliases, foreignTypes, methods, expressionTypes, diagnostics, primitive, scopeId, scopes, loopDepth)
                }
            }
            is AstWhile -> {
                validateExpression(statement.condition, locals, functions, globals, structs, methods, expressionTypes, diagnostics, primitive)
                validateStatement(statement.body, expectedReturn, LinkedHashMap(locals), functions, globals, structs, unions, enums, aliases, foreignTypes, methods, expressionTypes, diagnostics, primitive, scopeId, scopes, loopDepth + 1)
            }
            is AstFor -> {
                val loopScope = scopes.create(ScopeKind.BLOCK, scopeId)
                val loopLocals = LinkedHashMap(locals)
                statement.initializer?.let {
                    validateStatement(it, expectedReturn, loopLocals, functions, globals, structs, unions, enums, aliases, foreignTypes, methods, expressionTypes, diagnostics, primitive, loopScope, scopes, loopDepth)
                }
                statement.condition?.let {
                    validateExpression(it, loopLocals, functions, globals, structs, methods, expressionTypes, diagnostics, primitive)
                }
                validateStatement(statement.body, expectedReturn, loopLocals, functions, globals, structs, unions, enums, aliases, foreignTypes, methods, expressionTypes, diagnostics, primitive, loopScope, scopes, loopDepth + 1)
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
                    scopes.define(scopeId, statement.name, symbol.id)
                }
                statement.initializer?.let {
                    val actual = validateExpression(it, locals, functions, globals, structs, methods, expressionTypes, diagnostics, primitive)
                    if (actual !is UnknownType && !argumentCompatible(type, actual)) {
                        diagnostics.error(
                            "initializer for '${statement.name}' has type '${actual.name}', expected '${type.name}'",
                            rangeOf(statement.origin),
                            "SEM308"
                        )
                    }
                }
            }
            is AstInnerFunction -> {
                val nestedLocals = LinkedHashMap(locals)
                val nestedScope = scopes.create(ScopeKind.FUNCTION, scopeId)
                statement.function.parameters.forEach { parameter ->
                    val type = resolveType(parameter.type, structs, unions, enums, aliases, foreignTypes, primitive, diagnostics, parameter.arrayDimensions)
                    val symbol = Symbol(SymbolId(-nestedLocals.size - 1), parameter.name, SymbolKind.PARAMETER, type, parameter.origin)
                    nestedLocals[parameter.name] = symbol
                    scopes.define(nestedScope, parameter.name, symbol.id)
                }
                val returnType = resolveType(statement.function.returnType, structs, unions, enums, aliases, foreignTypes, primitive, diagnostics)
                validateStatement(statement.function.body ?: AstBlock(emptyList(), statement.origin), returnType, nestedLocals, functions, globals, structs, unions, enums, aliases, foreignTypes, methods, expressionTypes, diagnostics, primitive, nestedScope, scopes, loopDepth)
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
            is AstBooleanLiteral -> primitive("bool")
            is AstFloatLiteral -> primitive("double")
            is AstStringLiteral -> PointerType(TypeId(-1), primitive("char"))
            is AstStringTemplate -> {
                expression.parts.filterIsInstance<AstStringExpressionPart>().forEach { part ->
                    validateExpression(part.expression, locals, functions, globals, structs, methods, expressionTypes, diagnostics, primitive)
                }
                PointerType(TypeId(-1), primitive("char"))
            }
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
            is AstUnary -> {
                val operandType = validateExpression(
                    expression.operand,
                    locals,
                    functions,
                    globals,
                    structs,
                    methods,
                    expressionTypes,
                    diagnostics,
                    primitive
                )
                when (expression.operator) {
                    "&" -> {
                        if (!isAssignable(expression.operand, locals, globals)) {
                            diagnostics.error("operand of '&' is not addressable", rangeOf(expression.origin), "SEM312")
                        }
                        PointerType(TypeId(-1), operandType)
                    }
                    "*" -> when (val canonical = canonicalType(operandType)) {
                        is PointerType -> canonical.pointee
                        else -> {
                            diagnostics.error("cannot dereference non-pointer expression", rangeOf(expression.origin), "SEM313")
                            UnknownType(TypeId(-1))
                        }
                    }
                    "+", "-" -> if (isNumericType(operandType)) operandType else {
                        diagnostics.error("unary '${expression.operator}' requires a numeric operand", rangeOf(expression.origin), "SEM316")
                        UnknownType(TypeId(-1))
                    }
                    "!" -> if (isScalarType(operandType)) primitive("bool") else {
                        diagnostics.error("logical negation requires a scalar operand", rangeOf(expression.origin), "SEM316")
                        UnknownType(TypeId(-1))
                    }
                    "~" -> if (isIntegerType(operandType)) operandType else {
                        diagnostics.error("bitwise complement requires an integer operand", rangeOf(expression.origin), "SEM316")
                        UnknownType(TypeId(-1))
                    }
                    else -> operandType
                }
            }
            is AstBinary -> {
                val left = validateExpression(expression.left, locals, functions, globals, structs, methods, expressionTypes, diagnostics, primitive)
                val right = validateExpression(expression.right, locals, functions, globals, structs, methods, expressionTypes, diagnostics, primitive)
                if (expression.operator in assignmentOperators) {
                    if (!isAssignable(expression.left, locals, globals)) {
                        diagnostics.error("left side of '${expression.operator}' is not assignable", rangeOf(expression.left.origin), "SEM307")
                    } else if (right !is UnknownType && !argumentCompatible(left, right)) {
                        diagnostics.error("cannot assign '${right.name}' to '${left.name}'", rangeOf(expression.origin), "SEM308")
                    }
                }
                binaryResultType(expression.operator, left, right, expression.origin, diagnostics, primitive)
            }
            is AstConditional -> {
                val conditionType = validateExpression(expression.condition, locals, functions, globals, structs, methods, expressionTypes, diagnostics, primitive)
                if (!isScalarType(conditionType)) {
                    diagnostics.error("conditional expression requires a scalar condition", rangeOf(expression.condition.origin), "SEM316")
                }
                val thenType = validateExpression(expression.thenBranch, locals, functions, globals, structs, methods, expressionTypes, diagnostics, primitive)
                val elseType = validateExpression(expression.elseBranch, locals, functions, globals, structs, methods, expressionTypes, diagnostics, primitive)
                if (thenType !is UnknownType && elseType !is UnknownType && !argumentCompatible(thenType, elseType)) {
                    diagnostics.error("conditional branches have incompatible types", rangeOf(expression.origin), "SEM309")
                }
                if (isNumericType(thenType) && isNumericType(elseType) &&
                    (isComplexType(thenType) || isComplexType(elseType))) {
                    commonComplexArithmeticType(thenType, elseType, primitive)
                } else thenType
            }
            is AstUpdate -> {
                val operandType = validateExpression(expression.operand, locals, functions, globals, structs, methods, expressionTypes, diagnostics, primitive)
                if (!isAssignable(expression.operand, locals, globals)) {
                    diagnostics.error("operand of '${expression.operator}' is not assignable", rangeOf(expression.origin), "SEM307")
                }
                operandType
            }
            is AstSizeOf -> {
                expression.operand?.let {
                    validateExpression(it, locals, functions, globals, structs, methods, expressionTypes, diagnostics, primitive)
                }
                expression.targetType?.let { target ->
                    if (!CPrimitiveTypes.isKnownTypeName(target.name) && target.name !in activeTypeEnvironment &&
                        target.declarationKind == "named" && '.' !in target.name
                    ) {
                        diagnostics.error("unsupported sizeof type '${target.name}'", rangeOf(target.origin), "SEM311")
                    }
                }
                primitive("size_t")
            }
            is AstAbiQuery -> {
                expression.operand?.let {
                    validateExpression(it, locals, functions, globals, structs, methods, expressionTypes, diagnostics, primitive)
                }
                val target = expression.targetType
                if (target == null) {
                    diagnostics.error("${expression.query} requires a target type", rangeOf(expression.origin), "SEM312")
                } else if (
                    !CPrimitiveTypes.isKnownTypeName(target.name) &&
                    target.declarationKind == "named" &&
                    target.name !in activeTypeEnvironment && '.' !in target.name
                ) {
                    diagnostics.error("unsupported ${expression.query} type '${target.name}'", rangeOf(target.origin), "SEM312")
                }
                if (expression.query == "offsetof") {
                    val aggregate = target?.name?.let { activeTypeEnvironment[it] as? StructType }
                    if (aggregate != null && expression.fieldName !in aggregate.fields.map { it.symbol.name }) {
                        diagnostics.error("unknown field '${expression.fieldName}' in offsetof", rangeOf(expression.origin), "SEM313")
                    }
                }
                primitive("size_t")
            }
            is AstCast -> {
                validateExpression(expression.operand, locals, functions, globals, structs, methods, expressionTypes, diagnostics, primitive)
                val targetBase = when {
                    CPrimitiveTypes.isKnownTypeName(expression.target.name) -> primitive(expression.target.name)
                    else -> activeTypeEnvironment[expression.target.name]
                }
                if (targetBase == null) {
                    if ('.' !in expression.target.name) {
                        diagnostics.error("unsupported cast target '${expression.target.name}'", rangeOf(expression.target.origin), "SEM310")
                    }
                    UnknownType(TypeId(-1))
                } else {
                    var target: CType = targetBase
                    repeat(expression.target.pointerDepth) {
                        target = PointerType(TypeId(-1), target)
                    }
                    target
                }
            }
            is AstCall -> {
                val function = (expression.callee as? AstIdentifier)?.let { functions[it.name] }
                val methodCall = expression.callee as? AstMemberAccess
                val qualifiedFunction = methodCall?.let { member ->
                    (member.receiver as? AstIdentifier)?.let { receiver -> functions["${receiver.name}.${member.member}"] }
                }
                var ambiguousMethodCall = false
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
                    val candidates = activeMethodRegistry.lookup(
                        ReceiverIdentity.of(receiverType),
                        methodCall.member,
                        activeVisibleExtensionModules,
                        activeModuleName
                    )
                    if (candidates.size > 1) {
                        ambiguousMethodCall = true
                        val providers = candidates.map { it.definingModule }.distinct().sorted()
                        diagnostics.error(
                            "ambiguous extension method '${methodCall.member}' for receiver '${receiverType.name}' from providers ${providers.joinToString()}",
                            rangeOf(methodCall.origin),
                            "SEM418"
                        )
                    }
                    candidates.singleOrNull()?.also { method ->
                        resolvedMethodCalls[expression] = ResolvedMethodCall(
                            method,
                            methodCall.receiver,
                            receiverAdaptation(method, receiverType, methodCall.receiver, locals, globals, diagnostics)
                        )
                    }
                } else null
                val indirectSignature = if (function == null && qualifiedFunction == null && resolvedMethod == null && !ambiguousMethodCall) {
                    callableSignature(
                        validateExpression(
                            expression.callee,
                            locals,
                            functions,
                            globals,
                            structs,
                            methods,
                            expressionTypes,
                            diagnostics,
                            primitive
                        )
                    )
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
                    ambiguousMethodCall -> {
                        expression.arguments.forEach { validateExpression(it, locals, functions, globals, structs, methods, expressionTypes, diagnostics, primitive) }
                        UnknownType(TypeId(-1))
                    }
                    indirectSignature != null -> {
                        validateCallableArguments(
                            "indirect call",
                            indirectSignature,
                            expression,
                            locals,
                            functions,
                            globals,
                            structs,
                            methods,
                            expressionTypes,
                            diagnostics,
                            primitive
                        )
                        indirectSignature.returnType
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
            is AstIndexAccess -> {
                val receiver = validateExpression(expression.receiver, locals, functions, globals, structs, methods, expressionTypes, diagnostics, primitive)
                validateExpression(expression.index, locals, functions, globals, structs, methods, expressionTypes, diagnostics, primitive)
                when (val indexed = canonicalType(receiver)) {
                    is ArrayType -> indexed.element
                    is PointerType -> indexed.pointee
                    else -> {
                        diagnostics.error("expression is not indexable", rangeOf(expression.origin), "SEM305")
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
        validateCallableArguments(
            name,
            FunctionType(TypeId(-1), primitive("void"), parameters.map { it.type }, isVariadic),
            call,
            locals,
            functions,
            globals,
            structs,
            methods,
            expressionTypes,
            diagnostics,
            primitive
        )
    }

    private fun validateCallableArguments(
        name: String,
        signature: FunctionType,
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
        val parameters = signature.parameterTypes
        val isVariadic = signature.isVariadic
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
            if (actual !is UnknownType && !argumentCompatible(parameter, actual)) {
                diagnostics.error(
                    "argument for '$name' has type '${actual.name}', expected '${parameter.name}'",
                    rangeOf(call.origin),
                    "SEM306"
                )
            }
        }
    }

    private fun callableSignature(type: CType?): FunctionType? = when (type) {
        is FunctionType -> type
        is PointerType -> type.pointee as? FunctionType
        is AliasType -> callableSignature(type.target)
        else -> null
    }

    private fun equivalentTypes(left: CType, right: CType): Boolean = canonicalTypeKey(left) == canonicalTypeKey(right)

    private fun argumentCompatible(expected: CType, actual: CType): Boolean = when {
        expected is ArrayType && actual is ArrayType -> equivalentTypes(expected.element, actual.element)
        isNumericType(expected) && isNumericType(actual) -> true
        expected is PointerType && expected.pointee is FunctionType && actual is FunctionType ->
            equivalentTypes(expected.pointee, actual)
        expected is PointerType && expected.pointee is FunctionType && actual is PointerType && actual.pointee is FunctionType ->
            equivalentTypes(expected.pointee, actual.pointee)
        isPointerLike(expected) && isPointerLike(actual) -> pointersCompatible(expected, actual)
        else -> equivalentTypes(expected, actual)
    }

    private fun binaryResultType(
        operator: String,
        left: CType,
        right: CType,
        origin: Origin,
        diagnostics: DiagnosticBag,
        primitive: (String) -> PrimitiveType
    ): CType {
        if (left is UnknownType || right is UnknownType) return UnknownType(TypeId(-1))
        fun invalid(message: String): CType {
            diagnostics.error(message, rangeOf(origin), "SEM316")
            return UnknownType(TypeId(-1))
        }
        return when {
            operator in assignmentOperators -> {
                if (operator == "%=" && (isComplexType(left) || isComplexType(right))) {
                    invalid("operator '%=' requires integer operands")
                } else left
            }
            operator in logicalOperators -> {
                if (!isScalarType(left) || !isScalarType(right)) {
                    invalid("logical operator '$operator' requires scalar operands")
                } else primitive("bool")
            }
            operator in comparisonOperators -> {
                val valid = if (operator in setOf("==", "!=")) {
                    isNumericType(left) && isNumericType(right) || pointersCompatible(left, right)
                } else {
                    isNumericType(left) && isNumericType(right) &&
                        !isComplexType(left) && !isComplexType(right) || pointersCompatible(left, right)
                }
                if (!valid) invalid("comparison operator '$operator' requires compatible scalar operands")
                else primitive("bool")
            }
            operator == "+" -> when {
                isNumericType(left) && isNumericType(right) -> commonComplexArithmeticType(left, right, primitive)
                isPointerLike(left) && isIntegerType(right) -> pointerValueType(left)
                isIntegerType(left) && isPointerLike(right) -> pointerValueType(right)
                else -> invalid("operator '+' requires numeric operands or a pointer and integer")
            }
            operator == "-" -> when {
                isNumericType(left) && isNumericType(right) -> commonComplexArithmeticType(left, right, primitive)
                isPointerLike(left) && isIntegerType(right) -> pointerValueType(left)
                isPointerLike(left) && isPointerLike(right) && pointersCompatible(left, right) -> primitive("ptrdiff_t")
                else -> invalid("operator '-' requires numeric operands, pointer/integer, or compatible pointers")
            }
            operator in setOf("*", "/") -> {
                if (isNumericType(left) && isNumericType(right)) commonComplexArithmeticType(left, right, primitive)
                else invalid("operator '$operator' requires numeric operands")
            }
            operator == "%" -> {
                if (isIntegerType(left) && isIntegerType(right)) left
                else invalid("operator '%' requires integer operands")
            }
            operator in setOf("<<", ">>", "|", "^", "&") -> {
                if (isIntegerType(left) && isIntegerType(right)) left
                else invalid("operator '$operator' requires integer operands")
            }
            else -> left
        }
    }

    private fun isNumericType(type: CType): Boolean = when (val canonical = canonicalType(type)) {
        is PrimitiveType -> CPrimitiveTypes.isNumeric(canonical.name) || CPrimitiveTypes.isComplex(canonical.name)
        is ForeignType -> canonical.underlyingType?.let(::isNumericType) == true
        is EnumType -> true
        else -> false
    }

    private fun isComplexType(type: CType): Boolean = when (val canonical = canonicalType(type)) {
        is PrimitiveType -> CPrimitiveTypes.isComplex(canonical.name)
        is ForeignType -> canonical.underlyingType?.let(::isComplexType) == true
        else -> false
    }

    private fun complexComponentRank(type: CType): CFloatingRank? = when (val canonical = canonicalType(type)) {
        is PrimitiveType -> CPrimitiveTypes.typeInfo(canonical.name)?.let { primitive ->
            when (primitive.kind) {
                CPrimitiveKind.COMPLEX, CPrimitiveKind.FLOATING -> primitive.floatingRank
                else -> null
            }
        }
        is ForeignType -> canonical.underlyingType?.let(::complexComponentRank)
        else -> null
    }

    private fun commonComplexArithmeticType(
        left: CType,
        right: CType,
        primitive: (String) -> PrimitiveType
    ): CType {
        if (!isComplexType(left) && !isComplexType(right)) return left
        val rank = listOfNotNull(complexComponentRank(left), complexComponentRank(right))
            .maxOrNull() ?: CFloatingRank.DOUBLE
        val componentName = when (rank) {
            CFloatingRank.FLOAT -> "float"
            CFloatingRank.DOUBLE -> "double"
            CFloatingRank.LONG_DOUBLE -> "long double"
        }
        return primitive("$componentName _Complex")
    }

    private fun isIntegerType(type: CType): Boolean = when (val canonical = canonicalType(type)) {
        is PrimitiveType -> CPrimitiveTypes.isInteger(canonical.name)
        is ForeignType -> canonical.underlyingType?.let(::isIntegerType) == true
        is EnumType -> true
        else -> false
    }

    private fun isScalarType(type: CType): Boolean = isNumericType(type) || isPointerLike(type)

    private fun isPointerLike(type: CType): Boolean = when (val canonical = canonicalType(type)) {
        is PointerType -> true
        is ArrayType -> true
        else -> false
    }

    private fun pointerPointee(type: CType): CType? = when (val canonical = canonicalType(type)) {
        is PointerType -> canonical.pointee
        is ArrayType -> canonical.element
        else -> null
    }

    private fun pointerValueType(type: CType): CType = when (val canonical = canonicalType(type)) {
        is PointerType -> canonical
        is ArrayType -> PointerType(TypeId(-1), canonical.element)
        else -> UnknownType(TypeId(-1))
    }

    private fun pointersCompatible(left: CType, right: CType): Boolean {
        val leftPointee = pointerPointee(left) ?: return false
        val rightPointee = pointerPointee(right) ?: return false
        if (leftPointee is FunctionType || rightPointee is FunctionType) {
            return callableSignature(leftPointee) != null && callableSignature(rightPointee) != null &&
                equivalentTypes(leftPointee, rightPointee)
        }
        if (leftPointee is PrimitiveType && leftPointee.name == "void") return true
        if (rightPointee is PrimitiveType && rightPointee.name == "void") return true
        return equivalentTypes(leftPointee, rightPointee)
    }

    private fun isAssignable(
        expression: AstExpression,
        locals: Map<String, Symbol>,
        globals: Map<String, Symbol>
    ): Boolean = when (expression) {
        is AstIdentifier -> locals[expression.name]?.kind in setOf(SymbolKind.VARIABLE, SymbolKind.PARAMETER) ||
            globals[expression.name]?.kind == SymbolKind.VARIABLE ||
            globals[expression.name]?.kind == SymbolKind.FOREIGN_GLOBAL
        is AstMemberAccess, is AstIndexAccess -> true
        is AstUnary -> expression.operator == "*"
        is AstParenthesized -> isAssignable(expression.expression, locals, globals)
        else -> false
    }

    private fun receiverAdaptation(
        method: MethodSymbol,
        actualType: CType,
        receiver: AstExpression,
        locals: Map<String, Symbol>,
        globals: Map<String, Symbol>,
        diagnostics: DiagnosticBag
    ): ReceiverAdaptation {
        if (method.receiverKind == ReceiverKind.STATIC) return ReceiverAdaptation.NONE
        val pointerReceiver = isPointerLike(method.receiverType)
        val pointerValue = isPointerLike(actualType)
        if (pointerReceiver && !pointerValue) {
            if (!isAssignable(receiver, locals, globals)) {
                diagnostics.error(
                    "pointer receiver for '${method.symbol.name}' requires an addressable value",
                    rangeOf(receiver.origin),
                    "SEM419"
                )
            }
            return ReceiverAdaptation.ADDRESS
        }
        if (pointerValue) return ReceiverAdaptation.POINTER
        if (method.owner is StructType || method.owner is UnionType) {
            if (!isAssignable(receiver, locals, globals)) {
                diagnostics.error(
                    "aggregate receiver for '${method.symbol.name}' requires addressable storage",
                    rangeOf(receiver.origin),
                    "SEM419"
                )
            }
            return ReceiverAdaptation.ADDRESS
        }
        return ReceiverAdaptation.VALUE
    }

    private val assignmentOperators = setOf("=", "+=", "-=", "*=", "/=", "%=")

    private val comparisonOperators = setOf("==", "!=", "<", "<=", ">", ">=")

    private val logicalOperators = setOf("&&", "||")

    private fun canonicalType(type: CType): CType {
        val visited = mutableSetOf<TypeId>()
        var current = type
        while (visited.add(current.id)) {
            current = when (current) {
                is AliasType -> current.target
                is ForeignType -> current.underlyingType ?: return current
                else -> return current
            }
        }
        return current
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
        is ForeignType -> type.underlyingType?.let(::aggregateType) ?: type
        else -> type
    }

    private fun ownerStruct(receiver: AstExpression, receiverType: CType, structs: Map<String, StructType>): StructType? = when (receiver) {
        is AstIdentifier -> structs[receiver.name] ?: aggregateStruct(receiverType)?.let { structs[it.name] ?: it }
        else -> aggregateStruct(receiverType)?.let { structs[it.name] ?: it }
    }

    private fun rangeOf(origin: Origin): SourceRange? = origin.primaryRange

}
