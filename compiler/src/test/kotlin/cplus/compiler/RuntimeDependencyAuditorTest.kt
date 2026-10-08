package cplus.compiler

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

class RuntimeDependencyAuditorTest {
    @Test
    fun missingInspectionToolsFailClosed() {
        val root = SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize().parent!!.parent!!
        val descriptor = requireNotNull(TargetRegistry.load(root.resolve("abi/linux-x86_64.toml")).descriptor)
        val binary = Files.createTempFile("cplus-audit-tools-unavailable", ".bin")
        try {
            Files.writeString(binary, "an executable placeholder")

            val report = RuntimeDependencyAuditor.inspect(binary, descriptor, BuildProfile()) { null }

            assertFalse(report.isSuccessful)
            assertTrue(report.diagnostics.any { it.contains("unable to verify ELF target format") })
            assertTrue(report.diagnostics.any { it.contains("unable to inspect unresolved symbols") })
        } finally {
            Files.deleteIfExists(binary)
        }
    }

    @Test
    fun malformedElfInputFailsClosedInsteadOfReportingNoDependencies() {
        assumeTrue(System.getProperty("os.name").contains("linux", ignoreCase = true))
        val root = SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize().parent!!.parent!!
        val descriptor = requireNotNull(TargetRegistry.load(root.resolve("abi/linux-x86_64.toml")).descriptor)
        val invalidBinary = Files.createTempFile("cplus-audit-invalid-elf", ".bin")
        try {
            Files.writeString(invalidBinary, "not an ELF executable")

            val report = RuntimeDependencyAuditor.inspect(invalidBinary, descriptor, BuildProfile())

            assertFalse(report.isSuccessful)
            assertTrue(report.diagnostics.any { it.contains("unable to verify ELF target format") }, report.diagnostics.joinToString())
        } finally {
            Files.deleteIfExists(invalidBinary)
        }
    }

    @Test
    fun malformedPeAndMachOInputsFailClosedInsteadOfReportingNoDependencies() {
        val root = SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize().parent!!.parent!!
        val invalidBinary = Files.createTempFile("cplus-audit-invalid-format", ".bin")
        try {
            Files.writeString(invalidBinary, "not a native executable")
            val windows = requireNotNull(TargetRegistry.load(root.resolve("abi/windows-x86_64.toml")).descriptor)
            val darwin = requireNotNull(TargetRegistry.load(root.resolve("abi/darwin-aarch64.toml")).descriptor)

            val peReport = RuntimeDependencyAuditor.inspect(invalidBinary, windows, BuildProfile())
            assertFalse(peReport.isSuccessful)
            assertTrue(
                peReport.diagnostics.any { it.contains("unable to verify PE/COFF target format") },
                peReport.diagnostics.joinToString()
            )

            val machReport = RuntimeDependencyAuditor.inspect(invalidBinary, darwin, BuildProfile())
            assertFalse(machReport.isSuccessful)
            assertTrue(
                machReport.diagnostics.any { it.contains("unable to verify Mach-O target format") },
                machReport.diagnostics.joinToString()
            )
        } finally {
            Files.deleteIfExists(invalidBinary)
        }
    }
}
