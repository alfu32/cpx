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

    @Test
    fun appliesTargetIntegerModelToLong() {
        val windows = requireNotNull(
            TargetRegistry.load(
                SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize().parent!!.parent!!
                    .resolve("abi/windows-x86_64.toml")
            ).descriptor
        )
        val longType = PrimitiveType(TypeId(4), "long")
        val longLongType = PrimitiveType(TypeId(5), "long long")

        assertEquals(4, AbiLayoutEngine(windows).layout(longType).size)
        assertEquals(8, AbiLayoutEngine(windows).layout(longLongType).size)
    }

    @Test
    fun resolvesForeignFixedWidthAndStddefAliasesToTheirUnderlyingLayouts() {
        val result = CPlusCompiler().compileText(
            java.nio.file.Files.createTempFile("foreign-layout", ".cp"),
            """
                import { size_t, ptrdiff_t } from c.stddef;
                import { int32_t, uint32_t, int64_t, uint64_t } from c.stdint;
                int main() { return 0; }
            """.trimIndent()
        )
        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val model = requireNotNull(result.semanticModel)
        val windows = requireNotNull(
            TargetRegistry.load(
                SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize().parent!!.parent!!
                    .resolve("abi/windows-x86_64.toml")
            ).descriptor
        )
        val linuxLayouts = AbiLayoutEngine(target)
        val windowsLayouts = AbiLayoutEngine(windows)

        assertEquals(8, linuxLayouts.layout(model.foreignTypes.getValue("size_t")).size)
        assertEquals(8, linuxLayouts.layout(model.foreignTypes.getValue("ptrdiff_t")).size)
        assertEquals(4, windowsLayouts.layout(model.foreignTypes.getValue("size_t")).size)
        assertEquals(4, windowsLayouts.layout(model.foreignTypes.getValue("ptrdiff_t")).size)
        assertEquals(4, windowsLayouts.layout(model.foreignTypes.getValue("uint32_t")).size)
        assertEquals(8, windowsLayouts.layout(model.foreignTypes.getValue("int64_t")).size)
        assertEquals(8, windowsLayouts.layout(model.foreignTypes.getValue("uint64_t")).size)
    }

    @Test
    fun auditsPrimitiveAndAggregateLayoutAcrossDeclaredTargetMatrix() {
        val root = SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize().parent!!.parent!!
        listOf("linux-x86_64", "linux-aarch64", "windows-x86_64", "windows-aarch64").forEach { name ->
            val descriptor = requireNotNull(TargetRegistry.load(root.resolve("abi/$name.toml")).descriptor)
            val layouts = AbiLayoutEngine(descriptor)
            val longSize = if (descriptor.cIntegerModel == "llp64") 4 else 8
            assertEquals(descriptor.pointerBits / 8, layouts.layout(PointerType(TypeId(50), PrimitiveType(TypeId(51), "int"))).size, name)
            assertEquals(longSize, layouts.layout(PrimitiveType(TypeId(52), "long")).size, name)
            assertEquals(8, layouts.layout(PrimitiveType(TypeId(53), "long long")).size, name)

            val record = StructType(TypeId(54), "AbiRecord", emptyList())
            record.fields = listOf(
                FieldSymbol(Symbol(SymbolId(55), "tag", SymbolKind.FIELD, PrimitiveType(TypeId(56), "char"), ownerOrigin()), record),
                FieldSymbol(Symbol(SymbolId(57), "value", SymbolKind.FIELD, PrimitiveType(TypeId(58), "long"), ownerOrigin()), record),
                FieldSymbol(Symbol(SymbolId(59), "pointer", SymbolKind.FIELD, PointerType(TypeId(60), PrimitiveType(TypeId(61), "int")), ownerOrigin()), record)
            )
            val layout = layouts.layout(record)
            assertEquals(0, layout.fields[0].offset, name)
            assertEquals(longSize, layout.fields[1].offset, name)
            assertEquals(longSize, layout.fields[1].size, name)
            assertEquals(descriptor.pointerBits / 8, layout.fields[2].size, name)
            assertTrue(layout.alignment >= descriptor.pointerBits / 8, name)
        }
    }

    private fun ownerOrigin(): cplus.core.Origin = cplus.core.Origin.Synthetic(null)
}
