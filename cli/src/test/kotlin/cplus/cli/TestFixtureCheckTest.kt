package cplus.cli

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestFixtureCheckTest {
    @Test
    fun checkReportsFixtureSemanticErrorsWithoutExecutingBodies() {
        val root = Files.createTempDirectory("cplus-check-fixture")
        val source = root.resolve("main.cp")
        Files.writeString(
            source,
            """
                int mark_fixture_execution();
                test checked but not run {
                    mark_fixture_execution();
                    return 42;
                }
                int main() { return 0; }
            """.trimIndent()
        )

        val diagnostics = ByteArrayOutputStream()
        val previousError = System.err
        val exitCode = try {
            System.setErr(PrintStream(diagnostics))
            Cli().run(listOf("check", source.toString()))
        } finally {
            System.setErr(previousError)
        }

        assertEquals(1, exitCode)
        assertTrue(diagnostics.toString().contains("SEM202"), diagnostics.toString())
    }

    @Test
    fun validFixtureIsCheckedWithoutLinkingOrExecutingItsBody() {
        val root = Files.createTempDirectory("cplus-check-fixture-valid")
        val source = root.resolve("main.cp")
        Files.writeString(
            source,
            """
                int mark_fixture_execution();
                test checked but not run { mark_fixture_execution(); }
                int main() { return 0; }
            """.trimIndent()
        )

        assertEquals(0, Cli().run(listOf("check", source.toString())))
    }
}
