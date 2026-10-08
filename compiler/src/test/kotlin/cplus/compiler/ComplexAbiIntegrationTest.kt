package cplus.compiler

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComplexAbiIntegrationTest {
    @Test
    fun complexScalarFunctionsMatchIndependentLinuxC17CallerAbi() {
        val source = """
            pub float _Complex round_trip_float_complex(float _Complex value) { return value; }
            pub double _Complex round_trip_double_complex(double _Complex value) { return value; }
            pub long double _Complex round_trip_long_double_complex(long double _Complex value) { return value; }
        """.trimIndent()
        val result = CPlusCompiler().compileText(Files.createTempFile("complex-abi", ".cp"), source)

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val descriptor = requireNotNull(result.sdkResolution?.targetDescriptor)
        assertTrue(CCompilerToolchains.supportsC17Complex(descriptor, "cc"))
        val model = requireNotNull(result.semanticModel)
        listOf(
            Triple("round_trip_float_complex", "float _Complex", 8),
            Triple("round_trip_double_complex", "double _Complex", 16),
            Triple("round_trip_long_double_complex", "long double _Complex", 32)
        ).forEach { (name, typeName, expectedSize) ->
            val function = model.functions.getValue(name)
            assertEquals(typeName, function.returnType.name)
            assertEquals(typeName, function.parameters.single().type.name)
            val layout = AbiLayoutEngine(descriptor).layout(function.returnType)
            assertEquals(expectedSize, layout.size, typeName)
            assertEquals(descriptor.floatingTypes.getValue(typeName.substringBefore(" _Complex")).alignmentBytes, layout.alignment, typeName)
        }

        val root = SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize().parent!!.parent!!
        val directory = Files.createTempDirectory("complex-caller")
        val header = directory.resolve("complex_api.h")
        val generated = directory.resolve("generated.c")
        val caller = directory.resolve("caller.c")
        val executable = directory.resolve("complex-caller")
        try {
            header.toFile().writeText(result.generatedHeaders.single().text)
            generated.toFile().writeText(result.generatedUnits.single().text)
            caller.toFile().writeText(
                """
                    #include <complex.h>
                    #include "${header.fileName}"
                    _Static_assert(sizeof(float complex) == 2 * sizeof(float), "float complex representation");
                    _Static_assert(sizeof(double complex) == 2 * sizeof(double), "double complex representation");
                    _Static_assert(sizeof(long double complex) == 2 * sizeof(long double), "long double complex representation");
                    _Static_assert(_Alignof(float complex) == _Alignof(float), "float complex alignment");
                    _Static_assert(_Alignof(double complex) == _Alignof(double), "double complex alignment");
                    _Static_assert(_Alignof(long double complex) == _Alignof(long double), "long double complex alignment");
                    int main(void) {
                        float complex single = CMPLXF(1.25F, -2.5F);
                        double complex double_value = CMPLX(3.125, -4.5);
                        long double complex extended = CMPLXL(5.75L, -6.875L);
                        if (round_trip_float_complex(single) != single) return 1;
                        if (round_trip_double_complex(double_value) != double_value) return 2;
                        if (round_trip_long_double_complex(extended) != extended) return 3;
                        if (I != CMPLXF(0.0F, 1.0F)) return 4;
                        return 0;
                    }
                """.trimIndent()
            )
            val compile = ProcessBuilder(
                "cc", "-std=c17", "-I", root.resolve("libc/include").toString(),
                "-I", root.resolve("runtime/include").toString(), generated.toString(), caller.toString(),
                "-o", executable.toString()
            ).redirectErrorStream(true).start()
            val compileOutput = compile.inputStream.bufferedReader().readText()
            assertEquals(0, compile.waitFor(), compileOutput)
            val execution = ProcessBuilder(executable.toString()).redirectErrorStream(true).start()
            val executionOutput = execution.inputStream.bufferedReader().readText()
            assertEquals(0, execution.waitFor(), executionOutput)
        } finally {
            Files.deleteIfExists(executable)
            Files.deleteIfExists(caller)
            Files.deleteIfExists(generated)
            Files.deleteIfExists(header)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun rejectsComplexTypesForTargetsWithoutVerifiedCapability() {
        val result = CPlusCompiler().compileText(
            Files.createTempFile("complex-unavailable", ".cp"),
            "double _Complex identity(double _Complex value) { return value; }",
            target = TargetInfo(targetTriple = "linux-aarch64")
        )

        assertFalse(result.isSuccessful)
        assertTrue(result.diagnostics.any { it.code == "SEM412" }, result.diagnostics.joinToString())
    }

    @Test
    fun cComplexImportsExposeTheC17HeaderAndFunctionDeclarations() {
        val result = CPlusCompiler().compileText(
            Files.createTempFile("complex-import", ".cp"),
            """
                import { cabs } from c.complex;
                double magnitude(double _Complex value) { return cabs(value); }
            """.trimIndent()
        )

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        assertTrue(result.generatedUnits.single().text.contains("#include <complex.h>"))
        assertTrue(result.generatedUnits.single().text.contains("cabs"))
    }
}
