package cplus.core

class Parser(private val lexed: LexedSource) {
    private val tokens = lexed.tokens
    private var index = 0
    private val diagnostics = DiagnosticBag()

    data class ParsedExpression(
        val expression: SyntaxExpression?,
        val diagnostics: List<Diagnostic>
    )

    fun parseExpressionFragment(): ParsedExpression {
        val expression = parseExpression()
        if (expression != null && !atEnd()) {
            diagnostics.error("unexpected token after expression", peek().range, "PARSE403")
        }
        return ParsedExpression(expression, lexed.diagnostics + diagnostics.diagnostics)
    }

    fun parse(): ParsedSource {
        val declarations = mutableListOf<SyntaxDeclaration>()
        while (!atEnd()) {
            val before = index
            parseDeclaration()?.let(declarations::add)
            if (index == before) {
                diagnostics.error("unable to make progress while parsing", peek().range, "PARSE999")
                advance()
            }
        }
        val fileRange = SourceRange(lexed.source.id, 0, lexed.source.text.length)
        return ParsedSource(
            SyntaxProgram(declarations, fileRange, Origin.Direct(fileRange)),
            lexed.diagnostics + diagnostics.diagnostics
        )
    }

    private fun parseDeclaration(): SyntaxDeclaration? {
        var isPublic = false
        while (peek().lexeme in setOf("pub", "static", "extern", "inline")) {
            if (match("pub")) isPublic = true else advance()
        }
        if (peek().isLexeme("package")) return parsePackage()
        if (match("typedef")) return parseAlias(previous(), isPublic)
        if (peek().isLexeme("import")) return parseImport()
        if (peek().isLexeme("comptime")) return parseComptimeFunction(isPublic)
        if (peek().kind == TokenKind.IDENTIFIER && peek(1).isLexeme("(")) return parseCpxInvocation()
        if (match("struct") && peek(1).isLexeme("{")) {
            return parseStruct(peek(-1), isPublic)
        }
        if (peek().isLexeme("struct") && peek(2).isLexeme("{")) {
            val structKeyword = advance()
            return parseStruct(structKeyword, isPublic)
        }
        if (peek().isLexeme("union") && peek(2).isLexeme("{")) {
            val unionKeyword = advance()
            return parseUnion(unionKeyword, isPublic)
        }
        if (peek().isLexeme("enum") && peek(2).isLexeme("{")) {
            val enumKeyword = advance()
            return parseEnum(enumKeyword, isPublic)
        }

        val type = parseType() ?: return recoverDeclaration()
        val name = expectIdentifier("expected declaration name") ?: return recoverDeclaration()
        return if (match("(")) {
            parseFunction(type, name, isPublic = isPublic)
        } else {
            val arrayDimensions = parseArrayDimensions()
            val initializer = if (match("=")) parseExpression() else null
            expect(";", "expected ';' after global declaration")
            SyntaxGlobalVariable(
                type,
                name.lexeme,
                initializer,
                span(type.range, previous().range),
                direct(span(type.range, previous().range)),
                arrayDimensions,
                isPublic
            )
        }
    }

    private fun parsePackage(): SyntaxPackage {
        val start = expect("package", "expected 'package'") ?: previous()
        val parts = mutableListOf<String>()
        while (!atEnd() && !peek().isLexeme(";")) parts += advance().lexeme
        expect(";", "expected ';' after package declaration")
        val range = span(start.range, previous().range)
        return SyntaxPackage(parts.joinToString(""), range, direct(range))
    }

    private fun parseAlias(start: Token, isPublic: Boolean): SyntaxAlias? {
        val target = parseType() ?: return recoverDeclaration()?.let { null }
        val name = expectIdentifier("expected alias name") ?: return recoverDeclaration()?.let { null }
        val arrayDimensions = parseArrayDimensions()
        expect(";", "expected ';' after type alias")
        val range = span(start.range, previous().range)
        return SyntaxAlias(target, name.lexeme, arrayDimensions, range, direct(range), isPublic)
    }

    private fun parseComptimeFunction(isPublic: Boolean): SyntaxComptimeFunction {
        val start = expect("comptime", "expected 'comptime'") ?: previous()
        if (peek().isLexeme("cpx")) advance()
        else diagnostics.error("expected 'cpx' after 'comptime'", peek().range, "PARSE501")
        val category = if (match("<")) {
            val categoryToken = advance()
            expect(">", "expected '>' after CPX category")
            categoryToken.lexeme
        } else {
            "decl"
        }
        val name = expectIdentifier("expected compile-time function name") ?: syntheticToken("cpx", start.range)
        expect("(", "expected '(' after compile-time function name")
        val parameters = mutableListOf<SyntaxComptimeParameter>()
        if (!peek().isLexeme(")")) {
            do {
                val kind = if (peek().isLexeme("type")) advance() else {
                    diagnostics.error("compile-time parameters must declare a value kind", peek().range, "PARSE502")
                    advance()
                }
                val parameterName = expectIdentifier("expected compile-time parameter name")
                    ?: syntheticToken("parameter", kind.range)
                val parameterRange = span(kind.range, parameterName.range)
                parameters += SyntaxComptimeParameter(kind.lexeme, parameterName.lexeme, parameterRange, direct(parameterRange))
            } while (match(","))
        }
        expect(")", "expected ')' after compile-time parameters")
        expect("{", "expected '{' before compile-time function body")
        expect("return", "expected 'return' in compile-time function")
        val templateOpen = expect("{", "expected '{' to start CPX template") ?: previous()
        var depth = 1
        var templateEnd = templateOpen.range.endOffset
        while (!atEnd() && depth > 0) {
            val token = advance()
            when (token.lexeme) {
                "{" -> depth++
                "}" -> {
                    depth--
                    if (depth == 0) templateEnd = token.range.startOffset
                }
            }
        }
        val template = lexed.source.text.substring(templateOpen.range.endOffset, templateEnd)
        expect(";", "expected ';' after CPX template")
        val close = expect("}", "expected '}' after compile-time function") ?: previous()
        val range = span(start.range, close.range)
        return SyntaxComptimeFunction(name.lexeme, category, parameters, template, range, direct(range), isPublic)
    }

    private fun parseCpxInvocation(): SyntaxCpxInvocation {
        val name = advance()
        expect("(", "expected '(' after CPX invocation name")
        val arguments = mutableListOf<String>()
        while (!atEnd() && !peek().isLexeme(")")) {
            val parts = mutableListOf<String>()
            while (!atEnd() && !peek().isLexeme(",") && !peek().isLexeme(")")) {
                parts += advance().lexeme
            }
            if (parts.isNotEmpty()) arguments += parts.joinToString(" ")
            if (!match(",")) break
        }
        val close = expect(")", "expected ')' after CPX invocation arguments") ?: previous()
        expect(";", "expected ';' after CPX invocation")
        val range = span(name.range, previous().range)
        return SyntaxCpxInvocation(name.lexeme, arguments, range, direct(range))
    }

    private fun parseImport(): SyntaxImport {
        val start = expect("import", "expected 'import'") ?: previous()
        val names = mutableListOf<String>()
        if (match("{")) {
            while (!atEnd() && !peek().isLexeme("}")) {
                val name = expectIdentifier("expected imported name")
                if (name != null) names += name.lexeme
                if (!match(",")) break
            }
            expect("}", "expected '}' after imported names")
            expect("from", "expected 'from' after imported names")
        }
        val moduleParts = mutableListOf<String>()
        var alias: String? = null
        while (!atEnd() && !peek().isLexeme(";")) {
            if (match("as")) {
                alias = expectIdentifier("expected alias after 'as'")?.lexeme
            } else {
                moduleParts += advance().lexeme
            }
        }
        expect(";", "expected ';' after import")
        val range = span(start.range, previous().range)
        return SyntaxImport(names, moduleParts.joinToString(""), alias, range, direct(range))
    }

    private fun parseStruct(structKeyword: Token, isPublic: Boolean): SyntaxStruct {
        val name = expectIdentifier("expected structure name")
            ?: syntheticToken("anonymous_struct", structKeyword.range)
        expect("{", "expected '{' after structure name")
        val fields = mutableListOf<SyntaxField>()
        val methods = mutableListOf<SyntaxFunction>()
        while (!atEnd() && !peek().isLexeme("}")) {
            val start = peek()
            val type = parseType()
            if (type == null) {
                recoverTo(";", "}")
                match(";")
                continue
            }
            val fieldName = expectIdentifier("expected field name")
            if (fieldName == null) {
                recoverTo(";", "}")
                match(";")
                continue
            }
            if (match("(")) {
                methods += parseFunction(type, fieldName, isMethod = true, ownerName = name.lexeme)
                continue
            }
            val arrayDimensions = parseArrayDimensions()
            expect(";", "expected ';' after field declaration")
            val fieldRange = span(start.range, previous().range)
            fields += SyntaxField(type, fieldName.lexeme, fieldRange, direct(fieldRange), arrayDimensions)
        }
        val close = expect("}", "expected '}' after structure body") ?: previous()
        expect(";", "expected ';' after structure declaration")
        val structureRange = span(structKeyword.range, previous().range)
        return SyntaxStruct(name.lexeme, fields, methods, structureRange, direct(structureRange), isPublic)
    }

    private fun parseUnion(unionKeyword: Token, isPublic: Boolean): SyntaxUnion {
        val name = expectIdentifier("expected union name") ?: syntheticToken("anonymous_union", unionKeyword.range)
        expect("{", "expected '{' after union name")
        val fields = mutableListOf<SyntaxField>()
        while (!atEnd() && !peek().isLexeme("}")) {
            val start = peek()
            val type = parseType()
            if (type == null) {
                recoverTo(";", "}")
                match(";")
                continue
            }
            val fieldName = expectIdentifier("expected union field name")
            if (fieldName == null) {
                recoverTo(";", "}")
                match(";")
                continue
            }
            val arrayDimensions = parseArrayDimensions()
            expect(";", "expected ';' after union field declaration")
            val fieldRange = span(start.range, previous().range)
            fields += SyntaxField(type, fieldName.lexeme, fieldRange, direct(fieldRange), arrayDimensions)
        }
        val close = expect("}", "expected '}' after union body") ?: previous()
        expect(";", "expected ';' after union declaration")
        val range = span(unionKeyword.range, previous().range)
        return SyntaxUnion(name.lexeme, fields, range, direct(range), isPublic)
    }

    private fun parseEnum(enumKeyword: Token, isPublic: Boolean): SyntaxEnum {
        val name = expectIdentifier("expected enum name") ?: syntheticToken("anonymous_enum", enumKeyword.range)
        expect("{", "expected '{' after enum name")
        val values = mutableListOf<SyntaxEnumValue>()
        while (!atEnd() && !peek().isLexeme("}")) {
            val valueToken = expectIdentifier("expected enum value") ?: break
            val assigned = if (match("=")) {
                val expression = parseExpression()
                expression?.let { enumValueText(it) }
            } else null
            val range = span(valueToken.range, previous().range)
            values += SyntaxEnumValue(valueToken.lexeme, assigned, range, direct(range))
            if (!match(",") && !peek().isLexeme("}")) {
                diagnostics.error("expected ',' between enum values", peek().range, "PARSE204")
                recoverTo(",", "}")
                match(",")
            }
        }
        expect("}", "expected '}' after enum body")
        expect(";", "expected ';' after enum declaration")
        val range = span(enumKeyword.range, previous().range)
        return SyntaxEnum(name.lexeme, values, range, direct(range), isPublic)
    }

    private fun enumValueText(expression: SyntaxExpression): String? = when (expression) {
        is SyntaxIntegerLiteral -> expression.text
        is SyntaxUnary -> enumValueText(expression.operand)?.let { expression.operator + it }
        else -> null
    }

    private fun parseFunction(
        returnType: TypeSyntax,
        name: Token,
        isMethod: Boolean = false,
        ownerName: String? = null,
        isPublic: Boolean = false
    ): SyntaxFunction {
        val parameters = mutableListOf<SyntaxParameter>()
        if (!peek().isLexeme(")")) {
            if (peek().isLexeme("void") && peek(1).isLexeme(")")) {
                advance()
            } else {
                do {
                    if (isMethod && peek().isLexeme("self")) {
                        val receiver = advance()
                        parameters += SyntaxParameter(
                            TypeSyntax("self", false, 0, receiver.range, direct(receiver.range)),
                            receiver.lexeme,
                            true,
                            receiver.range,
                            direct(receiver.range)
                        )
                        continue
                    }
                    val type = parseType()
                    if (type == null) {
                        recoverTo(",", ")")
                        if (match(",")) continue
                        break
                    }
                    val parameterName = expectIdentifier("expected parameter name")
                        ?: syntheticToken("parameter", type.range)
                    val arrayDimensions = parseArrayDimensions()
                    val parameterRange = span(type.range, parameterName.range)
                    parameters += SyntaxParameter(type, parameterName.lexeme, false, parameterRange, direct(parameterRange), arrayDimensions)
                } while (match(","))
            }
        }
        expect(")", "expected ')' after parameter list")
        val body = if (peek().isLexeme("{")) parseStatement() else {
            expect(";", "expected function body or declaration terminator")
            null
        }
        val functionRange = span(returnType.range, body?.range ?: previousSyntaxToken().range)
        return SyntaxFunction(
            returnType,
            name.lexeme,
            parameters,
            body,
            isMethod,
            ownerName,
            functionRange,
            direct(functionRange),
            isPublic
        )
    }

    private fun parseStatement(): SyntaxStatement? {
        if (match("{")) {
            val open = previous()
            val statements = mutableListOf<SyntaxStatement>()
            while (!atEnd() && !peek().isLexeme("}")) {
                val before = index
                parseStatement()?.let(statements::add)
                if (index == before) advance()
            }
            val close = expect("}", "expected '}' after block") ?: previous()
            val range = span(open.range, close.range)
            return SyntaxBlock(statements, range, direct(range))
        }
        if (match("return")) {
            val start = previous()
            val expression = if (peek().isLexeme(";")) null else parseExpression()
            expect(";", "expected ';' after return statement")
            val range = span(start.range, previous().range)
            return SyntaxReturn(expression, range, direct(range))
        }
        if (match("defer")) {
            val start = previous()
            val expression = parseExpression()
            if (expression == null) return null
            expect(";", "expected ';' after defer statement")
            val range = span(start.range, previous().range)
            return SyntaxDefer(expression, range, direct(range))
        }
        if (match("if")) {
            val start = previous()
            expect("(", "expected '(' after if")
            val condition = parseExpression() ?: SyntaxErrorExpression(start.range, direct(start.range))
            expect(")", "expected ')' after if condition")
            val thenBranch = parseStatement() ?: SyntaxBlock(emptyList(), start.range, direct(start.range))
            val elseBranch = if (match("else")) parseStatement() else null
            val end = elseBranch?.range ?: thenBranch.range
            val range = span(start.range, end)
            return SyntaxIf(condition, thenBranch, elseBranch, range, direct(range))
        }
        if (match("while")) {
            val start = previous()
            expect("(", "expected '(' after while")
            val condition = parseExpression() ?: SyntaxErrorExpression(start.range, direct(start.range))
            expect(")", "expected ')' after while condition")
            val body = parseStatement() ?: SyntaxBlock(emptyList(), start.range, direct(start.range))
            val range = span(start.range, body.range)
            return SyntaxWhile(condition, body, range, direct(range))
        }
        if (match("for")) {
            val start = previous()
            expect("(", "expected '(' after for")
            val initializer = if (match(";")) {
                null
            } else if (looksLikeVariableDeclaration()) {
                parseVariableDeclaration()
            } else {
                val expression = parseExpression()
                expect(";", "expected ';' after for initializer")
                expression?.let {
                    val range = span(it.range, previous().range)
                    SyntaxExpressionStatement(it, range, direct(range))
                }
            }
            val condition = if (peek().isLexeme(";")) null else parseExpression()
            expect(";", "expected ';' after for condition")
            val increment = if (peek().isLexeme(")")) null else parseExpression()
            expect(")", "expected ')' after for clauses")
            val body = parseStatement() ?: SyntaxBlock(emptyList(), start.range, direct(start.range))
            val range = span(start.range, body.range)
            return SyntaxFor(initializer, condition, increment, body, range, direct(range))
        }
        if (match("break")) {
            val start = previous()
            expect(";", "expected ';' after break")
            val range = span(start.range, previous().range)
            return SyntaxBreak(range, direct(range))
        }
        if (match("continue")) {
            val start = previous()
            expect(";", "expected ';' after continue")
            val range = span(start.range, previous().range)
            return SyntaxContinue(range, direct(range))
        }
        if (looksLikeVariableDeclaration()) {
            return parseVariableDeclaration()
        }
        val expression = parseExpression() ?: return null
        expect(";", "expected ';' after expression")
        val range = span(expression.range, previous().range)
        return SyntaxExpressionStatement(expression, range, direct(range))
    }

    private fun parseVariableDeclaration(): SyntaxVariableDeclaration? {
        val type = parseType() ?: return null
        val name = expectIdentifier("expected local variable name") ?: return null
        val arrayDimensions = parseArrayDimensions()
        val initializer = if (match("=")) parseExpression() else null
        expect(";", "expected ';' after local declaration")
        val range = span(type.range, previous().range)
        return SyntaxVariableDeclaration(type, name.lexeme, initializer, range, direct(range), arrayDimensions)
    }

    private fun parseArrayDimensions(): List<String> {
        val dimensions = mutableListOf<String>()
        while (match("[")) {
            val parts = mutableListOf<String>()
            while (!atEnd() && !peek().isLexeme("]")) parts += advance().lexeme
            expect("]", "expected ']' after array dimension")
            dimensions += parts.joinToString("")
        }
        return dimensions
    }

    private fun parseExpression(minPrecedence: Int = 0): SyntaxExpression? {
        var left = parsePrefix() ?: return null
        while (true) {
            if (peek().isLexeme("?") && 2 >= minPrecedence) {
                val question = advance()
                val thenBranch = parseExpression() ?: run {
                    diagnostics.error("expected expression after '?'", question.range, "PARSE406")
                    SyntaxErrorExpression(question.range, direct(question.range))
                }
                expect(":", "expected ':' in conditional expression")
                val elseBranch = parseExpression(2) ?: run {
                    diagnostics.error("expected expression after ':'", peek().range, "PARSE407")
                    SyntaxErrorExpression(previous().range, direct(previous().range))
                }
                val range = span(left.range, elseBranch.range)
                left = SyntaxConditional(left, thenBranch, elseBranch, range, direct(range))
                continue
            }
            val operator = peek().lexeme
            val precedence = binaryPrecedence(operator)
            if (precedence < minPrecedence) break
            advance()
            val right = parseExpression(if (operator in assignmentOperators) precedence else precedence + 1)
            if (right == null) {
                diagnostics.error("expected expression after '$operator'", peek().range, "PARSE401")
                break
            }
            val range = span(left.range, right.range)
            left = SyntaxBinary(left, operator, right, range, direct(range))
        }
        return left
    }

    private fun parsePrefix(): SyntaxExpression? {
        val token = peek()
        var expression = when {
            match("sizeof") -> {
                val keyword = previous()
                val operand = if (match("(")) {
                    val value = parseExpression() ?: SyntaxErrorExpression(keyword.range, direct(keyword.range))
                    expect(")", "expected ')' after sizeof operand")
                    value
                } else {
                    parsePrefix() ?: SyntaxErrorExpression(keyword.range, direct(keyword.range))
                }
                SyntaxSizeOf(operand, span(keyword.range, operand.range), direct(span(keyword.range, operand.range)))
            }
            looksLikeCast() -> {
                val open = expect("(", "expected '(' before cast type") ?: token
                val target = parseType() ?: return null
                expect(")", "expected ')' after cast type")
                val operand = parsePrefix() ?: return null
                SyntaxCast(target, operand, span(open.range, operand.range), direct(span(open.range, operand.range)))
            }
            match("++") || match("--") -> {
                val operator = previous()
                val operand = parsePrefix() ?: return null
                SyntaxUpdate(operand, operator.lexeme, true, span(operator.range, operand.range), direct(span(operator.range, operand.range)))
            }
            match("-") || match("!") || match("~") || match("&") || match("*") -> {
                val operator = previous()
                val operand = parsePrefix() ?: return null
                SyntaxUnary(operator.lexeme, operand, span(operator.range, operand.range), direct(span(operator.range, operand.range)))
            }
            matchKind(TokenKind.INTEGER_LITERAL) -> SyntaxIntegerLiteral(token.lexeme, token.range, direct(token.range))
            matchKind(TokenKind.FLOAT_LITERAL) -> SyntaxFloatLiteral(token.lexeme, token.range, direct(token.range))
            matchKind(TokenKind.STRING_LITERAL) -> parseStringLiteral(token)
            matchKind(TokenKind.CHARACTER_LITERAL) -> SyntaxCharacterLiteral(token.lexeme, token.range, direct(token.range))
            matchKind(TokenKind.IDENTIFIER) || matchKind(TokenKind.KEYWORD) -> SyntaxIdentifier(token.lexeme, token.range, direct(token.range))
            match("(") -> {
                val inner = parseExpression()
                expect(")", "expected ')' after expression")
                if (inner == null) SyntaxErrorExpression(token.range, direct(token.range))
                else SyntaxParenthesized(inner, span(token.range, previous().range), direct(span(token.range, previous().range)))
            }
            else -> {
                diagnostics.error("expected expression", token.range, "PARSE402")
                return null
            }
        }

        while (true) {
            expression = when {
                match("(") -> {
                    val arguments = mutableListOf<SyntaxExpression>()
                    if (!peek().isLexeme(")")) {
                        do {
                            parseExpression()?.let(arguments::add)
                        } while (match(","))
                    }
                    val close = expect(")", "expected ')' after call arguments") ?: previous()
                    SyntaxCall(expression, arguments, span(expression.range, close.range), direct(span(expression.range, close.range)))
                }
                match(".") -> {
                    val member = expectIdentifier("expected member name after '.'") ?: return expression
                    SyntaxMemberAccess(expression, member.lexeme, span(expression.range, member.range), direct(span(expression.range, member.range)))
                }
                match("[") -> {
                    val index = parseExpression() ?: SyntaxErrorExpression(previous().range, direct(previous().range))
                    val close = expect("]", "expected ']' after index expression") ?: previous()
                    SyntaxIndexAccess(expression, index, span(expression.range, close.range), direct(span(expression.range, close.range)))
                }
                match("++") || match("--") -> {
                    val operator = previous()
                    SyntaxUpdate(expression, operator.lexeme, false, span(expression.range, operator.range), direct(span(expression.range, operator.range)))
                }
                else -> return expression
            }
        }
    }

    private fun parseStringLiteral(token: Token): SyntaxExpression {
        val content = token.lexeme.removePrefix("\"").removeSuffix("\"")
        val marker = "${'$'}{"
        if (marker !in content) return SyntaxStringLiteral(token.lexeme, token.range, direct(token.range))

        val parts = mutableListOf<SyntaxStringTemplatePart>()
        var cursor = 0
        while (cursor < content.length) {
            val open = content.indexOf(marker, cursor)
            if (open < 0) {
                if (cursor < content.length) parts += SyntaxStringTextPart(content.substring(cursor))
                break
            }
            if (open > cursor) parts += SyntaxStringTextPart(content.substring(cursor, open))
            val close = content.indexOf('}', open + marker.length)
            if (close < 0) {
                diagnostics.error("unterminated string interpolation", token.range, "PARSE404")
                parts += SyntaxStringTextPart(content.substring(open))
                break
            }
            val expressionText = content.substring(open + marker.length, close).trim()
            if (expressionText.isEmpty()) {
                diagnostics.error("string interpolation requires an expression", token.range, "PARSE405")
            } else {
                val expressionStart = token.range.startOffset + 1 + open + marker.length
                val padded = " ".repeat(expressionStart) + expressionText
                val nestedSource = lexed.source.copy(text = padded)
                val nested = Parser(Lexer().lex(nestedSource)).parseExpressionFragment()
                diagnostics.addAll(nested.diagnostics.filter { it !in lexed.diagnostics })
                nested.expression?.let { parts += SyntaxStringExpressionPart(it) }
            }
            cursor = close + 1
        }
        return SyntaxStringTemplate(parts, token.range, direct(token.range))
    }

    private fun parseType(): TypeSyntax? {
        val start = peek()
        val declarationKind = when {
            match("struct") -> "struct"
            match("union") -> "union"
            match("enum") -> "enum"
            else -> "named"
        }
        val isStruct = declarationKind == "struct"
        val name = if (declarationKind != "named") {
            expectIdentifier("expected structure type name")
        } else if (peek().kind == TokenKind.KEYWORD || peek().kind == TokenKind.IDENTIFIER) {
            advance()
        } else {
            diagnostics.error("expected type name", peek().range, "PARSE101")
            return null
        }
        var pointers = 0
        while (match("*")) pointers++
        val range = span(start.range, previous().range)
        return TypeSyntax(name?.lexeme ?: "<error>", isStruct, pointers, range, direct(range), declarationKind)
    }

    private fun looksLikeVariableDeclaration(): Boolean {
        if (peek().isLexeme("struct") || peek().isLexeme("union") || peek().isLexeme("enum")) return true
        if (peek().lexeme in primitiveTypes) return true
        return peek().kind == TokenKind.IDENTIFIER && peek(1).kind == TokenKind.IDENTIFIER
    }

    private fun looksLikeCast(): Boolean = peek().isLexeme("(") &&
        (peek(1).lexeme in primitiveTypes || peek(1).isLexeme("struct") || peek(1).isLexeme("union") || peek(1).isLexeme("enum"))

    private fun binaryPrecedence(operator: String): Int = when (operator) {
        "=", "+=", "-=", "*=", "/=", "%=" -> 1
        "||" -> 2
        "&&" -> 3
        "==", "!=" -> 4
        "<", ">", "<=", ">=" -> 5
        "+", "-" -> 10
        "|" -> 6
        "^" -> 7
        "&" -> 8
        "<<", ">>" -> 9
        "*", "/", "%" -> 20
        else -> -1
    }

    private val assignmentOperators = setOf("=", "+=", "-=", "*=", "/=", "%=")

    private fun recoverDeclaration(): SyntaxDeclaration? {
        recoverTo(";")
        match(";")
        return null
    }

    private fun recoverTo(vararg lexemes: String) {
        while (!atEnd() && peek().lexeme !in lexemes) advance()
    }

    private fun expectIdentifier(message: String): Token? {
        if (peek().kind == TokenKind.IDENTIFIER) return advance()
        diagnostics.error(message, peek().range, "PARSE100")
        return null
    }

    private fun expect(lexeme: String, message: String): Token? {
        if (peek().isLexeme(lexeme)) return advance()
        diagnostics.error(message, peek().range, "PARSE001")
        return null
    }

    private fun match(lexeme: String): Boolean = if (peek().isLexeme(lexeme)) {
        advance()
        true
    } else false

    private fun matchKind(kind: TokenKind): Boolean = if (peek().kind == kind) {
        advance()
        true
    } else false

    private fun peek(offset: Int = 0): Token {
        val position = (index + offset).coerceIn(0, tokens.lastIndex)
        return tokens[position]
    }

    private fun previous(): Token = tokens[(index - 1).coerceAtLeast(0)]

    private fun previousSyntaxToken(): Token = previous()

    private fun advance(): Token = tokens[index++]

    private fun atEnd(): Boolean = peek().kind == TokenKind.END_OF_FILE

    private fun syntheticToken(text: String, range: SourceRange): Token = Token(TokenKind.IDENTIFIER, text, range)

    private fun span(first: SourceRange, last: SourceRange): SourceRange = SourceRange(
        first.file,
        minOf(first.startOffset, last.startOffset),
        maxOf(first.endOffset, last.endOffset)
    )

    private fun direct(range: SourceRange): Origin = Origin.Direct(range)

    data class ParsedSource(
        val syntax: SyntaxProgram,
        val diagnostics: List<Diagnostic>
    )

    companion object {
        private val primitiveTypes = setOf(
            "void", "bool", "char", "short", "int", "long", "float", "double", "signed", "unsigned"
        )
    }
}
