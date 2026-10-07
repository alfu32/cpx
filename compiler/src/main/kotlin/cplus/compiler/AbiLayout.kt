package cplus.compiler

import cplus.semantic.*

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

    private fun primitive(name: String): AbiLayout = when (name) {
        "bool", "char", "signed char", "unsigned char" -> AbiLayout(1, 1)
        "short", "signed short", "unsigned short" -> AbiLayout(2, 2)
        "int", "signed", "signed int", "unsigned", "unsigned int", "float" -> AbiLayout(4, 4)
        "long", "unsigned long" -> {
            val size = if (target.cIntegerModel == "llp64") 4 else 8
            AbiLayout(size, size)
        }
        "long long", "unsigned long long", "double" -> AbiLayout(8, 8)
        else -> AbiLayout(0, 1)
    }

    private fun align(value: Int, alignment: Int): Int = if (alignment <= 1) value else {
        ((value + alignment - 1) / alignment) * alignment
    }
}
