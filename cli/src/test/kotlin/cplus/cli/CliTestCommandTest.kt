package cplus.cli

import cplus.compiler.LibcProfile
import cplus.compiler.RuntimeProfile
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CliTestCommandTest {
    @Test
    fun parsesOrderedNormalizedDeduplicatedRootsAndPathsWithSpaces() {
        val directory = Files.createTempDirectory("cplus test cli")
        val first = Files.writeString(directory.resolve("first root.cp"), "int main() { return 0; }")
        val second = Files.writeString(directory.resolve("second.cp"), "int main() { return 0; }")

        val parsed = TestCommandArguments.parse(
            listOf("first root.cp", "./first root.cp", "second.cp"),
            workingDirectory = directory
        )

        assertEquals(listOf(first.toAbsolutePath().normalize(), second.toAbsolutePath().normalize()), parsed?.roots)
        assertEquals(30, parsed?.timeoutSeconds)
    }

    @Test
    fun delimiterAllowsOptionLookingRootAndQuotedWildcardIsNeverExpanded() {
        val directory = Files.createTempDirectory("cplus-test-cli-glob")
        val literalGlob = Files.writeString(directory.resolve("*.cp"), "int main() { return 0; }")
        val other = Files.writeString(directory.resolve("other.cp"), "int main() { return 0; }")

        val glob = TestCommandArguments.parse(listOf("*.cp"), directory)
        val afterDelimiter = TestCommandArguments.parse(listOf("--", "*.cp", "other.cp"), directory)

        assertEquals(listOf(literalGlob.toAbsolutePath().normalize()), glob?.roots)
        assertEquals(listOf(literalGlob.toAbsolutePath().normalize(), other.toAbsolutePath().normalize()), afterDelimiter?.roots)
    }

    @Test
    fun parsesTimeoutSharedSdkAndTargetConfiguration() {
        val directory = Files.createTempDirectory("cplus-test-cli-config")
        val root = Files.writeString(directory.resolve("main.cp"), "int main() { return 0; }")
        val parsed = TestCommandArguments.parse(
            listOf(
                "main.cp", "--timeout", "9", "--sdk", "sdk/manifest/sdk.toml", "--target", "linux-x86_64",
                "--runtime", "freestanding", "--libc", "none", "--c-compiler", "clang",
                "--sysroot", "sysroot", "-I", "include", "--c-source", "shim.c", "-lm"
            ),
            directory
        )

        assertEquals(listOf(root.toAbsolutePath().normalize()), parsed?.roots)
        assertEquals(9, parsed?.timeoutSeconds)
        assertEquals(directory.resolve("sdk/manifest/sdk.toml").toAbsolutePath().normalize(), parsed?.sdkManifest)
        assertEquals(directory.resolve("sysroot").toAbsolutePath().normalize(), parsed?.externalSysroot)
        assertEquals("linux-x86_64", parsed?.target?.targetTriple)
        assertEquals(RuntimeProfile.FREESTANDING, parsed?.target?.buildProfile?.runtime)
        assertEquals(LibcProfile.NONE, parsed?.target?.buildProfile?.libc)
        assertEquals("clang", parsed?.cCompiler)
        assertEquals(listOf("m"), parsed?.libraries)
    }

    @Test
    fun rejectsMissingRootsFilesInvalidTimeoutAndOutputOptions() {
        val directory = Files.createTempDirectory("cplus-test-cli-invalid")
        val root = Files.writeString(directory.resolve("main.cp"), "int main() { return 0; }")

        assertNull(TestCommandArguments.parse(emptyList(), directory))
        assertNull(TestCommandArguments.parse(listOf("missing.cp"), directory))
        assertNull(TestCommandArguments.parse(listOf("'*.cp'"), directory))
        assertNull(TestCommandArguments.parse(listOf(root.toString(), "--timeout", "0"), directory))
        assertNull(TestCommandArguments.parse(listOf(root.toString(), "--timeout", "1.5"), directory))
        assertNull(TestCommandArguments.parse(listOf(root.toString(), "--output", "out"), directory))
        assertNull(TestCommandArguments.parse(listOf(root.toString(), "--mystery"), directory))
    }

    @Test
    fun projectConfigurationDoesNotInsertAnImplicitRoot() {
        val directory = Files.createTempDirectory("cplus-test-cli-project")
        val manifest = Files.writeString(directory.resolve("cplus.toml"), "[project]\nentry = \"src/main.cp\"\n")
        val root = Files.writeString(directory.resolve("explicit.cp"), "int main() { return 0; }")

        val parsed = TestCommandArguments.parse(listOf("explicit.cp", "--project", "cplus.toml"), directory)

        assertEquals(listOf(root.toAbsolutePath().normalize()), parsed?.roots)
        assertEquals(manifest.toAbsolutePath().normalize(), parsed?.projectManifest)
        assertTrue(parsed?.workspaceManifest == null)
    }
}
