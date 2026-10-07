package cplus.core

class Parser(private val lexed: LexedSource) {
    private val tokens = lexed.tokens
    private var index = 0
    private val diagnostics = DiagnosticBag()

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
        while (peek().lexeme in setOf("pub", "static", "extern", "inline")) advance()
        if (match("struct") && peek(1).isLexeme("{")) {
            return parseStruct(peek(-1))
        }
        if (peek().isLexeme("struct") && peek(2).isLexeme("{")) {
            val structKeyword = advance()
            return parseStruct(structKeyword)
        }

        val type = parseType() ?: return recoverDeclaration()
        val name = expectIdentifier("expected declaration name") ?: return recoverDeclaration()
        return if (match("(")) {
            parseFunction(type, name)
        } else {
            val initializer = if (match("=")) parseExpression() else null
            expect(";", "expected ';' after global declaration")
            SyntaxGlobalVariable(type, name.lexeme, initializer, span(type.range, previous().range), direct(span(type.range, previous().range)))
        }
    }

    private fun parseStruct(structKeyword: Token): SyntaxStruct {
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
            if (match("[")) {
                diagnostics.error("array fields are not supported in the initial vertical slice", previous().range, "PARSE201")
                recoverTo("]", ";")
                match("]")
            }
            expect(";", "expected ';' after field declaration")
            val fieldRange = span(start.range, previous().range)
            fields += SyntaxField(type, fieldName.lexeme, fieldRange, direct(fieldRange))
        }
        val close = expect("}", "expected '}' after structure body") ?: previous()
        expect(";", "expected ';' after structure declaration")
        val structureRange = span(structKeyword.range, previous().range)
        return SyntaxStruct(name.lexeme, fields, methods, structureRange, direct(structureRange))
    }

    private fun parseFunction(
        returnType: TypeSyntax,
        name: Token,
        isMethod: Boolean = false,
        ownerName: String? = null
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
                    val parameterRange = span(type.range, parameterName.range)
                    parameters += SyntaxParameter(type, parameterName.lexeme, false, parameterRange, direct(parameterRange))
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
            direct(functionRange)
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
        if (looksLikeVariableDeclaration()) {
            val type = parseType() ?: return null
            val name = expectIdentifier("expected local variable name") ?: return null
            val initializer = if (match("=")) parseExpression() else null
            expect(";", "expected ';' after local declaration")
            val range = span(type.range, previous().range)
            return SyntaxVariableDeclaration(type, name.lexeme, initializer, range, direct(range))
        }
        if (peek().isLexeme("if") || peek().isLexeme("while") || peek().isLexeme("for")) {
            diagnostics.error("control-flow statements are not implemented in the initial vertical slice", peek().range, "PARSE301")
            recoverTo(";", "}")
            match(";")
            return null
        }
        val expression = parseExpression() ?: return null
        expect(";", "expected ';' after expression")
        val range = span(expression.range, previous().range)
        return SyntaxExpressionStatement(expression, range, direct(range))
    }

    private fun parseExpression(minPrecedence: Int = 0): SyntaxExpression? {
        var left = parsePrefix() ?: return null
        while (true) {
            val operator = peek().lexeme
            val precedence = binaryPrecedence(operator)
            if (precedence < minPrecedence) break
            advance()
            val right = parseExpression(if (operator == "=") precedence else precedence + 1)
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
            match("-") || match("!") || match("~") || match("&") || match("*") -> {
                val operator = previous()
                val operand = parsePrefix() ?: return null
                SyntaxUnary(operator.lexeme, operand, span(operator.range, operand.range), direct(span(operator.range, operand.range)))
            }
            matchKind(TokenKind.INTEGER_LITERAL) -> SyntaxIntegerLiteral(token.lexeme, token.range, direct(token.range))
            matchKind(TokenKind.STRING_LITERAL) -> SyntaxStringLiteral(token.lexeme, token.range, direct(token.range))
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
                else -> return expression
            }
        }
    }

    private fun parseType(): TypeSyntax? {
        val start = peek()
        val isStruct = match("struct")
        val name = if (isStruct) {
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
        return TypeSyntax(name?.lexeme ?: "<error>", isStruct, pointers, range, direct(range))
    }

    private fun looksLikeVariableDeclaration(): Boolean {
        if (peek().isLexeme("struct")) return true
        if (peek().lexeme in primitiveTypes) return true
        return peek().kind == TokenKind.IDENTIFIER && peek(1).kind == TokenKind.IDENTIFIER
    }

    private fun binaryPrecedence(operator: String): Int = when (operator) {
        "=" -> 1
        "||" -> 2
        "&&" -> 3
        "==", "!=" -> 4
        "<", ">", "<=", ">=" -> 5
        "+", "-" -> 10
        "*", "/", "%" -> 20
        else -> -1
    }

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
