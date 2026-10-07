package cplus.cli

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CliIntegrationTest {
    @Test
    fun astAndExpandInspectDifferentCompilerPhaseRepresentations() {
        val directory = Files.createTempDirectory("cplus-cli-inspection")
        val source = directory.resolve("main.cp").also {
            it.writeText(
                """
                    comptime cpx<decl> box(type T) {
                        return { struct box_{T}_t { T value; }; };
                    }
                    box(int);
                    int main() { return 0; }
                """.trimIndent()
            )
        }
        val astOutput = captureStdout { assertEquals(0, Cli().run(listOf("ast", source.toString()))) }
        val expandedOutput = captureStdout { assertEquals(0, Cli().run(listOf("expand", source.toString()))) }

        assertTrue(astOutput.contains("Comptime decl box"))
        assertTrue(astOutput.contains("CpxInvocation box(int)"))
        assertTrue(!astOutput.contains("Struct box_int_t"))
        assertTrue(expandedOutput.contains("Struct box_int_t"))
        assertTrue(!expandedOutput.contains("CpxInvocation"))
    }

    @Test
    fun lspServesInitializationAndCompilerDiagnosticsOverStdio() {
        val directory = Files.createTempDirectory("cplus-cli-lsp")
        val source = directory.resolve("main.cp")
        val uri = source.toUri().toString()
        val input = listOf(
            """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}""",
            """{"jsonrpc":"2.0","method":"textDocument/didOpen","params":{"textDocument":{"uri":"$uri","version":1,"text":"void invalid_value; int main() { return 0; }"}}}""",
            """{"jsonrpc":"2.0","id":2,"method":"shutdown","params":null}""",
            """{"jsonrpc":"2.0","method":"exit"}"""
        ).joinToString(separator = "") { message -> frame(message) }
        val output = ByteArrayOutputStream()

        assertEquals(
            0,
            LspServer().run(ByteArrayInputStream(input.toByteArray()), output)
        )

        val responses = output.toString(Charsets.UTF_8)
        assertTrue(responses.contains("\"serverInfo\""))
        assertTrue(responses.contains("textDocument/publishDiagnostics"))
        assertTrue(responses.contains("LOW402"))
        assertTrue(responses.contains(uri))
    }

    @Test
    fun buildEmitsCHeaderAndExecutableWithExpectedBehavior() {
        val directory = Files.createTempDirectory("cplus-cli-build")
        val source = directory.resolve("main.cp").also {
            it.writeText(
                """
                    pub int answer() {
                        return 7;
                    }

                    int main() {
                        return answer();
                    }
                """.trimIndent()
            )
        }
        val executable = directory.resolve("program")
        val header = directory.resolve("program.h")

        val exitCode = Cli().run(
            listOf(
                "build",
                source.toString(),
                "--output",
                executable.toString(),
                "--header",
                header.toString()
            )
        )

        assertEquals(0, exitCode)
        assertTrue(executable.exists())
        assertTrue(executable.resolveSibling("program.c").exists())
        assertTrue(header.exists())
        assertTrue(header.readText().contains("int answer();"))
        assertEquals(7, ProcessBuilder(executable.toString()).start().waitFor())
    }

    @Test
    fun checkReportsFailureWithNonzeroExitCode() {
        val directory = Files.createTempDirectory("cplus-cli-check")
        val source = directory.resolve("invalid.cp").also {
            it.writeText("void invalid_value; int main() { return 0; }")
        }

        assertEquals(1, Cli().run(listOf("check", source.toString())))
    }

    private fun frame(message: String): String =
        "Content-Length: ${message.toByteArray(Charsets.UTF_8).size}\r\n\r\n$message"

    private fun captureStdout(action: () -> Unit): String {
        val original = System.out
        val captured = ByteArrayOutputStream()
        System.setOut(PrintStream(captured, true, Charsets.UTF_8))
        return try {
            action()
            captured.toString(Charsets.UTF_8)
        } finally {
            System.setOut(original)
        }
    }
}
