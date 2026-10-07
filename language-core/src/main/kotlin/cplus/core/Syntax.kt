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

sealed interface SyntaxDeclaration : SyntaxNode {
    val isPublic: Boolean
        get() = false
}

data class SyntaxPackage(
    val name: String,
    override val range: SourceRange,
    override val origin: Origin
) : SyntaxDeclaration

data class SyntaxAlias(
    val target: TypeSyntax,
    val name: String,
    val arrayDimensions: List<String> = emptyList(),
    override val range: SourceRange,
    override val origin: Origin,
    override val isPublic: Boolean = false
) : SyntaxDeclaration

data class SyntaxUnion(
    val name: String,
    val fields: List<SyntaxField>,
    override val range: SourceRange,
    override val origin: Origin,
    override val isPublic: Boolean = false
) : SyntaxDeclaration

data class SyntaxEnum(
    val name: String,
    val values: List<SyntaxEnumValue>,
    override val range: SourceRange,
    override val origin: Origin,
    override val isPublic: Boolean = false
) : SyntaxDeclaration

data class SyntaxEnumValue(
    val name: String,
    val value: String?,
    override val range: SourceRange,
    override val origin: Origin
) : SyntaxNode

data class TypeSyntax(
    val name: String,
    val isStruct: Boolean,
    val pointerDepth: Int,
    override val range: SourceRange,
    override val origin: Origin,
    val declarationKind: String = "named"
) : SyntaxNode

data class SyntaxStruct(
    val name: String,
    val fields: List<SyntaxField>,
    val methods: List<SyntaxFunction> = emptyList(),
    override val range: SourceRange,
    override val origin: Origin,
    override val isPublic: Boolean = false
) : SyntaxDeclaration

data class SyntaxField(
    val type: TypeSyntax,
    val name: String,
    override val range: SourceRange,
    override val origin: Origin,
    val arrayDimensions: List<String> = emptyList()
) : SyntaxNode

data class SyntaxGlobalVariable(
    val type: TypeSyntax,
    val name: String,
    val initializer: SyntaxExpression?,
    override val range: SourceRange,
    override val origin: Origin,
    val arrayDimensions: List<String> = emptyList(),
    override val isPublic: Boolean = false
) : SyntaxDeclaration

data class SyntaxComptimeParameter(
    val kind: String,
    val name: String,
    override val range: SourceRange,
    override val origin: Origin
) : SyntaxNode

data class SyntaxComptimeFunction(
    val name: String,
    val category: String,
    val parameters: List<SyntaxComptimeParameter>,
    val template: String,
    override val range: SourceRange,
    override val origin: Origin,
    override val isPublic: Boolean = false
) : SyntaxDeclaration

data class SyntaxCpxInvocation(
    val name: String,
    val arguments: List<String>,
    override val range: SourceRange,
    override val origin: Origin,
    override val isPublic: Boolean = false
) : SyntaxDeclaration

data class SyntaxImport(
    val names: List<String>,
    val module: String,
    val alias: String? = null,
    val nameAliases: Map<String, String> = emptyMap(),
    override val range: SourceRange,
    override val origin: Origin,
    override val isPublic: Boolean = false
) : SyntaxDeclaration

data class SyntaxFunction(
    val returnType: TypeSyntax,
    val name: String,
    val parameters: List<SyntaxParameter>,
    val body: SyntaxStatement?,
    val isMethod: Boolean = false,
    val ownerName: String? = null,
    override val range: SourceRange,
    override val origin: Origin,
    override val isPublic: Boolean = false,
    val attributes: Map<String, String> = emptyMap()
) : SyntaxDeclaration

data class SyntaxParameter(
    val type: TypeSyntax,
    val name: String,
    val isReceiver: Boolean = false,
    override val range: SourceRange,
    override val origin: Origin,
    val arrayDimensions: List<String> = emptyList(),
    val isPointerReceiver: Boolean = false
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

data class SyntaxDefer(
    val expression: SyntaxExpression,
    override val range: SourceRange,
    override val origin: Origin
) : SyntaxStatement

data class SyntaxIf(
    val condition: SyntaxExpression,
    val thenBranch: SyntaxStatement,
    val elseBranch: SyntaxStatement?,
    override val range: SourceRange,
    override val origin: Origin
) : SyntaxStatement

data class SyntaxWhile(
    val condition: SyntaxExpression,
    val body: SyntaxStatement,
    override val range: SourceRange,
    override val origin: Origin
) : SyntaxStatement

data class SyntaxFor(
    val initializer: SyntaxStatement?,
    val condition: SyntaxExpression?,
    val increment: SyntaxExpression?,
    val body: SyntaxStatement,
    override val range: SourceRange,
    override val origin: Origin
) : SyntaxStatement

data class SyntaxBreak(
    override val range: SourceRange,
    override val origin: Origin
) : SyntaxStatement

data class SyntaxContinue(
    override val range: SourceRange,
    override val origin: Origin
) : SyntaxStatement

data class SyntaxVariableDeclaration(
    val type: TypeSyntax,
    val name: String,
    val initializer: SyntaxExpression?,
    override val range: SourceRange,
    override val origin: Origin,
    val arrayDimensions: List<String> = emptyList()
) : SyntaxStatement

data class SyntaxInnerFunction(
    val returnType: TypeSyntax,
    val name: String,
    val parameters: List<SyntaxParameter>,
    val body: SyntaxStatement,
    override val range: SourceRange,
    override val origin: Origin
) : SyntaxStatement

sealed interface SyntaxExpression : SyntaxNode

data class SyntaxIntegerLiteral(
    val text: String,
    override val range: SourceRange,
    override val origin: Origin
) : SyntaxExpression

data class SyntaxBooleanLiteral(
    val text: String,
    override val range: SourceRange,
    override val origin: Origin
) : SyntaxExpression

data class SyntaxFloatLiteral(
    val text: String,
    override val range: SourceRange,
    override val origin: Origin
) : SyntaxExpression

data class SyntaxStringLiteral(
    val text: String,
    override val range: SourceRange,
    override val origin: Origin
) : SyntaxExpression

sealed interface SyntaxStringTemplatePart

data class SyntaxStringTextPart(val text: String) : SyntaxStringTemplatePart

data class SyntaxStringExpressionPart(val expression: SyntaxExpression) : SyntaxStringTemplatePart

data class SyntaxStringTemplate(
    val parts: List<SyntaxStringTemplatePart>,
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

data class SyntaxConditional(
    val condition: SyntaxExpression,
    val thenBranch: SyntaxExpression,
    val elseBranch: SyntaxExpression,
    override val range: SourceRange,
    override val origin: Origin
) : SyntaxExpression

data class SyntaxUpdate(
    val operand: SyntaxExpression,
    val operator: String,
    val prefix: Boolean,
    override val range: SourceRange,
    override val origin: Origin
) : SyntaxExpression

data class SyntaxSizeOf(
    val operand: SyntaxExpression?,
    val targetType: TypeSyntax? = null,
    override val range: SourceRange,
    override val origin: Origin
) : SyntaxExpression

/** Compiler-owned ABI/layout query kept explicit until semantic validation. */
data class SyntaxAbiQuery(
    val query: String,
    val operand: SyntaxExpression? = null,
    val targetType: TypeSyntax? = null,
    val fieldName: String? = null,
    override val range: SourceRange,
    override val origin: Origin
) : SyntaxExpression

data class SyntaxCast(
    val target: TypeSyntax,
    val operand: SyntaxExpression,
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

data class SyntaxIndexAccess(
    val receiver: SyntaxExpression,
    val index: SyntaxExpression,
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
