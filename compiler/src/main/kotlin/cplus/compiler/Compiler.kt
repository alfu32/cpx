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
    val generated: GeneratedCUnit?
)

data class CompileResult(
    val diagnostics: List<Diagnostic>,
    val generatedUnits: List<GeneratedCUnit>,
    val semanticModel: SemanticModel?,
    val artifacts: List<CompilationArtifacts>
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
        val artifacts = request.sources.map { compileOne(it, request.options) }
        return resultOf(artifacts)
    }

    fun compileText(path: Path, text: String, options: CompilerOptions = CompilerOptions()): CompileResult {
        val source = context.sourceRepository.put(path, text)
        return resultOf(listOf(compileSource(source, options)))
    }

    private fun compileOne(path: Path, options: CompilerOptions): CompilationArtifacts {
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
            return CompilationArtifacts(source, lexed, parsed, null, AstProgram(emptyList(), parsed.syntax.origin), SemanticResult(null, listOf(diagnostic)), null, null)
        }
        val source = context.sourceRepository.put(path, path.readText())
        return compileSource(source, options)
    }

    private fun compileSource(source: SourceFile, options: CompilerOptions): CompilationArtifacts {
        val lexed = context.lexer.lex(source)
        val parsed = Parser(lexed).parse()
        val expanded = context.cpxExpander.expand(source, parsed.syntax)
        val ast = context.astBuilder.build(expanded.program)
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

    private fun resultOf(artifacts: List<CompilationArtifacts>): CompileResult = CompileResult(
        diagnostics = artifacts.flatMap { it.allDiagnostics() },
        generatedUnits = artifacts.mapNotNull { it.generated },
        semanticModel = artifacts.singleOrNull()?.semantic?.model,
        artifacts = artifacts
    )

    private fun CompilationArtifacts.allDiagnostics(): List<Diagnostic> = buildList {
        addAll(lexed.diagnostics)
        addAll(parsed.diagnostics.filterNot { it in lexed.diagnostics })
        addAll(expanded?.diagnostics.orEmpty())
        addAll(semantic.diagnostics)
        addAll(lowered?.diagnostics.orEmpty())
    }
}
