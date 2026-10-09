package cplus.compiler

import cplus.comptime.ExpansionKey
import cplus.comptime.SpecializationKey
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.ArrayDeque

/**
 * The invalidation decision made for one incremental compilation.
 *
 * `invalidatedSources` is a dependency closure, not just the set of files
 * whose bytes changed. This makes dependent semantic results explicit even
 * though the current semantic pass consumes one complete workspace catalogue.
 */
data class InvalidationReport(
    val changedSources: Set<Path>,
    val invalidatedSources: Set<Path>,
    val reusedSources: Set<Path>,
    val invalidatedExpansionKeys: Set<ExpansionKey> = emptySet(),
    val reusedExpansionKeys: Set<ExpansionKey> = emptySet(),
    val invalidatedSpecializationKeys: Set<SpecializationKey> = emptySet(),
    val reusedSpecializationKeys: Set<SpecializationKey> = emptySet()
)

data class IncrementalCompileResult(
    val result: CompileResult,
    val invalidation: InvalidationReport,
    val cacheKey: IncrementalCacheKey? = null
) {
    val isSuccessful: Boolean
        get() = result.isSuccessful
}

/** Complete semantic cache identity for one workspace compilation. */
data class IncrementalCacheKey(
    val sourceFingerprints: Map<Path, String>,
    val foreignSourceFingerprints: Map<Path, String>,
    val target: TargetInfo,
    val options: CompilerOptions,
    val cLibraries: List<String>,
    val cIncludeDirectories: List<Path>,
    val sdkIdentity: SdkManifestIdentity,
    val externalSysroot: Path?,
    val cCompiler: String?,
    val mode: CompilationMode,
    val selectedFixtureIdentities: Set<String>?,
    val rootSources: List<Path>
)

/**
 * Stateful compiler coordinator for one workspace.
 *
 * Front-end units are cached by content fingerprint. A source edit invalidates
 * that source and the reverse module-dependency closure; unrelated modules
 * retain their lexed, parsed, expanded, and AST representations. The merged
 * semantic pass is rerun for the affected workspace so imported declarations
 * cannot be served from stale semantic state.
 */
class IncrementalCompiler(
    private val compiler: CPlusCompiler = CPlusCompiler()
) {
    private var state: WorkspaceState? = null

    @Synchronized
    fun compile(request: CompileRequest): IncrementalCompileResult {
        val canonicalRequest = request.canonicalized()
        val sdk = SdkManifestLoader.load(canonicalRequest.sdkManifest)
        if (!sdk.isSuccessful) {
            return IncrementalCompileResult(
                CompileResult(sdk.diagnostics, emptyList(), null, emptyList()),
                InvalidationReport(emptySet(), emptySet(), emptySet()),
                null
            )
        }
        val sdkIdentity = sdk.manifest!!.identity
        val previous = state
        val trackedSources = (canonicalRequest.sources + previous?.sourceFingerprints.orEmpty().keys)
            .distinct()
        val sourceFingerprints = trackedSources.associateWith(::fingerprint)
        val foreignFingerprints = canonicalRequest.cSources.associateWith(::fingerprint)
        val cacheKey = IncrementalCacheKey(
            sourceFingerprints,
            foreignFingerprints,
            canonicalRequest.target,
            canonicalRequest.options,
            canonicalRequest.cLibraries,
            canonicalRequest.cIncludeDirectories,
            sdkIdentity,
            canonicalRequest.externalSysroot,
            canonicalRequest.cCompiler,
            canonicalRequest.mode,
            canonicalRequest.selectedFixtureIdentities?.toSortedSet(),
            canonicalRequest.rootSources
        )
        val configuration = RequestConfiguration.from(canonicalRequest)

        val changedSources = if (previous == null) {
            sourceFingerprints.keys
        } else {
            (sourceFingerprints.keys + previous.sourceFingerprints.keys)
                .filter { previous.sourceFingerprints[it] != sourceFingerprints[it] }
                .toSet()
        }
        val externalInputsChanged = previous != null && previous.foreignFingerprints != foreignFingerprints
        val configurationChanged = previous != null && previous.configuration != configuration
        val seedSources = if (previous == null || configurationChanged || externalInputsChanged) {
            sourceFingerprints.keys
        } else {
            changedSources
        }

        if (previous != null && seedSources.isEmpty() && previous.cacheKey == cacheKey) {
            val report = InvalidationReport(
                changedSources = emptySet(),
                invalidatedSources = emptySet(),
                reusedSources = stablePaths(sourceFingerprints.keys),
                reusedExpansionKeys = previous.expansions.values.flatten().toSet(),
                reusedSpecializationKeys = previous.specializations.values.flatten().toSet()
            )
            return IncrementalCompileResult(previous.result, report, previous.cacheKey)
        }

        if (previous != null) {
            compiler.invalidateSpecializations(
                seedSources.flatMap { previous.specializations[it].orEmpty() }.toSet()
            )
        }

        val firstPass = compiler.compileIncremental(
            canonicalRequest,
            previous?.frontends.orEmpty(),
            seedSources.intersect(sourceFingerprints.keys),
            sourceFingerprints
        )
        val dependencyClosure = dependencyClosure(
            seedSources,
            previous?.moduleGraph,
            firstPass.result.moduleGraph
        )
        val recompute = dependencyClosure.intersect(sourceFingerprints.keys)
        if (previous != null) {
            compiler.invalidateSpecializations(
                dependencyClosure.flatMap { previous.specializations[it].orEmpty() }.toSet()
            )
        }
        val pipeline = if (recompute == seedSources.intersect(sourceFingerprints.keys)) {
            firstPass
        } else {
            compiler.compileIncremental(
                canonicalRequest,
                (previous?.frontends.orEmpty() + firstPass.frontends),
                recompute,
                sourceFingerprints
            )
        }

        val currentExpansions = pipeline.frontends.mapValues { (_, entry) ->
            entry.frontend.expanded?.expandedKeys.orEmpty()
        }
        val currentSpecializations = pipeline.frontends.mapValues { (_, entry) ->
            entry.frontend.expanded?.specializationKeys.orEmpty()
        }
        val invalidatedExpansionKeys = if (previous == null) {
            emptySet()
        } else {
            (dependencyClosure.flatMap { previous.expansions[it].orEmpty() } +
                dependencyClosure.intersect(sourceFingerprints.keys).flatMap { currentExpansions[it].orEmpty() })
                .toSet()
        }
        val reusedExpansionKeys = currentExpansions
            .filterKeys { it !in recompute }
            .values
            .flatten()
            .toSet()
        val invalidatedSpecializationKeys = if (previous == null) {
            emptySet()
        } else {
            (dependencyClosure.flatMap { previous.specializations[it].orEmpty() } +
                dependencyClosure.intersect(sourceFingerprints.keys).flatMap { currentSpecializations[it].orEmpty() })
                .toSet()
        }
        val reusedSpecializationKeys = currentSpecializations
            .filterKeys { it !in recompute }
            .values
            .flatten()
            .toSet()
        val report = InvalidationReport(
            changedSources = stablePaths(changedSources),
            invalidatedSources = stablePaths(dependencyClosure),
            reusedSources = stablePaths(pipeline.frontends.keys.filter { it !in recompute }),
            invalidatedExpansionKeys = invalidatedExpansionKeys,
            reusedExpansionKeys = reusedExpansionKeys,
            invalidatedSpecializationKeys = invalidatedSpecializationKeys,
            reusedSpecializationKeys = reusedSpecializationKeys
        )

        val finalSourceFingerprints = (canonicalRequest.sources + pipeline.frontends.keys)
            .distinct()
            .associateWith(::fingerprint)
        val finalCacheKey = cacheKey.copy(sourceFingerprints = finalSourceFingerprints)

        state = WorkspaceState(
            cacheKey = finalCacheKey,
            configuration = configuration,
            sourceFingerprints = finalSourceFingerprints,
            foreignFingerprints = foreignFingerprints,
            frontends = pipeline.frontends,
            moduleGraph = pipeline.result.moduleGraph,
            expansions = currentExpansions,
            specializations = currentSpecializations,
            result = pipeline.result
        )
        return IncrementalCompileResult(pipeline.result, report, finalCacheKey)
    }

    @Synchronized
    fun clear() {
        state = null
    }

    private fun dependencyClosure(
        seeds: Set<Path>,
        previous: ModuleGraph?,
        current: ModuleGraph?
    ): Set<Path> {
        val reverse = linkedMapOf<Path, MutableSet<Path>>()
        addReverseDependencies(reverse, previous)
        addReverseDependencies(reverse, current)
        val result = linkedSetOf<Path>()
        val queue = ArrayDeque<Path>()
        seeds.map(::normalize).sortedBy(Path::toString).forEach {
            result.add(it)
            queue.addLast(it)
        }
        while (queue.isNotEmpty()) {
            val dependency = queue.removeFirst()
            reverse[dependency].orEmpty().sortedBy(Path::toString).forEach { dependent ->
                if (result.add(dependent)) queue.addLast(dependent)
            }
        }
        return result
    }

    private fun addReverseDependencies(
        reverse: MutableMap<Path, MutableSet<Path>>,
        graph: ModuleGraph?
    ) {
        if (graph == null) return
        val pathsById = graph.nodes.values.associate { it.id to normalize(it.path) }
        graph.nodes.values.forEach { node ->
            val importer = normalize(node.path)
            node.imports.forEach { importedId ->
                val imported = pathsById[importedId] ?: return@forEach
                reverse.getOrPut(imported) { linkedSetOf() }.add(importer)
            }
        }
    }

    private fun fingerprint(path: Path): String {
        val marker = when {
            !Files.exists(path) -> "missing"
            !Files.isRegularFile(path) -> "not-regular"
            else -> "file"
        }
        val bytes = if (marker == "file") Files.readAllBytes(path) else marker.toByteArray()
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return marker + ":" + digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun CompileRequest.canonicalized(): CompileRequest = copy(
        sources = sources.map(::normalize).distinct(),
        cSources = cSources.map(::normalize).distinct(),
        cIncludeDirectories = cIncludeDirectories.map(::normalize).distinct(),
        rootSources = rootSources.map(::normalize).distinct(),
        selectedFixtureIdentities = selectedFixtureIdentities?.toSortedSet()
    )

    private fun normalize(path: Path): Path = path.toAbsolutePath().normalize()

    private fun stablePaths(paths: Collection<Path>): Set<Path> =
        paths.map(::normalize).toSortedSet(compareBy(Path::toString))

    private data class RequestConfiguration(
        val target: TargetInfo,
        val options: CompilerOptions,
        val cSources: List<Path>,
        val cLibraries: List<String>,
        val cIncludeDirectories: List<Path>,
        val sdkIdentity: SdkManifestIdentity,
        val externalSysroot: Path?,
        val cCompiler: String?,
        val mode: CompilationMode,
        val selectedFixtureIdentities: Set<String>?,
        val rootSources: List<Path>
    ) {
        companion object {
            fun from(request: CompileRequest): RequestConfiguration {
                val sdk = SdkManifestLoader.load(request.sdkManifest)
                check(sdk.isSuccessful) { "validated SDK manifest must be available" }
                return RequestConfiguration(
                    request.target,
                    request.options,
                    request.cSources,
                    request.cLibraries,
                    request.cIncludeDirectories,
                    sdk.manifest!!.identity,
                    request.externalSysroot,
                    request.cCompiler,
                    request.mode,
                    request.selectedFixtureIdentities?.toSortedSet(),
                    request.rootSources
                )
            }
        }
    }

    private data class WorkspaceState(
        val cacheKey: IncrementalCacheKey,
        val configuration: RequestConfiguration,
        val sourceFingerprints: Map<Path, String>,
        val foreignFingerprints: Map<Path, String>,
        val frontends: Map<Path, FrontendCacheEntry>,
        val moduleGraph: ModuleGraph?,
        val expansions: Map<Path, Set<ExpansionKey>>,
        val specializations: Map<Path, Set<SpecializationKey>>,
        val result: CompileResult
    )
}
