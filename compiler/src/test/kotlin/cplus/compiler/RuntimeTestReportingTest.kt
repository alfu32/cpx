package cplus.compiler

import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

class RuntimeTestReportingTest {
    @Test
    fun compilerGeneratedAssertionsUseRuntimeReportAbiAndDefaultLabels() {
        assumeTrue(System.getProperty("os.name").contains("linux", ignoreCase = true))
        val target = TargetInfo(targetTriple = "linux-x86_64")
        val sourceDirectory = Files.createTempDirectory("cplus-test-report-lowering")
        val source = sourceDirectory.resolve("assertions.cp")
        val generated = sourceDirectory.resolve("assertions.c")
        val executable = sourceDirectory.resolve("assertions")
        Files.writeString(
            source,
            """
                int truthValue() { return 1; }
                int expectedValue() { return 4; }
                int actualValue() { return 4; }
                int main() { return 0; }
                test reporting labels {
                    assert(truthValue());
                    assert("truth description", truthValue());
                    assertEquals(expectedValue(), actualValue());
                    assertEquals("equality description", expectedValue(), actualValue());
                }
            """.trimIndent()
        )
        try {
            val compilation = CPlusCompiler().compile(
                CompileRequest(listOf(source), target = target, mode = CompilationMode.TEST)
            )
            assertTrue(compilation.isSuccessful, compilation.diagnostics.joinToString())
            val product = compilation.artifacts.single().lowered!!.unit
            Files.writeString(generated, compilation.generatedUnits.single().text)
            val sdk = requireNotNull(compilation.sdkResolution)
            val helperSet = product.runtimeDependencies.toSet()
            val plan = requireNotNull(RuntimeLinker.plan(sdk, target, helperSet).plan)
            val link = LinkDriver.link(LinkRequest(generated, executable, target, sdk), plan)
            assertTrue(link.isSuccessful, link.output)

            val fixture = requireNotNull(product.testProduct).fixtures.single()
            val resultPath = sourceDirectory.resolve("result.tsv")
            val process = ProcessBuilder(executable.toString(), fixture.identity, resultPath.toString()).start()
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "generated assertion product timed out")
            assertEquals(0, process.exitValue(), stderr)
            assertTrue(stdout.contains("---- truthValue() ----------------"), stdout)
            assertTrue(stdout.contains("---- truth description ----------------"), stdout)
            assertTrue(stdout.contains("    - expectedValue() == actualValue() ----------------"), stdout)
            assertTrue(stdout.contains("    - equality description ----------------"), stdout)
            assertTrue(stdout.contains("expected expression: expectedValue()"), stdout)
            assertTrue(stdout.contains("evaluated expression: actualValue()"), stdout)
            val records = Files.readString(resultPath).lineSequence().filter(String::isNotEmpty).toList()
            assertEquals("CPLUS-TEST\t1\tBEGIN\t${fixture.identity}", records.first())
            assertEquals("CPLUS-TEST\t1\tCOMPLETE\t4\t0\t4", records.last())
            Files.deleteIfExists(resultPath)
        } finally {
            Files.deleteIfExists(executable)
            Files.deleteIfExists(generated)
            Files.deleteIfExists(source)
            Files.deleteIfExists(sourceDirectory)
        }
    }

    @Test
    fun selfHostedHelperFormatsTypedValuesAndWritesVersionedCompletionRecords() {
        assumeTrue(System.getProperty("os.name").contains("linux", ignoreCase = true))
        val manifest = requireNotNull(SdkManifestLoader.load(SdkManifestLocator.defaultManifestPath()).manifest)
        val target = TargetInfo(targetTriple = "linux-x86_64")
        val resolution = requireNotNull(SdkResolver.resolve(manifest, target).resolution)
        val helpers = setOf(
            "__cplus_test_report_truth",
            "__cplus_test_report_equality",
            "__cplus_test_begin",
            "__cplus_test_finish"
        )
        val plan = requireNotNull(RuntimeLinker.plan(resolution, target, helpers).plan)
        val directory = Files.createTempDirectory("cplus-test-report-runtime")
        val source = directory.resolve("report_vectors.c")
        val executable = directory.resolve("report_vectors")
        val resultPath = directory.resolve("results.tsv")
        val missingPath = directory.resolve("missing").resolve("results.tsv")
        Files.writeString(
            source,
            """
                #include "cplus_test_runtime.h"
                #include "cplus_platform.h"
                #include <complex.h>
                #include <math.h>

                #define REPORT(label, value, type, kind, passed) __cplus_test_report_truth(label, #value, &(value), type, sizeof(value), kind, 0, passed)

                enum report_enum { REPORT_ENUM_VALUE = -3 };
                static int report_callback(int value) { return value + 1; }

                int main(int argc, char** argv) {
                    long long signed_value = -9223372036854775807LL;
                    __int128 signed_wide = -(((__int128)1 << 100) + 123);
                    unsigned __int128 unsigned_wide = ((unsigned __int128)1 << 100) + 123;
                    char plain_value = 'Z';
                    _Bool boolean_value = 1;
                    float float_value = 1.5F;
                    double double_value = 2.25;
                    long double long_double_value = -3.5L;
                    double nan_value = __builtin_nan("");
                    double infinity_value = __builtin_huge_val();
                    double negative_zero = -0.0;
                    float complex complex_float = CMPLXF(1.5F, -2.25F);
                    double complex complex_double = CMPLX(2.5, -4.5);
                    long double complex complex_long_double = CMPLXL(3.5L, -5.25L);
                    int pointed_value = 17;
                    int* pointer_value = &pointed_value;
                    int* null_pointer = (int*)0;
                    enum report_enum enum_value = REPORT_ENUM_VALUE;
                    int (*function_pointer)(int) = report_callback;
                    int expected = 7;
                    unsigned int actual = 7;
                    if (argc == 3 && argv[2][0] == 'w') {
                        long long probe = platform_file_open("/dev/full", CPLUS_FILE_WRITE);
                        long long write_result;
                        int begin_result;
                        int finish_result;
                        if (probe < 0) return 80;
                        write_result = platform_file_write(probe, "x", 1);
                        platform_file_close(probe);
                        if (write_result >= 0) return 81;
                        begin_result = __cplus_test_begin("write-failure", "/dev/full");
                        finish_result = __cplus_test_finish();
                        return begin_result != 0 && finish_result != 0 ? 0 : 82;
                    }
                    if (argc != 2) {
                        return __cplus_test_begin("bad", argv[1]) == 0 ? 90 : 0;
                    }
                    if (__cplus_test_begin("fixture\tone", argv[1]) != 0) return 1;
                    REPORT("signed", signed_value, "long long", CPLUS_TEST_VALUE_SIGNED_INTEGER, 1);
                    REPORT("signed wide", signed_wide, "__int128", CPLUS_TEST_VALUE_SIGNED_INTEGER, 1);
                    REPORT("unsigned wide", unsigned_wide, "unsigned __int128", CPLUS_TEST_VALUE_UNSIGNED_INTEGER, 1);
                    REPORT("plain\nchar", plain_value, "char", CPLUS_TEST_VALUE_PLAIN_INTEGER, 0);
                    REPORT("boolean", boolean_value, "_Bool", CPLUS_TEST_VALUE_BOOLEAN, 1);
                    REPORT("float", float_value, "float", CPLUS_TEST_VALUE_FLOAT, 1);
                    REPORT("double", double_value, "double", CPLUS_TEST_VALUE_DOUBLE, 1);
                    REPORT("long double", long_double_value, "long double", CPLUS_TEST_VALUE_LONG_DOUBLE, 1);
                    REPORT("nan", nan_value, "double", CPLUS_TEST_VALUE_DOUBLE, 1);
                    REPORT("infinity", infinity_value, "double", CPLUS_TEST_VALUE_DOUBLE, 1);
                    REPORT("negative zero", negative_zero, "double", CPLUS_TEST_VALUE_DOUBLE, 1);
                    REPORT("complex float", complex_float, "float complex", CPLUS_TEST_VALUE_COMPLEX_FLOAT, 1);
                    REPORT("complex double", complex_double, "double complex", CPLUS_TEST_VALUE_COMPLEX_DOUBLE, 1);
                    REPORT("complex long double", complex_long_double, "long double complex", CPLUS_TEST_VALUE_COMPLEX_LONG_DOUBLE, 1);
                    REPORT("enum", enum_value, "enum report_enum", CPLUS_TEST_VALUE_SIGNED_INTEGER, 1);
                    REPORT("function pointer", function_pointer, "int (*)(int)", CPLUS_TEST_VALUE_POINTER, 1);
                    __cplus_test_report_truth("pointer", "pointer_value", &pointer_value, "int*", sizeof(pointer_value), CPLUS_TEST_VALUE_POINTER, pointer_value == 0, 1);
                    __cplus_test_report_truth("null pointer", "null_pointer", &null_pointer, "int*", sizeof(null_pointer), CPLUS_TEST_VALUE_POINTER, null_pointer == 0, 1);
                    __cplus_test_report_equality("mixed equality", "expected", "actual",
                        &expected, "long long", sizeof(expected), CPLUS_TEST_VALUE_SIGNED_INTEGER, 0,
                        &actual, "unsigned int", sizeof(actual), CPLUS_TEST_VALUE_UNSIGNED_INTEGER, 0, 1);
                    return __cplus_test_finish() == 0 ? 0 : 2;
                }
            """.trimIndent()
        )
        try {
            val link = LinkDriver.link(LinkRequest(source, executable, target, resolution), plan)
            assertTrue(link.isSuccessful, link.output)
            val descriptor = resolution.targetDescriptor
                ?: requireNotNull(TargetRegistry.load(resolution.layout.abiDescriptor).descriptor)
            val audit = RuntimeDependencyAuditor.inspect(executable, descriptor, target.buildProfile)
            assertTrue(audit.isSuccessful, audit.diagnostics.joinToString())

            val process = ProcessBuilder(executable.toString(), resultPath.toString()).start()
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "typed test report helper timed out")
            assertEquals(0, process.exitValue(), stderr)
            val records = Files.readString(resultPath).lineSequence().filter(String::isNotEmpty).toList()
            assertEquals("CPLUS-TEST\t1\tBEGIN\tfixture%09one", records.first())
            assertEquals("CPLUS-TEST\t1\tASSERT\t1\tPASS", records[1])
            assertEquals("CPLUS-TEST\t1\tASSERT\t4\tFAIL", records[4])
            assertEquals("CPLUS-TEST\t1\tCOMPLETE\t18\t1\t19", records.last())
            assertTrue(stdout.contains("1267650600228229401496703205499"), stdout)
            assertTrue(stdout.contains("-1267650600228229401496703205499"), stdout)
            assertTrue(stdout.contains("plain\\nchar"), stdout)
            assertTrue(stdout.contains("inf") && stdout.contains("nan"), stdout)
            assertTrue(stdout.contains("-0x0p+0"), stdout)
            assertTrue(stdout.contains("null pointer"), stdout)
            assertTrue(stdout.contains("---- value: 0x"), stdout)
            assertTrue(stdout.contains("expected value: 7"), stdout)
            assertTrue(stdout.contains("evaluated value: 7"), stdout)
            assertTrue(stdout.contains("---- SUCCESS") && stdout.contains("---- FAIL"), stdout)

            val failedOpen = ProcessBuilder(executable.toString(), missingPath.toString(), "fail-open").start()
            val failureOutput = failedOpen.inputStream.bufferedReader().readText()
            assertTrue(failedOpen.waitFor(30, TimeUnit.SECONDS), "result-file failure vector timed out")
            assertEquals(0, failedOpen.exitValue(), failureOutput)
            assertFalse(Files.exists(missingPath))

            val failedWrite = ProcessBuilder(executable.toString(), "/dev/full", "write-failure").start()
            val writeFailureOutput = failedWrite.inputStream.bufferedReader().readText()
            assertTrue(failedWrite.waitFor(30, TimeUnit.SECONDS), "result-file write failure vector timed out")
            assertEquals(0, failedWrite.exitValue(), writeFailureOutput)
        } finally {
            Files.deleteIfExists(resultPath)
            Files.deleteIfExists(executable)
            Files.deleteIfExists(source)
            Files.deleteIfExists(directory)
        }
    }
}
