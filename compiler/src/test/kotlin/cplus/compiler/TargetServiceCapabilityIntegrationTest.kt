package cplus.compiler

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TargetServiceCapabilityIntegrationTest {
    @Test
    fun compilerReportsServiceAvailabilityFromTheSelectedTargetDescriptor() {
        val directory = Files.createTempDirectory("cplus-service-capabilities")
        val source = directory.resolve("main.cp").also {
            Files.writeString(it, "require_service(\"file\"); int main() { return 0; }")
        }
        try {
            val linux = CPlusCompiler().compile(
                CompileRequest(listOf(source), target = TargetInfo(targetTriple = "linux-x86_64"))
            )
            assertTrue(linux.isSuccessful, linux.diagnostics.joinToString())

            val darwin = CPlusCompiler().compile(
                CompileRequest(listOf(source), target = TargetInfo(targetTriple = "darwin-aarch64"))
            )
            val diagnostic = darwin.diagnostics.single { it.code == "CPX603" }
            assertEquals(
                "target 'darwin-aarch64' does not provide platform service 'file'",
                diagnostic.message
            )
        } finally {
            Files.deleteIfExists(source)
            Files.deleteIfExists(directory)
        }
    }
}
