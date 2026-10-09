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

    fun moduleIdForPath(path: Path): ModuleId? {
        val normalized = path.toAbsolutePath().normalize()
        return nodes.values.firstOrNull { it.path.toAbsolutePath().normalize() == normalized }?.id
    }
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
        val idsByPath = moduleIdentities(sources)
        val nodes = sources.associate { unit ->
            val id = idsByPath.getValue(normalize(unit.source.path))
            val imports = unit.program.declarations
                .filterIsInstance<SyntaxImport>()
                .mapNotNull { import -> resolveImport(unit, import.module, sources, idsByPath) }
                .toSet()
            id to ModuleNode(id, unit.source.path, imports)
        }
        return ModuleGraph(nodes, stronglyConnectedComponents(nodes))
    }

    private fun moduleIdentities(sources: List<ModuleSource>): Map<Path, ModuleId> {
        val normalizedPaths = sources.associate { it to normalize(it.source.path) }
        val duplicateNames = sources.groupBy { moduleName(it.source.path) }
            .filterValues { it.size > 1 }
        val names = sources.associateWith { moduleName(it.source.path) }.toMutableMap()
        duplicateNames.values.forEach { duplicates ->
            var depth = 1
            while (true) {
                val candidates = duplicates.associateWith { unit -> moduleName(unit.source.path, depth) }
                if (candidates.values.toSet().size == duplicates.size) {
                    candidates.forEach { (unit, name) -> names[unit] = name }
                    break
                }
                depth++
                require(depth <= duplicates.maxOf { normalizedPaths.getValue(it).nameCount }) {
                    "distinct module files do not have unique source paths"
                }
            }
        }
        return sources.associate { unit -> normalizedPaths.getValue(unit) to ModuleId(names.getValue(unit)) }
    }

    private fun resolveImport(
        importer: ModuleSource,
        rawReference: String,
        sources: List<ModuleSource>,
        idsByPath: Map<Path, ModuleId>
    ): ModuleId? {
        val reference = rawReference.trim().removeSurrounding("\"", "\"")
        val isPath = reference.startsWith("./") || reference.startsWith("../") ||
            reference.startsWith("/") || reference.endsWith(".cp")
        if (isPath) {
            val target = runCatching { Path.of(reference) }.getOrNull() ?: return null
            val resolved = normalize(if (target.isAbsolute) target else importer.source.path.parent.resolve(target))
            return idsByPath[resolved]
        }

        val directNames = setOf(reference, reference.substringAfterLast('/'), reference.substringAfterLast('.'))
        val candidates = sources.filter { source ->
            val id = idsByPath.getValue(normalize(source.source.path)).value
            val packageName = source.program.declarations.filterIsInstance<cplus.core.SyntaxPackage>()
                .firstOrNull()?.name
            id in directNames || moduleName(source.source.path) in directNames ||
                packageName?.let { "$it.$id" in directNames || "$it.${moduleName(source.source.path)}" in directNames } == true
        }
        return candidates.singleOrNull()?.let { idsByPath.getValue(normalize(it.source.path)) }
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

    private fun moduleName(path: Path, parentDepth: Int = 0): String {
        val normalized = normalize(path)
        val parents = generateSequence(normalized.parent) { it.parent }
            .mapNotNull { it.fileName?.toString() }
            .toList()
        val prefix = parents.take(parentDepth).asReversed()
        return (prefix + normalized.nameWithoutExtension).joinToString(".")
    }

    private fun normalize(path: Path): Path = path.toAbsolutePath().normalize()
}
