package cplus.compiler

import cplus.backend.*
import cplus.comptime.ComptimeTypeIdentity
import cplus.comptime.ComptimeReferenceResolver
import cplus.comptime.ComptimeTargetInfo
import cplus.comptime.ComptimeTypeResolver
import cplus.comptime.CpxExpansionResult
import cplus.comptime.CpxExpander
import cplus.comptime.SpecializationKey
import cplus.core.*
import cplus.semantic.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import kotlin.io.path.readText

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

data class CompileRequest(
    val sources: List<Path>,
    val target: TargetInfo = TargetInfo(),
    val options: CompilerOptions = CompilerOptions(),
    val cSources: List<Path> = emptyList(),
    val cLibraries: List<String> = emptyList(),
    val cIncludeDirectories: List<Path> = emptyList(),
    val sdkManifest: Path = SdkManifestLocator.defaultManifestPath(),
    val externalSysroot: Path? = null
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
    val target: TargetInfo = TargetInfo(),
    val cpxExpander: CpxExpander = CpxExpander(lexer, target = BuildProfileValidator.toComptimeTarget(target)),
    val closureLowerer: AstClosureLowerer = AstClosureLowerer(),
    val cLowererFactory: (SemanticModel) -> CLowerer = ::CLowerer,
    val cEmitter: CEmitter = CEmitter()
)

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
        val intrinsics = IntrinsicRegistry.load(sdkResolution.resolution.layout.intrinsicCatalogue)
        if (!intrinsics.isSuccessful) return sdkFailure(intrinsics.diagnostics)
        val metadata = SdkMetadataCache.loadOrBuild(sdkResolution.resolution!!)
        if (!metadata.isSuccessful) return sdkFailure(metadata.diagnostics)
        val resolvedSdk = sdkResolution.resolution.copy(metadata = metadata.metadata, targetDescriptor = descriptor.descriptor, intrinsics = intrinsics.definitions)
        context.cpxExpander.configureTarget(BuildProfileValidator.toComptimeTarget(request.target, descriptor.descriptor))
        val foreignInputs = loadForeignSources(request.cSources)
        val cLinkDependencies = linkDependencies(request.cLibraries)
        val linkDiagnostics = validateLinkDependencies(cLinkDependencies)
        if (request.sources.size <= 1) {
            val artifacts = request.sources.map { compileOne(it, request.options, foreignInputs.units) }
            return resultOf(
                artifacts,
                cSourceDependencies = dependencies(request.cSources),
                additionalDiagnostics = foreignInputs.diagnostics + linkDiagnostics,
                cLinkDependencies = cLinkDependencies,
                sdkResolution = resolvedSdk
            )
        }
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
        val intrinsics = IntrinsicRegistry.load(sdkResolution.resolution.layout.intrinsicCatalogue)
        if (!intrinsics.isSuccessful) return IncrementalPipeline(sdkFailure(intrinsics.diagnostics), emptyMap())
        val metadata = SdkMetadataCache.loadOrBuild(sdkResolution.resolution!!)
        if (!metadata.isSuccessful) return IncrementalPipeline(sdkFailure(metadata.diagnostics), emptyMap())
        val resolvedSdk = sdkResolution.resolution.copy(metadata = metadata.metadata, targetDescriptor = descriptor.descriptor, intrinsics = intrinsics.definitions)
        context.cpxExpander.configureTarget(BuildProfileValidator.toComptimeTarget(request.target, descriptor.descriptor))
        val foreignInputs = loadForeignSources(request.cSources)
        val cLinkDependencies = linkDependencies(request.cLibraries)
        val linkDiagnostics = validateLinkDependencies(cLinkDependencies)
        val entries = linkedMapOf<Path, FrontendCacheEntry>()
        val paths = request.sources.map { it.toAbsolutePath().normalize() }
        val pathsToCompute = paths.filter { path ->
            val fingerprint = fingerprints.getValue(path)
            val reusable = cached[path]
            path in recompute || reusable?.fingerprint != fingerprint
        }
        val computed = prepareFrontends(pathsToCompute, request.options.parallelism)
        val units = paths.map { path ->
            val fingerprint = fingerprints.getValue(path)
            val reusable = cached[path]
            val entry = if (path !in recompute && reusable?.fingerprint == fingerprint) {
                reusable
            } else {
                FrontendCacheEntry(fingerprint, computed.getValue(path))
            }
            entries[path] = entry
            entry.frontend
        }
        val result = if (request.sources.size <= 1) {
            val artifacts = units.map { compileFrontend(it, request.options, foreignInputs.units) }
            resultOf(
                artifacts,
                cSourceDependencies = dependencies(request.cSources),
                additionalDiagnostics = foreignInputs.diagnostics + linkDiagnostics,
                cLinkDependencies = cLinkDependencies,
                sdkResolution = resolvedSdk
            )
        } else {
            compileWorkspace(
                request,
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
        target: TargetInfo = TargetInfo()
    ): CompileResult {
        return compileTextWorkspace(listOf(TextSource(path, text)), options, sdkManifest, target)
    }

    fun compileTextWorkspace(
        sources: List<TextSource>,
        options: CompilerOptions = CompilerOptions(),
        sdkManifest: Path = SdkManifestLocator.defaultManifestPath(),
        target: TargetInfo = TargetInfo()
    ): CompileResult {
        val sdk = SdkManifestLoader.load(sdkManifest)
        if (!sdk.isSuccessful) return sdkFailure(sdk.diagnostics)
        val profileDiagnostics = BuildProfileValidator.validate(target.buildProfile, sdk.manifest!!)
        if (profileDiagnostics.isNotEmpty()) return sdkFailure(profileDiagnostics)
        val sdkResolution = SdkResolver.resolve(sdk.manifest, target, null)
        if (!sdkResolution.isSuccessful) return sdkFailure(sdkResolution.diagnostics)
        val descriptor = TargetRegistry.load(sdkResolution.resolution!!)
        if (!descriptor.isSuccessful) return sdkFailure(descriptor.diagnostics)
        val intrinsics = IntrinsicRegistry.load(sdkResolution.resolution.layout.intrinsicCatalogue)
        if (!intrinsics.isSuccessful) return sdkFailure(intrinsics.diagnostics)
        val metadata = SdkMetadataCache.loadOrBuild(sdkResolution.resolution!!)
        if (!metadata.isSuccessful) return sdkFailure(metadata.diagnostics)
        val resolvedSdk = sdkResolution.resolution.copy(metadata = metadata.metadata, targetDescriptor = descriptor.descriptor, intrinsics = intrinsics.definitions)
        context.cpxExpander.configureTarget(BuildProfileValidator.toComptimeTarget(target, descriptor.descriptor))
        val sourceFiles = sources
            .distinctBy { it.path.toAbsolutePath().normalize() }
            .map { source -> context.sourceRepository.put(source.path, source.text) }
        val frontends = sourceFiles.map(::frontend)
        if (frontends.size <= 1) {
            return resultOf(frontends.map { compileFrontend(it, options) }, sdkResolution = resolvedSdk)
        }
        val request = CompileRequest(sourceFiles.map { it.path }, target = target, options = options, sdkManifest = sdkManifest)
        return compileWorkspace(
            request,
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

    private fun compileOne(path: Path, options: CompilerOptions, foreignSources: List<CSourceUnit>): CompilationArtifacts {
        return compileFrontend(frontend(path), options, foreignSources)
    }

    private fun compileFrontend(
        frontend: FrontendUnit,
        options: CompilerOptions,
        foreignSources: List<CSourceUnit> = emptyList()
    ): CompilationArtifacts {
        val source = frontend.source
        val lexed = frontend.lexed
        val parsed = frontend.parsed
        val expanded = frontend.expanded
        val ast = frontend.ast
        val semantic = context.semanticAnalyzer.analyze(ast, foreignSources = foreignSources)
        if (!semantic.isSuccessful) {
            return CompilationArtifacts(source, lexed, parsed, expanded, ast, semantic, null, null, frontend.closureDiagnostics)
        }
        if (frontend.diagnostics().any { it.severity == DiagnosticSeverity.ERROR }) {
            return CompilationArtifacts(source, lexed, parsed, expanded, ast, semantic, null, null, frontend.closureDiagnostics)
        }
        val model = semantic.model ?: return CompilationArtifacts(source, lexed, parsed, expanded, ast, semantic, null, null, frontend.closureDiagnostics)
        val backend = BackendProcessingPipeline(context.cLowererFactory(model), context.cEmitter).run(ast, model)
        return CompilationArtifacts(
            source,
            lexed,
            parsed,
            expanded,
            ast,
            semantic,
            backend.lowered,
            backend.generated,
            frontend.closureDiagnostics,
            backend.header
        )
    }

    private fun compileWorkspace(
        request: CompileRequest,
        foreignInputs: ForeignInputs,
        cLinkDependencies: List<CLinkDependency>,
        linkDiagnostics: List<Diagnostic>,
        units: List<FrontendUnit> = request.sources.map { frontend(it) },
        sdkResolution: SdkResolution? = null
    ): CompileResult {
        val moduleGraph = ModuleGraphBuilder().build(
            units.map { ModuleSource(it.source, it.expanded?.program ?: it.parsed.syntax) }
        )
        val first = units.firstOrNull()
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
            units.flatMap { it.ast.declarations },
            mergedOrigin,
            units.map { unit ->
                AstModule(unit.source.path.fileName.toString().substringBeforeLast('.'), unit.ast.declarations)
            }
        )
        val semantic = context.semanticAnalyzer.analyze(mergedAst, moduleGraph.moduleNames, foreignInputs.units)
        val additionalDiagnostics = first.closureDiagnostics + units.drop(1).flatMap { it.diagnostics() } +
            unresolvedImportCycleDiagnostics(moduleGraph, semantic.diagnostics) +
            foreignInputs.diagnostics +
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
        if (units.any { unit -> unit.diagnostics().any { it.severity == DiagnosticSeverity.ERROR } }) {
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
        val backend = BackendProcessingPipeline(context.cLowererFactory(model), context.cEmitter).run(mergedAst, model)
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

    private fun frontend(path: Path): FrontendUnit {
        if (!Files.exists(path)) {
            val source = context.sourceRepository.put(path, "")
            return missingFrontend(source)
        }
        val source = context.sourceRepository.put(path, path.readText())
        return frontend(source)
    }

    private fun prepareFrontends(paths: List<Path>, parallelism: Int): Map<Path, FrontendUnit> {
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
        fun compute(preparedSource: PreparedSource): FrontendUnit = if (preparedSource.missing) {
            missingFrontend(preparedSource.source)
        } else {
            frontend(preparedSource.source)
        }
        if (parallelism == 1 || paths.size == 1) {
            return paths.associateWith { path -> compute(prepared.getValue(path)) }
        }

        val workers = Executors.newFixedThreadPool(parallelism.coerceAtMost(paths.size))
        return try {
            val futures = paths.map { path ->
                path to workers.submit<FrontendUnit> { compute(prepared.getValue(path)) }
            }
            futures.associate { (path, future) -> path to future.get() }
        } finally {
            workers.shutdown()
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

    private fun frontend(source: SourceFile): FrontendUnit {
        val lexed = context.lexer.lex(source)
        val parsed = Parser(lexed).parse()
        val provisionalSemantic = SemanticAnalyzer().analyze(context.astBuilder.build(parsed.syntax))
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
        val expanded = context.cpxExpander.expand(source, parsed.syntax, typeResolver, referenceResolver)
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
