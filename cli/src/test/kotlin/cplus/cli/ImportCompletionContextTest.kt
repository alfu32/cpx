package cplus.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ImportCompletionContextTest {
    @Test
    fun classifiesProviderAndSelectiveNameContextsAcrossIncompleteImports() {
        val cases = listOf(
            Case("import std.|", ImportCompletionKind.PROVIDER, "std."),
            Case("import std/io|", ImportCompletionKind.PROVIDER, "std/io"),
            Case("import c.st|", ImportCompletionKind.PROVIDER, "c.st"),
            Case("import ./modules/m|", ImportCompletionKind.PROVIDER, "./modules/m"),
            Case("import { std_fs_op|", ImportCompletionKind.SELECTIVE_NAME, "std_fs_op"),
            Case("import {\n  std_fs_op|\n} from std.fs", ImportCompletionKind.SELECTIVE_NAME, "std_fs_op"),
            Case("import { std_fs_open } from std.|", ImportCompletionKind.PROVIDER, "std."),
            Case("import { std_fs_open }\nfrom \"./module path/re|f.cp\"", ImportCompletionKind.PROVIDER, "./module path/re")
        )

        cases.forEach { case ->
            val (text, position) = marked(case.source)
            val context = ImportCompletionContextFinder.find(text, position)
            assertEquals(case.kind, context?.kind, case.source)
            assertEquals(case.prefix, context?.prefix, case.source)
            assertEquals(case.prefix, text.substring(context!!.replacementRange.startOffset, context.replacementRange.endOffset)
                .take(context.prefix.length), case.source)
        }
    }

    @Test
    fun excludesAliasesCommentsAndOrdinaryStringContents() {
        val excluded = listOf(
            "import std.fs as |filesystem",
            "import { std_fs_open as |open } from std.fs",
            "import { std_fs_open as open| } from std.fs",
            "// import std.|",
            "/* import { std_| } */",
            "const char* text = \"import std.|\";"
        )
        excluded.forEach { source ->
            val (text, position) = marked(source)
            assertNull(ImportCompletionContextFinder.find(text, position), source)
        }
    }

    @Test
    fun replacementRangesRespectQuotedPathContentsAndUtf16Positions() {
        val (quotedText, quotedPosition) = marked("import { x } from \"./mod|ule.cp\"")
        val quoted = requireNotNull(ImportCompletionContextFinder.find(quotedText, quotedPosition))
        assertEquals("./mod", quoted.prefix)
        assertEquals("./module.cp", quotedText.substring(quoted.replacementRange.startOffset, quoted.replacementRange.endOffset))

        val (unicodeText, unicodePosition) = marked("const char* marker = \"😀\"; import { na|me } from std.api")
        assertEquals("const char* marker = \"😀\"; import { na".length, unicodePosition.character)
        val unicode = requireNotNull(ImportCompletionContextFinder.find(unicodeText, unicodePosition))
        assertEquals("na", unicode.prefix)
        assertEquals("name", unicodeText.substring(unicode.replacementRange.startOffset, unicode.replacementRange.endOffset))
    }

    @Test
    fun capturesSelectiveProviderAndPreviouslyImportedNames() {
        val (text, position) = marked("import { first, se|cond } from \"./helpers.cp\"")
        val context = requireNotNull(ImportCompletionContextFinder.find(text, position))

        assertEquals("./helpers.cp", context.provider)
        assertEquals(setOf("first"), context.existingNames)
        assertEquals("se", context.prefix)
    }

    @Test
    fun findsOnlyOrdinaryUnqualifiedIdentifierContextsForAutoImport() {
        val (text, position) = marked("int main() { return std_fs_op|en; }")
        val context = requireNotNull(ImportCompletionContextFinder.identifier(text, position))
        assertEquals("std_fs_op", context.prefix)
        assertEquals("std_fs_open", text.substring(context.replacementRange.startOffset, context.replacementRange.endOffset))

        listOf(
            "// std_fs_op|en",
            "const char* text = \"std_fs_op|en\";",
            "value.std_fs_op|en",
            "pointer->std_fs_op|en"
        ).forEach { source ->
            val (plainText, plainPosition) = marked(source)
            assertNull(ImportCompletionContextFinder.identifier(plainText, plainPosition), source)
        }
    }

    private data class Case(
        val source: String,
        val kind: ImportCompletionKind,
        val prefix: String
    )

    private fun marked(source: String): Pair<String, LspPosition> {
        val offset = source.indexOf('|')
        require(offset >= 0 && source.indexOf('|', offset + 1) < 0)
        val text = source.removeRange(offset, offset + 1)
        val before = text.substring(0, offset)
        val line = before.count { it == '\n' }
        val lineStart = before.lastIndexOf('\n').let { if (it < 0) 0 else it + 1 }
        return text to LspPosition(line, offset - lineStart)
    }
}
