package cplus.compiler

import cplus.core.SourceFile
import cplus.core.SyntaxImport
import cplus.core.SyntaxProgram
import java.nio.file.Path
import kotlin.io.path.nameWithoutExtension

@JvmInline
value class ModuleId(val value: String)

data class ModuleSource(
    val source: SourceFile,
    val program: SyntaxProgram
)

data class ModuleNode(
    val id: ModuleId,
    val path: Path,
    val imports: Set<ModuleId>
)

data class ModuleComponent(
    val modules: List<ModuleId>,
    val hasSelfLoop: Boolean = false
) {
    val isCyclic: Boolean
        get() = modules.size > 1 || hasSelfLoop
}

data class ModuleGraph(
    val nodes: Map<ModuleId, ModuleNode>,
    val components: List<ModuleComponent>
) {
    val moduleNames: Set<String>
        get() = nodes.keys.map(ModuleId::value).toSet()

    val cyclicComponents: List<ModuleComponent>
        get() = components.filter(ModuleComponent::isCyclic)
}

/**
 * Builds a module dependency graph before semantic resolution.
 *
 * Modules are catalogued as a complete set before any edge is traversed, so
 * an import cycle does not become a recursive compiler call. The semantic
 * phase receives the merged declaration catalogue and can resolve legal
 * declaration-only cycles to a fixed point.
 */
class ModuleGraphBuilder {
    fun build(sources: List<ModuleSource>): ModuleGraph {
        val idsByName = sources.associate { moduleName(it.source.path) to ModuleId(moduleName(it.source.path)) }
        val nodes = sources.associate { unit ->
            val id = idsByName.getValue(moduleName(unit.source.path))
            val imports = unit.program.declarations
                .filterIsInstance<SyntaxImport>()
                .mapNotNull { idsByName[normalizeImport(it.module)] }
                .toSet()
            id to ModuleNode(id, unit.source.path, imports)
        }
        return ModuleGraph(nodes, stronglyConnectedComponents(nodes))
    }

    private fun stronglyConnectedComponents(nodes: Map<ModuleId, ModuleNode>): List<ModuleComponent> {
        var index = 0
        val indexes = mutableMapOf<ModuleId, Int>()
        val lowLinks = mutableMapOf<ModuleId, Int>()
        val stack = ArrayDeque<ModuleId>()
        val onStack = mutableSetOf<ModuleId>()
        val components = mutableListOf<ModuleComponent>()

        fun visit(node: ModuleId) {
            indexes[node] = index
            lowLinks[node] = index
            index++
            stack.addLast(node)
            onStack += node

            nodes.getValue(node).imports.sortedBy(ModuleId::value).forEach { dependency ->
                when {
                    dependency !in indexes -> {
                        visit(dependency)
                        lowLinks[node] = minOf(lowLinks.getValue(node), lowLinks.getValue(dependency))
                    }
                    dependency in onStack -> {
                        lowLinks[node] = minOf(lowLinks.getValue(node), indexes.getValue(dependency))
                    }
                }
            }

            if (lowLinks.getValue(node) == indexes.getValue(node)) {
                val members = buildList {
                    do {
                        val member = stack.removeLast()
                        onStack -= member
                        add(member)
                    } while (member != node)
                }.sortedBy(ModuleId::value)
                components += ModuleComponent(
                    members,
                    members.size == 1 && nodes.getValue(members.single()).imports.contains(members.single())
                )
            }
        }

        nodes.keys.sortedBy(ModuleId::value).forEach { if (it !in indexes) visit(it) }
        return components
    }

    private fun moduleName(path: Path): String = path.nameWithoutExtension

    private fun normalizeImport(module: String): String = module
        .trim()
        .removeSurrounding("\"")
        .substringAfterLast('/')
        .removeSuffix(".cp")
        .substringAfterLast('.')
}
