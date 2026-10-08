package cplus.compiler

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModuleSourceResolverTest {
    @Test
    fun resolvesLogicalAndStandardImportsFromProjectRootsOutsideTheInstallation() {
        val project = Files.createTempDirectory("cplus-resolver-project")
        val sdk = Files.createTempDirectory("cplus-resolver-sdk")
        val sourceRoot = Files.createDirectories(project.resolve("src"))
        val entry = Files.createDirectories(sourceRoot.resolve("app")).resolve("main.cp")
        val helper = Files.createDirectories(sourceRoot.resolve("math")).resolve("helpers.cp")
        val stdFs = Files.createDirectories(sdk.resolve("std/src")).resolve("fs.cp")
        Files.writeString(entry, "import { add } from math.helpers;\nimport { std_fs_open } from std.fs;\nint main() { return add(1, 2); }")
        Files.writeString(helper, "pub int add(int left, int right) { return left + right; }")
        Files.writeString(stdFs, "pub int std_fs_open() { return 0; }")

        val result = ModuleSourceResolver(listOf(sourceRoot), sdk).resolveClosure(listOf(entry))

        assertEquals(listOf(entry, helper, stdFs).map { it.toAbsolutePath().normalize() }, result.paths)
        assertTrue(result.unresolvedImports.isEmpty())
    }

    @Test
    fun overlaysWinAndCyclesResolveWithoutPullingUnrelatedEntryPoints() {
        val root = Files.createTempDirectory("cplus-resolver-overlay")
        val entry = root.resolve("main.cp")
        val helper = root.resolve("helper.cp")
        val cycle = root.resolve("cycle.cp")
        val unrelatedMain = root.resolve("other-main.cp")
        Files.writeString(entry, "import { value } from ./helper.cp; int main() { return value(); }")
        Files.writeString(helper, "pub int value() { return 1; }")
        Files.writeString(unrelatedMain, "int main() { return 2; }")
        val overlay = "pub int value() { return 42; }\nimport { cycle_value } from ./cycle.cp;"
        Files.writeString(cycle, "pub int cycle_value() { return 0; }\nimport { value } from ./helper.cp;")

        val result = ModuleSourceResolver(listOf(root)).resolveClosure(
            listOf(entry),
            mapOf(helper to overlay)
        )

        assertEquals(listOf(entry, helper, cycle).map { it.toAbsolutePath().normalize() }, result.paths)
        assertEquals(overlay, result.modules.single { it.path == helper.toAbsolutePath().normalize() }.text)
        assertFalse(result.paths.contains(unrelatedMain.toAbsolutePath().normalize()))
        assertTrue(result.unresolvedImports.isEmpty())
    }

    @Test
    fun sameBasenameLogicalModulesRemainDistinctAndCandidateScanIsBounded() {
        val root = Files.createTempDirectory("cplus-resolver-identities")
        val first = Files.createDirectories(root.resolve("first")).resolve("common.cp")
        val second = Files.createDirectories(root.resolve("second")).resolve("common.cp")
        val entry = root.resolve("main.cp")
        Files.writeString(entry, "import { one } from first.common;\nimport { two } from second.common;")
        Files.writeString(first, "pub int one() { return 1; }")
        Files.writeString(second, "pub int two() { return 2; }")
        repeat(5) { index -> Files.writeString(root.resolve("extra$index.cp"), "int value$index;") }
        Files.writeString(root.resolve(unrelatedEntryName), "int main() { return 3; }")

        val resolver = ModuleSourceResolver(listOf(root), maximumCandidates = 3)
        val result = resolver.resolveClosure(listOf(entry))

        assertEquals(listOf(entry, first, second).map { it.toAbsolutePath().normalize() }, result.paths)
        assertEquals(3, resolver.candidatePaths().size)
        assertFalse(result.paths.contains(root.resolve(unrelatedEntryName).toAbsolutePath().normalize()))
    }

    private companion object {
        const val unrelatedEntryName = "other-main.cp"
    }
}
