package cplus.compiler

import cplus.core.AstArena
import cplus.core.AstNode
import cplus.core.NodeId
import cplus.core.Origin

/** Controlled mutation operations exposed to compiler transformation passes. */
interface AstRewriter {
    fun replace(old: NodeId, new: NodeId)

    fun insertBefore(anchor: NodeId, node: NodeId)

    fun insertAfter(anchor: NodeId, node: NodeId)

    fun hoist(targetScope: NodeId, node: NodeId)
}

/** Semantic indexes subscribe to rewrite invalidations instead of observing arena mutation. */
fun interface SemanticIndexInvalidator {
    fun invalidate(changedNodes: Set<NodeId>)
}

/**
 * NodeId-backed rewrite session for declaration and scope roots.
 *
 * The arena owns node identity; this class owns the ordered root membership
 * affected by list rewrites. A pass must provide an [Origin] when adding a
 * generated node, and the node's embedded origin must match it exactly.
 */
class ArenaAstRewriter(
    private val arena: AstArena,
    rootNodes: List<NodeId>,
    private val invalidator: SemanticIndexInvalidator = SemanticIndexInvalidator { }
) : AstRewriter {
    private val roots = rootNodes.toMutableList()
    private val scopedNodes = linkedMapOf<NodeId, MutableList<NodeId>>()

    /** Register the ordered child roots owned by a scope before hoisting into it. */
    fun registerScope(scope: NodeId, childNodes: List<NodeId> = emptyList()) {
        requireNode(scope)
        scopedNodes[scope] = childNodes.toMutableList()
        invalidate((childNodes + scope).toSet())
    }

    /** Add a node produced by a transformation with an explicit provenance value. */
    fun addGenerated(node: AstNode, origin: Origin): NodeId {
        require(node.origin == origin) {
            "generated AST node origin must match the explicit rewrite origin"
        }
        val id = arena.add(node)
        invalidate(setOf(id))
        return id
    }

    override fun replace(old: NodeId, new: NodeId) {
        requireNode(old)
        requireNode(new)
        arena.replace(old, arena[new])
        invalidate(setOf(old, new))
    }

    override fun insertBefore(anchor: NodeId, node: NodeId) {
        requireNode(anchor)
        requireNode(node)
        val index = roots.indexOf(anchor)
        require(index >= 0) { "insert anchor is not a registered root: $anchor" }
        roots.add(index, node)
        invalidate(setOf(anchor, node))
    }

    override fun insertAfter(anchor: NodeId, node: NodeId) {
        requireNode(anchor)
        requireNode(node)
        val index = roots.indexOf(anchor)
        require(index >= 0) { "insert anchor is not a registered root: $anchor" }
        roots.add(index + 1, node)
        invalidate(setOf(anchor, node))
    }

    override fun hoist(targetScope: NodeId, node: NodeId) {
        requireNode(targetScope)
        requireNode(node)
        val children = scopedNodes[targetScope]
            ?: error("target scope is not registered for hoisting: $targetScope")
        children += node
        invalidate(setOf(targetScope, node))
    }

    fun rootNodes(): List<NodeId> = roots.toList()

    fun childNodes(scope: NodeId): List<NodeId> = scopedNodes[scope]?.toList().orEmpty()

    private fun requireNode(id: NodeId) {
        arena[id]
    }

    private fun invalidate(ids: Set<NodeId>) {
        invalidator.invalidate(ids)
    }
}
