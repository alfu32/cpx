package cplus.core

class AstBuilder {
    fun build(syntax: SyntaxProgram): AstProgram = AstProgram(
        syntax.declarations.map(::declaration),
        syntax.origin
    )

    fun buildDeclaration(syntax: SyntaxDeclaration): AstDeclaration = declaration(syntax)

    fun buildStatement(syntax: SyntaxStatement): AstStatement = statement(syntax)

    fun buildExpression(syntax: SyntaxExpression): AstExpression = expression(syntax)

    private fun declaration(node: SyntaxDeclaration): AstDeclaration = when (node) {
        is SyntaxPackage -> AstPackage(node.name, node.origin)
        is SyntaxAlias -> AstAlias(type(node.target), node.name, node.arrayDimensions, node.origin, node.isPublic)
        is SyntaxUnion -> AstUnion(node.name, node.fields.map(::field), node.origin, node.isPublic)
        is SyntaxEnum -> AstEnum(
            node.name,
            node.values.map { AstEnumValue(it.name, it.value, it.origin) },
            node.origin,
            node.isPublic
        )
        is SyntaxStruct -> AstStruct(
            node.name,
            node.fields.map(::field),
            node.methods.map { function(it, node.name) },
            node.origin,
            node.isPublic
        )
        is SyntaxTrait -> AstTrait(
            node.targetName,
            node.targetOrigin,
            node.methods.map { function(it, node.targetName) },
            node.origin,
            node.isPublic
        )
        is SyntaxGlobalVariable -> AstGlobalVariable(
            type(node.type),
            node.name,
            node.initializer?.let(::expression),
            node.origin,
            node.arrayDimensions,
            node.isPublic,
            node.threadLocal
        )
        is SyntaxComptimeFunction -> AstComptimeFunction(
            node.name,
            node.category,
            node.parameters.map { it.name },
            node.template,
            node.origin,
            node.isPublic,
            node.parameters.map { it.kind }
        )
        is SyntaxCpxInvocation -> AstCpxInvocation(node.name, node.arguments, node.origin, node.isPublic)
        is SyntaxImport -> AstImport(node.names, node.module, node.alias, node.nameAliases, node.origin, node.isPublic)
        is SyntaxFunction -> function(node, node.ownerName)
    }

    private fun function(node: SyntaxFunction, ownerName: String?): AstFunction = AstFunction(
        type(node.returnType),
        node.name,
        node.parameters.map {
            AstParameter(type(it.type), it.name, it.isReceiver, it.origin, it.arrayDimensions, it.isPointerReceiver)
        },
        node.body?.let(::statement),
        node.isMethod,
        ownerName,
        node.origin,
        node.isPublic,
        node.attributes,
        node.isVariadic
    )

    private fun field(node: SyntaxField): AstField = AstField(type(node.type), node.name, node.origin, node.arrayDimensions)

    private fun type(node: TypeSyntax): AstTypeRef = AstTypeRef(
        node.name,
        node.isStruct,
        node.pointerDepth,
        node.origin,
        node.declarationKind,
        node.qualifiers,
        node.pointerQualifiers,
        node.functionParameters?.map {
            AstParameter(
                type(it.type),
                it.name,
                it.isReceiver,
                it.origin,
                it.arrayDimensions,
                it.isPointerReceiver
            )
        },
        node.functionVariadic,
        node.functionPointerDepth,
        node.functionPointerQualifiers
    )

    private fun statement(node: SyntaxStatement): AstStatement = when (node) {
        is SyntaxBlock -> AstBlock(node.statements.map(::statement), node.origin)
        is SyntaxReturn -> AstReturn(node.expression?.let(::expression), node.origin)
        is SyntaxExpressionStatement -> AstExpressionStatement(expression(node.expression), node.origin)
        is SyntaxDefer -> AstDefer(expression(node.expression), node.origin)
        is SyntaxIf -> AstIf(
            expression(node.condition),
            statement(node.thenBranch),
            node.elseBranch?.let(::statement),
            node.origin
        )
        is SyntaxWhile -> AstWhile(expression(node.condition), statement(node.body), node.origin)
        is SyntaxFor -> AstFor(
            node.initializer?.let(::statement),
            node.condition?.let(::expression),
            node.increment?.let(::expression),
            statement(node.body),
            node.origin
        )
        is SyntaxBreak -> AstBreak(node.origin)
        is SyntaxContinue -> AstContinue(node.origin)
            is SyntaxVariableDeclaration -> AstVariableDeclaration(type(node.type), node.name, node.initializer?.let(::expression), node.origin, node.arrayDimensions)
            is SyntaxInnerFunction -> AstInnerFunction(
                function(
                SyntaxFunction(
                    node.returnType,
                    node.name,
                    node.parameters,
                    node.body,
                    range = node.range,
                    origin = node.origin,
                    isVariadic = node.isVariadic
                ),
                    null
                ),
                node.origin
            )
    }

    private fun expression(node: SyntaxExpression): AstExpression = when (node) {
        is SyntaxIntegerLiteral -> AstIntegerLiteral(node.text, node.origin)
        is SyntaxBooleanLiteral -> AstBooleanLiteral(node.text, node.origin)
        is SyntaxFloatLiteral -> AstFloatLiteral(node.text, node.origin)
        is SyntaxStringLiteral -> AstStringLiteral(node.text, node.origin)
        is SyntaxStringTemplate -> AstStringTemplate(
            node.parts.map { part ->
                when (part) {
                    is SyntaxStringTextPart -> AstStringTextPart(part.text)
                    is SyntaxStringExpressionPart -> AstStringExpressionPart(expression(part.expression))
                }
            },
            node.origin
        )
        is SyntaxCharacterLiteral -> AstCharacterLiteral(node.text, node.origin)
        is SyntaxIdentifier -> AstIdentifier(node.name, node.origin)
        is SyntaxUnary -> AstUnary(node.operator, expression(node.operand), node.origin)
        is SyntaxBinary -> AstBinary(expression(node.left), node.operator, expression(node.right), node.origin)
        is SyntaxConditional -> AstConditional(
            expression(node.condition),
            expression(node.thenBranch),
            expression(node.elseBranch),
            node.origin
        )
        is SyntaxUpdate -> AstUpdate(expression(node.operand), node.operator, node.prefix, node.origin)
        is SyntaxSizeOf -> AstSizeOf(node.operand?.let(::expression), node.targetType?.let(::type), node.origin)
        is SyntaxAbiQuery -> AstAbiQuery(
            node.query,
            node.operand?.let(::expression),
            node.targetType?.let(::type),
            node.fieldName,
            node.origin
        )
        is SyntaxCast -> AstCast(type(node.target), expression(node.operand), node.origin)
        is SyntaxCall -> AstCall(expression(node.callee), node.arguments.map(::expression), node.origin)
        is SyntaxMemberAccess -> AstMemberAccess(expression(node.receiver), node.member, node.origin)
        is SyntaxIndexAccess -> AstIndexAccess(expression(node.receiver), expression(node.index), node.origin)
        is SyntaxParenthesized -> AstParenthesized(expression(node.expression), node.origin)
        is SyntaxErrorExpression -> AstErrorExpression(node.origin)
    }
}
