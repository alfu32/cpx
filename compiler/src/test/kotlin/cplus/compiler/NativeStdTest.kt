package cplus.compiler

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

class NativeStdTest {
    @Test
    fun targetNeutralCoreMemoryStringTextAndCollectionValuesExecute() {
        assumeTrue(System.getProperty("os.name").contains("linux", ignoreCase = true))
        val root = SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize().parent!!.parent!!
        val sources = listOf("core.cp", "mem.cp", "string.cp", "text.cp", "collections.cp").map {
            root.resolve("std/src").resolve(it)
        }
        val result = CPlusCompiler().compile(CompileRequest(sources))
        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val generated = result.generatedUnits.single().text
        assertTrue("#include <stddef.h>" in generated, generated)
        assertTrue("typedef size_t usize;" in generated, generated)
        assertTrue("typedef ptrdiff_t isize;" in generated, generated)
        val publicHeader = result.generatedHeaders.single().text
        assertTrue("#include <stddef.h>" in publicHeader, publicHeader)
        assertTrue("typedef size_t usize;" in publicHeader, publicHeader)
        assertTrue("typedef ptrdiff_t isize;" in publicHeader, publicHeader)
        listOf("std_error_t", "std_result_t", "std_option_t", "std_text_is_ascii")
            .forEach { symbol -> assertTrue(symbol in publicHeader, publicHeader) }
        val directory = Files.createTempDirectory("cplus-native-std")
        val combined = directory.resolve("native_std.c").also {
            Files.writeString(it, generated + """
                int main(void) {
                    char source[32] = "native";
                    char copy[32];
                    char overlap[10] = "abcdef";
                    char utf8[] = "\xC3\xA9";
                    unsigned char high_bytes[2];
                    struct std_result_t ok = std_result_ok(7);
                    struct std_option_t some = std_option_some(11);
                    struct std_range_t range = std_range(2, 6);
                    struct std_range_t crossing_zero = std_range(-1, 1);
                    struct std_range_t full_range = std_range(std_isize_min(), std_isize_max());
                    struct std_slice_t slice = std_slice_of(source, 6);
                    usize maximum_size = std_usize_max();
                    isize maximum_index = std_isize_max();
                    if (!std_result_is_ok(ok) || ok.value != 7) return 1;
                    if (!std_option_is_some(some) || some.value != 11) return 2;
                    if (std_range_length(range) != 4 || !std_range_contains(range, 5)) return 3;
                    if (std_range_length(crossing_zero) != 2 || std_range_length(full_range) != std_usize_max()) return 27;
                    if (std_slice_is_empty(slice)) return 4;
                    if (sizeof(usize) != sizeof(void*) || sizeof(isize) != sizeof(void*)) return 5;
                    if (std_size_width_bits() != 64 || std_pointer_width_bits() != 64) return 6;
                    if (maximum_size != (usize)-1 || std_isize_min() != -maximum_index - 1) return 7;
                    if (std_usize_compare(2, 3) != -1 || std_isize_compare(-1, 0) != -1) return 8;
                    if (std_byte_compare(255, 1) != 1) return 9;
                    if (!std_pointer_is_null((void*)0) || std_pointer_equal(source, copy)) return 10;
                    if (std_pointer_offset(source, 2) != source + 2) return 11;
                    if (std_pointer_distance(source, source + 4) != 4) return 12;
                    if (!std_mem_is_aligned(16, 8) || std_mem_is_aligned(17, 8)) return 13;
                    if (std_mem_align_up(13, 8) != 16 || std_mem_align_up(maximum_size, 2) != maximum_size) return 14;
                    if (std_mem_align_up(5, 3) != maximum_size) return 15;
                    struct std_memory_span_t span = std_memory_span(source, 6);
                    struct std_raw_memory_t raw = std_raw_memory_view(source, 6);
                    if (std_memory_span_is_empty(span) || std_memory_span_at(span, 5) != source + 5) return 16;
                    if (std_memory_span_at(span, 6) != (void*)0 || std_raw_memory_is_empty(raw)) return 17;
                    if (std_raw_memory_as_bytes(raw).length != 6) return 18;
                    std_mem_copy(copy, source, 7);
                    if (!std_mem_equal(copy, source, 7)) return 19;
                    std_mem_move(overlap + 1, overlap, 7);
                    if (std_string_compare(overlap, "aabcdef") != 0) return 20;
                    if (std_mem_compare(overlap + 1, "abcdef", 7) != 0) return 21;
                    if (std_string_length(copy) != 6 || std_string_compare(copy, "native") != 0) return 22;
                    if (!std_text_is_ascii(copy) || std_text_is_ascii(utf8)) return 28;
                    if (std_text_byte_length(copy) != 6 || !std_text_has_ascii_prefix(copy, "nat")) return 23;
                    if (std_text_byte_length(utf8) != 2 || std_text_is_ascii(utf8)) return 24;
                    std_mem_set(high_bytes, 255, 2);
                    if (high_bytes[0] != 255 || high_bytes[1] != 255) return 25;
                    std_mem_zero(copy, 7);
                    if (!std_text_is_empty(copy)) return 26;
                    return cplus_std_core_version() != 3;
                }
            """.trimIndent())
        }
        val executable = directory.resolve("native_std")
        val compile = ProcessBuilder(
            "cc", "-std=c17", "-fsanitize=signed-integer-overflow",
            "-fno-sanitize-recover=signed-integer-overflow",
            "-I", root.resolve("libc/include").toString(),
            combined.toString(), "-o", executable.toString()
        )
            .redirectErrorStream(true)
            .start()
        val output = compile.inputStream.bufferedReader().readText()
        assertEquals(0, compile.waitFor(), output)
        assertEquals(0, ProcessBuilder(executable.toString()).redirectErrorStream(true).start().waitFor())
    }
}
