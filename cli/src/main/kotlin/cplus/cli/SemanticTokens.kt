package cplus.cli

import cplus.compiler.CompileResult
import cplus.core.Origin
import cplus.core.SourceRange
import cplus.core.Token
import cplus.core.TokenKind
import cplus.semantic.ReferenceKind
import cplus.semantic.SemanticModel
import cplus.semantic.Symbol
import cplus.semantic.SymbolKind

internal object SemanticTokenService {
    val tokenTypes: List<String> = listOf(
        "type",
        "function",
        "method",
        "property",
        "parameter",
        "variable",
        "keyword",
        "number",
        "string",
        "operator",
        "boolean"
    )

    private val tokenTypeIndexes = tokenTypes.withIndex().associate { it.value to it.index }

    fun encode(result: CompileResult): List<Int> {
        val artifact = result.artifacts.firstOrNull() ?: return emptyList()
        val model = result.semanticModel
        val references = model?.referenceIndex?.all().orEmpty()
            .mapNotNull { reference -> reference.origin.primaryRange?.let { it to reference } }
            .toMap()
        val source = artifact.lexed.source
        val lineIndex = cplus.core.LineIndex.from(source.text)
        val tokens = artifact.lexed.tokens
            .filter { it.kind != TokenKind.END_OF_FILE && it.kind != TokenKind.UNKNOWN }
            .mapNotNull { token ->
                classify(token, model, references)?.let { type ->
                    val start = lineIndex.positionAt(token.range.startOffset)
                    val end = lineIndex.positionAt(token.range.endOffset)
                    EncodedToken(
                        start.line - 1,
                        start.column - 1,
                        (end.column - start.column).coerceAtLeast(token.lexeme.length),
                        tokenTypeIndexes.getValue(type)
                    )
                }
            }

        var previousLine = 0
        var previousStart = 0
        return buildList(tokens.size * 5) {
            tokens.forEach { token ->
                val deltaLine = token.line - previousLine
                val deltaStart = if (deltaLine == 0) token.start - previousStart else token.start
                add(deltaLine)
                add(deltaStart)
                add(token.length)
                add(token.type)
                add(0)
                previousLine = token.line
                previousStart = token.start
            }
        }
    }

    private fun classify(
        token: Token,
        model: SemanticModel?,
        references: Map<SourceRange, cplus.semantic.SymbolReference>
    ): String? = when (token.kind) {
        TokenKind.KEYWORD -> if (token.lexeme == "true" || token.lexeme == "false") "boolean" else "keyword"
        TokenKind.INTEGER_LITERAL, TokenKind.FLOAT_LITERAL -> "number"
        TokenKind.STRING_LITERAL, TokenKind.CHARACTER_LITERAL -> "string"
        TokenKind.SYMBOL -> "operator"
        TokenKind.IDENTIFIER -> classifyIdentifier(token, model, references)
        TokenKind.END_OF_FILE, TokenKind.UNKNOWN -> null
    }

    private fun classifyIdentifier(
        token: Token,
        model: SemanticModel?,
        references: Map<SourceRange, cplus.semantic.SymbolReference>
    ): String {
        val reference = references[token.range]
        if (reference != null && model != null) {
            model.symbols.firstOrNull { it.id == reference.symbol }?.let { return symbolTokenType(it, reference.kind) }
        }
        val symbol = model?.symbols
            ?.asSequence()
            ?.filter { it.name == token.lexeme && contains(it.origin, token.range) }
            ?.minByOrNull { symbol ->
                symbol.origin.primaryRange?.let { it.endOffset - it.startOffset } ?: Int.MAX_VALUE
            }
        return symbol?.let { symbolTokenType(it, null) } ?: "variable"
    }

    private fun symbolTokenType(symbol: Symbol, referenceKind: ReferenceKind?): String = when {
        referenceKind == ReferenceKind.MEMBER || symbol.kind == SymbolKind.FIELD -> "property"
        symbol.kind == SymbolKind.METHOD -> "method"
        symbol.kind == SymbolKind.FUNCTION || symbol.kind == SymbolKind.FOREIGN -> "function"
        symbol.kind == SymbolKind.STRUCT ||
            symbol.kind == SymbolKind.UNION ||
            symbol.kind == SymbolKind.ENUM ||
            symbol.kind == SymbolKind.ALIAS ||
            symbol.kind == SymbolKind.FOREIGN_TYPE -> "type"
        symbol.kind == SymbolKind.PARAMETER -> "parameter"
        else -> "variable"
    }

    private fun contains(origin: Origin, range: SourceRange): Boolean {
        val source = origin.primaryRange ?: return false
        return source.file == range.file &&
            source.startOffset <= range.startOffset &&
            source.endOffset >= range.endOffset
    }

    private data class EncodedToken(
        val line: Int,
        val start: Int,
        val length: Int,
        val type: Int
    )
}
