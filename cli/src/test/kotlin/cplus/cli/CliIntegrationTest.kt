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
    fun lspCompilesOpenWorkspaceImportsForDiagnostics() {
        val directory = Files.createTempDirectory("cplus-cli-lsp-modules")
        val helper = directory.resolve("module_helpers.cp")
        val main = directory.resolve("module_main.cp")
        val helperUri = helper.toUri().toString()
        val mainUri = main.toUri().toString()
        val helperText = "pub int add(int left, int right) { return left + right; }"
        val mainText = "import { add } from ./module_helpers.cp; int main() { return add(7, 5); }"
        val input = listOf(
            """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}""",
            """{"jsonrpc":"2.0","method":"textDocument/didOpen","params":{"textDocument":{"uri":"$helperUri","version":1,"text":"$helperText"}}}""",
            """{"jsonrpc":"2.0","method":"textDocument/didOpen","params":{"textDocument":{"uri":"$mainUri","version":1,"text":"$mainText"}}}""",
            """{"jsonrpc":"2.0","id":2,"method":"shutdown","params":null}""",
            """{"jsonrpc":"2.0","method":"exit"}"""
        ).joinToString(separator = "") { message -> frame(message) }
        val output = ByteArrayOutputStream()

        assertEquals(0, LspServer().run(ByteArrayInputStream(input.toByteArray()), output))

        val responses = output.toString(Charsets.UTF_8)
        assertTrue(!responses.contains("module import 'module_helpers' cannot be resolved"), responses)
        assertTrue(responses.contains("\"uri\":\"$mainUri\""))
    }

    @Test
    fun lspLoadsClosedImportedModulesFromDiskForDiagnostics() {
        val directory = Files.createTempDirectory("cplus-cli-lsp-disk-modules")
        val helper = directory.resolve("module_helpers.cp").also {
            it.writeText("pub int add(int left, int right) { return left + right; }")
        }
        val main = directory.resolve("module_main.cp")
        val mainUri = main.toUri().toString()
        val mainText = "import { add } from module_helpers; int main() { return add(7, 5); }"
        val input = listOf(
            """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}""",
            """{"jsonrpc":"2.0","method":"textDocument/didOpen","params":{"textDocument":{"uri":"$mainUri","version":1,"text":"$mainText"}}}""",
            """{"jsonrpc":"2.0","id":2,"method":"shutdown","params":null}""",
            """{"jsonrpc":"2.0","method":"exit"}"""
        ).joinToString(separator = "") { message -> frame(message) }
        val output = ByteArrayOutputStream()

        assertEquals(0, LspServer().run(ByteArrayInputStream(input.toByteArray()), output))

        val responses = output.toString(Charsets.UTF_8)
        assertTrue(!responses.contains("module import 'module_helpers' cannot be resolved"), responses)
        assertTrue(responses.contains("\"uri\":\"$mainUri\""))
        assertTrue(helper.exists())
    }

    @Test
    fun lspPublishesSemanticTokensFromTheCompilerFrontEnd() {
        val directory = Files.createTempDirectory("cplus-cli-semantic-tokens")
        val source = directory.resolve("main.cp")
        val uri = source.toUri().toString()
        val input = listOf(
            """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}""",
            """{"jsonrpc":"2.0","method":"textDocument/didOpen","params":{"textDocument":{"uri":"$uri","version":1,"text":"struct User { int value; }; int main() { User item; return item.value; }"}}}""",
            """{"jsonrpc":"2.0","id":2,"method":"textDocument/semanticTokens/full","params":{"textDocument":{"uri":"$uri"}}}""",
            """{"jsonrpc":"2.0","id":3,"method":"shutdown","params":null}""",
            """{"jsonrpc":"2.0","method":"exit"}"""
        ).joinToString(separator = "") { message -> frame(message) }
        val output = ByteArrayOutputStream()

        assertEquals(0, LspServer().run(ByteArrayInputStream(input.toByteArray()), output))

        val responses = output.toString(Charsets.UTF_8)
        assertTrue(responses.contains("semanticTokensProvider"))
        assertTrue(responses.contains("\"id\":2"))
        assertTrue(responses.contains("\"data\":["))
    }

    @Test
    fun lspCompletionAndHoverUseSemanticSymbols() {
        val directory = Files.createTempDirectory("cplus-cli-language-service")
        val source = directory.resolve("main.cp")
        val uri = source.toUri().toString()
        val text = "int main() { return 0; }"
        val input = listOf(
            """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}""",
            """{"jsonrpc":"2.0","method":"textDocument/didOpen","params":{"textDocument":{"uri":"$uri","version":1,"text":"$text"}}}""",
            """{"jsonrpc":"2.0","id":2,"method":"textDocument/completion","params":{"textDocument":{"uri":"$uri"},"position":{"line":0,"character":6}}}""",
            """{"jsonrpc":"2.0","id":3,"method":"textDocument/hover","params":{"textDocument":{"uri":"$uri"},"position":{"line":0,"character":5}}}""",
            """{"jsonrpc":"2.0","id":4,"method":"shutdown","params":null}""",
            """{"jsonrpc":"2.0","method":"exit"}"""
        ).joinToString(separator = "") { message -> frame(message) }
        val output = ByteArrayOutputStream()

        assertEquals(0, LspServer().run(ByteArrayInputStream(input.toByteArray()), output))

        val responses = output.toString(Charsets.UTF_8)
        assertTrue(responses.contains("\"id\":2"))
        assertTrue(responses.contains("\"label\":\"main\""))
        assertTrue(responses.contains("\"id\":3"))
        assertTrue(responses.contains("main"))
    }

    @Test
    fun lspNavigationUsesCompilerReferenceIndex() {
        val directory = Files.createTempDirectory("cplus-cli-navigation")
        val source = directory.resolve("main.cp")
        val uri = source.toUri().toString()
        val text = "int answer() { return 7; } int main() { return answer(); }"
        val input = listOf(
            """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}""",
            """{"jsonrpc":"2.0","method":"textDocument/didOpen","params":{"textDocument":{"uri":"$uri","version":1,"text":"$text"}}}""",
            """{"jsonrpc":"2.0","id":2,"method":"textDocument/definition","params":{"textDocument":{"uri":"$uri"},"position":{"line":0,"character":52}}}""",
            """{"jsonrpc":"2.0","id":3,"method":"textDocument/references","params":{"textDocument":{"uri":"$uri"},"position":{"line":0,"character":52},"context":{"includeDeclaration":true}}}""",
            """{"jsonrpc":"2.0","id":4,"method":"shutdown","params":null}""",
            """{"jsonrpc":"2.0","method":"exit"}"""
        ).joinToString(separator = "") { message -> frame(message) }
        val output = ByteArrayOutputStream()

        assertEquals(0, LspServer().run(ByteArrayInputStream(input.toByteArray()), output))

        val responses = output.toString(Charsets.UTF_8)
        assertTrue(responses.contains("definitionProvider"))
        assertTrue(responses.contains("referencesProvider"))
        assertTrue(responses.contains("\"id\":2"))
        assertTrue(responses.contains("\"id\":3"))
        assertTrue(responses.contains("\"uri\":\"$uri\""))
    }

    @Test
    fun lspForeignSymbolsParticipateInCompletionHoverAndSignatureHelp() {
        val directory = Files.createTempDirectory("cplus-cli-foreign-language-service")
        val source = directory.resolve("main.cp")
        val uri = source.toUri().toString()
        val text = "import { printf } from c.stdio; int main() { return printf(0); }"
        val printfOffset = text.indexOf("printf(0")
        val input = listOf(
            """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}""",
            """{"jsonrpc":"2.0","method":"textDocument/didOpen","params":{"textDocument":{"uri":"$uri","version":1,"text":"$text"}}}""",
            """{"jsonrpc":"2.0","id":2,"method":"textDocument/completion","params":{"textDocument":{"uri":"$uri"},"position":{"line":0,"character":${printfOffset + 3}}}}""",
            """{"jsonrpc":"2.0","id":3,"method":"textDocument/hover","params":{"textDocument":{"uri":"$uri"},"position":{"line":0,"character":$printfOffset}}}""",
            """{"jsonrpc":"2.0","id":4,"method":"textDocument/signatureHelp","params":{"textDocument":{"uri":"$uri"},"position":{"line":0,"character":${printfOffset + 7}}}}""",
            """{"jsonrpc":"2.0","id":5,"method":"shutdown","params":null}""",
            """{"jsonrpc":"2.0","method":"exit"}"""
        ).joinToString(separator = "") { message -> frame(message) }
        val output = ByteArrayOutputStream()

        assertEquals(0, LspServer().run(ByteArrayInputStream(input.toByteArray()), output))

        val responses = output.toString(Charsets.UTF_8)
        assertTrue(responses.contains("signatureHelpProvider"))
        assertTrue(responses.contains("\"label\":\"printf\""))
        assertTrue(responses.contains("printf("))
        assertTrue(responses.contains("fn(...)->int"))
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
    fun runDiscoversLocalImportedModulesFromTheEntryPoint() {
        val directory = Files.createTempDirectory("cplus-cli-run-modules")
        val helper = directory.resolve("module_helpers.cp").also {
            it.writeText(
                """
                    pub int add(int left, int right) {
                        return left + right;
                    }
                """.trimIndent()
            )
        }
        val main = directory.resolve("module_main.cp").also {
            it.writeText(
                """
                    import { add } from module_helpers;

                    int main() {
                        return add(7, 5);
                    }
                """.trimIndent()
            )
        }

        assertEquals(12, Cli().run(listOf("run", main.toString())))
        assertTrue(helper.exists())
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
