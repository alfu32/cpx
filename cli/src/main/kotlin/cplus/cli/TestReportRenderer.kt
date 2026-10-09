package cplus.cli

import java.nio.file.Path

internal data class TestFileSummary(
    val path: Path,
    val passed: Int,
    val failed: Int,
    val errors: Int,
    val fixtureCount: Int
) {
    val total: Int get() = passed + failed
}

internal class TestReportRenderer {
    fun fileHeader(index: Int, total: Int, path: Path): String = "::: [$index/$total] $path"

    fun fixtureHeader(index: Int, total: Int, description: String): String = "... [$index/$total] $description"

    fun emptyFixture(): String = "... EMPTY"

    fun noFixtures(): String = "... NO TESTS"

    fun fixtureFooter(result: FixtureProcessResult): String {
        val protocol = result.protocol
        return "... asserts passed ${protocol?.passed ?: 0} / failed ${protocol?.failed ?: 0} / " +
            "total ${protocol?.total ?: 0}; errors ${if (result.error == null) 0 else 1}"
    }

    fun fileFooter(summary: TestFileSummary): String =
        "::: asserts passed ${summary.passed} / failed ${summary.failed} / total ${summary.total}; errors ${summary.errors}"

    fun finalReport(files: List<TestFileSummary>): List<String> = buildList {
        add("::: final report")
        files.forEach { file ->
            add("::: ${file.path}: passed ${file.passed} / failed ${file.failed} / total ${file.total}; errors ${file.errors}")
        }
        val passed = files.sumOf(TestFileSummary::passed)
        val failed = files.sumOf(TestFileSummary::failed)
        val errors = files.sumOf(TestFileSummary::errors)
        add("::: total: passed $passed / failed $failed / total ${passed + failed}; errors $errors")
    }
}
