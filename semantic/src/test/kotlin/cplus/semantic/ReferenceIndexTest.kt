package cplus.semantic

import cplus.core.AstBuilder
import cplus.core.Lexer
import cplus.core.Parser
import cplus.core.SourceFile
import cplus.core.SourceFileId
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class ReferenceIndexTest {
    @Test
    fun resolvedIdentifiersExposeSymbolIdsAndCanBeInvalidated() {
        val source = SourceFile(
            SourceFileId(40),
            Path.of("references.cp"),
            "int value; int main() { return value; }",
            1
        )
        val parsed = Parser(Lexer().lex(source)).parse()
        val result = SemanticAnalyzer().analyze(AstBuilder().build(parsed.syntax))
        val model = assertNotNull(result.model)
        val symbol = assertNotNull(model.symbolNamed("value"))
        val references = model.resolvedAst.referenceIndex.referencesTo(symbol.id)

        assertEquals(1, references.size)
        val reference = references.single()
        assertEquals(symbol.id, model.resolvedAst.symbolFor(reference.node))
        assertIs<cplus.core.AstIdentifier>(model.resolvedAst.nodes[reference.node])

        model.referenceIndex.invalidate(setOf(reference.node))

        assertEquals(emptyList(), model.referenceIndex.referencesTo(symbol.id))
    }
}
