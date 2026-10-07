package cplus.compiler

import cplus.semantic.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AbiLayoutTest {
    private val target = requireNotNull(
        TargetRegistry.load(
            SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize().parent!!.parent!!
                .resolve("abi/linux-x86_64.toml")
        ).descriptor
    )

    @Test
    fun computesStableLp64StructOffsets() {
        val intType = PrimitiveType(TypeId(1), "int")
        val pointer = PointerType(TypeId(2), intType)
        val owner = StructType(TypeId(3), "Record", emptyList())
        owner.fields = listOf(
            FieldSymbol(Symbol(SymbolId(1), "count", SymbolKind.FIELD, intType, ownerOrigin()), owner),
            FieldSymbol(Symbol(SymbolId(2), "next", SymbolKind.FIELD, pointer, ownerOrigin()), owner)
        )

        val layout = AbiLayoutEngine(target).layout(owner)

        assertEquals(16, layout.size)
        assertEquals(8, layout.alignment)
        assertEquals(0, layout.fields[0].offset)
        assertEquals(8, layout.fields[1].offset)
        assertTrue(layout.fields.all { it.alignment > 0 })
    }

    private fun ownerOrigin(): cplus.core.Origin = cplus.core.Origin.Synthetic(null)
}
