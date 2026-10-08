package cplus.cli

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VersionCommandTest {
    @Test
    fun versionSubcommandPrintsGeneratedBuildMetadata() {
        val output = captureStdout { assertEquals(0, Cli().run(listOf("version"))) }
        val version = Version.current

        assertTrue(output.contains("git commit:       ${version.gitCommit}"))
        assertTrue(output.contains("short commit:     ${version.gitShortCommit}"))
        assertTrue(output.contains("commit date:      ${version.gitCommitDate}"))
        assertTrue(output.contains("build date:       ${version.buildDate}"))
        assertTrue(output.contains("codename:         ${version.codename}"))
        assertTrue(version.gitCommitDate.matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")))
        assertTrue(version.buildDate.matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")))
        assertTrue(version.codename.isNotBlank())
    }

    @Test
    fun noArgumentsIncludesVersionInHelp() {
        val output = captureStdout { assertEquals(0, Cli().run(emptyList())) }

        assertTrue(output.contains("C+ CLI version"))
        assertTrue(output.contains("usage: cplus <command>"))
        assertTrue(output.contains("  version     print build and source-control version metadata"))
    }

    private fun captureStdout(block: () -> Unit): String {
        val original = System.out
        val bytes = ByteArrayOutputStream()
        System.setOut(PrintStream(bytes, true, Charsets.UTF_8))
        return try {
            block()
            bytes.toString(Charsets.UTF_8)
        } finally {
            System.setOut(original)
        }
    }
}
