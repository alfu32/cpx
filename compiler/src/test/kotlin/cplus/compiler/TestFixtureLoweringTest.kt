package cplus.compiler

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class TestFixtureLoweringTest {
    @Test
    fun normalModeErasesFixturesAndTestModeAddsStableDispatcher() {
        val directory = Files.createTempDirectory("cplus-test-lowering")
        val source = directory.resolve("main.cp")
        Files.writeString(
            source,
            """
                test first fixture { int local = 1; int userResult = main(); }
                test second fixture { int other = 2; }
                int main() { return 0; }
            """.trimIndent()
        )

        val compiler = CPlusCompiler()
        val normal = compiler.compile(CompileRequest(listOf(source)))
        assertTrue(normal.isSuccessful, normal.diagnostics.joinToString())
        val normalC = normal.generatedUnits.single().text
        assertFalse(normalC.contains("fixture_"))
        assertTrue(normalC.contains("int main("))
        assertFalse(normal.artifacts.single().header?.text.orEmpty().contains("__cplus_test_fixture_"))
        val fixtureRange = normal.semanticModel!!.testFixtures.first().fixture.origin.primaryRange
        assertTrue(normal.generatedUnits.single().sourceMap.none { it.origin.primaryRange == fixtureRange })

        val selected = normal.semanticModel!!.testFixtures.first().identity
        val test = compiler.compile(
            CompileRequest(listOf(source), mode = CompilationMode.TEST, selectedFixtureIdentities = setOf(selected))
        )
        assertTrue(test.isSuccessful, test.diagnostics.joinToString())
        val testC = test.generatedUnits.single().text
        assertTrue(testC.contains("int main("))
        assertEquals(1, Regex("(?m)^int main\\s*\\(\\) \\{").findAll(testC).count())
        assertTrue(testC.contains("__cplus_test_fixture_"))
        assertTrue(testC.contains("__cplus_user_main_"))
        assertTrue(Regex("__cplus_user_main_[0-9a-f]+\\(\\);").containsMatchIn(testC), testC)
        val metadata = assertNotNull(test.artifacts.single().lowered?.unit?.testProduct)
        assertEquals(1, metadata.fixtures.size)
        assertTrue(test.artifacts.single().lowered!!.unit.runtimeDependencies.isEmpty())
    }

    @Test
    fun importedFixturesAreValidatedButNotSelectedAsRootTests() {
        val directory = Files.createTempDirectory("cplus-imported-test-lowering")
        val root = directory.resolve("main.cp")
        val provider = directory.resolve("provider.cp")
        Files.writeString(provider, "pub int helper() { return 1; } test imported fixture { int value = 2; }")
        Files.writeString(
            root,
            "import { helper } from \"./provider.cp\"; test root fixture { int value = helper(); } int main() { return 0; }"
        )

        val result = CPlusCompiler().compile(CompileRequest(listOf(root), mode = CompilationMode.TEST))
        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val metadata = assertNotNull(result.artifacts.single().lowered?.unit?.testProduct)
        assertEquals(1, metadata.fixtures.size)
        assertTrue(metadata.fixtures.single().identity.contains("main.cp"))
    }

    @Test
    fun switchingWarmIncrementalCompilerModesMatchesColdTestBuild() {
        val directory = Files.createTempDirectory("cplus-test-cache-mode")
        val source = directory.resolve("main.cp")
        Files.writeString(source, "test fixture { int value = 1; } int main() { return 0; }")
        val incremental = IncrementalCompiler()
        assertTrue(incremental.compile(CompileRequest(listOf(source))).isSuccessful)
        val warm = incremental.compile(CompileRequest(listOf(source), mode = CompilationMode.TEST))
        val cold = CPlusCompiler().compile(CompileRequest(listOf(source), mode = CompilationMode.TEST))

        assertTrue(warm.isSuccessful, warm.result.diagnostics.joinToString())
        assertTrue(cold.isSuccessful, cold.diagnostics.joinToString())
        assertEquals(cold.generatedUnits.single().text, warm.result.generatedUnits.single().text)
        assertEquals(CompilationMode.TEST, warm.cacheKey?.mode)
    }

    @Test
    fun assertionsEvaluateOperandsOnceInExplicitOrderAndRetainTheirTypes() {
        val directory = Files.createTempDirectory("cplus-test-assertion-lowering")
        val source = directory.resolve("main.cp")
        Files.writeString(
            source,
            """
                int calls = 0;
                volatile int volatileValue = 1;
                int* pointerValue = (int*)0;
                unsigned int unsignedValue = 1;
                int nextValue() { calls += 1; return calls; }
                int main() { return 0; }
                test all assertion forms {
                    int index = 0;
                    while (index < 1) {
                        assert(nextValue());
                        index += 1;
                    }
                    assert(nextValue());
                    assert("described truth", nextValue());
                    assertEquals(nextValue(), nextValue());
                    assertEquals("described equality", nextValue(), nextValue());
                    assert(volatileValue);
                    assert(pointerValue);
                    assertEquals(pointerValue, 0);
                    assertEquals(-1, unsignedValue);
                }
            """.trimIndent()
        )

        val result = CPlusCompiler().compile(CompileRequest(listOf(source), mode = CompilationMode.TEST))
        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        assertTrue(result.artifacts.single().lowered!!.unit.runtimeDependencies.containsAll(
            setOf("__cplus_test_report_truth", "__cplus_test_report_equality")
        ))
        val generated = result.generatedUnits.single().text
        val operandEvaluations = Regex("= nextValue\\(\\);").findAll(generated).toList()
        assertEquals(7, operandEvaluations.size, generated)
        assertTrue(
            Regex("int (__cplus_test_[0-9a-f]+)_value_0 = nextValue\\(\\);\\s+int \\1_value_1 = nextValue\\(\\);")
                .containsMatchIn(generated),
            generated
        )
        assertTrue(generated.contains("while ("), generated)
        assertTrue(generated.contains("volatile int") && generated.contains("= volatileValue;"), generated)
        assertTrue(generated.contains("int*" ) || generated.contains("int *"), generated)
        assertTrue(generated.contains("__cplus_test_report_truth("), generated)
        assertTrue(generated.contains("__cplus_test_report_equality("), generated)
        assertTrue(generated.contains("unsigned int") && generated.contains("= unsignedValue;"), generated)
        assertTrue(Regex("== \\(int\\*\\)__cplus_test_[0-9a-f]+_value_1").containsMatchIn(generated), generated)
        val assertionRanges = result.semanticModel!!.typedTestAssertions.keys.map { it.origin.primaryRange }.toSet()
        assertTrue(assertionRanges.all { range -> result.generatedUnits.single().sourceMap.any { it.origin.primaryRange == range } })
    }

    @Test
    fun testModeWithoutFixturesDoesNotInventDispatcher() {
        val directory = Files.createTempDirectory("cplus-empty-test-lowering")
        val source = directory.resolve("main.cp")
        Files.writeString(source, "int main() { return 0; }")

        val result = CPlusCompiler().compile(CompileRequest(listOf(source), mode = CompilationMode.TEST))
        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val generated = result.generatedUnits.single().text
        assertTrue(generated.contains("int main("))
        assertFalse(generated.contains("__cplus_test_fixture_"))
        assertTrue(result.artifacts.single().lowered?.unit?.testProduct == null)
    }
}
