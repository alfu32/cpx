package cplus.cli

import cplus.compiler.CPlusCompiler
import cplus.compiler.CompileRequest
import cplus.compiler.ImportIndex
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImportQuickFixTest {
    @Test
    fun mapsOnlyCompatibleUnresolvedSymbolsToPublicProviderEdits() {
        val root = Files.createTempDirectory("cplus-import-quick-fix")
        val main = root.resolve("main.cp")
        Files.writeString(
            main,
            "int known_fn(int value); int main() { MissingType value; return missing_fn() + missing_value + known_fn(1, 2) + known_fn(\"bad\"); }\n"
        )
        Files.writeString(
            root.resolve("helper.cp"),
            "pub struct MissingType { int value; }; pub int missing_fn() { return 1; } pub int missing_value; int private_value;\n"
        )
        Files.writeString(root.resolve("alternative.cp"), "pub int missing_fn() { return 2; }\n")
        val compiled = CPlusCompiler().compile(CompileRequest(listOf(main)))
        val index = ImportIndex().build(listOf(root))

        val fixes = LspLanguageService.importQuickFixes(compiled, Files.readString(main), main, index)

        assertEquals(setOf("MissingType", "missing_fn", "missing_value"), fixes.map { it.title.substringAfter("Import '").substringBefore('\'') }.toSet())
        assertEquals(2, fixes.count { it.title.contains("missing_fn") })
        assertTrue(fixes.all { fix -> fix.edits.any { it.newText.contains("import") || it.newText.contains(", ") } })
        assertFalse(fixes.any { it.title.contains("private_value") })
        assertFalse(fixes.any { it.title.contains("known_fn") })
        assertFalse(fixes.any { it.title.contains("SEM302") || it.title.contains("SEM303") })
    }

    @Test
    fun doesNotOfferImportFixesWhenTheSourceHasMalformedSyntax() {
        val root = Files.createTempDirectory("cplus-import-quick-fix-malformed")
        val main = root.resolve("main.cp")
        Files.writeString(main, "int main() { return missing_fn( ; }\n")
        Files.writeString(root.resolve("helper.cp"), "pub int missing_fn() { return 1; }\n")
        val compiled = CPlusCompiler().compile(CompileRequest(listOf(main)))
        val index = ImportIndex().build(listOf(root))

        assertTrue(LspLanguageService.importQuickFixes(compiled, Files.readString(main), main, index).isEmpty())
    }

    @Test
    fun applyingUnresolvedCallableFixRemovesItsDiagnostic() {
        val root = Files.createTempDirectory("cplus-import-quick-fix-apply")
        val main = root.resolve("main.cp")
        val source = "int main() { return missing_fn(); }\n"
        Files.writeString(main, source)
        Files.writeString(root.resolve("helper.cp"), "pub int missing_fn() { return 42; }\n")
        val compiled = CPlusCompiler().compile(CompileRequest(listOf(main)))
        val index = ImportIndex().build(listOf(root))
        val action = LspLanguageService.importQuickFixes(compiled, source, main, index).single()
        val edited = action.edits.sortedByDescending { it.range.startOffset }.fold(source) { current, edit ->
            current.replaceRange(edit.range.startOffset, edit.range.endOffset, edit.newText)
        }
        Files.writeString(main, edited)

        assertEquals(0, Cli().run(listOf("check", main.toString())))
    }
}
