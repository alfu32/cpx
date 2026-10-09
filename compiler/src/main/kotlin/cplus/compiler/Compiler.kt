package cplus.compiler

import cplus.backend.*
import cplus.comptime.ComptimeTypeIdentity
import cplus.comptime.ComptimeReferenceResolver
import cplus.comptime.ComptimeTargetInfo
import cplus.comptime.ComptimeTypeResolver
import cplus.comptime.CpxExpansionResult
import cplus.comptime.CpxExpander
import cplus.comptime.ImportedComptimeDefinition
import cplus.comptime.SpecializationKey
import cplus.comptime.StructuralFieldDescriptor
import cplus.comptime.StructuralMethodDescriptor
import cplus.comptime.StructuralLayout
import cplus.comptime.StructuralTypeDescriptor
import cplus.comptime.StructuralTypeReference
import cplus.core.*
import cplus.semantic.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import kotlin.io.path.readText

private const val MAX_WORKSPACE_CPX_ROUNDS = 64

fun defaultHostTargetTriple(): String {
    val operatingSystem = System.getProperty("os.name").lowercase()
    val architecture = System.getProperty("os.arch").lowercase()
    val isAarch64 = architecture in setOf("aarch64", "arm64")
    return when {
        operatingSystem.contains("windows") -> if (isAarch64) "windows-aarch64" else "windows-x86_64"
        operatingSystem.contains("mac") || operatingSystem.contains("darwin") -> if (isAarch64) "darwin-aarch64" else "darwin-x86_64"
        else -> if (isAarch64) "linux-aarch64" else "linux-x86_64"
    }
}

data class TargetInfo(
    val cDialect: String = "c17",
    val buildProfile: BuildProfile = BuildProfile(),
    val targetTriple: String = "linux-x86_64"
)

data class CompilerOptions(
    val emitSourceMap: Boolean = true,
    /** Maximum number of independent front-end workers for incremental builds. */
    val parallelism: Int = 1
) {
    init {
        require(parallelism > 0) { "parallelism must be positive" }
    }
}

enum class CompilationMode { NORMAL, TEST }

data class CompileRequest(
    val sources: List<Path>,
    val target: TargetInfo = TargetInfo(),
    val options: CompilerOptions = CompilerOptions(),
    val cSources: List<Path> = emptyList(),
    val cLibraries: List<String> = emptyList(),
    val cIncludeDirectories: List<Path> = emptyList(),
    val sdkManifest: Path = SdkManifestLocator.defaultManifestPath(),
    val externalSysroot: Path? = null,
    val cCompiler: String? = null,
    val mode: CompilationMode = CompilationMode.NORMAL,
    /** Null selects every fixture owned by rootSources; an empty set selects none. */
    val selectedFixtureIdentities: Set<String>? = null,
    /** Preserved when sources is expanded to include imported workspace modules. */
    val rootSources: List<Path> = sources
)

data class TextSource(
    val path: Path,
    val text: String
)

data class CSourceDependency(val path: Path)

enum class CLinkDependencyKind {
    LOCAL,
    FOREIGN
}

data class CLinkDependency(
    val value: String,
    val kind: CLinkDependencyKind
)

data class CompilationArtifacts(
    val source: SourceFile,
    val lexed: LexedSource,
    val parsed: Parser.ParsedSource,
    val expanded: CpxExpansionResult?,
    val ast: AstProgram,
    val semantic: SemanticResult,
    val lowered: LoweredCResult?,
    val generated: GeneratedCUnit?,
    val additionalDiagnostics: List<Diagnostic> = emptyList(),
    val header: GeneratedCUnit? = null
) {
    /** Public phase boundary for inspection without exposing the CPX module type. */
    val expandedSyntax: SyntaxProgram?
        get() = expanded?.program
}

data class CompileResult(
    val diagnostics: List<Diagnostic>,
    val generatedUnits: List<GeneratedCUnit>,
    val semanticModel: SemanticModel?,
    val artifacts: List<CompilationArtifacts>,
    val moduleGraph: ModuleGraph? = null,
    val cSourceDependencies: List<CSourceDependency> = emptyList(),
    val generatedHeaders: List<GeneratedCUnit> = emptyList(),
    val cLinkDependencies: List<CLinkDependency> = emptyList(),
    val sdkResolution: SdkResolution? = null
) {
    val isSuccessful: Boolean
        get() = diagnostics.none { it.severity == DiagnosticSeverity.ERROR }
}

/**
 * Shared state passed between pipeline phases.
 *
 * The state object is deliberately the only communication boundary between
 * phases. This allows later channel schedulers to represent circular
 * dependencies without making compiler modules depend on one another.
 */
class CompilerContext(
    val sourceRepository: SourceRepository = SourceRepository(),
    val lexer: Lexer = Lexer(),
    val astBuilder: AstBuilder = AstBuilder(),
    val semanticAnalyzer: SemanticAnalyzer = SemanticAnalyzer(),
    val semanticAnalyzerFactory: ((HeaderEnvironment) -> SemanticAnalyzer)? = null,
    val target: TargetInfo = TargetInfo(),
    val cpxExpander: CpxExpander = CpxExpander(lexer, target = BuildProfileValidator.toComptimeTarget(target)),
    val closureLowerer: AstClosureLowerer = AstClosureLowerer(),
    val cLowererFactory: (SemanticModel) -> CLowerer = ::CLowerer,
    val cEmitter: CEmitter = CEmitter()
) {
    fun analyzerFor(environment: HeaderEnvironment, provisional: Boolean = false): SemanticAnalyzer =
        semanticAnalyzerFactory?.invoke(environment)
            ?: if (provisional) SemanticAnalyzer() else semanticAnalyzer
}

internal data class FrontendCacheEntry(
    val fingerprint: String,
    val frontend: CPlusCompiler.FrontendUnit
)

internal data class IncrementalPipeline(
    val result: CompileResult,
    val frontends: Map<Path, FrontendCacheEntry>
)

class CPlusCompiler(
    private val context: CompilerContext = CompilerContext()
) {
    private var activeTargetAbiDescriptor: TargetAbiDescriptor? = null

    internal fun invalidateSpecializations(keys: Set<SpecializationKey>) {
        if (keys.isNotEmpty()) context.cpxExpander.invalidateSpecializations(keys)
    }

    fun remapCCompilerDiagnostics(
        result: CompileResult,
        generatedPath: Path,
        compilerOutput: String
    ): List<RemappedCCompilerDiagnostic> {
        val generated = result.generatedUnits.singleOrNull() ?: return emptyList()
        return CCompilerDiagnosticRemapper(context.sourceRepository).remap(
            compilerOutput,
            generatedPath,
            generated
        )
    }

    fun compile(request: CompileRequest): CompileResult {
        val sdk = SdkManifestLoader.load(request.sdkManifest)
        if (!sdk.isSuccessful) return sdkFailure(sdk.diagnostics)
        val profileDiagnostics = BuildProfileValidator.validate(request.target.buildProfile, sdk.manifest!!)
        if (profileDiagnostics.isNotEmpty()) return sdkFailure(profileDiagnostics)
        val sdkResolution = SdkResolver.resolve(sdk.manifest, request.target, request.externalSysroot)
        if (!sdkResolution.isSuccessful) return sdkFailure(sdkResolution.diagnostics)
        val descriptor = TargetRegistry.load(sdkResolution.resolution!!)
        if (!descriptor.isSuccessful) return sdkFailure(descriptor.diagnostics)
        activeTargetAbiDescriptor = descriptor.descriptor
        val intrinsics = IntrinsicRegistry.load(sdkResolution.resolution.layout.intrinsicCatalogue)
        if (!intrinsics.isSuccessful) return sdkFailure(intrinsics.diagnostics)
        val metadata = SdkMetadataCache.loadOrBuild(sdkResolution.resolution!!)
        if (!metadata.isSuccessful) return sdkFailure(metadata.diagnostics)
        val headerEnvironment = HeaderEnvironment.create(request, sdkResolution.resolution, descriptor.descriptor!!)
        val resolvedSdk = sdkResolution.resolution.copy(
            metadata = metadata.metadata,
            targetDescriptor = descriptor.descriptor,
            intrinsics = intrinsics.definitions,
            headerEnvironment = headerEnvironment
        )
        context.cpxExpander.configureTarget(BuildProfileValidator.toComptimeTarget(request.target, descriptor.descriptor))
        val foreignInputs = loadForeignSources(request.cSources)
        val cLinkDependencies = linkDependencies(request.cLibraries)
        val linkDiagnostics = validateLinkDependencies(cLinkDependencies)
        return compileWorkspace(
            request,
            foreignInputs,
            cLinkDependencies,
            linkDiagnostics,
            sdkResolution = resolvedSdk
        )
    }

    /**
     * Runs a workspace compilation while reusing front-end units whose
     * fingerprints are unchanged and which the incremental coordinator has
     * not invalidated. Semantic analysis still receives the complete merged
     * workspace, so a changed declaration cannot be hidden by a stale module
     * result. The returned front-end map is the next cache snapshot.
     */
    internal fun compileIncremental(
        request: CompileRequest,
        cached: Map<Path, FrontendCacheEntry>,
        recompute: Set<Path>,
        fingerprints: Map<Path, String>
    ): IncrementalPipeline {
        val sdk = SdkManifestLoader.load(request.sdkManifest)
        if (!sdk.isSuccessful) return IncrementalPipeline(sdkFailure(sdk.diagnostics), emptyMap())
        val profileDiagnostics = BuildProfileValidator.validate(request.target.buildProfile, sdk.manifest!!)
        if (profileDiagnostics.isNotEmpty()) return IncrementalPipeline(sdkFailure(profileDiagnostics), emptyMap())
        val sdkResolution = SdkResolver.resolve(sdk.manifest, request.target, request.externalSysroot)
        if (!sdkResolution.isSuccessful) return IncrementalPipeline(sdkFailure(sdkResolution.diagnostics), emptyMap())
        val descriptor = TargetRegistry.load(sdkResolution.resolution!!)
        if (!descriptor.isSuccessful) return IncrementalPipeline(sdkFailure(descriptor.diagnostics), emptyMap())
        activeTargetAbiDescriptor = descriptor.descriptor
        val intrinsics = IntrinsicRegistry.load(sdkResolution.resolution.layout.intrinsicCatalogue)
        if (!intrinsics.isSuccessful) return IncrementalPipeline(sdkFailure(intrinsics.diagnostics), emptyMap())
        val metadata = SdkMetadataCache.loadOrBuild(sdkResolution.resolution!!)
        if (!metadata.isSuccessful) return IncrementalPipeline(sdkFailure(metadata.diagnostics), emptyMap())
        val headerEnvironment = HeaderEnvironment.create(request, sdkResolution.resolution, descriptor.descriptor!!)
        val resolvedSdk = sdkResolution.resolution.copy(
            metadata = metadata.metadata,
            targetDescriptor = descriptor.descriptor,
            intrinsics = intrinsics.definitions,
            headerEnvironment = headerEnvironment
        )
        context.cpxExpander.configureTarget(BuildProfileValidator.toComptimeTarget(request.target, descriptor.descriptor))
        val foreignInputs = loadForeignSources(request.cSources)
        val cLinkDependencies = linkDependencies(request.cLibraries)
        val linkDiagnostics = validateLinkDependencies(cLinkDependencies)
        val paths = request.sources.map { it.toAbsolutePath().normalize() }
        val pathsToCompute = paths.filter { path ->
            val fingerprint = fingerprints.getValue(path)
            val reusable = cached[path]
            path in recompute || reusable?.fingerprint != fingerprint
        }
        val computed = prepareFrontends(pathsToCompute, request.options.parallelism, headerEnvironment)
        val candidates = cached.mapValues { it.value.frontend } + computed
        val graph = ModuleGraphBuilder().build(
            candidates.values.map { unit -> ModuleSource(unit.source, unit.expanded?.program ?: unit.parsed.syntax) }
        )
        val reachableModules = linkedSetOf<ModuleId>()
        val pendingModules = ArrayDeque<ModuleId>()
        paths.mapNotNull(graph::moduleIdForPath).forEach { root ->
            if (reachableModules.add(root)) pendingModules.addLast(root)
        }
        while (pendingModules.isNotEmpty()) {
            val current = pendingModules.removeFirst()
            graph.nodes[current]?.imports.orEmpty().forEach { imported ->
                if (reachableModules.add(imported)) pendingModules.addLast(imported)
            }
        }
        val pathsByModule = graph.nodes.values.associate { it.id to it.path.toAbsolutePath().normalize() }
        val reachablePaths = reachableModules.mapNotNullTo(linkedSetOf()) { pathsByModule[it] }
        val selectedPaths = (paths.filter { it in reachablePaths } +
            (reachablePaths - paths.toSet()).sortedBy(Path::toString)).distinct()
        val entries = selectedPaths.associateWith { path ->
            val frontend = candidates[path]
                ?: error("reachable incremental module has no prepared frontend: $path")
            FrontendCacheEntry(fingerprints[path].orEmpty(), frontend)
        }
        val units = selectedPaths.map { entries.getValue(it).frontend }
        val effectiveRequest = request.copy(sources = selectedPaths)
        val result = if (units.size <= 1) {
            val artifacts = units.map {
                val headers = discoverCHeaders(listOf(it), headerEnvironment)
                compileFrontend(
                    it,
                    request.options,
                    foreignInputs.units + headers.units,
                    headerEnvironment,
                    headers.diagnostics,
                    request.mode,
                    request.selectedFixtureIdentities,
                    request.rootSources
                )
            }
            resultOf(
                artifacts,
                cSourceDependencies = dependencies(request.cSources),
                additionalDiagnostics = foreignInputs.diagnostics + linkDiagnostics,
                cLinkDependencies = cLinkDependencies,
                sdkResolution = resolvedSdk
            )
        } else {
            compileWorkspace(
                effectiveRequest,
                foreignInputs,
                cLinkDependencies,
                linkDiagnostics,
                units,
                resolvedSdk
            )
        }
        return IncrementalPipeline(result, entries)
    }

    fun compileText(
        path: Path,
        text: String,
        options: CompilerOptions = CompilerOptions(),
        sdkManifest: Path = SdkManifestLocator.defaultManifestPath(),
        target: TargetInfo = TargetInfo(),
        cCompiler: String? = null,
        cIncludeDirectories: List<Path> = emptyList(),
        externalSysroot: Path? = null
    ): CompileResult {
        return compileTextWorkspace(
            listOf(TextSource(path, text)), options, sdkManifest, target,
            cCompiler, cIncludeDirectories, externalSysroot
        )
    }

    fun compileTextWorkspace(
        sources: List<TextSource>,
        options: CompilerOptions = CompilerOptions(),
        sdkManifest: Path = SdkManifestLocator.defaultManifestPath(),
        target: TargetInfo = TargetInfo(),
        cCompiler: String? = null,
        cIncludeDirectories: List<Path> = emptyList(),
        externalSysroot: Path? = null
    ): CompileResult {
        val sdk = SdkManifestLoader.load(sdkManifest)
        if (!sdk.isSuccessful) return sdkFailure(sdk.diagnostics)
        val profileDiagnostics = BuildProfileValidator.validate(target.buildProfile, sdk.manifest!!)
        if (profileDiagnostics.isNotEmpty()) return sdkFailure(profileDiagnostics)
        val sdkResolution = SdkResolver.resolve(sdk.manifest, target, externalSysroot)
        if (!sdkResolution.isSuccessful) return sdkFailure(sdkResolution.diagnostics)
        val descriptor = TargetRegistry.load(sdkResolution.resolution!!)
        if (!descriptor.isSuccessful) return sdkFailure(descriptor.diagnostics)
        activeTargetAbiDescriptor = descriptor.descriptor
        val intrinsics = IntrinsicRegistry.load(sdkResolution.resolution.layout.intrinsicCatalogue)
        if (!intrinsics.isSuccessful) return sdkFailure(intrinsics.diagnostics)
        val metadata = SdkMetadataCache.loadOrBuild(sdkResolution.resolution!!)
        if (!metadata.isSuccessful) return sdkFailure(metadata.diagnostics)
        val request = CompileRequest(
            sources = sources.map { it.path },
            target = target,
            options = options,
            sdkManifest = sdkManifest,
            cIncludeDirectories = cIncludeDirectories,
            externalSysroot = externalSysroot,
            cCompiler = cCompiler
        )
        val headerEnvironment = HeaderEnvironment.create(request, sdkResolution.resolution, descriptor.descriptor!!)
        val resolvedSdk = sdkResolution.resolution.copy(
            metadata = metadata.metadata,
            targetDescriptor = descriptor.descriptor,
            intrinsics = intrinsics.definitions,
            headerEnvironment = headerEnvironment
        )
        context.cpxExpander.configureTarget(BuildProfileValidator.toComptimeTarget(target, descriptor.descriptor))
        val sourceFiles = sources
            .distinctBy { it.path.toAbsolutePath().normalize() }
            .map { source -> context.sourceRepository.put(source.path, source.text) }
        // Finish parsing every workspace source before any source enters CPX.
        // Besides making the phase boundary explicit, this keeps source IDs
        // and parse results independent of expansion order.
        val parsedUnits = sourceFiles.map(::parseFrontend)
        val frontends = expandWorkspace(parsedUnits, headerEnvironment)
        if (frontends.size <= 1) {
            return resultOf(
                frontends.map { frontend ->
                    val headers = discoverCHeaders(listOf(frontend), headerEnvironment)
                    compileFrontend(
                        frontend,
                        options,
                        headers.units,
                        headerEnvironment,
                        headers.diagnostics,
                        request.mode,
                        request.selectedFixtureIdentities,
                        request.rootSources
                    )
                },
                sdkResolution = resolvedSdk
            )
        }
        val workspaceRequest = request.copy(sources = frontends.map { it.source.path })
        return compileWorkspace(
            workspaceRequest,
            ForeignInputs(emptyList(), emptyList()),
            emptyList(),
            emptyList(),
            frontends,
            sdkResolution = resolvedSdk
        )
    }

    fun sourcePathFor(file: SourceFileId): Path? = context.sourceRepository.find(file)?.path

    private fun sdkFailure(diagnostics: List<Diagnostic>): CompileResult = CompileResult(
        diagnostics = diagnostics,
        generatedUnits = emptyList(),
        semanticModel = null,
        artifacts = emptyList()
    )

    private fun compileOne(
        path: Path,
        options: CompilerOptions,
        foreignSources: ForeignInputs,
        headerEnvironment: HeaderEnvironment
    ): CompilationArtifacts {
        val unit = frontend(path, headerEnvironment)
        val headers = discoverCHeaders(listOf(unit), headerEnvironment)
        return compileFrontend(
            unit,
            options,
            foreignSources.units + headers.units,
            headerEnvironment,
            headers.diagnostics
        )
    }

    private fun compileFrontend(
        frontend: FrontendUnit,
        options: CompilerOptions,
        foreignSources: List<CSourceUnit> = emptyList(),
        headerEnvironment: HeaderEnvironment,
        discoveryDiagnostics: List<Diagnostic> = emptyList(),
        mode: CompilationMode = CompilationMode.NORMAL,
        selectedFixtureIdentities: Set<String>? = null,
        rootSources: List<Path> = listOf(frontend.source.path)
    ): CompilationArtifacts {
        val source = frontend.source
        val lexed = frontend.lexed
        val parsed = frontend.parsed
        val expanded = frontend.expanded
        val ast = frontend.ast
        val semantic = context.analyzerFor(headerEnvironment).analyze(
            ast,
            foreignSources = foreignSources,
            knownModules = foreignSources.map { it.moduleName }.toSet(),
            targetFeatures = activeTargetAbiDescriptor?.features.orEmpty(),
            targetName = activeTargetAbiDescriptor?.targetTriple ?: "selected target"
        )
        if (!semantic.isSuccessful) {
            return CompilationArtifacts(source, lexed, parsed, expanded, ast, semantic, null, null, frontend.closureDiagnostics + discoveryDiagnostics)
        }
        if ((frontend.diagnostics() + discoveryDiagnostics).any { it.severity == DiagnosticSeverity.ERROR }) {
            return CompilationArtifacts(source, lexed, parsed, expanded, ast, semantic, null, null, frontend.closureDiagnostics + discoveryDiagnostics)
        }
        val model = semantic.model ?: return CompilationArtifacts(
            source, lexed, parsed, expanded, ast, semantic, null, null,
            frontend.closureDiagnostics + discoveryDiagnostics
        )
        val selection = selectRootFixtures(model, mode, selectedFixtureIdentities, rootSources)
        if (selection.diagnostics.isNotEmpty()) {
            return CompilationArtifacts(
                source, lexed, parsed, expanded, ast, semantic, null, null,
                frontend.closureDiagnostics + discoveryDiagnostics + selection.diagnostics
            )
        }
        val backend = BackendProcessingPipeline(context.cLowererFactory(model), context.cEmitter).run(
            ast,
            model,
            mode == CompilationMode.TEST,
            selection.identities
        )
        return CompilationArtifacts(
            source,
            lexed,
            parsed,
            expanded,
            ast,
            semantic,
            backend.lowered,
            backend.generated,
            frontend.closureDiagnostics + discoveryDiagnostics,
            backend.header
        )
    }

    private data class RootFixtureSelection(val identities: Set<String>, val diagnostics: List<Diagnostic>)

    private fun selectRootFixtures(
        model: SemanticModel,
        mode: CompilationMode,
        requested: Set<String>?,
        rootSources: List<Path>
    ): RootFixtureSelection {
        if (mode != CompilationMode.TEST) return RootFixtureSelection(emptySet(), emptyList())
        val roots = rootSources.mapTo(linkedSetOf()) { it.toAbsolutePath().normalize() }
        val rootFixtures = model.testFixtures.filter { fixture ->
            val file = fixture.fixture.origin.primaryRange?.file ?: return@filter false
            context.sourceRepository.find(file)?.path?.toAbsolutePath()?.normalize() in roots
        }
        val rootIds = rootFixtures.mapTo(linkedSetOf(), SemanticTestFixture::identity)
        if (requested == null) return RootFixtureSelection(rootIds, emptyList())
        val unowned = requested - rootIds
        val diagnostics = unowned.map { identity ->
            Diagnostic(
                DiagnosticSeverity.ERROR,
                "selected test fixture '$identity' is not owned by an explicit root source",
                null,
                "CMPTEST001"
            )
        }
        return RootFixtureSelection(requested.intersect(rootIds), diagnostics)
    }

    private fun compileWorkspace(
        request: CompileRequest,
        foreignInputs: ForeignInputs,
        cLinkDependencies: List<CLinkDependency>,
        linkDiagnostics: List<Diagnostic>,
        units: List<FrontendUnit>? = null,
        sdkResolution: SdkResolution? = null
    ): CompileResult {
        val headerEnvironment = requireNotNull(sdkResolution?.headerEnvironment) {
            "workspace compilation requires its resolved header environment"
        }
        val resolvedUnits = units ?: prepareFrontends(
            request.sources,
            request.options.parallelism,
            headerEnvironment
        ).values.toList()
        val discoveredHeaders = discoverCHeaders(resolvedUnits, headerEnvironment)
        val allForeignUnits = foreignInputs.units + discoveredHeaders.units
        val moduleGraph = ModuleGraphBuilder().build(
            resolvedUnits.map { ModuleSource(it.source, it.expanded?.program ?: it.parsed.syntax) }
        )
        val first = resolvedUnits.firstOrNull()
        val cSourceDependencies = dependencies(request.cSources)
        if (first == null) {
            return CompileResult(
                emptyList(),
                emptyList(),
                null,
                emptyList(),
                moduleGraph,
                cSourceDependencies,
                cLinkDependencies = cLinkDependencies,
                sdkResolution = sdkResolution
            )
        }

        val mergedOrigin = first.ast.origin
        val mergedAst = AstProgram(
            resolvedUnits.flatMap { it.ast.declarations },
            mergedOrigin,
            resolvedUnits.map { unit ->
                val moduleId = requireNotNull(moduleGraph.moduleIdForPath(unit.source.path))
                val comptimeFunctions = (
                    unit.parsed.syntax.declarations.filterIsInstance<SyntaxComptimeFunction>() +
                        unit.expanded?.availableComptimeDefinitions?.values.orEmpty()
                    ).distinctBy { it.origin to it.name }
                val comptimeFunctionSyntax = unit.parsed.syntax.copy(declarations = comptimeFunctions)
                AstModule(
                    moduleId.value,
                    unit.ast.declarations,
                    unit.source.path.toAbsolutePath().normalize().toString(),
                    context.astBuilder.build(comptimeFunctionSyntax).declarations.filterIsInstance<AstComptimeFunction>(),
                    context.astBuilder.build(unit.parsed.syntax).declarations
                        .filterIsInstance<AstCpxInvocation>()
                )
            }
        )
        val semantic = context.analyzerFor(headerEnvironment).analyze(
            mergedAst,
            moduleGraph.moduleNames + allForeignUnits.map { it.moduleName },
            allForeignUnits,
            activeTargetAbiDescriptor?.features.orEmpty(),
            activeTargetAbiDescriptor?.targetTriple ?: "selected target"
        )
        val additionalDiagnostics = first.closureDiagnostics + resolvedUnits.drop(1).flatMap { it.diagnostics() } +
            unresolvedImportCycleDiagnostics(moduleGraph, semantic.diagnostics) +
            foreignInputs.diagnostics + discoveredHeaders.diagnostics +
            linkDiagnostics
        val base = first
        if (!semantic.isSuccessful) {
            return resultOf(
                listOf(
                    CompilationArtifacts(
                        base.source,
                        base.lexed,
                        base.parsed,
                        base.expanded,
                        mergedAst,
                        semantic,
                        null,
                        null,
                        additionalDiagnostics
                    )
                ),
                moduleGraph,
                cSourceDependencies,
                cLinkDependencies = cLinkDependencies,
                sdkResolution = sdkResolution
            )
        }
        val model = semantic.model
        if (model == null) {
            return resultOf(
                listOf(
                    CompilationArtifacts(
                        base.source,
                        base.lexed,
                        base.parsed,
                        base.expanded,
                        mergedAst,
                        semantic,
                        null,
                        null,
                        additionalDiagnostics
                    )
                ),
                moduleGraph,
                cSourceDependencies,
                cLinkDependencies = cLinkDependencies,
                sdkResolution = sdkResolution
            )
        }
        if (resolvedUnits.any { unit -> unit.diagnostics().any { it.severity == DiagnosticSeverity.ERROR } }) {
            return resultOf(
                listOf(
                    CompilationArtifacts(
                        base.source,
                        base.lexed,
                        base.parsed,
                        base.expanded,
                        mergedAst,
                        semantic,
                        null,
                        null,
                        additionalDiagnostics
                    )
                ),
                moduleGraph,
                cSourceDependencies,
                cLinkDependencies = cLinkDependencies,
                sdkResolution = sdkResolution
            )
        }
        val fixtureSelection = selectRootFixtures(model, request.mode, request.selectedFixtureIdentities, request.rootSources)
        if (fixtureSelection.diagnostics.isNotEmpty()) {
            return resultOf(
                listOf(CompilationArtifacts(base.source, base.lexed, base.parsed, base.expanded, mergedAst, semantic, null, null, additionalDiagnostics + fixtureSelection.diagnostics)),
                moduleGraph,
                cSourceDependencies,
                cLinkDependencies = cLinkDependencies,
                sdkResolution = sdkResolution
            )
        }
        val backend = BackendProcessingPipeline(context.cLowererFactory(model), context.cEmitter).run(
            mergedAst,
            model,
            request.mode == CompilationMode.TEST,
            fixtureSelection.identities
        )
        if (backend.lowered.diagnostics.any { it.severity == DiagnosticSeverity.ERROR }) {
            return resultOf(
                listOf(
                    CompilationArtifacts(
                        base.source,
                        base.lexed,
                        base.parsed,
                        base.expanded,
                        mergedAst,
                        semantic,
                        backend.lowered,
                        null,
                        additionalDiagnostics
                    )
                ),
                moduleGraph,
                cSourceDependencies,
                cLinkDependencies = cLinkDependencies,
                sdkResolution = sdkResolution
            )
        }
        return resultOf(
            listOf(
                CompilationArtifacts(
                    base.source,
                    base.lexed,
                    base.parsed,
                    base.expanded,
                    mergedAst,
                    semantic,
                    backend.lowered,
                    backend.generated,
                    additionalDiagnostics,
                    backend.header
                )
            ),
            moduleGraph,
            cSourceDependencies,
            cLinkDependencies = cLinkDependencies,
            sdkResolution = sdkResolution
        )
    }

    private fun resultOf(
        artifacts: List<CompilationArtifacts>,
        moduleGraph: ModuleGraph? = null,
        cSourceDependencies: List<CSourceDependency> = emptyList(),
        additionalDiagnostics: List<Diagnostic> = emptyList(),
        cLinkDependencies: List<CLinkDependency> = emptyList(),
        sdkResolution: SdkResolution? = null
    ): CompileResult = CompileResult(
        diagnostics = artifacts.flatMap { it.allDiagnostics() } + additionalDiagnostics,
        generatedUnits = artifacts.mapNotNull { it.generated },
        semanticModel = artifacts.singleOrNull()?.semantic?.model,
        artifacts = artifacts,
        moduleGraph = moduleGraph,
        cSourceDependencies = cSourceDependencies,
        generatedHeaders = artifacts.mapNotNull { it.header },
        cLinkDependencies = cLinkDependencies,
        sdkResolution = sdkResolution
    )

    private fun dependencies(paths: List<Path>): List<CSourceDependency> = paths
        .map { it.toAbsolutePath().normalize() }
        .distinct()
        .map(::CSourceDependency)

    private fun linkDependencies(values: List<String>): List<CLinkDependency> = buildList {
        values.forEach { value ->
            val path = runCatching { Path.of(value) }.getOrNull()
            val isLocal = path != null && (
                Files.exists(path) ||
                    value.contains('/') ||
                    value.contains('\\') ||
                    value.endsWith(".a") ||
                    value.endsWith(".so") ||
                    value.endsWith(".dylib")
                )
            val dependency = if (isLocal) {
                CLinkDependency(path!!.toAbsolutePath().normalize().toString(), CLinkDependencyKind.LOCAL)
            } else {
                CLinkDependency(value.removePrefix("-l"), CLinkDependencyKind.FOREIGN)
            }
            if (none { it == dependency }) add(dependency)
        }
    }

    private fun validateLinkDependencies(dependencies: List<CLinkDependency>): List<Diagnostic> = dependencies
        .filter { it.kind == CLinkDependencyKind.LOCAL && !Files.isRegularFile(Path.of(it.value)) }
        .map { dependency ->
            Diagnostic(
                DiagnosticSeverity.ERROR,
                "C library dependency does not exist or is not a regular file: ${dependency.value}",
                null,
                "CIMP003"
            )
        }

    private fun loadForeignSources(paths: List<Path>): ForeignInputs {
        val units = mutableListOf<CSourceUnit>()
        val diagnostics = mutableListOf<Diagnostic>()
        dependencies(paths).forEach { dependency ->
            val path = dependency.path
            if (!Files.isRegularFile(path)) {
                diagnostics += Diagnostic(
                    DiagnosticSeverity.ERROR,
                    "C source dependency does not exist or is not a regular file: $path",
                    null,
                    "CIMP001"
                )
                return@forEach
            }
            try {
                val source = context.sourceRepository.put(path, Files.readString(path))
                units += CSourceUnit(source, "c.source.$path")
            } catch (error: Exception) {
                diagnostics += Diagnostic(
                    DiagnosticSeverity.ERROR,
                    "unable to read C source dependency '$path': ${error.message ?: error::class.simpleName}",
                    null,
                    "CIMP002"
                )
            }
        }
        return ForeignInputs(units, diagnostics)
    }

    private fun discoverCHeaders(frontends: List<FrontendUnit>, environment: HeaderEnvironment): ForeignInputs {
        val modules = frontends.asSequence()
            .flatMap { it.ast.declarations.asSequence() }
            .filterIsInstance<AstImport>()
            .map { it.module }
            .filter { it.startsWith("c.") }
            .distinct()
            .sorted()
            .toList()
        val units = mutableListOf<CSourceUnit>()
        val diagnostics = mutableListOf<Diagnostic>()
        val discovery = CHeaderDiscovery()
        modules.forEach { module ->
            val result = discovery.discover(module, environment)
            diagnostics += result.diagnostics
            val processed = result.preprocessed ?: return@forEach
            val source = context.sourceRepository.put(processed.header, processed.text)
            units += CSourceUnit(
                source,
                module,
                processed.semanticMacros(),
                processed.semanticSourceLineOrigins()
            )
        }
        return ForeignInputs(units, diagnostics)
    }

    private fun unresolvedImportCycleDiagnostics(
        moduleGraph: ModuleGraph,
        diagnostics: List<Diagnostic>
    ): List<Diagnostic> {
        val unresolvedImportCodes = setOf("SEM402", "SEM404", "SEM406")
        if (diagnostics.none { it.code in unresolvedImportCodes }) return emptyList()
        return moduleGraph.cyclicComponents.map { component ->
            Diagnostic(
                DiagnosticSeverity.ERROR,
                "module import cycle cannot reach a declaration fixed point: ${component.modules.joinToString(" -> ") { it.value }}",
                null,
                "MOD201"
            )
        }
    }

    private fun CompilationArtifacts.allDiagnostics(): List<Diagnostic> = buildList {
        addAll(lexed.diagnostics)
        addAll(parsed.diagnostics.filterNot { it in lexed.diagnostics })
        addAll(expanded?.diagnostics.orEmpty())
        addAll(semantic.diagnostics)
        addAll(lowered?.diagnostics.orEmpty())
        addAll(additionalDiagnostics)
    }

    internal data class FrontendUnit(
        val source: SourceFile,
        val lexed: LexedSource,
        val parsed: Parser.ParsedSource,
        val expanded: CpxExpansionResult?,
        val ast: AstProgram,
        val closureDiagnostics: List<Diagnostic> = emptyList()
    ) {
        fun diagnostics(): List<Diagnostic> = buildList {
            addAll(lexed.diagnostics)
            addAll(parsed.diagnostics.filterNot { it in lexed.diagnostics })
            addAll(expanded?.diagnostics.orEmpty())
            addAll(closureDiagnostics)
        }
    }

    /** Parsed source boundary: no provisional semantic pass or CPX has run. */
    internal data class ParsedUnit(
        val source: SourceFile,
        val lexed: LexedSource,
        val parsed: Parser.ParsedSource
    )

    private fun frontend(path: Path, headerEnvironment: HeaderEnvironment): FrontendUnit {
        if (!Files.exists(path)) {
            val source = context.sourceRepository.put(path, "")
            return missingFrontend(source)
        }
        val source = context.sourceRepository.put(path, path.readText())
        return frontend(source, headerEnvironment)
    }

    private fun prepareFrontends(
        paths: List<Path>,
        parallelism: Int,
        headerEnvironment: HeaderEnvironment
    ): Map<Path, FrontendUnit> {
        if (paths.isEmpty()) return emptyMap()
        // Register every source in request order before workers start. This
        // keeps SourceFileId assignment independent of worker scheduling.
        val prepared = paths.associateWith { path ->
            if (Files.exists(path)) {
                PreparedSource(context.sourceRepository.put(path, path.readText()), false)
            } else {
                PreparedSource(context.sourceRepository.put(path, ""), true)
            }
        }
        fun parse(preparedSource: PreparedSource): ParsedUnit? =
            if (preparedSource.missing) null else parseFrontend(preparedSource.source)

        val parsedUnits = if (parallelism == 1 || paths.size == 1) {
            paths.associateWith { path -> parse(prepared.getValue(path)) }
        } else {
            val workers = Executors.newFixedThreadPool(parallelism.coerceAtMost(paths.size))
            try {
                val futures = paths.map { path ->
                    path to workers.submit<ParsedUnit?> { parse(prepared.getValue(path)) }
                }
                futures.associate { (path, future) -> path to future.get() }
            } finally {
                workers.shutdown()
            }
        }

        val workspaceUnits = paths.mapNotNull { parsedUnits[it] }
        val expandedUnits = expandWorkspace(workspaceUnits, headerEnvironment)
        val missingUnits = paths.mapNotNull { path ->
            prepared.getValue(path).takeIf(PreparedSource::missing)?.let { missingFrontend(it.source) }
        }
        return (expandedUnits + missingUnits).associateBy { it.source.path.toAbsolutePath().normalize() }
    }

    /**
     * Re-expands the parsed workspace as import edges and generated callable
     * declarations become visible. Newly materialized path imports are parsed
     * into the same workspace; the bounded loop never recursively compiles a
     * provider from inside CPX evaluation.
     */
    private fun expandWorkspace(
        initialUnits: List<ParsedUnit>,
        headerEnvironment: HeaderEnvironment
    ): List<FrontendUnit> {
        if (initialUnits.isEmpty()) return emptyList()
        val parsedByPath = initialUnits.associateBy { it.source.path.toAbsolutePath().normalize() }
            .toMutableMap()
        val roots = initialUnits.mapNotNull { it.source.path.toAbsolutePath().normalize().parent }.distinct()
        val resolver = ModuleSourceResolver(roots, headerEnvironment.sdkRoot)
        var catalogPrograms = parsedByPath.mapValues { it.value.parsed.syntax }
        var previousDefinitions = emptyMap<Path, Map<String, SyntaxComptimeFunction>>()
        var lastExpanded = emptyList<FrontendUnit>()
        var lastGeneratedImportRange: SourceRange? = null

        fun importKey(import: SyntaxImport): String = listOf(
            import.module,
            import.names.joinToString(","),
            import.alias.orEmpty(),
            import.nameAliases.toSortedMap().entries.joinToString(",") { "${it.key}=${it.value}" }
        ).joinToString("|")

        fun catalogSignature(programs: Map<Path, SyntaxProgram>): List<String> = programs.toSortedMap(
            compareBy(Path::toString)
        ).flatMap { (path, program) ->
            program.declarations.filterIsInstance<SyntaxImport>().map { "${path}: ${importKey(it)}" }
        }.sorted()

        fun definitionSignature(definitions: Map<Path, Map<String, SyntaxComptimeFunction>>): List<String> =
            definitions.toSortedMap(compareBy(Path::toString)).flatMap { (path, functions) ->
                functions.toSortedMap().map { (name, definition) ->
                    "$path|$name|${definition.isPublic}|${definition.category}|" +
                        definition.parameters.joinToString(",") { "${it.kind}:${it.name}" } + "|${definition.template}"
                }
            }

        repeat(MAX_WORKSPACE_CPX_ROUNDS) {
            val currentPaths = parsedByPath.keys.toList()
            val currentUnits = currentPaths.map(parsedByPath::getValue)
            val imported = workspaceComptimeDefinitions(currentUnits, catalogPrograms, previousDefinitions)
            lastExpanded = currentUnits.map { unit ->
                expandFrontend(
                    unit,
                    headerEnvironment,
                    deferImportedCpx = currentUnits.size > 1,
                    importedDefinitions = imported[unit.source.path.toAbsolutePath().normalize()].orEmpty()
                )
            }

            val nextPrograms = currentUnits.associate { unit ->
                val path = unit.source.path.toAbsolutePath().normalize()
                val original = unit.parsed.syntax
                val existingImports = original.declarations.filterIsInstance<SyntaxImport>().map(::importKey).toSet()
                val generatedImports = lastExpanded.first { it.source.path.toAbsolutePath().normalize() == path }
                    .expanded?.program?.declarations.orEmpty().filterIsInstance<SyntaxImport>()
                    .filterNot { importKey(it) in existingImports }
                generatedImports.lastOrNull()?.origin?.primaryRange?.let { lastGeneratedImportRange = it }
                path to original.copy(declarations = original.declarations + generatedImports)
            }

            val overlayPaths = parsedByPath.keys.toSet()
            val newlyParsed = linkedMapOf<Path, ParsedUnit>()
            nextPrograms.forEach { (importer, program) ->
                program.declarations.filterIsInstance<SyntaxImport>().forEach { import ->
                    if (import.module.startsWith("c.") || import.module.startsWith("c/")) return@forEach
                    val dependency = resolver.resolveImport(importer, import.module, overlayPaths) ?: return@forEach
                    val normalized = dependency.toAbsolutePath().normalize()
                    if (normalized in parsedByPath || normalized in newlyParsed) return@forEach
                    val text = runCatching { Files.readString(normalized) }.getOrNull() ?: return@forEach
                    val source = context.sourceRepository.put(normalized, text)
                    newlyParsed[normalized] = parseFrontend(source)
                }
            }

            val nextSignature = catalogSignature(nextPrograms)
            val currentSignature = catalogSignature(catalogPrograms)
            val nextDefinitions = lastExpanded.associate { frontend ->
                frontend.source.path.toAbsolutePath().normalize() to
                    frontend.expanded?.availableComptimeDefinitions.orEmpty()
            }
            val definitionsChanged = definitionSignature(previousDefinitions) != definitionSignature(nextDefinitions)
            previousDefinitions = nextDefinitions
            if (newlyParsed.isEmpty() && nextSignature == currentSignature && !definitionsChanged) return lastExpanded

            parsedByPath.putAll(newlyParsed)
            catalogPrograms = nextPrograms + newlyParsed.mapValues { it.value.parsed.syntax }
        }

        val first = initialUnits.first()
        val diagnostic = Diagnostic(
            DiagnosticSeverity.ERROR,
            "workspace compile-time import expansion did not stabilize within $MAX_WORKSPACE_CPX_ROUNDS rounds",
            lastGeneratedImportRange ?: first.parsed.syntax.range,
            "CPX007"
        )
        return lastExpanded.mapIndexed { index, unit ->
            if (index == 0) unit.copy(closureDiagnostics = unit.closureDiagnostics + diagnostic) else unit
        }
    }

    private fun workspaceComptimeDefinitions(
        parsedUnits: List<ParsedUnit>,
        catalogPrograms: Map<Path, SyntaxProgram> = emptyMap(),
        previousDefinitions: Map<Path, Map<String, SyntaxComptimeFunction>> = emptyMap()
    ): Map<Path, Map<String, ImportedComptimeDefinition>> {
        if (parsedUnits.size < 2) return emptyMap()
        val programs = parsedUnits.associate { unit ->
            val path = unit.source.path.toAbsolutePath().normalize()
            path to (catalogPrograms[path] ?: unit.parsed.syntax)
        }
        val sources = parsedUnits.map { unit ->
            ModuleSource(unit.source, programs.getValue(unit.source.path.toAbsolutePath().normalize()))
        }
        val graph = ModuleGraphBuilder().build(sources)
        val unitsById = parsedUnits.associateBy { unit -> requireNotNull(graph.moduleIdForPath(unit.source.path)) }
        val visibleDefinitions = unitsById.mapValues { (_, unit) ->
            val path = unit.source.path.toAbsolutePath().normalize()
            val visible = unit.parsed.syntax.declarations.filterIsInstance<SyntaxComptimeFunction>()
                .associateByTo(linkedMapOf(), SyntaxComptimeFunction::name)
            previousDefinitions[path].orEmpty().forEach { (name, definition) -> visible[name] = definition }
            visible
        }.toMutableMap()
        val visibleIdentities = unitsById.mapValues { (moduleId, unit) ->
            val path = unit.source.path.toAbsolutePath().normalize()
            val definitions = visibleDefinitions.getValue(moduleId)
            definitions.mapValuesTo(linkedMapOf()) { (_, definition) -> "$path#${definition.name}" }
        }.toMutableMap()
        // Imports are solved as a bounded workspace fixed point, not recursively
        // loaded while expanding a generator. This also handles declaration-only
        // import SCCs: each round publishes only already-declared public callables.
        repeat(graph.nodes.size.coerceAtLeast(1)) {
            var changed = false
            unitsById.forEach { (moduleId, unit) ->
                val visible = visibleDefinitions.getValue(moduleId)
                val identities = visibleIdentities.getValue(moduleId)
                programs.getValue(unit.source.path.toAbsolutePath().normalize()).declarations
                    .filterIsInstance<SyntaxImport>().forEach { import ->
                    val importedId = graph.importBindings[moduleId]?.get(import.module) ?: return@forEach
                    val providerVisible = visibleDefinitions[importedId].orEmpty()
                    val providerIdentities = visibleIdentities[importedId].orEmpty()
                    val publicDefinitions = providerVisible.entries
                        .filter { it.value.isPublic }
                        .distinctBy { providerIdentities[it.key] }
                    fun publish(name: String, definition: SyntaxComptimeFunction, identity: String) {
                        if (name !in visible) {
                            visible[name] = definition
                            identities[name] = identity
                            changed = true
                        }
                    }
                    if (import.names.isEmpty()) {
                        import.alias?.let { alias -> publicDefinitions.forEach { entry ->
                            publish("$alias.${entry.value.name}", entry.value, providerIdentities[entry.key].orEmpty())
                        } }
                    } else {
                        import.names.forEach { name ->
                            val entry = publicDefinitions.singleOrNull { it.value.name == name } ?: return@forEach
                            val localName = import.nameAliases[name] ?: name
                            val identity = providerIdentities[entry.key].orEmpty()
                            publish(localName, entry.value, identity)
                            import.alias?.let { alias -> publish("$alias.$localName", entry.value, identity) }
                        }
                    }
                }
            }
            if (!changed) return@repeat
        }
        return parsedUnits.associate { importer ->
            val importerId = requireNotNull(graph.moduleIdForPath(importer.source.path))
            val candidates = linkedMapOf<String, MutableList<ImportedComptimeDefinition>>()
            programs.getValue(importer.source.path.toAbsolutePath().normalize()).declarations
                .filterIsInstance<SyntaxImport>().forEach { import ->
                val providerId = graph.importBindings[importerId]?.get(import.module) ?: return@forEach
                val lexicalDefinitions = visibleDefinitions[providerId].orEmpty()
                val lexicalIdentities = visibleIdentities[providerId].orEmpty()
                val definitions = lexicalDefinitions.entries.filter { it.value.isPublic }
                    .distinctBy { lexicalIdentities[it.key] }
                fun offer(localName: String, definition: SyntaxComptimeFunction, identity: String) {
                    candidates.getOrPut(localName) { mutableListOf() } +=
                        ImportedComptimeDefinition(definition, lexicalDefinitions, identity, lexicalIdentities)
                }
                if (import.names.isEmpty()) {
                    import.alias?.let { alias -> definitions.distinctBy { lexicalIdentities[it.key] }.forEach { entry ->
                        offer("$alias.${entry.value.name}", entry.value, lexicalIdentities[entry.key].orEmpty())
                    } }
                } else {
                    import.names.forEach { importedName ->
                        val entry = definitions.singleOrNull { it.value.name == importedName } ?: return@forEach
                        val localName = import.nameAliases[importedName] ?: importedName
                        val identity = lexicalIdentities[entry.key].orEmpty()
                        offer(localName, entry.value, identity)
                        import.alias?.let { alias -> offer("$alias.$localName", entry.value, identity) }
                    }
                }
            }
            val unambiguous = candidates.mapNotNull { (name, definitions) ->
                val distinct = definitions.distinctBy { it.declaration.origin to it.declaration.name }
                distinct.singleOrNull()?.let { name to it }
            }.toMap()
            importer.source.path.toAbsolutePath().normalize() to unambiguous
        }
    }

    private fun missingFrontend(source: SourceFile): FrontendUnit {
        val path = source.path
        val diagnostic = Diagnostic(
            DiagnosticSeverity.ERROR,
            "source file does not exist: $path",
            SourceRange(source.id, 0, 0),
            "CLI001"
        )
        val lexed = LexedSource(source, emptyList(), listOf(diagnostic))
        val missingRange = SourceRange(source.id, 0, 0)
        val parsed = Parser.ParsedSource(
            SyntaxProgram(emptyList(), missingRange, Origin.Direct(missingRange)),
            listOf(diagnostic)
        )
        return FrontendUnit(source, lexed, parsed, null, AstProgram(emptyList(), parsed.syntax.origin))
    }

    private fun frontend(source: SourceFile, headerEnvironment: HeaderEnvironment): FrontendUnit =
        expandFrontend(parseFrontend(source), headerEnvironment)

    private fun parseFrontend(source: SourceFile): ParsedUnit {
        val lexed = context.lexer.lex(source)
        return ParsedUnit(source, lexed, Parser(lexed).parse())
    }

    private fun expandFrontend(
        parsedUnit: ParsedUnit,
        headerEnvironment: HeaderEnvironment,
        deferImportedCpx: Boolean = false,
        importedDefinitions: Map<String, ImportedComptimeDefinition> = emptyMap()
    ): FrontendUnit {
        val source = parsedUnit.source
        val lexed = parsedUnit.lexed
        val parsed = parsedUnit.parsed
        val provisionalSemantic = context.analyzerFor(headerEnvironment, provisional = true).analyze(
            context.astBuilder.build(parsed.syntax),
            targetFeatures = activeTargetAbiDescriptor?.features.orEmpty(),
            targetName = activeTargetAbiDescriptor?.targetTriple ?: "selected target"
        )
        val typeResolver = provisionalSemantic.model?.let { model ->
            ComptimeTypeResolver { typeText ->
                model.resolveComptimeTypeIdentity(typeText)?.let { identity ->
                    ComptimeTypeIdentity(identity.typeId, identity.canonicalTypeId, identity.canonicalText)
                }
            }
        }
        val referenceResolver = provisionalSemantic.model?.let { model ->
            ComptimeReferenceResolver { node, arena -> model.resolveComptimeReferences(node, arena) }
        }
        val imports = parsed.syntax.declarations.filterIsInstance<SyntaxImport>()
        val deferredInvocationNames = if (deferImportedCpx) imports.flatMapTo(linkedSetOf()) { import ->
            import.names + import.nameAliases.values
        } else emptySet()
        val deferredInvocationPrefixes = if (deferImportedCpx) imports.mapNotNullTo(linkedSetOf()) { it.alias }
        else emptySet()
        val expanded = context.cpxExpander.expand(
            source,
            parsed.syntax,
            typeResolver,
            referenceResolver,
            provisionalSemantic.model?.let { model ->
                comptimeTypeDescriptors(model, activeTargetAbiDescriptor)
            }.orEmpty(),
            deferredInvocationNames,
            deferredInvocationPrefixes,
            importedDefinitions
        )
        val ast = context.astBuilder.build(expanded.program)
        val closure = context.closureLowerer.lower(ast)
        return FrontendUnit(source, lexed, parsed, expanded, closure.program, closure.diagnostics)
    }

    private data class ForeignInputs(
        val units: List<CSourceUnit>,
        val diagnostics: List<Diagnostic>
    )

    private data class PreparedSource(
        val source: SourceFile,
        val missing: Boolean
    )
}

private fun comptimeTypeDescriptors(
    model: SemanticModel,
    target: TargetAbiDescriptor?
): List<StructuralTypeDescriptor> =
    model.types.distinctBy { it.id }.mapNotNull { type ->
        when (type) {
            is cplus.semantic.PrimitiveType -> StructuralTypeDescriptor(
                type.name,
                "primitive",
                layout = target?.let { comptimeLayout(type, it) },
                typeId = type.id
            )
            is cplus.semantic.StructType -> StructuralTypeDescriptor(
                type.name,
                "struct",
                fields = type.fields.map(::comptimeFieldDescriptor),
                methods = type.methods.map(::comptimeMethodDescriptor),
                layout = target?.let { comptimeLayout(type, it) },
                typeId = type.id
            )
            is cplus.semantic.UnionType -> StructuralTypeDescriptor(
                type.name,
                "union",
                fields = type.fields.map(::comptimeFieldDescriptor),
                layout = target?.let { comptimeLayout(type, it) },
                typeId = type.id
            )
            is cplus.semantic.EnumType -> StructuralTypeDescriptor(
                type.name,
                "enum",
                layout = StructuralLayout("enum", type.values, isSized = true, size = 4, alignment = 4),
                enumValues = type.values,
                typeId = type.id
            )
            is cplus.semantic.AliasType -> StructuralTypeDescriptor(
                type.name,
                "alias",
                aliasTarget = comptimeTypeReference(type.target),
                typeId = type.id
            )
            is cplus.semantic.ForeignType -> StructuralTypeDescriptor(
                type.name,
                "foreign",
                aliasTarget = type.underlyingType?.let(::comptimeTypeReference),
                typeId = type.id
            )
            is cplus.semantic.PointerType -> StructuralTypeDescriptor(type.name, "pointer", typeId = type.id)
            is cplus.semantic.ArrayType -> StructuralTypeDescriptor(type.name, "array", typeId = type.id)
            is cplus.semantic.FunctionType -> StructuralTypeDescriptor(type.name, "function", typeId = type.id)
            is cplus.semantic.UnknownType -> null
        }
    }

private fun comptimeLayout(type: cplus.semantic.CType, target: TargetAbiDescriptor): cplus.comptime.StructuralLayout {
    if (hasByValueCycle(type)) return cplus.comptime.StructuralLayout(
        representation = when (type) {
            is cplus.semantic.StructType -> "struct"
            is cplus.semantic.UnionType -> "union"
            else -> "object"
        },
        fieldOrder = emptyList(),
        isSized = false
    )
    val layout = AbiLayoutEngine(target).layout(type)
    return cplus.comptime.StructuralLayout(
        representation = when (type) {
            is cplus.semantic.StructType -> "struct"
            is cplus.semantic.UnionType -> "union"
            else -> "object"
        },
        fieldOrder = layout.fields.map { it.name },
        isSized = layout.size > 0,
        size = layout.size.toLong(),
        alignment = layout.alignment.toLong()
    )
}

private fun hasByValueCycle(type: cplus.semantic.CType): Boolean =
    hasByValueCycle(type, linkedSetOf())

private fun hasByValueCycle(
    type: cplus.semantic.CType,
    active: MutableSet<TypeId>
): Boolean = when (type) {
    is cplus.semantic.PointerType,
    is cplus.semantic.PrimitiveType,
    is cplus.semantic.EnumType,
    is cplus.semantic.FunctionType,
    is cplus.semantic.ForeignType,
    is cplus.semantic.UnknownType -> false
    is cplus.semantic.ArrayType -> hasByValueCycle(type.element, active)
    is cplus.semantic.AliasType -> hasByValueCycle(type.target, active)
    is cplus.semantic.StructType -> {
        if (!active.add(type.id)) true
        else type.fields.any { hasByValueCycle(it.symbol.type, active) }.also { active.remove(type.id) }
    }
    is cplus.semantic.UnionType -> {
        if (!active.add(type.id)) true
        else type.fields.any { hasByValueCycle(it.symbol.type, active) }.also { active.remove(type.id) }
    }
}

private fun comptimeFieldDescriptor(field: FieldSymbol): StructuralFieldDescriptor {
    val reference = comptimeTypeReference(field.symbol.type)
    return StructuralFieldDescriptor(
        field.symbol.name,
        reference.name,
        reference.pointerDepth,
        typeReference = reference
    )
}

private fun comptimeMethodDescriptor(method: MethodSymbol): StructuralMethodDescriptor =
    StructuralMethodDescriptor(
        method.symbol.name,
        comptimeTypeReference(method.returnType).name,
        method.parameters.map { comptimeTypeReference(it.type).name },
        method.receiverKind == ReceiverKind.STATIC,
        comptimeTypeReference(method.returnType),
        method.parameters.map { comptimeTypeReference(it.type) }
    )

private fun comptimeTypeReference(type: cplus.semantic.CType): StructuralTypeReference {
    var current = type
    var pointers = 0
    while (current is cplus.semantic.PointerType) {
        pointers++
        current = current.pointee
    }
    val kind = when (current) {
        is cplus.semantic.StructType -> "struct"
        is cplus.semantic.UnionType -> "union"
        is cplus.semantic.EnumType -> "enum"
        is cplus.semantic.PrimitiveType -> "primitive"
        is cplus.semantic.AliasType -> "alias"
        is cplus.semantic.ForeignType -> "foreign"
        is cplus.semantic.ArrayType -> "array"
        is cplus.semantic.FunctionType -> "function"
        is cplus.semantic.UnknownType -> "unknown"
        is cplus.semantic.PointerType -> "pointer"
    }
    return StructuralTypeReference(current.name, pointers, kind)
}
