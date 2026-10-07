package cplus.compiler

import cplus.core.AstArena
import cplus.core.AstFunction
import cplus.core.AstGlobalVariable
import cplus.core.AstParameter
import cplus.core.AstTypeRef
import cplus.core.NodeId
import cplus.core.Origin
import cplus.core.SourceFileId
import cplus.core.SourceRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AstRewriteTest {
    @Test
    fun rewriteOperationsPreserveStableIdsAndInvalidateIndexes() {
        val range = SourceRange(SourceFileId(31), 0, 1)
        val origin = Origin.Direct(range)
        val generatedOrigin = Origin.Generated(origin)
        val arena = AstArena()
        val first = arena.add(function("first", origin))
        val second = arena.add(function("second", origin))
        val replacement = arena.add(function("replacement", generatedOrigin))
        val hoisted = arena.add(global("hoisted", generatedOrigin))
        val invalidations = mutableListOf<Set<NodeId>>()
        val rewriter = ArenaAstRewriter(
            arena,
            listOf(first, second),
            SemanticIndexInvalidator { invalidations += it }
        )
        rewriter.registerScope(first)

        rewriter.replace(first, replacement)
        rewriter.insertBefore(second, replacement)
        rewriter.insertAfter(second, hoisted)
        rewriter.hoist(first, hoisted)

        assertEquals(listOf(first, replacement, second, hoisted), rewriter.rootNodes())
        assertEquals(listOf(hoisted), rewriter.childNodes(first))
        assertEquals("replacement", (arena[first] as AstFunction).name)
        assertTrue(invalidations.any { first in it && replacement in it })
        assertTrue(invalidations.any { second in it && hoisted in it })
    }

    @Test
    fun generatedNodesRequireMatchingExplicitOrigin() {
        val range = SourceRange(SourceFileId(32), 0, 1)
        val direct = Origin.Direct(range)
        val generated = Origin.Generated(direct)
        val arena = AstArena()
        val rewriter = ArenaAstRewriter(arena, emptyList())
        val node = global("value", generated)

        val id = rewriter.addGenerated(node, generated)

        assertEquals(node, arena[id])
        assertFailsWith<IllegalArgumentException> {
            rewriter.addGenerated(node, direct)
        }
    }

    private fun function(name: String, origin: Origin) = AstFunction(
        AstTypeRef("int", false, 0, origin),
        name,
        emptyList<AstParameter>(),
        null,
        origin = origin
    )

    private fun global(name: String, origin: Origin) = AstGlobalVariable(
        AstTypeRef("int", false, 0, origin),
        name,
        null,
        origin = origin
    )
}
