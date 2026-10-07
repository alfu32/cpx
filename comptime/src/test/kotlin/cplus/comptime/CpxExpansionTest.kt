package cplus.comptime

import cplus.core.*
import cplus.semantic.TypeId
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CpxExpansionTest {
    @Test
    fun expandsTypedScalarExpressionAndEntityArguments() {
        val sourceText = """
            comptime cpx<decl> build(type T, expr E, int N, float F, bool B, string S, identifier I) {
                return {
                    struct box_{I}_{N}_t { T value; };
                    double number_{I} = F;
                    int flag_{I} = B;
                    char* text_{I} = S;
                    int sum_{I}() { return E + N; }
                };
            }
            build(int, 1 + 2, 03, 1.50e1, true, "ok", item);
        """.trimIndent()
        val source = SourceFile(SourceFileId(30), Path.of("typed-values.cp"), sourceText, 1)
        val parsed = Parser(Lexer().lex(source)).parse()
        val result = CpxExpander().expand(source, parsed.syntax)

        assertTrue(parsed.diagnostics.isEmpty(), parsed.diagnostics.joinToString())
        assertTrue(result.diagnostics.isEmpty(), result.diagnostics.joinToString())
        assertEquals(
            setOf(
                ExpansionKey(
                    "build",
                    listOf("int", "1 + 2", "3", "15", "true", "\"ok\"", "item"),
                    listOf("type", "expr", "int", "float", "bool", "string", "identifier")
                )
            ),
            result.expandedKeys
        )
        assertEquals(listOf("box_item_3_t"), result.program.declarations.filterIsInstance<SyntaxStruct>().map { it.name })
        assertEquals(listOf("number_item", "flag_item", "text_item"), result.program.declarations.filterIsInstance<SyntaxGlobalVariable>().map { it.name })
        assertEquals(listOf("sum_item"), result.program.declarations.filterIsInstance<SyntaxFunction>().map { it.name })
        assertEquals("int", result.program.declarations.filterIsInstance<SyntaxStruct>().single().fields.single().type.name)
    }

    @Test
    fun syntaxArgumentsRetainAstNodesIdsOriginsAndReferenceHook() {
        val sourceText = """
            comptime cpx<decl> capture(expr E, stmt S, decl D, member M, unit U, cpx C) {
                return { int generated() { return E; } };
            }
            capture(1 + 2, return 3;, int helper() { return 1; }, int field;, int unit_fn() { return 1; }, nested(int););
        """.trimIndent()
        val source = SourceFile(SourceFileId(33), Path.of("syntax-values.cp"), sourceText, 1)
        val parsed = Parser(Lexer().lex(source)).parse()
        val seen = mutableListOf<Pair<AstNode, NodeId>>()
        val result = CpxExpander().expand(
            source,
            parsed.syntax,
            referenceResolver = ComptimeReferenceResolver { node, arena ->
                seen += node to arena.add(node)
                emptyMap()
            }
        )

        assertTrue(parsed.diagnostics.isEmpty(), parsed.diagnostics.joinToString())
        assertTrue(result.diagnostics.isEmpty(), result.diagnostics.joinToString())
        assertEquals(6, seen.size)
        assertEquals((1..6).map(::NodeId), seen.map { it.second })
        assertEquals(7, result.syntaxArena.size)
        assertTrue(seen.all { it.first.origin is Origin.Generated })
        assertEquals(listOf("generated"), result.program.declarations.filterIsInstance<SyntaxFunction>().map { it.name })
    }

    @Test
    fun canonicalSyntaxEncodingSeparatesValuesAndIgnoresFormatting() {
        val sourceText = """
            comptime cpx<decl> make(expr E) {
                return { int generated() { return E; } };
            }
            make(1+2);
            make(1 + 2);
            make(2+1);
        """.trimIndent()
        val source = SourceFile(SourceFileId(34), Path.of("canonical-values.cp"), sourceText, 1)
        val parsed = Parser(Lexer().lex(source)).parse()
        val result = CpxExpander().expand(source, parsed.syntax)

        assertTrue(result.diagnostics.isEmpty(), result.diagnostics.joinToString())
        assertEquals(
            setOf(
                SpecializationKey("make", listOf(CanonicalComptimeValue("expr", "1 + 2"))),
                SpecializationKey("make", listOf(CanonicalComptimeValue("expr", "2 + 1")))
            ),
            result.specializationKeys
        )
    }

    @Test
    fun listArgumentsRetainTypedElementsAndCanonicalEncoding() {
        val sourceText = """
            comptime cpx<decl> collect(list Values) {
                return { int generated() { return 0; } };
            }
            collect([int, 02, 1.50e1, true, "ok", item, 1 + 2]);
        """.trimIndent()
        val source = SourceFile(SourceFileId(35), Path.of("list-values.cp"), sourceText, 1)
        val parsed = Parser(Lexer().lex(source)).parse()
        val result = CpxExpander().expand(source, parsed.syntax)

        assertTrue(parsed.diagnostics.isEmpty(), parsed.diagnostics.joinToString())
        assertTrue(result.diagnostics.isEmpty(), result.diagnostics.joinToString())
        val list = result.argumentValues.values.flatten().single() as ComptimeValue.CtList
        assertEquals(
            listOf("type", "int", "float", "bool", "string", "identifier", "expr"),
            list.values.map(ComptimeValue::canonicalKind)
        )
        assertEquals(
            "[type:int,int:2,float:15,bool:true,string:\"ok\",identifier:item,expr:1 + 2]",
            list.canonicalText
        )
        assertEquals(
            setOf(SpecializationKey("collect", listOf(CanonicalComptimeValue("list", list.canonicalText)))),
            result.specializationKeys
        )
    }

    @Test
    fun generatedFunctionLocalsAreHygienicallyRenamedWithTheirReferences() {
        val sourceText = """
            comptime cpx<decl> make() {
                return {
                    int generated() {
                        int tmp = 1;
                        return tmp;
                    }
                };
            }
            make();
        """.trimIndent()
        val source = SourceFile(SourceFileId(36), Path.of("hygiene.cp"), sourceText, 1)
        val parsed = Parser(Lexer().lex(source)).parse()
        val result = CpxExpander().expand(source, parsed.syntax)

        assertTrue(result.diagnostics.isEmpty(), result.diagnostics.joinToString())
        val function = result.program.declarations.filterIsInstance<SyntaxFunction>().single()
        val body = function.body as SyntaxBlock
        val local = body.statements.filterIsInstance<SyntaxVariableDeclaration>().single()
        val returned = (body.statements.filterIsInstance<SyntaxReturn>().single().expression as SyntaxIdentifier).name
        assertTrue(local.name.startsWith("tmp__cpx_"), local.name)
        assertEquals(local.name, returned)
    }

    @Test
    fun evaluatorReceivesStructuredContextAndReturnsExpansionChannels() {
        val sourceText = """
            comptime cpx<decl> make() {
                return { int generated() { return 0; } };
            }
            make();
        """.trimIndent()
        val source = SourceFile(SourceFileId(37), Path.of("evaluator.cp"), sourceText, 1)
        val parsed = Parser(Lexer().lex(source)).parse()
        val contexts = mutableListOf<ComptimeContext>()
        val result = CpxExpander(
            evaluator = ComptimeEvaluator { functionName, template, bindings, context ->
                assertEquals("make", functionName)
                contexts += context
                ComptimeEvaluationResult(
                    template.render(bindings),
                    ComptimeExpansionChannels(dependencies = setOf(ComptimeDependency.Module("runtime")))
                )
            }
        ).expand(source, parsed.syntax)

        assertTrue(result.diagnostics.isEmpty(), result.diagnostics.joinToString())
        val context = contexts.single()
        assertEquals("evaluator.cp", context.module)
        assertEquals(CpxPhase.STRUCTURAL, context.phase)
        assertEquals("c17", context.target.cDialect)
        assertEquals(ExpansionKey("make", emptyList()), context.expansion!!.key)
        assertEquals("runtime", (result.evaluationResults.values.single().channels.dependencies.single() as ComptimeDependency.Module).name)
    }

    @Test
    fun rejectsArgumentsThatDoNotMatchTheirCompileTimeKind() {
        val sourceText = """
            comptime cpx<decl> build(int N) {
                return { struct value_{N}_t { int value; }; };
            }
            build(not_an_integer);
        """.trimIndent()
        val source = SourceFile(SourceFileId(31), Path.of("invalid-value.cp"), sourceText, 1)
        val parsed = Parser(Lexer().lex(source)).parse()
        val result = CpxExpander().expand(source, parsed.syntax)

        assertTrue(parsed.diagnostics.isEmpty(), parsed.diagnostics.joinToString())
        assertTrue(result.diagnostics.any { it.code == "CPX010" }, result.diagnostics.joinToString())
        assertTrue(result.program.declarations.none { it is SyntaxStruct })
    }

    @Test
    fun typeArgumentsRetainDeclaredIdentityAndRenderCanonicalSyntax() {
        val sourceText = """
            comptime cpx<decl> make(type T) {
                return { int generated_{T}() { return 1; } };
            }
            make(alias_t);
        """.trimIndent()
        val source = SourceFile(SourceFileId(32), Path.of("type-identity.cp"), sourceText, 1)
        val parsed = Parser(Lexer().lex(source)).parse()
        val result = CpxExpander().expand(
            source,
            parsed.syntax,
            ComptimeTypeResolver { ComptimeTypeIdentity(TypeId(4), TypeId(1), "int") }
        )

        assertTrue(result.diagnostics.isEmpty(), result.diagnostics.joinToString())
        assertEquals(
            setOf(ExpansionKey("make", listOf("alias_t"), argumentIdentities = listOf("id:4"))),
            result.expandedKeys
        )
        assertEquals(listOf("generated_int"), result.program.declarations.filterIsInstance<SyntaxFunction>().map { it.name })
    }

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
    fun directInterpolationSkipsStringLiteralsAndComments() {
        val template = CpxTemplateParser().parse(
            "// T\nconst char* text = \"T\"; int generated_{T}() { return 0; }",
            CpxCategory.DECLARATION,
            Origin.Direct(SourceRange(SourceFileId(120), 0, 1)),
            setOf("T")
        )

        assertEquals(
            "// T\nconst char* text = \"T\"; int generated_item() { return 0; }",
            template.render(mapOf("T" to ComptimeValue.CtIdentifier("item")))
        )
        assertEquals(1, template.nodes.count { it is TemplateNode.Binding })
    }

    @Test
    fun nestedSyntaxValuePreservesGeneratedOriginOnEveryAstNode() {
        val sourceText = """
            comptime cpx<decl> capture(expr E) {
                return { int generated() { return E; } };
            }
            capture(value + (other * 2));
        """.trimIndent()
        val source = SourceFile(SourceFileId(121), Path.of("nested-origin-value.cp"), sourceText, 1)
        val parsed = Parser(Lexer().lex(source)).parse()
        val result = CpxExpander().expand(source, parsed.syntax)
        val value = result.argumentValues.values.single().single() as ComptimeValue.CtExpression
        val root = result.syntaxArena[value.nodeId]

        fun assertGenerated(node: AstNode) {
            assertTrue(node.origin is Origin.Generated, "unexpected origin: ${node.origin}")
            when (node) {
                is AstBinary -> {
                    assertGenerated(node.left)
                    assertGenerated(node.right)
                }
                is AstParenthesized -> assertGenerated(node.expression)
                is AstIdentifier,
                is AstIntegerLiteral -> Unit
                else -> error("unexpected captured expression: $node")
            }
        }

        assertGenerated(root)
    }

    @Test
    fun rejectsInvalidTypedArgumentsBeforeTemplateInstantiation() {
        val sourceText = """
            comptime cpx<decl> make(type T) {
                return { struct generated_{T}_t { int value; }; };
            }
            make(1 + 2);
        """.trimIndent()
        val source = SourceFile(SourceFileId(122), Path.of("invalid-type-value.cp"), sourceText, 1)
        val parsed = Parser(Lexer().lex(source)).parse()
        val result = CpxExpander().expand(source, parsed.syntax)

        assertTrue(result.diagnostics.any { it.code == "CPX010" }, result.diagnostics.joinToString())
        assertTrue(result.program.declarations.none { it is SyntaxStruct })
    }

    @Test
    fun rejectsSyntaxValuesInIdentifierComposition() {
        val sourceText = """
            comptime cpx<decl> make(string S) {
                return { int generated_{S}() { return 0; } };
            }
            make("not_an_identifier");
        """.trimIndent()
        val source = SourceFile(SourceFileId(123), Path.of("invalid-interpolation.cp"), sourceText, 1)
        val parsed = Parser(Lexer().lex(source)).parse()
        val result = CpxExpander().expand(source, parsed.syntax)

        assertTrue(result.diagnostics.any { it.code == "CPX011" }, result.diagnostics.joinToString())
        assertTrue(result.program.declarations.none { it is SyntaxFunction && it.name.startsWith("generated_") })
    }

    @Test
    fun typeAndMemberCpxCategoriesAreStructural() {
        val sourceText = """
            comptime cpx<type> makeType(type T) {
                return { struct generated_{T}_t { T value; }; };
            }
            comptime cpx<member> makeMember(type T) {
                return { int generated_{T}(); };
            }
            makeType(int);
            makeMember(int);
        """.trimIndent()
        val source = SourceFile(SourceFileId(124), Path.of("structural-categories.cp"), sourceText, 1)
        val parsed = Parser(Lexer().lex(source)).parse()
        val result = CpxExpander().expand(source, parsed.syntax)

        assertTrue(result.diagnostics.none { it.code == "CPX003" }, result.diagnostics.joinToString())
        assertTrue(result.program.declarations.any { it is SyntaxStruct && it.name == "generated_int_t" })
        assertTrue(result.program.declarations.any { it is SyntaxFunction && it.name == "generated_int" })
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
    fun nestedExpansionRetainsCompleteOriginAncestry() {
        val sourceText = """
            comptime cpx<decl> outer(type T) { return { inner(T); }; }
            comptime cpx<decl> inner(type T) { return { struct nested_{T}_t { T value; }; }; }
            outer(int);
        """.trimIndent()
        val source = SourceFile(SourceFileId(19), Path.of("nested-origin.cp"), sourceText, 1)
        val parsed = Parser(Lexer().lex(source)).parse()
        val result = CpxExpander().expand(source, parsed.syntax)

        assertTrue(result.diagnostics.isEmpty(), result.diagnostics.joinToString())
        val generated = result.program.declarations.filterIsInstance<SyntaxStruct>().single()
        val innerOrigin = generated.origin as Origin.Expansion
        val outerOrigin = innerOrigin.parent as Origin.Expansion
        assertEquals("inner(int)", innerOrigin.key)
        assertEquals("outer(int)", outerOrigin.key)
        assertEquals(source.id, outerOrigin.invocation.primaryRange?.file)
        val expansionIds = result.expansionIds
        assertEquals(2, expansionIds.size)
        val outerId = expansionIds.first { it.declaration == "outer" }
        val innerId = expansionIds.first { it.declaration == "inner" }
        assertEquals(outerId, innerId.parent)
        assertTrue(outerId.callSite != innerId.callSite)
    }

    @Test
    fun generatedComptimeDeclarationsJoinTheStructuralFixedPoint() {
        val sourceText = """
            comptime cpx<decl> factory(type T) {
                return {
                    comptime cpx<decl> inner(type U) {
                        return { struct generated_{U}_t { U value; }; };
                    }
                    inner(T);
                };
            }
            factory(int);
        """.trimIndent()
        val source = SourceFile(SourceFileId(20), Path.of("fixed-point.cp"), sourceText, 1)
        val parsed = Parser(Lexer().lex(source)).parse()
        val result = CpxExpander().expand(source, parsed.syntax)

        assertTrue(result.diagnostics.isEmpty(), result.diagnostics.joinToString())
        assertEquals(
            listOf("generated_int_t"),
            result.program.declarations.filterIsInstance<SyntaxStruct>().map { it.name }
        )
        assertEquals(
            setOf(
                ExpansionKey("factory", listOf("int")),
                ExpansionKey("inner", listOf("int"))
            ),
            result.expandedKeys
        )
        assertTrue(result.structuralFingerprint.contains("struct:generated_int_t"))
    }

    @Test
    fun deferredInvocationResolvesWhenAStructuralWaveIntroducesItsDefinition() {
        val sourceText = """
            inner(int);
            comptime cpx<decl> factory(type T) {
                return {
                    comptime cpx<decl> inner(type U) {
                        return { struct deferred_{U}_t { U value; }; };
                    }
                };
            }
            factory(int);
        """.trimIndent()
        val source = SourceFile(SourceFileId(26), Path.of("deferred.cp"), sourceText, 1)
        val parsed = Parser(Lexer().lex(source)).parse()
        val result = CpxExpander().expand(source, parsed.syntax)

        assertTrue(result.diagnostics.isEmpty(), result.diagnostics.joinToString())
        assertEquals(listOf("deferred_int_t"), result.program.declarations.filterIsInstance<SyntaxStruct>().map { it.name })
        assertEquals(
            setOf(
                ExpansionKey("factory", listOf("int")),
                ExpansionKey("inner", listOf("int"))
            ),
            result.expandedKeys
        )
    }

    @Test
    fun structuralFingerprintIgnoresSourceFormattingAndOrigins() {
        fun fingerprint(sourceId: Int, text: String): String {
            val source = SourceFile(SourceFileId(sourceId), Path.of("fingerprint$sourceId.cp"), text, 1)
            val parsed = Parser(Lexer().lex(source)).parse()
            return CpxExpander().expand(source, parsed.syntax).structuralFingerprint
        }

        assertEquals(
            fingerprint(21, "struct item { int value; };"),
            fingerprint(22, "  struct item{int value;} ;  ")
        )
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

    @Test
    fun reflectiveTasksWaitForTheStructuralTypeUniverseBarrier() {
        val source = SourceFile(SourceFileId(23), Path.of("phase.cp"), "", 1)
        val range = SourceRange(source.id, 0, 0)
        val invocation = SyntaxCpxInvocation("reflect", emptyList(), range, Origin.Direct(range))
        val definition = SyntaxComptimeFunction(
            "reflect",
            "decl",
            emptyList(),
            "struct reflected_value { int value; };",
            range,
            Origin.Direct(range)
        )
        val key = ExpansionKey("reflect", emptyList())
        val scheduler = ComptimeScheduler()
        scheduler.enqueue(
            ExpansionTask(
                invocation,
                definition,
                key,
                phase = CpxPhase.REFLECTIVE
            )
        )

        assertNull(scheduler.next())
        assertTrue(scheduler.closeStructuralPhase())
        assertTrue(scheduler.isStructuralPhaseClosed)
        assertEquals(key, scheduler.next()?.key)
    }

    @Test
    fun reflectiveCpxRunsAfterStructuralExpansionAndGeneratesExecutableCode() {
        val sourceText = """
            comptime cpx<decl> makeType(type T) {
                return { struct generated_{T}_t { T value; }; };
            }
            comptime cpx<stmt> makeFunction(type T) {
                return { int generated_{T}_value() { return 7; } };
            }
            makeFunction(int);
            makeType(int);
        """.trimIndent()
        val source = SourceFile(SourceFileId(24), Path.of("reflective.cp"), sourceText, 1)
        val parsed = Parser(Lexer().lex(source)).parse()
        val result = CpxExpander().expand(source, parsed.syntax)

        assertTrue(result.diagnostics.isEmpty(), result.diagnostics.joinToString())
        assertEquals(listOf("generated_int_t"), result.program.declarations.filterIsInstance<SyntaxStruct>().map { it.name })
        assertEquals(
            listOf("generated_int_value"),
            result.program.declarations.filterIsInstance<SyntaxFunction>().map { it.name }
        )
        assertEquals(
            setOf(
                ExpansionKey("makeFunction", listOf("int")),
                ExpansionKey("makeType", listOf("int"))
            ),
            result.expandedKeys
        )
    }

    @Test
    fun reflectiveCpxRejectsStructuralDeclarationsButKeepsExecutableDeclarations() {
        val sourceText = """
            comptime cpx<stmt> invalid(type T) {
                return {
                    struct forbidden_{T}_t { T value; };
                    int generated_value() { return 1; }
                };
            }
            invalid(int);
        """.trimIndent()
        val source = SourceFile(SourceFileId(25), Path.of("reflective-invalid.cp"), sourceText, 1)
        val parsed = Parser(Lexer().lex(source)).parse()
        val result = CpxExpander().expand(source, parsed.syntax)

        assertTrue(result.diagnostics.any { it.code == "CPX008" }, result.diagnostics.joinToString())
        assertTrue(result.program.declarations.none { it is SyntaxStruct && it.name == "forbidden_int_t" })
        assertTrue(result.program.declarations.any { it is SyntaxFunction && it.name == "generated_value" })
    }

    @Test
    fun typeUniverseRejectsMutationAndFullIntrospectionAfterFreeze() {
        val scheduler = ComptimeScheduler()
        val descriptor = StructuralTypeDescriptor(
            "BeforeFreeze",
            "struct",
            fields = listOf(StructuralFieldDescriptor("value", "int")),
            methods = listOf(StructuralMethodDescriptor("read", "int", emptyList())),
            layout = StructuralLayout("struct", listOf("value"), isSized = true)
        )
        assertTrue(scheduler.typeUniverse.register(descriptor))
        assertEquals(
            setOf("BeforeFreeze"),
            scheduler.typeUniverse.snapshot(TypeUniverseAccess.EARLY_SAFE).names
        )
        assertFailsWith<IllegalStateException> {
            scheduler.typeUniverse.snapshot(TypeUniverseAccess.FULL)
        }

        assertTrue(scheduler.closeStructuralPhase())
        assertTrue(scheduler.typeUniverse.isFrozen)
        assertTrue(!scheduler.typeUniverse.register("AfterFreeze"))
        assertEquals(
            setOf("BeforeFreeze"),
            scheduler.typeUniverse.snapshot(TypeUniverseAccess.FULL).names
        )
        assertEquals(descriptor, scheduler.typeUniverse.snapshot(TypeUniverseAccess.FULL).typeNamed("BeforeFreeze"))
        assertEquals("int", descriptor.fields.single().typeReference.name)
        assertEquals(listOf("value"), descriptor.layout?.fieldOrder)
    }
}
