package cplus.backend

import cplus.core.Origin

data class CTranslationUnit(
    val includes: List<String>,
    val structs: List<CStructDeclaration>,
    val unions: List<CUnionDeclaration>,
    val enums: List<CEnumDeclaration>,
    val aliases: List<CAliasDeclaration>,
    val globals: List<CGlobalDeclaration>,
    val functions: List<CFunction>,
    val requiresStringTemplateRuntime: Boolean = false,
    val forwardDeclarations: List<CForwardDeclaration> = emptyList(),
    val aggregateDeclarations: List<CAggregateDeclaration> = emptyList(),
    val publicIncludes: List<String> = emptyList(),
    val runtimeDependencies: List<String> = emptyList(),
    val testProduct: CTestProductMetadata? = null
)

data class CTestProductMetadata(val entryPointName: String, val fixtures: List<CTestFixtureMetadata>)
data class CTestFixtureMetadata(
    val identity: String,
    val functionName: String,
    val origin: Origin,
    val description: String = identity
)

enum class CTestValueKind(val tag: Int) {
    UNKNOWN(0),
    SIGNED_INTEGER(1),
    UNSIGNED_INTEGER(2),
    PLAIN_INTEGER(3),
    BOOLEAN(4),
    FLOATING(5),
    DOUBLE(6),
    LONG_DOUBLE(7),
    COMPLEX_FLOAT(8),
    COMPLEX_DOUBLE(9),
    COMPLEX_LONG_DOUBLE(10),
    POINTER(11)
}

enum class CTagKind {
    STRUCT,
    UNION
}

data class CForwardDeclaration(
    val kind: CTagKind,
    val name: String,
    val origin: Origin
)

sealed interface CAggregateDeclaration {
    val name: String
    val fields: List<CField>
    val origin: Origin
    val isPublic: Boolean
}

data class CStructDeclaration(
    override val name: String,
    override val fields: List<CField>,
    override val origin: Origin,
    override val isPublic: Boolean = false
) : CAggregateDeclaration

data class CField(
    val type: CType,
    val name: String,
    val origin: Origin,
    val arrayDimensions: List<String> = emptyList()
)

data class CUnionDeclaration(
    override val name: String,
    override val fields: List<CField>,
    override val origin: Origin,
    override val isPublic: Boolean = false
) : CAggregateDeclaration

data class CEnumDeclaration(
    val name: String,
    val values: List<CEnumValue>,
    val origin: Origin,
    val isPublic: Boolean = false,
    val isStatic: Boolean = false
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
    val origin: Origin,
    val isPublic: Boolean = false
)

data class CGlobalDeclaration(
    val type: CType,
    val name: String,
    val initializer: CExpression?,
    val origin: Origin,
    val arrayDimensions: List<String> = emptyList(),
    val isExtern: Boolean = false,
    val isPublic: Boolean = false,
    val threadLocal: Boolean = false
)

data class CFunction(
    val returnType: CType,
    val name: String,
    val parameters: List<CParameter>,
    val body: CStatement?,
    val origin: Origin,
    val isVariadic: Boolean = false,
    val isPublic: Boolean = false,
    val isStatic: Boolean = false
)

data class CParameter(
    val type: CType,
    val name: String,
    val origin: Origin,
    val arrayDimensions: List<String> = emptyList()
)

sealed interface CType {
    fun render(): String
    fun renderDeclaration(name: String): String = "${render()} $name"

    data class Primitive(
        val name: String,
        val pointerDepth: Int = 0,
        val qualifiers: Set<String> = emptySet(),
        val pointerQualifiers: List<Set<String>> = emptyList()
    ) : CType {
        override fun render(): String = renderCType(name, pointerDepth, qualifiers, pointerQualifiers)
    }

    data class Struct(
        val name: String,
        val pointerDepth: Int = 0,
        val qualifiers: Set<String> = emptySet(),
        val pointerQualifiers: List<Set<String>> = emptyList()
    ) : CType {
        override fun render(): String = renderCType("struct $name", pointerDepth, qualifiers, pointerQualifiers)
    }

    data class Union(
        val name: String,
        val pointerDepth: Int = 0,
        val qualifiers: Set<String> = emptySet(),
        val pointerQualifiers: List<Set<String>> = emptyList()
    ) : CType {
        override fun render(): String = renderCType("union $name", pointerDepth, qualifiers, pointerQualifiers)
    }

    data class Enum(
        val name: String,
        val pointerDepth: Int = 0,
        val qualifiers: Set<String> = emptySet(),
        val pointerQualifiers: List<Set<String>> = emptyList()
    ) : CType {
        override fun render(): String = renderCType("enum $name", pointerDepth, qualifiers, pointerQualifiers)
    }

    data class Named(
        val name: String,
        val pointerDepth: Int = 0,
        val qualifiers: Set<String> = emptySet(),
        val pointerQualifiers: List<Set<String>> = emptyList()
    ) : CType {
        override fun render(): String = renderCType(name, pointerDepth, qualifiers, pointerQualifiers)
    }

    data class FunctionPointer(
        val returnType: CType,
        val parameterTypes: List<CType>,
        val isVariadic: Boolean = false,
        val pointerDepth: Int = 1,
        val pointerQualifiers: List<Set<String>> = emptyList()
    ) : CType {
        override fun render(): String = renderFunctionPointer("")

        override fun renderDeclaration(name: String): String = renderFunctionPointer(name)

        private fun renderFunctionPointer(name: String): String = buildString {
            append(returnType.render())
            append(" (")
            repeat(pointerDepth) { index ->
                append('*')
                pointerQualifiers.getOrNull(index)?.takeIf { it.isNotEmpty() }?.let {
                    append(' ').append(it.joinToString(" "))
                }
            }
            append(name)
            append(")(")
            append(parameterTypes.joinToString(", ") { it.render() })
            if (isVariadic) {
                if (parameterTypes.isNotEmpty()) append(", ")
                append("...")
            }
            append(')')
        }
    }

    data object Unknown : CType {
        override fun render(): String = "int"
    }
}

private fun renderCType(
    baseName: String,
    pointerDepth: Int,
    qualifiers: Set<String>,
    pointerQualifiers: List<Set<String>>
): String = buildString {
    if (qualifiers.isNotEmpty()) append(qualifiers.joinToString(" ")).append(' ')
    append(baseName)
    repeat(pointerDepth) { index ->
        append('*')
        pointerQualifiers.getOrNull(index)?.takeIf { it.isNotEmpty() }?.let {
            append(' ').append(it.joinToString(" "))
        }
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
data class CFloatLiteral(val text: String, override val origin: Origin) : CExpression
data class CStringLiteral(val text: String, override val origin: Origin) : CExpression
data class CCharacterLiteral(val text: String, override val origin: Origin) : CExpression
data class CIdentifier(val name: String, override val origin: Origin) : CExpression
data class CUnary(val operator: String, val operand: CExpression, override val origin: Origin) : CExpression
data class CBinary(val left: CExpression, val operator: String, val right: CExpression, override val origin: Origin) : CExpression
data class CAssignment(val left: CExpression, val operator: String, val right: CExpression, override val origin: Origin) : CExpression
data class CConditional(
    val condition: CExpression,
    val thenBranch: CExpression,
    val elseBranch: CExpression,
    override val origin: Origin
) : CExpression
data class CUpdate(
    val operand: CExpression,
    val operator: String,
    val prefix: Boolean,
    override val origin: Origin
) : CExpression
data class CSizeOf(val operand: CExpression?, val targetType: CType? = null, override val origin: Origin) : CExpression
data class CAbiQuery(
    val query: String,
    val operand: CExpression? = null,
    val targetType: CType? = null,
    val fieldName: String? = null,
    override val origin: Origin
) : CExpression
data class CCast(val target: CType, val operand: CExpression, override val origin: Origin) : CExpression
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
    val origin: Origin,
    val generatedStartOffset: Int = 0,
    val generatedEndOffset: Int = 0
)

data class GeneratedCUnit(
    val text: String,
    val sourceMap: List<SourceMapping>
) {
    fun mappingAtByteOffset(offset: Int): SourceMapping? = sourceMap.firstOrNull {
        offset >= it.generatedStartOffset && offset < it.generatedEndOffset
    }

    fun mappingsForGeneratedLine(line: Int): List<SourceMapping> = sourceMap.filter {
        it.generatedLine == line
    }
}
