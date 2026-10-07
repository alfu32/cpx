package cplus.compiler

import cplus.backend.*
import cplus.comptime.CpxExpansionResult
import cplus.comptime.CpxExpander
import cplus.core.*
import cplus.semantic.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText

data class TargetInfo(
    val cDialect: String = "c17"
)

data class CompilerOptions(
    val emitSourceMap: Boolean = true
)

data class CompileRequest(
    val sources: List<Path>,
    val target: TargetInfo = TargetInfo(),
    val options: CompilerOptions = CompilerOptions(),
    val cSources: List<Path> = emptyList()
)

data class CSourceDependency(val path: Path)

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
)

data class CompileResult(
    val diagnostics: List<Diagnostic>,
    val generatedUnits: List<GeneratedCUnit>,
    val semanticModel: SemanticModel?,
    val artifacts: List<CompilationArtifacts>,
    val moduleGraph: ModuleGraph? = null,
    val cSourceDependencies: List<CSourceDependency> = emptyList(),
    val generatedHeaders: List<GeneratedCUnit> = emptyList()
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
    val cpxExpander: CpxExpander = CpxExpander(lexer),
    val cLowererFactory: (SemanticModel) -> CLowerer = ::CLowerer,
    val cEmitter: CEmitter = CEmitter()
)

class CPlusCompiler(
    private val context: CompilerContext = CompilerContext()
) {
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
        val foreignInputs = loadForeignSources(request.cSources)
        if (request.sources.size <= 1) {
            val artifacts = request.sources.map { compileOne(it, request.options, foreignInputs.units) }
            return resultOf(
                artifacts,
                cSourceDependencies = dependencies(request.cSources),
                additionalDiagnostics = foreignInputs.diagnostics
            )
        }
        return compileWorkspace(request, foreignInputs)
    }

    fun compileText(path: Path, text: String, options: CompilerOptions = CompilerOptions()): CompileResult {
        val source = context.sourceRepository.put(path, text)
        return resultOf(listOf(compileFrontend(frontend(source), options)))
    }

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
            return CompilationArtifacts(source, lexed, parsed, expanded, ast, semantic, null, null)
        }
        val model = semantic.model ?: return CompilationArtifacts(source, lexed, parsed, expanded, ast, semantic, null, null)
        val lowered = context.cLowererFactory(model).lower(ast)
        if (lowered.diagnostics.any { it.severity == DiagnosticSeverity.ERROR }) {
            return CompilationArtifacts(source, lexed, parsed, expanded, ast, semantic, lowered, null)
        }
        val generated = context.cEmitter.emit(lowered.unit)
        val header = CHeaderGenerator().generate(lowered.unit)
        return CompilationArtifacts(source, lexed, parsed, expanded, ast, semantic, lowered, generated, header = header)
    }

    private fun compileWorkspace(request: CompileRequest, foreignInputs: ForeignInputs): CompileResult {
        val units = request.sources.map(::frontend)
        val moduleGraph = ModuleGraphBuilder().build(
            units.map { ModuleSource(it.source, it.expanded?.program ?: it.parsed.syntax) }
        )
        val first = units.firstOrNull()
        val cSourceDependencies = dependencies(request.cSources)
        if (first == null) return CompileResult(emptyList(), emptyList(), null, emptyList(), moduleGraph, cSourceDependencies)

        val mergedOrigin = first.ast.origin
        val mergedAst = AstProgram(
            units.flatMap { it.ast.declarations },
            mergedOrigin,
            units.map { unit ->
                AstModule(unit.source.path.fileName.toString().substringBeforeLast('.'), unit.ast.declarations)
            }
        )
        val semantic = context.semanticAnalyzer.analyze(mergedAst, moduleGraph.moduleNames, foreignInputs.units)
        val additionalDiagnostics = units.drop(1).flatMap { it.diagnostics() } +
            unresolvedImportCycleDiagnostics(moduleGraph, semantic.diagnostics) +
            foreignInputs.diagnostics
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
                cSourceDependencies
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
                cSourceDependencies
            )
        }
        val lowered = context.cLowererFactory(model).lower(mergedAst)
        if (lowered.diagnostics.any { it.severity == DiagnosticSeverity.ERROR }) {
            return resultOf(
                listOf(
                    CompilationArtifacts(
                        base.source,
                        base.lexed,
                        base.parsed,
                        base.expanded,
                        mergedAst,
                        semantic,
                        lowered,
                        null,
                        additionalDiagnostics
                    )
                ),
                moduleGraph,
                cSourceDependencies
            )
        }
        val generated = context.cEmitter.emit(lowered.unit)
        val header = CHeaderGenerator().generate(lowered.unit)
        return resultOf(
            listOf(
                CompilationArtifacts(
                    base.source,
                    base.lexed,
                    base.parsed,
                    base.expanded,
                    mergedAst,
                    semantic,
                    lowered,
                    generated,
                    additionalDiagnostics,
                    header
                )
            ),
            moduleGraph,
            cSourceDependencies
        )
    }

    private fun resultOf(
        artifacts: List<CompilationArtifacts>,
        moduleGraph: ModuleGraph? = null,
        cSourceDependencies: List<CSourceDependency> = emptyList(),
        additionalDiagnostics: List<Diagnostic> = emptyList()
    ): CompileResult = CompileResult(
        diagnostics = artifacts.flatMap { it.allDiagnostics() } + additionalDiagnostics,
        generatedUnits = artifacts.mapNotNull { it.generated },
        semanticModel = artifacts.singleOrNull()?.semantic?.model,
        artifacts = artifacts,
        moduleGraph = moduleGraph,
        cSourceDependencies = cSourceDependencies,
        generatedHeaders = artifacts.mapNotNull { it.header }
    )

    private fun dependencies(paths: List<Path>): List<CSourceDependency> = paths
        .map { it.toAbsolutePath().normalize() }
        .distinct()
        .map(::CSourceDependency)

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

    private data class FrontendUnit(
        val source: SourceFile,
        val lexed: LexedSource,
        val parsed: Parser.ParsedSource,
        val expanded: CpxExpansionResult?,
        val ast: AstProgram
    ) {
        fun diagnostics(): List<Diagnostic> = buildList {
            addAll(lexed.diagnostics)
            addAll(parsed.diagnostics.filterNot { it in lexed.diagnostics })
            addAll(expanded?.diagnostics.orEmpty())
        }
    }

    private fun frontend(path: Path): FrontendUnit {
        if (!Files.exists(path)) {
            val source = context.sourceRepository.put(path, "")
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
        val source = context.sourceRepository.put(path, path.readText())
        return frontend(source)
    }

    private fun frontend(source: SourceFile): FrontendUnit {
        val lexed = context.lexer.lex(source)
        val parsed = Parser(lexed).parse()
        val expanded = context.cpxExpander.expand(source, parsed.syntax)
        val ast = context.astBuilder.build(expanded.program)
        return FrontendUnit(source, lexed, parsed, expanded, ast)
    }

    private data class ForeignInputs(
        val units: List<CSourceUnit>,
        val diagnostics: List<Diagnostic>
    )
}
