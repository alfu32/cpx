package cplus.core

sealed interface SyntaxNode {
    val range: SourceRange
    val origin: Origin
}

data class SyntaxProgram(
    val declarations: List<SyntaxDeclaration>,
    override val range: SourceRange,
    override val origin: Origin
) : SyntaxNode

sealed interface SyntaxDeclaration : SyntaxNode

data class TypeSyntax(
    val name: String,
    val isStruct: Boolean,
    val pointerDepth: Int,
    override val range: SourceRange,
    override val origin: Origin
) : SyntaxNode

data class SyntaxStruct(
    val name: String,
    val fields: List<SyntaxField>,
    val methods: List<SyntaxFunction> = emptyList(),
    override val range: SourceRange,
    override val origin: Origin
) : SyntaxDeclaration

data class SyntaxField(
    val type: TypeSyntax,
    val name: String,
    override val range: SourceRange,
    override val origin: Origin
) : SyntaxNode

data class SyntaxGlobalVariable(
    val type: TypeSyntax,
    val name: String,
    val initializer: SyntaxExpression?,
    override val range: SourceRange,
    override val origin: Origin
) : SyntaxDeclaration

data class SyntaxFunction(
    val returnType: TypeSyntax,
    val name: String,
    val parameters: List<SyntaxParameter>,
    val body: SyntaxStatement?,
    val isMethod: Boolean = false,
    val ownerName: String? = null,
    override val range: SourceRange,
    override val origin: Origin
) : SyntaxDeclaration

data class SyntaxParameter(
    val type: TypeSyntax,
    val name: String,
    val isReceiver: Boolean = false,
    override val range: SourceRange,
    override val origin: Origin
) : SyntaxNode

sealed interface SyntaxStatement : SyntaxNode

data class SyntaxBlock(
    val statements: List<SyntaxStatement>,
    override val range: SourceRange,
    override val origin: Origin
) : SyntaxStatement

data class SyntaxReturn(
    val expression: SyntaxExpression?,
    override val range: SourceRange,
    override val origin: Origin
) : SyntaxStatement

data class SyntaxExpressionStatement(
    val expression: SyntaxExpression,
    override val range: SourceRange,
    override val origin: Origin
) : SyntaxStatement

data class SyntaxVariableDeclaration(
    val type: TypeSyntax,
    val name: String,
    val initializer: SyntaxExpression?,
    override val range: SourceRange,
    override val origin: Origin
) : SyntaxStatement

sealed interface SyntaxExpression : SyntaxNode

data class SyntaxIntegerLiteral(
    val text: String,
    override val range: SourceRange,
    override val origin: Origin
) : SyntaxExpression

data class SyntaxStringLiteral(
    val text: String,
    override val range: SourceRange,
    override val origin: Origin
) : SyntaxExpression

data class SyntaxCharacterLiteral(
    val text: String,
    override val range: SourceRange,
    override val origin: Origin
) : SyntaxExpression

data class SyntaxIdentifier(
    val name: String,
    override val range: SourceRange,
    override val origin: Origin
) : SyntaxExpression

data class SyntaxUnary(
    val operator: String,
    val operand: SyntaxExpression,
    override val range: SourceRange,
    override val origin: Origin
) : SyntaxExpression

data class SyntaxBinary(
    val left: SyntaxExpression,
    val operator: String,
    val right: SyntaxExpression,
    override val range: SourceRange,
    override val origin: Origin
) : SyntaxExpression

data class SyntaxCall(
    val callee: SyntaxExpression,
    val arguments: List<SyntaxExpression>,
    override val range: SourceRange,
    override val origin: Origin
) : SyntaxExpression

data class SyntaxMemberAccess(
    val receiver: SyntaxExpression,
    val member: String,
    override val range: SourceRange,
    override val origin: Origin
) : SyntaxExpression

data class SyntaxParenthesized(
    val expression: SyntaxExpression,
    override val range: SourceRange,
    override val origin: Origin
) : SyntaxExpression

data class SyntaxErrorExpression(
    override val range: SourceRange,
    override val origin: Origin
) : SyntaxExpression
