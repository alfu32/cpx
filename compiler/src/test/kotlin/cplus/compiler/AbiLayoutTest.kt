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
    fun exposesInt128LayoutOnlyForTheVerifiedTarget() {
        val int128 = PrimitiveType(TypeId(40), "__int128")
        val unsignedInt128 = PrimitiveType(TypeId(41), "unsigned __int128")
        val linuxLayouts = AbiLayoutEngine(target)
        val root = SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize().parent!!.parent!!
        listOf(
            "linux-aarch64", "windows-x86_64", "windows-aarch64", "darwin-x86_64", "darwin-aarch64"
        ).forEach { targetName ->
            val descriptor = requireNotNull(TargetRegistry.load(root.resolve("abi/$targetName.toml")).descriptor)
            val layouts = AbiLayoutEngine(descriptor)
            assertEquals(0, layouts.layout(int128).size, targetName)
            assertEquals(0, layouts.layout(unsignedInt128).size, targetName)
        }
        assertEquals(16, linuxLayouts.layout(int128).size)
        assertEquals(16, linuxLayouts.layout(int128).alignment)
        assertEquals(16, linuxLayouts.layout(unsignedInt128).size)
        assertEquals(16, linuxLayouts.layout(unsignedInt128).alignment)
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
            val integerSizes = linkedMapOf(
                "char" to 1,
                "signed char" to 1,
                "unsigned char" to 1,
                "short" to 2,
                "unsigned short" to 2,
                "int" to 4,
                "unsigned int" to 4,
                "long" to longSize,
                "unsigned long" to longSize,
                "long long" to 8,
                "unsigned long long" to 8
            )
            integerSizes.entries.forEachIndexed { index, (typeName, expectedSize) ->
                val layout = layouts.layout(PrimitiveType(TypeId(100 + index), typeName))
                assertEquals(expectedSize, layout.size, "$name $typeName size")
                assertEquals(expectedSize, layout.alignment, "$name $typeName alignment")
            }
            assertEquals(descriptor.pointerBits / 8, layouts.layout(PointerType(TypeId(50), PrimitiveType(TypeId(51), "int"))).size, name)
            assertEquals(descriptor.pointerBits / 8, layouts.layout(PrimitiveType(TypeId(62), "size_t")).size, "$name size_t")
            assertEquals(descriptor.pointerBits / 8, layouts.layout(PrimitiveType(TypeId(63), "ptrdiff_t")).size, "$name ptrdiff_t")
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

    @Test
    fun laysOutParsedCanonicalIntegerTypesAcrossLp64AndLlp64Targets() {
        val source = """
            struct parsed_integer_record {
                char plain_char;
                signed char signed_char;
                unsigned char unsigned_char;
                short signed_short;
                unsigned short unsigned_short;
                int signed_int;
                unsigned int unsigned_int;
                long signed_long;
                unsigned long unsigned_long;
                long long signed_long_long;
                unsigned long long unsigned_long_long;
            };
            char plain_char;
            char signed signed_char;
            char unsigned unsigned_char;
            short signed int signed_short;
            int unsigned short unsigned_short;
            int signed signed_int;
            unsigned int unsigned_int;
            int long signed_long;
            long unsigned int unsigned_long;
            long int long signed_long_long;
            unsigned long long int unsigned_long_long;
            int main() { return 0; }
        """.trimIndent()
        val result = CPlusCompiler().compileText(
            java.nio.file.Files.createTempFile("parsed-primitive-layout", ".cp"),
            source
        )
        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val model = requireNotNull(result.semanticModel)
        val fixedSizes = linkedMapOf(
            "plain_char" to 1,
            "signed_char" to 1,
            "unsigned_char" to 1,
            "signed_short" to 2,
            "unsigned_short" to 2,
            "signed_int" to 4,
            "unsigned_int" to 4,
            "signed_long_long" to 8,
            "unsigned_long_long" to 8
        )
        val longTypes = setOf("signed_long", "unsigned_long")
        val root = SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize().parent!!.parent!!

        listOf("linux-x86_64", "linux-aarch64", "windows-x86_64", "windows-aarch64").forEach { name ->
            val descriptor = requireNotNull(TargetRegistry.load(root.resolve("abi/$name.toml")).descriptor)
            val layouts = AbiLayoutEngine(descriptor)
            val longSize = if (descriptor.cIntegerModel == "llp64") 4 else 8
            (fixedSizes.keys + longTypes).forEach { symbolName ->
                val symbol = model.symbols.first { it.name == symbolName }
                val layout = layouts.layout(symbol.type)
                val expectedSize = if (symbolName in longTypes) longSize else fixedSizes.getValue(symbolName)
                assertEquals(expectedSize, layout.size, "$name $symbolName size")
                assertEquals(expectedSize, layout.alignment, "$name $symbolName alignment")
            }

            val record = layouts.layout(model.structs.getValue("parsed_integer_record"))
            var expectedOffset = 0
            var expectedAlignment = 1
            record.fields.forEachIndexed { index, field ->
                val memberType = model.structs.getValue("parsed_integer_record").fields[index].symbol.type
                val memberLayout = layouts.layout(memberType)
                expectedOffset = ((expectedOffset + memberLayout.alignment - 1) / memberLayout.alignment) * memberLayout.alignment
                assertEquals(expectedOffset, field.offset, "$name ${field.name} offset")
                assertEquals(memberLayout.size, field.size, "$name ${field.name} size")
                assertEquals(memberLayout.alignment, field.alignment, "$name ${field.name} alignment")
                expectedOffset += memberLayout.size
                expectedAlignment = maxOf(expectedAlignment, memberLayout.alignment)
            }
            val expectedRecordSize = ((expectedOffset + expectedAlignment - 1) / expectedAlignment) * expectedAlignment
            assertEquals(expectedRecordSize, record.size, "$name parsed record size")
        }
    }

    private fun ownerOrigin(): cplus.core.Origin = cplus.core.Origin.Synthetic(null)
}
