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
    val options: CompilerOptions = CompilerOptions()
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
    val additionalDiagnostics: List<Diagnostic> = emptyList()
)

data class CompileResult(
    val diagnostics: List<Diagnostic>,
    val generatedUnits: List<GeneratedCUnit>,
    val semanticModel: SemanticModel?,
    val artifacts: List<CompilationArtifacts>,
    val moduleGraph: ModuleGraph? = null
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
    fun compile(request: CompileRequest): CompileResult {
        if (request.sources.size <= 1) {
            val artifacts = request.sources.map { compileOne(it, request.options) }
            return resultOf(artifacts)
        }
        return compileWorkspace(request)
    }

    fun compileText(path: Path, text: String, options: CompilerOptions = CompilerOptions()): CompileResult {
        val source = context.sourceRepository.put(path, text)
        return resultOf(listOf(compileFrontend(frontend(source), options)))
    }

    private fun compileOne(path: Path, options: CompilerOptions): CompilationArtifacts {
        return compileFrontend(frontend(path), options)
    }

    private fun compileFrontend(frontend: FrontendUnit, options: CompilerOptions): CompilationArtifacts {
        val source = frontend.source
        val lexed = frontend.lexed
        val parsed = frontend.parsed
        val expanded = frontend.expanded
        val ast = frontend.ast
        val semantic = context.semanticAnalyzer.analyze(ast)
        if (!semantic.isSuccessful) {
            return CompilationArtifacts(source, lexed, parsed, expanded, ast, semantic, null, null)
        }
        val model = semantic.model ?: return CompilationArtifacts(source, lexed, parsed, expanded, ast, semantic, null, null)
        val lowered = context.cLowererFactory(model).lower(ast)
        if (lowered.diagnostics.any { it.severity == DiagnosticSeverity.ERROR }) {
            return CompilationArtifacts(source, lexed, parsed, expanded, ast, semantic, lowered, null)
        }
        val generated = context.cEmitter.emit(lowered.unit)
        return CompilationArtifacts(source, lexed, parsed, expanded, ast, semantic, lowered, generated)
    }

    private fun compileWorkspace(request: CompileRequest): CompileResult {
        val units = request.sources.map(::frontend)
        val moduleGraph = ModuleGraphBuilder().build(
            units.map { ModuleSource(it.source, it.expanded?.program ?: it.parsed.syntax) }
        )
        val first = units.firstOrNull()
        if (first == null) return CompileResult(emptyList(), emptyList(), null, emptyList(), moduleGraph)

        val mergedOrigin = first.ast.origin
        val mergedAst = AstProgram(
            units.flatMap { it.ast.declarations },
            mergedOrigin,
            units.map { unit ->
                AstModule(unit.source.path.fileName.toString().substringBeforeLast('.'), unit.ast.declarations)
            }
        )
        val semantic = context.semanticAnalyzer.analyze(mergedAst, moduleGraph.moduleNames)
        val additionalDiagnostics = units.drop(1).flatMap { it.diagnostics() }
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
                moduleGraph
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
                moduleGraph
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
                moduleGraph
            )
        }
        val generated = context.cEmitter.emit(lowered.unit)
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
                    additionalDiagnostics
                )
            ),
            moduleGraph
        )
    }

    private fun resultOf(artifacts: List<CompilationArtifacts>, moduleGraph: ModuleGraph? = null): CompileResult = CompileResult(
        diagnostics = artifacts.flatMap { it.allDiagnostics() },
        generatedUnits = artifacts.mapNotNull { it.generated },
        semanticModel = artifacts.singleOrNull()?.semantic?.model,
        artifacts = artifacts,
        moduleGraph = moduleGraph
    )

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
}
