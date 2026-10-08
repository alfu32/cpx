package cplus.cli

import cplus.compiler.CompileResult
import cplus.core.LineIndex
import cplus.core.Origin
import cplus.core.SourceRange
import cplus.core.Lexer
import cplus.core.SourceFile
import cplus.core.SourceFileId
import cplus.core.TokenKind
import cplus.core.Token
import cplus.core.AstIdentifier
import cplus.semantic.AliasType
import cplus.semantic.ArrayType
import cplus.semantic.CType
import cplus.semantic.FunctionSymbol
import cplus.semantic.PointerType
import cplus.semantic.ReferenceKind
import cplus.semantic.StructType
import cplus.semantic.Symbol
import cplus.semantic.SymbolKind
import cplus.semantic.UnionType
import cplus.semantic.MethodSymbol
import java.nio.file.Path

internal data class CompletionItem(
    val label: String,
    val kind: Int,
    val detail: String
)

internal data class HoverInfo(
    val markdown: String,
    val range: SourceRange
)

internal data class NavigationInfo(
    val definition: SourceRange?,
    val references: List<SourceRange>
)

internal data class SignatureParameterInfo(
    val label: String,
    val documentation: String? = null
)

internal data class SignatureInfo(
    val label: String,
    val parameters: List<SignatureParameterInfo>,
    val activeParameter: Int
)

internal object LspLanguageService {
    fun completion(result: CompileResult, text: String, position: LspPosition): List<CompletionItem> {
        val model = result.semanticModel ?: return emptyList()
        val offset = offsetAt(text, position) ?: return emptyList()
        val before = text.substring(0, offset)
        val member = Regex("([A-Za-z_][A-Za-z0-9_]*)\\s*(?:\\.|->)\\s*([A-Za-z_]\\w*)?$").find(before)
        val prefix = member?.groupValues?.getOrNull(2).orEmpty()
        val candidates = if (member != null) {
            memberCandidates(model, member.groupValues[1], prefix)
        } else {
            val identifier = Regex("[A-Za-z_]\\w*$").find(before)?.value.orEmpty()
            model.symbols
                .asSequence()
                .filter { it.name.startsWith(identifier) }
                .map { CompletionItem(it.name, completionKind(it.kind), it.type.name) }
                .toList()
        }
        return candidates.distinctBy { it.label to it.kind }.sortedBy { it.label }
    }

    fun hover(result: CompileResult, text: String, position: LspPosition, sourcePath: Path? = null): HoverInfo? {
        val model = result.semanticModel ?: return null
        val artifact = artifactFor(result, sourcePath) ?: return null
        val offset = offsetAt(text, position) ?: return null
        val token = artifact.lexed.tokens.firstOrNull {
            it.range.startOffset <= offset && offset < it.range.endOffset
        } ?: return null
        val symbol = symbolAt(model, token) ?: return null
        val kind = symbol.kind.name.lowercase().replace('_', ' ')
        return HoverInfo(
            "```cplus\n$kind ${symbol.name}: ${symbol.type.name}\n```",
            token.range
        )
    }

    fun navigation(
        result: CompileResult,
        text: String,
        position: LspPosition,
        includeDeclaration: Boolean = true,
        sourcePath: Path? = null,
        sourcePathFor: (SourceFileId) -> Path? = { null },
        sourceTextFor: (SourceFileId) -> String? = { null }
    ): NavigationInfo? {
        val model = result.semanticModel ?: return null
        val artifact = artifactFor(result, sourcePath) ?: return null
        val offset = offsetAt(text, position) ?: return null
        val token = artifact.lexed.tokens.firstOrNull {
            it.range.startOffset <= offset && offset < it.range.endOffset
        } ?: return null
        val symbol = symbolAt(model, token) ?: return null
        val declaration = model.symbols.firstOrNull { it.id == symbol.id }
        val definition = declaration?.let { symbolDeclarationRange(result, it, sourcePathFor, sourceTextFor) }
        val indexedReferences = model.referenceIndex.referencesTo(symbol.id)
            .mapNotNull { it.origin.primaryRange }
        val references = (if (includeDeclaration) listOfNotNull(definition) else emptyList())
            .plus(indexedReferences)
            .distinct()
            .sortedWith(compareBy<SourceRange>({ it.file.value }, { it.startOffset }, { it.endOffset }))
        return NavigationInfo(definition, references)
    }

    private fun symbolDeclarationRange(
        result: CompileResult,
        symbol: Symbol,
        sourcePathFor: (SourceFileId) -> Path?,
        sourceTextFor: (SourceFileId) -> String?
    ): SourceRange? {
        val declaration = symbol.origin.primaryRange ?: return null
        val declarationPath = sourcePathFor(declaration.file)?.toAbsolutePath()?.normalize()
        val text = sourceTextFor(declaration.file)
        val tokens = if (declarationPath != null && text != null) {
            Lexer().lex(SourceFile(declaration.file, declarationPath, text, 0)).tokens
        } else {
            result.artifacts.firstOrNull { it.source.id == declaration.file }?.lexed?.tokens.orEmpty()
        }
        val token = tokens.firstOrNull {
                it.kind == TokenKind.IDENTIFIER && it.lexeme == symbol.name &&
                    it.range.startOffset >= declaration.startOffset &&
                    it.range.endOffset <= declaration.endOffset
            }
        return token?.range ?: declaration
    }

    fun signatureHelp(result: CompileResult, text: String, position: LspPosition, sourcePath: Path? = null): SignatureInfo? {
        val model = result.semanticModel ?: return null
        val artifact = artifactFor(result, sourcePath) ?: return null
        val offset = offsetAt(text, position) ?: return null
        val call = model.nodeIds.values
            .filterIsInstance<cplus.core.AstCall>()
            .firstOrNull { contains(it.origin, SourceRange(artifact.source.id, offset, offset)) }
            ?: return null
        val function = when (val callee = call.callee) {
            is AstIdentifier -> model.resolveFunction(callee.name)
            is cplus.core.AstMemberAccess -> {
                val receiverType = model.expressionTypes[callee.receiver]
                val owner = aggregateType(receiverType ?: return null)
                (owner as? StructType)?.methods?.firstOrNull { it.symbol.name == callee.member }?.let { method ->
                    FunctionSymbol(method.symbol, method.returnType, method.parameters, signature = method.signature)
                }
            }
            else -> null
        } ?: return null
        val parameters = function.parameters.map { parameter ->
            SignatureParameterInfo("${parameter.type.name} ${parameter.name}")
        }
        val activeParameter = call.arguments.indexOfFirst { argument -> contains(argument.origin, SourceRange(artifact.source.id, offset, offset)) }
            .takeIf { it >= 0 }
            ?: call.arguments.size.coerceAtMost(parameters.lastIndex.coerceAtLeast(0))
        return SignatureInfo(
            "${function.symbol.name}(${parameters.joinToString(", ") { it.label }}): ${function.returnType.name}",
            parameters,
            activeParameter
        )
    }

    private fun artifactFor(result: CompileResult, sourcePath: Path?) =
        if (sourcePath == null) result.artifacts.firstOrNull()
        else result.artifacts.firstOrNull { it.source.path.toAbsolutePath().normalize() == sourcePath.toAbsolutePath().normalize() }

    private fun memberCandidates(
        model: cplus.semantic.SemanticModel,
        receiverName: String,
        prefix: String
    ): List<CompletionItem> {
        val type = model.structs[receiverName]
            ?: model.symbolNamed(receiverName)?.type
                ?.let { aggregateType(it) }
            ?: model.expressionTypes.entries
                .firstOrNull { (expression, _) -> expression is AstIdentifier && expression.name == receiverName }
                ?.value
                ?.let(::aggregateType)
        val aggregate = type ?: return emptyList()
        val fields = when (aggregate) {
            is StructType -> aggregate.fields.map { CompletionItem(it.symbol.name, 5, it.symbol.type.name) }
            is UnionType -> aggregate.fields.map { CompletionItem(it.symbol.name, 5, it.symbol.type.name) }
            else -> emptyList()
        }
        val methods = (aggregate as? StructType)?.methods.orEmpty()
            .map { method -> CompletionItem(method.symbol.name, 2, method.signature.name) }
        return (fields + methods).filter { it.label.startsWith(prefix) }
    }

    private fun aggregateType(type: CType): CType? = when (type) {
        is AliasType -> aggregateType(type.target)
        is PointerType -> aggregateType(type.pointee)
        is StructType, is UnionType -> type
        is ArrayType -> aggregateType(type.element)
        else -> null
    }

    private fun symbolAt(model: cplus.semantic.SemanticModel, token: Token): Symbol? {
        val reference = model.referenceIndex.all().firstOrNull { it.origin.primaryRange == token.range }
        return reference?.let { ref -> model.symbols.firstOrNull { it.id == ref.symbol } }
            ?: model.symbols
                .asSequence()
                .filter { it.name == token.lexeme && contains(it.origin, token.range) }
                .minByOrNull { it.origin.primaryRange?.let { range -> range.endOffset - range.startOffset } ?: Int.MAX_VALUE }
    }

    private fun contains(origin: Origin, range: SourceRange): Boolean {
        val primary = origin.primaryRange ?: return false
        return primary.file == range.file &&
            primary.startOffset <= range.startOffset &&
            primary.endOffset >= range.endOffset
    }

    private fun offsetAt(text: String, position: LspPosition): Int? {
        if (position.line < 0 || position.character < 0) return null
        val offset = runCatching {
            LineIndex.from(text).offsetAt(cplus.core.SourcePosition(position.line + 1, position.character + 1))
        }.getOrNull() ?: return null
        return offset.takeIf { it in 0..text.length }
    }

    private fun completionKind(kind: SymbolKind): Int = when (kind) {
        SymbolKind.STRUCT, SymbolKind.UNION, SymbolKind.ENUM, SymbolKind.ALIAS, SymbolKind.FOREIGN_TYPE -> 7
        SymbolKind.FUNCTION, SymbolKind.FOREIGN -> 3
        SymbolKind.METHOD -> 2
        SymbolKind.FIELD -> 5
        SymbolKind.PARAMETER -> 6
        else -> 6
    }
}
