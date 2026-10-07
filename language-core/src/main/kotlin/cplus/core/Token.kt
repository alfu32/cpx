package cplus.core

enum class TokenKind {
    IDENTIFIER,
    INTEGER_LITERAL,
    FLOAT_LITERAL,
    STRING_LITERAL,
    CHARACTER_LITERAL,
    KEYWORD,
    SYMBOL,
    END_OF_FILE,
    UNKNOWN
}

data class Token(
    val kind: TokenKind,
    val lexeme: String,
    val range: SourceRange
) {
    fun isLexeme(value: String): Boolean = lexeme == value
}

data class LexedSource(
    val source: SourceFile,
    val tokens: List<Token>,
    val diagnostics: List<Diagnostic>
)

class Lexer {
    private val keywords = setOf(
        "auto", "bool", "break", "case", "char", "const", "continue", "default",
        "do", "double", "else", "enum", "extern", "float", "for", "if", "inline",
        "int", "long", "package", "return", "short", "signed", "sizeof", "static",
        "struct", "switch", "typedef", "union", "enum", "unsigned", "void", "volatile", "while",
        "comptime", "import", "pub", "defer", "as", "true", "false"
    )

    private val multiCharacterSymbols = listOf(
        "...", "->", "++", "--", "==", "!=", "<=", ">=", "&&", "||", "+=", "-=",
        "*=", "/=", "<<", ">>", "::"
    )

    fun lex(source: SourceFile): LexedSource {
        val tokens = mutableListOf<Token>()
        val diagnostics = DiagnosticBag()
        var offset = 0

        fun range(start: Int, end: Int) = SourceRange(source.id, start, end)
        fun add(kind: TokenKind, start: Int, end: Int) {
            tokens += Token(kind, source.text.substring(start, end), range(start, end))
        }

        while (offset < source.text.length) {
            val character = source.text[offset]
            when {
                character.isWhitespace() -> offset++
                character == '/' && source.text.getOrNull(offset + 1) == '/' -> {
                    offset += 2
                    while (offset < source.text.length && source.text[offset] != '\n') offset++
                }
                character == '/' && source.text.getOrNull(offset + 1) == '*' -> {
                    val start = offset
                    offset += 2
                    while (offset + 1 < source.text.length &&
                        !(source.text[offset] == '*' && source.text[offset + 1] == '/')
                    ) offset++
                    if (offset + 1 >= source.text.length) {
                        diagnostics.error("unterminated block comment", range(start, source.text.length), "LEX001")
                    } else {
                        offset += 2
                    }
                }
                character.isLetter() || character == '_' -> {
                    val start = offset++
                    while (offset < source.text.length &&
                        (source.text[offset].isLetterOrDigit() || source.text[offset] == '_')
                    ) offset++
                    val kind = if (source.text.substring(start, offset) in keywords) {
                        TokenKind.KEYWORD
                    } else {
                        TokenKind.IDENTIFIER
                    }
                    add(kind, start, offset)
                }
                character.isDigit() -> {
                    val start = offset++
                    while (offset < source.text.length && source.text[offset].isDigit()) offset++
                    var isFloating = false
                    if (source.text.getOrNull(offset) == '.' && source.text.getOrNull(offset + 1)?.isDigit() == true) {
                        isFloating = true
                        offset++
                        while (offset < source.text.length && source.text[offset].isDigit()) offset++
                    }
                    if (source.text.getOrNull(offset) == 'e' || source.text.getOrNull(offset) == 'E') {
                        isFloating = true
                        offset++
                        if (source.text.getOrNull(offset) == '+' || source.text.getOrNull(offset) == '-') offset++
                        while (offset < source.text.length && source.text[offset].isDigit()) offset++
                    }
                    add(if (isFloating) TokenKind.FLOAT_LITERAL else TokenKind.INTEGER_LITERAL, start, offset)
                }
                character == '.' && source.text.getOrNull(offset + 1)?.isDigit() == true -> {
                    val start = offset++
                    while (offset < source.text.length && source.text[offset].isDigit()) offset++
                    if (source.text.getOrNull(offset) == 'e' || source.text.getOrNull(offset) == 'E') {
                        offset++
                        if (source.text.getOrNull(offset) == '+' || source.text.getOrNull(offset) == '-') offset++
                        while (offset < source.text.length && source.text[offset].isDigit()) offset++
                    }
                    add(TokenKind.FLOAT_LITERAL, start, offset)
                }
                character == '"' -> {
                    val start = offset++
                    var terminated = false
                    while (offset < source.text.length) {
                        if (source.text[offset] == '\\') {
                            offset += 2
                        } else if (source.text[offset] == '"') {
                            offset++
                            terminated = true
                            break
                        } else {
                            offset++
                        }
                    }
                    if (!terminated) diagnostics.error("unterminated string literal", range(start, source.text.length), "LEX002")
                    add(TokenKind.STRING_LITERAL, start, offset.coerceAtMost(source.text.length))
                }
                character == '\'' -> {
                    val start = offset++
                    var terminated = false
                    while (offset < source.text.length) {
                        if (source.text[offset] == '\\') {
                            offset += 2
                        } else if (source.text[offset] == '\'') {
                            offset++
                            terminated = true
                            break
                        } else {
                            offset++
                        }
                    }
                    if (!terminated) diagnostics.error("unterminated character literal", range(start, source.text.length), "LEX003")
                    add(TokenKind.CHARACTER_LITERAL, start, offset.coerceAtMost(source.text.length))
                }
                else -> {
                    val start = offset
                    val symbol = multiCharacterSymbols.firstOrNull { source.text.startsWith(it, offset) }
                    if (symbol != null) {
                        offset += symbol.length
                        add(TokenKind.SYMBOL, start, offset)
                    } else if (character in "{}()[];,.?:+-*/%<>=!&|^~") {
                        offset++
                        add(TokenKind.SYMBOL, start, offset)
                    } else {
                        offset++
                        add(TokenKind.UNKNOWN, start, offset)
                        diagnostics.error("unrecognized character '$character'", range(start, offset), "LEX004")
                    }
                }
            }
        }

        val eofRange = range(source.text.length, source.text.length)
        tokens += Token(TokenKind.END_OF_FILE, "", eofRange)
        return LexedSource(source, tokens, diagnostics.diagnostics)
    }

    /**
     * Re-tokenizes a changed source without retaining mutable lexer state.
     *
     * The edit window is expanded to complete source lines and to the next
     * lexically neutral boundary. Tokens and diagnostics outside that window
     * are reused with their offsets shifted by the edit delta. If the edit
     * changes an unterminated comment or literal context, the safe fallback is
     * a complete lex so diagnostics remain authoritative.
     */
    fun relex(
        source: SourceFile,
        previous: LexedSource? = null,
        changedRange: IntRange? = null
    ): LexedSource {
        if (previous == null || changedRange == null || previous.source.id != source.id) return lex(source)
        val oldText = previous.source.text
        val newText = source.text
        if (oldText == newText) return previous.copy(source = source)

        val prefix = commonPrefix(oldText, newText)
        val suffix = commonSuffix(oldText, newText, prefix)
        val oldEditEnd = oldText.length - suffix
        val newEditEnd = newText.length - suffix
        val oldStart = lineStart(oldText, prefix)
        val newStart = lineStart(newText, prefix)
        if (!isNeutral(oldText, oldStart) || !isNeutral(newText, newStart)) return lex(source)

        var oldEnd = lineEnd(oldText, oldEditEnd)
        var newEnd = lineEnd(newText, newEditEnd)
        while (!isNeutral(newText, newEnd) || !isNeutral(oldText, oldEnd)) {
            if (newEnd >= newText.length || oldEnd >= oldText.length) {
                oldEnd = oldText.length
                newEnd = newText.length
                break
            }
            oldEnd = lineEnd(oldText, oldEnd + 1)
            newEnd = lineEnd(newText, newEnd + 1)
        }

        val windowText = " ".repeat(newStart) + newText.substring(newStart, newEnd)
        val windowSource = source.copy(text = windowText)
        val window = lex(windowSource)
        val delta = newEnd - oldEnd
        val before = previous.tokens
            .filter { it.kind != TokenKind.END_OF_FILE && it.range.endOffset <= oldStart }
        val after = previous.tokens
            .filter { it.kind != TokenKind.END_OF_FILE && it.range.startOffset >= oldEnd }
            .map { token -> token.copy(range = shift(token.range, delta)) }
        val inside = window.tokens.filter {
            it.kind != TokenKind.END_OF_FILE &&
                it.range.startOffset >= newStart &&
                it.range.endOffset <= newEnd
        }
        val merged = (before + inside + after).sortedWith(compareBy({ it.range.startOffset }, { it.range.endOffset }))
        val diagnostics = previous.diagnostics
            .filter { diagnostic -> (diagnostic.range?.endOffset ?: 0) <= oldStart }
            .map { it }
            .plus(
                previous.diagnostics
                    .filter { diagnostic -> (diagnostic.range?.startOffset ?: Int.MAX_VALUE) >= oldEnd }
                    .map { diagnostic -> diagnostic.shifted(source.id, delta) }
            )
            .plus(window.diagnostics)
            .distinct()
        return LexedSource(
            source,
            merged + Token(TokenKind.END_OF_FILE, "", SourceRange(source.id, source.text.length, source.text.length)),
            diagnostics
        )
    }

    private fun commonPrefix(left: String, right: String): Int {
        var index = 0
        while (index < left.length && index < right.length && left[index] == right[index]) index++
        return index
    }

    private fun commonSuffix(left: String, right: String, prefix: Int): Int {
        var count = 0
        while (
            count < left.length - prefix &&
            count < right.length - prefix &&
            left[left.length - count - 1] == right[right.length - count - 1]
        ) count++
        return count
    }

    private fun lineStart(text: String, offset: Int): Int {
        val bounded = offset.coerceIn(0, text.length)
        return text.lastIndexOf('\n', bounded - 1).let { if (it < 0) 0 else it + 1 }
    }

    private fun lineEnd(text: String, offset: Int): Int {
        val bounded = offset.coerceIn(0, text.length)
        val newline = text.indexOf('\n', bounded)
        return if (newline < 0) text.length else newline + 1
    }

    private fun isNeutral(text: String, offset: Int): Boolean {
        var index = 0
        var blockComment = false
        var string = false
        var character = false
        var escaped = false
        while (index < offset.coerceIn(0, text.length)) {
            val current = text[index]
            val next = text.getOrNull(index + 1)
            when {
                blockComment -> if (current == '*' && next == '/') {
                    blockComment = false
                    index += 2
                    continue
                }
                string -> when {
                    escaped -> escaped = false
                    current == '\\' -> escaped = true
                    current == '"' -> string = false
                }
                character -> when {
                    escaped -> escaped = false
                    current == '\\' -> escaped = true
                    current == '\'' -> character = false
                }
                current == '/' && next == '*' -> {
                    blockComment = true
                    index += 2
                    continue
                }
                current == '/' && next == '/' -> {
                    val newline = text.indexOf('\n', index + 2)
                    index = if (newline < 0) offset else newline
                    continue
                }
                current == '"' -> string = true
                current == '\'' -> character = true
            }
            index++
        }
        return !blockComment && !string && !character
    }

    private fun shift(range: SourceRange, delta: Int): SourceRange = SourceRange(
        range.file,
        (range.startOffset + delta).coerceAtLeast(0),
        (range.endOffset + delta).coerceAtLeast(0)
    )

    private fun Diagnostic.shifted(file: SourceFileId, delta: Int): Diagnostic = copy(
        range = range?.let { original ->
            SourceRange(
                file,
                (original.startOffset + delta).coerceAtLeast(0),
                (original.endOffset + delta).coerceAtLeast(0)
            )
        }
    )
}
