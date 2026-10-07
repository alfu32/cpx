package cplus.cli

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LspWorkspaceTest {
    @Test
    fun appliesRangedChangesInOrderAndRetainsClientVersion() {
        val workspace = LspWorkspace()
        val uri = "file:///workspace/main.cp"
        workspace.open(uri, Path.of("/workspace/main.cp"), 1, "int main() {\n    return 0;\n}")

        val updated = workspace.change(
            uri,
            2,
            listOf(
                LspTextChange(
                    LspTextRange(LspPosition(1, 11), LspPosition(1, 12)),
                    "42"
                ),
                LspTextChange(
                    LspTextRange(LspPosition(1, 13), LspPosition(1, 14)),
                    ""
                )
            )
        )

        assertEquals(2, updated?.version)
        assertEquals("int main() {\n    return 42\n}", updated?.text)
    }

    @Test
    fun rejectsInvalidRangesWithoutDiscardingTheCurrentDocument() {
        val workspace = LspWorkspace()
        val uri = "file:///workspace/main.cp"
        val original = workspace.open(uri, Path.of("/workspace/main.cp"), 4, "int main() {}")

        assertNull(
            workspace.change(
                uri,
                5,
                listOf(
                    LspTextChange(
                        LspTextRange(LspPosition(-1, 0), LspPosition(0, 0)),
                        "broken"
                    )
                )
            )
        )
        assertEquals(original, workspace.get(uri))
    }
}
