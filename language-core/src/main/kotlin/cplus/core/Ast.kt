package cplus.core

sealed interface AstNode {
    val origin: Origin
}

data class AstProgram(
    val declarations: List<AstDeclaration>,
    override val origin: Origin,
    val modules: List<AstModule> = emptyList()
) : AstNode

data class AstModule(
    val name: String,
    val declarations: List<AstDeclaration>
)

sealed interface AstDeclaration : AstNode {
    val isPublic: Boolean
        get() = false
}

data class AstPackage(
    val name: String,
    override val origin: Origin
) : AstDeclaration

data class AstAlias(
    val target: AstTypeRef,
    val name: String,
    val arrayDimensions: List<String> = emptyList(),
    override val origin: Origin,
    override val isPublic: Boolean = false
) : AstDeclaration

data class AstUnion(
    val name: String,
    val fields: List<AstField>,
    override val origin: Origin,
    override val isPublic: Boolean = false
) : AstDeclaration

data class AstEnum(
    val name: String,
    val values: List<AstEnumValue>,
    override val origin: Origin,
    override val isPublic: Boolean = false
) : AstDeclaration

data class AstEnumValue(
    val name: String,
    val value: String?,
    override val origin: Origin
) : AstNode

data class AstTypeRef(
    val name: String,
    val isStruct: Boolean,
    val pointerDepth: Int,
    override val origin: Origin,
    val declarationKind: String = "named",
    val qualifiers: Set<String> = emptySet(),
    val pointerQualifiers: List<Set<String>> = emptyList()
) : AstNode

data class AstStruct(
    val name: String,
    val fields: List<AstField>,
    val methods: List<AstFunction> = emptyList(),
    override val origin: Origin,
    override val isPublic: Boolean = false
) : AstDeclaration

data class AstField(
    val type: AstTypeRef,
    val name: String,
    override val origin: Origin,
    val arrayDimensions: List<String> = emptyList()
) : AstNode

data class AstGlobalVariable(
    val type: AstTypeRef,
    val name: String,
    val initializer: AstExpression?,
    override val origin: Origin,
    val arrayDimensions: List<String> = emptyList(),
    override val isPublic: Boolean = false
) : AstDeclaration

data class AstComptimeFunction(
    val name: String,
    val category: String,
    val parameters: List<String>,
    val template: String,
    override val origin: Origin,
    override val isPublic: Boolean = false
) : AstDeclaration

data class AstCpxInvocation(
    val name: String,
    val arguments: List<String>,
    override val origin: Origin,
    override val isPublic: Boolean = false
) : AstDeclaration

data class AstImport(
    val names: List<String>,
    val module: String,
    val alias: String? = null,
    val nameAliases: Map<String, String> = emptyMap(),
    override val origin: Origin,
    override val isPublic: Boolean = false
) : AstDeclaration

data class AstFunction(
    val returnType: AstTypeRef,
    val name: String,
    val parameters: List<AstParameter>,
    val body: AstStatement?,
    val isMethod: Boolean = false,
    val ownerName: String? = null,
    override val origin: Origin,
    override val isPublic: Boolean = false,
    val attributes: Map<String, String> = emptyMap()
) : AstDeclaration

data class AstParameter(
    val type: AstTypeRef,
    val name: String,
    val isReceiver: Boolean = false,
    override val origin: Origin,
    val arrayDimensions: List<String> = emptyList(),
    val isPointerReceiver: Boolean = false
) : AstNode

sealed interface AstStatement : AstNode

data class AstBlock(
    val statements: List<AstStatement>,
    override val origin: Origin
) : AstStatement

data class AstReturn(
    val expression: AstExpression?,
    override val origin: Origin
) : AstStatement

data class AstExpressionStatement(
    val expression: AstExpression,
    override val origin: Origin
) : AstStatement

data class AstDefer(
    val expression: AstExpression,
    override val origin: Origin
) : AstStatement

data class AstIf(
    val condition: AstExpression,
    val thenBranch: AstStatement,
    val elseBranch: AstStatement?,
    override val origin: Origin
) : AstStatement

data class AstWhile(
    val condition: AstExpression,
    val body: AstStatement,
    override val origin: Origin
) : AstStatement

data class AstFor(
    val initializer: AstStatement?,
    val condition: AstExpression?,
    val increment: AstExpression?,
    val body: AstStatement,
    override val origin: Origin
) : AstStatement

data class AstBreak(override val origin: Origin) : AstStatement

data class AstContinue(override val origin: Origin) : AstStatement

data class AstVariableDeclaration(
    val type: AstTypeRef,
    val name: String,
    val initializer: AstExpression?,
    override val origin: Origin,
    val arrayDimensions: List<String> = emptyList()
) : AstStatement

data class AstInnerFunction(
    val function: AstFunction,
    override val origin: Origin
) : AstStatement

sealed interface AstExpression : AstNode

data class AstIntegerLiteral(val text: String, override val origin: Origin) : AstExpression
data class AstBooleanLiteral(val text: String, override val origin: Origin) : AstExpression
data class AstFloatLiteral(val text: String, override val origin: Origin) : AstExpression
data class AstStringLiteral(val text: String, override val origin: Origin) : AstExpression

sealed interface AstStringTemplatePart

data class AstStringTextPart(val text: String) : AstStringTemplatePart

data class AstStringExpressionPart(val expression: AstExpression) : AstStringTemplatePart

data class AstStringTemplate(
    val parts: List<AstStringTemplatePart>,
    override val origin: Origin
) : AstExpression

data class AstCharacterLiteral(val text: String, override val origin: Origin) : AstExpression
data class AstIdentifier(val name: String, override val origin: Origin) : AstExpression
data class AstUnary(val operator: String, val operand: AstExpression, override val origin: Origin) : AstExpression
data class AstBinary(val left: AstExpression, val operator: String, val right: AstExpression, override val origin: Origin) : AstExpression
data class AstConditional(
    val condition: AstExpression,
    val thenBranch: AstExpression,
    val elseBranch: AstExpression,
    override val origin: Origin
) : AstExpression
data class AstUpdate(
    val operand: AstExpression,
    val operator: String,
    val prefix: Boolean,
    override val origin: Origin
) : AstExpression
data class AstSizeOf(val operand: AstExpression?, val targetType: AstTypeRef? = null, override val origin: Origin) : AstExpression
data class AstAbiQuery(
    val query: String,
    val operand: AstExpression? = null,
    val targetType: AstTypeRef? = null,
    val fieldName: String? = null,
    override val origin: Origin
) : AstExpression
data class AstCast(val target: AstTypeRef, val operand: AstExpression, override val origin: Origin) : AstExpression
data class AstCall(val callee: AstExpression, val arguments: List<AstExpression>, override val origin: Origin) : AstExpression
data class AstMemberAccess(val receiver: AstExpression, val member: String, override val origin: Origin) : AstExpression
data class AstIndexAccess(val receiver: AstExpression, val index: AstExpression, override val origin: Origin) : AstExpression
data class AstParenthesized(val expression: AstExpression, override val origin: Origin) : AstExpression
data class AstErrorExpression(override val origin: Origin) : AstExpression
