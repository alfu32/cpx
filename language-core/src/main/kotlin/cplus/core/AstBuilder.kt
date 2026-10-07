package cplus.core

class AstBuilder {
    fun build(syntax: SyntaxProgram): AstProgram = AstProgram(
        syntax.declarations.map(::declaration),
        syntax.origin
    )

    private fun declaration(node: SyntaxDeclaration): AstDeclaration = when (node) {
        is SyntaxStruct -> AstStruct(
            node.name,
            node.fields.map(::field),
            node.methods.map { function(it, node.name) },
            node.origin
        )
        is SyntaxGlobalVariable -> AstGlobalVariable(type(node.type), node.name, node.initializer?.let(::expression), node.origin)
        is SyntaxComptimeFunction -> AstComptimeFunction(node.name, node.category, node.parameters.map { it.name }, node.template, node.origin)
        is SyntaxCpxInvocation -> AstCpxInvocation(node.name, node.arguments, node.origin)
        is SyntaxFunction -> function(node, node.ownerName)
    }

    private fun function(node: SyntaxFunction, ownerName: String?): AstFunction = AstFunction(
        type(node.returnType),
        node.name,
        node.parameters.map { AstParameter(type(it.type), it.name, it.isReceiver, it.origin) },
        node.body?.let(::statement),
        node.isMethod,
        ownerName,
        node.origin
    )

    private fun field(node: SyntaxField): AstField = AstField(type(node.type), node.name, node.origin)

    private fun type(node: TypeSyntax): AstTypeRef = AstTypeRef(node.name, node.isStruct, node.pointerDepth, node.origin)

    private fun statement(node: SyntaxStatement): AstStatement = when (node) {
        is SyntaxBlock -> AstBlock(node.statements.map(::statement), node.origin)
        is SyntaxReturn -> AstReturn(node.expression?.let(::expression), node.origin)
        is SyntaxExpressionStatement -> AstExpressionStatement(expression(node.expression), node.origin)
        is SyntaxVariableDeclaration -> AstVariableDeclaration(type(node.type), node.name, node.initializer?.let(::expression), node.origin)
    }

    private fun expression(node: SyntaxExpression): AstExpression = when (node) {
        is SyntaxIntegerLiteral -> AstIntegerLiteral(node.text, node.origin)
        is SyntaxStringLiteral -> AstStringLiteral(node.text, node.origin)
        is SyntaxCharacterLiteral -> AstCharacterLiteral(node.text, node.origin)
        is SyntaxIdentifier -> AstIdentifier(node.name, node.origin)
        is SyntaxUnary -> AstUnary(node.operator, expression(node.operand), node.origin)
        is SyntaxBinary -> AstBinary(expression(node.left), node.operator, expression(node.right), node.origin)
        is SyntaxCall -> AstCall(expression(node.callee), node.arguments.map(::expression), node.origin)
        is SyntaxMemberAccess -> AstMemberAccess(expression(node.receiver), node.member, node.origin)
        is SyntaxParenthesized -> AstParenthesized(expression(node.expression), node.origin)
        is SyntaxErrorExpression -> AstErrorExpression(node.origin)
    }
}
