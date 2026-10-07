package cplus.core

import java.util.IdentityHashMap

@JvmInline
value class NodeId(val value: Int)

/** Stable storage boundary for AST nodes used by later rewrite passes. */
class AstArena {
    private val nodes = mutableListOf<AstNode>()
    private val ids = IdentityHashMap<AstNode, NodeId>()

    fun add(node: AstNode): NodeId {
        ids[node]?.let { return it }
        nodes += node
        return NodeId(nodes.lastIndex).also { ids[node] = it }
    }

    operator fun get(id: NodeId): AstNode = nodes.getOrNull(id.value)
        ?: error("Unknown AST node id: ${id.value}")

    fun replace(id: NodeId, node: AstNode): AstNode {
        val previous = this[id]
        nodes[id.value] = node
        ids.remove(previous)
        ids[node] = id
        return previous
    }

    val size: Int
        get() = nodes.size
}
