package cplus.compiler

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

class LinuxAarch64SourceValidationTest {
    @Test
    fun runtimeSourcesAndStartupAssemblyPassAarch64ClangChecks() {
        assumeTrue(System.getProperty("os.name").contains("linux", ignoreCase = true))
        val clangAvailable = runCatching {
            ProcessBuilder("clang", "--version").start().waitFor() == 0
        }.getOrDefault(false)
        assumeTrue(clangAvailable, "Clang is not installed")

        val manifest = requireNotNull(SdkManifestLoader.load(SdkManifestLocator.defaultManifestPath()).manifest)
        val target = TargetInfo(targetTriple = "linux-aarch64")
        val resolution = requireNotNull(SdkResolver.resolve(manifest, target).resolution)
        val runtimeSources = Files.list(resolution.layout.runtimeSource).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".c") }
                .sorted()
                .toList()
        }
        val platformSources = Files.list(resolution.layout.platformSource).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".c") }
                .sorted()
                .toList()
        }
        val assemblySources = listOf(
            resolution.layout.startupSource.resolve("start.S"),
            resolution.layout.platformSource.resolve("thread-aarch64.S")
        )
        val temporary = Files.createTempDirectory("cplus-linux-aarch64-source-check")

        try {
            (runtimeSources + platformSources).forEach { source ->
                val process = ProcessBuilder(
                    "clang", "--target=aarch64-unknown-linux-gnu", "-std=c17", "-Wall", "-Wextra",
                    "-Werror", "-ffreestanding", "-fno-builtin", "-fno-stack-protector", "-fsyntax-only",
                    "-I", resolution.layout.libcInclude.toString(),
                    "-I", resolution.layout.runtimeInclude.toString(),
                    source.toString()
                ).redirectErrorStream(true).start()
                val output = process.inputStream.bufferedReader().readText()
                assertEquals(0, process.waitFor(), "${source.fileName}: $output")
            }

            assemblySources.forEachIndexed { index, source ->
                val objectFile = temporary.resolve("aarch64-$index.o")
                val process = ProcessBuilder(
                    "clang", "--target=aarch64-unknown-linux-gnu", "-c", source.toString(),
                    "-o", objectFile.toString()
                ).redirectErrorStream(true).start()
                val output = process.inputStream.bufferedReader().readText()
                assertEquals(0, process.waitFor(), "${source.fileName}: $output")
                assertTrue(Files.isRegularFile(objectFile), "${source.fileName} produced no object")
            }
        } finally {
            Files.walk(temporary).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }
}
