package cplus.cli

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CliTestReportTest {
    @Test
    fun rendersMultipleRootsFixturesAllAssertionFormsEscapedLabelsAndOutputBoundaries() {
        val directory = Files.createTempDirectory("cplus report roots with spaces")
        val first = Files.writeString(
            directory.resolve("first root.cp"),
            """
                import { printf } from c.stdio;
                int main() { return 0; }
                test first fixture with spaces {
                    printf("user output");
                    assert(1);
                    assert("line\nlabel", 1);
                    assertEquals(7, 7);
                    assertEquals("equality label", 8, 8);
                }
                test empty output fixture {
                    printf("\n");
                    printf("tail without newline");
                }
            """.trimIndent()
        )
        val second = Files.writeString(directory.resolve("second root.cp"), "int main() { return 0; }")
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val originalOut = System.out
        val originalErr = System.err
        val status = try {
            System.setOut(PrintStream(out))
            System.setErr(PrintStream(err))
            Cli().run(listOf("test", first.toString(), second.toString()))
        } finally {
            System.setOut(originalOut)
            System.setErr(originalErr)
        }
        val report = out.toString()

        assertEquals(0, status, "${err}; $report")
        assertTrue(report.contains("::: [1/2] $first"), report)
        assertTrue(report.contains("... [1/2] first fixture with spaces"), report)
        assertTrue(report.contains("user output\n---- 1 ----------------"), report)
        assertTrue(report.contains("---- expression: 1"), report)
        assertTrue(report.contains("---- value: 1"), report)
        assertTrue(report.contains("---- SUCCESS"), report)
        assertTrue(report.contains("line\\nlabel"), report)
        assertTrue(report.contains("expected expression: 7"), report)
        assertTrue(report.contains("expected value: 7"), report)
        assertTrue(report.contains("evaluated expression: 7"), report)
        assertTrue(report.contains("evaluated value: 7"), report)
        assertTrue(report.contains("equality label"), report)
        assertTrue(report.contains("... [2/2] empty output fixture\n\ntail without newline\n... EMPTY"), report)
        assertTrue(report.contains("... asserts passed 4 / failed 0 / total 4; errors 0"), report)
        assertTrue(report.contains("... NO TESTS"), report)
        assertTrue(report.contains("::: $first: passed 4 / failed 0 / total 4; errors 0"), report)
        assertTrue(report.contains("::: $second: passed 0 / failed 0 / total 0; errors 0"), report)
        assertTrue(report.contains("::: total: passed 4 / failed 0 / total 4; errors 0"), report)
        assertTrue(report.indexOf("::: [1/2]") < report.indexOf("::: [2/2]"), report)
        assertTrue(err.toString().isEmpty(), err.toString())
    }

    @Test
    fun includesUncompiledRootsAndContinuesToCountLaterPassingRoots() {
        val directory = Files.createTempDirectory("cplus-test-aggregate")
        val broken = Files.writeString(directory.resolve("broken.cp"), "int main( {")
        val passing = Files.writeString(
            directory.resolve("passing.cp"),
            "int main() { return 0; } test surviving fixture { assert(1); }"
        )
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val originalOut = System.out
        val originalErr = System.err
        val status = try {
            System.setOut(PrintStream(out))
            System.setErr(PrintStream(err))
            Cli().run(listOf("test", broken.toString(), passing.toString()))
        } finally {
            System.setOut(originalOut)
            System.setErr(originalErr)
        }

        val report = out.toString()
        assertEquals(1, status, "${err}; $report")
        assertTrue(report.contains("::: [1/2] $broken"), report)
        assertTrue(report.contains("::: [2/2] $passing"), report)
        assertTrue(report.contains("::: $broken: passed 0 / failed 0 / total 0; errors 1"), report)
        assertTrue(report.contains("::: $passing: passed 1 / failed 0 / total 1; errors 0"), report)
        assertTrue(report.contains("::: total: passed 1 / failed 0 / total 1; errors 1"), report)
    }
}
