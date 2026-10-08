package cplus.compiler

import cplus.core.Lexer
import cplus.core.Parser
import cplus.core.SourceFile
import cplus.core.SourceFileId
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RuntimeStdIoTest {
    @Test
    fun cplusStdIoStreamsForwardThroughThePortableFileFacade() {
        val manifestPath = SdkManifestLocator.defaultManifestPath()
        val manifest = requireNotNull(SdkManifestLoader.load(manifestPath).manifest)
        val target = TargetInfo(targetTriple = defaultHostTargetTriple())
        val resolution = requireNotNull(SdkResolver.resolve(manifest, target).resolution)
        val plan = requireNotNull(RuntimeLinker.plan(resolution, target).plan)
        val root = manifestPath.toAbsolutePath().normalize().parent!!.parent!!
        val ioPath = root.resolve("std/src/io.cp")
        val ioText = Files.readString(ioPath)
        val ioSyntax = Parser(Lexer().lex(SourceFile(SourceFileId(1), ioPath, ioText, 1))).parse()
        assertTrue(ioSyntax.diagnostics.isEmpty(), ioSyntax.diagnostics.joinToString())
        val directory = Files.createTempDirectory("cplus-std-io")
        val nestedPath = directory.resolve("fs-dir").toAbsolutePath().normalize()
        val dataPath = nestedPath.resolve("stream-data.txt").toAbsolutePath().normalize()
        val generatedC = directory.resolve("std-io.c")
        val executable = directory.resolve(if (target.targetTriple.startsWith("windows-")) "std-io.exe" else "std-io")
        val mainSource = directory.resolve("main.cp").also { path ->
            Files.writeString(
                path,
                """
                    import {
                        std_file_stream_close,
                        std_file_stream_is_open,
                        std_file_stream_open,
                        std_file_stream_read,
                        std_file_stream_seek,
                        std_file_stream_t,
                        std_file_stream_write
                    } from std.io;
                    import {
                        std_file_metadata_t,
                        std_fs_create_directory,
                        std_fs_directory_close,
                        std_fs_directory_open,
                        std_fs_directory_read,
                        std_fs_error_buffer_too_small,
                        std_fs_metadata,
                        std_fs_remove_directory,
                        std_fs_remove_file
                    } from std.fs;

                    int main() {
                        char* path = "${cString(dataPath.toString().replace('\\', '/'))}";
                        char* nested = "${cString(nestedPath.toString().replace('\\', '/'))}";
                        char buffer[7];
                        char entry[32];
                        char tiny[2];
                        std_file_metadata_t metadata;
                        if (std_fs_create_directory(nested) != 0) return 12;
                        std_file_stream_t stream = std_file_stream_open(path, 0, 1, 1, 1);
                        if (!std_file_stream_is_open(&stream)) return 1;
                        if (std_file_stream_read(&stream, buffer, 1) != -2) return 11;
                        if (std_file_stream_write(&stream, "portable", 8) != 8) return 2;
                        if (std_file_stream_seek(&stream, 0, 0) != 0) return 3;
                        if (std_file_stream_close(&stream) != 0 || std_file_stream_is_open(&stream)) return 4;
                        std_file_stream_t read_stream = std_file_stream_open(path, 1, 0, 0, 0);
                        if (!std_file_stream_is_open(&read_stream)) return 5;
                        if (std_file_stream_read(&read_stream, buffer, 7) != 7) return 6;
                        if (buffer[0] != 'p' || buffer[1] != 'o' || buffer[2] != 'r' || buffer[3] != 't' ||
                            buffer[4] != 'a' || buffer[5] != 'b' || buffer[6] != 'l') return 7;
                        if (std_file_stream_close(&read_stream) != 0) return 8;
                        if (std_fs_metadata(path, &metadata) != 0 || metadata.size_bytes != 8 ||
                            metadata.kind != 1 || metadata.reserved0 != 0 || metadata.reserved1 != 0) return 9;
                        int64_t directory_handle = std_fs_directory_open(nested);
                        if (directory_handle < 0) return 13;
                        if (std_fs_directory_read(directory_handle, tiny, sizeof(tiny)) != std_fs_error_buffer_too_small()) return 14;
                        int64_t entry_length = std_fs_directory_read(directory_handle, entry, sizeof(entry));
                        if (entry_length != 15 || entry[0] != 's' || entry[6] != '-' || entry[14] != 't' || entry[15] != '\0') return 15;
                        if (std_fs_directory_read(directory_handle, entry, sizeof(entry)) != 0) return 16;
                        if (std_fs_directory_close(directory_handle) != 0) return 17;
                        if (std_fs_remove_directory(nested) >= 0) return 18;
                        if (std_fs_remove_file(path) != 0) return 10;
                        if (std_fs_remove_directory(nested) != 0) return 19;
                        return 0;
                    }
                """.trimIndent()
            )
        }

        try {
            val sources = listOf(
                root.resolve("std/src/core.cp"),
                root.resolve("std/src/fs.cp"),
                ioPath,
                mainSource
            )
            val compilation = CPlusCompiler().compile(CompileRequest(sources, target))
            assertTrue(compilation.isSuccessful, compilation.diagnostics.joinToString())
            assertEquals(1, compilation.generatedUnits.size)
            Files.writeString(generatedC, compilation.generatedUnits.single().text)

            val link = LinkDriver.link(
                LinkRequest(generatedC, executable, target, resolution),
                plan
            )
            assertTrue(link.isSuccessful, link.output)
            val process = ProcessBuilder(executable.toString()).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(0, process.waitFor(), output)
            assertTrue(!Files.exists(dataPath))
            assertTrue(!Files.exists(nestedPath))
        } finally {
            Files.deleteIfExists(dataPath)
            Files.deleteIfExists(nestedPath)
            Files.deleteIfExists(executable)
            Files.deleteIfExists(generatedC)
            Files.deleteIfExists(mainSource)
            Files.deleteIfExists(directory)
        }
    }

    private fun cString(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")
}
