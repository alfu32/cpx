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
        val generated = sources.map { path ->
            val result = CPlusCompiler().compileText(path, Files.readString(path))
            assertTrue(result.isSuccessful, result.diagnostics.joinToString())
            result.generatedUnits.single().text
        }.joinToString("\n")
        val directory = Files.createTempDirectory("cplus-native-std")
        val combined = directory.resolve("native_std.c").also {
            Files.writeString(it, generated + """
                int main(void) {
                    char source[32] = "native";
                    char copy[32];
                    struct std_result_t ok = std_result_ok(7);
                    struct std_option_t some = std_option_some(11);
                    struct std_range_t range = std_range(2, 6);
                    struct std_slice_t slice = std_slice_of(source, 6);
                    if (!std_result_is_ok(ok) || ok.value != 7) return 1;
                    if (!std_option_is_some(some) || some.value != 11) return 2;
                    if (std_range_length(range) != 4 || !std_range_contains(range, 5)) return 3;
                    if (std_slice_is_empty(slice)) return 4;
                    std_mem_copy(copy, source, 7);
                    if (!std_mem_equal(copy, source, 7)) return 5;
                    if (std_string_compare(copy, "native") != 0) return 6;
                    if (std_text_byte_length(copy) != 6 || !std_text_has_ascii_prefix(copy, "nat")) return 7;
                    std_mem_zero(copy, 7);
                    return !std_text_is_empty(copy);
                }
            """.trimIndent())
        }
        val executable = directory.resolve("native_std")
        val compile = ProcessBuilder("cc", "-std=c17", combined.toString(), "-o", executable.toString())
            .redirectErrorStream(true)
            .start()
        val output = compile.inputStream.bufferedReader().readText()
        assertEquals(0, compile.waitFor(), output)
        assertEquals(0, ProcessBuilder(executable.toString()).redirectErrorStream(true).start().waitFor())
    }
}
