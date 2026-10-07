package cplus.compiler

import cplus.backend.GeneratedCUnit
import cplus.backend.SourceMapping
import cplus.comptime.ComptimeValue
import cplus.core.Origin
import cplus.core.SourceRange
import cplus.core.SourceRepository
import cplus.semantic.SymbolKind
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompilerIntegrationTest {
    @Test
    fun externalCCompilerDiagnosticsMapGeneratedRangesAndRetainForeignLocations() {
        val directory = Files.createTempDirectory("cplus-c-diagnostics")
        val source = SourceRepository().let { repository ->
            val sourceFile = repository.put(directory.resolve("main.cp"), "int main() { return 0; }")
            val origin = Origin.Direct(SourceRange(sourceFile.id, 0, 3))
            val generated = GeneratedCUnit(
                "int main() {\n    return 0;\n}\n",
                listOf(SourceMapping(1, origin, 0, 12))
            )
            val diagnostics = CCompilerDiagnosticRemapper(repository).remap(
                "${directory.resolve("generated.c")}:1:5: error: expected expression\n" +
                    "${directory.resolve("helper.c")}:4:2: warning: foreign warning",
                directory.resolve("generated.c"),
                generated
            )
            Triple(repository, sourceFile, diagnostics)
        }

        assertEquals(2, source.third.size)
        assertEquals(source.second.path, source.third[0].source!!.path)
        assertEquals(SourceRange(source.second.id, 0, 3), source.third[0].sourceRange)
        assertEquals("CCOMP001", source.third[0].asDiagnostic().code)
        assertTrue(source.third[1].origin == null)
        assertEquals(4, source.third[1].generated.line)
        assertEquals("CCOMP002", source.third[1].asDiagnostic().code)
    }

    @Test
    fun cSourceDependenciesAreNormalizedAndDeduplicated() {
        val directory = Files.createTempDirectory("cplus-c-source-dependencies")
        val source = directory.resolve("main.cp").also {
            it.writeText("int main() { return 0; }")
        }
        Files.createDirectories(directory.resolve("helpers"))
        val cSource = directory.resolve("helpers").resolve("..").resolve("helper.c").also {
            it.writeText("int helper_value(void) { return 0; }")
        }

        val result = CPlusCompiler().compile(
            CompileRequest(
                listOf(source),
                cSources = listOf(cSource, cSource)
            )
        )

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        assertEquals(
            listOf(cSource.toAbsolutePath().normalize()),
            result.cSourceDependencies.map(CSourceDependency::path)
        )
    }

    @Test
    fun cLinkDependenciesPreserveLocalAndForeignLinkageKinds() {
        val directory = Files.createTempDirectory("cplus-c-link-dependencies")
        val source = directory.resolve("main.cp").also {
            it.writeText("int main() { return 0; }")
        }
        val localLibrary = directory.resolve("libhelper.a").also {
            Files.write(it, byteArrayOf())
        }

        val result = CPlusCompiler().compile(
            CompileRequest(
                listOf(source),
                cLibraries = listOf(localLibrary.toString(), "m", "-lm")
            )
        )

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        assertEquals(
            listOf(
                CLinkDependency(localLibrary.toAbsolutePath().normalize().toString(), CLinkDependencyKind.LOCAL),
                CLinkDependency("m", CLinkDependencyKind.FOREIGN)
            ),
            result.cLinkDependencies
        )
    }

    @Test
    fun missingLocalCLinkDependenciesProduceCompilerDiagnostics() {
        val directory = Files.createTempDirectory("cplus-missing-c-link")
        val source = directory.resolve("main.cp").also {
            it.writeText("int main() { return 0; }")
        }
        val missing = directory.resolve("libmissing.a")

        val result = CPlusCompiler().compile(
            CompileRequest(listOf(source), cLibraries = listOf(missing.toString()))
        )

        assertTrue(result.diagnostics.any { it.code == "CIMP003" }, result.diagnostics.joinToString())
        assertTrue(!result.isSuccessful)
    }

    @Test
    fun cSourceImplementationCanSatisfyPrototypeOnlyDeclaration() {
        val directory = Files.createTempDirectory("cplus-c-source-link")
        val source = directory.resolve("main.cp").also {
            it.writeText(
                """
                    int main() {
                        return helper_value();
                    }
                """.trimIndent()
            )
        }
        val cSource = directory.resolve("helper.c").also {
            it.writeText(
                """
                    int helper_value(void) {
                        return 12;
                    }
                """.trimIndent()
            )
        }

        val result = CPlusCompiler().compile(CompileRequest(listOf(source), cSources = listOf(cSource)))

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val generated = result.generatedUnits.single().text
        assertTrue(generated.contains("int helper_value();"))
        assertTrue(!generated.contains("int helper_value() {"))

        val executable = directory.resolve("program")
        val cFile = directory.resolve("program.c").also { it.writeText(generated) }
        val compileProcess = ProcessBuilder("cc", "-std=c17", cFile.toString(), cSource.toString(), "-o", executable.toString())
            .redirectErrorStream(true)
            .start()
        val compileOutput = compileProcess.inputStream.bufferedReader().readText()
        assertEquals(0, compileProcess.waitFor(), compileOutput)
        val execution = ProcessBuilder(executable.toString()).redirectErrorStream(true).start()
        assertEquals(12, execution.waitFor())
    }

    @Test
    fun cSourceGlobalsLowerAsExternAssignableLvalues() {
        val directory = Files.createTempDirectory("cplus-c-source-global")
        val source = directory.resolve("main.cp").also {
            it.writeText(
                """
                    int main() {
                        shared_value = shared_value + 5;
                        return shared_value;
                    }
                """.trimIndent()
            )
        }
        val cSource = directory.resolve("globals.c").also {
            it.writeText(
                """
                    int shared_value = 4;
                """.trimIndent()
            )
        }

        val result = CPlusCompiler().compile(CompileRequest(listOf(source), cSources = listOf(cSource)))

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        assertEquals(SymbolKind.FOREIGN_GLOBAL, result.semanticModel!!.foreignGlobals.getValue("shared_value").kind)
        val generated = result.generatedUnits.single().text
        assertTrue(generated.contains("extern int shared_value;"))

        val executable = directory.resolve("program")
        val cFile = directory.resolve("program.c").also { it.writeText(generated) }
        val compileProcess = ProcessBuilder("cc", "-std=c17", cFile.toString(), cSource.toString(), "-o", executable.toString())
            .redirectErrorStream(true)
            .start()
        val compileOutput = compileProcess.inputStream.bufferedReader().readText()
        assertEquals(0, compileProcess.waitFor(), compileOutput)
        val execution = ProcessBuilder(executable.toString()).redirectErrorStream(true).start()
        assertEquals(9, execution.waitFor())
    }

    @Test
    fun mutuallyReferentialAggregatePointersReceiveForwardDeclarations() {
        val source = """
            struct first {
                struct second* second;
            };

            struct second {
                struct first* first;
            };

            int main() {
                struct first first;
                struct second second;
                first.second = &second;
                second.first = &first;
                return first.second->first == &first;
            }
        """.trimIndent()

        val result = CPlusCompiler().compileText(Files.createTempFile("cplus-forward-declarations", ".cp"), source)

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val generatedUnit = result.generatedUnits.single()
        val generated = generatedUnit.text
        assertTrue(generated.contains("struct second;"))
        val firstMapping = generatedUnit.sourceMap.first { it.generatedEndOffset > it.generatedStartOffset }
        assertTrue(generatedUnit.mappingAtByteOffset(firstMapping.generatedStartOffset) != null)
        assertTrue(generatedUnit.mappingsForGeneratedLine(firstMapping.generatedLine).isNotEmpty())

        val directory = Files.createTempDirectory("cplus-forward-declarations-e2e")
        val cFile = directory.resolve("program.c").also { it.writeText(generated) }
        val executable = directory.resolve("program")
        val compileProcess = ProcessBuilder("cc", "-std=c17", cFile.toString(), "-o", executable.toString())
            .redirectErrorStream(true)
            .start()
        val compileOutput = compileProcess.inputStream.bufferedReader().readText()
        assertEquals(0, compileProcess.waitFor(), compileOutput)
        val execution = ProcessBuilder(executable.toString()).redirectErrorStream(true).start()
        assertEquals(1, execution.waitFor())
    }

    @Test
    fun byValueAggregateDependenciesAreEmittedBeforeTheirUsers() {
        val source = """
            struct container {
                struct value value;
            };

            struct value {
                int number;
            };

            int main() {
                struct container container;
                container.value.number = 7;
                return container.value.number;
            }
        """.trimIndent()

        val result = CPlusCompiler().compileText(Files.createTempFile("cplus-by-value-declarations", ".cp"), source)

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val generated = result.generatedUnits.single().text
        assertTrue(generated.indexOf("struct value {") < generated.indexOf("struct container {"))

        val directory = Files.createTempDirectory("cplus-by-value-declarations-e2e")
        val cFile = directory.resolve("program.c").also { it.writeText(generated) }
        val executable = directory.resolve("program")
        val compileProcess = ProcessBuilder("cc", "-std=c17", cFile.toString(), "-o", executable.toString())
            .redirectErrorStream(true)
            .start()
        val compileOutput = compileProcess.inputStream.bufferedReader().readText()
        assertEquals(0, compileProcess.waitFor(), compileOutput)
        val execution = ProcessBuilder(executable.toString()).redirectErrorStream(true).start()
        assertEquals(7, execution.waitFor())
    }

    @Test
    fun cyclicByValueAggregateDependenciesProduceLoweringDiagnostics() {
        val source = """
            struct left {
                struct right right;
            };

            struct right {
                struct left left;
            };

            int main() {
                return 0;
            }
        """.trimIndent()

        val result = CPlusCompiler().compileText(Files.createTempFile("cplus-by-value-cycle", ".cp"), source)

        assertTrue(result.diagnostics.any { it.code == "LOW102" }, result.diagnostics.joinToString())
        assertTrue(!result.isSuccessful)
    }

    @Test
    fun loweredMethodNameCollisionsAreDiagnosedBeforeCEmission() {
        val source = """
            struct point {
                int get(self) {
                    return 1;
                }
            };

            int point__get() {
                return 2;
            }

            int main() {
                point value;
                return value.get();
            }
        """.trimIndent()

        val result = CPlusCompiler().compileText(Files.createTempFile("cplus-c-name-collision", ".cp"), source)

        assertTrue(result.diagnostics.any { it.code == "LOW401" }, result.diagnostics.joinToString())
        assertTrue(!result.isSuccessful)
    }

    @Test
    fun invalidVoidObjectTypesAreRejectedBeforeCEmission() {
        val source = """
            void invalid_value;

            int main() {
                return 0;
            }
        """.trimIndent()

        val result = CPlusCompiler().compileText(Files.createTempFile("cplus-invalid-c-subset", ".cp"), source)

        assertTrue(result.diagnostics.any { it.code == "LOW402" }, result.diagnostics.joinToString())
        assertTrue(!result.isSuccessful)
    }

    @Test
    fun syntaxErrorExpressionsAreRejectedBeforeCEmission() {
        val source = """
            int main() {
                return (1 + );
            }
        """.trimIndent()

        val result = CPlusCompiler().compileText(Files.createTempFile("cplus-error-expression", ".cp"), source)

        assertTrue(result.diagnostics.any { it.code == "PARSE401" || it.code == "PARSE402" }, result.diagnostics.joinToString())
        assertTrue(result.generatedUnits.isEmpty())
        assertTrue(!result.isSuccessful)
    }

    @Test
    fun publicDeclarationsGenerateAVisibilityFilteredHeader() {
        val source = """
            pub struct PublicBox {
                int value;
            };

            struct PrivateBox {
                int hidden;
            };

            pub int exported(struct PublicBox box) {
                return box.value;
            }

            int private_function() {
                return 0;
            }

            pub int public_value;
            int private_value;
            typedef int private_alias;
            pub private_alias aliased_public_value;

            int main() {
                struct PublicBox box;
                box.value = 3;
                return exported(box);
            }
        """.trimIndent()

        val result = CPlusCompiler().compileText(Files.createTempFile("cplus-public-header", ".cp"), source)

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val header = result.generatedHeaders.single().text
        assertTrue(header.contains("#pragma once"))
        assertTrue(header.contains("struct PublicBox {"))
        assertTrue(header.contains("int exported(struct PublicBox box);"))
        assertTrue(header.contains("extern int public_value;"))
        assertTrue(header.contains("typedef int private_alias;"))
        assertTrue(header.contains("extern private_alias aliased_public_value;"))
        assertTrue(!header.contains("struct PrivateBox"))
        assertTrue(!header.contains("private_function"))
        assertTrue(!header.contains("private_value"))

        val directory = Files.createTempDirectory("cplus-public-header-e2e")
        val headerFile = directory.resolve("public.h").also { it.writeText(header) }
        val headerCheck = ProcessBuilder("cc", "-std=c17", "-fsyntax-only", headerFile.toString())
            .redirectErrorStream(true)
            .start()
        val headerOutput = headerCheck.inputStream.bufferedReader().readText()
        assertEquals(0, headerCheck.waitFor(), headerOutput)
    }

    @Test
    fun publicHeadersPreservePrivateSystemDependencyBoundaries() {
        val source = """
            import { FILE } from c.stdio;

            FILE* private_handle;

            pub int exported_value;

            int main() {
                return exported_value;
            }
        """.trimIndent()

        val result = CPlusCompiler().compileText(Files.createTempFile("cplus-public-dependency-boundary", ".cp"), source)

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val implementation = result.generatedUnits.single().text
        val header = result.generatedHeaders.single().text
        assertTrue(implementation.contains("#include <stdio.h>"))
        assertTrue(!header.contains("#include <stdio.h>"))
        assertTrue(header.contains("extern int exported_value;"))
        assertTrue(!header.contains("private_handle"))
    }

    @Test
    fun foreignSourceTypesContributeRequiredSystemIncludes() {
        val directory = Files.createTempDirectory("cplus-foreign-source-types")
        val source = directory.resolve("main.cp").also {
            it.writeText(
                """
                    int main() {
                        return helper_size() == 3 ? 3 : 0;
                    }
                """.trimIndent()
            )
        }
        val cSource = directory.resolve("helper.c").also {
            it.writeText(
                """
                    #include <stddef.h>
                    size_t helper_size(void) {
                        return 3;
                    }
                """.trimIndent()
            )
        }

        val result = CPlusCompiler().compile(CompileRequest(listOf(source), cSources = listOf(cSource)))

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val generated = result.generatedUnits.single().text
        assertTrue(generated.contains("#include <stddef.h>"))

        val executable = directory.resolve("program")
        val cFile = directory.resolve("program.c").also { it.writeText(generated) }
        val compileProcess = ProcessBuilder("cc", "-std=c17", cFile.toString(), cSource.toString(), "-o", executable.toString())
            .redirectErrorStream(true)
            .start()
        val compileOutput = compileProcess.inputStream.bufferedReader().readText()
        assertEquals(0, compileProcess.waitFor(), compileOutput)
        val execution = ProcessBuilder(executable.toString()).redirectErrorStream(true).start()
        assertEquals(3, execution.waitFor())
    }

    @Test
    fun importedForeignTypesRetainCNamesAndHeaders() {
        val source = """
            import { FILE } from c.stdio;
            import { EOF } from c.stdio;
            import { SEEK_SET } from c.stdio;
            import { size_t } from c.stddef;

            FILE* output;
            size_t length;

            int main() {
                return EOF - EOF;
            }
        """.trimIndent()
        val result = CPlusCompiler().compileText(Files.createTempFile("cplus-foreign-types", ".cp"), source)

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val generated = result.generatedUnits.single().text
        assertTrue(generated.contains("#include <stddef.h>"))
        assertTrue(generated.contains("#include <stdio.h>"))
        assertTrue(generated.contains("FILE* output;"))
        assertTrue(generated.contains("size_t length;"))
        assertTrue(generated.contains("return (EOF - EOF);"))
        assertEquals("EOF", result.semanticModel!!.foreignGlobals.getValue("EOF").externalName)
        assertEquals(SymbolKind.FOREIGN_ENUM_VALUE, result.semanticModel!!.foreignGlobals.getValue("SEEK_SET").kind)

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
    fun configuredHeaderDeclarationsResolveForeignFunctionSignatures() {
        val source = """
            import { puts } from c.stdio;

            int main() {
                puts("header adapter");
                return 0;
            }
        """.trimIndent()
        val result = CPlusCompiler().compileText(Files.createTempFile("cplus-header-import", ".cp"), source)

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        assertEquals("puts", result.semanticModel!!.functions.getValue("puts").symbol.externalName)
        val directory = Files.createTempDirectory("cplus-header-import-e2e")
        val cFile = directory.resolve("program.c")
        val executable = directory.resolve("program")
        cFile.writeText(result.generatedUnits.single().text)
        val compileProcess = ProcessBuilder("cc", "-std=c17", cFile.toString(), "-o", executable.toString())
            .redirectErrorStream(true)
            .start()
        val compileOutput = compileProcess.inputStream.bufferedReader().readText()
        assertEquals(0, compileProcess.waitFor(), compileOutput)

        val execution = ProcessBuilder(executable.toString()).redirectErrorStream(true).start()
        assertEquals(0, execution.waitFor())
        assertEquals("header adapter\n", normalizeLineEndings(execution.inputStream.bufferedReader().readText()))
    }

    @Test
    fun standardCHeaderImportsResolveAcrossLibraryModules() {
        val source = """
            import { strlen } from c.string;
            import { isdigit } from c.ctype;
            import { abs } from c.stdlib;
            import { sqrt } from c.math;

            int main() {
                return strlen("123") == 3 && isdigit('1') && abs(-4) == 4 && sqrt(4.0) == 2.0 ? 0 : 1;
            }
        """.trimIndent()
        val result = CPlusCompiler().compileText(Files.createTempFile("cplus-standard-c-imports", ".cp"), source)

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val generated = result.generatedUnits.single().text
        assertTrue(generated.contains("#include <ctype.h>"))
        assertTrue(generated.contains("#include <math.h>"))
        assertTrue(generated.contains("#include <stdlib.h>"))
        assertTrue(generated.contains("#include <string.h>"))

        val directory = Files.createTempDirectory("cplus-standard-c-imports-e2e")
        val cFile = directory.resolve("program.c").also { it.writeText(generated) }
        val executable = directory.resolve("program")
        val compileProcess = ProcessBuilder("cc", "-std=c17", cFile.toString(), "-lm", "-o", executable.toString())
            .redirectErrorStream(true)
            .start()
        val compileOutput = compileProcess.inputStream.bufferedReader().readText()
        assertEquals(0, compileProcess.waitFor(), compileOutput)
        assertEquals(0, ProcessBuilder(executable.toString()).redirectErrorStream(true).start().waitFor())
    }

    @Test
    fun runtimeStringTemplatesLowerThroughFormattingHelper() {
        val source = """
            import { puts } from c.stdio;

            int main() {
                int value = 7;
                puts("value=${'$'}{value}");
                return 0;
            }
        """.trimIndent()
        val result = CPlusCompiler().compileText(Files.createTempFile("cplus-string-template", ".cp"), source)

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        assertEquals(listOf("__cplus_format"), result.artifacts.single().lowered!!.unit.runtimeDependencies)
        val generated = result.generatedUnits.single().text
        assertTrue(!generated.contains("#include <stdarg.h>"))
        assertTrue(generated.contains("const char* __cplus_format"))
        assertTrue(!generated.contains("${'$'}{value}"))

        val directory = Files.createTempDirectory("cplus-string-template-e2e")
        val cFile = directory.resolve("program.c")
        val executable = directory.resolve("program")
        cFile.writeText(generated)
        val runtime = SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize().parent!!.parent!!.resolve("runtime/src/format.c")
        val compileProcess = ProcessBuilder("cc", "-std=c17", cFile.toString(), runtime.toString(), "-o", executable.toString())
            .redirectErrorStream(true)
            .start()
        val compileOutput = compileProcess.inputStream.bufferedReader().readText()
        assertEquals(0, compileProcess.waitFor(), compileOutput)
        val execution = ProcessBuilder(executable.toString()).redirectErrorStream(true).start()
        assertEquals(0, execution.waitFor())
        assertEquals("value=7\n", normalizeLineEndings(execution.inputStream.bufferedReader().readText()))
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
                return values[1];
            }

            int main() {
                int local[2];
                local[0] = 1;
                local[1] = 4;
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
        assertTrue(generated.contains("return values[1];"))

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
    fun assignmentsUpdatesAndConditionalExpressionsLowerToC() {
        val source = """
            int main() {
                int value = 1;
                value += 2;
                ++value;
                value--;
                return value >= 3 ? value : 0;
            }
        """.trimIndent()
        val result = CPlusCompiler().compileText(Files.createTempFile("cplus-expressions", ".cp"), source)

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val generated = result.generatedUnits.single().text
        assertTrue(generated.contains("(value += 2)"))
        assertTrue(generated.contains("++value"))
        assertTrue(generated.contains("value--"))
        assertTrue(generated.contains("? value : 0"))

        val directory = Files.createTempDirectory("cplus-expressions-e2e")
        val cFile = directory.resolve("program.c")
        val executable = directory.resolve("program")
        cFile.writeText(generated)
        val compileProcess = ProcessBuilder("cc", "-std=c17", cFile.toString(), "-o", executable.toString())
            .redirectErrorStream(true)
            .start()
        val compileOutput = compileProcess.inputStream.bufferedReader().readText()
        assertEquals(0, compileProcess.waitFor(), compileOutput)
        val execution = ProcessBuilder(executable.toString()).redirectErrorStream(true).start()
        assertEquals(3, execution.waitFor())
    }

    @Test
    fun floatingPointLiteralsRetainTheirCSpelling() {
        val source = """
            double scale(double value) {
                return value * 1.5e1;
            }

            int main() {
                return scale(2.0) == 30.0 ? 0 : 1;
            }
        """.trimIndent()
        val result = CPlusCompiler().compileText(Files.createTempFile("cplus-float", ".cp"), source)

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val generated = result.generatedUnits.single().text
        assertTrue(generated.contains("1.5e1"))
        assertTrue(generated.contains("2.0"))
        assertTrue(generated.contains("30.0"))

        val directory = Files.createTempDirectory("cplus-float-e2e")
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
    fun sizeofExpressionsLowerAsNativeCOperators() {
        val source = """
            struct item {
                int value;
            };

            int main() {
                struct item item;
                int size = sizeof(int);
                return size > 0 && sizeof(struct item) > 0 && sizeof(item) > 0 ? 0 : 1;
            }
        """.trimIndent()
        val result = CPlusCompiler().compileText(Files.createTempFile("cplus-sizeof", ".cp"), source)

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val generated = result.generatedUnits.single().text
        assertTrue(generated.contains("sizeof(item)"))
        assertTrue(generated.contains("sizeof(int)"))
        assertTrue(generated.contains("sizeof(struct item)"))

        val directory = Files.createTempDirectory("cplus-sizeof-e2e")
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
    fun primitiveCastsLowerToExplicitCasts() {
        val source = """
            int main() {
                double value = 3.75;
                return (int)value;
            }
        """.trimIndent()
        val result = CPlusCompiler().compileText(Files.createTempFile("cplus-cast", ".cp"), source)

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val generated = result.generatedUnits.single().text
        assertTrue(generated.contains("(int)value"))

        val directory = Files.createTempDirectory("cplus-cast-e2e")
        val cFile = directory.resolve("program.c")
        val executable = directory.resolve("program")
        cFile.writeText(generated)
        val compileProcess = ProcessBuilder("cc", "-std=c17", cFile.toString(), "-o", executable.toString())
            .redirectErrorStream(true)
            .start()
        val compileOutput = compileProcess.inputStream.bufferedReader().readText()
        assertEquals(0, compileProcess.waitFor(), compileOutput)
        val execution = ProcessBuilder(executable.toString()).redirectErrorStream(true).start()
        assertEquals(3, execution.waitFor())
    }

    @Test
    fun pointerMemberAssignmentsLowerThroughArrowSyntax() {
        val source = """
            struct item {
                int value;
            };

            int main() {
                struct item item;
                struct item* pointer = &item;
                pointer->value = 9;
                return item.value;
            }
        """.trimIndent()
        val result = CPlusCompiler().compileText(Files.createTempFile("cplus-arrow", ".cp"), source)

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val generated = result.generatedUnits.single().text
        assertTrue(generated.contains("pointer->value"))

        val directory = Files.createTempDirectory("cplus-arrow-e2e")
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
    fun fixedWidthCPrimitiveSpellingsReachGeneratedC() {
        val result = CPlusCompiler().compileText(
            Files.createTempFile("cplus-fixed-width", ".cp"),
            """
                long long add_wide(long long left, unsigned long long right) {
                    return left + (long long) right;
                }

                int main() {
                    return (int) add_wide((long long) 1, (unsigned long long) 2);
                }
            """.trimIndent()
        )

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val generated = result.generatedUnits.single().text
        assertTrue(generated.contains("long long add_wide"), generated)
        assertTrue(generated.contains("unsigned long long right"), generated)
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
    fun pointerReceiversCanReadAndMutateTheUnderlyingObject() {
        val source = """
            struct counter_t {
                int value;

                void increment(self*) {
                    self->value = self->value + 1;
                }

                int read(self*) {
                    return self->value;
                }
            };

            int main() {
                counter_t counter;
                counter.value = 41;
                counter_t* pointer = &counter;
                pointer.increment();
                return pointer.read();
            }
        """.trimIndent()
        val result = CPlusCompiler().compileText(Files.createTempFile("cplus-pointer-receiver", ".cp"), source)

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val generated = result.generatedUnits.single().text
        assertTrue(generated.contains("self->value = (self->value + 1)"))
        assertTrue(generated.contains("counter_t__increment(pointer)"))
        assertTrue(generated.contains("counter_t__read(pointer)"))

        val directory = Files.createTempDirectory("cplus-pointer-receiver-e2e")
        val cFile = directory.resolve("program.c").also { it.writeText(generated) }
        val executable = directory.resolve("program")
        val compileProcess = ProcessBuilder("cc", "-std=c17", cFile.toString(), "-o", executable.toString())
            .redirectErrorStream(true)
            .start()
        val compileOutput = compileProcess.inputStream.bufferedReader().readText()
        assertEquals(0, compileProcess.waitFor(), compileOutput)
        assertEquals(42, ProcessBuilder(executable.toString()).redirectErrorStream(true).start().waitFor())
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
    fun scalarAndExpressionCpxArgumentsReachTheCBackend() {
        val source = """
            comptime cpx<decl> make(expr E, int N) {
                return {
                    int generated_{N}() {
                        return E + N;
                    }
                };
            }

            make(1 + 2, 4);

            int main() {
                return generated_4() == 7 ? 0 : 1;
            }
        """.trimIndent()
        val result = CPlusCompiler().compileText(Files.createTempFile("cplus-cpx-values", ".cp"), source)

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val generated = result.generatedUnits.single().text
        assertTrue(generated.contains("int generated_4()"))
        assertTrue(generated.contains("return ((1 + 2) + 4);"))

        val directory = Files.createTempDirectory("cplus-cpx-values-e2e")
        val cFile = directory.resolve("program.c").also { it.writeText(generated) }
        val executable = directory.resolve("program")
        val compileProcess = ProcessBuilder("cc", "-std=c17", cFile.toString(), "-o", executable.toString())
            .redirectErrorStream(true)
            .start()
        val compileOutput = compileProcess.inputStream.bufferedReader().readText()
        assertEquals(0, compileProcess.waitFor(), compileOutput)
        assertEquals(0, ProcessBuilder(executable.toString()).redirectErrorStream(true).start().waitFor())
    }

    @Test
    fun cpxTypeArgumentsResolveAliasesAndInterpolateCanonicalSyntax() {
        val source = """
            typedef int count_t;

            comptime cpx<decl> make(type T) {
                return {
                    int generated_{T}() { return 1; }
                };
            }

            make(count_t);

            int main() {
                return generated_int() == 1 ? 0 : 1;
            }
        """.trimIndent()
        val result = CPlusCompiler().compileText(Files.createTempFile("cplus-cpx-type", ".cp"), source)

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val generated = result.generatedUnits.single().text
        assertTrue(generated.contains("int generated_int()"), generated)
        assertTrue(!generated.contains("generated_count_t"), generated)
    }

    @Test
    fun expressionCpxArgumentsRetainResolvedSymbolIdentity() {
        val source = """
            int value;

            comptime cpx<decl> make(expr E) {
                return {
                    int generated() { return E; }
                };
            }

            make(value);

            int main() {
                return generated();
            }
        """.trimIndent()
        val result = CPlusCompiler().compileText(Files.createTempFile("cplus-cpx-reference", ".cp"), source)

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val expression = result.artifacts.single().expanded!!.argumentValues.values
            .flatten()
            .filterIsInstance<ComptimeValue.CtExpression>()
            .single()
        val valueSymbol = result.semanticModel!!.symbolNamed("value")!!
        val nodeId = expression.nodeId
        assertEquals(valueSymbol.id, expression.references[nodeId])
        assertTrue(expression.origin is Origin.Generated)
        assertEquals(expression, result.artifacts.single().expanded!!.argumentValues.values.flatten().single())
    }

    @Test
    fun injectedCpxDeclarationNamesUseOrdinaryCollisionDiagnostics() {
        val source = """
            comptime cpx<decl> make() {
                return { int exported() { return 0; } };
            }

            make();
            int exported() { return 1; }
            int main() { return exported(); }
        """.trimIndent()
        val result = CPlusCompiler().compileText(Files.createTempFile("cplus-cpx-collision", ".cp"), source)

        assertTrue(result.diagnostics.any { it.code == "SEM002" }, result.diagnostics.joinToString())
        assertTrue(!result.isSuccessful)
    }

    @Test
    fun booleanLiteralsAndBoolCpxArgumentsLowerToPortableC() {
        val source = """
            comptime cpx<decl> make(bool B) {
                return {
                    int generated() {
                        return B ? 1 : 0;
                    }
                };
            }

            make(false);

            int main() {
                return generated();
            }
        """.trimIndent()
        val result = CPlusCompiler().compileText(Files.createTempFile("cplus-bool", ".cp"), source)

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val generated = result.generatedUnits.single().text
        assertTrue(generated.contains("return (0 ? 1 : 0);"))

        val directory = Files.createTempDirectory("cplus-bool-e2e")
        val cFile = directory.resolve("program.c").also { it.writeText(generated) }
        val executable = directory.resolve("program")
        val compileProcess = ProcessBuilder("cc", "-std=c17", cFile.toString(), "-o", executable.toString())
            .redirectErrorStream(true)
            .start()
        val compileOutput = compileProcess.inputStream.bufferedReader().readText()
        assertEquals(0, compileProcess.waitFor(), compileOutput)
        assertEquals(0, ProcessBuilder(executable.toString()).redirectErrorStream(true).start().waitFor())
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
        assertEquals("working\nleaving main\n", normalizeLineEndings(executionOutput))
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
        assertEquals("tick\ntick\ntick\n", normalizeLineEndings(executionOutput))
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
        assertEquals("leaving loop\nleaving loop\nleaving loop\n", normalizeLineEndings(executionOutput))
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
    fun resolvesPathImportsPackagePathsAndSelectiveFunctionAliases() {
        val directory = Files.createTempDirectory("cplus-import-targets")
        val helper = directory.resolve("module_helpers.cp").also {
            it.writeText(
                """
                    pub int add(int left, int right) {
                        return left + right;
                    }
                """.trimIndent()
            )
        }
        val io = directory.resolve("io.cp").also {
            it.writeText(
                """
                    package stdlib;
                    pub int fs() {
                        return 3;
                    }
                """.trimIndent()
            )
        }
        val pathMain = directory.resolve("path_main.cp").also {
            it.writeText(
                """
                    import { add as sum } from "./module_helpers.cp";

                    int main() {
                        return sum(7, 5);
                    }
                """.trimIndent()
            )
        }
        val packageMain = directory.resolve("package_main.cp").also {
            it.writeText(
                """
                    import { fs as fs1 } from stdlib/io;

                    int main() {
                        return fs1();
                    }
                """.trimIndent()
            )
        }

        val pathResult = CPlusCompiler().compile(CompileRequest(listOf(pathMain, helper)))
        val packageResult = CPlusCompiler().compile(CompileRequest(listOf(packageMain, io)))

        assertTrue(pathResult.isSuccessful, pathResult.diagnostics.joinToString())
        assertTrue(packageResult.isSuccessful, packageResult.diagnostics.joinToString())
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
    fun unresolvedBindingInCircularImportGetsCycleDiagnostic() {
        val directory = Files.createTempDirectory("cplus-circular-import-error")
        val first = directory.resolve("first.cp")
        val second = directory.resolve("second.cp")
        first.writeText(
            """
                import { missing } from second;
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

        assertTrue(result.diagnostics.any { it.code == "MOD201" }, result.diagnostics.joinToString())
        assertTrue(result.diagnostics.any { it.message.contains("first") && it.message.contains("second") })
        assertTrue(!result.isSuccessful)
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
        assertEquals("inner\nouter\n", normalizeLineEndings(executionOutput))
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
    private fun normalizeLineEndings(value: String): String =
        value.replace("\r\n", "\n").replace('\r', '\n')
}
