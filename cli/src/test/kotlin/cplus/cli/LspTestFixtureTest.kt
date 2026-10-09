package cplus.cli

import cplus.compiler.CPlusCompiler
import cplus.compiler.CompileRequest
import cplus.compiler.ImportIndex
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LspTestFixtureTest {
    @Test
    fun providesSignatureHelpForAllFourFixtureAssertionForms() {
        val directory = Files.createTempDirectory("cplus-lsp-test-signatures")
        val source = directory.resolve("main.cp")
        val text = """
            int main() { return 0; }
            test fixture with spaces {
                int expected = 1;
                assert(expected);
                assert("description", expected);
                assertEquals(expected, 1);
                assertEquals("equal description", expected, 1);
            }
        """.trimIndent()
        source.writeText(text)
        val result = CPlusCompiler().compile(CompileRequest(listOf(source)))
        assertTrue(result.diagnostics.isEmpty(), result.diagnostics.joinToString())

        val cases = listOf(
            "assert(expected)" to listOf("condition"),
            "assert(\"description\", expected)" to listOf("string description", "condition"),
            "assertEquals(expected, 1)" to listOf("expected value", "actual value"),
            "assertEquals(\"equal description\", expected, 1)" to listOf("string description", "expected value", "actual value")
        )
        cases.forEach { (expression, expectedParameters) ->
            val argumentPosition = text.indexOf(expression) + expression.indexOf('(') + 1
            val signature = LspLanguageService.signatureHelp(result, text, positionAt(text, argumentPosition), source)
            assertNotNull(signature, "No signature help for $expression")
            assertEquals(expectedParameters, signature.parameters.map { it.label }, expression)
        }

        val invalidSource = directory.resolve("invalid.cp")
        invalidSource.writeText("test invalid assertions { assert(); assertEquals(1); }")
        val invalid = CPlusCompiler().compile(CompileRequest(listOf(invalidSource)))
        assertEquals(2, invalid.diagnostics.count { it.code == "PARSE535" }, invalid.diagnostics.joinToString())
    }

    @Test
    fun fixtureOperandsRetainDiagnosticsAndNavigationAndAppearAsDocumentSymbols() {
        val directory = Files.createTempDirectory("cplus-lsp-test-symbols")
        val source = directory.resolve("main.cp")
        val text = """
            int shared_value = 7;
            test fixture description with spaces {
                assert(shared_value);
                assert(unknown_value);
            }
        """.trimIndent()
        source.writeText(text)
        val compiler = CPlusCompiler()
        val result = compiler.compile(CompileRequest(listOf(source)))
        assertTrue(result.diagnostics.any { it.code == "SEM301" }, result.diagnostics.joinToString())

        val reference = text.indexOf("shared_value", text.indexOf("test fixture"))
        val navigation = LspLanguageService.navigation(
            result,
            text,
            positionAt(text, reference + 2),
            sourcePath = source,
            sourcePathFor = compiler::sourcePathFor,
            sourceTextFor = { id -> result.artifacts.firstOrNull { it.source.id == id }?.source?.text }
        )
        assertNotNull(navigation)
        assertTrue(navigation.references.any { it.startOffset == reference }, navigation.toString())

        val uri = source.toUri().toString()
        val renameOffset = text.indexOf("shared_value", text.indexOf("test fixture"))
        val renamePosition = positionAt(text, renameOffset + 2)
        val messages = listOf(
            """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"rootUri":"${directory.toUri()}"}}""",
            """{"jsonrpc":"2.0","method":"textDocument/didOpen","params":{"textDocument":{"uri":"$uri","version":1,"text":${jsonString(text)}}}}""",
            """{"jsonrpc":"2.0","id":2,"method":"textDocument/documentSymbol","params":{"textDocument":{"uri":"$uri"}}}""",
            """{"jsonrpc":"2.0","id":4,"method":"textDocument/rename","params":{"textDocument":{"uri":"$uri"},"position":{"line":${renamePosition.line},"character":${renamePosition.character}},"newName":"shared_count"}}""",
            """{"jsonrpc":"2.0","id":3,"method":"shutdown","params":null}""",
            """{"jsonrpc":"2.0","method":"exit"}"""
        )
        val output = ByteArrayOutputStream()
        assertEquals(0, LspServer().run(ByteArrayInputStream(messages.joinToString("", transform = ::frame).toByteArray()), output))
        val responses = output.toString(Charsets.UTF_8)
        assertTrue(responses.contains("fixture description with spaces"), responses)
        assertTrue(responses.contains("shared_count"), responses)
    }

    @Test
    fun importedSymbolsAndGeneratedMembersRemainAvailableInsideFixtures() {
        val directory = Files.createTempDirectory("cplus-lsp-test-cpx")
        directory.resolve("box.cp").writeText(
            "pub comptime cpx<decl> box(type T) { return { struct box_{T}_t { T value; }; }; }\n" +
                "pub int imported_value() { return 1; }\n"
        )
        val source = directory.resolve("main.cp")
        val text = """
            import { box, imported_value } from "./box.cp";
            box(int);
            test generated member fixture {
                struct box_int_t item;
                assert(imported_value());
                assert(item.va);
            }
        """.trimIndent()
        source.writeText(text)
        val compiler = CPlusCompiler()
        val result = compiler.compile(CompileRequest(listOf(source)))
        assertTrue(result.diagnostics.all { it.code == "SEM304" }, result.diagnostics.joinToString())

        val memberPrefix = text.indexOf("item.va") + "item.va".length
        val members = LspLanguageService.completion(result, text, positionAt(text, memberPrefix), sourcePath = source)
        assertTrue(members.any { it.label == "value" }, members.toString())

        val importedCall = text.indexOf("imported_value", text.indexOf("test generated"))
        val navigation = LspLanguageService.navigation(
            result,
            text,
            positionAt(text, importedCall + 4),
            sourcePath = source,
            sourcePathFor = compiler::sourcePathFor,
            sourceTextFor = { id -> result.artifacts.firstOrNull { it.source.id == id }?.source?.text },
            importIndex = ImportIndex().build(listOf(directory))
        )
        assertNotNull(navigation)
        val definition = assertNotNull(navigation.definition, navigation.toString())
        assertEquals(
            directory.resolve("box.cp").toAbsolutePath().normalize(),
            compiler.sourcePathFor(definition.file)?.toAbsolutePath()?.normalize()
        )
        assertTrue(navigation.references.any { it.startOffset == importedCall }, navigation.toString())
    }

    private fun positionAt(text: String, offset: Int): LspPosition {
        val prefix = text.substring(0, offset)
        return LspPosition(prefix.count { it == '\n' }, prefix.substringAfterLast('\n').length)
    }

    private fun frame(message: String): String {
        val bytes = message.toByteArray()
        return "Content-Length: ${bytes.size}\r\n\r\n$message"
    }

    private fun jsonString(text: String): String = buildString {
        append('"')
        text.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(character)
            }
        }
        append('"')
    }
}
