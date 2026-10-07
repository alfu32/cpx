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
}
