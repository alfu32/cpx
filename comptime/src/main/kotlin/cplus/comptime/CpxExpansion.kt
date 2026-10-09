package cplus.comptime

import cplus.core.*
import cplus.semantic.SymbolId
import cplus.semantic.TypeId
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.file.Path
import java.security.MessageDigest
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

data class ComptimeTargetInfo(
    val cDialect: String = "c17",
    val runtimeProfile: String = "cplus",
    val libcProfile: String = "c17",
    val os: String = "unknown",
    val architecture: String = "unknown",
    val vendor: String = "unknown",
    val abi: String = "unknown",
    val objectFormat: String = "unknown",
    val endianness: String = "unknown",
    val pointerBits: Int = 0,
    val wordBits: Int = 0,
    val cIntegerModel: String = "unknown",
    val features: Set<String> = emptySet(),
    val intrinsics: Set<String> = emptySet(),
    val supportedAbis: Set<String> = emptySet(),
    val libcProfiles: Set<String> = emptySet(),
    val services: Set<String> = emptySet()
) {
    fun hasFeature(name: String): Boolean = name in features
    fun hasIntrinsic(name: String): Boolean = name in intrinsics
    fun hasLibcProfile(name: String): Boolean = name in libcProfiles || libcProfile == name
    fun supportsAbi(name: String): Boolean = name in supportedAbis || abi == name
    fun hasService(name: String): Boolean = name in services
}

data class ExpansionId(
    val declaration: String,
    val callSite: NodeId,
    val parent: ExpansionId?,
    val key: ExpansionKey
)

data class ComptimeContext(
    val module: String,
    val scope: cplus.semantic.ScopeId?,
    val containingType: TypeId?,
    val containingFunction: SymbolId?,
    val phase: CpxPhase,
    val target: ComptimeTargetInfo,
    val sourceOrigin: Origin,
    val expansion: ExpansionId?,
    val typeUniverse: TypeUniverseSnapshot = TypeUniverseSnapshot(emptySet(), TypeUniverseAccess.EARLY_SAFE)
) {
    val reflection: ComptimeReflection
        get() = ComptimeReflection(typeUniverse)
}

data class ComptimeExpansionChannels(
    val replacement: List<NodeId> = emptyList(),
    val hoistedDeclarations: List<NodeId> = emptyList(),
    val localDeclarations: List<NodeId> = emptyList(),
    val beforeStatements: List<NodeId> = emptyList(),
    val afterStatements: List<NodeId> = emptyList(),
    val dependencies: Set<ComptimeDependency> = emptySet()
)

data class ComptimeEvaluationResult(
    val renderedText: String,
    val channels: ComptimeExpansionChannels = ComptimeExpansionChannels(),
    val diagnostics: List<Diagnostic> = emptyList()
)

fun interface ComptimeEvaluator {
    fun evaluate(
        functionName: String,
        template: CpxTemplate,
        bindings: Map<String, ComptimeValue>,
        context: ComptimeContext
    ): ComptimeEvaluationResult
}

class TemplateComptimeEvaluator : ComptimeEvaluator {
    override fun evaluate(
        functionName: String,
        template: CpxTemplate,
        bindings: Map<String, ComptimeValue>,
        context: ComptimeContext
    ): ComptimeEvaluationResult = ComptimeEvaluationResult(template.render(bindings))
}

data class ComptimeTypeIdentity(
    val typeId: TypeId,
    val canonicalTypeId: TypeId,
    val canonicalText: String
)

fun interface ComptimeTypeResolver {
    fun resolve(sourceText: String): ComptimeTypeIdentity?
}

fun interface ComptimeReferenceResolver {
    fun resolve(node: AstNode, arena: AstArena): Map<NodeId, SymbolId>
}

sealed interface ComptimeValue {
    val sourceText: String
    val canonicalKind: String
    val canonicalText: String
    val nodeId: NodeId?
        get() = null
    val origin: Origin?
        get() = null
    val references: Map<NodeId, SymbolId>
        get() = emptyMap()

    data class CtType(
        override val sourceText: String,
        val identifierText: String = sourceText.removePrefix("struct ").trim(),
        val typeId: TypeId? = null,
        val canonicalTypeId: TypeId? = typeId,
        val canonicalSyntax: String? = null
    ) : ComptimeValue {
        override val canonicalKind: String = "type"
        override val canonicalText: String = canonicalTypeId?.let { "id:${it.value}" } ?: identifierText
    }

    data class CtIdentifier(val text: String) : ComptimeValue {
        override val sourceText: String = text
        override val canonicalKind: String = "identifier"
        override val canonicalText: String = text
    }

    data class CtInteger(val text: String, val value: BigInteger) : ComptimeValue {
        override val sourceText: String = value.toString()
        override val canonicalKind: String = "int"
        override val canonicalText: String = value.toString()
    }

    data class CtFloat(val text: String, val value: BigDecimal) : ComptimeValue {
        override val sourceText: String = value.stripTrailingZeros().toPlainString()
        override val canonicalKind: String = "float"
        override val canonicalText: String = value.stripTrailingZeros().toPlainString()
    }

    data class CtBoolean(val value: Boolean, val text: String = value.toString()) : ComptimeValue {
        override val sourceText: String = value.toString()
        override val canonicalKind: String = "bool"
        override val canonicalText: String = value.toString()
    }

    data class CtString(val text: String) : ComptimeValue {
        override val sourceText: String = text
        override val canonicalKind: String = "string"
        override val canonicalText: String = text
    }

    data class CtExpression(
        val text: String,
        override val nodeId: NodeId,
        override val origin: Origin,
        override val references: Map<NodeId, SymbolId> = emptyMap()
    ) : ComptimeValue {
        override val sourceText: String = text
        override val canonicalKind: String = "expr"
        override val canonicalText: String = canonicalSyntax(text, references)
    }

    data class CtStatement(
        val text: String,
        override val nodeId: NodeId,
        override val origin: Origin,
        override val references: Map<NodeId, SymbolId> = emptyMap()
    ) : ComptimeValue {
        override val sourceText: String = text
        override val canonicalKind: String = "stmt"
        override val canonicalText: String = canonicalSyntax(text, references)
    }

    data class CtDeclaration(
        val text: String,
        override val nodeId: NodeId,
        override val origin: Origin,
        override val references: Map<NodeId, SymbolId> = emptyMap()
    ) : ComptimeValue {
        override val sourceText: String = text
        override val canonicalKind: String = "decl"
        override val canonicalText: String = canonicalSyntax(text, references)
    }

    data class CtMember(
        val text: String,
        override val nodeId: NodeId,
        override val origin: Origin,
        override val references: Map<NodeId, SymbolId> = emptyMap()
    ) : ComptimeValue {
        override val sourceText: String = text
        override val canonicalKind: String = "member"
        override val canonicalText: String = canonicalSyntax(text, references)
    }

    data class CtUnit(
        val text: String,
        override val nodeId: NodeId,
        override val origin: Origin,
        override val references: Map<NodeId, SymbolId> = emptyMap()
    ) : ComptimeValue {
        override val sourceText: String = text
        override val canonicalKind: String = "unit"
        override val canonicalText: String = canonicalSyntax(text, references)
    }

    data class CtCpx(
        val text: String,
        override val nodeId: NodeId,
        override val origin: Origin,
        override val references: Map<NodeId, SymbolId> = emptyMap()
    ) : ComptimeValue {
        override val sourceText: String = text
        override val canonicalKind: String = "cpx"
        override val canonicalText: String = canonicalSyntax(text, references)
    }

    data class CtList(
        val text: String,
        val values: List<ComptimeValue>
    ) : ComptimeValue {
        override val sourceText: String = text
        override val canonicalKind: String = "list"
        override val canonicalText: String = values.joinToString(",", prefix = "[", postfix = "]") {
            "${it.canonicalKind}:${it.canonicalText}"
        }
    }
}

private fun canonicalSyntax(text: String, references: Map<NodeId, SymbolId> = emptyMap()): String {
    val fragment = SourceFile(SourceFileId(Int.MIN_VALUE), Path.of("cpx-canonical.cp"), text, 1)
    val tokens = Lexer().lex(fragment).tokens
        .filterNot { it.kind == TokenKind.END_OF_FILE }
        .joinToString(" ") { token ->
            when (token.kind) {
                TokenKind.INTEGER_LITERAL -> token.lexeme.toBigIntegerOrNull()?.toString() ?: token.lexeme
                TokenKind.FLOAT_LITERAL -> token.lexeme.toBigDecimalOrNull()?.stripTrailingZeros()?.toPlainString() ?: token.lexeme
                else -> token.lexeme
            }
        }
    if (references.isEmpty()) return tokens
    val referenceEncoding = references.entries
        .sortedBy { it.key.value }
        .joinToString(",") { "${it.key.value}=${it.value.value}" }
    return "$tokens|refs:$referenceEncoding"
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
                        is ComptimeValue.CtType -> append(if (node.explicit) value.canonicalSyntax ?: value.identifierText else value.sourceText)
                        else -> append(value?.sourceText ?: node.name)
                    }
                }
            }
        }
    }

    /**
     * Explicit interpolation is identifier composition.  Syntax-bearing
     * values must be inserted at a complete syntax boundary instead; allowing
     * them into an identifier would turn a valid CPX into token soup and move
     * the error to a later parser/backend phase.
     */
    fun interpolationErrors(bindings: Map<String, ComptimeValue>): List<String> = buildList {
        nodes.filterIsInstance<TemplateNode.Binding>()
            .filter { it.explicit }
            .forEach { binding ->
                val value = bindings[binding.name] ?: return@forEach
                if (value !is ComptimeValue.CtType &&
                    value !is ComptimeValue.CtIdentifier &&
                    value !is ComptimeValue.CtInteger &&
                    value !is ComptimeValue.CtFloat &&
                    value !is ComptimeValue.CtBoolean
                ) {
                    add("compile-time value '${binding.name}' of kind '${value.canonicalKind}' cannot be composed into an identifier")
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
        if (bindingNames.isEmpty()) return CpxTemplate(category, listOf(TemplateNode.Literal(text)), origin)

        // Scan the template instead of applying a regular expression over the
        // complete source.  A binding-looking token in a string or comment is
        // literal C+ text; only explicit braces can opt into interpolation
        // there in a future template-string extension.
        val nodes = mutableListOf<TemplateNode>()
        var literalStart = 0
        var cursor = 0
        var state = TemplateLexState.CODE
        while (cursor < text.length) {
            when (state) {
                TemplateLexState.CODE -> when {
                    text[cursor] == '/' && text.getOrNull(cursor + 1) == '/' -> {
                        cursor += 2
                        state = TemplateLexState.LINE_COMMENT
                    }
                    text[cursor] == '/' && text.getOrNull(cursor + 1) == '*' -> {
                        cursor += 2
                        state = TemplateLexState.BLOCK_COMMENT
                    }
                    text[cursor] == '"' -> {
                        cursor++
                        state = TemplateLexState.STRING
                    }
                    text[cursor] == '\'' -> {
                        cursor++
                        state = TemplateLexState.CHARACTER
                    }
                    text[cursor] == '{' -> {
                        val explicit = explicitBindingAt(text, cursor, bindingNames)
                        if (explicit != null) {
                            if (cursor > literalStart) nodes += directBindings(text.substring(literalStart, cursor), bindingNames)
                            nodes += TemplateNode.Binding(explicit.first, explicit = true)
                            cursor = explicit.second
                            literalStart = cursor
                        } else {
                            cursor++
                        }
                    }
                    else -> cursor++
                }
                TemplateLexState.STRING,
                TemplateLexState.CHARACTER -> {
                    if (text[cursor] == '\\') cursor += 2 else {
                        if ((state == TemplateLexState.STRING && text[cursor] == '"') ||
                            (state == TemplateLexState.CHARACTER && text[cursor] == '\'')) {
                            state = TemplateLexState.CODE
                        }
                        cursor++
                    }
                }
                TemplateLexState.LINE_COMMENT -> {
                    cursor++
                    if (text[cursor - 1] == '\n') state = TemplateLexState.CODE
                }
                TemplateLexState.BLOCK_COMMENT -> {
                    if (text[cursor] == '*' && text.getOrNull(cursor + 1) == '/') {
                        cursor += 2
                        state = TemplateLexState.CODE
                    } else cursor++
                }
            }
        }
        if (literalStart < text.length) nodes += directBindings(text.substring(literalStart), bindingNames)
        return CpxTemplate(category, nodes, origin)
    }

    private fun directBindings(text: String, bindingNames: Set<String>): List<TemplateNode> {
        if (text.isEmpty() || bindingNames.isEmpty()) return if (text.isEmpty()) emptyList() else listOf(TemplateNode.Literal(text))
        val result = mutableListOf<TemplateNode>()
        var literalStart = 0
        var cursor = 0
        var state = TemplateLexState.CODE
        while (cursor < text.length) {
            when (state) {
                TemplateLexState.CODE -> when {
                    text[cursor] == '/' && text.getOrNull(cursor + 1) == '/' -> {
                        cursor += 2
                        state = TemplateLexState.LINE_COMMENT
                    }
                    text[cursor] == '/' && text.getOrNull(cursor + 1) == '*' -> {
                        cursor += 2
                        state = TemplateLexState.BLOCK_COMMENT
                    }
                    text[cursor] == '"' -> {
                        cursor++
                        state = TemplateLexState.STRING
                    }
                    text[cursor] == '\'' -> {
                        cursor++
                        state = TemplateLexState.CHARACTER
                    }
                    text[cursor].isLetter() || text[cursor] == '_' -> {
                        val start = cursor++
                        while (cursor < text.length && (text[cursor].isLetterOrDigit() || text[cursor] == '_')) cursor++
                        val token = text.substring(start, cursor)
                        if (token in bindingNames) {
                            if (start > literalStart) result += TemplateNode.Literal(text.substring(literalStart, start))
                            result += TemplateNode.Binding(token, explicit = false)
                            literalStart = cursor
                        }
                    }
                    else -> cursor++
                }
                TemplateLexState.STRING,
                TemplateLexState.CHARACTER -> {
                    if (text[cursor] == '\\') cursor += 2 else {
                        if ((state == TemplateLexState.STRING && text[cursor] == '"') ||
                            (state == TemplateLexState.CHARACTER && text[cursor] == '\'')) {
                            state = TemplateLexState.CODE
                        }
                        cursor++
                    }
                }
                TemplateLexState.LINE_COMMENT -> {
                    cursor++
                    if (text[cursor - 1] == '\n') state = TemplateLexState.CODE
                }
                TemplateLexState.BLOCK_COMMENT -> {
                    if (text[cursor] == '*' && text.getOrNull(cursor + 1) == '/') {
                        cursor += 2
                        state = TemplateLexState.CODE
                    } else cursor++
                }
            }
        }
        if (literalStart < text.length) result += TemplateNode.Literal(text.substring(literalStart))
        return result
    }

    private fun explicitBindingAt(text: String, start: Int, bindingNames: Set<String>): Pair<String, Int>? {
        if (text[start] != '{') return null
        var cursor = start + 1
        while (cursor < text.length && text[cursor].isWhitespace()) cursor++
        val nameStart = cursor
        if (cursor >= text.length || !(text[cursor].isLetter() || text[cursor] == '_')) return null
        cursor++
        while (cursor < text.length && (text[cursor].isLetterOrDigit() || text[cursor] == '_')) cursor++
        val name = text.substring(nameStart, cursor)
        if (name !in bindingNames) return null
        while (cursor < text.length && text[cursor].isWhitespace()) cursor++
        return if (cursor < text.length && text[cursor] == '}') name to (cursor + 1) else null
    }

    private enum class TemplateLexState { CODE, STRING, CHARACTER, LINE_COMMENT, BLOCK_COMMENT }
}

data class ExpansionKey(
    val functionName: String,
    val arguments: List<String>,
    val argumentKinds: List<String> = emptyList(),
    val argumentIdentities: List<String> = emptyList()
) {
    val canonical: String
        get() = "$functionName(${arguments.joinToString(",")})"

    val specializationKey: SpecializationKey
        get() = SpecializationKey(
            functionName,
            arguments.mapIndexed { index, argument ->
                CanonicalComptimeValue(
                    argumentKinds.getOrNull(index) ?: "type",
                    argumentIdentities.getOrNull(index) ?: argument
                )
            }
        )
}

data class CanonicalComptimeValue(
    val kind: String,
    val value: String
) {
    val canonical: String
        get() = "$kind:$value"
}

data class SpecializationKey(
    val declaration: String,
    val arguments: List<CanonicalComptimeValue>
) {
    val canonical: String
        get() = "$declaration(${arguments.joinToString(",") { it.canonical }})"
}

data class SpecializationCacheStatistics(
    val entries: Int,
    val hits: Long,
    val misses: Long
)

private data class SpecializationCacheEntry(
    val definitionFingerprint: String,
    val evaluation: ComptimeEvaluationResult
)

/**
 * Definition-sensitive cache for rendered structural CPX templates.
 *
 * The cache stores only canonical rendered text. Each invocation is still
 * reparsed and re-originated at its own call site, so cached output cannot
 * leak source locations between modules or suppress parser diagnostics.
 */
class SpecializationCache {
    private val entries = linkedMapOf<SpecializationKey, SpecializationCacheEntry>()
    private var hitCount = 0L
    private var missCount = 0L

    @Synchronized
    fun get(key: SpecializationKey, definitionFingerprint: String): String? {
        return getEvaluation(key, definitionFingerprint)?.renderedText
    }

    @Synchronized
    fun getEvaluation(key: SpecializationKey, definitionFingerprint: String): ComptimeEvaluationResult? {
        val entry = entries[key]
        if (entry == null || entry.definitionFingerprint != definitionFingerprint) {
            missCount++
            if (entry != null) entries.remove(key)
            return null
        }
        hitCount++
        return entry.evaluation
    }

    @Synchronized
    fun put(key: SpecializationKey, definitionFingerprint: String, instantiatedText: String) {
        putEvaluation(key, definitionFingerprint, ComptimeEvaluationResult(instantiatedText))
    }

    @Synchronized
    fun putEvaluation(
        key: SpecializationKey,
        definitionFingerprint: String,
        evaluation: ComptimeEvaluationResult
    ) {
        entries[key] = SpecializationCacheEntry(definitionFingerprint, evaluation)
    }

    @Synchronized
    fun invalidate(keys: Set<SpecializationKey>) {
        keys.forEach(entries::remove)
    }

    @Synchronized
    fun clear() {
        entries.clear()
        hitCount = 0L
        missCount = 0L
    }

    @Synchronized
    fun statistics(): SpecializationCacheStatistics = SpecializationCacheStatistics(
        entries.size,
        hitCount,
        missCount
    )
}

sealed interface ComptimeDependency {
    data class Symbol(val name: String) : ComptimeDependency
    data class Type(val name: String) : ComptimeDependency
    data class Module(val name: String) : ComptimeDependency
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

enum class TypeUniverseAccess {
    EARLY_SAFE,
    FULL
}

data class StructuralTypeReference(
    val name: String,
    val pointerDepth: Int = 0,
    val declarationKind: String = "named"
)

data class StructuralFieldDescriptor(
    val name: String,
    val type: String,
    val pointerDepth: Int = 0,
    val arrayDimensions: List<String> = emptyList(),
    val typeReference: StructuralTypeReference = StructuralTypeReference(type, pointerDepth)
)

data class StructuralMethodDescriptor(
    val name: String,
    val returnType: String,
    val parameters: List<String>,
    val isStatic: Boolean = false,
    val returnTypeReference: StructuralTypeReference = StructuralTypeReference(returnType),
    val parameterTypeReferences: List<StructuralTypeReference> = parameters.map(::StructuralTypeReference)
)

data class StructuralLayout(
    val representation: String,
    val fieldOrder: List<String>,
    val isSized: Boolean,
    val size: Long? = null,
    val alignment: Long? = null
)

data class StructuralTypeDescriptor(
    val name: String,
    val kind: String,
    val fields: List<StructuralFieldDescriptor> = emptyList(),
    val methods: List<StructuralMethodDescriptor> = emptyList(),
    val layout: StructuralLayout? = null,
    val aliasTarget: StructuralTypeReference? = null,
    val enumValues: List<String> = emptyList(),
    val typeId: TypeId? = null
)

data class TypeUniverseSnapshot(
    val names: Set<String>,
    val access: TypeUniverseAccess,
    val descriptors: Map<String, StructuralTypeDescriptor> = emptyMap(),
    val typeIds: Map<TypeId, String> = emptyMap()
) {
    fun typeNamed(name: String): StructuralTypeDescriptor? = descriptors[name]
}

/** Structured, read-only reflection view available to compile-time evaluators. */
class ComptimeReflection(private val snapshot: TypeUniverseSnapshot) {
    fun names(): Set<String> = snapshot.names

    fun descriptor(name: String): StructuralTypeDescriptor? = snapshot.typeNamed(name)

    fun nameOf(typeId: TypeId): String? = snapshot.typeIds[typeId]

    fun descriptor(typeId: TypeId): StructuralTypeDescriptor? = nameOf(typeId)?.let(::descriptor)

    fun fieldsOf(name: String): List<StructuralFieldDescriptor> = descriptor(name)?.fields.orEmpty()

    fun methodsOf(name: String): List<StructuralMethodDescriptor> = descriptor(name)?.methods.orEmpty()

    fun enumValuesOf(name: String): List<String> = descriptor(name)?.enumValues.orEmpty()

    fun aliasTargetOf(name: String): StructuralTypeReference? = descriptor(name)?.aliasTarget

    fun kindOf(name: String): String? = descriptor(name)?.kind

    fun sizeOf(name: String): Long? = descriptor(name)?.layout?.size

    fun alignmentOf(name: String): Long? = descriptor(name)?.layout?.alignment

    val access: TypeUniverseAccess
        get() = snapshot.access
}

/** Mutable only during structural expansion and immutable after freeze. */
class ComptimeTypeUniverse {
    private val descriptors = linkedMapOf<String, StructuralTypeDescriptor>()
    private val typeIds = linkedMapOf<TypeId, String>()
    private var frozen = false

    fun register(name: String): Boolean = register(StructuralTypeDescriptor(name, "unknown"))

    fun register(descriptor: StructuralTypeDescriptor): Boolean {
        if (frozen) return false
        descriptor.typeId?.let { typeIds[it] = descriptor.name }
        if (descriptor.name in descriptors) return false
        descriptors[descriptor.name] = descriptor
        return true
    }

    fun registerAll(names: Iterable<String>) {
        names.forEach(::register)
    }

    fun registerDescriptors(descriptors: Iterable<StructuralTypeDescriptor>) {
        descriptors.forEach(::register)
    }

    fun freeze() {
        frozen = true
    }

    val isFrozen: Boolean
        get() = frozen

    fun snapshot(access: TypeUniverseAccess): TypeUniverseSnapshot {
        check(access != TypeUniverseAccess.FULL || frozen) {
            "full type-universe introspection requires the structural barrier"
        }
        return TypeUniverseSnapshot(
            descriptors.keys.toSet(),
            access,
            if (access == TypeUniverseAccess.FULL) descriptors.toMap() else emptyMap(),
            if (access == TypeUniverseAccess.FULL) typeIds.toMap() else emptyMap()
        )
    }
}

data class ExpansionTask(
    val invocation: SyntaxCpxInvocation,
    val definition: SyntaxComptimeFunction,
    val key: ExpansionKey,
    val callSite: NodeId = NodeId(-1),
    val parentExpansion: ExpansionId? = null,
    val ancestors: List<ExpansionKey> = emptyList(),
    val phase: CpxPhase = CpxPhase.STRUCTURAL,
    val dependencies: Set<ComptimeDependency> = emptySet()
) {
    val expansionId: ExpansionId
        get() = ExpansionId(definition.name, callSite, parentExpansion, key)
}

private data class DeferredCpxInvocation(
    val invocation: SyntaxCpxInvocation,
    val ancestors: List<ExpansionKey>,
    val parentExpansion: ExpansionId?
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
    private val published = linkedSetOf<ComptimeDependency>()
    private var structuralPhaseClosed = false
    val typeUniverse: ComptimeTypeUniverse = ComptimeTypeUniverse()

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
        published += ComptimeDependency.Expansion(key)
        states[key] = ComptimeTaskState.EXPANDED
    }

    /** Publish a semantic readiness token to every task waiting on it. */
    fun publish(dependency: ComptimeDependency) {
        published += dependency
    }

    fun publishAll(dependencies: Iterable<ComptimeDependency>) {
        published += dependencies
    }

    /**
     * Closes structural expansion only after no structural task remains
     * queued. The published token is the channel a reflective task waits on.
     */
    fun closeStructuralPhase(): Boolean {
        if (pending.any { it.phase == CpxPhase.STRUCTURAL }) return false
        typeUniverse.freeze()
        structuralPhaseClosed = true
        published += ComptimeDependency.StableTypeUniverse
        return true
    }

    val isStructuralPhaseClosed: Boolean
        get() = structuralPhaseClosed

    fun isPublished(dependency: ComptimeDependency): Boolean = dependency in published

    fun waitingDependencies(key: ExpansionKey): Set<ComptimeDependency> = pending
        .firstOrNull { it.key == key }
        ?.dependencies
        ?.filterNot(::dependencyReady)
        ?.toSet()
        .orEmpty()

    fun markFailed(key: ExpansionKey) {
        states[key] = ComptimeTaskState.FAILED
    }

    fun markBlocked(key: ExpansionKey) {
        states[key] = ComptimeTaskState.BLOCKED
    }

    fun state(key: ExpansionKey): ComptimeTaskState? = states[key]

    fun pendingKeys(): Set<ExpansionKey> = pending.map { it.key }.toSet()

    /**
     * Returns deterministic expansion-key cycles among currently pending
     * tasks. Non-expansion dependencies are deliberately excluded because
     * they are external readiness channels rather than task-to-task edges.
     */
    fun dependencyCycles(): List<List<ExpansionKey>> {
        val tasks = pending.associateBy { it.key }
        val cycles = linkedSetOf<List<ExpansionKey>>()
        val visiting = linkedSetOf<ExpansionKey>()
        val visited = mutableSetOf<ExpansionKey>()

        fun visit(key: ExpansionKey, path: List<ExpansionKey>) {
            if (key in visiting) {
                val start = path.indexOf(key)
                if (start >= 0) cycles += path.subList(start, path.size) + key
                return
            }
            if (!visited.add(key)) return
            val task = tasks[key] ?: return
            visiting += key
            task.dependencies
                .mapNotNull { (it as? ComptimeDependency.Expansion)?.key }
                .filter { it in tasks }
                .sortedBy(ExpansionKey::canonical)
                .forEach { visit(it, path + key) }
            visiting -= key
        }

        tasks.keys.sortedBy(ExpansionKey::canonical).forEach { visit(it, emptyList()) }
        return cycles.sortedBy { cycle -> cycle.joinToString(" -> ") { it.canonical } }
    }

    private fun dependenciesReady(task: ExpansionTask): Boolean =
        (task.phase != CpxPhase.STRUCTURAL || !isStructuralPhaseClosed) &&
            (task.phase != CpxPhase.REFLECTIVE || isStructuralPhaseClosed) &&
            task.dependencies.all(::dependencyReady)

    private fun dependencyReady(dependency: ComptimeDependency): Boolean = when (dependency) {
        is ComptimeDependency.Expansion -> dependency.key in expanded
        is ComptimeDependency.Symbol,
        is ComptimeDependency.Type,
        is ComptimeDependency.Module,
        ComptimeDependency.StableTypeUniverse -> dependency in published
    }

    val hasPending: Boolean
        get() = pending.isNotEmpty()

    val expandedKeys: Set<ExpansionKey>
        get() = expanded.toSet()

    val specializationKeys: Set<SpecializationKey>
        get() = expanded.map { it.specializationKey }.toSet()
}

data class CpxExpansionResult(
    val program: SyntaxProgram,
    val diagnostics: List<Diagnostic>,
    val expandedKeys: Set<ExpansionKey>,
    val specializationKeys: Set<SpecializationKey> = expandedKeys.map { it.specializationKey }.toSet(),
    val structuralFingerprint: String = structuralFingerprint(program),
    val syntaxArena: AstArena = AstArena(),
    val argumentValues: Map<ExpansionKey, List<ComptimeValue>> = emptyMap(),
    val evaluationResults: Map<ExpansionKey, ComptimeEvaluationResult> = emptyMap(),
    val expansionIds: List<ExpansionId> = emptyList()
)

/**
 * Fingerprints the stabilized structural declaration universe without source
 * ranges, origins, whitespace, or emitted C text.
 */
fun structuralFingerprint(program: SyntaxProgram): String = program.declarations
    .mapNotNull { declaration ->
        when (declaration) {
            is SyntaxPackage -> "package:${declaration.name}"
            is SyntaxAlias -> "alias:${declaration.name}:${structuralType(declaration.target)}:${declaration.arrayDimensions}"
            is SyntaxUnion -> "union:${declaration.name}:${declaration.fields.joinToString { structuralField(it) }}"
            is SyntaxEnum -> "enum:${declaration.name}:${declaration.values.joinToString { "${it.name}=${it.value}" }}"
            is SyntaxStruct -> "struct:${declaration.name}:${declaration.fields.joinToString { structuralField(it) }}:" +
                declaration.methods.joinToString { structuralFunction(it) }
            is SyntaxTrait -> "trait:${declaration.isPublic}:${declaration.targetName}:" +
                declaration.methods.joinToString { structuralFunction(it) }
            is SyntaxGlobalVariable -> "global:${declaration.name}:${structuralType(declaration.type)}:${declaration.arrayDimensions}"
            is SyntaxFunction -> "function:${structuralFunction(declaration)}"
            is SyntaxComptimeFunction,
            is SyntaxCpxInvocation,
            is SyntaxImport -> null
        }
    }
    .joinToString("|")

private fun structuralField(field: SyntaxField): String =
    "${field.name}:${structuralType(field.type)}:${field.arrayDimensions}"

private fun structuralFunction(function: SyntaxFunction): String =
    "${function.ownerName}:${function.name}:${structuralType(function.returnType)}:" +
        function.parameters.joinToString(",") {
            "${it.name}:${structuralType(it.type)}:${it.isReceiver}:${it.isPointerReceiver}"
        }

private fun structuralType(type: TypeSyntax): String =
    "${type.declarationKind}:${type.name}:${type.isStruct}:${type.pointerDepth}"

data class CpxExpansionLimits(
    val maxExpansionDepth: Int = 64,
    val maxExpansionTasks: Int = 10_000,
    val maxGeneratedDeclarations: Int = 10_000
) {
    init {
        require(maxExpansionDepth > 0) { "maxExpansionDepth must be positive" }
        require(maxExpansionTasks > 0) { "maxExpansionTasks must be positive" }
        require(maxGeneratedDeclarations > 0) { "maxGeneratedDeclarations must be positive" }
    }
}

class CpxExpander(
    private val lexer: Lexer = Lexer(),
    private val templateParser: CpxTemplateParser = CpxTemplateParser(),
    val specializationCache: SpecializationCache = SpecializationCache(),
    private val limits: CpxExpansionLimits = CpxExpansionLimits(),
    private val evaluator: ComptimeEvaluator = TemplateComptimeEvaluator(),
    private var target: ComptimeTargetInfo = ComptimeTargetInfo()
) {
    private val platformServices = setOf(
        "memory", "file", "process", "time", "threads", "sync", "atomics",
        "socket-transport", "dns"
    )

    @Synchronized
    fun configureTarget(target: ComptimeTargetInfo) {
        this.target = target
    }

    fun invalidateSpecializations(keys: Set<SpecializationKey>) {
        specializationCache.invalidate(keys)
    }

    fun expand(
        source: SourceFile,
        program: SyntaxProgram,
        typeResolver: ComptimeTypeResolver? = null,
        referenceResolver: ComptimeReferenceResolver? = null,
        typeDescriptors: Iterable<StructuralTypeDescriptor> = emptyList(),
        deferredInvocationNames: Set<String> = emptySet(),
        deferredInvocationPrefixes: Set<String> = emptySet(),
        importedDefinitions: Map<String, SyntaxComptimeFunction> = emptyMap()
    ): CpxExpansionResult {
        val diagnostics = DiagnosticBag()
        val syntaxArena = AstArena()
        val astBuilder = AstBuilder()
        val argumentValues = linkedMapOf<ExpansionKey, List<ComptimeValue>>()
        val specializationKeys = linkedSetOf<SpecializationKey>()
        val evaluationResults = linkedMapOf<ExpansionKey, ComptimeEvaluationResult>()
        val expansionIds = mutableListOf<ExpansionId>()
        val definitions = linkedMapOf<String, SyntaxComptimeFunction>()
        program.declarations
            .filterIsInstance<SyntaxComptimeFunction>()
            .forEach { definitions[it.name] = it }
        importedDefinitions.forEach { (name, definition) -> definitions.putIfAbsent(name, definition) }
        val scheduler = ComptimeScheduler()
        val deferredInvocations = mutableListOf<DeferredCpxInvocation>()

        fun queueInvocation(
            invocation: SyntaxCpxInvocation,
            ancestors: List<ExpansionKey> = emptyList(),
            parentExpansion: ExpansionId? = null
        ) {
            if (invocation.name == "require_service") {
                val argument = invocation.arguments.singleOrNull()
                val service = argument?.let {
                    Regex("^\"([a-z][a-z0-9-]*)\"$").matchEntire(it)?.groupValues?.get(1)
                }
                if (service == null) {
                    diagnostics.error(
                        "require_service expects exactly one quoted canonical service name",
                        invocation.range,
                        "CPX603"
                    )
                } else if (service !in platformServices) {
                    diagnostics.error(
                        "unknown platform service '$service'",
                        invocation.range,
                        "CPX603"
                    )
                } else {
                    if (!target.hasService(service)) {
                        diagnostics.error(
                            "target '${target.os}-${target.architecture}' does not provide platform service '$service'",
                            invocation.range,
                            "CPX603"
                        )
                    }
                }
                return
            }
            val callSite = syntaxArena.add(astBuilder.buildDeclaration(invocation))
            val definition = definitions[invocation.name]
            if (definition == null) {
                deferredInvocations += DeferredCpxInvocation(invocation, ancestors, parentExpansion)
            } else {
                val task = taskFor(invocation, definition, callSite, parentExpansion, ancestors, typeResolver)
                expansionIds += task.expansionId
                scheduler.enqueue(task)
            }
        }

        fun resolveDeferredInvocations() {
            val iterator = deferredInvocations.iterator()
            while (iterator.hasNext()) {
                val deferred = iterator.next()
                val definition = definitions[deferred.invocation.name] ?: continue
                val phase = phaseFor(definition.category)
                if (scheduler.isStructuralPhaseClosed && phase == CpxPhase.STRUCTURAL) {
                    diagnostics.error(
                        "reflective CPX cannot schedule structural invocation '${deferred.invocation.name}' after type-universe stabilization",
                        deferred.invocation.origin.primaryRange,
                        "CPX008"
                    )
                } else {
                    val callSite = syntaxArena.add(astBuilder.buildDeclaration(deferred.invocation))
                    val task = taskFor(
                        deferred.invocation,
                        definition,
                        callSite,
                        deferred.parentExpansion,
                        deferred.ancestors,
                        typeResolver
                    )
                    expansionIds += task.expansionId
                    scheduler.enqueue(task)
                }
                iterator.remove()
            }
        }

        scheduler.typeUniverse.registerDescriptors(typeDescriptors)
        registerStructuralDeclarations(scheduler.typeUniverse, program.declarations)
        program.declarations.filterIsInstance<SyntaxCpxInvocation>().forEach { invocation ->
            queueInvocation(invocation)
        }

        val generated = mutableListOf<SyntaxDeclaration>()
        var generatedFileIndex = 0
        var expandedTaskCount = 0
        while (scheduler.hasPending) {
            val task = scheduler.next()
            if (task == null) {
                if (!scheduler.isStructuralPhaseClosed && scheduler.closeStructuralPhase()) continue
                scheduler.pendingKeys().forEach(scheduler::markBlocked)
                val cycle = scheduler.dependencyCycles().firstOrNull()
                val cycleText = cycle?.joinToString(" -> ") { it.canonical }
                diagnostics.error(
                    if (cycleText != null) {
                        "compile-time dependency cycle: $cycleText"
                    } else {
                        "compile-time dependency cycle or unsatisfied phase barrier: ${scheduler.pendingKeys().joinToString { it.canonical }}"
                    },
                    program.origin.primaryRange,
                    "CPX006"
                )
                break
            }
            if (expandedTaskCount >= limits.maxExpansionTasks) {
                scheduler.markBlocked(task.key)
                scheduler.pendingKeys().forEach(scheduler::markBlocked)
                diagnostics.error(
                    "compile-time expansion task limit exceeded (${limits.maxExpansionTasks})",
                    task.invocation.origin.primaryRange,
                    "CPX007"
                )
                break
            }
            expandedTaskCount++
            if (task.key in task.ancestors) {
                diagnostics.error("compile-time expansion cycle detected at ${task.key.canonical}", task.invocation.origin.primaryRange, "CPX002")
                continue
            }
            if (task.ancestors.size >= limits.maxExpansionDepth) {
                scheduler.markFailed(task.key)
                diagnostics.error(
                    "compile-time expansion depth limit exceeded (${limits.maxExpansionDepth}) at ${task.key.canonical}",
                    task.invocation.origin.primaryRange,
                    "CPX007"
                )
                continue
            }
            if (scheduler.wasExpanded(task.key)) continue
            scheduler.markExpanded(task.key)
            if (!isAllowedCategory(task)) {
                diagnostics.error(
                    if (task.phase == CpxPhase.STRUCTURAL) {
                        "structural expansion requires a declaration or unit CPX, got '${task.definition.category}'"
                    } else {
                        "reflective expansion requires a statement or expression CPX, got '${task.definition.category}'"
                    },
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
            val values = task.definition.parameters.zip(task.invocation.arguments).mapNotNull { (parameter, argument) ->
                parseComptimeValue(
                    parameter.kind,
                    argument,
                    task.invocation,
                    source,
                    diagnostics,
                    typeResolver,
                    referenceResolver,
                    syntaxArena,
                    astBuilder
                )
            }
            if (values.size != task.definition.parameters.size) {
                continue
            }
            argumentValues[task.key] = values
            val specializationKey = SpecializationKey(
                task.definition.name,
                values.map { value -> CanonicalComptimeValue(value.canonicalKind, value.canonicalText) }
            )
            specializationKeys += specializationKey

            val category = parseCategory(task.definition.category)
            val definitionFingerprint = buildString {
                append(task.definition.name)
                append('|')
                append(task.definition.category)
                append('|')
                append(task.definition.parameters.joinToString(",") { "${it.kind}:${it.name}" })
                append('|')
                append(canonicalSyntax(task.definition.template))
            }
            val context = ComptimeContext(
                module = source.path.fileName.toString(),
                scope = cplus.semantic.ScopeId(1),
                containingType = null,
                containingFunction = null,
                phase = task.phase,
                target = target,
                sourceOrigin = task.invocation.origin,
                expansion = task.expansionId,
                typeUniverse = scheduler.typeUniverse.snapshot(
                    if (task.phase == CpxPhase.REFLECTIVE) TypeUniverseAccess.FULL else TypeUniverseAccess.EARLY_SAFE
                )
            )
            val template = templateParser.parse(
                task.definition.template,
                category,
                task.definition.origin,
                task.definition.parameters.map { it.name }.toSet()
            )
            val bindings = task.definition.parameters.zip(values).associate { (parameter, value) ->
                parameter.name to value
            }
            val interpolationErrors = template.interpolationErrors(bindings)
            interpolationErrors.forEach { message ->
                diagnostics.error(message, task.invocation.origin.primaryRange, "CPX011")
            }
            if (interpolationErrors.isNotEmpty()) {
                scheduler.markFailed(task.key)
                continue
            }
            val cached = specializationCache.getEvaluation(specializationKey, definitionFingerprint)
            val instantiated = if (cached != null) {
                evaluationResults[task.key] = cached
                diagnostics.addAll(cached.diagnostics)
                scheduler.publishAll(cached.channels.dependencies)
                cached.renderedText
            } else {
                val evaluation = evaluator.evaluate(task.definition.name, template, bindings, context)
                evaluationResults[task.key] = evaluation
                diagnostics.addAll(evaluation.diagnostics)
                scheduler.publishAll(evaluation.channels.dependencies)
                specializationCache.putEvaluation(specializationKey, definitionFingerprint, evaluation)
                evaluation.renderedText
            }
            val generatedFile = SourceFile(
                SourceFileId(-(++generatedFileIndex)),
                syntheticSibling(source.path, "cpx-expansion-$generatedFileIndex.cp"),
                instantiated,
                source.version
            )
            val parsed = Parser(lexer.lex(generatedFile)).parse()
            diagnostics.addAll(parsed.diagnostics)
            val expansionOrigin = Origin.Expansion(
                task.definition.origin,
                task.invocation.origin,
                if (task.ancestors.isEmpty()) null else task.invocation.origin,
                task.key.canonical
            )
            val declarations = parsed.syntax.declarations
                .map { reorigin(it, expansionOrigin) }
                .map { hygienize(it, task.key) }
            val structuralDeclarations = declarations.filter(::isStructuralDeclaration)
            if (task.phase == CpxPhase.REFLECTIVE && structuralDeclarations.isNotEmpty()) {
                diagnostics.error(
                    "reflective CPX '${task.definition.name}' cannot introduce structural declarations: " +
                        structuralDeclarations.joinToString { declarationName(it) },
                    task.invocation.origin.primaryRange,
                    "CPX008"
                )
            }
            if (task.phase == CpxPhase.STRUCTURAL) {
                registerStructuralDeclarations(scheduler.typeUniverse, structuralDeclarations)
            }
            val acceptedDeclarations = if (task.phase == CpxPhase.REFLECTIVE) {
                declarations.filterNot(::isStructuralDeclaration)
            } else {
                declarations
            }
            acceptedDeclarations
                .filterIsInstance<SyntaxComptimeFunction>()
                .forEach { definitions.putIfAbsent(it.name, it) }
            resolveDeferredInvocations()
            if (generated.size + acceptedDeclarations.count { it !is SyntaxComptimeFunction && it !is SyntaxCpxInvocation } > limits.maxGeneratedDeclarations) {
                scheduler.markFailed(task.key)
                diagnostics.error(
                    "compile-time generated declaration limit exceeded (${limits.maxGeneratedDeclarations})",
                    task.invocation.origin.primaryRange,
                    "CPX007"
                )
                break
            }
            generated += acceptedDeclarations.filterNot { it is SyntaxComptimeFunction || it is SyntaxCpxInvocation }
            acceptedDeclarations.filterIsInstance<SyntaxCpxInvocation>().forEach { invocation ->
                queueInvocation(invocation, task.ancestors + task.key, task.expansionId)
            }
        }

        resolveDeferredInvocations()
        deferredInvocations.forEach { deferred ->
            val name = deferred.invocation.name
            val deferredToWorkspace = name in deferredInvocationNames ||
                deferredInvocationPrefixes.any { prefix -> name.startsWith("$prefix.") }
            if (!deferredToWorkspace) {
                diagnostics.error("unknown compile-time function '$name'", deferred.invocation.origin.primaryRange, "CPX001")
            }
        }
        if (scheduler.pendingKeys().isEmpty()) scheduler.closeStructuralPhase()
        val retained = program.declarations.filterNot { it is SyntaxComptimeFunction || it is SyntaxCpxInvocation }
        val range = program.range
        return CpxExpansionResult(
            SyntaxProgram(retained + generated, range, program.origin),
            diagnostics.diagnostics,
            scheduler.expandedKeys,
            specializationKeys,
            syntaxArena = syntaxArena,
            argumentValues = argumentValues,
            evaluationResults = evaluationResults,
            expansionIds = expansionIds
        )
    }

    private fun taskFor(
        invocation: SyntaxCpxInvocation,
        definition: SyntaxComptimeFunction,
        callSite: NodeId,
        parentExpansion: ExpansionId? = null,
        ancestors: List<ExpansionKey> = emptyList(),
        typeResolver: ComptimeTypeResolver? = null
    ): ExpansionTask = ExpansionTask(
        invocation,
        definition,
        ExpansionKey(
            definition.name,
            invocation.arguments.mapIndexed { index, argument ->
                canonicalArgument(definition.parameters.getOrNull(index)?.kind, argument)
            },
            definition.parameters
                .map { normalizeParameterKind(it.kind) }
                .takeUnless { kinds -> kinds.all { it == "type" } }
                .orEmpty(),
            invocation.arguments.mapIndexed { index, argument ->
                if (normalizeParameterKind(definition.parameters.getOrNull(index)?.kind ?: "type") != "type") {
                    ""
                } else {
                    typeResolver?.resolve(argument.trim())?.typeId?.let { "id:${it.value}" }.orEmpty()
                }
            }.takeUnless { identities -> identities.all(String::isEmpty) }.orEmpty()
        ),
        callSite,
        parentExpansion,
        ancestors,
        phase = phaseFor(definition.category)
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

    private fun phaseFor(category: String): CpxPhase = when (category.lowercase()) {
        "stmt", "statement", "expr", "expression" -> CpxPhase.REFLECTIVE
        else -> CpxPhase.STRUCTURAL
    }

    private fun isAllowedCategory(task: ExpansionTask): Boolean = when (task.phase) {
        CpxPhase.STRUCTURAL -> task.definition.category.lowercase() in setOf("decl", "unit", "member", "type")
        CpxPhase.REFLECTIVE -> task.definition.category.lowercase() in setOf("stmt", "statement", "expr", "expression")
    }

    private fun isStructuralDeclaration(declaration: SyntaxDeclaration): Boolean = when (declaration) {
        is SyntaxAlias,
        is SyntaxEnum,
        is SyntaxUnion,
        is SyntaxStruct,
        // Traits do not create layout types, but registering their methods
        // changes the callable universe and must happen before reflection.
        is SyntaxTrait -> true
        else -> false
    }

    private fun declarationName(declaration: SyntaxDeclaration): String = when (declaration) {
        is SyntaxAlias -> "alias ${declaration.name}"
        is SyntaxEnum -> "enum ${declaration.name}"
        is SyntaxUnion -> "union ${declaration.name}"
        is SyntaxStruct -> "struct ${declaration.name}"
        is SyntaxTrait -> "trait for ${declaration.targetName}"
        else -> declaration::class.simpleName ?: "declaration"
    }

    private fun normalizeParameterKind(kind: String): String = when (kind.trim().lowercase()) {
        "integer" -> "int"
        "boolean" -> "bool"
        "values" -> "list"
        "expression" -> "expr"
        "statement" -> "stmt"
        "declaration" -> "decl"
        else -> kind.trim().lowercase()
    }

    private fun canonicalArgument(kind: String?, argument: String): String {
        val normalized = normalizeParameterKind(kind ?: "type")
        val trimmed = argument.trim()
        return when (normalized) {
            "type" -> trimmed
                .replace(Regex("\\s+"), " ")
                .removePrefix("struct ")
                .trim()
            "int" -> trimmed.toBigIntegerOrNull()?.toString() ?: canonicalSyntax(trimmed)
            "float" -> trimmed.toBigDecimalOrNull()?.stripTrailingZeros()?.toPlainString() ?: canonicalSyntax(trimmed)
            "bool" -> trimmed.lowercase()
            else -> canonicalSyntax(trimmed)
        }
    }

    private fun parseComptimeValue(
        kind: String,
        argument: String,
        invocation: SyntaxCpxInvocation,
        source: SourceFile,
        diagnostics: DiagnosticBag,
        typeResolver: ComptimeTypeResolver?,
        referenceResolver: ComptimeReferenceResolver?,
        syntaxArena: AstArena,
        astBuilder: AstBuilder
    ): ComptimeValue? {
        val normalized = normalizeParameterKind(kind)
        val text = argument.trim()
        fun invalid(expected: String): ComptimeValue? {
            diagnostics.error(
                "CPX argument '$text' is not a valid $expected compile-time value",
                invocation.origin.primaryRange,
                "CPX010"
            )
            return null
        }
        if (text.isEmpty()) return invalid(normalized)
        return when (normalized) {
            "type" -> {
                val normalizedText = text.replace(Regex("\\s+"), " ")
                if (!isTypeSyntax(normalizedText, source)) return invalid("type")
                val identity = typeResolver?.resolve(normalizedText)
                ComptimeValue.CtType(
                    normalizedText,
                    normalizedText.removePrefix("struct ").trim(),
                    identity?.typeId,
                    identity?.canonicalTypeId,
                    identity?.canonicalText
                )
            }
            "identifier" -> if (identifierPattern.matches(text)) ComptimeValue.CtIdentifier(text) else invalid("identifier")
            "int" -> parseIntegerLiteral(text)?.let { ComptimeValue.CtInteger(text, it) } ?: invalid("integer")
            "float" -> parseFloatingLiteral(text)?.let { ComptimeValue.CtFloat(text, it) } ?: invalid("floating-point value")
            "bool" -> when (text.lowercase()) {
                "true" -> ComptimeValue.CtBoolean(true, text)
                "false" -> ComptimeValue.CtBoolean(false, text)
                else -> invalid("boolean")
            }
            "string" -> if (text.length >= 2 && text.first() == '"' && text.last() == '"') {
                ComptimeValue.CtString(text)
            } else invalid("string")
            "list" -> {
                if (!text.startsWith("[") || !text.endsWith("]")) return invalid("list")
                val inner = text.substring(1, text.length - 1).trim()
                if (inner.isEmpty()) {
                    ComptimeValue.CtList(text, emptyList())
                } else {
                    val values = splitTopLevel(inner)
                        .map { element ->
                            parseComptimeValue(
                                inferListElementKind(element, typeResolver),
                                element,
                                invocation,
                                source,
                                diagnostics,
                                typeResolver,
                                referenceResolver,
                                syntaxArena,
                                astBuilder
                            )
                        }
                    if (values.any { it == null }) null else ComptimeValue.CtList(text, values.filterNotNull())
                }
            }
            "expr", "stmt", "decl", "member", "unit", "cpx" -> {
                val capturedOrigin = Origin.Generated(invocation.origin)
                val parsedNode = parseSyntaxValueNode(normalized, text, source, diagnostics, astBuilder)
                    ?: return invalid(normalized)
                val node = captureOrigin(parsedNode, capturedOrigin)
                val nodeId = syntaxArena.add(node)
                val references = referenceResolver?.resolve(node, syntaxArena).orEmpty()
                when (normalized) {
                    "expr" -> ComptimeValue.CtExpression(text, nodeId, capturedOrigin, references)
                    "stmt" -> ComptimeValue.CtStatement(text, nodeId, capturedOrigin, references)
                    "decl" -> ComptimeValue.CtDeclaration(text, nodeId, capturedOrigin, references)
                    "member" -> ComptimeValue.CtMember(text, nodeId, capturedOrigin, references)
                    "unit" -> ComptimeValue.CtUnit(text, nodeId, capturedOrigin, references)
                    else -> ComptimeValue.CtCpx(text, nodeId, capturedOrigin, references)
                }
            }
            else -> {
                diagnostics.error(
                    "unsupported compile-time parameter kind '$kind'",
                    invocation.origin.primaryRange,
                    "CPX005"
                )
                null
            }
        }
    }

    private fun inferListElementKind(text: String, typeResolver: ComptimeTypeResolver?): String {
        val value = text.trim()
        return when {
            value.startsWith("[") && value.endsWith("]") -> "list"
            typeResolver?.resolve(value) != null -> "type"
            parseIntegerLiteral(value) != null -> "int"
            parseFloatingLiteral(value) != null -> "float"
            value.equals("true", ignoreCase = true) || value.equals("false", ignoreCase = true) -> "bool"
            value.length >= 2 && value.first() == '"' && value.last() == '"' -> "string"
            value.matches(identifierPattern) && value in primitiveTypeNames -> "type"
            value.matches(identifierPattern) -> "identifier"
            else -> "expr"
        }
    }

    private fun isTypeSyntax(text: String, source: SourceFile): Boolean {
        val fragment = source.copy(
            id = SourceFileId(-source.id.value - 2),
            path = syntheticSibling(source.path, "cpx-type.cp"),
            text = "$text __cpx_type;"
        )
        val parsed = Parser(lexer.lex(fragment)).parse()
        return parsed.syntax.declarations.singleOrNull() is SyntaxGlobalVariable &&
            parsed.diagnostics.none { it.severity == DiagnosticSeverity.ERROR }
    }

    private fun parseIntegerLiteral(text: String): BigInteger? {
        val normalized = text.replace(Regex("^([+-])\\s+"), "$1")
        val sign = when {
            normalized.startsWith("-") -> -1
            else -> 1
        }
        val unsigned = normalized.removePrefix("+").removePrefix("-")
        if (unsigned.isEmpty()) return null
        return try {
            when {
                unsigned.startsWith("0x", ignoreCase = true) -> BigInteger(unsigned.substring(2), 16) * BigInteger.valueOf(sign.toLong())
                unsigned.length > 1 && unsigned.startsWith("0") -> BigInteger(unsigned.substring(1), 8) * BigInteger.valueOf(sign.toLong())
                unsigned.all(Char::isDigit) -> BigInteger(unsigned) * BigInteger.valueOf(sign.toLong())
                else -> null
            }
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun parseFloatingLiteral(text: String): BigDecimal? = text
        .replace(Regex("^([+-])\\s+"), "$1")
        .toBigDecimalOrNull()

    private fun splitTopLevel(text: String): List<String> {
        val result = mutableListOf<String>()
        var start = 0
        var parentheses = 0
        var brackets = 0
        var braces = 0
        var inString = false
        var escaped = false
        text.forEachIndexed { index, character ->
            if (inString) {
                if (escaped) escaped = false
                else if (character == '\\') escaped = true
                else if (character == '"') inString = false
                return@forEachIndexed
            }
            when (character) {
                '"' -> inString = true
                '(' -> parentheses++
                ')' -> parentheses--
                '[' -> brackets++
                ']' -> brackets--
                '{' -> braces++
                '}' -> braces--
                ',' -> if (parentheses == 0 && brackets == 0 && braces == 0) {
                    result += text.substring(start, index).trim()
                    start = index + 1
                }
            }
        }
        result += text.substring(start).trim()
        return result.filter(String::isNotEmpty)
    }

    private fun captureOrigin(node: AstNode, origin: Origin): AstNode = when (node) {
        is AstProgram -> node.copy(
            declarations = node.declarations.map { captureOriginDeclaration(it, origin) },
            origin = origin
        )
        is AstPackage -> node.copy(origin = origin)
        is AstAlias -> node.copy(target = captureOriginType(node.target, origin), origin = origin)
        is AstUnion -> node.copy(
            fields = node.fields.map { captureOriginField(it, origin) },
            origin = origin
        )
        is AstEnum -> node.copy(
            values = node.values.map { it.copy(origin = origin) },
            origin = origin
        )
        is AstEnumValue -> node.copy(origin = origin)
        is AstTypeRef -> captureOriginType(node, origin)
        is AstStruct -> node.copy(
            fields = node.fields.map { captureOriginField(it, origin) },
            methods = node.methods.map { captureOriginFunction(it, origin) },
            origin = origin
        )
        is AstTrait -> node.copy(
            targetOrigin = origin,
            methods = node.methods.map { captureOriginFunction(it, origin) },
            origin = origin
        )
        is AstField -> captureOriginField(node, origin)
        is AstGlobalVariable -> node.copy(
            type = captureOriginType(node.type, origin),
            initializer = node.initializer?.let { captureOriginExpression(it, origin) },
            origin = origin
        )
        is AstComptimeFunction -> node.copy(origin = origin)
        is AstCpxInvocation -> node.copy(origin = origin)
        is AstImport -> node.copy(origin = origin)
        is AstFunction -> captureOriginFunction(node, origin)
        is AstParameter -> node.copy(type = captureOriginType(node.type, origin), origin = origin)
        is AstBlock -> node.copy(statements = node.statements.map { captureOriginStatement(it, origin) }, origin = origin)
        is AstReturn -> node.copy(expression = node.expression?.let { captureOriginExpression(it, origin) }, origin = origin)
        is AstExpressionStatement -> node.copy(expression = captureOriginExpression(node.expression, origin), origin = origin)
        is AstDefer -> node.copy(expression = captureOriginExpression(node.expression, origin), origin = origin)
        is AstIf -> node.copy(
            condition = captureOriginExpression(node.condition, origin),
            thenBranch = captureOriginStatement(node.thenBranch, origin),
            elseBranch = node.elseBranch?.let { captureOriginStatement(it, origin) },
            origin = origin
        )
        is AstWhile -> node.copy(
            condition = captureOriginExpression(node.condition, origin),
            body = captureOriginStatement(node.body, origin),
            origin = origin
        )
        is AstFor -> node.copy(
            initializer = node.initializer?.let { captureOriginStatement(it, origin) },
            condition = node.condition?.let { captureOriginExpression(it, origin) },
            increment = node.increment?.let { captureOriginExpression(it, origin) },
            body = captureOriginStatement(node.body, origin),
            origin = origin
        )
        is AstBreak -> node.copy(origin = origin)
        is AstContinue -> node.copy(origin = origin)
        is AstVariableDeclaration -> node.copy(
            type = captureOriginType(node.type, origin),
            initializer = node.initializer?.let { captureOriginExpression(it, origin) },
            origin = origin
        )
        is AstInnerFunction -> node.copy(
            function = captureOriginFunction(node.function, origin),
            origin = origin
        )
        is AstIntegerLiteral -> node.copy(origin = origin)
        is AstBooleanLiteral -> node.copy(origin = origin)
        is AstFloatLiteral -> node.copy(origin = origin)
        is AstStringLiteral -> node.copy(origin = origin)
        is AstStringTemplate -> node.copy(
            parts = node.parts.map { part ->
                when (part) {
                    is AstStringTextPart -> part
                    is AstStringExpressionPart -> part.copy(expression = captureOriginExpression(part.expression, origin))
                }
            },
            origin = origin
        )
        is AstCharacterLiteral -> node.copy(origin = origin)
        is AstIdentifier -> node.copy(origin = origin)
        is AstUnary -> node.copy(operand = captureOriginExpression(node.operand, origin), origin = origin)
        is AstBinary -> node.copy(
            left = captureOriginExpression(node.left, origin),
            right = captureOriginExpression(node.right, origin),
            origin = origin
        )
        is AstConditional -> node.copy(
            condition = captureOriginExpression(node.condition, origin),
            thenBranch = captureOriginExpression(node.thenBranch, origin),
            elseBranch = captureOriginExpression(node.elseBranch, origin),
            origin = origin
        )
        is AstUpdate -> node.copy(operand = captureOriginExpression(node.operand, origin), origin = origin)
        is AstSizeOf -> node.copy(
            operand = node.operand?.let { captureOriginExpression(it, origin) },
            targetType = node.targetType?.let { captureOriginType(it, origin) },
            origin = origin
        )
        is AstAbiQuery -> node.copy(
            operand = node.operand?.let { captureOriginExpression(it, origin) },
            targetType = node.targetType?.let { captureOriginType(it, origin) },
            origin = origin
        )
        is AstCast -> node.copy(
            target = captureOriginType(node.target, origin),
            operand = captureOriginExpression(node.operand, origin),
            origin = origin
        )
        is AstCall -> node.copy(
            callee = captureOriginExpression(node.callee, origin),
            arguments = node.arguments.map { captureOriginExpression(it, origin) },
            origin = origin
        )
        is AstMemberAccess -> node.copy(receiver = captureOriginExpression(node.receiver, origin), origin = origin)
        is AstIndexAccess -> node.copy(
            receiver = captureOriginExpression(node.receiver, origin),
            index = captureOriginExpression(node.index, origin),
            origin = origin
        )
        is AstParenthesized -> node.copy(expression = captureOriginExpression(node.expression, origin), origin = origin)
        is AstErrorExpression -> node.copy(origin = origin)
    }

    private fun captureOriginDeclaration(declaration: AstDeclaration, origin: Origin): AstDeclaration =
        captureOrigin(declaration, origin) as AstDeclaration

    private fun captureOriginField(field: AstField, origin: Origin): AstField =
        field.copy(type = captureOriginType(field.type, origin), origin = origin)

    private fun captureOriginFunction(function: AstFunction, origin: Origin): AstFunction = function.copy(
        returnType = captureOriginType(function.returnType, origin),
        parameters = function.parameters.map { parameter ->
            parameter.copy(type = captureOriginType(parameter.type, origin), origin = origin)
        },
        body = function.body?.let { captureOriginStatement(it, origin) },
        origin = origin
    )

    private fun captureOriginType(type: AstTypeRef, origin: Origin): AstTypeRef = type.copy(
        functionParameters = type.functionParameters?.map { parameter ->
            parameter.copy(type = captureOriginType(parameter.type, origin), origin = origin)
        },
        origin = origin
    )

    private fun captureOriginStatement(statement: AstStatement, origin: Origin): AstStatement =
        captureOrigin(statement, origin) as AstStatement

    private fun captureOriginExpression(expression: AstExpression, origin: Origin): AstExpression =
        captureOrigin(expression, origin) as AstExpression

    private fun parseSyntaxValueNode(
        kind: String,
        text: String,
        source: SourceFile,
        diagnostics: DiagnosticBag,
        astBuilder: AstBuilder
    ): AstNode? {
        val fragment = when (kind) {
            "member" -> "struct __cpx_member { $text };"
            else -> text
        }
        val fragmentSource = source.copy(
            id = SourceFileId(-source.id.value - 1),
            path = syntheticSibling(source.path, "cpx-$kind.cp"),
            text = fragment
        )
        val parser = Parser(lexer.lex(fragmentSource))
        return when (kind) {
            "expr" -> parser.parseExpressionFragment().let { parsed ->
                if (parsed.diagnostics.any { it.severity == DiagnosticSeverity.ERROR }) {
                    diagnostics.addAll(parsed.diagnostics)
                    null
                } else {
                    parsed.expression?.let(astBuilder::buildExpression)
                }
            }
            "stmt" -> parser.parseStatementFragment().let { parsed ->
                if (parsed.diagnostics.any { it.severity == DiagnosticSeverity.ERROR }) {
                    diagnostics.addAll(parsed.diagnostics)
                    null
                } else {
                    parsed.statement?.let(astBuilder::buildStatement)
                }
            }
            "decl", "unit", "cpx" -> parser.parse().let { parsed ->
                if (parsed.diagnostics.any { it.severity == DiagnosticSeverity.ERROR }) {
                    diagnostics.addAll(parsed.diagnostics)
                    null
                } else {
                    val ast = astBuilder.build(parsed.syntax)
                    when (kind) {
                        "unit" -> ast
                        "decl" -> ast.declarations.singleOrNull()
                        "cpx" -> ast.declarations.singleOrNull()
                        else -> null
                    }
                }
            }
            "member" -> parser.parse().let { parsed ->
                if (parsed.diagnostics.any { it.severity == DiagnosticSeverity.ERROR }) {
                    diagnostics.addAll(parsed.diagnostics)
                    null
                } else {
                    val structure = astBuilder.build(parsed.syntax).declarations.singleOrNull() as? AstStruct
                    structure?.fields?.singleOrNull() ?: structure?.methods?.singleOrNull()
                }
            }
            else -> null
        }
    }

    private fun isExpression(text: String, source: SourceFile): Boolean {
        val fragmentSource = source.copy(
            id = SourceFileId(-source.id.value - 1),
            path = syntheticSibling(source.path, "cpx-expression.cp"),
            text = text
        )
        val parsed = Parser(lexer.lex(fragmentSource)).parseExpressionFragment()
        return parsed.expression != null && parsed.diagnostics.none { it.severity == DiagnosticSeverity.ERROR }
    }

    private val identifierPattern = Regex("[A-Za-z_][A-Za-z0-9_]*")
    private val primitiveTypeNames = CPrimitiveTypes.specifierKeywords

    private fun syntheticSibling(source: Path, name: String): Path =
        source.resolveSibling(name.replace(Regex("[^A-Za-z0-9_.-]"), "_"))

    private fun registerStructuralDeclarations(
        universe: ComptimeTypeUniverse,
        declarations: Iterable<SyntaxDeclaration>
    ) {
        declarations.mapNotNull(::structuralTypeDescriptor).forEach(universe::register)
    }

    private fun structuralTypeDescriptor(declaration: SyntaxDeclaration): StructuralTypeDescriptor? = when (declaration) {
        is SyntaxAlias -> StructuralTypeDescriptor(
            declaration.name,
            "alias",
            aliasTarget = StructuralTypeReference(
                declaration.target.name,
                declaration.target.pointerDepth,
                declaration.target.declarationKind
            )
        )
        is SyntaxEnum -> StructuralTypeDescriptor(
            declaration.name,
            "enum",
            layout = StructuralLayout("enum", declaration.values.map { it.name }, isSized = true),
            enumValues = declaration.values.map { it.name }
        )
        is SyntaxUnion -> StructuralTypeDescriptor(
            declaration.name,
            "union",
            declaration.fields.map(::structuralFieldDescriptor),
            layout = StructuralLayout("union", declaration.fields.map { it.name }, isSized = true)
        )
        is SyntaxStruct -> StructuralTypeDescriptor(
            declaration.name,
            "struct",
            declaration.fields.map(::structuralFieldDescriptor),
            declaration.methods.map(::structuralMethodDescriptor),
            StructuralLayout("struct", declaration.fields.map { it.name }, isSized = true)
        )
        else -> null
    }

    private fun structuralFieldDescriptor(field: SyntaxField): StructuralFieldDescriptor = StructuralFieldDescriptor(
        field.name,
        field.type.name,
        field.type.pointerDepth,
        field.arrayDimensions,
        StructuralTypeReference(field.type.name, field.type.pointerDepth, field.type.declarationKind)
    )

    private fun structuralMethodDescriptor(function: SyntaxFunction): StructuralMethodDescriptor = StructuralMethodDescriptor(
        function.name,
        function.returnType.name,
        function.parameters.map { it.type.name },
        function.isMethod && function.parameters.none { it.isReceiver },
        StructuralTypeReference(
            function.returnType.name,
            function.returnType.pointerDepth,
            function.returnType.declarationKind
        ),
        function.parameters.map {
            StructuralTypeReference(it.type.name, it.type.pointerDepth, it.type.declarationKind)
        }
    )

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
        is SyntaxTrait -> declaration.copy(
            targetOrigin = origin,
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

    private fun hygienize(declaration: SyntaxDeclaration, key: ExpansionKey): SyntaxDeclaration = when (declaration) {
        is SyntaxFunction -> {
            val localNames = declaration.body?.let(::localNames).orEmpty()
            val renames = localNames.associateWith { "${it}__cpx_${hygieneSuffix(key)}" }
            declaration.copy(
                parameters = declaration.parameters.map { parameter ->
                    parameter.copy(name = renames[parameter.name] ?: parameter.name)
                },
                body = declaration.body?.let { hygienize(it, renames, key) }
            )
        }
        is SyntaxStruct -> declaration.copy(
            methods = declaration.methods.map { method -> hygienize(method, key) as SyntaxFunction }
        )
        is SyntaxTrait -> declaration.copy(
            methods = declaration.methods.map { method -> hygienize(method, key) as SyntaxFunction }
        )
        else -> declaration
    }

    private fun localNames(statement: SyntaxStatement): Set<String> = buildSet {
        fun visit(current: SyntaxStatement) {
            when (current) {
                is SyntaxBlock -> current.statements.forEach(::visit)
                is SyntaxVariableDeclaration -> add(current.name)
                is SyntaxInnerFunction -> {
                    add(current.name)
                    current.parameters.forEach { add(it.name) }
                    visit(current.body)
                }
                is SyntaxIf -> {
                    visit(current.thenBranch)
                    current.elseBranch?.let(::visit)
                }
                is SyntaxWhile -> visit(current.body)
                is SyntaxFor -> {
                    current.initializer?.let(::visit)
                    visit(current.body)
                }
                is SyntaxReturn,
                is SyntaxExpressionStatement,
                is SyntaxDefer,
                is SyntaxBreak,
                is SyntaxContinue -> Unit
            }
        }
        visit(statement)
    }

    private fun hygienize(
        statement: SyntaxStatement,
        renames: Map<String, String>,
        key: ExpansionKey
    ): SyntaxStatement = when (statement) {
        is SyntaxBlock -> statement.copy(statements = statement.statements.map { hygienize(it, renames, key) })
        is SyntaxReturn -> statement.copy(expression = statement.expression?.let { hygienize(it, renames) })
        is SyntaxExpressionStatement -> statement.copy(expression = hygienize(statement.expression, renames))
        is SyntaxDefer -> statement.copy(expression = hygienize(statement.expression, renames))
        is SyntaxIf -> statement.copy(
            condition = hygienize(statement.condition, renames),
            thenBranch = hygienize(statement.thenBranch, renames, key),
            elseBranch = statement.elseBranch?.let { hygienize(it, renames, key) }
        )
        is SyntaxWhile -> statement.copy(
            condition = hygienize(statement.condition, renames),
            body = hygienize(statement.body, renames, key)
        )
        is SyntaxFor -> statement.copy(
            initializer = statement.initializer?.let { hygienize(it, renames, key) },
            condition = statement.condition?.let { hygienize(it, renames) },
            increment = statement.increment?.let { hygienize(it, renames) },
            body = hygienize(statement.body, renames, key)
        )
        is SyntaxVariableDeclaration -> statement.copy(
            name = renames[statement.name] ?: statement.name,
            initializer = statement.initializer?.let { hygienize(it, renames) }
        )
        is SyntaxInnerFunction -> {
            val nestedNames = localNames(statement.body) + statement.parameters.map { it.name } + statement.name
            val nestedRenames = nestedNames.associateWith { "${it}__cpx_${hygieneSuffix(key)}" } + renames
            statement.copy(
                name = nestedRenames[statement.name] ?: statement.name,
                parameters = statement.parameters.map { parameter ->
                    parameter.copy(name = nestedRenames[parameter.name] ?: parameter.name)
                },
                body = hygienize(statement.body, nestedRenames, key)
            )
        }
        is SyntaxBreak,
        is SyntaxContinue -> statement
    }

    private fun hygienize(expression: SyntaxExpression, renames: Map<String, String>): SyntaxExpression = when (expression) {
        is SyntaxIntegerLiteral,
        is SyntaxBooleanLiteral,
        is SyntaxFloatLiteral,
        is SyntaxStringLiteral,
        is SyntaxCharacterLiteral,
        is SyntaxErrorExpression -> expression
        is SyntaxStringTemplate -> expression.copy(
            parts = expression.parts.map { part ->
                when (part) {
                    is SyntaxStringTextPart -> part
                    is SyntaxStringExpressionPart -> part.copy(expression = hygienize(part.expression, renames))
                }
            }
        )
        is SyntaxIdentifier -> expression.copy(name = renames[expression.name] ?: expression.name)
        is SyntaxUnary -> expression.copy(operand = hygienize(expression.operand, renames))
        is SyntaxBinary -> expression.copy(
            left = hygienize(expression.left, renames),
            right = hygienize(expression.right, renames)
        )
        is SyntaxConditional -> expression.copy(
            condition = hygienize(expression.condition, renames),
            thenBranch = hygienize(expression.thenBranch, renames),
            elseBranch = hygienize(expression.elseBranch, renames)
        )
        is SyntaxUpdate -> expression.copy(operand = hygienize(expression.operand, renames))
        is SyntaxSizeOf -> expression.copy(
            operand = expression.operand?.let { hygienize(it, renames) }
        )
        is SyntaxAbiQuery -> expression.copy(
            operand = expression.operand?.let { hygienize(it, renames) }
        )
        is SyntaxCast -> expression.copy(operand = hygienize(expression.operand, renames))
        is SyntaxCall -> expression.copy(
            callee = hygienize(expression.callee, renames),
            arguments = expression.arguments.map { hygienize(it, renames) }
        )
        is SyntaxMemberAccess -> expression.copy(receiver = hygienize(expression.receiver, renames))
        is SyntaxIndexAccess -> expression.copy(
            receiver = hygienize(expression.receiver, renames),
            index = hygienize(expression.index, renames)
        )
        is SyntaxParenthesized -> expression.copy(expression = hygienize(expression.expression, renames))
    }

    private fun hygieneSuffix(key: ExpansionKey): String = MessageDigest.getInstance("SHA-256")
        .digest(key.canonical.toByteArray(Charsets.UTF_8))
        .take(6)
        .joinToString("") { byte -> "%02x".format(byte) }

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
        is SyntaxInnerFunction -> statement.copy(
            returnType = reorigin(statement.returnType, origin),
            parameters = statement.parameters.map { it.copy(type = reorigin(it.type, origin), origin = origin) },
            body = reorigin(statement.body, origin),
            origin = origin
        )
    }

    private fun reorigin(expression: SyntaxExpression, origin: Origin): SyntaxExpression = when (expression) {
        is SyntaxIntegerLiteral -> expression.copy(origin = origin)
        is SyntaxBooleanLiteral -> expression.copy(origin = origin)
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
        is SyntaxSizeOf -> expression.copy(
            operand = expression.operand?.let { reorigin(it, origin) },
            targetType = expression.targetType?.let { reorigin(it, origin) },
            origin = origin
        )
        is SyntaxAbiQuery -> expression.copy(
            operand = expression.operand?.let { reorigin(it, origin) },
            targetType = expression.targetType?.let { reorigin(it, origin) },
            origin = origin
        )
        is SyntaxCast -> expression.copy(
            target = reorigin(expression.target, origin),
            operand = reorigin(expression.operand, origin),
            origin = origin
        )
        is SyntaxCall -> expression.copy(callee = reorigin(expression.callee, origin), arguments = expression.arguments.map { reorigin(it, origin) }, origin = origin)
        is SyntaxMemberAccess -> expression.copy(receiver = reorigin(expression.receiver, origin), origin = origin)
        is SyntaxIndexAccess -> expression.copy(receiver = reorigin(expression.receiver, origin), index = reorigin(expression.index, origin), origin = origin)
        is SyntaxParenthesized -> expression.copy(expression = reorigin(expression.expression, origin), origin = origin)
        is SyntaxErrorExpression -> expression.copy(origin = origin)
    }
}
