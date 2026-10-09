package cplus.cli

import cplus.compiler.CompileRequest
import cplus.compiler.CPlusCompiler
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LspTraitMethodsTest {
    @Test
    fun memberCompletionOffersOnlyDirectlyVisiblePublicExtensions() {
        val directory = Files.createTempDirectory("cplus-lsp-trait-completion")
        val types = directory.resolve("types.cp").also {
            it.writeText("pub struct point_t { int value; };")
        }
        val extensions = directory.resolve("extensions.cp").also {
            it.writeText(
                """
                    import {point_t} from types;
                    pub comptime trait point_t { int area(self) { return self.value; } }
                    comptime trait point_t { int secret(self) { return 0; } }
                """.trimIndent()
            )
        }
        val scalarExtensions = directory.resolve("scalar_extensions.cp").also {
            it.writeText("pub comptime trait int { int doubled(self) { return self * 2; } }")
        }
        val source = directory.resolve("main.cp")
        val text = """
            import {point_t as Point} from types;
            import {area} from extensions;
            import {doubled} from scalar_extensions;
            int main() {
                Point point;
                int scalar = 3;
                return point.ar + scalar.do;
            }
        """.trimIndent()
        source.writeText(text)
        val result = CPlusCompiler().compile(CompileRequest(listOf(source, extensions, scalarExtensions, types)))
        assertNotNull(result.semanticModel)

        val cursor = positionAt(text, text.indexOf("point.ar") + "point.ar".length)
        val items = LspLanguageService.completion(result, text, cursor, sourcePath = source)

        assertTrue(items.any { it.label == "area" }, items.toString())
        assertTrue(items.none { it.label == "secret" }, items.toString())

        val scalarCursor = positionAt(text, text.indexOf("scalar.do") + "scalar.do".length)
        val scalarItems = LspLanguageService.completion(result, text, scalarCursor, sourcePath = source)
        assertTrue(scalarItems.any { it.label == "doubled" }, scalarItems.toString())

        val withoutExtensionImport = text.replace("import {area} from extensions;\n", "")
        val hiddenSource = directory.resolve("hidden.cp").also { it.writeText(withoutExtensionImport) }
        val hiddenResult = CPlusCompiler().compile(CompileRequest(listOf(hiddenSource, extensions, scalarExtensions, types)))
        val hiddenCursor = positionAt(withoutExtensionImport, withoutExtensionImport.indexOf("point.ar") + "point.ar".length)
        val hiddenItems = LspLanguageService.completion(
            hiddenResult,
            withoutExtensionImport,
            hiddenCursor,
            sourcePath = hiddenSource
        )
        assertTrue(hiddenItems.none { it.label == "area" }, hiddenItems.toString())
    }

    @Test
    fun extensionCallsResolveToTheirDeclarationAndReferenceLocations() {
        val directory = Files.createTempDirectory("cplus-lsp-trait-navigation")
        val types = directory.resolve("types.cp").also {
            it.writeText("pub struct point_t { int value; };")
        }
        val extensionText = """
            import {point_t} from types;
            pub comptime trait point_t {
                int area(self) { return self.value; }
                int shift(self*, int amount) { self->value += amount; return self->value; }
            }
        """.trimIndent()
        val extensions = directory.resolve("extensions.cp").also { it.writeText(extensionText) }
        val sourceText = "import {point_t} from types; import {area, shift} from extensions; int main() { point_t point; return point.area() + point.shift(2); }"
        val source = directory.resolve("main.cp").also { it.writeText(sourceText) }
        val result = CPlusCompiler().compile(CompileRequest(listOf(source, extensions, types)))

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val extensionSymbol = assertNotNull(result.semanticModel?.symbols?.firstOrNull { it.name == "area" })
        val extensionFileId = assertNotNull(extensionSymbol.origin.primaryRange?.file)
        val callOffset = sourceText.lastIndexOf("area")
        val navigation = LspLanguageService.navigation(
            result,
            sourceText,
            positionAt(sourceText, callOffset + 1),
            sourcePath = source,
            sourcePathFor = { id ->
                when {
                    id == extensionFileId -> extensions
                    else -> result.artifacts.firstOrNull { it.source.id == id }?.source?.path
                }
            },
            sourceTextFor = { id ->
                when {
                    id == extensionFileId -> extensionText
                    else -> result.artifacts.firstOrNull { it.source.id == id }?.source?.text
                }
            }
        )

        assertNotNull(navigation)
        val definition = assertNotNull(navigation.definition)
        val declaredNameOffset = extensionText.indexOf("area")
        assertEquals(extensionFileId, definition.file)
        assertEquals(declaredNameOffset, definition.startOffset)
        assertEquals(declaredNameOffset + "area".length, definition.endOffset)
        val mainSource = assertNotNull(
            result.artifacts.firstOrNull { it.source.path.toAbsolutePath().normalize() == source.toAbsolutePath().normalize() },
            "main artifact paths: ${result.artifacts.map { it.source.path }}"
        )
        assertTrue(navigation.references.any { it.file == mainSource.source.id && it.startOffset == callOffset })

        val hover = LspLanguageService.hover(result, sourceText, positionAt(sourceText, callOffset + 1), source)
        assertTrue(hover?.markdown?.contains("method area") == true, hover?.markdown.orEmpty())

        val shiftArgument = sourceText.indexOf("point.shift(2)") + "point.shift(".length
        val signature = LspLanguageService.signatureHelp(result, sourceText, positionAt(sourceText, shiftArgument), source)
        assertNotNull(signature)
        assertTrue(signature.label.contains("shift(point_t* self, int amount)"), signature.label)
        assertEquals(listOf("point_t* self", "int amount"), signature.parameters.map { it.label })
    }

    @Test
    fun lspRenamesAnExtensionMethodAcrossProviderAndCallerFiles() {
        val directory = Files.createTempDirectory("cplus-lsp-trait-rename")
        directory.resolve("types.cp").writeText("pub struct point_t { int value; };")
        val extension = directory.resolve("extensions.cp").also {
            it.writeText("import {point_t} from types; pub comptime trait point_t { int area(self) { return self.value; } }")
        }
        val mainText = "import {point_t} from types; import {area} from extensions; int main() { point_t point; return point.area(); }"
        val main = directory.resolve("main.cp").also { it.writeText(mainText) }
        val uri = main.toUri().toString()
        val messages = listOf(
            """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"rootUri":"${directory.toUri()}"}}""",
            """{"jsonrpc":"2.0","method":"textDocument/didOpen","params":{"textDocument":{"uri":"$uri","version":1,"text":"$mainText"}}}""",
            """{"jsonrpc":"2.0","id":2,"method":"textDocument/rename","params":{"textDocument":{"uri":"$uri"},"position":{"line":0,"character":${mainText.lastIndexOf("area") + 1}},"newName":"measure"}}""",
            """{"jsonrpc":"2.0","id":3,"method":"shutdown","params":null}""",
            """{"jsonrpc":"2.0","method":"exit"}"""
        )
        val output = ByteArrayOutputStream()
        assertEquals(
            0,
            LspServer().run(ByteArrayInputStream(messages.joinToString(separator = "", transform = ::frame).toByteArray()), output)
        )

        val response = output.toString(Charsets.UTF_8)
        assertTrue(response.contains("\"id\":2, \"result\":{\"changes\":"), response)
        assertTrue(response.contains(extension.toUri().toString()), response)
        assertTrue(response.contains(main.toUri().toString()), response)
        assertTrue(Regex("\\\"newText\\\":\\\"measure\\\"").findAll(response).count() >= 2, response)
    }

    private fun positionAt(text: String, offset: Int): LspPosition {
        val prefix = text.substring(0, offset)
        val line = prefix.count { it == '\n' }
        val character = prefix.substringAfterLast('\n').length
        return LspPosition(line, character)
    }

    private fun frame(message: String): String {
        val bytes = message.toByteArray()
        return "Content-Length: ${bytes.size}\r\n\r\n$message"
    }
}
