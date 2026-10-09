package cplus.cli

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class TestReportRendererTest {
    @Test
    fun rendersOrderedFileFixtureEmptyAndAggregateSummaries() {
        val renderer = TestReportRenderer()
        val successful = FixtureProcessResult(0, TestProtocolResult(listOf(true, false), true))
        val first = TestFileSummary(Path.of("tests/box.cp"), passed = 2, failed = 1, errors = 0, fixtureCount = 1)
        val second = TestFileSummary(Path.of("tests/other.cp"), passed = 1, failed = 0, errors = 0, fixtureCount = 1)

        assertEquals("::: [1/2] ${first.path}", renderer.fileHeader(1, 2, first.path))
        assertEquals("... [1/1] generated box stores a value", renderer.fixtureHeader(1, 1, "generated box stores a value"))
        assertEquals("... asserts passed 1 / failed 1 / total 2; errors 0", renderer.fixtureFooter(successful))
        assertEquals("::: asserts passed 2 / failed 1 / total 3; errors 0", renderer.fileFooter(first))
        assertEquals(
            listOf(
                "::: final report",
                "::: ${first.path}: passed 2 / failed 1 / total 3; errors 0",
                "::: ${second.path}: passed 1 / failed 0 / total 1; errors 0",
                "::: total: passed 3 / failed 1 / total 4; errors 0"
            ),
            renderer.finalReport(listOf(first, second))
        )
        assertEquals("... EMPTY", renderer.emptyFixture())
        assertEquals("... NO TESTS", renderer.noFixtures())
    }
}
