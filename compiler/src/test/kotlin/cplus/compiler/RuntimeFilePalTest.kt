package cplus.compiler

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

class RuntimeFilePalTest {
    @Test
    fun linuxFilesystemPermissionFailuresUseAccessDeniedPalCode() {
        assumeTrue(System.getProperty("os.name").contains("linux", ignoreCase = true))
        assumeTrue(!System.getProperty("user.name").equals("root", ignoreCase = true), "root bypasses POSIX permission checks")
        val manifest = requireNotNull(SdkManifestLoader.load(SdkManifestLocator.defaultManifestPath()).manifest)
        val target = TargetInfo(targetTriple = "linux-x86_64")
        val resolution = requireNotNull(SdkResolver.resolve(manifest, target).resolution)
        val plan = requireNotNull(RuntimeLinker.plan(resolution, target).plan)
        val directory = Files.createTempDirectory("cplus-file-pal-access")
        val deniedDirectory = Files.createDirectory(directory.resolve("denied"))
        val deniedFile = deniedDirectory.resolve("secret.txt").toAbsolutePath().normalize()
        val source = directory.resolve("access-pal.c")
        val executable = directory.resolve("access-pal")
        val directoryName = cString(deniedDirectory.toAbsolutePath().normalize().toString())
        val fileName = cString(deniedFile.toString())
        Files.setPosixFilePermissions(deniedDirectory, PosixFilePermissions.fromString("---------"))
        Files.writeString(source, """
            #include "cplus_platform.h"

            int main(void) {
                const char* directory = "$directoryName";
                const char* file = "$fileName";
                cplus_file_metadata_t metadata;
                if (platform_file_open(file, CPLUS_FILE_READ) != CPLUS_PAL_ACCESS_DENIED) return 1;
                if (platform_file_metadata(file, &metadata) != CPLUS_PAL_ACCESS_DENIED) return 2;
                if (platform_directory_open(directory) != CPLUS_PAL_ACCESS_DENIED) return 3;
                if (platform_directory_create(file) != CPLUS_PAL_ACCESS_DENIED) return 4;
                return 0;
            }
        """.trimIndent())

        try {
            val link = LinkDriver.link(LinkRequest(source, executable, target, resolution), plan)
            assertTrue(link.isSuccessful, link.output)
            val process = ProcessBuilder(executable.toString()).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(0, process.waitFor(), output)
            val descriptor = resolution.targetDescriptor
                ?: requireNotNull(TargetRegistry.load(resolution.layout.abiDescriptor).descriptor)
            val audit = RuntimeDependencyAuditor.inspect(executable, descriptor, target.buildProfile)
            assertTrue(audit.isSuccessful, audit.diagnostics.joinToString())
        } finally {
            Files.setPosixFilePermissions(deniedDirectory, PosixFilePermissions.fromString("rwx------"))
            Files.deleteIfExists(deniedFile)
            Files.deleteIfExists(executable)
            Files.deleteIfExists(source)
            Files.deleteIfExists(deniedDirectory)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun linuxMetadataFollowsSymlinksAndFileRemovalUnlinksOnlyTheSymlink() {
        assumeTrue(System.getProperty("os.name").contains("linux", ignoreCase = true))
        val manifest = requireNotNull(SdkManifestLoader.load(SdkManifestLocator.defaultManifestPath()).manifest)
        val target = TargetInfo(targetTriple = "linux-x86_64")
        val resolution = requireNotNull(SdkResolver.resolve(manifest, target).resolution)
        val plan = requireNotNull(RuntimeLinker.plan(resolution, target).plan)
        val directory = Files.createTempDirectory("cplus-file-pal-symlink")
        val targetPath = directory.resolve("target.txt").toAbsolutePath().normalize()
        val linkPath = directory.resolve("alias.txt").toAbsolutePath().normalize()
        val source = directory.resolve("symlink-pal.c")
        val executable = directory.resolve("symlink-pal")
        Files.writeString(targetPath, "data")
        Files.createSymbolicLink(linkPath, targetPath)
        val targetName = cString(targetPath.toString())
        val linkName = cString(linkPath.toString())
        Files.writeString(source, """
            #include "cplus_platform.h"
            #include <stdint.h>

            int main(void) {
                cplus_file_metadata_t metadata;
                const char* target = "$targetName";
                const char* alias = "$linkName";
                if (platform_file_metadata(alias, &metadata) != 0) return 1;
                if (metadata.kind != CPLUS_FILE_KIND_REGULAR || metadata.size_bytes != 4) return 2;
                if (platform_file_remove(alias) != 0) return 3;
                if (platform_file_metadata(alias, &metadata) != CPLUS_PAL_NOT_FOUND) return 4;
                if (platform_file_metadata(target, &metadata) != 0) return 5;
                if (metadata.kind != CPLUS_FILE_KIND_REGULAR || metadata.size_bytes != 4) return 6;
                return 0;
            }
        """.trimIndent())

        try {
            val link = LinkDriver.link(LinkRequest(source, executable, target, resolution), plan)
            assertTrue(link.isSuccessful, link.output)
            val process = ProcessBuilder(executable.toString()).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(0, process.waitFor(), output)
            val descriptor = resolution.targetDescriptor
                ?: requireNotNull(TargetRegistry.load(resolution.layout.abiDescriptor).descriptor)
            val audit = RuntimeDependencyAuditor.inspect(executable, descriptor, target.buildProfile)
            assertTrue(audit.isSuccessful, audit.diagnostics.joinToString())
            assertTrue(Files.exists(targetPath))
            assertTrue(!Files.exists(linkPath))
        } finally {
            Files.deleteIfExists(linkPath)
            Files.deleteIfExists(targetPath)
            Files.deleteIfExists(executable)
            Files.deleteIfExists(source)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun selfHostedPalOpensReadsWritesAndRenamesCanonicalSlashPaths() {
        val manifest = requireNotNull(SdkManifestLoader.load(SdkManifestLocator.defaultManifestPath()).manifest)
        val target = TargetInfo(targetTriple = defaultHostTargetTriple())
        val resolution = requireNotNull(SdkResolver.resolve(manifest, target).resolution)
        val plan = requireNotNull(RuntimeLinker.plan(resolution, target).plan)
        val directory = Files.createTempDirectory("cplus-file-pal")
        val nestedPath = directory.resolve("nested").toAbsolutePath().normalize()
        val sourcePath = nestedPath.resolve("source.txt").toAbsolutePath().normalize()
        val renamedPath = nestedPath.resolve("renamed.txt").toAbsolutePath().normalize()
        val missingPath = nestedPath.resolve("missing.txt").toAbsolutePath().normalize()
        val notDirectoryPath = sourcePath.resolve("child").toAbsolutePath().normalize()
        val source = directory.resolve("file-pal.c")
        val executable = directory.resolve(if (target.targetTriple.startsWith("windows-")) "file-pal.exe" else "file-pal")
        val nestedName = cString(nestedPath.toString().replace('\\', '/'))
        val sourceName = cString(sourcePath.toString().replace('\\', '/'))
        val renamedName = cString(renamedPath.toString().replace('\\', '/'))
        val missingName = cString(missingPath.toString().replace('\\', '/'))
        val notDirectoryName = cString(notDirectoryPath.toString().replace('\\', '/'))
        Files.writeString(source, """
            #include "cplus_platform.h"
            #include <stddef.h>
            #include <stdint.h>

            int64_t std_fs_open(const char* path, uint64_t mode);
            ptrdiff_t std_fs_read(int64_t handle, void* buffer, size_t size);
            ptrdiff_t std_fs_write(int64_t handle, const void* buffer, size_t size);
            int std_fs_close(int64_t handle);
            int std_fs_rename(const char* source, const char* target);

            int main(int argc, char** argv) {
                const char* source = "$sourceName";
                const char* renamed = "$renamedName";
                const char* nested = "$nestedName";
                const char* missing = "$missingName";
                const char* not_directory = "$notDirectoryName";
                const char* text = "portable";
                char buffer[8];
                char entry[32];
                char tiny[2];
                char invalid_utf8_path[3] = { (char)0xc0, (char)0xaf, '\0' };
                long long handle;
                long long result;
                long long position;
                long long directory_handle;
                cplus_file_metadata_t metadata;
                (void)argc;
                (void)argv;
                if (CPLUS_PAL_API_VERSION != 4) return 10;
                if (platform_file_open("", CPLUS_FILE_READ) != CPLUS_PAL_INVALID_ARGUMENT) return 38;
                if (platform_file_open(source, 0) != CPLUS_PAL_INVALID_ARGUMENT) return 39;
                if (platform_file_open(source, CPLUS_FILE_READ | CPLUS_FILE_TRUNCATE) != CPLUS_PAL_INVALID_ARGUMENT) return 40;
                if (platform_file_open(source, CPLUS_FILE_READ | 0x10ULL) != CPLUS_PAL_INVALID_ARGUMENT) return 41;
                if (platform_file_open(invalid_utf8_path, CPLUS_FILE_READ) != CPLUS_PAL_INVALID_ARGUMENT) return 42;
                if (platform_file_open(missing, CPLUS_FILE_READ) != CPLUS_PAL_NOT_FOUND) return 43;
                if (platform_file_metadata(missing, &metadata) != CPLUS_PAL_NOT_FOUND) return 44;
                if (platform_file_remove(missing) != CPLUS_PAL_NOT_FOUND) return 45;
                if (platform_directory_open(missing) != CPLUS_PAL_NOT_FOUND) return 46;
                if (platform_file_rename(missing, renamed) != CPLUS_PAL_NOT_FOUND) return 47;
                if (platform_file_read(-1, buffer, 1) != CPLUS_PAL_INVALID_ARGUMENT) return 48;
                if (platform_file_write(-1, text, 1) != CPLUS_PAL_INVALID_ARGUMENT) return 49;
                if (platform_file_close(-1) != CPLUS_PAL_INVALID_ARGUMENT) return 50;
                if (platform_file_seek(-1, 0, CPLUS_SEEK_BEGIN) != CPLUS_PAL_INVALID_ARGUMENT) return 51;
                if (platform_file_read(0x7fffffffffffffffLL, buffer, 1) != CPLUS_PAL_INVALID_ARGUMENT) return 55;
                if (platform_file_write(0x7fffffffffffffffLL, text, 1) != CPLUS_PAL_INVALID_ARGUMENT) return 56;
                if (platform_file_close(0x7fffffffffffffffLL) != CPLUS_PAL_INVALID_ARGUMENT) return 57;
                if (platform_file_seek(0x7fffffffffffffffLL, 0, CPLUS_SEEK_BEGIN) != CPLUS_PAL_INVALID_ARGUMENT) return 58;
                if (platform_directory_create(nested) != 0) return 28;
                handle = std_fs_open(source, CPLUS_FILE_WRITE | CPLUS_FILE_CREATE | CPLUS_FILE_TRUNCATE);
                if (handle < 0) return 11;
                if (platform_file_read(handle, (void*)0, 1) != CPLUS_PAL_INVALID_ARGUMENT) return 52;
                if (platform_file_write(handle, (const void*)0, 1) != CPLUS_PAL_INVALID_ARGUMENT) return 53;
                if (platform_file_read(handle, (void*)0, 0) != 0 || platform_file_write(handle, (const void*)0, 0) != 0) return 54;
                result = std_fs_write(handle, text, 8);
                if (result != 8) return 12;
                if (std_fs_close(handle) != 0) return 13;
                if (platform_file_metadata(source, &metadata) != 0) return 20;
                if (metadata.size_bytes != 8 || metadata.kind != CPLUS_FILE_KIND_REGULAR) return 21;
                if (metadata.modified_nanoseconds > 999999999U || metadata.reserved0 != 0 || metadata.reserved1 != 0) return 22;
                if (platform_file_open(not_directory, CPLUS_FILE_READ) != CPLUS_PAL_NOT_FOUND) return 59;
                if (platform_file_metadata(not_directory, &metadata) != CPLUS_PAL_NOT_FOUND) return 60;
                if (platform_directory_open(not_directory) != CPLUS_PAL_NOT_FOUND) return 61;
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
                if (platform_file_metadata(nested, &metadata) != 0 || metadata.kind != CPLUS_FILE_KIND_DIRECTORY) return 29;
                directory_handle = platform_directory_open(nested);
                if (directory_handle < 0) return 30;
                result = platform_directory_read(directory_handle, tiny, sizeof(tiny));
                if (result != CPLUS_PAL_BUFFER_TOO_SMALL) return 31;
                result = platform_directory_read(directory_handle, entry, sizeof(entry));
                if (result != 11 || entry[0] != 'r' || entry[1] != 'e' || entry[2] != 'n' || entry[3] != 'a' ||
                    entry[4] != 'm' || entry[5] != 'e' || entry[6] != 'd' || entry[7] != '.' ||
                    entry[8] != 't' || entry[9] != 'x' || entry[10] != 't' || entry[11] != '\0') return 32;
                if (platform_directory_read(directory_handle, entry, sizeof(entry)) != 0) return 33;
                if (platform_directory_close(directory_handle) != 0) return 34;
                if (platform_directory_remove(nested) >= 0) return 35;
                if (platform_file_remove(renamed) != 0) return 36;
                if (platform_directory_remove(nested) != 0) return 37;
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
            val descriptor = resolution.targetDescriptor
                ?: requireNotNull(TargetRegistry.load(resolution.layout.abiDescriptor).descriptor)
            val audit = RuntimeDependencyAuditor.inspect(executable, descriptor, target.buildProfile)
            assertTrue(audit.isSuccessful, audit.diagnostics.joinToString())
            assertTrue(!Files.exists(renamedPath))
            assertTrue(!Files.exists(nestedPath))
        } finally {
            Files.deleteIfExists(sourcePath)
            Files.deleteIfExists(renamedPath)
            Files.deleteIfExists(nestedPath)
            Files.deleteIfExists(executable)
            Files.deleteIfExists(source)
            Files.deleteIfExists(directory)
        }
    }

    private fun cString(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")
}
