package cplus.compiler

import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompilerIntegrationTest {
    @Test
    fun importedForeignTypesRetainCNamesAndHeaders() {
        val source = """
            import { FILE } from c.stdio;
            import { size_t } from c.stddef;

            FILE* output;
            size_t length;

            int main() {
                return 0;
            }
        """.trimIndent()
        val result = CPlusCompiler().compileText(Files.createTempFile("cplus-foreign-types", ".cp"), source)

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val generated = result.generatedUnits.single().text
        assertTrue(generated.contains("#include <stddef.h>"))
        assertTrue(generated.contains("#include <stdio.h>"))
        assertTrue(generated.contains("FILE* output;"))
        assertTrue(generated.contains("size_t length;"))

        val directory = Files.createTempDirectory("cplus-foreign-types-e2e")
        val cFile = directory.resolve("program.c")
        val executable = directory.resolve("program")
        cFile.writeText(generated)
        val compileProcess = ProcessBuilder("cc", "-std=c17", cFile.toString(), "-o", executable.toString())
            .redirectErrorStream(true)
            .start()
        val compileOutput = compileProcess.inputStream.bufferedReader().readText()
        assertEquals(0, compileProcess.waitFor(), compileOutput)

        val execution = ProcessBuilder(executable.toString()).redirectErrorStream(true).start()
        assertEquals(0, execution.waitFor())
    }

    @Test
    fun typeAliasesResolveAndEmitAsTypedefs() {
        val source = """
            count_t total;

            count_t identity(count_t value) {
                return value;
            }

            int main() {
                count_t local = 7;
                return identity(local);
            }

            typedef int count_t;
        """.trimIndent()
        val result = CPlusCompiler().compileText(Files.createTempFile("cplus-alias", ".cp"), source)

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val generated = result.generatedUnits.single().text
        assertTrue(generated.contains("typedef int count_t;"))
        assertTrue(generated.contains("count_t identity(count_t value);"))
        assertTrue(generated.contains("count_t local = 7;"))

        val directory = Files.createTempDirectory("cplus-alias-e2e")
        val cFile = directory.resolve("program.c")
        val executable = directory.resolve("program")
        cFile.writeText(generated)
        val compileProcess = ProcessBuilder("cc", "-std=c17", cFile.toString(), "-o", executable.toString())
            .redirectErrorStream(true)
            .start()
        val compileOutput = compileProcess.inputStream.bufferedReader().readText()
        assertEquals(0, compileProcess.waitFor(), compileOutput)

        val execution = ProcessBuilder(executable.toString()).redirectErrorStream(true).start()
        assertEquals(7, execution.waitFor())
    }

    @Test
    fun arrayDeclaratorsLowerAcrossDeclarations() {
        val source = """
            struct table {
                int values[3];
            };

            int numbers[4];

            int first(int values[3]) {
                return 4;
            }

            int main() {
                int local[2];
                return first(local);
            }
        """.trimIndent()
        val result = CPlusCompiler().compileText(Files.createTempFile("cplus-arrays", ".cp"), source)

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val generated = result.generatedUnits.single().text
        assertTrue(generated.contains("int values[3];"))
        assertTrue(generated.contains("int numbers[4];"))
        assertTrue(generated.contains("int first(int values[3]);"))
        assertTrue(generated.contains("int local[2];"))

        val directory = Files.createTempDirectory("cplus-arrays-e2e")
        val cFile = directory.resolve("program.c")
        val executable = directory.resolve("program")
        cFile.writeText(generated)
        val compileProcess = ProcessBuilder("cc", "-std=c17", cFile.toString(), "-o", executable.toString())
            .redirectErrorStream(true)
            .start()
        val compileOutput = compileProcess.inputStream.bufferedReader().readText()
        assertEquals(0, compileProcess.waitFor(), compileOutput)

        val execution = ProcessBuilder(executable.toString()).redirectErrorStream(true).start()
        assertEquals(4, execution.waitFor())
    }

    @Test
    fun minimalProgramTranscodesAndExecutes() {
        val source = """
            struct point_t {
                int x;
                int y;
            };

            int add(int a, int b) {
                return a + b;
            }

            int main() {
                return add(1, 2);
            }
        """.trimIndent()
        val compiler = CPlusCompiler()
        val result = compiler.compileText(Files.createTempFile("cplus", ".cp"), source)

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val generated = result.generatedUnits.single().text
        assertTrue(generated.contains("struct point_t"))
        assertTrue(generated.contains("int add(int a, int b);"))
        assertTrue(generated.contains("return (a + b);"))

        val directory = Files.createTempDirectory("cplus-e2e")
        val cFile = directory.resolve("program.c")
        val executable = directory.resolve("program")
        cFile.writeText(generated)

        val compileProcess = ProcessBuilder("cc", "-std=c17", cFile.toString(), "-o", executable.toString())
            .redirectErrorStream(true)
            .start()
        val compileOutput = compileProcess.inputStream.bufferedReader().readText()
        assertEquals(0, compileProcess.waitFor(), compileOutput)

        val execution = ProcessBuilder(executable.toString()).redirectErrorStream(true).start()
        val executionOutput = execution.inputStream.bufferedReader().readText()
        assertEquals(3, execution.waitFor(), executionOutput)
    }

    @Test
    fun instanceAndStaticMethodsLowerToCallableCFunctions() {
        val source = """
            struct point_t {
                int x;

                int get(self) {
                    return self.x;
                }

                int default_value() {
                    return 4;
                }
            };

            point_t* pointer;

            int main() {
                point_t point;
                point.x = 3;
                return point.get() + point_t.default_value();
            }

            int pointer_value() {
                return pointer.get();
            }
        """.trimIndent()
        val result = CPlusCompiler().compileText(Files.createTempFile("cplus-methods", ".cp"), source)

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val generated = result.generatedUnits.single().text
        assertTrue(generated.contains("int point_t__get(struct point_t* self);"))
        assertTrue(generated.contains("int point_t__default_value();"))
        assertTrue(generated.contains("point_t__get(&point)"))
        assertTrue(generated.contains("point_t__default_value()"))
        assertTrue(generated.contains("point_t__get(pointer)"))
    }

    @Test
    fun structuralCpxGeneratesTypedSpecializationBeforeLowering() {
        val source = """
            comptime cpx<decl> optional(type T) {
                return {
                    struct optional_{T}_t {
                        bool valid;
                        T value;
                    };
                };
            }

            optional(int);

            int main() {
                optional_int_t value;
                value.valid = 1;
                value.value = 9;
                return value.value;
            }
        """.trimIndent()
        val result = CPlusCompiler().compileText(Files.createTempFile("cplus-cpx", ".cp"), source)

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val generated = result.generatedUnits.single().text
        assertTrue(generated.contains("struct optional_int_t"))
        assertTrue(generated.contains("bool valid;"))
        assertTrue(generated.contains("int value;"))

        val directory = Files.createTempDirectory("cplus-cpx-e2e")
        val cFile = directory.resolve("program.c")
        val executable = directory.resolve("program")
        cFile.writeText(generated)
        val compileProcess = ProcessBuilder("cc", "-std=c17", cFile.toString(), "-o", executable.toString())
            .redirectErrorStream(true)
            .start()
        val compileOutput = compileProcess.inputStream.bufferedReader().readText()
        assertEquals(0, compileProcess.waitFor(), compileOutput)
        val execution = ProcessBuilder(executable.toString()).redirectErrorStream(true).start()
        assertEquals(9, execution.waitFor())
    }

    @Test
    fun foreignImportAndDeferLoweringPreserveCleanupOrder() {
        val source = """
            import { printf } from c.stdio;

            int main() {
                defer printf("leaving main\n");
                printf("working\n");
                return 0;
            }
        """.trimIndent()
        val result = CPlusCompiler().compileText(Files.createTempFile("cplus-import-defer", ".cp"), source)

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val generated = result.generatedUnits.single().text
        assertTrue(generated.contains("#include <stdio.h>"))
        assertTrue(generated.contains("printf(\"working\\n\")"))
        assertTrue(generated.contains("printf(\"leaving main\\n\")"))

        val directory = Files.createTempDirectory("cplus-import-defer-e2e")
        val cFile = directory.resolve("program.c")
        val executable = directory.resolve("program")
        cFile.writeText(generated)
        val compileProcess = ProcessBuilder("cc", "-std=c17", cFile.toString(), "-o", executable.toString())
            .redirectErrorStream(true)
            .start()
        val compileOutput = compileProcess.inputStream.bufferedReader().readText()
        assertEquals(0, compileProcess.waitFor(), compileOutput)

        val execution = ProcessBuilder(executable.toString()).redirectErrorStream(true).start()
        val executionOutput = execution.inputStream.bufferedReader().readText()
        assertEquals(0, execution.waitFor(), executionOutput)
        assertEquals("working\nleaving main\n", executionOutput)
    }

    @Test
    fun loopsAndDeferredCleanupCoverContinueAndBreakExits() {
        val source = """
            import { printf } from c.stdio;

            int main() {
                int sum = 0;
                for (int i = 0; i < 3; i = i + 1) {
                    defer printf("tick\n");
                    if (i == 1) {
                        continue;
                    }
                    sum = sum + i;
                }
                return sum;
            }
        """.trimIndent()
        val result = CPlusCompiler().compileText(Files.createTempFile("cplus-loop-defer", ".cp"), source)

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val generated = result.generatedUnits.single().text
        assertTrue(generated.contains("for (int i = 0; (i < 3); (i = (i + 1)))"))
        assertTrue(generated.contains("continue;"))
        assertTrue(generated.contains("printf(\"tick\\n\")"))

        val directory = Files.createTempDirectory("cplus-loop-defer-e2e")
        val cFile = directory.resolve("program.c")
        val executable = directory.resolve("program")
        cFile.writeText(generated)
        val compileProcess = ProcessBuilder("cc", "-std=c17", cFile.toString(), "-o", executable.toString())
            .redirectErrorStream(true)
            .start()
        val compileOutput = compileProcess.inputStream.bufferedReader().readText()
        assertEquals(0, compileProcess.waitFor(), compileOutput)

        val execution = ProcessBuilder(executable.toString()).redirectErrorStream(true).start()
        val executionOutput = execution.inputStream.bufferedReader().readText()
        assertEquals(2, execution.waitFor(), executionOutput)
        assertEquals("tick\ntick\ntick\n", executionOutput)
    }

    @Test
    fun whileBreakRunsDeferredCleanupBeforeReturning() {
        val source = """
            import { printf } from c.stdio;

            int main() {
                int value = 0;
                while (value < 5) {
                    defer printf("leaving loop\n");
                    if (value == 2) {
                        break;
                    }
                    value = value + 1;
                }
                return value;
            }
        """.trimIndent()
        val result = CPlusCompiler().compileText(Files.createTempFile("cplus-while-break", ".cp"), source)

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val directory = Files.createTempDirectory("cplus-while-break-e2e")
        val cFile = directory.resolve("program.c")
        val executable = directory.resolve("program")
        cFile.writeText(result.generatedUnits.single().text)
        val compileProcess = ProcessBuilder("cc", "-std=c17", cFile.toString(), "-o", executable.toString())
            .redirectErrorStream(true)
            .start()
        val compileOutput = compileProcess.inputStream.bufferedReader().readText()
        assertEquals(0, compileProcess.waitFor(), compileOutput)

        val execution = ProcessBuilder(executable.toString()).redirectErrorStream(true).start()
        val executionOutput = execution.inputStream.bufferedReader().readText()
        assertEquals(2, execution.waitFor(), executionOutput)
        assertEquals("leaving loop\nleaving loop\nleaving loop\n", executionOutput)
    }

    @Test
    fun compilesMultipleModulesFromOneDeclarationCatalogue() {
        val directory = Files.createTempDirectory("cplus-modules")
        val helper = directory.resolve("helpers.cp")
        val main = directory.resolve("main.cp")
        helper.writeText(
            """
                package demo.core;

                pub int add(int left, int right) {
                    return left + right;
                }
            """.trimIndent()
        )
        main.writeText(
            """
                package demo.core;
                import { add } from helpers;

                int main() {
                    return add(7, 5);
                }
            """.trimIndent()
        )

        val result = CPlusCompiler().compile(CompileRequest(listOf(main, helper)))

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        assertEquals(emptyList(), result.diagnostics)
        assertEquals(listOf("helpers", "main"), result.moduleGraph?.components?.flatMap { it.modules }?.map(ModuleId::value)?.sorted())
        assertEquals("demo.core::helpers::add", result.semanticModel!!.functions.getValue("add").symbol.qualifiedName.value)
        assertEquals(setOf("helpers", "main"), result.semanticModel!!.packageModules["demo.core"])
        val generated = result.generatedUnits.single().text
        assertTrue(generated.contains("int add(int left, int right);"))
        assertTrue(generated.contains("return add(7, 5);"))

        val cFile = directory.resolve("program.c")
        val executable = directory.resolve("program")
        cFile.writeText(generated)
        val compileProcess = ProcessBuilder("cc", "-std=c17", cFile.toString(), "-o", executable.toString())
            .redirectErrorStream(true)
            .start()
        val compileOutput = compileProcess.inputStream.bufferedReader().readText()
        assertEquals(0, compileProcess.waitFor(), compileOutput)
        val execution = ProcessBuilder(executable.toString()).redirectErrorStream(true).start()
        assertEquals(12, execution.waitFor())
    }

    @Test
    fun declarationCatalogueAllowsCircularModuleImports() {
        val directory = Files.createTempDirectory("cplus-circular-modules")
        val first = directory.resolve("first.cp")
        val second = directory.resolve("second.cp")
        first.writeText(
            """
                import { secondValue } from second;

                pub int firstValue() {
                    return secondValue();
                }

                int main() {
                    return firstValue();
                }
            """.trimIndent()
        )
        second.writeText(
            """
                import { firstValue } from first;

                pub int secondValue() {
                    return 9;
                }
            """.trimIndent()
        )

        val result = CPlusCompiler().compile(CompileRequest(listOf(first, second)))

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        assertEquals(1, result.moduleGraph?.cyclicComponents?.size)
        assertEquals(setOf("first", "second"), result.moduleGraph?.cyclicComponents?.single()?.modules?.map(ModuleId::value)?.toSet())
    }

    @Test
    fun nestedScopeReturnRunsDeferredActionsFromInnerToOuter() {
        val source = """
            import { printf } from c.stdio;

            int main() {
                defer printf("outer\n");
                {
                    defer printf("inner\n");
                    return 0;
                }
            }
        """.trimIndent()
        val result = CPlusCompiler().compileText(Files.createTempFile("cplus-nested-defer", ".cp"), source)

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val directory = Files.createTempDirectory("cplus-nested-defer-e2e")
        val cFile = directory.resolve("program.c")
        val executable = directory.resolve("program")
        cFile.writeText(result.generatedUnits.single().text)
        val compileProcess = ProcessBuilder("cc", "-std=c17", cFile.toString(), "-o", executable.toString())
            .redirectErrorStream(true)
            .start()
        val compileOutput = compileProcess.inputStream.bufferedReader().readText()
        assertEquals(0, compileProcess.waitFor(), compileOutput)
        val execution = ProcessBuilder(executable.toString()).redirectErrorStream(true).start()
        val executionOutput = execution.inputStream.bufferedReader().readText()
        assertEquals(0, execution.waitFor(), executionOutput)
        assertEquals("inner\nouter\n", executionOutput)
    }

    @Test
    fun nestedLoopsLowerBreakAndContinueExits() {
        val source = """
            int main() {
                int outer = 0;
                int total = 0;
                while (outer < 3) {
                    int inner = 0;
                    while (inner < 3) {
                        inner = inner + 1;
                        if (inner == 2) {
                            continue;
                        }
                        if (inner == 3) {
                            break;
                        }
                        total = total + 1;
                    }
                    outer = outer + 1;
                }
                return total;
            }
        """.trimIndent()
        val result = CPlusCompiler().compileText(Files.createTempFile("cplus-nested-loops", ".cp"), source)

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val directory = Files.createTempDirectory("cplus-nested-loops-e2e")
        val cFile = directory.resolve("program.c")
        val executable = directory.resolve("program")
        cFile.writeText(result.generatedUnits.single().text)
        val compileProcess = ProcessBuilder("cc", "-std=c17", cFile.toString(), "-o", executable.toString())
            .redirectErrorStream(true)
            .start()
        val compileOutput = compileProcess.inputStream.bufferedReader().readText()
        assertEquals(0, compileProcess.waitFor(), compileOutput)
        val execution = ProcessBuilder(executable.toString()).redirectErrorStream(true).start()
        assertEquals(3, execution.waitFor())
    }

    @Test
    fun importedFunctionVisibilityIsModuleLocal() {
        val directory = Files.createTempDirectory("cplus-import-visibility")
        val helper = directory.resolve("helpers.cp")
        val main = directory.resolve("main.cp")
        helper.writeText("pub int add(int left, int right) { return left + right; }")
        main.writeText(
            """
                int main() {
                    return add(1, 2);
                }
            """.trimIndent()
        )

        val result = CPlusCompiler().compile(CompileRequest(listOf(main, helper)))

        assertTrue(result.diagnostics.any { it.code == "SEM302" }, result.diagnostics.joinToString())
        assertTrue(!result.isSuccessful)
    }

    @Test
    fun importedPrivateFunctionIsRejected() {
        val directory = Files.createTempDirectory("cplus-private-import")
        val helper = directory.resolve("helpers.cp")
        val main = directory.resolve("main.cp")
        helper.writeText("int add(int left, int right) { return left + right; }")
        main.writeText(
            """
                import { add } from helpers;
                int main() { return add(1, 2); }
            """.trimIndent()
        )

        val result = CPlusCompiler().compile(CompileRequest(listOf(main, helper)))

        assertTrue(result.diagnostics.any { it.code == "SEM406" }, result.diagnostics.joinToString())
        assertTrue(!result.isSuccessful)
    }

    @Test
    fun unresolvedSelectiveImportIsDiagnosed() {
        val directory = Files.createTempDirectory("cplus-import-error")
        val helper = directory.resolve("helpers.cp")
        val main = directory.resolve("main.cp")
        helper.writeText("pub int add(int left, int right) { return left + right; }")
        main.writeText(
            """
                import { missing } from helpers;
                int main() { return 0; }
            """.trimIndent()
        )

        val result = CPlusCompiler().compile(CompileRequest(listOf(main, helper)))

        assertTrue(result.diagnostics.any { it.code == "SEM404" }, result.diagnostics.joinToString())
        assertTrue(!result.isSuccessful)
    }

    @Test
    fun repeatedSelectiveImportsProduceCollisionDiagnostic() {
        val directory = Files.createTempDirectory("cplus-import-collision")
        val helper = directory.resolve("helpers.cp")
        val main = directory.resolve("main.cp")
        helper.writeText("pub int add(int left, int right) { return left + right; }")
        main.writeText(
            """
                import { add } from helpers;
                import { add } from helpers;
                int main() { return add(1, 2); }
            """.trimIndent()
        )

        val result = CPlusCompiler().compile(CompileRequest(listOf(main, helper)))

        assertTrue(result.diagnostics.any { it.code == "SEM405" }, result.diagnostics.joinToString())
        assertTrue(!result.isSuccessful)
    }

    @Test
    fun moduleAliasResolvesQualifiedFunctionCall() {
        val directory = Files.createTempDirectory("cplus-import-alias")
        val helper = directory.resolve("helpers.cp")
        val main = directory.resolve("main.cp")
        helper.writeText("pub int add(int left, int right) { return left + right; }")
        main.writeText(
            """
                import helpers as h;
                int main() { return h.add(4, 6); }
            """.trimIndent()
        )

        val result = CPlusCompiler().compile(CompileRequest(listOf(main, helper)))

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val cFile = directory.resolve("program.c")
        val executable = directory.resolve("program")
        cFile.writeText(result.generatedUnits.single().text)
        val compileProcess = ProcessBuilder("cc", "-std=c17", cFile.toString(), "-o", executable.toString())
            .redirectErrorStream(true)
            .start()
        val compileOutput = compileProcess.inputStream.bufferedReader().readText()
        assertEquals(0, compileProcess.waitFor(), compileOutput)
        val execution = ProcessBuilder(executable.toString()).redirectErrorStream(true).start()
        assertEquals(10, execution.waitFor())
    }

    @Test
    fun unionsAndEnumsRemainValidCDeclarationsAndSupportFieldAccess() {
        val source = """
            union number {
                int integer;
                char character;
            };

            enum status {
                idle,
                ready = 2,
                done
            };

            int main() {
                union number value;
                value.integer = 6;
                enum status current = ready;
                return value.integer + current;
            }
        """.trimIndent()
        val result = CPlusCompiler().compileText(Files.createTempFile("cplus-union-enum", ".cp"), source)

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val generated = result.generatedUnits.single().text
        assertTrue(generated.contains("union number"))
        assertTrue(generated.contains("enum status"))
        assertTrue(generated.contains("ready = 2"))

        val directory = Files.createTempDirectory("cplus-union-enum-e2e")
        val cFile = directory.resolve("program.c")
        val executable = directory.resolve("program")
        cFile.writeText(generated)
        val compileProcess = ProcessBuilder("cc", "-std=c17", cFile.toString(), "-o", executable.toString())
            .redirectErrorStream(true)
            .start()
        val compileOutput = compileProcess.inputStream.bufferedReader().readText()
        assertEquals(0, compileProcess.waitFor(), compileOutput)
        val execution = ProcessBuilder(executable.toString()).redirectErrorStream(true).start()
        assertEquals(8, execution.waitFor())
    }
}
