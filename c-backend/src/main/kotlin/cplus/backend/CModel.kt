package cplus.backend

import cplus.core.Origin

data class CTranslationUnit(
    val includes: List<String>,
    val structs: List<CStructDeclaration>,
    val unions: List<CUnionDeclaration>,
    val enums: List<CEnumDeclaration>,
    val aliases: List<CAliasDeclaration>,
    val globals: List<CGlobalDeclaration>,
    val functions: List<CFunction>
)

data class CStructDeclaration(
    val name: String,
    val fields: List<CField>,
    val origin: Origin
)

data class CField(
    val type: CType,
    val name: String,
    val origin: Origin,
    val arrayDimensions: List<String> = emptyList()
)

data class CUnionDeclaration(
    val name: String,
    val fields: List<CField>,
    val origin: Origin
)

data class CEnumDeclaration(
    val name: String,
    val values: List<CEnumValue>,
    val origin: Origin
)

data class CEnumValue(
    val name: String,
    val value: String?,
    val origin: Origin
)

data class CAliasDeclaration(
    val name: String,
    val target: CType,
    val arrayDimensions: List<String>,
    val origin: Origin
)

data class CGlobalDeclaration(
    val type: CType,
    val name: String,
    val initializer: CExpression?,
    val origin: Origin,
    val arrayDimensions: List<String> = emptyList()
)

data class CFunction(
    val returnType: CType,
    val name: String,
    val parameters: List<CParameter>,
    val body: CStatement?,
    val origin: Origin,
    val isVariadic: Boolean = false
)

data class CParameter(
    val type: CType,
    val name: String,
    val origin: Origin,
    val arrayDimensions: List<String> = emptyList()
)

sealed interface CType {
    fun render(): String

    data class Primitive(val name: String, val pointerDepth: Int = 0) : CType {
        override fun render(): String = name + "*".repeat(pointerDepth)
    }

    data class Struct(val name: String, val pointerDepth: Int = 0) : CType {
        override fun render(): String = "struct $name" + "*".repeat(pointerDepth)
    }

    data class Union(val name: String, val pointerDepth: Int = 0) : CType {
        override fun render(): String = "union $name" + "*".repeat(pointerDepth)
    }

    data class Enum(val name: String, val pointerDepth: Int = 0) : CType {
        override fun render(): String = "enum $name" + "*".repeat(pointerDepth)
    }

    data class Named(val name: String, val pointerDepth: Int = 0) : CType {
        override fun render(): String = name + "*".repeat(pointerDepth)
    }

    data object Unknown : CType {
        override fun render(): String = "int"
    }
}

sealed interface CStatement {
    val origin: Origin
}

data class CBlock(
    val statements: List<CStatement>,
    override val origin: Origin
) : CStatement

data class CReturn(
    val expression: CExpression?,
    override val origin: Origin
) : CStatement

data class CExpressionStatement(
    val expression: CExpression,
    override val origin: Origin
) : CStatement

data class CVariableDeclaration(
    val type: CType,
    val name: String,
    val initializer: CExpression?,
    override val origin: Origin,
    val arrayDimensions: List<String> = emptyList()
) : CStatement

data class CIf(
    val condition: CExpression,
    val thenBranch: CStatement,
    val elseBranch: CStatement?,
    override val origin: Origin
) : CStatement

data class CWhile(
    val condition: CExpression,
    val body: CStatement,
    override val origin: Origin
) : CStatement

data class CFor(
    val initializer: CStatement?,
    val condition: CExpression?,
    val increment: CExpression?,
    val body: CStatement,
    override val origin: Origin
) : CStatement

data class CBreak(override val origin: Origin) : CStatement

data class CContinue(override val origin: Origin) : CStatement

sealed interface CExpression {
    val origin: Origin
}

data class CIntegerLiteral(val text: String, override val origin: Origin) : CExpression
data class CStringLiteral(val text: String, override val origin: Origin) : CExpression
data class CCharacterLiteral(val text: String, override val origin: Origin) : CExpression
data class CIdentifier(val name: String, override val origin: Origin) : CExpression
data class CUnary(val operator: String, val operand: CExpression, override val origin: Origin) : CExpression
data class CBinary(val left: CExpression, val operator: String, val right: CExpression, override val origin: Origin) : CExpression
data class CCall(val callee: CExpression, val arguments: List<CExpression>, override val origin: Origin) : CExpression
data class CMemberAccess(
    val receiver: CExpression,
    val member: String,
    val pointerReceiver: Boolean = false,
    override val origin: Origin
) : CExpression
data class CIndexAccess(
    val receiver: CExpression,
    val index: CExpression,
    override val origin: Origin
) : CExpression
data class CParenthesized(val expression: CExpression, override val origin: Origin) : CExpression

data class SourceMapping(
    val generatedLine: Int,
    val origin: Origin
)

data class GeneratedCUnit(
    val text: String,
    val sourceMap: List<SourceMapping>
)
