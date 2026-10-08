package cplus.compiler

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

class NativeStdTest {
    @Test
    fun targetNeutralCoreMemoryStringTextAndCollectionsExecuteOnLinuxAarch64WhenAvailable() {
        assumeTrue(System.getProperty("os.name").contains("linux", ignoreCase = true))
        val runner = C17TargetRunner.commandPrefix("linux-aarch64")
        assumeTrue(runner != null, "AArch64 QEMU user-mode runner is unavailable")
        assumeTrue(runCCompiler("clang"), "Clang is unavailable")

        val root = SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize().parent!!.parent!!
        val target = TargetInfo(targetTriple = "linux-aarch64")
        assumeTrue(CCompilerToolchains.targetLinkerFlags(target, "clang").isNotEmpty(), "LLD is unavailable")
        val manifest = requireNotNull(SdkManifestLoader.load(SdkManifestLocator.defaultManifestPath()).manifest)
        val resolution = requireNotNull(SdkResolver.resolve(manifest, target).resolution)
        val plan = requireNotNull(RuntimeLinker.plan(resolution, target).plan)
        val sdkSources = listOf("core.cp", "mem.cp", "string.cp", "text.cp", "collections.cp").map {
            root.resolve("std/src").resolve(it)
        }
        val result = CPlusCompiler().compile(CompileRequest(sdkSources, target = target))
        assertTrue(result.isSuccessful, result.diagnostics.joinToString())

        val directory = Files.createTempDirectory("cplus-native-std-aarch64")
        val generatedSource = directory.resolve("native_std.c")
        val executable = directory.resolve("native_std")
        Files.writeString(generatedSource, result.generatedUnits.single().text + """
            int main(void) {
                char memory[12] = "abcdefgh";
                struct std_memory_span_t null_span = std_memory_span((void*)0, 4);
                struct std_range_t full_range = std_range(std_isize_min(), std_isize_max());
                if (sizeof(usize) != 8 || sizeof(isize) != 8 || std_pointer_width_bits() != 64) return 1;
                if (std_memory_span_at(null_span, 0) != (void*)0) return 2;
                if (std_range_length(full_range) != std_usize_max()) return 3;
                std_mem_move(memory + 2, memory, 9);
                if (std_string_compare(memory, "ababcdefgh") != 0) return 4;
                std_mem_move(memory, memory + 2, 9);
                if (std_string_compare(memory, "abcdefgh") != 0) return 5;
                if (std_mem_move(memory, memory, 9) != memory) return 6;
                return 0;
            }
        """.trimIndent())

        try {
            val link = LinkDriver.link(LinkRequest(generatedSource, executable, target, resolution), plan)
            assertTrue(link.isSuccessful, link.output)
            val descriptor = resolution.targetDescriptor
                ?: requireNotNull(TargetRegistry.load(resolution.layout.abiDescriptor).descriptor)
            val audit = RuntimeDependencyAuditor.inspect(executable, descriptor, target.buildProfile)
            assertTrue(audit.isSuccessful, audit.diagnostics.joinToString())

            val process = ProcessBuilder(runner!! + executable.toString()).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(0, process.waitFor(), output)
        } finally {
            Files.deleteIfExists(executable)
            Files.deleteIfExists(generatedSource)
            Files.deleteIfExists(directory)
        }
    }

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
                    if (std_memory_span_at(std_memory_span((void*)0, 2), 0) != (void*)0) return 29;
                    if (std_raw_memory_as_bytes(raw).length != 6) return 18;
                    std_mem_copy(copy, source, 7);
                    if (!std_mem_equal(copy, source, 7)) return 19;
                    std_mem_move(overlap + 1, overlap, 7);
                    if (std_string_compare(overlap, "aabcdef") != 0) return 20;
                    if (std_mem_compare(overlap + 1, "abcdef", 7) != 0) return 21;
                    std_mem_move(overlap, overlap + 1, 7);
                    if (std_string_compare(overlap, "abcdef") != 0) return 30;
                    if (std_mem_move(overlap, overlap, 7) != overlap) return 31;
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
        val compilers = listOf("cc", "clang").filter(::runCCompiler)
        assertTrue(compilers.isNotEmpty(), "neither cc nor clang is available")
        try {
            compilers.forEach { compiler ->
                val executable = directory.resolve("native_std_${compiler.replace('/', '_')}")
                val compile = ProcessBuilder(
                    compiler, "-std=c17", "-fsanitize=signed-integer-overflow",
                    "-fno-sanitize-recover=signed-integer-overflow",
                    "-I", root.resolve("libc/include").toString(),
                    combined.toString(), "-o", executable.toString()
                )
                    .redirectErrorStream(true)
                    .start()
                val output = compile.inputStream.bufferedReader().readText()
                assertEquals(0, compile.waitFor(), "$compiler: $output")
                val run = ProcessBuilder(executable.toString()).redirectErrorStream(true).start()
                val runOutput = run.inputStream.bufferedReader().readText()
                assertEquals(0, run.waitFor(), "$compiler: $runOutput")
                Files.deleteIfExists(executable)
            }
        } finally {
            Files.list(directory).use { paths -> paths.forEach { Files.deleteIfExists(it) } }
            Files.deleteIfExists(directory)
        }
    }

    private fun runCCompiler(compiler: String): Boolean = runCatching {
        ProcessBuilder(compiler, "--version").start().waitFor() == 0
    }.getOrDefault(false)
}
