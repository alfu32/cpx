package cplus.cli

import cplus.core.Lexer
import cplus.core.LineIndex
import cplus.core.SourceFile
import cplus.core.SourceFileId
import cplus.core.Token
import cplus.core.TokenKind
import java.nio.file.Path

internal enum class ImportCompletionKind {
    PROVIDER,
    SELECTIVE_NAME
}

internal data class ImportTextRange(val startOffset: Int, val endOffset: Int)

internal data class ImportCompletionContext(
    val kind: ImportCompletionKind,
    val prefix: String,
    val replacementRange: ImportTextRange,
    val provider: String? = null,
    val existingNames: Set<String> = emptySet()
)

internal data class IdentifierCompletionContext(
    val prefix: String,
    val replacementRange: ImportTextRange
)

/** Classifies import completion positions using the compiler's lexer tokens. */
internal object ImportCompletionContextFinder {
    private val providerCharacter = { character: Char ->
        character.isLetterOrDigit() || character == '_' || character == '.' || character == '/' || character == '-'
    }
    private val identifierCharacter = { character: Char ->
        character.isLetterOrDigit() || character == '_'
    }

    fun find(text: String, position: LspPosition): ImportCompletionContext? {
        val offset = offsetAt(text, position) ?: return null
        if (isInsideComment(text, offset)) return null

        val source = SourceFile(SourceFileId(0), Path.of("<lsp-import-context>"), text, 0)
        val tokens = Lexer().lex(source).tokens
        val preceding = tokens.filter { it.kind != TokenKind.END_OF_FILE && it.range.endOffset <= offset }
        val lastBoundary = preceding.indexOfLast { it.isLexeme(";") }
        val segmentStart = lastBoundary + 1
        val importIndex = (segmentStart until preceding.size).lastOrNull { preceding[it].isLexeme("import") }
            ?: return null
        val importTokens = preceding.drop(importIndex)
        val currentToken = tokens.firstOrNull {
            it.kind != TokenKind.END_OF_FILE && it.range.startOffset < offset && offset < it.range.endOffset
        }

        val completeSegment = tokens.asSequence()
            .filter { it.kind != TokenKind.END_OF_FILE && it.range.startOffset >= preceding[importIndex].range.startOffset }
            .takeWhile { !it.isLexeme(";") }
            .toList()
        val kind = classify(importTokens) ?: return null
        val tokenForPrefix = currentToken?.takeIf { it.range.endOffset > offset }
        val quotedProvider = kind == ImportCompletionKind.PROVIDER &&
            tokenForPrefix?.kind == TokenKind.STRING_LITERAL
        if (tokenForPrefix?.kind == TokenKind.STRING_LITERAL && !quotedProvider) return null

        val range = when {
            quotedProvider -> {
                val token = requireNotNull(tokenForPrefix)
                val startsWithQuote = text.getOrNull(token.range.startOffset) == '"'
                val endsWithQuote = token.range.endOffset > token.range.startOffset + 1 &&
                    text.getOrNull(token.range.endOffset - 1) == '"'
                ImportTextRange(
                    (token.range.startOffset + if (startsWithQuote) 1 else 0).coerceAtMost(offset),
                    (token.range.endOffset - if (endsWithQuote) 1 else 0).coerceAtLeast(offset)
                )
            }
            else -> rawWordRange(text, offset, if (kind == ImportCompletionKind.PROVIDER) providerCharacter else identifierCharacter)
        }
        val selective = kind == ImportCompletionKind.SELECTIVE_NAME
        val provider = if (selective) providerAfterFrom(completeSegment) else null
        val existingNames = if (selective) existingSelectiveNames(importTokens, offset) else emptySet()
        return ImportCompletionContext(kind, text.substring(range.startOffset, offset), range, provider, existingNames)
    }

    fun identifier(text: String, position: LspPosition): IdentifierCompletionContext? {
        val offset = offsetAt(text, position) ?: return null
        if (isInsideComment(text, offset)) return null
        val source = SourceFile(SourceFileId(0), Path.of("<lsp-identifier-context>"), text, 0)
        val tokens = Lexer().lex(source).tokens
        val current = tokens.firstOrNull { it.range.startOffset <= offset && offset < it.range.endOffset }
        if (current?.kind in setOf(TokenKind.STRING_LITERAL, TokenKind.CHARACTER_LITERAL)) return null
        val before = text.substring(0, offset)
        if (Regex("[A-Za-z_][A-Za-z0-9_]*\\s*(?:\\.|->)\\s*[A-Za-z_]*$").containsMatchIn(before)) return null
        val range = rawWordRange(text, offset, identifierCharacter)
        val prefix = text.substring(range.startOffset, offset)
        return IdentifierCompletionContext(prefix, range).takeIf { prefix.isNotEmpty() }
    }

    private fun providerAfterFrom(tokens: List<Token>): String? {
        val close = tokens.indexOfFirst { it.isLexeme("}") }
        if (close < 0) return null
        val from = tokens.drop(close + 1).indexOfFirst { it.isLexeme("from") }
        if (from < 0) return null
        val target = tokens.drop(close + 2 + from).takeWhile { !it.isLexeme("as") }
        if (target.isEmpty()) return null
        return target.joinToString("") { token ->
            if (token.kind == TokenKind.STRING_LITERAL) token.lexeme.removeSurrounding("\"", "\"") else token.lexeme
        }
    }

    private fun existingSelectiveNames(tokens: List<Token>, offset: Int): Set<String> {
        val close = tokens.indexOfFirst { it.isLexeme("}") }
        val names = tokens.drop(2).let { if (close >= 0) it.take(close - 2) else it }
        return names.filter { token ->
            token.kind == TokenKind.IDENTIFIER && !(token.range.startOffset < offset && token.range.endOffset >= offset)
        }.map(Token::lexeme).toSet()
    }

    private fun classify(importTokens: List<Token>): ImportCompletionKind? {
        if (importTokens.size < 1) return null
        if (importTokens.size == 1) return ImportCompletionKind.PROVIDER
        val first = importTokens.getOrNull(1)?.lexeme
        if (first != "{") {
            return if (importTokens.drop(1).any { it.isLexeme("as") }) null
            else ImportCompletionKind.PROVIDER
        }

        val close = importTokens.indexOfFirstFrom(2) { it.isLexeme("}") }
        if (close < 0) {
            val insideNames = importTokens.drop(2)
            val lastSeparator = insideNames.indexOfLast { it.isLexeme(",") }
            val currentNamePart = insideNames.drop(lastSeparator + 1)
            if (currentNamePart.any { it.isLexeme("as") }) return null
            return ImportCompletionKind.SELECTIVE_NAME
        }
        val afterBrace = importTokens.drop(close + 1)
        val fromIndex = afterBrace.indexOfFirst { it.isLexeme("from") }
        if (fromIndex < 0) return null
        if (afterBrace.drop(fromIndex + 1).any { it.isLexeme("as") }) return null
        return ImportCompletionKind.PROVIDER
    }

    private fun List<Token>.indexOfFirstFrom(startIndex: Int, predicate: (Token) -> Boolean): Int {
        for (index in startIndex until size) if (predicate(this[index])) return index
        return -1
    }

    private fun rawWordRange(text: String, offset: Int, allowed: (Char) -> Boolean): ImportTextRange {
        var start = offset
        var end = offset
        while (start > 0 && allowed(text[start - 1])) start--
        while (end < text.length && allowed(text[end])) end++
        return ImportTextRange(start, end)
    }

    private fun offsetAt(text: String, position: LspPosition): Int? {
        if (position.line < 0 || position.character < 0) return null
        val offset = runCatching {
            LineIndex.from(text).offsetAt(cplus.core.SourcePosition(position.line + 1, position.character + 1))
        }.getOrNull() ?: return null
        return offset.takeIf { it in 0..text.length }
    }

    private fun isInsideComment(text: String, offset: Int): Boolean {
        var index = 0
        var inString = false
        var inCharacter = false
        var inLineComment = false
        var inBlockComment = false
        while (index < offset) {
            val character = text[index]
            val next = text.getOrNull(index + 1)
            when {
                inLineComment -> {
                    if (character == '\n') inLineComment = false
                    index++
                }
                inBlockComment -> {
                    if (character == '*' && next == '/') {
                        inBlockComment = false
                        index += 2
                    } else index++
                }
                inString || inCharacter -> {
                    if (character == '\\') index += 2
                    else {
                        if (inString && character == '"') inString = false
                        if (inCharacter && character == '\'') inCharacter = false
                        index++
                    }
                }
                character == '/' && next == '/' -> {
                    inLineComment = true
                    index += 2
                }
                character == '/' && next == '*' -> {
                    inBlockComment = true
                    index += 2
                }
                character == '"' -> {
                    inString = true
                    index++
                }
                character == '\'' -> {
                    inCharacter = true
                    index++
                }
                else -> index++
            }
        }
        return inLineComment || inBlockComment
    }
}
