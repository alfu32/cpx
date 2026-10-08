package cplus.compiler

import cplus.semantic.*
import cplus.core.CIntegerRank
import cplus.core.CPrimitiveKind
import cplus.core.CPrimitiveTypes

data class AbiFieldLayout(
    val name: String,
    val offset: Int,
    val size: Int,
    val alignment: Int
)

data class AbiLayout(
    val size: Int,
    val alignment: Int,
    val fields: List<AbiFieldLayout> = emptyList()
)

class AbiLayoutEngine(private val target: TargetAbiDescriptor) {
    fun layout(type: CType): AbiLayout = when (type) {
        is PointerType -> AbiLayout(target.pointerBits / 8, target.pointerBits / 8)
        is PrimitiveType -> primitive(type.name)
        is ArrayType -> {
            val element = layout(type.element)
            val count = type.dimensions.firstOrNull()?.toIntOrNull() ?: 0
            AbiLayout(element.size * count, element.alignment)
        }
        is StructType -> struct(type)
        is UnionType -> union(type)
        is EnumType -> AbiLayout(4, 4)
        is AliasType -> layout(type.target)
        is ForeignType -> type.underlyingType?.let(::layout)
            ?: AbiLayout(target.pointerBits / 8, target.pointerBits / 8)
        is FunctionType -> AbiLayout(target.pointerBits / 8, target.pointerBits / 8)
        is UnknownType -> AbiLayout(0, 1)
    }

    private fun struct(type: StructType): AbiLayout {
        var offset = 0
        var alignment = 1
        val fields = type.fields.map { field ->
            val fieldLayout = layout(field.symbol.type)
            offset = align(offset, fieldLayout.alignment)
            val result = AbiFieldLayout(field.symbol.name, offset, fieldLayout.size, fieldLayout.alignment)
            offset += fieldLayout.size
            alignment = maxOf(alignment, fieldLayout.alignment)
            result
        }
        return AbiLayout(align(offset, alignment), alignment, fields)
    }

    private fun union(type: UnionType): AbiLayout {
        val fields = type.fields.map { field ->
            val fieldLayout = layout(field.symbol.type)
            AbiFieldLayout(field.symbol.name, 0, fieldLayout.size, fieldLayout.alignment)
        }
        return AbiLayout(
            align(fields.maxOfOrNull { it.size } ?: 0, fields.maxOfOrNull { it.alignment } ?: 1),
            fields.maxOfOrNull { it.alignment } ?: 1,
            fields
        )
    }

    private fun primitive(name: String): AbiLayout {
        if (name in CPrimitiveTypes.standardIntegerTypedefNames) {
            val size = target.pointerBits / 8
            return AbiLayout(size, size)
        }
        val type = CPrimitiveTypes.typeInfo(name) ?: return AbiLayout(0, 1)
        val size = when (type.kind) {
            CPrimitiveKind.VOID -> 0
            CPrimitiveKind.BOOLEAN -> 1
            CPrimitiveKind.FLOATING -> target.floatingTypes[type.name]?.sizeBytes ?: 0
            CPrimitiveKind.COMPLEX -> if ("c17_complex" in target.features) {
                (type.componentTypeName?.let(target.floatingTypes::get)?.sizeBytes ?: 0) * 2
            } else 0
            CPrimitiveKind.INTEGER -> when (type.rank) {
                CIntegerRank.CHAR -> 1
                CIntegerRank.SHORT -> 2
                CIntegerRank.INT -> 4
                CIntegerRank.LONG -> if (target.cIntegerModel == "llp64") 4 else 8
                CIntegerRank.LONG_LONG -> 8
                CIntegerRank.INT128 -> if ("int128" in target.features) 16 else 0
                null -> 0
            }
        }
        val alignment = when {
            type.kind == CPrimitiveKind.FLOATING -> target.floatingTypes[type.name]?.alignmentBytes ?: 1
            type.kind == CPrimitiveKind.COMPLEX -> type.componentTypeName
                ?.let(target.floatingTypes::get)?.alignmentBytes ?: 1
            type.rank == CIntegerRank.INT128 && size > 0 -> 16
            else -> size
        }
        return AbiLayout(size, if (alignment == 0) 1 else alignment)
    }

    private fun align(value: Int, alignment: Int): Int = if (alignment <= 1) value else {
        ((value + alignment - 1) / alignment) * alignment
    }
}
