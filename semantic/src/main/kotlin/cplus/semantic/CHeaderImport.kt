package cplus.semantic

import cplus.core.SourceFile
import cplus.core.Lexer
import cplus.core.Token
import cplus.core.TokenKind

enum class ForeignDeclarationKind {
    TYPE,
    FUNCTION,
    GLOBAL,
    ENUM_VALUE
}

data class CHeaderDeclaration(
    val name: String,
    val kind: ForeignDeclarationKind,
    val typeName: String? = null,
    val parameterTypes: List<String> = emptyList(),
    val isVariadic: Boolean = false,
    val sourceRange: IntRange? = null
)

data class CSourceUnit(
    val source: SourceFile,
    val moduleName: String
)

/**
 * Parses the deliberately small, declaration-only subset used by configured
 * C imports. Unsupported preprocessor and declaration forms stay outside the
 * semantic catalogue instead of being guessed.
 */
class CHeaderImportService(
    private val configuredHeaders: Map<String, String> = defaultHeaders
) {
    fun declarations(module: String): Map<String, CHeaderDeclaration> =
        configuredHeaders[module].orEmpty().let(::parse)

    fun isKnownModule(module: String): Boolean = module in configuredHeaders

    fun sourceDeclarations(text: String): Map<String, CHeaderDeclaration> =
        parse(text, allowFunctionDefinitions = true)

    fun unsupportedPreprocessorLines(module: String): List<String> = configuredHeaders[module]
        .orEmpty()
        .lineSequence()
        .map(String::trim)
        .filter { it.startsWith("#") }
        .toList()

    private fun parse(text: String, allowFunctionDefinitions: Boolean = false): Map<String, CHeaderDeclaration> {
        val declarations = linkedMapOf<String, CHeaderDeclaration>()
        parseFunctions(text, allowFunctionDefinitions).forEach { declarations[it.name] = it }
        val typedefPattern = Regex(
            """(?m)^\s*typedef\s+([A-Za-z_][A-Za-z0-9_\s\*]*)\s+([A-Za-z_][A-Za-z0-9_]*)\s*;"""
        )
        typedefPattern.findAll(text).forEach { match ->
            declarations[match.groupValues[2]] = CHeaderDeclaration(
                match.groupValues[2],
                ForeignDeclarationKind.TYPE,
                normalizeType(match.groupValues[1]),
                sourceRange = match.range
            )
        }
        val globalPattern = Regex(
            """(?m)^\s*(?:extern\s+)?([A-Za-z_][A-Za-z0-9_\s\*]*?)\s+([A-Za-z_][A-Za-z0-9_]*)\s*(?:=\s*[^;]+)?\s*;"""
        )
        val globalText = if (allowFunctionDefinitions) stripBracedBodies(text) else text
        globalPattern.findAll(globalText).forEach { match ->
            if (match.groupValues[2] !in declarations) {
                declarations[match.groupValues[2]] = CHeaderDeclaration(
                    match.groupValues[2],
                    ForeignDeclarationKind.GLOBAL,
                    normalizeType(match.groupValues[1]),
                    sourceRange = match.range
                )
            }
        }
        return declarations
    }

    /** Scans top-level C declaration boundaries so nested callback parameters and bodies are opaque. */
    private fun parseFunctions(text: String, allowDefinitions: Boolean): List<CHeaderDeclaration> {
        val source = SourceFile(cplus.core.SourceFileId(0), java.nio.file.Path.of("<c-header>"), text, 0)
        val tokens = Lexer().lex(source).tokens.filter { it.kind != TokenKind.END_OF_FILE }
        val parsed = mutableListOf<CHeaderDeclaration>()
        var cursor = 0
        while (cursor < tokens.size) {
            var boundary = cursor
            var parens = 0
            var brackets = 0
            var candidate: FunctionCandidate? = null
            while (boundary < tokens.size) {
                val token = tokens[boundary]
                when (token.lexeme) {
                    "(" -> {
                        if (parens == 0 && brackets == 0) {
                            candidate = candidateAt(tokens, cursor, boundary)
                        }
                        parens++
                    }
                    ")" -> parens = (parens - 1).coerceAtLeast(0)
                    "[" -> brackets++
                    "]" -> brackets = (brackets - 1).coerceAtLeast(0)
                    ";" -> {
                        if (parens == 0 && brackets == 0) {
                            candidate?.takeIf { it.closeParen < boundary }?.let {
                                parsed += declaration(it, tokens[boundary].range.endOffset)
                            }
                            cursor = boundary + 1
                            break
                        }
                    }
                    "{" -> {
                        if (parens == 0 && brackets == 0) {
                            val bodyEnd = matchingBrace(tokens, boundary)
                            if (allowDefinitions && candidate != null && candidate.closeParen < boundary && bodyEnd != null) {
                                parsed += declaration(candidate, tokens[bodyEnd].range.endOffset)
                                cursor = bodyEnd + 1
                            } else if (bodyEnd != null) {
                                cursor = bodyEnd + 1
                            } else {
                                cursor = boundary + 1
                            }
                            break
                        }
                    }
                }
                boundary++
            }
            if (boundary >= tokens.size) cursor = tokens.size
            else if (cursor <= boundary) cursor = boundary + 1
        }
        return parsed
    }

    private data class FunctionCandidate(
        val name: String,
        val returnType: String,
        val parameters: List<String>,
        val variadic: Boolean,
        val startOffset: Int,
        val closeParen: Int
    )

    private fun candidateAt(tokens: List<Token>, start: Int, open: Int): FunctionCandidate? {
        val nameToken = tokens.getOrNull(open - 1) ?: return null
        if (nameToken.kind !in setOf(TokenKind.IDENTIFIER, TokenKind.KEYWORD)) return null
        val name = nameToken.lexeme
        if (name in C_DECLARATION_KEYWORDS) return null
        val close = matchingDelimiter(tokens, open, "(", ")") ?: return null
        val prefix = tokens.subList(start, open - 1)
        if (prefix.isEmpty()) return null
        val returnType = render(prefix).removePrefix("extern ").trim()
        if (returnType.isBlank() || returnType.contains("=")) return null
        val parameterTokens = splitParameters(tokens, open + 1, close)
        val rawParameters = parameterTokens.map(::render).map(String::trim)
        val variadic = rawParameters.any { it == "..." }
        val parameters = rawParameters.filter { it.isNotEmpty() && it != "..." && it != "void" }
            .map(::parameterType)
        return FunctionCandidate(name, normalizeType(returnType), parameters, variadic, tokens[start].range.startOffset, close)
    }

    private fun declaration(candidate: FunctionCandidate, end: Int) = CHeaderDeclaration(
        candidate.name,
        ForeignDeclarationKind.FUNCTION,
        candidate.returnType,
        candidate.parameters,
        candidate.variadic,
        candidate.startOffset until end
    )

    private fun splitParameters(tokens: List<Token>, start: Int, end: Int): List<List<Token>> {
        if (start == end) return emptyList()
        val result = mutableListOf<List<Token>>()
        var segmentStart = start
        var parens = 0
        var brackets = 0
        for (index in start until end) {
            when (tokens[index].lexeme) {
                "(" -> parens++
                ")" -> parens--
                "[" -> brackets++
                "]" -> brackets--
                "," -> if (parens == 0 && brackets == 0) {
                    result += tokens.subList(segmentStart, index)
                    segmentStart = index + 1
                }
            }
        }
        result += tokens.subList(segmentStart, end)
        return result
    }

    private fun matchingDelimiter(tokens: List<Token>, start: Int, left: String, right: String): Int? {
        var depth = 0
        for (index in start until tokens.size) {
            if (tokens[index].lexeme == left) depth++
            if (tokens[index].lexeme == right && --depth == 0) return index
        }
        return null
    }

    private fun matchingBrace(tokens: List<Token>, start: Int): Int? = matchingDelimiter(tokens, start, "{", "}")

    private fun render(tokens: List<Token>): String {
        val output = StringBuilder()
        tokens.forEachIndexed { index, token ->
            val previous = tokens.getOrNull(index - 1)
            val needsSpace = previous != null &&
                (previous.kind in setOf(TokenKind.IDENTIFIER, TokenKind.KEYWORD, TokenKind.INTEGER_LITERAL) &&
                    token.kind in setOf(TokenKind.IDENTIFIER, TokenKind.KEYWORD, TokenKind.INTEGER_LITERAL) ||
                    previous.lexeme == "," ||
                    previous.lexeme in setOf("const", "volatile", "restrict", "struct", "union", "enum"))
            if (needsSpace) output.append(' ')
            output.append(token.lexeme)
        }
        return output.toString()
    }

    private fun parameterType(parameter: String): String {
        val tokens = Lexer().lex(
            SourceFile(cplus.core.SourceFileId(0), java.nio.file.Path.of("<c-parameter>"), parameter, 0)
        ).tokens
            .filter { it.kind != TokenKind.END_OF_FILE }
        if (tokens.size < 2) return normalizeType(parameter)
        val lexemes = tokens.map(Token::lexeme)
        val callbackName = lexemes.indices.firstOrNull { index ->
            lexemes.getOrNull(index - 1) == "*" && index + 1 < lexemes.size && lexemes[index + 1] == ")"
        }
        val nameIndex = callbackName ?: tokens.lastIndex.takeIf {
            tokens[it].kind == TokenKind.IDENTIFIER &&
            tokens.size >= 2 && lexemes[it - 1] !in setOf("struct", "union", "enum", "const", "volatile")
        } ?: return normalizeType(parameter)
        return normalizeType(render(tokens.filterIndexed { index, _ -> index != nameIndex }))
    }

    private val C_DECLARATION_KEYWORDS = setOf(
        "if", "while", "for", "switch", "return", "sizeof", "_Alignof", "__attribute__", "__declspec"
    )

    private fun stripBracedBodies(text: String): String {
        val characters = text.toCharArray()
        var depth = 0
        text.forEachIndexed { index, character ->
            when {
                character == '{' -> {
                    depth++
                    characters[index] = ' '
                }
                character == '}' && depth > 0 -> {
                    depth--
                    characters[index] = ' '
                }
                depth > 0 && character != '\n' -> characters[index] = ' '
            }
        }
        return String(characters)
    }

    private fun normalizeType(type: String): String = type
        .trim()
        .replace(Regex("\\s+"), " ")
        .removePrefix("extern ")
        .trim()

    companion object {
        private fun complexHeaderDeclarations(): String = buildList {
            val complexResultFunctions = listOf(
                "cacos", "casin", "catan", "ccos", "csin", "ctan", "cacosh", "casinh", "catanh",
                "ccosh", "csinh", "ctanh", "cexp", "clog", "csqrt", "conj", "cproj"
            )
            val realResultFunctions = listOf("cabs", "carg", "cimag", "creal")
            val types = listOf(
                Triple("float", "f", "float _Complex"),
                Triple("double", "", "double _Complex"),
                Triple("long double", "l", "long double _Complex")
            )
            types.forEach { (realType, suffix, complexType) ->
                complexResultFunctions.forEach { name ->
                    add("extern $complexType $name$suffix($complexType value);")
                }
                add("extern $complexType cpow$suffix($complexType left, $complexType right);")
                realResultFunctions.forEach { name ->
                    add("extern $realType $name$suffix($complexType value);")
                }
            }
        }.joinToString("\n")

        private fun mathHeaderDeclarations(): String = buildList {
            add("typedef float float_t;")
            add("typedef double double_t;")
            val realTypes = listOf("float" to "f", "double" to "", "long double" to "l")

            fun variants(
                baseName: String,
                resultType: (String) -> String,
                parameters: (String) -> List<String>
            ) {
                realTypes.forEach { (type, suffix) ->
                    val name = "$baseName$suffix"
                    add("extern ${resultType(type)} $name(${parameters(type).joinToString(", ")});")
                }
            }

            listOf(
                "acos", "asin", "atan", "acosh", "asinh", "atanh", "cos", "sin", "tan",
                "cosh", "sinh", "tanh", "exp", "exp2", "expm1", "log", "log10", "log1p",
                "log2", "logb", "cbrt", "fabs", "sqrt", "erf", "erfc", "lgamma", "tgamma",
                "ceil", "floor", "nearbyint", "rint", "round", "trunc"
            ).forEach { name -> variants(name, { it }) { type -> listOf("$type value") } }

            listOf("atan2", "fmod", "remainder", "hypot", "pow", "copysign", "nextafter", "fdim", "fmax", "fmin")
                .forEach { name -> variants(name, { it }) { type -> listOf("$type left", "$type right") } }

            variants("frexp", { it }) { type -> listOf("$type value", "int* exponent") }
            variants("modf", { it }) { type -> listOf("$type value", "$type* integral") }
            variants("ilogb", { "int" }) { type -> listOf("$type value") }
            variants("ldexp", { it }) { type -> listOf("$type value", "int exponent") }
            variants("scalbn", { it }) { type -> listOf("$type value", "int exponent") }
            variants("scalbln", { it }) { type -> listOf("$type value", "long int exponent") }
            variants("lrint", { "long int" }) { type -> listOf("$type value") }
            variants("llrint", { "long long int" }) { type -> listOf("$type value") }
            variants("lround", { "long int" }) { type -> listOf("$type value") }
            variants("llround", { "long long int" }) { type -> listOf("$type value") }
            variants("nan", { it }) { _ -> listOf("const char* tag") }
            variants("nexttoward", { it }) { type -> listOf("$type value", "long double direction") }
            variants("remquo", { it }) { type -> listOf("$type left", "$type right", "int* quotient") }
            variants("fma", { it }) { type -> listOf("$type first", "$type second", "$type third") }
        }.joinToString("\n")

        private val defaultHeaders = mapOf(
            "c.stdio" to """
                extern int printf(const char* format, ...);
                extern int fprintf(FILE* stream, const char* format, ...);
                extern int sprintf(char* buffer, const char* format, ...);
                extern int snprintf(char* buffer, size_t size, const char* format, ...);
                extern int vprintf(const char* format, va_list arguments);
                extern int vfprintf(FILE* stream, const char* format, va_list arguments);
                extern int vsprintf(char* buffer, const char* format, va_list arguments);
                extern int vsnprintf(char* buffer, size_t size, const char* format, va_list arguments);
                extern int puts(const char* text);
                extern int putchar(int character);
                extern int getchar(void);
                extern FILE* fopen(const char* path, const char* mode);
                extern int fclose(FILE* stream);
                extern int fflush(FILE* stream);
                extern int fgetc(FILE* stream);
                extern int fputc(int character, FILE* stream);
                extern size_t fread(void* buffer, size_t size, size_t count, FILE* stream);
                extern size_t fwrite(const void* buffer, size_t size, size_t count, FILE* stream);
            """.trimIndent(),
            "c.stddef" to """
                typedef unsigned long size_t;
                typedef long ptrdiff_t;
                typedef long max_align_t;
            """.trimIndent(),
            "c.math" to mathHeaderDeclarations(),
            "c.complex" to complexHeaderDeclarations(),
            "c.stdlib" to """
                extern int abs(int value);
                extern long labs(long value);
                extern int atoi(const char* text);
                extern long strtol(const char* text, char** end, int base);
                extern unsigned long strtoul(const char* text, char** end, int base);
                extern void* malloc(size_t size);
                extern void* calloc(size_t count, size_t size);
                extern void* realloc(void* pointer, size_t size);
                extern void free(void* pointer);
                extern int rand(void);
                extern void srand(unsigned int seed);
                extern void exit(int status);
            """.trimIndent(),
            "c.string" to """
                extern size_t strlen(const char* text);
                extern char* strcpy(char* destination, const char* source);
                extern char* strncpy(char* destination, const char* source, size_t count);
                extern int strcmp(const char* left, const char* right);
                extern int strncmp(const char* left, const char* right, size_t count);
                extern void* memcpy(void* destination, const void* source, size_t count);
                extern void* memmove(void* destination, const void* source, size_t count);
                extern void* memset(void* destination, int value, size_t count);
                extern char* strchr(const char* text, int character);
                extern char* strstr(const char* text, const char* pattern);
            """.trimIndent(),
            "c.ctype" to """
                extern int isalnum(int character);
                extern int isalpha(int character);
                extern int isdigit(int character);
                extern int isspace(int character);
                extern int islower(int character);
                extern int isupper(int character);
                extern int tolower(int character);
                extern int toupper(int character);
            """.trimIndent(),
            "c.time" to """
                typedef long time_t;
                typedef long clock_t;
                extern time_t time(time_t* result);
                extern double difftime(time_t end, time_t start);
                extern clock_t clock(void);
            """.trimIndent(),
            "c.stdint" to """
                typedef signed char int8_t;
                typedef unsigned char uint8_t;
                typedef short int16_t;
                typedef unsigned short uint16_t;
                typedef int int32_t;
                typedef unsigned int uint32_t;
                typedef long long int64_t;
                typedef unsigned long long uint64_t;
            """.trimIndent(),
            "c.stdarg" to """
                typedef void* va_list;
            """.trimIndent()
        )
    }
}
