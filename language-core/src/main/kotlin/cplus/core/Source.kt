package cplus.core

import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

@JvmInline
value class SourceFileId(val value: Int)

data class SourceFile(
    val id: SourceFileId,
    val path: Path,
    val text: String,
    val version: Long
)

data class SourcePosition(
    val line: Int,
    val column: Int
)

data class SourceRange(
    val file: SourceFileId,
    val startOffset: Int,
    val endOffset: Int
) {
    init {
        require(startOffset >= 0) { "startOffset must not be negative" }
        require(endOffset >= startOffset) { "endOffset must not precede startOffset" }
    }

}

class LineIndex private constructor(private val lineStarts: IntArray) {
    fun positionAt(offset: Int): SourcePosition {
        require(offset >= 0) { "offset must not be negative" }
        var low = 0
        var high = lineStarts.lastIndex
        while (low <= high) {
            val middle = (low + high) ushr 1
            if (lineStarts[middle] <= offset) {
                low = middle + 1
            } else {
                high = middle - 1
            }
        }
        val line = high.coerceAtLeast(0)
        return SourcePosition(line + 1, offset - lineStarts[line] + 1)
    }

    fun offsetAt(position: SourcePosition): Int {
        require(position.line > 0) { "line must be positive" }
        require(position.column > 0) { "column must be positive" }
        val lineIndex = (position.line - 1).coerceIn(0, lineStarts.lastIndex)
        return lineStarts[lineIndex] + position.column - 1
    }

    companion object {
        fun from(text: String): LineIndex {
            val starts = ArrayList<Int>()
            starts += 0
            text.forEachIndexed { index, character ->
                if (character == '\n') starts += index + 1
            }
            return LineIndex(starts.toIntArray())
        }
    }
}

class SourceRepository {
    private val nextId = AtomicInteger(1)
    private val idsByPath = linkedMapOf<Path, SourceFileId>()
    private val filesById = linkedMapOf<SourceFileId, SourceFile>()

    fun put(path: Path, text: String): SourceFile {
        val normalized = path.toAbsolutePath().normalize()
        val id = idsByPath.getOrPut(normalized) { SourceFileId(nextId.getAndIncrement()) }
        val previous = filesById[id]
        val file = SourceFile(id, normalized, text, (previous?.version ?: 0L) + 1L)
        filesById[id] = file
        return file
    }

    fun get(id: SourceFileId): SourceFile = filesById[id]
        ?: error("Unknown source file id: ${id.value}")

    fun find(path: Path): SourceFile? = idsByPath[path.toAbsolutePath().normalize()]?.let(::get)
}
