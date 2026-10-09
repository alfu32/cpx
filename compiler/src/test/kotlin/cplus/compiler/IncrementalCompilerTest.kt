package cplus.compiler

import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class IncrementalCompilerTest {
    @Test
    fun changedModuleInvalidatesDependentsButReusesUnrelatedFrontends() {
        val directory = Files.createTempDirectory("cplus-incremental-dependencies")
        val helper = directory.resolve("helpers.cp")
        val main = directory.resolve("main.cp")
        val unrelated = directory.resolve("unrelated.cp")
        helper.writeText(
            """
                package demo.core;
                pub int add(int left, int right) {
                    return left + right;
                }
            """.trimIndent()
        )
        main.writeText(
            """
                package demo.core;
                import { add } from helpers;
                int main() {
                    return add(7, 5);
                }
            """.trimIndent()
        )
        unrelated.writeText(
            """
                package demo.core;
                int unrelated_value() {
                    return 4;
                }
            """.trimIndent()
        )

        val request = CompileRequest(listOf(main, helper, unrelated))
        val incremental = IncrementalCompiler()
        val first = incremental.compile(request)
        assertTrue(first.isSuccessful, first.result.diagnostics.joinToString())

        helper.writeText(
            """
                package demo.core;
                pub int add(int left, int right) {
                    return left - right;
                }
            """.trimIndent()
        )
        val second = incremental.compile(request)
        val normalizedHelper = helper.toAbsolutePath().normalize()
        val normalizedMain = main.toAbsolutePath().normalize()
        val normalizedUnrelated = unrelated.toAbsolutePath().normalize()

        assertTrue(second.isSuccessful, second.result.diagnostics.joinToString())
        assertEquals(setOf(normalizedHelper), second.invalidation.changedSources)
        assertEquals(setOf(normalizedHelper, normalizedMain), second.invalidation.invalidatedSources)
        assertEquals(setOf(normalizedUnrelated), second.invalidation.reusedSources)
    }

    @Test
    fun changedModuleInvalidatesDependentCpxExpansionKeys() {
        val directory = Files.createTempDirectory("cplus-incremental-cpx")
        val helper = directory.resolve("helpers.cp")
        val main = directory.resolve("main.cp")
        helper.writeText(
            """
                package demo.core;
                pub int add(int left, int right) {
                    return left + right;
                }
            """.trimIndent()
        )
        main.writeText(
            """
                package demo.core;
                import { add } from helpers;
                comptime cpx<decl> box(type T) {
                    return { struct box_{T}_t { T value; }; };
                }
                box(int);
                int main() {
                    box_int_t value;
                    value.value = add(7, 5);
                    return value.value;
                }
            """.trimIndent()
        )

        val request = CompileRequest(listOf(main, helper))
        val incremental = IncrementalCompiler()
        val first = incremental.compile(request)
        assertTrue(first.isSuccessful, first.result.diagnostics.joinToString())
        val expansionKey = first.result.artifacts
            .flatMap { it.expanded?.expandedKeys.orEmpty() }
            .single()

        helper.writeText(
            """
                package demo.core;
                pub int add(int left, int right) {
                    return left * right;
                }
            """.trimIndent()
        )
        val second = incremental.compile(request)

        assertTrue(second.isSuccessful, second.result.diagnostics.joinToString())
        assertTrue(expansionKey in second.invalidation.invalidatedExpansionKeys)
        assertTrue(expansionKey.specializationKey in second.invalidation.invalidatedSpecializationKeys)
    }

    @Test
    fun importedComptimeProviderEditsInvalidateClientAndNeverServeStaleOutput() {
        val directory = Files.createTempDirectory("cplus-incremental-imported-cpx")
        val provider = directory.resolve("box.cp")
        val main = directory.resolve("main.cp")
        provider.writeText(
            """
                pub comptime cpx<decl> box(type T) {
                    return { struct box_{T}_t { int value; }; };
                }
            """.trimIndent()
        )
        main.writeText(
            """
                import { box } from "./box.cp";
                box(int);
                int main() {
                    struct box_int_t value;
                    value.value = 42;
                    return value.value;
                }
            """.trimIndent()
        )
        val request = CompileRequest(listOf(main))
        val incremental = IncrementalCompiler()
        val first = incremental.compile(request)
        assertTrue(first.isSuccessful, first.result.diagnostics.joinToString())

        provider.writeText(
            """
                pub comptime cpx<decl> box(type T) {
                    return { struct box_{T}_t { long value; }; };
                }
            """.trimIndent()
        )
        val updated = incremental.compile(request)
        val cold = CPlusCompiler().compile(request)
        val normalizedProvider = provider.toAbsolutePath().normalize()
        val normalizedMain = main.toAbsolutePath().normalize()

        assertTrue(updated.isSuccessful, updated.result.diagnostics.joinToString())
        assertTrue(cold.isSuccessful, cold.diagnostics.joinToString())
        assertTrue(normalizedProvider in updated.invalidation.changedSources)
        assertTrue(normalizedMain in updated.invalidation.invalidatedSources)
        assertEquals(cold.generatedUnits.map { it.text }, updated.result.generatedUnits.map { it.text })
        assertTrue(first.result.generatedUnits.single().text != updated.result.generatedUnits.single().text)

        provider.writeText(
            """
                comptime cpx<decl> box(type T) {
                    return { struct box_{T}_t { long value; }; };
                }
            """.trimIndent()
        )
        val unpublished = incremental.compile(request)
        assertTrue(!unpublished.isSuccessful)
        assertTrue(unpublished.result.diagnostics.any { it.code == "SEM406" }, unpublished.result.diagnostics.joinToString())

        Files.delete(provider)
        val deleted = incremental.compile(request)
        assertTrue(!deleted.isSuccessful)
        assertTrue(deleted.result.diagnostics.any { it.code == "SEM406" }, deleted.result.diagnostics.joinToString())
        assertTrue(deleted.result.semanticModel?.structs?.get("box_int_t") == null)
    }

    @Test
    fun importedProviderEditsRefreshGeneratedFixtureExpansionAndSourceOrigins() {
        val directory = Files.createTempDirectory("cplus-incremental-imported-test-fixture")
        val provider = directory.resolve("box.cp")
        val main = directory.resolve("main.cp")
        fun writeProvider(expected: Int, fieldType: String) {
            provider.writeText(
                """
                    pub comptime cpx<decl> box(type T) {
                        return {
                            struct box_{T}_t { $fieldType value; };
                            test generated box fixture {
                                struct box_int_t item;
                                item.value = 42;
                                assertEquals($expected, item.value);
                            }
                        };
                    }
                """.trimIndent()
            )
        }
        writeProvider(42, "int")
        main.writeText(
            """
                import { box } from "./box.cp";
                box(int);
                int main() { return 0; }
                test client fixture {
                    struct box_int_t item;
                    item.value = 7;
                    assertEquals(7, item.value);
                }
            """.trimIndent()
        )
        val request = CompileRequest(listOf(main), mode = CompilationMode.TEST)
        val incremental = IncrementalCompiler()
        val first = incremental.compile(request)
        assertTrue(first.isSuccessful, first.result.diagnostics.joinToString())
        val initialGenerated = first.result.generatedUnits.single().text
        val initialFixture = first.result.semanticModel!!.testFixtures
            .single { it.fixture.description == "generated box fixture" }
        assertTrue(initialFixture.fixture.origin is cplus.core.Origin.Expansion)
        val initialAssertion = initialFixture.fixture.body.statements
            .filterIsInstance<cplus.core.AstAssertion>().single().origin
        assertTrue(first.result.generatedUnits.single().sourceMap.any { it.origin == initialAssertion })

        writeProvider(99, "long")
        val updated = incremental.compile(request)
        val cold = CPlusCompiler().compile(request)
        assertTrue(updated.isSuccessful, updated.result.diagnostics.joinToString())
        assertTrue(cold.isSuccessful, cold.diagnostics.joinToString())
        assertTrue(updated.result.generatedUnits.single().text != initialGenerated)
        assertEquals(cold.generatedUnits.single().text, updated.result.generatedUnits.single().text)
        assertTrue(updated.result.generatedUnits.single().text.contains("99"), updated.result.generatedUnits.single().text)
        assertEquals("long", updated.result.semanticModel!!.structs.getValue("box_int_t").fields.single().symbol.type.name)
        assertTrue(provider.toAbsolutePath().normalize() in updated.invalidation.changedSources)
        assertTrue(main.toAbsolutePath().normalize() in updated.invalidation.invalidatedSources)
        val refreshedFixture = updated.result.semanticModel!!.testFixtures
            .single { it.fixture.description == "generated box fixture" }
        assertTrue(refreshedFixture.fixture.origin is cplus.core.Origin.Expansion)
        assertTrue(updated.result.generatedUnits.single().sourceMap.any {
            it.origin == refreshedFixture.fixture.body.statements
                .filterIsInstance<cplus.core.AstAssertion>().single().origin
        })
    }

    @Test
    fun privateImportedComptimeHelperEditsInvalidateClientExpansion() {
        val directory = Files.createTempDirectory("cplus-incremental-imported-helper")
        val provider = directory.resolve("box.cp")
        val main = directory.resolve("main.cp")
        fun writeProvider(fieldType: String) {
            provider.writeText(
                """
                    comptime cpx<decl> helper(type T) {
                        return { struct helper_{T}_t { $fieldType value; }; };
                    }
                    pub comptime cpx<decl> box(type T) {
                        return { helper(T); struct box_{T}_t { int marker; }; };
                    }
                """.trimIndent()
            )
        }
        writeProvider("int")
        main.writeText(
            """
                import { box } from "./box.cp";
                box(int);
                int main() {
                    struct helper_int_t value;
                    value.value = 42;
                    return value.value;
                }
            """.trimIndent()
        )
        val request = CompileRequest(listOf(main))
        val incremental = IncrementalCompiler()
        val first = incremental.compile(request)
        assertTrue(first.isSuccessful, first.result.diagnostics.joinToString())

        writeProvider("long")
        val second = incremental.compile(request)

        assertTrue(second.isSuccessful, second.result.diagnostics.joinToString())
        assertTrue(second.invalidation.invalidatedExpansionKeys.isNotEmpty())
        assertEquals("long", second.result.semanticModel?.structs?.get("helper_int_t")?.fields?.single()?.symbol?.type?.name)
    }

    @Test
    fun retargetedGeneratedImportDropsOldProviderFromWarmWorkspace() {
        val directory = Files.createTempDirectory("cplus-incremental-retarget-cpx")
        val providerA = directory.resolve("provider_a.cp").also {
            it.writeText("pub comptime cpx<decl> box(type T) { return { struct box_a_{T}_t { int value; }; }; }")
        }
        val providerB = directory.resolve("provider_b.cp").also {
            it.writeText("pub comptime cpx<decl> box(type T) { return { struct box_b_{T}_t { int value; }; }; }")
        }
        val main = directory.resolve("main.cp")
        fun writeMain(providerName: String, importedName: String, invokedName: String, generatedType: String) {
            main.writeText(
                """
                    import { $importedName } from "./$providerName";
                    $invokedName(int);
                    int main() {
                        struct $generatedType value;
                        return value.value;
                    }
                """.trimIndent()
            )
        }
        writeMain("provider_a.cp", "box", "box", "box_a_int_t")
        val request = CompileRequest(listOf(main))
        val incremental = IncrementalCompiler()
        val first = incremental.compile(request)
        assertTrue(first.isSuccessful, first.result.diagnostics.joinToString())

        writeMain("provider_b.cp", "box as makeBox", "makeBox", "box_b_int_t")
        val retargeted = incremental.compile(request)
        val normalizedA = providerA.toAbsolutePath().normalize()
        val normalizedB = providerB.toAbsolutePath().normalize()

        assertTrue(retargeted.isSuccessful, retargeted.result.diagnostics.joinToString())
        assertEquals("main", retargeted.result.semanticModel?.structs?.get("box_b_int_t")?.moduleName)
        assertTrue(retargeted.result.semanticModel?.structs?.get("box_a_int_t") == null)
        assertTrue(normalizedB in retargeted.cacheKey!!.sourceFingerprints.keys)
        assertTrue(normalizedA !in retargeted.cacheKey.sourceFingerprints.keys)
        assertTrue(normalizedA !in retargeted.invalidation.reusedSources)
    }

    @Test
    fun unchangedWorkspaceReturnsCachedCompilationResult() {
        val source = Files.createTempFile("cplus-incremental-stable", ".cp").also {
            it.writeText("int main() { return 0; }")
        }
        val incremental = IncrementalCompiler()
        val request = CompileRequest(listOf(source))

        val first = incremental.compile(request)
        val second = incremental.compile(request)

        assertSame(first.result, second.result)
        assertEquals(emptySet(), second.invalidation.changedSources)
        assertEquals(setOf(source.toAbsolutePath().normalize()), second.invalidation.reusedSources)
    }

    @Test
    fun parallelFrontEndPreparationPreservesDeterministicOutput() {
        val directory = Files.createTempDirectory("cplus-incremental-parallel")
        val main = directory.resolve("main.cp").also {
            it.writeText(
                """
                    int main() {
                        return 0;
                    }
                """.trimIndent()
            )
        }
        val first = directory.resolve("first.cp").also {
            it.writeText("int first_value() { return 3; }")
        }
        val second = directory.resolve("second.cp").also {
            it.writeText("int second_value() { return 4; }")
        }
        val sources = listOf(main, first, second)

        val sequential = IncrementalCompiler().compile(
            CompileRequest(sources, options = CompilerOptions(parallelism = 1))
        )
        val parallel = IncrementalCompiler().compile(
            CompileRequest(sources, options = CompilerOptions(parallelism = 3))
        )

        assertTrue(sequential.isSuccessful, sequential.result.diagnostics.joinToString())
        assertTrue(parallel.isSuccessful, parallel.result.diagnostics.joinToString())
        assertEquals(sequential.result.diagnostics, parallel.result.diagnostics)
        assertEquals(
            sequential.result.generatedUnits.map { it.text },
            parallel.result.generatedUnits.map { it.text }
        )
    }

    @Test
    fun compilerConfigurationParticipatesInIncrementalCacheIdentity() {
        val source = Files.createTempFile("cplus-incremental-cache-key", ".cp").also {
            it.writeText("int main() { return 0; }")
        }
        val incremental = IncrementalCompiler()
        val first = incremental.compile(
            CompileRequest(listOf(source), options = CompilerOptions(parallelism = 1))
        )
        val second = incremental.compile(
            CompileRequest(listOf(source), options = CompilerOptions(parallelism = 2))
        )

        assertTrue(first.isSuccessful, first.result.diagnostics.joinToString())
        assertTrue(second.isSuccessful, second.result.diagnostics.joinToString())
        assertEquals(
            setOf(source.toAbsolutePath().normalize()),
            first.cacheKey!!.sourceFingerprints.keys
        )
        assertTrue(second.invalidation.invalidatedSources.contains(source.toAbsolutePath().normalize()))
        assertTrue(second.invalidation.changedSources.isEmpty())
        assertTrue(first.result !== second.result)
    }
}
