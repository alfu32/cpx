package cplus.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull

class ImportEditsTest {
    @Test
    fun insertsIntoEmptyAndPackageFilesWithoutMovingComments() {
        val empty = requireNotNull(ImportEdits.build("", "std.fs", "std_fs_open"))
        assertEquals("import { std_fs_open } from std.fs;\n", empty.importEdit?.newText)
        assertEquals(0, empty.importEdit?.range?.startOffset)

        val source = "package demo;\n\n// documentation for main\nint main() { return 0; }\n"
        val plan = requireNotNull(ImportEdits.build(source, "std.fs", "std_fs_open"))
        val edited = apply(source, requireNotNull(plan.importEdit))
        assertEquals("package demo;\nimport { std_fs_open } from std.fs;\n\n// documentation for main\nint main() { return 0; }\n", edited)
    }

    @Test
    fun mergesCompatibleSelectiveImportsAndIsIdempotent() {
        val source = "import { old } from std.fs; // retain this comment\nint main() { return 0; }\n"
        val plan = requireNotNull(ImportEdits.build(source, "std.fs", "std_fs_open"))
        assertEquals(", std_fs_open", plan.importEdit?.newText)
        val edited = apply(source, requireNotNull(plan.importEdit))
        assertEquals("import { old, std_fs_open } from std.fs; // retain this comment\nint main() { return 0; }\n", edited)
        assertNull(ImportEdits.build(edited, "std.fs", "std_fs_open")?.importEdit)
    }

    @Test
    fun preservesCrLfBomAndNonBmpText() {
        val crlf = "package demo;\r\nint main() { return 0; }\r\n"
        val crlfPlan = requireNotNull(ImportEdits.build(crlf, "std.fs", "std_fs_open"))
        assertEquals("import { std_fs_open } from std.fs;\r\n", crlfPlan.importEdit?.newText)
        assertEquals("package demo;\r\nimport { std_fs_open } from std.fs;\r\nint main() { return 0; }\r\n", apply(crlf, requireNotNull(crlfPlan.importEdit)))

        val bom = "\uFEFF// 😀 header\nint main() { return 0; }\n"
        val bomPlan = requireNotNull(ImportEdits.build(bom, "std.fs", "std_fs_open"))
        val bomEdited = apply(bom, requireNotNull(bomPlan.importEdit))
        assertEquals('\uFEFF', bomEdited.first())
        assertEquals("import { std_fs_open } from std.fs;\n", bomEdited.substring(2).substringBefore("//"))
        assertTrueContains(bomEdited, "// 😀 header")
    }

    @Test
    fun reusesExistingAliasesAndQualifiedModuleImports() {
        val selective = "import { original as local_name } from std.fs;\n"
        val selected = requireNotNull(ImportEdits.build(selective, "std.fs", "original"))
        assertNull(selected.importEdit)
        assertEquals("local_name", selected.symbolReplacement)

        val module = "import std.fs as filesystem;\n"
        val qualified = requireNotNull(ImportEdits.build(module, "std.fs", "std_fs_open"))
        assertNull(qualified.importEdit)
        assertEquals("filesystem.std_fs_open", qualified.symbolReplacement)

        val unaliased = requireNotNull(ImportEdits.build("import std.fs;\n", "std.fs", "std_fs_open"))
        assertNull(unaliased.importEdit)
        assertEquals("std.fs.std_fs_open", unaliased.symbolReplacement)
    }

    @Test
    fun avoidsConflictsAndUsesSeparateImportForMultilineOrCommentedLists() {
        assertNull(ImportEdits.build("int std_fs_open;\n", "std.fs", "std_fs_open"))
        assertNull(ImportEdits.build("import { old } from std.other;\n", "std.fs", "old"))

        val source = "import {\n  old,\n} from std.fs; // keep comment\nint main() { return 0; }\n"
        val plan = requireNotNull(ImportEdits.build(source, "std.fs", "std_fs_open"))
        val edited = apply(source, requireNotNull(plan.importEdit))
        assertEquals(source.replace("int main()", "import { std_fs_open } from std.fs;\nint main()"), edited)
        assertTrueContains(edited, "} from std.fs; // keep comment\n")

        assertNull(ImportEdits.build("import { old from std.fs;\n", "std.fs", "new_name"))

        val spacedPath = requireNotNull(ImportEdits.build("", "./some path/helper.cp", "helper_fn"))
        assertEquals("import { helper_fn } from \"./some path/helper.cp\";\n", spacedPath.importEdit?.newText)
    }

    private fun apply(source: String, edit: ImportTextEdit): String = buildString {
        append(source, 0, edit.range.startOffset)
        append(edit.newText)
        append(source, edit.range.endOffset, source.length)
    }

    private fun assertTrueContains(source: String, expected: String) {
        assertNotNull(source.takeIf { expected in it })
    }
}
