package cplus.comptime

import cplus.core.*
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CpxExpansionTest {
    @Test
    fun templateNodesDistinguishDirectBindingFromIdentifierComposition() {
        val origin = Origin.Direct(SourceRange(SourceFileId(12), 0, 1))
        val template = CpxTemplateParser().parse(
            "Temporary T optional_{T}_t",
            CpxCategory.DECLARATION,
            origin,
            setOf("T")
        )

        val rendered = template.render(mapOf("T" to ComptimeValue.CtType("int")))
        assertEquals("Temporary int optional_int_t", rendered)
        assertTrue(template.nodes.any { it == TemplateNode.Binding("T", explicit = false) })
        assertTrue(template.nodes.any { it == TemplateNode.Binding("T", explicit = true) })
    }

    @Test
    fun expandsTypedDeclarationAndReusesEquivalentSpecialization() {
        val sourceText = """
            comptime cpx<decl> optional(type T) {
                return {
                    struct optional_{T}_t { T value; };
                };
            }
            optional(int);
            optional(int);
        """.trimIndent()
        val source = SourceFile(SourceFileId(10), Path.of("optional.cp"), sourceText, 1)
        val parsed = Parser(Lexer().lex(source)).parse()
        val result = CpxExpander().expand(source, parsed.syntax)

        assertTrue(result.diagnostics.isEmpty(), result.diagnostics.joinToString())
        assertEquals(setOf(ExpansionKey("optional", listOf("int"))), result.expandedKeys)
        assertEquals(listOf("optional_int_t"), result.program.declarations.filterIsInstance<SyntaxStruct>().map { it.name })
        val generated = result.program.declarations.filterIsInstance<SyntaxStruct>().single()
        assertEquals("int", generated.fields.single().type.name)
        assertTrue(generated.origin is Origin.Expansion)
    }

    @Test
    fun detectsNestedExpansionCycleByExpansionKey() {
        val sourceText = """
            comptime cpx<decl> a(type T) { return { b(T); }; }
            comptime cpx<decl> b(type T) { return { a(T); }; }
            a(int);
        """.trimIndent()
        val source = SourceFile(SourceFileId(11), Path.of("cycle.cp"), sourceText, 1)
        val parsed = Parser(Lexer().lex(source)).parse()
        val result = CpxExpander().expand(source, parsed.syntax)

        assertTrue(result.diagnostics.any { it.code == "CPX002" }, result.diagnostics.joinToString())
    }
}
