package cplus.cli

import cplus.compiler.CPlusCompiler
import cplus.compiler.CompileRequest
import cplus.compiler.ImportIndex
import cplus.core.LineIndex
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LspComptimeImportTest {
    @Test
    fun cpxImportQuickFixEnablesExpansionAndGeneratedMemberCompletion() {
        val root = Files.createTempDirectory("cplus-lsp-comptime-import-fix")
        root.resolve("box.cp").also {
            Files.writeString(
                it,
                "pub comptime cpx<decl> box(type T) { return { struct box_{T}_t { T value; }; }; }\n" +
                    "comptime cpx<decl> privateBox(type T) { return {}; }\n"
            )
        }
        val main = root.resolve("main.cp")
        val unresolved = "box(int); int main() { struct box_int_t item; return item.value; }\n"
        Files.writeString(main, unresolved)
        val compiler = CPlusCompiler()
        val failed = compiler.compile(CompileRequest(listOf(main)))
        val index = ImportIndex().build(listOf(root))

        val completionPosition = positionAt(unresolved, unresolved.indexOf("box(int)") + 3)
        val completion = LspLanguageService.completion(failed, unresolved, completionPosition, index, main)
        assertTrue(completion.any { it.label == "box" }, completion.toString())
        assertTrue(completion.none { it.label == "privateBox" }, completion.toString())

        val fix = LspLanguageService.importQuickFixes(failed, unresolved, main, index).single()
        assertEquals("Import 'box' from box", fix.title)
        val resolvedText = fix.edits.sortedByDescending { it.range.startOffset }.fold(unresolved) { current, edit ->
            current.replaceRange(edit.range.startOffset, edit.range.endOffset, edit.newText)
        }
        Files.writeString(main, resolvedText)
        val resolved = compiler.compile(CompileRequest(listOf(main)))

        assertTrue(resolved.isSuccessful, resolved.diagnostics.joinToString())
        val memberCursor = positionAt(resolvedText, resolvedText.indexOf("item.value") + "item.val".length)
        assertTrue(
            LspLanguageService.completion(resolved, resolvedText, memberCursor, sourcePath = main)
                .any { it.label == "value" },
            "generated box field should be available to member completion"
        )
    }

    @Test
    fun importedCpxHoverAndNavigationReachAliasedProviderDeclaration() {
        val root = Files.createTempDirectory("cplus-lsp-comptime-navigation")
        val provider = root.resolve("box.cp").also {
            Files.writeString(
                it,
                "/// Generates a typed box.\npub comptime cpx<decl> box(type T) { return { struct box_{T}_t { T value; }; }; }\n"
            )
        }
        val main = root.resolve("main.cp").also {
            Files.writeString(
                it,
                "import { box as makeBox } from \"./box.cp\"; makeBox(int); int main() { struct box_int_t item; return item.value; }\n"
            )
        }
        val text = Files.readString(main)
        val compiler = CPlusCompiler()
        val result = compiler.compile(CompileRequest(listOf(main)))
        val index = ImportIndex().build(listOf(root))
        val callOffset = text.indexOf("makeBox(int)")
        val callPosition = positionAt(text, callOffset + 2)

        val hover = LspLanguageService.hover(result, text, callPosition, main, index)
        assertTrue(hover?.markdown?.contains("comptime decl box") == true, hover?.markdown.orEmpty())
        val navigation = assertNotNull(
            LspLanguageService.navigation(
                result,
                text,
                callPosition,
                sourcePath = main,
                sourcePathFor = compiler::sourcePathFor,
                importIndex = index
            )
        )
        val definition = assertNotNull(navigation.externalDefinition)
        assertEquals(provider.toAbsolutePath().normalize(), definition.path.toAbsolutePath().normalize())
        assertEquals("box", definition.name)
        assertTrue(navigation.references.any { it.file == result.artifacts.first { artifact -> artifact.source.path == main }.source.id })
    }

    @Test
    fun lspProtocolPublishesImportedCpxHoverDefinitionAndReferences() {
        val root = Files.createTempDirectory("cplus-lsp-comptime-protocol")
        root.resolve("box.cp").also {
            Files.writeString(it, "pub comptime cpx<decl> box(type T) { return { struct box_{T}_t { T value; }; }; }\n")
        }
        val main = root.resolve("main.cp")
        val uri = main.toUri().toString()
        val text = "import { box as makeBox } from \"./box.cp\"; makeBox(int); int main() { struct box_int_t item; return item.value; }\n"
        val jsonText = text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        val callOffset = text.indexOf("makeBox(int)")
        val input = listOf(
            """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"rootUri":"${root.toUri()}"}}""",
            """{"jsonrpc":"2.0","method":"textDocument/didOpen","params":{"textDocument":{"uri":"$uri","version":1,"text":"$jsonText"}}}""",
            """{"jsonrpc":"2.0","id":2,"method":"textDocument/hover","params":{"textDocument":{"uri":"$uri"},"position":{"line":0,"character":${callOffset + 2}}}}""",
            """{"jsonrpc":"2.0","id":3,"method":"textDocument/definition","params":{"textDocument":{"uri":"$uri"},"position":{"line":0,"character":${callOffset + 2}}}}""",
            """{"jsonrpc":"2.0","id":4,"method":"textDocument/references","params":{"textDocument":{"uri":"$uri"},"position":{"line":0,"character":${callOffset + 2}},"context":{"includeDeclaration":true}}}""",
            """{"jsonrpc":"2.0","id":5,"method":"shutdown","params":null}""",
            """{"jsonrpc":"2.0","method":"exit"}"""
        ).joinToString("") { frame(it) }
        val output = ByteArrayOutputStream()

        assertEquals(0, LspServer().run(ByteArrayInputStream(input.toByteArray()), output))

        val responses = output.toString(Charsets.UTF_8)
        assertTrue(responses.contains("comptime decl box"), responses)
        assertTrue(responses.contains(root.resolve("box.cp").toUri().toString()), responses)
        assertTrue(responses.contains("\"id\":4"), responses)
    }

    @Test
    fun providerOverlayEditsRefreshClientDiagnosticsAndReferences() {
        val root = Files.createTempDirectory("cplus-lsp-comptime-provider-edit")
        val provider = root.resolve("box.cp")
        val providerUri = provider.toUri().toString()
        val publicProvider = "pub comptime cpx<decl> box(type T) { return { struct box_{T}_t { T value; }; }; }\n"
        val privateProvider = publicProvider.removePrefix("pub ")
        Files.writeString(provider, publicProvider)
        val main = root.resolve("main.cp")
        val mainUri = main.toUri().toString()
        val mainText = "import { box } from \"./box.cp\"; box(int); int main() { struct box_int_t item; return item.value; }\n"
        val callOffset = mainText.indexOf("box(int)")
        val mainJson = mainText.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        val publicJson = publicProvider.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        val privateJson = privateProvider.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        val input = listOf(
            """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"rootUri":"${root.toUri()}"}}""",
            """{"jsonrpc":"2.0","method":"textDocument/didOpen","params":{"textDocument":{"uri":"$mainUri","version":1,"text":"$mainJson"}}}""",
            """{"jsonrpc":"2.0","method":"textDocument/didOpen","params":{"textDocument":{"uri":"$providerUri","version":1,"text":"$publicJson"}}}""",
            """{"jsonrpc":"2.0","id":2,"method":"textDocument/references","params":{"textDocument":{"uri":"$mainUri"},"position":{"line":0,"character":${callOffset + 1}},"context":{"includeDeclaration":true}}}""",
            """{"jsonrpc":"2.0","method":"textDocument/didChange","params":{"textDocument":{"uri":"$providerUri","version":2},"contentChanges":[{"text":"$privateJson"}]}}""",
            """{"jsonrpc":"2.0","id":3,"method":"textDocument/references","params":{"textDocument":{"uri":"$mainUri"},"position":{"line":0,"character":${callOffset + 1}},"context":{"includeDeclaration":true}}}""",
            """{"jsonrpc":"2.0","id":4,"method":"shutdown","params":null}""",
            """{"jsonrpc":"2.0","method":"exit"}"""
        ).joinToString("") { frame(it) }
        val output = ByteArrayOutputStream()

        assertEquals(0, LspServer().run(ByteArrayInputStream(input.toByteArray()), output))

        val responses = output.toString(Charsets.UTF_8)
        assertTrue(responses.contains("\"code\":\"SEM406\""), responses)
        assertTrue(responseFor(responses, 2).contains(providerUri), responseFor(responses, 2))
        assertTrue(responseFor(responses, 3).contains("\"result\":[]"), responseFor(responses, 3))
    }

    private fun positionAt(text: String, offset: Int): LspPosition {
        val position = LineIndex.from(text).positionAt(offset)
        return LspPosition(position.line - 1, position.column - 1)
    }

    private fun frame(message: String): String {
        val body = message.toByteArray(Charsets.UTF_8)
        return "Content-Length: ${body.size}\r\n\r\n$message"
    }

    private fun responseFor(responses: String, id: Int): String {
        val marker = "\"id\":$id"
        val start = responses.indexOf(marker)
        if (start < 0) return ""
        val next = responses.indexOf("\"id\":", start + marker.length)
        return responses.substring(start, if (next < 0) responses.length else next)
    }
}
