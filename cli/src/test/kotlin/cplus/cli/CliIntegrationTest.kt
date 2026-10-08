package cplus.cli

import cplus.compiler.SdkManifestLocator
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
    fun lspDoesNotMergeUnrelatedOpenProgramsIntoTheActiveWorkspace() {
        val directory = Files.createTempDirectory("cplus-cli-lsp-unrelated")
        val first = directory.resolve("first.cp")
        val second = directory.resolve("second.cp")
        val firstUri = first.toUri().toString()
        val secondUri = second.toUri().toString()
        val source = "int main() { return 0; }"
        val input = listOf(
            """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}""",
            """{"jsonrpc":"2.0","method":"textDocument/didOpen","params":{"textDocument":{"uri":"$firstUri","version":1,"text":"$source"}}}""",
            """{"jsonrpc":"2.0","method":"textDocument/didOpen","params":{"textDocument":{"uri":"$secondUri","version":1,"text":"$source"}}}""",
            """{"jsonrpc":"2.0","id":2,"method":"shutdown","params":null}""",
            """{"jsonrpc":"2.0","method":"exit"}"""
        ).joinToString(separator = "") { message -> frame(message) }
        val output = ByteArrayOutputStream()

        assertEquals(0, LspServer().run(ByteArrayInputStream(input.toByteArray()), output))

        val responses = output.toString(Charsets.UTF_8)
        assertTrue(!responses.contains("duplicate function 'main'"), responses)
        assertTrue(responses.contains("\"uri\":\"$firstUri\""))
        assertTrue(responses.contains("\"uri\":\"$secondUri\""))
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
    fun runResolvesPathAndPackageImportsForSourceTypes() {
        val directory = Files.createTempDirectory("cplus-cli-run-imported-types")
        val standardLibrary = Files.createDirectories(directory.resolve("stdlib"))
        val helper = standardLibrary.resolve("io.cp").also {
            it.writeText(
                """
                    package stdlib;
                    pub struct Point { int x; };
                    pub typedef int Coord;
                    pub int pointValue() {
                        struct Point point;
                        point.x = 3;
                        return point.x;
                    }
                """.trimIndent()
            )
        }
        val main = directory.resolve("main.cp").also {
            it.writeText(
                """
                    import { Coord as LocalCoord } from stdlib/io;
                    import { Point as LocalPoint, pointValue } from "./stdlib/io.cp";
                    import stdlib/io as geo;

                    int main() {
                        LocalPoint* localPoint;
                        geo.Point* qualifiedPoint;
                        LocalCoord localCoordinate = 2;
                        geo.Coord qualifiedCoordinate = 3;
                        return pointValue() + localCoordinate + qualifiedCoordinate +
                            sizeof(LocalPoint) + sizeof(geo.Point);
                    }
                """.trimIndent()
            )
        }

        assertEquals(16, Cli().run(listOf("run", main.toString())))
        assertTrue(helper.exists())
    }

    @Test
    fun runSupportsExplicitFixedWidthSdkModuleImport() {
        val sdkRoot = SdkManifestLocator.defaultManifestPath()
            .toAbsolutePath().normalize().parent!!.parent!!
        val fixedWidthModule = sdkRoot.resolve("std/src/fixed_width.cp")
        val directory = Files.createTempDirectory("cplus-cli-fixed-width-import")
        val main = directory.resolve("main.cp").also {
            it.writeText(
                """
                    import { i8, i32, u64 } from std.fixed_width;
                    i32 add_wide(i32 left, u64 right) { return left + (i32)right; }
                    int main() { i8 small; return add_wide(1, 2); }
                """.trimIndent()
            )
        }

        assertEquals(3, Cli().run(listOf("run", main.toString())))
        assertTrue(fixedWidthModule.exists())
    }

    @Test
    fun projectAndWorkspaceManifestsShareOneSourceModelAcrossCommands() {
        val directory = Files.createTempDirectory("cplus-cli-project-manifest")
        val app = Files.createDirectories(directory.resolve("app"))
        val modules = Files.createDirectories(directory.resolve("modules"))
        modules.resolve("math.cp").writeText("pub int add(int left, int right) { return left + right; }")
        app.resolve("main.cp").writeText(
            "import { add } from math; int main() { return add(7, 5); }"
        )
        val project = directory.resolve("cplus.toml").also {
            it.writeText(
                """
                    [project]
                    entry = "app/main.cp"
                    source_roots = ["modules"]
                """.trimIndent()
            )
        }
        val workspace = directory.resolve("cplus.workspace.toml").also {
            it.writeText(
                """
                    [workspace]
                    entry = "app/main.cp"
                    members = ["modules"]
                """.trimIndent()
            )
        }
        val projectOption = listOf("--project", project.toString())

        assertEquals(0, Cli().run(listOf("check") + projectOption))
        val generatedC = directory.resolve("main.c")
        assertEquals(0, Cli().run(listOf("transcode") + projectOption + listOf("--output", generatedC.toString())))
        assertTrue(generatedC.exists())
        val executable = directory.resolve("project-program")
        assertEquals(0, Cli().run(listOf("build") + projectOption + listOf("--output", executable.toString())))
        assertEquals(12, ProcessBuilder(executable.toString()).start().waitFor())
        assertEquals(12, Cli().run(listOf("run") + projectOption))
        assertEquals(0, Cli().run(listOf("check", "--workspace", workspace.toString())))
    }

    @Test
    fun selfHostedRuntimeProvidesStringTemplateFormatterWithoutHostedStdio() {
        val directory = Files.createTempDirectory("cplus-cli-runtime-format")
        val source = directory.resolve("main.cp").also {
            it.writeText("import { printf } from c.stdio; int main() { printf(\"value=${'$'}{7}\\n\"); return 0; }")
        }

        assertEquals(0, Cli().run(listOf("build", source.toString(), "--output", directory.resolve("program").toString())))
    }

    @Test
    fun checkReportsFailureWithNonzeroExitCode() {
        val directory = Files.createTempDirectory("cplus-cli-check")
        val source = directory.resolve("invalid.cp").also {
            it.writeText("void invalid_value; int main() { return 0; }")
        }

        assertEquals(1, Cli().run(listOf("check", source.toString())))
    }

    @Test
    fun cliAndLspPublishTheSameInvalidIntegerSpecifierDiagnostic() {
        val directory = Files.createTempDirectory("cplus-integer-diagnostic-parity")
        val source = directory.resolve("invalid.cp").also {
            it.writeText("unsigned signed int invalid; int main() { return 0; }")
        }
        val uri = source.toUri().toString()
        val cliDiagnostics = captureStderr {
            assertEquals(1, Cli().run(listOf("check", source.toString())))
        }
        val input = listOf(
            """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}""",
            """{"jsonrpc":"2.0","method":"textDocument/didOpen","params":{"textDocument":{"uri":"$uri","version":1,"text":"unsigned signed int invalid; int main() { return 0; }"}}}""",
            """{"jsonrpc":"2.0","id":2,"method":"shutdown","params":null}""",
            """{"jsonrpc":"2.0","method":"exit"}"""
        ).joinToString(separator = "") { message -> frame(message) }
        val output = ByteArrayOutputStream()

        assertEquals(0, LspServer().run(ByteArrayInputStream(input.toByteArray()), output))

        val lspDiagnostics = output.toString(Charsets.UTF_8)
        val message = "invalid primitive type specifier sequence 'unsigned signed int'"
        assertTrue(cliDiagnostics.contains("[PARSE102]"), cliDiagnostics)
        assertTrue(lspDiagnostics.contains("\"code\":\"PARSE102\""), lspDiagnostics)
        assertTrue(cliDiagnostics.contains(message), cliDiagnostics)
        assertTrue(lspDiagnostics.contains(message), lspDiagnostics)
    }

    @Test
    fun cliAndLspAgreeOnPrivateAndUnimportedSourceTypeDiagnostics() {
        data class DiagnosticCase(
            val name: String,
            val mainText: String,
            val helperText: String,
            val code: String,
            val message: String
        )
        val cases = listOf(
            DiagnosticCase(
                "private",
                "import { Hidden } from ./types.cp; int main() { Hidden item; return 0; }",
                "struct Hidden { int value; };",
                "SEM406",
                "imported type 'Hidden' is not public in module './types.cp'"
            ),
            DiagnosticCase(
                "unimported",
                "import { makeValue } from ./types.cp; int main() { Point item; return makeValue(); }",
                "pub struct Point { int value; }; pub int makeValue() { return 0; }",
                "SEM410",
                "type 'Point' belongs to module 'types' and is not imported into module 'main'"
            )
        )

        cases.forEach { testCase ->
            val directory = Files.createTempDirectory("cplus-type-import-parity-${testCase.name}")
            val mainText = testCase.mainText
            val main = directory.resolve("main.cp").also { it.writeText(mainText) }
            directory.resolve("types.cp").writeText(testCase.helperText)
            val cliDiagnostics = captureStderr {
                assertEquals(1, Cli().run(listOf("check", main.toString())))
            }
            val uri = main.toUri().toString()
            val input = listOf(
                """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}""",
                """{"jsonrpc":"2.0","method":"textDocument/didOpen","params":{"textDocument":{"uri":"$uri","version":1,"text":"$mainText"}}}""",
                """{"jsonrpc":"2.0","id":2,"method":"shutdown","params":null}""",
                """{"jsonrpc":"2.0","method":"exit"}"""
            ).joinToString(separator = "") { message -> frame(message) }
            val output = ByteArrayOutputStream()

            assertEquals(0, LspServer().run(ByteArrayInputStream(input.toByteArray()), output))

            val lspDiagnostics = output.toString(Charsets.UTF_8)
            assertTrue(cliDiagnostics.contains("[${testCase.code}]"), cliDiagnostics)
            assertTrue(lspDiagnostics.contains("\"code\":\"${testCase.code}\""), lspDiagnostics)
            assertTrue(cliDiagnostics.contains(testCase.message), cliDiagnostics)
            assertTrue(lspDiagnostics.contains(testCase.message), lspDiagnostics)
        }
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

    private fun captureStderr(action: () -> Unit): String {
        val original = System.err
        val captured = ByteArrayOutputStream()
        System.setErr(PrintStream(captured, true, Charsets.UTF_8))
        return try {
            action()
            captured.toString(Charsets.UTF_8)
        } finally {
            System.setErr(original)
        }
    }
}
