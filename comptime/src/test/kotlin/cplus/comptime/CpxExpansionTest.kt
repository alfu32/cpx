package cplus.comptime

import cplus.core.*
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
        assertEquals(
            setOf(
                SpecializationKey(
                    "optional",
                    listOf(CanonicalComptimeValue("type", "int"))
                )
            ),
            result.specializationKeys
        )
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

    @Test
    fun equivalentTypeSpellingSharesOneSpecializationKey() {
        val sourceText = """
            comptime cpx<decl> optional(type T) {
                return { struct optional_{T}_t { T value; }; };
            }
            optional(struct item);
            optional(item);
        """.trimIndent()
        val source = SourceFile(SourceFileId(15), Path.of("canonical.cp"), sourceText, 1)
        val parsed = Parser(Lexer().lex(source)).parse()
        val result = CpxExpander().expand(source, parsed.syntax)

        assertTrue(result.diagnostics.isEmpty(), result.diagnostics.joinToString())
        assertEquals(setOf(ExpansionKey("optional", listOf("item"))), result.expandedKeys)
        assertEquals(
            setOf(SpecializationKey("optional", listOf(CanonicalComptimeValue("type", "item")))),
            result.specializationKeys
        )
    }

    @Test
    fun specializationCacheReusesRenderedTextButReparsesEachInvocation() {
        val sourceText = """
            comptime cpx<decl> optional(type T) {
                return { struct optional_{T}_t { T value; }; };
            }
            optional(int);
        """.trimIndent()
        val source = SourceFile(SourceFileId(16), Path.of("cached.cp"), sourceText, 1)
        val parsed = Parser(Lexer().lex(source)).parse()
        val cache = SpecializationCache()
        val expander = CpxExpander(specializationCache = cache)

        val first = expander.expand(source, parsed.syntax)
        val second = expander.expand(source, parsed.syntax)

        assertTrue(first.diagnostics.isEmpty(), first.diagnostics.joinToString())
        assertTrue(second.diagnostics.isEmpty(), second.diagnostics.joinToString())
        assertEquals(first.program, second.program)
        assertEquals(SpecializationCacheStatistics(1, hits = 1, misses = 1), cache.statistics())
    }

    @Test
    fun specializationCacheDoesNotSuppressCachedExpansionDiagnostics() {
        val sourceText = """
            comptime cpx<decl> broken(type T) {
                return { missing(T); };
            }
            broken(int);
        """.trimIndent()
        val source = SourceFile(SourceFileId(17), Path.of("broken-cache.cp"), sourceText, 1)
        val parsed = Parser(Lexer().lex(source)).parse()
        val cache = SpecializationCache()
        val expander = CpxExpander(specializationCache = cache)

        val first = expander.expand(source, parsed.syntax)
        val second = expander.expand(source, parsed.syntax)

        assertTrue(first.diagnostics.isNotEmpty())
        assertTrue(second.diagnostics.isNotEmpty())
        assertEquals(1L, cache.statistics().hits)
        assertEquals(1L, cache.statistics().misses)
    }

    @Test
    fun nestedExpansionLimitsStopUnboundedGeneratedWork() {
        val sourceText = """
            comptime cpx<decl> outer(type T) { return { inner(T); }; }
            comptime cpx<decl> inner(type T) { return { struct inner_{T}_t { T value; }; }; }
            outer(int);
        """.trimIndent()
        val source = SourceFile(SourceFileId(18), Path.of("limits.cp"), sourceText, 1)
        val parsed = Parser(Lexer().lex(source)).parse()
        val result = CpxExpander(
            limits = CpxExpansionLimits(maxExpansionDepth = 1)
        ).expand(source, parsed.syntax)

        assertTrue(result.diagnostics.any { it.code == "CPX007" }, result.diagnostics.joinToString())
        assertTrue(result.diagnostics.none { it.code == "CPX002" }, result.diagnostics.joinToString())
    }

    @Test
    fun schedulerBlocksTasksWhoseExpansionDependenciesFormACycle() {
        val sourceText = """
            comptime cpx<decl> a(type T) { return { struct a_{T}_t { T value; }; }; }
            comptime cpx<decl> b(type T) { return { struct b_{T}_t { T value; }; }; }
            a(int);
            b(int);
        """.trimIndent()
        val source = SourceFile(SourceFileId(13), Path.of("scheduler.cp"), sourceText, 1)
        val parsed = Parser(Lexer().lex(source)).parse()
        val definitions = parsed.syntax.declarations.filterIsInstance<SyntaxComptimeFunction>().associateBy { it.name }
        val invocations = parsed.syntax.declarations.filterIsInstance<SyntaxCpxInvocation>().associateBy { it.name }
        val keyA = ExpansionKey("a", listOf("int"))
        val keyB = ExpansionKey("b", listOf("int"))
        val scheduler = ComptimeScheduler()
        scheduler.enqueue(
            ExpansionTask(
                invocations.getValue("a"),
                definitions.getValue("a"),
                keyA,
                dependencies = setOf(ComptimeDependency.Expansion(keyB))
            )
        )
        scheduler.enqueue(
            ExpansionTask(
                invocations.getValue("b"),
                definitions.getValue("b"),
                keyB,
                dependencies = setOf(ComptimeDependency.Expansion(keyA))
            )
        )

        assertNull(scheduler.next())
        assertEquals(ComptimeTaskState.PENDING, scheduler.state(keyA))
        assertEquals(ComptimeTaskState.PENDING, scheduler.state(keyB))
        assertEquals(
            listOf(listOf(keyA, keyB, keyA)),
            scheduler.dependencyCycles()
        )
    }

    @Test
    fun schedulerUsesExplicitReadinessChannelsForSemanticDependencies() {
        val source = SourceFile(SourceFileId(14), Path.of("channels.cp"), "", 1)
        val range = SourceRange(source.id, 0, 0)
        val invocation = SyntaxCpxInvocation("ready", emptyList(), range, Origin.Direct(range))
        val definition = SyntaxComptimeFunction(
            "ready",
            "decl",
            listOf(),
            "struct ready_value { int value; };",
            range,
            Origin.Direct(range)
        )
        val key = ExpansionKey("ready", emptyList())
        val symbol = ComptimeDependency.Symbol("Input")
        val type = ComptimeDependency.Type("InputType")
        val module = ComptimeDependency.Module("input")
        val stableUniverse = ComptimeDependency.StableTypeUniverse
        val scheduler = ComptimeScheduler()
        scheduler.enqueue(
            ExpansionTask(
                invocation,
                definition,
                key,
                dependencies = setOf(symbol, type, module, stableUniverse)
            )
        )

        assertNull(scheduler.next())
        assertEquals(setOf(symbol, type, module, stableUniverse), scheduler.waitingDependencies(key))
        scheduler.publishAll(listOf(symbol, type, module, stableUniverse))

        assertEquals(key, scheduler.next()?.key)
        assertTrue(scheduler.isPublished(symbol))
    }
}
