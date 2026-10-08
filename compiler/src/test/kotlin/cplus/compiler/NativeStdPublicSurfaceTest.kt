package cplus.compiler

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NativeStdPublicSurfaceTest {
    @Test
    fun standardValueTypesAndTextOperationsAreExplicitlyImportableAcrossTargets() {
        val root = SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize().parent!!.parent!!
        val directory = Files.createTempDirectory("cplus-native-std-public-api")
        val main = directory.resolve("main.cp").also {
            Files.writeString(
                it,
                """
                    import { std_error_t, std_error_ok, std_error_is_ok,
                        std_result_t, std_result_ok, std_result_is_ok,
                        std_option_t, std_option_some, std_option_is_some } from std.core;
                    import { std_slice_t, std_slice_empty, std_slice_is_empty,
                        std_range_t, std_range, std_range_length } from std.collections;
                    import { std_text_is_ascii } from std.text;

                    int main() {
                        std_error_t error = std_error_ok();
                        std_result_t result = std_result_ok(7);
                        std_option_t option = std_option_some(9);
                        std_slice_t slice = std_slice_empty();
                        std_range_t range = std_range(-1, 1);
                        if (!std_error_is_ok(error)) return 1;
                        if (!std_result_is_ok(result) || result.value != 7) return 2;
                        if (!std_option_is_some(option) || option.value != 9) return 3;
                        if (!std_slice_is_empty(slice)) return 4;
                        if (std_range_length(range) != 2) return 5;
                        return !std_text_is_ascii("ascii");
                    }
                """.trimIndent()
            )
        }
        val modules = listOf("core.cp", "mem.cp", "collections.cp", "text.cp", "string.cp")
            .map { root.resolve("std/src/$it") }
        try {
            listOf("linux-x86_64", "linux-aarch64", "windows-x86_64", "windows-aarch64").forEach { target ->
                val result = CPlusCompiler().compile(
                    CompileRequest(listOf(main) + modules, target = TargetInfo(targetTriple = target))
                )

                assertTrue(result.isSuccessful, "$target: ${result.diagnostics.joinToString()}")
                val generatedHeader = result.generatedHeaders.single().text
                listOf("std_error_t", "std_result_t", "std_option_t", "std_slice_t", "std_range_t", "std_text_is_ascii")
                    .forEach { declaration -> assertTrue(declaration in generatedHeader, "$target missing $declaration") }

                val descriptor = requireNotNull(TargetRegistry.load(root.resolve("abi/$target.toml")).descriptor)
                val layouts = AbiLayoutEngine(descriptor)
                val model = requireNotNull(result.semanticModel)
                val expectedLayouts = mapOf(
                    "std_error_t" to Triple(4, 4, listOf(0)),
                    "std_result_t" to Triple(24, 8, listOf(0, 8, 16)),
                    "std_option_t" to Triple(16, 8, listOf(0, 8)),
                    "std_slice_t" to Triple(16, 8, listOf(0, 8)),
                    "std_range_t" to Triple(16, 8, listOf(0, 8))
                )
                expectedLayouts.forEach { (typeName, expected) ->
                    val layout = layouts.layout(model.structs.getValue(typeName))
                    assertEquals(expected.first, layout.size, "$target $typeName size")
                    assertEquals(expected.second, layout.alignment, "$target $typeName alignment")
                    assertEquals(expected.third, layout.fields.map { it.offset }, "$target $typeName offsets")
                }
            }
        } finally {
            Files.deleteIfExists(main)
            Files.deleteIfExists(directory)
        }
    }
}
