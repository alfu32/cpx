package cplus.compiler

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RuntimeFilePalTest {
    @Test
    fun selfHostedPalOpensReadsWritesAndRenamesCanonicalSlashPaths() {
        val manifest = requireNotNull(SdkManifestLoader.load(SdkManifestLocator.defaultManifestPath()).manifest)
        val target = TargetInfo(targetTriple = defaultHostTargetTriple())
        val resolution = requireNotNull(SdkResolver.resolve(manifest, target).resolution)
        val plan = requireNotNull(RuntimeLinker.plan(resolution, target).plan)
        val directory = Files.createTempDirectory("cplus-file-pal")
        val sourcePath = directory.resolve("source.txt").toAbsolutePath().normalize()
        val renamedPath = directory.resolve("renamed.txt").toAbsolutePath().normalize()
        val source = directory.resolve("file-pal.c")
        val executable = directory.resolve(if (target.targetTriple.startsWith("windows-")) "file-pal.exe" else "file-pal")
        val sourceName = cString(sourcePath.toString().replace('\\', '/'))
        val renamedName = cString(renamedPath.toString().replace('\\', '/'))
        Files.writeString(source, """
            #include "cplus_platform.h"

            long long std_fs_open(const char* path, unsigned long long mode);
            long long std_fs_read(long long handle, void* buffer, long long size);
            long long std_fs_write(long long handle, const void* buffer, long long size);
            int std_fs_close(long long handle);
            int std_fs_rename(const char* source, const char* target);

            int main(int argc, char** argv) {
                const char* source = "$sourceName";
                const char* renamed = "$renamedName";
                const char* text = "portable";
                char buffer[8];
                long long handle;
                long long result;
                long long position;
                cplus_file_metadata_t metadata;
                (void)argc;
                (void)argv;
                if (CPLUS_PAL_API_VERSION != 3) return 10;
                handle = std_fs_open(source, CPLUS_FILE_WRITE | CPLUS_FILE_CREATE | CPLUS_FILE_TRUNCATE);
                if (handle < 0) return 11;
                result = std_fs_write(handle, text, 8);
                if (result != 8) return 12;
                if (std_fs_close(handle) != 0) return 13;
                if (platform_file_metadata(source, &metadata) != 0) return 20;
                if (metadata.size_bytes != 8 || metadata.kind != CPLUS_FILE_KIND_REGULAR) return 21;
                if (metadata.modified_nanoseconds > 999999999U || metadata.reserved0 != 0 || metadata.reserved1 != 0) return 22;
                if (platform_file_seek(-1, 0, CPLUS_SEEK_BEGIN) != CPLUS_PAL_INVALID_ARGUMENT) return 23;
                if (std_fs_rename(source, renamed) != 0) return 14;
                handle = std_fs_open(renamed, CPLUS_FILE_READ);
                if (handle < 0) return 15;
                result = std_fs_read(handle, buffer, 3);
                if (result != 3 || buffer[0] != 'p' || buffer[1] != 'o' || buffer[2] != 'r') return 16;
                position = platform_file_seek(handle, 1, CPLUS_SEEK_CURRENT);
                if (position != 4) return 24;
                result = std_fs_read(handle, buffer, 4);
                if (result != 4 || buffer[0] != 'a' || buffer[1] != 'b' || buffer[2] != 'l' || buffer[3] != 'e') return 25;
                position = platform_file_seek(handle, -5, CPLUS_SEEK_END);
                if (position != 3) return 26;
                result = std_fs_read(handle, buffer, 5);
                if (result != 5 || buffer[0] != 't' || buffer[1] != 'a' || buffer[2] != 'b' || buffer[3] != 'l' || buffer[4] != 'e') return 27;
                if (std_fs_close(handle) != 0) return 17;
                return 0;
            }
        """.trimIndent())

        try {
            val link = LinkDriver.link(
                LinkRequest(
                    generatedSource = source,
                    output = executable,
                    target = target,
                    sdk = resolution
                ),
                plan
            )
            assertTrue(link.isSuccessful, link.output)
            val process = ProcessBuilder(executable.toString()).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(0, process.waitFor(), output)
            assertTrue(Files.exists(renamedPath))
            assertEquals("portable", Files.readString(renamedPath))
        } finally {
            Files.deleteIfExists(sourcePath)
            Files.deleteIfExists(renamedPath)
            Files.deleteIfExists(executable)
            Files.deleteIfExists(source)
            Files.deleteIfExists(directory)
        }
    }

    private fun cString(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")
}
