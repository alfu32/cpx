package cplus.core

enum class CPrimitiveKind {
    VOID,
    BOOLEAN,
    INTEGER,
    FLOATING
}

enum class CIntegerRank {
    CHAR,
    SHORT,
    INT,
    LONG,
    LONG_LONG
}

enum class CIntegerSignedness {
    PLAIN,
    SIGNED,
    UNSIGNED
}

data class CPrimitiveTypeInfo(
    val name: String,
    val kind: CPrimitiveKind,
    val rank: CIntegerRank? = null,
    val signedness: CIntegerSignedness? = null
)

/** Canonical C primitive spellings shared by parsing, semantic analysis, and backends. */
object CPrimitiveTypes {
    val specifierKeywords: Set<String> = setOf(
        "void", "bool", "char", "short", "int", "long", "float", "double", "signed", "unsigned"
    )

    val standardTypedefNames: Set<String> = setOf("size_t", "ptrdiff_t", "max_align_t")
    val standardIntegerTypedefNames: Set<String> = setOf("size_t", "ptrdiff_t")

    val types: List<CPrimitiveTypeInfo> = listOf(
        CPrimitiveTypeInfo("void", CPrimitiveKind.VOID),
        CPrimitiveTypeInfo("bool", CPrimitiveKind.BOOLEAN),
        CPrimitiveTypeInfo("char", CPrimitiveKind.INTEGER, CIntegerRank.CHAR, CIntegerSignedness.PLAIN),
        CPrimitiveTypeInfo("signed char", CPrimitiveKind.INTEGER, CIntegerRank.CHAR, CIntegerSignedness.SIGNED),
        CPrimitiveTypeInfo("unsigned char", CPrimitiveKind.INTEGER, CIntegerRank.CHAR, CIntegerSignedness.UNSIGNED),
        CPrimitiveTypeInfo("short", CPrimitiveKind.INTEGER, CIntegerRank.SHORT, CIntegerSignedness.SIGNED),
        CPrimitiveTypeInfo("unsigned short", CPrimitiveKind.INTEGER, CIntegerRank.SHORT, CIntegerSignedness.UNSIGNED),
        CPrimitiveTypeInfo("int", CPrimitiveKind.INTEGER, CIntegerRank.INT, CIntegerSignedness.SIGNED),
        CPrimitiveTypeInfo("unsigned int", CPrimitiveKind.INTEGER, CIntegerRank.INT, CIntegerSignedness.UNSIGNED),
        CPrimitiveTypeInfo("long", CPrimitiveKind.INTEGER, CIntegerRank.LONG, CIntegerSignedness.SIGNED),
        CPrimitiveTypeInfo("unsigned long", CPrimitiveKind.INTEGER, CIntegerRank.LONG, CIntegerSignedness.UNSIGNED),
        CPrimitiveTypeInfo("long long", CPrimitiveKind.INTEGER, CIntegerRank.LONG_LONG, CIntegerSignedness.SIGNED),
        CPrimitiveTypeInfo("unsigned long long", CPrimitiveKind.INTEGER, CIntegerRank.LONG_LONG, CIntegerSignedness.UNSIGNED),
        CPrimitiveTypeInfo("float", CPrimitiveKind.FLOATING),
        CPrimitiveTypeInfo("double", CPrimitiveKind.FLOATING)
    )

    private val typesByName = types.associateBy(CPrimitiveTypeInfo::name)

    val canonicalNames: Set<String> = typesByName.keys
    val integerNames: Set<String> = types.filter {
        it.kind == CPrimitiveKind.BOOLEAN || it.kind == CPrimitiveKind.INTEGER
    }.mapTo(linkedSetOf()) { it.name }
    val numericNames: Set<String> = types.filter {
        it.kind == CPrimitiveKind.BOOLEAN || it.kind == CPrimitiveKind.INTEGER || it.kind == CPrimitiveKind.FLOATING
    }.mapTo(linkedSetOf()) { it.name } + standardIntegerTypedefNames

    /** Returns the canonical built-in spelling, including legal C aliases such as `signed int`. */
    fun canonicalName(name: String): String? {
        val normalized = name.trim().replace(Regex("\\s+"), " ")
        if (normalized in typesByName) return normalized
        if (normalized.isEmpty()) return null
        return canonicalizeSpecifierSequence(normalized.split(' '))
    }

    fun typeInfo(name: String): CPrimitiveTypeInfo? = canonicalName(name)?.let(typesByName::get)

    fun isKnownTypeName(name: String): Boolean = typeInfo(name) != null || name in standardTypedefNames

    fun isInteger(name: String): Boolean = canonicalName(name)?.let { it in integerNames } == true ||
        name in standardIntegerTypedefNames

    fun isNumeric(name: String): Boolean = canonicalName(name)?.let { it in numericNames } == true ||
        name in standardIntegerTypedefNames

    /**
     * Canonicalizes the full sequence of C primitive type specifiers. `long double` remains
     * syntactically recognized, while semantic/ABI support is intentionally not implied here.
     */
    fun canonicalizeSpecifierSequence(specifiers: List<String>): String? {
        if (specifiers.isEmpty() || specifiers.any { it !in specifierKeywords }) return null
        val signCount = specifiers.count { it == "signed" || it == "unsigned" }
        if (signCount > 1) return null
        val unsigned = "unsigned" in specifiers

        val nonIntegerSpecifiers = specifiers.filter { it in setOf("void", "bool", "float", "double") }
        if (nonIntegerSpecifiers.isNotEmpty()) {
            return when {
                specifiers == listOf("void") -> "void"
                specifiers == listOf("bool") -> "bool"
                specifiers == listOf("float") -> "float"
                specifiers == listOf("double") -> "double"
                specifiers.size == 2 && specifiers.toSet() == setOf("long", "double") -> "long double"
                else -> null
            }
        }

        val charCount = specifiers.count { it == "char" }
        val shortCount = specifiers.count { it == "short" }
        val longCount = specifiers.count { it == "long" }
        val intCount = specifiers.count { it == "int" }
        if (charCount > 0) {
            if (charCount != 1 || shortCount != 0 || longCount != 0 || intCount != 0) return null
            return when {
                unsigned -> "unsigned char"
                "signed" in specifiers -> "signed char"
                else -> "char"
            }
        }

        if (shortCount > 1 || longCount > 2 || intCount > 1 || (shortCount > 0 && longCount > 0)) return null
        val rank = when {
            shortCount == 1 -> "short"
            longCount == 1 -> "long"
            longCount == 2 -> "long long"
            else -> "int"
        }
        return when {
            unsigned && rank == "int" -> "unsigned int"
            unsigned -> "unsigned $rank"
            else -> rank
        }
    }
}
