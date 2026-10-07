package cplus.semantic

@JvmInline
value class ScopeId(val value: Int)

enum class ScopeKind {
    PACKAGE,
    MODULE,
    TYPE,
    FUNCTION,
    BLOCK,
    COMPTIME,
    CPX_TEMPLATE
}

data class Scope(
    val id: ScopeId,
    val parent: ScopeId?,
    val owner: SymbolId?,
    val kind: ScopeKind,
    internal val bindings: MutableMap<String, MutableList<SymbolId>> = linkedMapOf()
)

/** Explicit lexical scope storage shared by semantic and compile-time phases. */
class ScopeTable {
    private val scopes = linkedMapOf<ScopeId, Scope>()
    private var nextId = 1

    fun create(kind: ScopeKind, parent: ScopeId? = null, owner: SymbolId? = null): ScopeId {
        val id = ScopeId(nextId++)
        scopes[id] = Scope(id, parent, owner, kind)
        return id
    }

    fun define(scopeId: ScopeId, name: String, symbol: SymbolId) {
        val scope = scope(scopeId)
        scope.bindings.getOrPut(name) { mutableListOf() } += symbol
    }

    fun lookup(scopeId: ScopeId, name: String): List<SymbolId> {
        var current: ScopeId? = scopeId
        while (current != null) {
            val matches = scope(current).bindings[name]
            if (!matches.isNullOrEmpty()) return matches.toList()
            current = scope(current).parent
        }
        return emptyList()
    }

    fun get(scopeId: ScopeId): Scope = scope(scopeId).copy(
        bindings = scope(scopeId).bindings.mapValues { (_, values) -> values.toMutableList() }.toMutableMap()
    )

    val all: List<Scope>
        get() = scopes.values.map { get(it.id) }

    private fun scope(scopeId: ScopeId): Scope = scopes[scopeId]
        ?: error("Unknown scope id: ${scopeId.value}")
}
