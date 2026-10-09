package cplus.compiler

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WorkspaceFrontendPhasesTest {
    @Test
    fun parsesEveryWorkspaceModuleBeforeSerialOrParallelExpansion() {
        val directory = Files.createTempDirectory("cplus-frontend-phases")
        val sources = listOf(
            TextSource(
                directory.resolve("first.cp"),
                """
                    comptime cpx<decl> make_first() {
                        return { struct first_generated_t { int value; }; };
                    }
                    make_first();
                """.trimIndent()
            ),
            TextSource(
                directory.resolve("second.cp"),
                """
                    comptime cpx<decl> make_second() {
                        return { struct second_generated_t { int value; }; };
                    }
                    make_second();
                """.trimIndent()
            )
        )

        val serial = CPlusCompiler().compileTextWorkspace(
            sources,
            options = CompilerOptions(parallelism = 1)
        )
        val parallel = CPlusCompiler().compileTextWorkspace(
            sources,
            options = CompilerOptions(parallelism = 2)
        )

        assertTrue(serial.isSuccessful, serial.diagnostics.joinToString())
        assertTrue(parallel.isSuccessful, parallel.diagnostics.joinToString())
        assertEquals(2, serial.moduleGraph?.nodes?.size)
        assertEquals(2, parallel.moduleGraph?.nodes?.size)
        val serialC = assertNotNull(serial.generatedUnits.singleOrNull()).text
        val parallelC = assertNotNull(parallel.generatedUnits.singleOrNull()).text
        assertTrue(serialC.contains("struct first_generated_t"), serialC)
        assertTrue(serialC.contains("struct second_generated_t"), serialC)
        assertEquals(serialC, parallelC)
    }
}
