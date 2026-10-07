package cplus.cli

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CliIntegrationTest {
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
}
