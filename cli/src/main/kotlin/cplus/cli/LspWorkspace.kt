package cplus.cli

import cplus.core.LineIndex
import cplus.core.SourcePosition
import java.nio.file.Path

/** Immutable snapshot of one document owned by an LSP workspace. */
internal data class WorkspaceDocument(
    val uri: String,
    val path: Path,
    val version: Int,
    val text: String
)

/** A zero-based LSP text position. LSP characters use UTF-16 code units. */
internal data class LspPosition(
    val line: Int,
    val character: Int
)

internal data class LspTextRange(
    val start: LspPosition,
    val end: LspPosition
)

internal data class LspTextChange(
    val range: LspTextRange?,
    val text: String
)

/**
 * Owns the live workspace documents and their client versions.
 *
 * The store is deliberately independent of compilation. The LSP server can
 * snapshot a document and hand that text to [cplus.compiler.CPlusCompiler]
 * without creating a second parser or semantic model.
 */
internal class LspWorkspace {
    private val documents = linkedMapOf<String, WorkspaceDocument>()

    fun open(uri: String, path: Path, version: Int, text: String): WorkspaceDocument {
        val document = WorkspaceDocument(uri, path, version, text)
        documents[uri] = document
        return document
    }

    /**
     * Applies changes in the order supplied by the client. Each ranged change
     * is applied to the result of the preceding change in the same
     * notification, as required by the LSP text document protocol.
     */
    fun change(uri: String, version: Int, changes: List<LspTextChange>): WorkspaceDocument? {
        val current = documents[uri] ?: return null
        var text = current.text
        for (change in changes) {
            text = if (change.range == null) {
                change.text
            } else {
                apply(text, change.range, change.text) ?: return null
            }
        }
        return open(uri, current.path, version, text)
    }

    fun close(uri: String): WorkspaceDocument? = documents.remove(uri)

    fun get(uri: String): WorkspaceDocument? = documents[uri]

    fun snapshot(): List<WorkspaceDocument> = documents.values.toList()

    private fun apply(text: String, range: LspTextRange, replacement: String): String? {
        val lineIndex = LineIndex.from(text)
        val start = offsetAt(lineIndex, range.start, text.length) ?: return null
        val end = offsetAt(lineIndex, range.end, text.length) ?: return null
        if (end < start) return null
        return buildString(text.length - (end - start) + replacement.length) {
            append(text, 0, start)
            append(replacement)
            append(text, end, text.length)
        }
    }

    private fun offsetAt(index: LineIndex, position: LspPosition, textLength: Int): Int? {
        if (position.line < 0 || position.character < 0) return null
        val offset = runCatching {
            index.offsetAt(SourcePosition(position.line + 1, position.character + 1))
        }.getOrNull() ?: return null
        return offset.takeIf { it in 0..textLength }
    }
}
