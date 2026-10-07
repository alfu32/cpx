package cplus.comptime

import cplus.core.*
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.file.Path
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

sealed interface ComptimeValue {
    val sourceText: String
    val canonicalKind: String
    val canonicalText: String

    data class CtType(
        override val sourceText: String,
        val identifierText: String = sourceText.removePrefix("struct ").trim()
    ) : ComptimeValue {
        override val canonicalKind: String = "type"
        override val canonicalText: String = identifierText
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

    data class CtExpression(val text: String) : ComptimeValue {
        override val sourceText: String = text
        override val canonicalKind: String = "expr"
        override val canonicalText: String = canonicalSyntax(text)
    }

    data class CtStatement(val text: String) : ComptimeValue {
        override val sourceText: String = text
        override val canonicalKind: String = "stmt"
        override val canonicalText: String = canonicalSyntax(text)
    }

    data class CtDeclaration(val text: String) : ComptimeValue {
        override val sourceText: String = text
        override val canonicalKind: String = "decl"
        override val canonicalText: String = canonicalSyntax(text)
    }

    data class CtMember(val text: String) : ComptimeValue {
        override val sourceText: String = text
        override val canonicalKind: String = "member"
        override val canonicalText: String = canonicalSyntax(text)
    }

    data class CtUnit(val text: String) : ComptimeValue {
        override val sourceText: String = text
        override val canonicalKind: String = "unit"
        override val canonicalText: String = canonicalSyntax(text)
    }

    data class CtCpx(val text: String) : ComptimeValue {
        override val sourceText: String = text
        override val canonicalKind: String = "cpx"
        override val canonicalText: String = canonicalSyntax(text)
    }
}

private fun canonicalSyntax(text: String): String = text.trim().replace(Regex("\\s+"), " ")

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
                        is ComptimeValue.CtType -> append(if (node.explicit) value.identifierText else value.sourceText)
                        else -> append(value?.sourceText ?: node.name)
                    }
                }
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
        val nodes = mutableListOf<TemplateNode>()
        if (bindingNames.isEmpty()) return CpxTemplate(category, listOf(TemplateNode.Literal(text)), origin)
        val explicitPattern = Regex("\\{\\s*(${bindingNames.joinToString("|") { Regex.escape(it) }})\\s*}")
        var cursor = 0
        explicitPattern.findAll(text).forEach { match ->
            if (match.range.first > cursor) nodes += directBindings(text.substring(cursor, match.range.first), bindingNames)
            nodes += TemplateNode.Binding(match.groupValues[1], explicit = true)
            cursor = match.range.last + 1
        }
        if (cursor < text.length) nodes += directBindings(text.substring(cursor), bindingNames)
        return CpxTemplate(category, nodes, origin)
    }

    private fun directBindings(text: String, bindingNames: Set<String>): List<TemplateNode> {
        if (text.isEmpty() || bindingNames.isEmpty()) return if (text.isEmpty()) emptyList() else listOf(TemplateNode.Literal(text))
        val pattern = Regex("(?<![A-Za-z0-9_])(${bindingNames.joinToString("|") { Regex.escape(it) }})(?![A-Za-z0-9_])")
        val result = mutableListOf<TemplateNode>()
        var cursor = 0
        pattern.findAll(text).forEach { match ->
            if (match.range.first > cursor) result += TemplateNode.Literal(text.substring(cursor, match.range.first))
            result += TemplateNode.Binding(match.value, explicit = false)
            cursor = match.range.last + 1
        }
        if (cursor < text.length) result += TemplateNode.Literal(text.substring(cursor))
        return result
    }
}

data class ExpansionKey(
    val functionName: String,
    val arguments: List<String>,
    val argumentKinds: List<String> = emptyList()
) {
    val canonical: String
        get() = "$functionName(${arguments.joinToString(",")})"

    val specializationKey: SpecializationKey
        get() = SpecializationKey(
            functionName,
            arguments.mapIndexed { index, argument ->
                CanonicalComptimeValue(argumentKinds.getOrNull(index) ?: "type", argument)
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
    val instantiatedText: String
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
        val entry = entries[key]
        if (entry == null || entry.definitionFingerprint != definitionFingerprint) {
            missCount++
            if (entry != null) entries.remove(key)
            return null
        }
        hitCount++
        return entry.instantiatedText
    }

    @Synchronized
    fun put(key: SpecializationKey, definitionFingerprint: String, instantiatedText: String) {
        entries[key] = SpecializationCacheEntry(definitionFingerprint, instantiatedText)
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
    val isSized: Boolean
)

data class StructuralTypeDescriptor(
    val name: String,
    val kind: String,
    val fields: List<StructuralFieldDescriptor> = emptyList(),
    val methods: List<StructuralMethodDescriptor> = emptyList(),
    val layout: StructuralLayout? = null
)

data class TypeUniverseSnapshot(
    val names: Set<String>,
    val access: TypeUniverseAccess,
    val descriptors: Map<String, StructuralTypeDescriptor> = emptyMap()
) {
    fun typeNamed(name: String): StructuralTypeDescriptor? = descriptors[name]
}

/** Mutable only during structural expansion and immutable after freeze. */
class ComptimeTypeUniverse {
    private val descriptors = linkedMapOf<String, StructuralTypeDescriptor>()
    private var frozen = false

    fun register(name: String): Boolean = register(StructuralTypeDescriptor(name, "unknown"))

    fun register(descriptor: StructuralTypeDescriptor): Boolean {
        if (frozen) return false
        if (descriptor.name in descriptors) return false
        descriptors[descriptor.name] = descriptor
        return true
    }

    fun registerAll(names: Iterable<String>) {
        names.forEach(::register)
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
            if (access == TypeUniverseAccess.FULL) descriptors.toMap() else emptyMap()
        )
    }
}

data class ExpansionTask(
    val invocation: SyntaxCpxInvocation,
    val definition: SyntaxComptimeFunction,
    val key: ExpansionKey,
    val ancestors: List<ExpansionKey> = emptyList(),
    val phase: CpxPhase = CpxPhase.STRUCTURAL,
    val dependencies: Set<ComptimeDependency> = emptySet()
)

private data class DeferredCpxInvocation(
    val invocation: SyntaxCpxInvocation,
    val ancestors: List<ExpansionKey>
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
    val structuralFingerprint: String = structuralFingerprint(program)
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
        function.parameters.joinToString(",") { "${it.name}:${structuralType(it.type)}:${it.isReceiver}" }

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
    private val limits: CpxExpansionLimits = CpxExpansionLimits()
) {
    fun invalidateSpecializations(keys: Set<SpecializationKey>) {
        specializationCache.invalidate(keys)
    }

    fun expand(source: SourceFile, program: SyntaxProgram): CpxExpansionResult {
        val diagnostics = DiagnosticBag()
        val definitions = linkedMapOf<String, SyntaxComptimeFunction>()
        program.declarations
            .filterIsInstance<SyntaxComptimeFunction>()
            .forEach { definitions[it.name] = it }
        val scheduler = ComptimeScheduler()
        val deferredInvocations = mutableListOf<DeferredCpxInvocation>()

        fun queueInvocation(invocation: SyntaxCpxInvocation, ancestors: List<ExpansionKey> = emptyList()) {
            val definition = definitions[invocation.name]
            if (definition == null) {
                deferredInvocations += DeferredCpxInvocation(invocation, ancestors)
            } else {
                scheduler.enqueue(taskFor(invocation, definition, ancestors))
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
                    scheduler.enqueue(taskFor(deferred.invocation, definition, deferred.ancestors))
                }
                iterator.remove()
            }
        }

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
                parseComptimeValue(parameter.kind, argument, task.invocation, source, diagnostics)
            }
            if (values.size != task.definition.parameters.size) {
                continue
            }

            val category = parseCategory(task.definition.category)
            val definitionFingerprint = buildString {
                append(source.path.toAbsolutePath().normalize())
                append('|')
                append(task.definition.name)
                append('|')
                append(task.definition.category)
                append('|')
                append(task.definition.parameters.joinToString(",") { "${it.kind}:${it.name}" })
                append('|')
                append(task.definition.template)
            }
            val specializationKey = task.key.specializationKey
            val instantiated = specializationCache.get(specializationKey, definitionFingerprint)
                ?: run {
                    val template = templateParser.parse(
                        task.definition.template,
                        category,
                        task.definition.origin,
                        task.definition.parameters.map { it.name }.toSet()
                    )
                    val bindings = task.definition.parameters.zip(values).associate { (parameter, value) ->
                        parameter.name to value
                    }
                    template.render(bindings).also {
                        specializationCache.put(specializationKey, definitionFingerprint, it)
                    }
                }
            val generatedFile = SourceFile(
                SourceFileId(-(++generatedFileIndex)),
                source.path.resolveSibling("<${task.key.canonical}>"),
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
            val declarations = parsed.syntax.declarations.map { reorigin(it, expansionOrigin) }
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
                queueInvocation(invocation, task.ancestors + task.key)
            }
        }

        resolveDeferredInvocations()
        deferredInvocations.forEach { deferred ->
            diagnostics.error("unknown compile-time function '${deferred.invocation.name}'", deferred.invocation.origin.primaryRange, "CPX001")
        }
        if (scheduler.pendingKeys().isEmpty()) scheduler.closeStructuralPhase()
        val retained = program.declarations.filterNot { it is SyntaxComptimeFunction || it is SyntaxCpxInvocation }
        val range = program.range
        return CpxExpansionResult(
            SyntaxProgram(retained + generated, range, program.origin),
            diagnostics.diagnostics,
            scheduler.expandedKeys,
            scheduler.specializationKeys
        )
    }

    private fun taskFor(
        invocation: SyntaxCpxInvocation,
        definition: SyntaxComptimeFunction,
        ancestors: List<ExpansionKey> = emptyList()
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
                .orEmpty()
        ),
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
        CpxPhase.STRUCTURAL -> task.definition.category.lowercase() in setOf("decl", "unit")
        CpxPhase.REFLECTIVE -> task.definition.category.lowercase() in setOf("stmt", "statement", "expr", "expression")
    }

    private fun isStructuralDeclaration(declaration: SyntaxDeclaration): Boolean = when (declaration) {
        is SyntaxAlias,
        is SyntaxEnum,
        is SyntaxUnion,
        is SyntaxStruct -> true
        else -> false
    }

    private fun declarationName(declaration: SyntaxDeclaration): String = when (declaration) {
        is SyntaxAlias -> "alias ${declaration.name}"
        is SyntaxEnum -> "enum ${declaration.name}"
        is SyntaxUnion -> "union ${declaration.name}"
        is SyntaxStruct -> "struct ${declaration.name}"
        else -> declaration::class.simpleName ?: "declaration"
    }

    private fun normalizeParameterKind(kind: String): String = when (kind.trim().lowercase()) {
        "integer" -> "int"
        "boolean" -> "bool"
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
        diagnostics: DiagnosticBag
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
            "type" -> ComptimeValue.CtType(
                text.replace(Regex("\\s+"), " "),
                text.replace(Regex("\\s+"), " ").removePrefix("struct ").trim()
            )
            "identifier" -> if (identifierPattern.matches(text)) ComptimeValue.CtIdentifier(text) else invalid("identifier")
            "int" -> text.toBigIntegerOrNull()?.let { ComptimeValue.CtInteger(text, it) } ?: invalid("integer")
            "float" -> text.toBigDecimalOrNull()?.let { ComptimeValue.CtFloat(text, it) } ?: invalid("floating-point value")
            "bool" -> when (text.lowercase()) {
                "true" -> ComptimeValue.CtBoolean(true, text)
                "false" -> ComptimeValue.CtBoolean(false, text)
                else -> invalid("boolean")
            }
            "string" -> if (text.length >= 2 && text.first() == '"' && text.last() == '"') {
                ComptimeValue.CtString(text)
            } else invalid("string")
            "expr" -> if (isExpression(text, source)) ComptimeValue.CtExpression(text) else invalid("expression")
            "stmt" -> ComptimeValue.CtStatement(text)
            "decl" -> ComptimeValue.CtDeclaration(text)
            "member" -> ComptimeValue.CtMember(text)
            "unit" -> ComptimeValue.CtUnit(text)
            "cpx" -> ComptimeValue.CtCpx(text)
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

    private fun isExpression(text: String, source: SourceFile): Boolean {
        val fragmentSource = source.copy(
            id = SourceFileId(-source.id.value - 1),
            path = source.path.resolveSibling("<cpx-expression>"),
            text = text
        )
        val parsed = Parser(lexer.lex(fragmentSource)).parseExpressionFragment()
        return parsed.expression != null && parsed.diagnostics.none { it.severity == DiagnosticSeverity.ERROR }
    }

    private val identifierPattern = Regex("[A-Za-z_][A-Za-z0-9_]*")

    private fun registerStructuralDeclarations(
        universe: ComptimeTypeUniverse,
        declarations: Iterable<SyntaxDeclaration>
    ) {
        declarations.mapNotNull(::structuralTypeDescriptor).forEach(universe::register)
    }

    private fun structuralTypeDescriptor(declaration: SyntaxDeclaration): StructuralTypeDescriptor? = when (declaration) {
        is SyntaxAlias -> StructuralTypeDescriptor(declaration.name, "alias")
        is SyntaxEnum -> StructuralTypeDescriptor(
            declaration.name,
            "enum",
            layout = StructuralLayout("enum", declaration.values.map { it.name }, isSized = true)
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
