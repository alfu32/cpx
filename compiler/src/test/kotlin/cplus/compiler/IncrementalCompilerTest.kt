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
}
