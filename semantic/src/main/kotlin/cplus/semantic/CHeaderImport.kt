package cplus.semantic

import cplus.core.SourceFile
import cplus.core.Lexer
import cplus.core.Token
import cplus.core.TokenKind
import cplus.core.LineIndex
import java.nio.file.Path

enum class ForeignDeclarationKind {
    TYPE,
    FUNCTION,
    GLOBAL,
    ENUM_VALUE
}

enum class CHeaderAggregateKind { STRUCT, UNION, ENUM }

data class CHeaderField(val name: String, val typeName: String)

data class CFunctionPointerType(
    val returnType: String,
    val parameterTypes: List<String>,
    val isVariadic: Boolean
)

data class CHeaderMacro(
    val name: String,
    val parameters: String?,
    val replacement: String,
    val source: Path? = null,
    val line: Int? = null
)

data class CHeaderSourceLocation(val path: Path, val line: Int)

data class CHeaderDeclaration(
    val name: String,
    val kind: ForeignDeclarationKind,
    val typeName: String? = null,
    val parameterTypes: List<String> = emptyList(),
    val isVariadic: Boolean = false,
    val sourceRange: IntRange? = null,
    val aggregateKind: CHeaderAggregateKind? = null,
    val fields: List<CHeaderField> = emptyList(),
    val aggregateComplete: Boolean = false,
    val unsupportedReason: String? = null,
    val functionPointerType: CFunctionPointerType? = null,
    val constantExpression: String? = null,
    val externalSource: Path? = null,
    val externalLine: Int? = null
)

data class CSourceUnit(
    val source: SourceFile,
    val moduleName: String,
    val macros: List<CHeaderMacro> = emptyList(),
    val sourceLineOrigins: Map<Int, CHeaderSourceLocation> = emptyMap()
)

/**
 * Parses the deliberately small, declaration-only subset used by configured
 * C imports. Unsupported preprocessor and declaration forms stay outside the
 * semantic catalogue instead of being guessed.
 */
class CHeaderImportService(
    private val configuredHeaders: Map<String, String> = emptyMap()
) {
    fun declarations(module: String): Map<String, CHeaderDeclaration> =
        configuredHeaders[module].orEmpty().let(::parse)

    fun isKnownModule(module: String): Boolean = module in configuredHeaders

    fun sourceDeclarations(
        text: String,
        macros: List<CHeaderMacro> = emptyList(),
        sourceLineOrigins: Map<Int, CHeaderSourceLocation> = emptyMap()
    ): Map<String, CHeaderDeclaration> =
        parse(text, allowFunctionDefinitions = true).mapValues { (_, declaration) ->
            val offset = declaration.sourceRange?.first ?: return@mapValues declaration
            val generatedLine = LineIndex.from(text).positionAt(offset).line
            val original = sourceLineOrigins[generatedLine] ?: return@mapValues declaration
            if (declaration.externalSource != null) declaration
            else declaration.copy(externalSource = original.path, externalLine = original.line)
        }.toMutableMap().also { declarations ->
            macros.forEach { macro ->
                safeMacroDeclaration(macro)?.let { declarations[macro.name] = it }
            }
        }

    private fun safeMacroDeclaration(macro: CHeaderMacro): CHeaderDeclaration? {
        if (macro.parameters != null) return null
        val value = macro.replacement.trim().let(::unwrapIntegerParentheses)
        val type = when {
            C_STRING_LITERAL.matches(value) -> "char*"
            C_CHARACTER_LITERAL.matches(value) -> "int"
            C_INTEGER_LITERAL.matches(value) -> when {
                value.endsWith("ull", true) || value.endsWith("llu", true) -> "unsigned long long"
                value.endsWith("ll", true) -> "long long"
                value.endsWith("ul", true) || value.endsWith("lu", true) -> "unsigned long"
                value.endsWith("u", true) -> "unsigned int"
                value.endsWith("l", true) -> "long"
                else -> value.removePrefix("-").removePrefix("+").toLongOrNull()?.let {
                    if (it in Int.MIN_VALUE..Int.MAX_VALUE) "int" else return null
                } ?: return null
            }
            C_FLOAT_LITERAL.matches(value) -> when (value.lastOrNull()?.lowercaseChar()) {
                'f' -> "float"
                'l' -> "long double"
                else -> "double"
            }
            else -> return null
        }
        return CHeaderDeclaration(
            macro.name,
            ForeignDeclarationKind.ENUM_VALUE,
            typeName = type,
            constantExpression = value,
            externalSource = macro.source,
            externalLine = macro.line
        )
    }

    fun unsupportedPreprocessorLines(module: String): List<String> = configuredHeaders[module]
        .orEmpty()
        .lineSequence()
        .map(String::trim)
        .filter { it.startsWith("#") }
        .toList()

    private fun parse(text: String, allowFunctionDefinitions: Boolean = false): Map<String, CHeaderDeclaration> {
        val declarations = linkedMapOf<String, CHeaderDeclaration>()
        parseFunctions(text, allowFunctionDefinitions).forEach { declarations[it.name] = it }
        parseTypes(text).forEach { declarations[it.name] = it }
        parseGlobals(text).forEach { if (it.name !in declarations) declarations[it.name] = it }
        return declarations
    }

    private fun unwrapIntegerParentheses(value: String): String {
        var result = value.trim()
        while (result.startsWith('(') && result.endsWith(')')) {
            var depth = 0
            var enclosesWholeExpression = true
            result.forEachIndexed { index, character ->
                when (character) {
                    '(' -> depth++
                    ')' -> depth--
                }
                if (depth == 0 && index != result.lastIndex) enclosesWholeExpression = false
            }
            if (!enclosesWholeExpression || depthIsUnbalanced(result)) break
            result = result.substring(1, result.lastIndex).trim()
        }
        return result
    }

    private fun depthIsUnbalanced(value: String): Boolean {
        var depth = 0
        value.forEach { character ->
            if (character == '(') depth++
            if (character == ')') depth--
            if (depth < 0) return true
        }
        return depth != 0
    }

    private fun parseGlobals(text: String): List<CHeaderDeclaration> {
        val tokens = lex(text)
        val declarations = mutableListOf<CHeaderDeclaration>()
        var start = 0
        var index = 0
        var parens = 0
        var brackets = 0
        while (index < tokens.size) {
            when (tokens[index].lexeme) {
                "(" -> parens++
                ")" -> parens = (parens - 1).coerceAtLeast(0)
                "[" -> brackets++
                "]" -> brackets = (brackets - 1).coerceAtLeast(0)
                "{" -> if (parens == 0 && brackets == 0) {
                    val close = matchingBrace(tokens, index)
                    if (close == null) break
                    val prefix = tokens.subList(start, index)
                    val functionBody = prefix.indices.any { open ->
                        prefix[open].lexeme == "(" && candidateAt(prefix, 0, open) != null
                    }
                    index = close + 1
                    if (functionBody) start = index
                    // Aggregate and initializer braces stay attached to their top-level
                    // declaration until its semicolon; function bodies do not.
                    parens = 0
                    brackets = 0
                    continue
                }
                ";" -> if (parens == 0 && brackets == 0) {
                    globalDeclaration(tokens, start, index)?.let(declarations::add)
                    start = index + 1
                }
            }
            index++
        }
        return declarations
    }

    private fun globalDeclaration(tokens: List<Token>, start: Int, end: Int): CHeaderDeclaration? {
        if (start >= end) return null
        val declaration = tokens.subList(start, end)
        if (declaration.any { it.lexeme in setOf("typedef", "struct", "union", "enum", "static") }) return null
        if (declaration.any { it.lexeme == "(" || it.lexeme == ")" || it.lexeme == "{" || it.lexeme == "}" }) return null
        val initializer = declaration.indexOfFirst { it.lexeme == "=" }.let { if (it < 0) declaration.size else it }
        val declarator = declaration.subList(0, initializer)
        if (declarator.any { it.lexeme == "," || it.lexeme == ":" }) return null
        val nameIndex = declarator.indexOfLast { it.kind == TokenKind.IDENTIFIER }
        if (nameIndex <= 0) return null
        val name = declarator[nameIndex].lexeme
        val typeTokens = declarator.filterIndexed { tokenIndex, _ -> tokenIndex != nameIndex }
            .filterNot { it.lexeme in setOf("extern", "register", "_Thread_local") }
        if (typeTokens.isEmpty()) return null
        return CHeaderDeclaration(
            name,
            ForeignDeclarationKind.GLOBAL,
            normalizeType(render(typeTokens)),
            sourceRange = tokens[start].range.startOffset until tokens[end].range.endOffset
        )
    }

    private fun parseTypes(text: String): List<CHeaderDeclaration> {
        val tokens = lex(text)
        val declarations = linkedMapOf<String, CHeaderDeclaration>()
        var index = 0
        while (index < tokens.size) {
            val token = tokens[index]
            val aggregateKind = when (token.lexeme) {
                "struct" -> CHeaderAggregateKind.STRUCT
                "union" -> CHeaderAggregateKind.UNION
                "enum" -> CHeaderAggregateKind.ENUM
                else -> null
            }
            if (aggregateKind != null) {
                val tag = tokens.getOrNull(index + 1)?.takeIf { it.kind == TokenKind.IDENTIFIER }
                val afterTag = if (tag == null) index + 1 else index + 2
                val bodyOpen = tokens.getOrNull(afterTag)?.takeIf { it.lexeme == "{" }
                if (aggregateKind == CHeaderAggregateKind.ENUM && bodyOpen != null) {
                    val bodyClose = matchingBrace(tokens, afterTag)
                    if (bodyClose != null) {
                        parseEnumerators(tokens, afterTag + 1, bodyClose).forEach { declarations[it.name] = it }
                    }
                }
                if (tag != null && (bodyOpen != null || tokens.getOrNull(afterTag)?.lexeme == ";")) {
                    val bodyClose = bodyOpen?.let { matchingBrace(tokens, afterTag) }
                    val unsupportedAggregate = if (bodyClose != null && aggregateKind != CHeaderAggregateKind.ENUM) {
                        unsupportedAggregateLayout(tokens, afterTag + 1, bodyClose)
                    } else null
                    val endIndex = bodyClose ?: afterTag
                    val endOffset = tokens.getOrNull(endIndex + 1)?.takeIf { it.lexeme == ";" }?.range?.endOffset
                        ?: tokens[endIndex].range.endOffset
                    declarations[tag.lexeme] = CHeaderDeclaration(
                        tag.lexeme,
                        ForeignDeclarationKind.TYPE,
                        "${token.lexeme} ${tag.lexeme}",
                        sourceRange = token.range.startOffset until endOffset,
                        aggregateKind = aggregateKind,
                        aggregateComplete = bodyClose != null,
                        unsupportedReason = unsupportedAggregate,
                        fields = if (bodyClose != null && aggregateKind != CHeaderAggregateKind.ENUM) {
                            parseFields(tokens, afterTag + 1, bodyClose)
                        } else emptyList()
                    )
                }
            }
            if (token.lexeme == "typedef") {
                val end = findDeclarationEnd(tokens, index + 1) ?: break
                val semicolon = end.first
                val typedefTokens = tokens.subList(index + 1, semicolon)
                val functionPointerName = (0 until typedefTokens.size - 4).firstNotNullOfOrNull { candidate ->
                    candidate.takeIf {
                        typedefTokens[it].lexeme == "(" && typedefTokens[it + 1].lexeme == "*" &&
                            typedefTokens[it + 2].kind == TokenKind.IDENTIFIER && typedefTokens[it + 3].lexeme == ")" &&
                            typedefTokens[it + 4].lexeme == "("
                    }?.plus(2)
                }
                val aliasIndex = functionPointerName ?: typedefTokens.indexOfLast { it.kind == TokenKind.IDENTIFIER }
                if (aliasIndex > 0) {
                    val alias = typedefTokens[aliasIndex].lexeme
                    val aggregateBody = typedefTokens.indexOfFirst { it.lexeme == "{" }
                    val aliasTypeTokens = if (aggregateBody >= 0) {
                        typedefTokens.subList(0, aggregateBody)
                    } else {
                        typedefTokens.filterIndexed { tokenIndex, _ -> tokenIndex != aliasIndex }
                    }
                    var typeName = normalizeType(render(aliasTypeTokens))
                    if (aggregateBody >= 0 && typeName in setOf("struct", "union", "enum")) {
                        typeName = "$typeName $alias"
                    }
                    val anonymousOrTypedefAggregateKind = typedefTokens.firstOrNull()?.lexeme?.let {
                        when (it) {
                            "struct" -> CHeaderAggregateKind.STRUCT
                            "union" -> CHeaderAggregateKind.UNION
                            "enum" -> CHeaderAggregateKind.ENUM
                            else -> null
                        }
                    }
                    val aggregateClose = if (aggregateBody >= 0) matchingBrace(typedefTokens, aggregateBody) else null
                    val unsupportedAggregate = if (aggregateClose != null && anonymousOrTypedefAggregateKind != CHeaderAggregateKind.ENUM) {
                        unsupportedAggregateLayout(typedefTokens, aggregateBody + 1, aggregateClose)
                    } else null
                    if (anonymousOrTypedefAggregateKind == CHeaderAggregateKind.ENUM && aggregateClose != null) {
                        parseEnumerators(typedefTokens, aggregateBody + 1, aggregateClose)
                            .forEach { declarations[it.name] = it }
                    }
                    val namedTag = aliasTypeTokens.firstOrNull()?.lexeme
                        ?.takeIf { it in setOf("struct", "union", "enum") }
                        ?.let { aliasTypeTokens.getOrNull(1)?.lexeme }
                    val taggedDeclaration = namedTag?.let(declarations::get)
                    val functionPointerType = parseFunctionPointerType(aliasTypeTokens)
                    if (typeName.isNotBlank()) {
                        declarations[alias] = CHeaderDeclaration(
                            alias,
                            ForeignDeclarationKind.TYPE,
                            typeName,
                            sourceRange = token.range.startOffset until tokens[semicolon].range.endOffset,
                            aggregateKind = anonymousOrTypedefAggregateKind ?: taggedDeclaration?.aggregateKind,
                            aggregateComplete = aggregateClose != null || taggedDeclaration?.aggregateComplete == true,
                            unsupportedReason = unsupportedAggregate ?: taggedDeclaration?.unsupportedReason,
                            functionPointerType = functionPointerType,
                            fields = if (aggregateClose != null && anonymousOrTypedefAggregateKind != CHeaderAggregateKind.ENUM) {
                                parseFields(typedefTokens, aggregateBody + 1, aggregateClose)
                            } else taggedDeclaration?.fields.orEmpty()
                        )
                    }
                }
                index = semicolon + 1
                continue
            }
            index++
        }
        val entries = declarations.values.toList()
        val cyclicTypedefs = cyclicTypedefNames(entries)
        return entries.map { declaration ->
            if (declaration.name in cyclicTypedefs) {
                declaration.copy(unsupportedReason = "cyclic C typedef dependency involving '${declaration.name}'")
            } else declaration
        }
    }

    private fun parseFunctionPointerType(tokens: List<Token>): CFunctionPointerType? {
        val pointerOpen = (0 until tokens.size - 4).firstOrNull { index ->
            tokens[index].lexeme == "(" && tokens[index + 1].lexeme == "*" &&
                tokens[index + 2].lexeme == ")" && tokens[index + 3].lexeme == "("
        } ?: return null
        val parameterOpen = pointerOpen + 3
        val parameterClose = matchingDelimiter(tokens, parameterOpen, "(", ")") ?: return null
        val returnType = normalizeType(render(tokens.subList(0, pointerOpen)))
        if (returnType.isBlank()) return null
        val rawParameters = splitParameters(tokens, parameterOpen + 1, parameterClose).map(::render).map(String::trim)
        val variadic = rawParameters.any { it == "..." }
        val parameters = rawParameters.filter { it.isNotEmpty() && it != "..." && it != "void" }.map(::parameterType)
        return CFunctionPointerType(returnType, parameters, variadic)
    }

    private fun cyclicTypedefNames(declarations: List<CHeaderDeclaration>): Set<String> {
        val aliases = declarations.asSequence()
            .filter { it.kind == ForeignDeclarationKind.TYPE && it.aggregateKind == null }
            .mapNotNull { declaration ->
                val target = declaration.typeName?.trim() ?: return@mapNotNull null
                if (Regex("[A-Za-z_][A-Za-z0-9_]*").matches(target)) declaration.name to target else null
            }
            .toMap()
        val cyclic = mutableSetOf<String>()
        val visited = mutableSetOf<String>()
        val active = mutableListOf<String>()
        fun visit(name: String) {
            val cycleStart = active.indexOf(name)
            if (cycleStart >= 0) {
                cyclic += active.subList(cycleStart, active.size)
                return
            }
            if (!visited.add(name)) return
            active += name
            aliases[name]?.takeIf { it in aliases }?.let(::visit)
            active.removeAt(active.lastIndex)
        }
        aliases.keys.forEach(::visit)
        return cyclic
    }

    private fun parseFields(tokens: List<Token>, start: Int, end: Int): List<CHeaderField> {
        val fields = mutableListOf<CHeaderField>()
        var segmentStart = start
        var parens = 0
        var brackets = 0
        for (index in start..end) {
            if (index == end || (tokens[index].lexeme == ";" && parens == 0 && brackets == 0)) {
                val declaration = tokens.subList(segmentStart, index)
                val fieldNameIndex = declaration.indexOfLast { it.kind == TokenKind.IDENTIFIER }
                if (fieldNameIndex > 0) {
                    val fieldName = declaration[fieldNameIndex].lexeme
                    val fieldType = normalizeType(render(declaration.filterIndexed { fieldIndex, _ -> fieldIndex != fieldNameIndex }))
                    if (fieldType.isNotBlank()) fields += CHeaderField(fieldName, fieldType)
                }
                segmentStart = index + 1
            } else {
                when (tokens[index].lexeme) {
                    "(" -> parens++
                    ")" -> parens--
                    "[" -> brackets++
                    "]" -> brackets--
                }
            }
        }
        return fields
    }

    private fun unsupportedAggregateLayout(tokens: List<Token>, start: Int, end: Int): String? {
        var parens = 0
        var brackets = 0
        for (index in start until end) {
            when (tokens[index].lexeme) {
                "(" -> parens++
                ")" -> parens--
                "[" -> {
                    if (parens == 0) return "C aggregate array fields do not yet have a verified ABI layout"
                    brackets++
                }
                "]" -> brackets--
                ":" -> if (parens == 0 && brackets == 0) {
                    return "C bit-field layouts are target/compiler-specific and are not supported"
                }
                "," -> if (parens == 0 && brackets == 0) {
                    return "multiple C field declarators in one declaration are not supported"
                }
                "{" -> if (parens == 0) return "nested C aggregate field layouts are not supported"
            }
        }
        return null
    }

    private fun parseEnumerators(tokens: List<Token>, start: Int, end: Int): List<CHeaderDeclaration> {
        val values = mutableListOf<CHeaderDeclaration>()
        var segmentStart = start
        var nested = 0
        for (index in start..end) {
            if (index == end || (tokens[index].lexeme == "," && nested == 0)) {
                val name = tokens.subList(segmentStart, index).firstOrNull { it.kind == TokenKind.IDENTIFIER }
                if (name != null) {
                    val assignment = (segmentStart until index).firstOrNull { tokens[it].lexeme == "=" }
                    val value = assignment?.let { render(tokens.subList(it + 1, index)) }
                    values += CHeaderDeclaration(
                        name.lexeme,
                        ForeignDeclarationKind.ENUM_VALUE,
                        "int",
                        sourceRange = tokens[segmentStart].range.startOffset until tokens[index - 1].range.endOffset,
                        constantExpression = value
                    )
                }
                segmentStart = index + 1
            } else if (tokens[index].lexeme in setOf("(", "[")) {
                nested++
            } else if (tokens[index].lexeme in setOf(")", "]")) {
                nested--
            }
        }
        return values
    }

    private fun findDeclarationEnd(tokens: List<Token>, start: Int): Pair<Int, Int>? {
        var braces = 0
        var parens = 0
        for (index in start until tokens.size) {
            when (tokens[index].lexeme) {
                "{" -> braces++
                "}" -> braces--
                "(" -> parens++
                ")" -> parens--
                ";" -> if (braces == 0 && parens == 0) return index to index
            }
        }
        return null
    }

    private fun lex(text: String): List<Token> = Lexer().lex(
        SourceFile(cplus.core.SourceFileId(0), java.nio.file.Path.of("<c-header>"), maskPreprocessorLines(text), 0)
    ).tokens.filter { it.kind != TokenKind.END_OF_FILE }

    private fun maskPreprocessorLines(text: String): String {
        val chars = text.toCharArray()
        var lineStart = 0
        while (lineStart < chars.size) {
            val newline = text.indexOf('\n', lineStart).let { if (it < 0) chars.size else it }
            var first = lineStart
            while (first < newline && chars[first].isWhitespace()) first++
            if (first < newline && chars[first] == '#') {
                var continued: Boolean
                do {
                    var end = text.indexOf('\n', lineStart).let { if (it < 0) chars.size else it }
                    var last = end - 1
                    while (last >= lineStart && chars[last].isWhitespace()) last--
                    continued = last >= lineStart && chars[last] == '\\'
                    for (offset in lineStart until end) chars[offset] = ' '
                    lineStart = if (end < chars.size) end + 1 else chars.size
                } while (continued && lineStart < chars.size)
            } else {
                lineStart = if (newline < chars.size) newline + 1 else chars.size
            }
        }
        return String(chars)
    }

    /** Scans top-level C declaration boundaries so nested callback parameters and bodies are opaque. */
    private fun parseFunctions(text: String, allowDefinitions: Boolean): List<CHeaderDeclaration> {
        val tokens = lex(text)
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

    private fun normalizeType(type: String): String = type
        .trim()
        .replace(Regex("\\s+"), " ")
        .replace(Regex("^(?:(?:extern|static|inline|__inline|__inline__)\\s+)+"), "")
        .trim()

    companion object {
        private val C_STRING_LITERAL = Regex("(?:u8|u|U|L)?\"(?:\\\\.|[^\"\\\\])*\"")
        private val C_CHARACTER_LITERAL = Regex("(?:u|U|L)?'(?:\\\\.|[^'\\\\])'")
        private val C_INTEGER_LITERAL = Regex("[+-]?(?:0[xX][0-9A-Fa-f]+|[0-9]+)(?:[uU](?:ll|LL|l|L)?|(?:ll|LL|l|L)[uU]?)?")
        private val C_FLOAT_LITERAL = Regex("[+-]?(?:(?:[0-9]+\\.[0-9]*|\\.[0-9]+|[0-9]+)(?:[eE][+-]?[0-9]+)?)[fFlL]?")

    }
}
