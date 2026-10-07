package cplus.cli

import cplus.compiler.CPlusCompiler
import cplus.core.DiagnosticSeverity
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CliGoldenFixtureTest {
    @Test
    fun minimalFixtureMatchesExpandedAstCHeaderAndSourceMap() {
        assertFixture("minimal")
    }

    @Test
    fun malformedFixtureMatchesDiagnosticsAndPublishesNoCArtifacts() {
        assertFixture("malformed")
    }

    @Test
    fun deterministicCpxFixtureCompilesAndExecutesGeneratedC() {
        assertFixture("cpx-deterministic")
        val resource = requireNotNull(javaClass.getResource("/golden/cpx-deterministic"))
        val input = Path.of(resource.toURI()).resolve("input.cp")
        val result = CPlusCompiler().compileText(input, input.readText())
        val directory = Files.createTempDirectory("cplus-cpx-golden-e2e")
        val cFile = directory.resolve("program.c").also { it.writeText(requireNotNull(result.generatedUnits.singleOrNull()).text) }
        val executable = directory.resolve("program")
        val compile = ProcessBuilder("cc", "-std=c17", cFile.toString(), "-o", executable.toString())
            .redirectErrorStream(true)
            .start()
        val output = compile.inputStream.bufferedReader().readText()
        assertEquals(0, compile.waitFor(), output)
        assertEquals(0, ProcessBuilder(executable.toString()).redirectErrorStream(true).start().waitFor())
    }

    private fun assertFixture(name: String) {
        val resource = requireNotNull(javaClass.getResource("/golden/$name")) {
            "missing CLI golden fixture '$name'"
        }
        val root = Path.of(resource.toURI())
        val input = root.resolve("input.cp")
        val result = CPlusCompiler().compileText(input, input.readText())
        val artifact = result.artifacts.singleOrNull()

        assertOptionalText(root.resolve("expected.expanded.cp"), artifact?.let { AstPrinter().print(it.ast) })
        assertOptionalText(root.resolve("expected.c"), result.generatedUnits.singleOrNull()?.text)
        assertOptionalText(root.resolve("expected.h"), result.generatedHeaders.singleOrNull()?.text)
        assertOptionalText(
            root.resolve("expected.map"),
            result.generatedUnits.singleOrNull()?.let(::serializeMap)
        )

        val expectedDiagnostics = optionalLines(root.resolve("expected.diagnostics"))
        val actualDiagnostics = result.diagnostics
            .filter { it.severity == DiagnosticSeverity.ERROR }
            .map { it.code }
        assertEquals(expectedDiagnostics, actualDiagnostics)

        if (expectedDiagnostics.isNotEmpty()) {
            assertTrue(result.generatedUnits.isEmpty(), "diagnostic fixture unexpectedly emitted C")
            assertTrue(result.generatedHeaders.isEmpty(), "diagnostic fixture unexpectedly emitted a header")
        }
    }

    private fun serializeMap(unit: cplus.backend.GeneratedCUnit): String = buildString {
        unit.sourceMap.forEach { mapping ->
            val range = requireNotNull(mapping.origin.primaryRange) {
                "golden source-map mapping has no primary source range"
            }
            appendLine(
                "${mapping.generatedLine}:${mapping.generatedStartOffset}-${mapping.generatedEndOffset}" +
                    " -> ${range.file.value}:${range.startOffset}-${range.endOffset}"
            )
        }
    }

    private fun assertOptionalText(path: Path, actual: String?) {
        if (path.exists()) {
            assertEquals(normalizeLineEndings(path.readText()), actual?.let(::normalizeLineEndings), "golden mismatch for $path")
        }
    }

    private fun normalizeLineEndings(value: String): String =
        value.replace("\r\n", "\n").replace('\r', '\n')

    private fun optionalLines(path: Path): List<String> = if (path.exists()) {
        path.readLines().map(String::trim).filter(String::isNotEmpty)
    } else {
        emptyList()
    }
}
