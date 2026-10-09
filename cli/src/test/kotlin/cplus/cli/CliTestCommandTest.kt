package cplus.cli

import cplus.compiler.LibcProfile
import cplus.compiler.RuntimeProfile
import java.nio.file.Files
import java.nio.file.Path
import java.io.ByteArrayOutputStream
import java.io.PrintStream
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

    @Test
    fun buildsIndependentProductsForRootsWithDuplicateMainAndDoesNotSelectImportedFixtures() {
        val directory = Files.createTempDirectory("cplus-cli-test-roots")
        val provider = Files.writeString(
            directory.resolve("provider.cp"),
            "pub int helper() { return 1; } test imported fixture { assert(1); }"
        )
        val first = Files.writeString(
            directory.resolve("first.cp"),
            "import { helper } from \"./provider.cp\"; int main() { return 0; } test first fixture { assert(helper()); }"
        )
        val second = Files.writeString(
            directory.resolve("second.cp"),
            "int main() { return 0; } test second fixture { assert(1); }"
        )
        val broken = Files.writeString(directory.resolve("broken.cp"), "int main( {")
        val capturedOut = ByteArrayOutputStream()
        val capturedErr = ByteArrayOutputStream()
        val originalOut = System.out
        val originalErr = System.err
        val arguments = requireNotNull(TestCommandArguments.parse(listOf(broken.toString(), first.toString(), second.toString())))
        val temporary = Files.createTempDirectory("cplus-test-products")
        val builds = try {
            System.setOut(PrintStream(capturedOut))
            System.setErr(PrintStream(capturedErr))
            Cli().buildTestProducts(arguments, temporary)
        } finally {
            System.setOut(originalOut)
            System.setErr(originalErr)
            Files.walk(temporary).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }

        assertEquals(listOf(broken, first, second), builds.map { it.root })
        assertTrue(builds[0].exitCode != 0, capturedErr.toString())
        assertEquals(0, builds[1].exitCode, "expected a successful root build: ${capturedOut}; ${capturedErr}")
        assertEquals(0, builds[2].exitCode, "expected a successful root build: ${capturedOut}; ${capturedErr}")
        assertEquals(1, builds[1].fixtures.size, "imported fixture was incorrectly selected")
        assertEquals(1, builds[2].fixtures.size)
        assertTrue(Files.isRegularFile(provider))
    }
}
