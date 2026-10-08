package cplus.compiler

import cplus.core.Lexer
import cplus.core.Parser
import cplus.core.SourceFile
import cplus.core.SourceFileId
import cplus.core.SyntaxImport
import java.nio.file.Files
import java.nio.file.Path
import java.util.ArrayDeque

data class ResolvedModuleSource(
    val path: Path,
    val text: String,
    val imports: List<String>
)

data class ModuleSourceResolution(
    val modules: List<ResolvedModuleSource>,
    val unresolvedImports: List<Pair<Path, String>>
) {
    val paths: List<Path> get() = modules.map(ResolvedModuleSource::path)
}

/** Shared, bounded source-path and SDK-module resolver for CLI and language tooling. */
class ModuleSourceResolver(
    roots: List<Path>,
    sdkRoot: Path? = null,
    private val maximumCandidates: Int = DEFAULT_MAXIMUM_CANDIDATES
) {
    private val roots = roots.map(::normalize).distinct()
    private val sdkRoot = sdkRoot?.let(::normalize)

    init {
        require(maximumCandidates > 0)
    }

    fun resolveClosure(
        entries: List<Path>,
        overlays: Map<Path, String> = emptyMap()
    ): ModuleSourceResolution {
        val normalizedOverlays = overlays.mapKeys { normalize(it.key) }
        val fallbackCandidates by lazy(LazyThreadSafetyMode.NONE) { candidatePaths(normalizedOverlays.keys) }
        val contents = linkedMapOf<Path, String>()
        val importsByPath = linkedMapOf<Path, List<String>>()
        val unresolved = mutableListOf<Pair<Path, String>>()
        val visited = linkedSetOf<Path>()
        val pending = ArrayDeque<Path>()
        entries.map(::normalize).forEach(pending::addLast)

        while (pending.isNotEmpty()) {
            val path = pending.removeFirst()
            if (!visited.add(path)) continue
            val text = normalizedOverlays[path] ?: runCatching { Files.readString(path) }.getOrNull()
                ?: continue
            contents[path] = text
            val imports = imports(path, text)
            importsByPath[path] = imports
            imports.forEach { reference ->
                if (reference.startsWith("c.") || reference.startsWith("c/")) return@forEach
                val dependency = resolveImport(path, reference, normalizedOverlays.keys) { fallbackCandidates }
                if (dependency == null) unresolved += path to reference
                else if (dependency !in visited) pending.addLast(dependency)
            }
        }

        return ModuleSourceResolution(
            visited.mapNotNull { path -> contents[path]?.let { ResolvedModuleSource(path, it, importsByPath[path].orEmpty()) } },
            unresolved
        )
    }

    fun resolveImport(
        importer: Path,
        reference: String,
        overlayPaths: Set<Path> = emptySet()
    ): Path? = resolveImport(
        normalize(importer),
        reference,
        overlayPaths.map(::normalize).toSet(),
    ) { candidatePaths(overlayPaths.map(::normalize).toSet()) }

    /** Candidate enumeration is deliberately separate from entry-point closure resolution. */
    fun candidatePaths(overlays: Set<Path> = emptySet()): List<Path> {
        val result = linkedSetOf<Path>()
        overlays.map(::normalize).sortedBy(Path::toString).forEach(result::add)
        for (root in roots) {
            if (result.size >= maximumCandidates || !Files.isDirectory(root)) break
            runCatching {
                Files.walk(root).use { paths ->
                    paths.filter { path ->
                        val relative = root.relativize(path)
                        relative.none { it.toString() in EXCLUDED_DIRECTORIES } &&
                            Files.isRegularFile(path) && path.fileName.toString().endsWith(".cp")
                    }
                        .map(::normalize)
                        .sorted()
                        .limit((maximumCandidates - result.size).toLong())
                        .forEach(result::add)
                }
            }
        }
        return result.take(maximumCandidates)
    }

    private fun resolveImport(
        importer: Path,
        rawReference: String,
        overlayPaths: Set<Path>,
        candidates: () -> List<Path>
    ): Path? {
        val reference = rawReference.trim().removeSurrounding("\"", "\"")
        val isPathImport = reference.startsWith("./") || reference.startsWith("../") ||
            reference.startsWith("/") || reference.endsWith(".cp")
        if (isPathImport) {
            val path = runCatching { Path.of(reference) }.getOrNull() ?: return null
            val candidatesByPath = listOfNotNull(importer.parent?.resolve(path), Path.of("").toAbsolutePath().resolve(path))
                .map(::normalize)
            return candidatesByPath.firstOrNull { it in overlayPaths || Files.isRegularFile(it) }
        }

        val isStd = reference.startsWith("std.") || reference.startsWith("std/")
        if (isStd) {
            val root = sdkRoot ?: return null
            val modulePath = reference.removePrefix("std.").removePrefix("std/")
                .replace('.', '/')
                .removeSuffix(".cp")
            val standard = normalize(root.resolve("std/src/$modulePath.cp"))
            return standard.takeIf { it.startsWith(root) && (it in overlayPaths || Files.isRegularFile(it)) }
        }

        val logicalPath = reference.replace('.', '/').removeSuffix(".cp") + ".cp"
        val basename = logicalPath.substringAfterLast('/')
        val rootsToSearch = listOfNotNull(importer.parent) + roots
        rootsToSearch.distinct().forEach { root ->
            val exact = normalize(root.resolve(logicalPath))
            if (exact in overlayPaths || Files.isRegularFile(exact)) return exact
        }
        val sibling = importer.parent?.resolve(basename)?.let(::normalize)
        if (sibling != null && (sibling in overlayPaths || Files.isRegularFile(sibling))) return sibling

        val matches = candidates().filter { it.fileName.toString() == basename }
        return matches.singleOrNull()
    }

    private fun imports(path: Path, text: String): List<String> {
        val source = SourceFile(SourceFileId(path.toString().hashCode()), path, text, 1)
        return Parser(Lexer().lex(source)).parse().syntax.declarations
            .filterIsInstance<SyntaxImport>()
            .map(SyntaxImport::module)
            .distinct()
    }

    private fun normalize(path: Path): Path = path.toAbsolutePath().normalize()

    companion object {
        const val DEFAULT_MAXIMUM_CANDIDATES = 10_000
        private val EXCLUDED_DIRECTORIES = setOf(
            ".git", ".hg", ".svn", ".gradle", "build", "node_modules", "target", "dist"
        )
    }
}
