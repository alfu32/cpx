package cplus.compiler

import cplus.core.AstBinary
import cplus.core.AstBlock
import cplus.core.AstConditional
import cplus.core.AstFunction
import cplus.core.AstReturn
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

class ComplexAbiIntegrationTest {
    @Test
    fun complexScalarFunctionsMatchIndependentLinuxC17CallerAbi() {
        assumeTrue(System.getProperty("os.name").contains("linux", ignoreCase = true))
        val source = """
            import {
                cabsf, cabs, cabsl, cargf, carg, cargl,
                crealf, creal, creall, cimagf, cimag, cimagl,
                conjf, conj, conjl, cprojf, cproj, cprojl
            } from c.complex;
            pub float _Complex round_trip_float_complex(float _Complex value) { return value; }
            pub double _Complex round_trip_double_complex(double _Complex value) { return value; }
            pub long double _Complex round_trip_long_double_complex(long double _Complex value) { return value; }
            pub float _Complex add_float_complex(float _Complex left, float right) { return left + right; }
            pub double _Complex add_mixed_complex(float _Complex left, double right) { return left + right; }
            pub float _Complex add_integer_complex(float _Complex left, int right) { return left + right; }
            pub long double _Complex add_extended_complex(double _Complex left, long double right) { return left + right; }
            pub double _Complex subtract_complex(double _Complex left, double _Complex right) { return left - right; }
            pub double _Complex negate_complex(double _Complex value) { return -value; }
            pub double _Complex multiply_complex(double _Complex left, double _Complex right) { return left * right; }
            pub double _Complex divide_complex(double _Complex left, double _Complex right) { return left / right; }
            pub double _Complex add_assign_complex(double _Complex left, double _Complex right) { left += right; return left; }
            pub bool equal_complex(double _Complex left, double _Complex right) { return left == right; }
            pub bool not_equal_complex(double _Complex left, double _Complex right) { return left != right; }
            pub bool logical_and_complex(double _Complex left, double _Complex right) { return left && right; }
            pub bool logical_or_complex(double _Complex left, double _Complex right) { return left || right; }
            pub bool logical_not_complex(double _Complex value) { return !value; }
            pub long double _Complex select_extended_complex(bool choose, float _Complex left, long double _Complex right) {
                return choose ? left : right;
            }
            pub float magnitude_float(float _Complex value) { return cabsf(value); }
            pub double magnitude_double(double _Complex value) { return cabs(value); }
            pub long double magnitude_extended(long double _Complex value) { return cabsl(value); }
            pub float phase_float(float _Complex value) { return cargf(value); }
            pub double phase_double(double _Complex value) { return carg(value); }
            pub long double phase_extended(long double _Complex value) { return cargl(value); }
            pub float real_float(float _Complex value) { return crealf(value); }
            pub double real_double(double _Complex value) { return creal(value); }
            pub long double real_extended(long double _Complex value) { return creall(value); }
            pub float imaginary_float(float _Complex value) { return cimagf(value); }
            pub double imaginary_double(double _Complex value) { return cimag(value); }
            pub long double imaginary_extended(long double _Complex value) { return cimagl(value); }
            pub float _Complex conjugate_float(float _Complex value) { return conjf(value); }
            pub double _Complex conjugate_double(double _Complex value) { return conj(value); }
            pub long double _Complex conjugate_extended(long double _Complex value) { return conjl(value); }
            pub float _Complex project_float(float _Complex value) { return cprojf(value); }
            pub double _Complex project_double(double _Complex value) { return cproj(value); }
            pub long double _Complex project_extended(long double _Complex value) { return cprojl(value); }
        """.trimIndent()
        val result = CPlusCompiler().compileText(Files.createTempFile("complex-abi", ".cp"), source)

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val descriptor = requireNotNull(result.sdkResolution?.targetDescriptor)
        val compilers = listOf("cc", "clang").filter(::isCompilerAvailable)
        assertTrue("cc" in compilers, "the Linux test host must provide cc")
        compilers.forEach { compiler ->
            assertTrue(CCompilerToolchains.supportsC17Complex(descriptor, compiler), compiler)
        }
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
        fun binaryExpressionType(functionName: String): String {
            val function = result.artifacts.single().ast.declarations
                .filterIsInstance<AstFunction>().single { it.name == functionName }
            val expression = ((function.body as AstBlock).statements.single() as AstReturn).expression as AstBinary
            return model.expressionTypes.getValue(expression).name
        }
        assertEquals("float _Complex", binaryExpressionType("add_float_complex"))
        assertEquals("double _Complex", binaryExpressionType("add_mixed_complex"))
        assertEquals("float _Complex", binaryExpressionType("add_integer_complex"))
        assertEquals("long double _Complex", binaryExpressionType("add_extended_complex"))
        val conditionalFunction = result.artifacts.single().ast.declarations
            .filterIsInstance<AstFunction>().single { it.name == "select_extended_complex" }
        val conditionalExpression = ((conditionalFunction.body as AstBlock).statements.single() as AstReturn)
            .expression as AstConditional
        assertEquals("long double _Complex", model.expressionTypes.getValue(conditionalExpression).name)

        val directory = Files.createTempDirectory("complex-caller")
        val header = directory.resolve("complex_api.h")
        val generated = directory.resolve("generated.c")
        val caller = directory.resolve("caller.c")
        try {
            header.toFile().writeText(result.generatedHeaders.single().text)
            generated.toFile().writeText(result.generatedUnits.single().text)
            caller.toFile().writeText(
                """
                    #include <math.h>
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
                        if (add_float_complex(CMPLXF(1.0F, 2.0F), 2.5F) != CMPLXF(3.5F, 2.0F)) return 5;
                        if (add_mixed_complex(CMPLXF(1.0F, 2.0F), 3.0) != CMPLX(4.0, 2.0)) return 6;
                        if (add_integer_complex(CMPLXF(1.0F, 2.0F), 4) != CMPLXF(5.0F, 2.0F)) return 7;
                        if (add_extended_complex(CMPLX(2.0, 3.0), 4.0L) != CMPLXL(6.0L, 3.0L)) return 8;
                        if (subtract_complex(CMPLX(3.0, 4.0), CMPLX(1.0, 2.0)) != CMPLX(2.0, 2.0)) return 9;
                        if (negate_complex(CMPLX(1.0, 2.0)) != CMPLX(-1.0, -2.0)) return 10;
                        if (multiply_complex(CMPLX(1.0, 2.0), CMPLX(3.0, 4.0)) != CMPLX(-5.0, 10.0)) return 11;
                        if (divide_complex(CMPLX(1.0, 2.0), CMPLX(3.0, 4.0)) != CMPLX(0.44, 0.08)) return 12;
                        if (add_assign_complex(CMPLX(1.0, 2.0), CMPLX(3.0, 4.0)) != CMPLX(4.0, 6.0)) return 13;
                        if (!equal_complex(double_value, double_value)) return 14;
                        if (not_equal_complex(double_value, double_value)) return 15;
                        if (!logical_and_complex(double_value, extended)) return 16;
                        if (!logical_or_complex(CMPLX(0.0, 0.0), double_value)) return 17;
                        if (!logical_not_complex(CMPLX(0.0, 0.0))) return 18;
                        if (select_extended_complex(1, CMPLXF(1.0F, 2.0F), CMPLXL(3.0L, 4.0L)) != CMPLXL(1.0L, 2.0L)) return 19;
                        if (select_extended_complex(0, CMPLXF(1.0F, 2.0F), CMPLXL(3.0L, 4.0L)) != CMPLXL(3.0L, 4.0L)) return 20;
                        if (magnitude_float(CMPLXF(3.0F, 4.0F)) != 5.0F) return 21;
                        if (magnitude_double(CMPLX(3.0, 4.0)) != 5.0) return 22;
                        if (magnitude_extended(CMPLXL(3.0L, 4.0L)) != 5.0L) return 23;
                        if (phase_float(CMPLXF(0.0F, 1.0F)) != atan2f(1.0F, 0.0F)) return 24;
                        if (phase_double(CMPLX(0.0, 1.0)) != atan2(1.0, 0.0)) return 25;
                        if (phase_extended(CMPLXL(0.0L, 1.0L)) != atan2l(1.0L, 0.0L)) return 26;
                        if (real_float(CMPLXF(1.0F, 2.0F)) != 1.0F || imaginary_float(CMPLXF(1.0F, 2.0F)) != 2.0F) return 27;
                        if (real_double(CMPLX(1.0, 2.0)) != 1.0 || imaginary_double(CMPLX(1.0, 2.0)) != 2.0) return 28;
                        if (real_extended(CMPLXL(1.0L, 2.0L)) != 1.0L || imaginary_extended(CMPLXL(1.0L, 2.0L)) != 2.0L) return 29;
                        if (conjugate_float(CMPLXF(1.0F, 2.0F)) != CMPLXF(1.0F, -2.0F)) return 30;
                        if (conjugate_double(CMPLX(1.0, 2.0)) != CMPLX(1.0, -2.0)) return 31;
                        if (conjugate_extended(CMPLXL(1.0L, 2.0L)) != CMPLXL(1.0L, -2.0L)) return 32;
                        if (project_float(CMPLXF(1.0F, 2.0F)) != CMPLXF(1.0F, 2.0F)) return 33;
                        if (project_double(CMPLX(1.0, 2.0)) != CMPLX(1.0, 2.0)) return 34;
                        if (project_extended(CMPLXL(1.0L, 2.0L)) != CMPLXL(1.0L, 2.0L)) return 35;
                        return 0;
                    }
                """.trimIndent()
            )
            val sdk = requireNotNull(result.sdkResolution)
            val target = TargetInfo(targetTriple = "linux-x86_64")
            val plan = requireNotNull(RuntimeLinker.plan(sdk, target).plan)
            compilers.forEach { compiler ->
                val executable = directory.resolve("complex-caller-${compiler.replace('/', '-')}")
                val link = LinkDriver.link(
                    LinkRequest(
                        generated,
                        executable,
                        target,
                        sdk,
                        sourceDependencies = listOf(caller),
                        cCompiler = compiler
                    ),
                    plan
                )
                assertTrue(link.isSuccessful, "$compiler: ${link.output}")
                val execution = ProcessBuilder(executable.toString()).redirectErrorStream(true).start()
                val executionOutput = execution.inputStream.bufferedReader().readText()
                assertEquals(0, execution.waitFor(), "$compiler: $executionOutput")
                val audit = RuntimeDependencyAuditor.inspect(executable, descriptor, target.buildProfile)
                assertTrue(audit.isSuccessful, "$compiler: ${audit.diagnostics.joinToString()}")
                Files.deleteIfExists(executable)
            }
        } finally {
            Files.walk(directory).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
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
    fun rejectsOrderedAndIntegralOnlyComplexOperations() {
        val invalidBodies = listOf(
            "return left < right;",
            "return left % right;",
            "left %= right; return left;",
            "return ~left;",
            "return left & right;"
        )
        invalidBodies.forEachIndexed { index, body ->
            val result = CPlusCompiler().compileText(
                Files.createTempFile("complex-invalid-op-$index", ".cp"),
                "bool invalid_operation(double _Complex left, double _Complex right) { $body }"
            )

            assertFalse(result.isSuccessful, body)
            assertTrue(result.diagnostics.any { it.code == "SEM316" }, result.diagnostics.joinToString())
        }
    }

    @Test
    fun cComplexImportsExposeTheC17HeaderAndFunctionDeclarations() {
        val result = CPlusCompiler().compileText(
            Files.createTempFile("complex-import", ".cp"),
            """
                import {
                    cabsf, cabs, cabsl, cargf, carg, cargl,
                    crealf, creal, creall, cimagf, cimag, cimagl,
                    conjf, conj, conjl, cprojf, cproj, cprojl
                } from c.complex;
                double magnitude(double _Complex value) { return cabs(value); }
                float magnitude_float(float _Complex value) { return cabsf(value); }
                long double magnitude_extended(long double _Complex value) { return cabsl(value); }
                double angle(double _Complex value) { return carg(value); }
                double real_part(double _Complex value) { return creal(value); }
                double imaginary_part(double _Complex value) { return cimag(value); }
                double _Complex conjugate(double _Complex value) { return conj(value); }
                double _Complex project(double _Complex value) { return cproj(value); }
            """.trimIndent()
        )

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        assertTrue(result.generatedUnits.single().text.contains("#include <complex.h>"))
        assertTrue(result.generatedUnits.single().text.contains("cabs"))
    }

    private fun isCompilerAvailable(compiler: String): Boolean = runCatching {
        val process = ProcessBuilder(compiler, "--version").redirectErrorStream(true).start()
        process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor() == 0
    }.getOrDefault(false)
}
