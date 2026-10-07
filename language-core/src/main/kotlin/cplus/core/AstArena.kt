package cplus.core

@JvmInline
value class NodeId(val value: Int)

/** Stable storage boundary for AST nodes used by later rewrite passes. */
class AstArena {
    private val nodes = mutableListOf<AstNode>()

    fun add(node: AstNode): NodeId {
        nodes += node
        return NodeId(nodes.lastIndex)
    }

    operator fun get(id: NodeId): AstNode = nodes.getOrNull(id.value)
        ?: error("Unknown AST node id: ${id.value}")

    fun replace(id: NodeId, node: AstNode): AstNode {
        val previous = this[id]
        nodes[id.value] = node
        return previous
    }

    val size: Int
        get() = nodes.size
}
