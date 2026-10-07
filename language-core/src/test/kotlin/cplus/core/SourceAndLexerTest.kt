package cplus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.nio.file.Path

class SourceAndLexerTest {
    @Test
    fun sourceIdentitySurvivesUpdates() {
        val repository = SourceRepository()
        val path = Path.of("fixture.cp")
        val first = repository.put(path, "int main() { return 0; }")
        val second = repository.put(path, "int main() { return 1; }")

        assertEquals(first.id, second.id)
        assertEquals(1L, first.version)
        assertEquals(2L, second.version)
        assertEquals(second.text, repository.get(first.id).text)
    }

    @Test
    fun lexerRecognizesCPlusTokensAndSkipsComments() {
        val source = SourceFile(SourceFileId(1), Path.of("fixture.cp"), "// comment\ncomptime int value = 42;", 1)
        val result = Lexer().lex(source)

        assertTrue(result.diagnostics.isEmpty())
        assertEquals(listOf("comptime", "int", "value", "=", "42", ";", ""), result.tokens.map { it.lexeme })
        assertEquals(TokenKind.KEYWORD, result.tokens[0].kind)
        assertEquals(TokenKind.INTEGER_LITERAL, result.tokens[4].kind)
    }

    @Test
    fun relexReusesTheSafeBoundaryButMatchesAFullLex() {
        val repository = SourceRepository()
        val path = Path.of("incremental.cp")
        val first = repository.put(path, "int first;\nint second;")
        val previous = Lexer().lex(first)
        val updated = repository.put(path, "int first;\nint inserted;\nint second;")

        val incremental = Lexer().relex(updated, previous, 11..21)
        val complete = Lexer().lex(updated)

        assertEquals(complete.tokens, incremental.tokens)
        assertEquals(complete.diagnostics, incremental.diagnostics)
        assertEquals(updated.id, incremental.source.id)
    }

    @Test
    fun relexFallsBackToAuthoritativeDiagnosticsForAChangedLiteralContext() {
        val repository = SourceRepository()
        val path = Path.of("literal.cp")
        val first = repository.put(path, "int value = 1;")
        val previous = Lexer().lex(first)
        val updated = repository.put(path, "int value = \"unterminated;")

        val incremental = Lexer().relex(updated, previous, 12..24)

        assertTrue(incremental.diagnostics.any { it.code == "LEX002" })
        assertEquals(Lexer().lex(updated).tokens, incremental.tokens)
    }

    @Test
    fun lineIndexRoundTripsPositions() {
        val index = LineIndex.from("alpha\nbeta\ngamma")

        val position = index.positionAt(7)
        assertEquals(SourcePosition(2, 2), position)
        assertEquals(7, index.offsetAt(position))
    }

    @Test
    fun originsRetainNestedExpansionAncestry() {
        val directRange = SourceRange(SourceFileId(1), 4, 8)
        val definition = Origin.Direct(directRange)
        val invocation = Origin.Direct(SourceRange(SourceFileId(1), 20, 24))
        val generated = Origin.Generated(definition)
        val expansion = Origin.Expansion(definition, invocation, generated, "optional:int")

        assertEquals(invocation.primaryRange, expansion.primaryRange)
        assertEquals(definition, expansion.definition)
        assertEquals(generated, expansion.parent)
    }
}
