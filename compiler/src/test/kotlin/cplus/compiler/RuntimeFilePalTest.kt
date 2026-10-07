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

            int main(int argc, char** argv) {
                const char* source = "$sourceName";
                const char* renamed = "$renamedName";
                const char* text = "portable";
                char buffer[8];
                long long handle;
                long long result;
                (void)argc;
                (void)argv;
                handle = platform_file_open(source, CPLUS_FILE_WRITE | CPLUS_FILE_CREATE | CPLUS_FILE_TRUNCATE);
                if (handle < 0) return 11;
                result = platform_file_write(handle, text, 8);
                if (result != 8) return 12;
                if (platform_file_close(handle) != 0) return 13;
                if (platform_file_rename(source, renamed) != 0) return 14;
                handle = platform_file_open(renamed, CPLUS_FILE_READ);
                if (handle < 0) return 15;
                result = platform_file_read(handle, buffer, 8);
                if (result != 8) return 16;
                if (platform_file_close(handle) != 0) return 17;
                if (buffer[0] != 'p' || buffer[1] != 'o' || buffer[2] != 'r' || buffer[3] != 't') return 18;
                if (buffer[4] != 'a' || buffer[5] != 'b' || buffer[6] != 'l' || buffer[7] != 'e') return 19;
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
