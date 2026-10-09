package cplus.compiler

import cplus.backend.GeneratedCUnit
import cplus.backend.SourceMapping
import cplus.comptime.ComptimeEvaluationResult
import cplus.comptime.ComptimeEvaluator
import cplus.comptime.ComptimeValue
import cplus.comptime.CpxExpander
import cplus.core.Origin
import cplus.core.SourceRange
import cplus.core.SourceRepository
import cplus.semantic.SymbolKind
import cplus.semantic.Visibility
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CompilerIntegrationTest {
    @Test
    fun importsPublicComptimeAsTypedNonRuntimeBindingAndRejectsInvalidExports() {
        val directory = Files.createTempDirectory("cplus-comptime-export-bindings")
        val provider = directory.resolve("box.cp").also { path ->
            path.writeText(
                """
                    pub comptime cpx<decl> box(type T) { return { struct box_{T}_t { T value; }; }; }
                    comptime cpx<decl> privateBox(type T) { return { }; }
                """.trimIndent()
            )
        }

        fun compile(
            importedName: String,
            localFunction: Boolean = false,
            invokeAsRuntime: Boolean = false
        ): CompileResult {
            val main = directory.resolve("main.cp").also { path ->
                path.writeText(
                    """
                        import { $importedName } from "./box.cp";
                        ${if (localFunction) "int box() { return 0; }" else ""}
                        int main() { return ${if (invokeAsRuntime) "box()" else "0"}; }
                    """.trimIndent()
                )
            }
            return CPlusCompiler().compile(CompileRequest(listOf(main, provider)))
        }

        val accepted = compile("box")
        assertTrue(accepted.isSuccessful, accepted.diagnostics.joinToString())
        val model = assertNotNull(accepted.semanticModel)
        val providerModule = assertNotNull(accepted.moduleGraph?.moduleIdForPath(provider)).value
        val binding = model.comptimeFunctions.getValue(providerModule).getValue("box")
        assertEquals("decl", binding.category)
        assertEquals(listOf("type"), binding.parameterKinds)
        assertEquals(listOf("T"), binding.parameters)
        assertEquals(Visibility.PUBLIC, binding.visibility)
        assertEquals(binding, model.importedComptimeFunctions.getValue("main").getValue("box"))
        assertFalse(model.moduleFunctions["main"].orEmpty().containsKey("box"))

        val private = compile("privateBox")
        assertTrue(private.diagnostics.any { it.code == "SEM406" }, private.diagnostics.joinToString())
        val missing = compile("absent")
        assertTrue(missing.diagnostics.any { it.code == "SEM404" }, missing.diagnostics.joinToString())
        val runtimeCall = compile("box", invokeAsRuntime = true)
        assertTrue(runtimeCall.diagnostics.any { it.code == "SEM302" }, runtimeCall.diagnostics.joinToString())
        val conflict = compile("box", localFunction = true)
        assertTrue(conflict.diagnostics.any { it.code == "SEM405" }, conflict.diagnostics.joinToString())
    }

    @Test
    fun sameBasenamePathImportsKeepProviderFunctionsAndSymbolsDistinct() {
        val workspace = Files.createTempDirectory("cplus-duplicate-module-basename")
        val directory = workspace.resolve("workspace with spaces").also(Files::createDirectories)
        val first = directory.resolve("first/box.cp").also { path ->
            Files.createDirectories(path.parent)
            path.writeText("pub struct first_box_t { int value; }; pub int value() { return 11; }")
        }
        val second = directory.resolve("second/box.cp").also { path ->
            Files.createDirectories(path.parent)
            path.writeText("pub struct second_box_t { int value; }; pub int value() { return 31; }")
        }
        val main = directory.resolve("main.cp").also { path ->
            path.writeText(
                """
                    import { value as firstValue } from "./first/box.cp";
                    import { value as secondValue } from "./second/box.cp";
                    int main() { return firstValue() + secondValue(); }
                """.trimIndent()
            )
        }

        val result = CPlusCompiler().compile(CompileRequest(listOf(main, first, second)))

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val graph = assertNotNull(result.moduleGraph)
        val firstId = assertNotNull(graph.moduleIdForPath(first))
        val secondId = assertNotNull(graph.moduleIdForPath(second))
        assertNotEquals(firstId, secondId)
        assertEquals(setOf(firstId, secondId), graph.nodes.getValue(assertNotNull(graph.moduleIdForPath(main))).imports)
        val semantic = assertNotNull(result.semanticModel)
        assertEquals(firstId.value, semantic.structs.getValue("first_box_t").moduleName)
        assertEquals(secondId.value, semantic.structs.getValue("second_box_t").moduleName)
        assertNotEquals(semantic.structs.getValue("first_box_t").id, semantic.structs.getValue("second_box_t").id)
        val generated = result.generatedUnits.single().text
        val firstSymbol = "__cplus_mod_${firstId.value.replace('.', '_')}_value"
        val secondSymbol = "__cplus_mod_${secondId.value.replace('.', '_')}_value"
        assertTrue(generated.contains(firstSymbol), generated)
        assertTrue(generated.contains(secondSymbol), generated)

        val cFile = directory.resolve("main.c").also { it.writeText(generated) }
        val executable = directory.resolve("main")
        val compile = ProcessBuilder("cc", "-std=c17", cFile.toString(), "-o", executable.toString())
            .redirectErrorStream(true)
            .start()
        val compileOutput = compile.inputStream.bufferedReader().readText()
        assertEquals(0, compile.waitFor(), compileOutput)
        val execution = ProcessBuilder(executable.toString()).redirectErrorStream(true).start()
        val executionOutput = execution.inputStream.bufferedReader().readText()
        assertEquals(42, execution.waitFor(), executionOutput)
    }

    @Test
    fun traitDeclarationIsRegisteredBeforeTheBackendTraitLoweringStage() {
        val source = """
            struct counter_t { int value; };
            comptime trait counter_t {
                int read(self) { return self.value; }
            }
            int main() { return 0; }
        """.trimIndent()
        val result = CPlusCompiler().compileText(Files.createTempFile("cplus-trait-parser-stage", ".cp"), source)

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val model = assertNotNull(result.semanticModel)
        val owner = model.structs.getValue("counter_t")
        val method = model.lookupMethods(owner, "read", "<main>").single()
        assertTrue(method.isExtension)
        assertEquals("<main>", method.definingModule)
        assertEquals("counter_t", method.owner.name)
    }

    @Test
    fun extensionDefinitionsEmitTypedFunctionsForAggregateEnumAndPrimitiveReceivers() {
        val result = CPlusCompiler().compileText(
            Files.createTempFile("cplus-trait-c-emission", ".cp"),
            """
                pub struct counter_t { int value; };
                pub enum status_t { ready = 1 };
                pub comptime trait counter_t {
                    int read(self) { return self.value; }
                }
                pub comptime trait status_t {
                    int code(self) { return self; }
                }
                pub comptime trait int {
                    int doubled(self) { return self * 2; }
                }
                int main() { return 0; }
            """.trimIndent()
        )

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val generated = result.generatedUnits.single().text
        val header = result.generatedHeaders.single().text

        listOf(
            "__cplus_ext__main__counter_t_read",
            "__cplus_ext__main__status_t_code",
            "__cplus_ext__main__int_doubled"
        ).forEach { symbol ->
            assertEquals(2, Regex("\\b$symbol\\b").findAll(generated).count(), "prototype and definition for $symbol")
            assertTrue(header.contains("$symbol("), "public extension prototype $symbol")
        }
        assertTrue(generated.contains("struct counter_t* self"))
        assertTrue(generated.contains("enum status_t self"))
        assertTrue(generated.contains("int self"))
        assertFalse(generated.contains("struct int"))
    }

    @Test
    fun extensionCNamesIncludeTheDefiningModuleAcrossCyclicProviders() {
        val directory = Files.createTempDirectory("cplus-trait-provider-names")
        val types = directory.resolve("types.cp").also {
            it.writeText("pub struct point_t { int value; };")
        }
        val first = directory.resolve("first.cp").also {
            it.writeText("import {point_t} from types; import {secondValue} from second; pub int firstValue() { return secondValue(); } pub comptime trait point_t { int area(self) { return 1; } }")
        }
        val second = directory.resolve("second.cp").also {
            it.writeText("import {point_t} from types; import {firstValue} from first; pub int secondValue() { return 2; } pub comptime trait point_t { int area(self) { return 2; } }")
        }
        val main = directory.resolve("main.cp").also {
            it.writeText("import types; import first; import second; int main() { return 0; }")
        }

        val result = CPlusCompiler().compile(CompileRequest(listOf(main, first, second, types)))

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val generated = result.generatedUnits.single().text
        val firstName = "__cplus_ext_first_point_t_area"
        val secondName = "__cplus_ext_second_point_t_area"
        assertEquals(2, Regex("\\b$firstName\\b").findAll(generated).count(), generated)
        assertEquals(2, Regex("\\b$secondName\\b").findAll(generated).count(), generated)
        assertFalse(generated.contains("struct int"))
    }

    @Test
    fun aliasedReceiverTypeProducesOneExtensionDefinitionForItsCanonicalType() {
        val result = CPlusCompiler().compileText(
            Files.createTempFile("cplus-trait-alias-emission", ".cp"),
            """
                pub struct point_t { int value; };
                typedef point_t point_alias_t;
                comptime trait point_alias_t {
                    int area(self) { return self.value; }
                }
                int main() { return 0; }
            """.trimIndent()
        )

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val generated = result.generatedUnits.single().text
        assertEquals(2, Regex("\\b__cplus_ext__main__point_t_area\\b").findAll(generated).count(), generated)
        assertFalse(generated.contains("__cplus_ext__main__point_alias_t_area"), generated)
    }

    @Test
    fun extensionCallsUseResolvedReceiverAdaptationForValuesAndPointers() {
        val directory = Files.createTempDirectory("cplus-trait-call-lowering")
        val result = CPlusCompiler().compileText(
            directory.resolve("main.cp"),
            """
                struct counter_t { int value; };
                int calls;
                counter_t counter;
                counter_t* getCounter() { calls += 1; return &counter; }
                comptime trait counter_t {
                    int increment(self*) { self->value += 1; return self->value; }
                }
                comptime trait int {
                    int doubled(self) { return self * 2; }
                }
                int main() {
                    counter.value = 4;
                    counter_t* pointer = &counter;
                    int first = pointer.increment();
                    int second = getCounter().increment();
                    int scalar = 3;
                    int third = scalar.doubled();
                    return first + second + third + calls;
                }
            """.trimIndent()
        )

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val generated = result.generatedUnits.single().text
        assertTrue(generated.contains("__cplus_ext__main__counter_t_increment(pointer)"), generated)
        assertTrue(generated.contains("__cplus_ext__main__counter_t_increment(getCounter())"), generated)
        assertTrue(generated.contains("__cplus_ext__main__int_doubled(scalar)"), generated)
        val cFile = directory.resolve("main.c").also { it.writeText(generated) }
        val executable = directory.resolve("main")
        val compile = ProcessBuilder("cc", "-std=c17", cFile.toString(), "-o", executable.toString())
            .redirectErrorStream(true)
            .start()
        val compileOutput = compile.inputStream.bufferedReader().readText()
        assertEquals(0, compile.waitFor(), compileOutput)

        val execution = ProcessBuilder(executable.toString()).redirectErrorStream(true).start()
        val executionOutput = execution.inputStream.bufferedReader().readText()
        assertEquals(18, execution.waitFor(), executionOutput)
    }

    @Test
    fun cpxGeneratedExtensionExecutesClosuresAndDeferWithExpansionSourceMaps() {
        val directory = Files.createTempDirectory("cplus-trait-cpx-runtime")
        val sourceText = """
            import { puts } from c.stdio;
            comptime cpx<decl> make(type T) {
                return {
                    struct box_{T}_t { T value; };
                    comptime trait box_{T}_t {
                        int read(self) {
                            int offset = 2;
                            int addOffset(int value) { return value + offset; }
                            return addOffset(self.value);
                        }
                        char* display(self) { return "value=${'$'}{self.value}"; }
                        void increment(self*) { defer self->value += 1; }
                    }
                };
            }
            make(int);
            int main() {
                box_int_t box;
                box.value = 9;
                int before = box.read();
                box.increment();
                puts(box.display());
                return before + box.read();
            }
        """.trimIndent()
        val sourcePath = directory.resolve("main.cp")
        val result = CPlusCompiler().compileText(
            sourcePath,
            sourceText,
            target = TargetInfo(targetTriple = defaultHostTargetTriple())
        )

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val generated = result.generatedUnits.single()
        assertEquals(1, generated.text.lines().count { it.trim() == "struct box_int_t {" }, generated.text)
        val methodLine = generated.text.lines().indexOfFirst { it.contains("self->value") } + 1
        assertTrue(methodLine > 0, generated.text)
        val mapping = generated.mappingsForGeneratedLine(methodLine).firstOrNull { it.origin is Origin.Expansion }
        assertNotNull(mapping, "generated trait body must retain expansion source mapping")
        val expansion = mapping.origin as Origin.Expansion
        assertEquals(sourcePath, result.artifacts.single().source.path)
        assertTrue(expansion.definition.primaryRange != null)
        assertTrue(expansion.invocation.primaryRange != null)

        val cFile = directory.resolve("main.c").also { it.writeText(generated.text) }
        val executable = directory.resolve("main")
        val runtime = SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize()
            .parent!!.parent!!.resolve("runtime/src/format.c")
        val compile = ProcessBuilder("cc", "-std=c17", cFile.toString(), runtime.toString(), "-o", executable.toString())
            .redirectErrorStream(true)
            .start()
        val compileOutput = compile.inputStream.bufferedReader().readText()
        assertEquals(0, compile.waitFor(), compileOutput)

        val execution = ProcessBuilder(executable.toString()).redirectErrorStream(true).start()
        val executionOutput = normalizeLineEndings(execution.inputStream.bufferedReader().readText())
        assertEquals(23, execution.waitFor(), executionOutput)
        assertTrue(executionOutput.startsWith("value=10\n"), executionOutput)
    }

    @Test
    fun importedStructAliasesUnionAndEnumExtensionsExecuteAcrossModules() {
        val directory = Files.createTempDirectory("cplus-trait-imported-receivers")
        val types = directory.resolve("types.cp").also {
            it.writeText(
                """
                    pub struct point_t { int value; };
                    pub typedef point_t point_alias_t;
                    pub union packet_t { int number; };
                    pub enum status_t { ready = 1 };
                """.trimIndent()
            )
        }
        val extensions = directory.resolve("extensions.cp").also {
            it.writeText(
                """
                    import {point_alias_t, packet_t, status_t} from types;
                    pub comptime trait point_alias_t { int read(self) { return self.value; } }
                    pub comptime trait packet_t { int readNumber(self) { return self.number; } }
                    pub comptime trait status_t { int code(self) { return self; } }
                """.trimIndent()
            )
        }
        val main = directory.resolve("main.cp").also {
            it.writeText(
                """
                    import {point_alias_t as Point, packet_t, status_t} from types;
                    import extensions;
                    int main() {
                        Point point;
                        point.value = 4;
                        packet_t packet;
                        packet.number = 3;
                        status_t state = ready;
                        return point.read() + packet.readNumber() + state.code();
                    }
                """.trimIndent()
            )
        }

        val result = CPlusCompiler().compile(CompileRequest(listOf(main, extensions, types)))

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val generated = result.generatedUnits.single().text
        val cFile = directory.resolve("program.c").also { it.writeText(generated) }
        val executable = directory.resolve("program")
        val compile = ProcessBuilder("cc", "-std=c17", cFile.toString(), "-o", executable.toString())
            .redirectErrorStream(true)
            .start()
        val compileOutput = compile.inputStream.bufferedReader().readText()
        assertEquals(0, compile.waitFor(), compileOutput)

        val execution = ProcessBuilder(executable.toString()).redirectErrorStream(true).start()
        val executionOutput = execution.inputStream.bufferedReader().readText()
        assertEquals(8, execution.waitFor(), executionOutput)
    }

    @Test
    fun sameNamedCpxTraitMethodsStayBoundToTheirDistinctReceivers() {
        val directory = Files.createTempDirectory("cplus-trait-cpx-same-method")
        val source = directory.resolve("main.cp")
        val result = CPlusCompiler().compileText(
            source,
            """
                comptime cpx<decl> make(type T) {
                    return {
                        struct left_{T}_t { T value; };
                        struct right_{T}_t { T value; };
                        comptime trait left_{T}_t { int read(self) { return self.value; } }
                        comptime trait right_{T}_t { int read(self) { return self.value + 1; } }
                    };
                }
                make(int);
                int main() {
                    left_int_t left;
                    right_int_t right;
                    left.value = 2;
                    right.value = 4;
                    return left.read() + right.read();
                }
            """.trimIndent()
        )

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val generated = result.generatedUnits.single().text
        assertTrue(generated.contains("__cplus_ext__main__left_int_t_read(&left)"), generated)
        assertTrue(generated.contains("__cplus_ext__main__right_int_t_read(&right)"), generated)
        val cFile = directory.resolve("main.c").also { it.writeText(generated) }
        val executable = directory.resolve("main")
        val compile = ProcessBuilder("cc", "-std=c17", cFile.toString(), "-o", executable.toString())
            .redirectErrorStream(true)
            .start()
        val compileOutput = compile.inputStream.bufferedReader().readText()
        assertEquals(0, compile.waitFor(), compileOutput)

        val execution = ProcessBuilder(executable.toString()).redirectErrorStream(true).start()
        val executionOutput = execution.inputStream.bufferedReader().readText()
        assertEquals(7, execution.waitFor(), executionOutput)
    }

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
    fun msvcDiagnosticsMapWindowsPathsThroughGeneratedRanges() {
        val directory = Files.createTempDirectory("cplus-msvc-diagnostics")
        val repository = SourceRepository()
        val source = repository.put(directory.resolve("main.cp"), "int main() { return 0; }")
        val origin = Origin.Direct(SourceRange(source.id, 0, 3))
        val generated = GeneratedCUnit(
            "int main() {\n    return 0;\n}\n",
            listOf(SourceMapping(1, origin, 0, 12))
        )

        val diagnostic = CCompilerDiagnosticRemapper(repository).remap(
            "C:\\\\build\\\\generated.c(1,5): error C2143: syntax error",
            directory.resolve("generated.c"),
            generated
        ).single()

        assertEquals("CCOMP001", diagnostic.asDiagnostic().code)
        assertEquals(source.path, diagnostic.source!!.path)
        assertEquals(1, diagnostic.generated.line)
        assertEquals(5, diagnostic.generated.column)
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
    fun qualifiedDeclarationsReachGeneratedCWithoutLosingDeclaratorQualifiers() {
        val source = """
            const char* message;
            volatile int* const value;
            int first(const char * const input) {
                return input[0];
            }
            int main() {
                return first("ok");
            }
        """.trimIndent()
        val result = CPlusCompiler().compileText(Files.createTempFile("cplus-qualified", ".cp"), source)

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val generated = result.generatedUnits.single().text
        assertTrue(generated.contains("const char* message;"), generated)
        assertTrue(generated.contains("volatile int* const value;"), generated)
        assertTrue(generated.contains("int first(const char* const input)"), generated)
    }

    @Test
    fun functionPointerCallbacksCompileAndExecuteThroughGeneratedC() {
        val source = """
            int add_one(int value) {
                return value + 1;
            }

            int apply(int (*callback)(int value), int value) {
                return callback(value);
            }

            int main() {
                return apply(add_one, 4) == 5 ? 0 : 1;
            }
        """.trimIndent()
        val result = CPlusCompiler().compileText(Files.createTempFile("cplus-function-pointer", ".cp"), source)

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val directory = Files.createTempDirectory("cplus-function-pointer-e2e")
        val cFile = directory.resolve("program.c").also { it.writeText(result.generatedUnits.single().text) }
        val executable = directory.resolve("program")
        val compileProcess = ProcessBuilder("cc", "-std=c17", cFile.toString(), "-o", executable.toString())
            .redirectErrorStream(true)
            .start()
        val compileOutput = compileProcess.inputStream.bufferedReader().readText()
        assertEquals(0, compileProcess.waitFor(), compileOutput)
        val execution = ProcessBuilder(executable.toString()).redirectErrorStream(true).start()
        assertEquals(0, execution.waitFor())
        val generated = result.generatedUnits.single().text
        assertTrue(generated.contains("int (*callback)(int)"), generated)
        assertTrue(generated.contains("return callback(value);"), generated)
    }

    @Test
    fun pointerArithmeticAndArrayDecayCompileAndExecute() {
        val source = """
            int main() {
                int values[3];
                values[0] = 4;
                values[1] = 5;
                values[2] = 6;
                int* pointer = values;
                return *(pointer + 1) == 5 && (pointer + 2) - pointer == 2 ? 0 : 1;
            }
        """.trimIndent()
        val result = CPlusCompiler().compileText(Files.createTempFile("cplus-pointer-arithmetic", ".cp"), source)

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val directory = Files.createTempDirectory("cplus-pointer-arithmetic-e2e")
        val cFile = directory.resolve("program.c").also { it.writeText(result.generatedUnits.single().text) }
        val executable = directory.resolve("program")
        val compileProcess = ProcessBuilder("cc", "-std=c17", cFile.toString(), "-o", executable.toString())
            .redirectErrorStream(true)
            .start()
        val compileOutput = compileProcess.inputStream.bufferedReader().readText()
        assertEquals(0, compileProcess.waitFor(), compileOutput)
        val execution = ProcessBuilder(executable.toString()).redirectErrorStream(true).start()
        assertEquals(0, execution.waitFor())
    }

    @Test
    fun aggregatePointerCastsRemainValidCDeclarators() {
        val source = """
            struct item {
                int value;
            };

            int main() {
                struct item item;
                item.value = 7;
                void* raw = &item;
                struct item* typed = (struct item*) raw;
                return typed->value == 7 ? 0 : 1;
            }
        """.trimIndent()
        val result = CPlusCompiler().compileText(Files.createTempFile("cplus-aggregate-cast", ".cp"), source)

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val directory = Files.createTempDirectory("cplus-aggregate-cast-e2e")
        val cFile = directory.resolve("program.c").also { it.writeText(result.generatedUnits.single().text) }
        val executable = directory.resolve("program")
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
    fun cMathImportsExposeScalarTypesAndRealFunctionVariants() {
        val source = """
            import { float_t, double_t, frexpf, lrintf, nanf, modf, ilogb, remquo, nexttowardl, fmal, llroundl } from c.math;

            float_t preserve_float_t(float_t value) { return value; }
            double_t preserve_double_t(double_t value) { return value; }

            float use_float_math(float value) {
                int exponent;
                return frexpf(value, &exponent) + nanf("payload") + (float)lrintf(value);
            }

            double use_double_math(double value) {
                double integral;
                int quotient;
                return modf(value, &integral) + remquo(value, 2.0, &quotient) + (double)ilogb(value);
            }

            long double use_long_double_math(long double value) {
                return nexttowardl(value, value) + fmal(value, value, value) + (long double)llroundl(value);
            }
        """.trimIndent()
        val result = CPlusCompiler().compileText(Files.createTempFile("cplus-c-math-imports", ".cp"), source)

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val generated = result.generatedUnits.single().text
        assertTrue(generated.contains("#include <math.h>"), generated)
        val directory = Files.createTempDirectory("cplus-c-math-imports-syntax")
        val cFile = directory.resolve("math_imports.c").also { it.writeText(generated) }
        val sdkRoot = SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize().parent!!.parent!!
        val compile = ProcessBuilder(
            "cc", "-std=c17", "-Werror=implicit-function-declaration", "-I",
            sdkRoot.resolve("libc/include").toString(), "-fsyntax-only", cFile.toString()
        ).redirectErrorStream(true).start()
        val output = compile.inputStream.bufferedReader().readText()
        assertEquals(0, compile.waitFor(), output)
    }

    @Test
    fun stdMathDeclaresDistinctFloatDoubleAndLongDoubleEntryPoints() {
        val directory = Files.createTempDirectory("cplus-std-math-api")
        val source = directory.resolve("main.cp").also {
            it.writeText(
                """
                    import { std_math_sqrtf, std_math_sqrt, std_math_sqrtl } from std.math;
                    float sqrt_float(float value) { return std_math_sqrtf(value); }
                    double sqrt_double(double value) { return std_math_sqrt(value); }
                    long double sqrt_long_double(long double value) { return std_math_sqrtl(value); }
                """.trimIndent()
            )
        }
        val sdkRoot = SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize().parent!!.parent!!
        val result = CPlusCompiler().compile(
            CompileRequest(
                listOf(source, sdkRoot.resolve("std/src/math.cp")),
                target = TargetInfo(targetTriple = "linux-x86_64")
            )
        )

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val model = requireNotNull(result.semanticModel)
        assertEquals("float", model.functions.getValue("std_math_sqrtf").returnType.name)
        assertEquals("double", model.functions.getValue("std_math_sqrt").returnType.name)
        assertEquals("long double", model.functions.getValue("std_math_sqrtl").returnType.name)
        val generated = result.generatedUnits.joinToString("\n") { it.text }
        assertTrue("long double std_math_sqrtl(long double value)" in generated, generated)
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
    fun stdFixedWidthAliasesAreExplicitSourceTypedefsAcrossTargets() {
        val sdkRoot = SdkManifestLocator.defaultManifestPath()
            .toAbsolutePath().normalize().parent!!.parent!!
        val fixedWidthModule = sdkRoot.resolve("std/src/fixed_width.cp")
        val directory = Files.createTempDirectory("cplus-std-fixed-width")
        val main = directory.resolve("main.cp").also {
            it.writeText(
                """
                    import {
                        i8, i16, i32, i64,
                        u8, u16, u32, u64
                    } from std.fixed_width;

                    struct fixed_width_record {
                        i8 signed8;
                        i16 signed16;
                        i32 signed32;
                        i64 signed64;
                        u8 unsigned8;
                        u16 unsigned16;
                        u32 unsigned32;
                        u64 unsigned64;
                    };

                    i32 add_wide(i32 left, u64 right) {
                        return left + (i32)right;
                    }

                    int main() {
                        struct fixed_width_record record;
                        i8* signed_pointer;
                        u64* unsigned_pointer;
                        return add_wide(1, 2);
                    }
                """.trimIndent()
            )
        }
        val result = CPlusCompiler().compile(CompileRequest(listOf(main, fixedWidthModule)))

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val model = requireNotNull(result.semanticModel)
        val expectedSizes = mapOf(
            "i8" to 1, "i16" to 2, "i32" to 4, "i64" to 8,
            "u8" to 1, "u16" to 2, "u32" to 4, "u64" to 8
        )
        listOf("linux-x86_64", "linux-aarch64", "windows-x86_64", "windows-aarch64").forEach { targetName ->
            val descriptor = requireNotNull(TargetRegistry.load(sdkRoot.resolve("abi/$targetName.toml")).descriptor)
            val layouts = AbiLayoutEngine(descriptor)
            expectedSizes.forEach { (aliasName, expectedSize) ->
                assertEquals(expectedSize, layouts.layout(model.aliases.getValue(aliasName)).size, "$targetName $aliasName")
            }
        }

        val generated = result.generatedUnits.single().text
        val cTypes = mapOf(
            "i8" to "int8_t", "i16" to "int16_t", "i32" to "int32_t", "i64" to "int64_t",
            "u8" to "uint8_t", "u16" to "uint16_t", "u32" to "uint32_t", "u64" to "uint64_t"
        )
        cTypes.forEach { (alias, cType) -> assertTrue("typedef $cType $alias;" in generated, generated) }
        assertTrue("i32 add_wide(i32 left, u64 right)" in generated, generated)
        assertTrue("struct fixed_width_record" in generated, generated)
        val cFile = directory.resolve("program.c").also { it.writeText(generated) }
        val executable = directory.resolve("program")
        val compileProcess = ProcessBuilder(
            "cc", "-std=c17", "-I", sdkRoot.resolve("libc/include").toString(),
            cFile.toString(), "-o", executable.toString()
        ).redirectErrorStream(true).start()
        val compileOutput = compileProcess.inputStream.bufferedReader().readText()
        assertEquals(0, compileProcess.waitFor(), compileOutput)
        assertEquals(3, ProcessBuilder(executable.toString()).redirectErrorStream(true).start().waitFor())
    }

    @Test
    fun stdFixedWidthInt128AliasesCompileAndUseTheLinuxCAbiAcrossTranslationUnits() {
        val sdkRoot = SdkManifestLocator.defaultManifestPath()
            .toAbsolutePath().normalize().parent!!.parent!!
        val fixedWidthModule = sdkRoot.resolve("std/src/fixed_width.cp")
        val directory = Files.createTempDirectory("cplus-int128-abi")
        val main = directory.resolve("int128_main.cp").also {
            it.writeText(
                """
                    import { i128, u128, i64 } from std.fixed_width;

                    pub i128 add_signed(i128 left, i128 right) { return left + right; }
                    pub u128 add_unsigned(u128 left, u128 right) { return left + right; }
                    pub i128 convert_unsigned(u128 value) { return (i128)value; }
                    pub i64 truncate_signed(i128 value) { return (i64)value; }
                """.trimIndent()
            )
        }
        val result = CPlusCompiler().compile(CompileRequest(listOf(main, fixedWidthModule)))

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val generated = result.generatedUnits.single().text
        assertTrue("typedef __int128 i128;" in generated, generated)
        assertTrue("typedef unsigned __int128 u128;" in generated, generated)
        assertTrue("i128 add_signed(i128 left, i128 right)" in generated, generated)
        assertTrue("u128 add_unsigned(u128 left, u128 right)" in generated, generated)
        val header = result.generatedHeaders.single().text
        assertTrue("typedef __int128 i128;" in header, header)
        assertTrue("typedef unsigned __int128 u128;" in header, header)
        assertTrue("i128 add_signed(i128 left, i128 right);" in header, header)
        assertTrue("u128 add_unsigned(u128 left, u128 right);" in header, header)
        val model = requireNotNull(result.semanticModel)
        val layouts = AbiLayoutEngine(requireNotNull(result.sdkResolution?.targetDescriptor))
        assertEquals(16, layouts.layout(model.aliases.getValue("i128")).size)
        assertEquals(16, layouts.layout(model.aliases.getValue("i128")).alignment)
        assertEquals(16, layouts.layout(model.aliases.getValue("u128")).size)
        assertEquals(16, layouts.layout(model.aliases.getValue("u128")).alignment)

        val generatedFile = directory.resolve("generated.c").also { it.writeText(generated) }
        directory.resolve("generated.h").writeText(header)
        val callerFile = directory.resolve("caller.c").also {
            it.writeText(
                """
                    #include "generated.h"
                    int main(void) {
                        i128 signed_value = ((i128)1) << 100;
                        u128 unsigned_value = ((u128)1) << 120;
                        if (add_signed(signed_value, 9) != signed_value + 9) return 1;
                        if (add_unsigned(unsigned_value, 17) != unsigned_value + 17) return 2;
                        if (convert_unsigned((u128)42) != (i128)42) return 3;
                        if (truncate_signed((i128)123) != 123) return 4;
                        return 0;
                    }
                """.trimIndent()
            )
        }
        val executable = directory.resolve("int128-abi")
        val process = ProcessBuilder(
            "cc", "-std=c17", "-I", sdkRoot.resolve("libc/include").toString(),
            generatedFile.toString(), callerFile.toString(), "-o", executable.toString()
        ).redirectErrorStream(true).start()
        val compilerOutput = process.inputStream.bufferedReader().readText()
        assertEquals(0, process.waitFor(), compilerOutput)
        val execution = ProcessBuilder(executable.toString()).redirectErrorStream(true).start()
        val executionOutput = execution.inputStream.bufferedReader().readText()
        assertEquals(0, execution.waitFor(), executionOutput)
    }

    @Test
    fun int128TypesAreExplicitlyRejectedOnTargetsWithoutTheVerifiedFeature() {
        val sdkRoot = SdkManifestLocator.defaultManifestPath()
            .toAbsolutePath().normalize().parent!!.parent!!
        val fixedWidthModule = sdkRoot.resolve("std/src/fixed_width.cp")
        val directory = Files.createTempDirectory("cplus-int128-unsupported")
        val supportedWidthUse = directory.resolve("supported.cp").also {
            it.writeText("import { i64 } from std.fixed_width; int main() { i64 value; return 0; }")
        }
        val supportedWidthResult = CPlusCompiler().compile(
            CompileRequest(listOf(supportedWidthUse, fixedWidthModule), target = TargetInfo(targetTriple = "linux-aarch64"))
        )
        assertTrue(supportedWidthResult.isSuccessful, supportedWidthResult.diagnostics.joinToString())
        assertFalse("typedef __int128 i128;" in supportedWidthResult.generatedUnits.single().text)
        assertFalse("typedef unsigned __int128 u128;" in supportedWidthResult.generatedUnits.single().text)

        val importedUse = directory.resolve("imported.cp").also {
            it.writeText("import { i128 } from std.fixed_width; pub i128 pass(i128 value) { return value; }")
        }
        val importedResult = CPlusCompiler().compile(
            CompileRequest(listOf(importedUse, fixedWidthModule), target = TargetInfo(targetTriple = "linux-aarch64"))
        )
        assertTrue(importedResult.diagnostics.any { it.code == "SEM411" }, importedResult.diagnostics.joinToString())
        assertFalse(importedResult.isSuccessful)

        val builtinUse = directory.resolve("builtin.cp").also {
            it.writeText(
                """
                    __int128 pass_signed(__int128 value) { return value; }
                    unsigned __int128 pass_unsigned(unsigned __int128 value) { return value; }
                """.trimIndent()
            )
        }
        val builtinResult = CPlusCompiler().compile(
            CompileRequest(listOf(builtinUse), target = TargetInfo(targetTriple = "windows-x86_64"))
        )
        assertTrue(builtinResult.diagnostics.count { it.code == "SEM411" } >= 2, builtinResult.diagnostics.joinToString())
        assertFalse(builtinResult.isSuccessful)
    }

    @Test
    fun stdFixedWidthAliasesAreNotImplicitlyVisible() {
        val sdkRoot = SdkManifestLocator.defaultManifestPath()
            .toAbsolutePath().normalize().parent!!.parent!!
        val fixedWidthModule = sdkRoot.resolve("std/src/fixed_width.cp")
        val main = Files.createTempFile("cplus-fixed-width-no-import", ".cp").also {
            it.writeText("int main() { i8 value; return 0; }")
        }

        val result = CPlusCompiler().compile(CompileRequest(listOf(main, fixedWidthModule)))

        assertTrue(result.diagnostics.any { it.code == "SEM410" }, result.diagnostics.joinToString())
        assertTrue(!result.isSuccessful)
    }

    @Test
    fun fixedWidthAliasesDoNotFallBackOnTargetsWithoutAnSdkAbi() {
        val sdkRoot = SdkManifestLocator.defaultManifestPath()
            .toAbsolutePath().normalize().parent!!.parent!!
        val fixedWidthModule = sdkRoot.resolve("std/src/fixed_width.cp")
        val main = Files.createTempFile("cplus-fixed-width-unsupported-target", ".cp").also {
            it.writeText("import { i64 } from std.fixed_width; int main() { return 0; }")
        }

        val result = CPlusCompiler().compile(
            CompileRequest(listOf(main, fixedWidthModule), target = TargetInfo(targetTriple = "linux-riscv64"))
        )

        assertTrue(result.diagnostics.any { it.code == "SDK008" }, result.diagnostics.joinToString())
        assertTrue(!result.isSuccessful)
    }

    @Test
    fun integerSpecifierVariantsWorkAcrossDeclarationsAndGeneratedC() {
        val result = CPlusCompiler().compileText(
            Files.createTempFile("cplus-integer-specifiers", ".cp"),
            """
                typedef unsigned long long int count_t;

                struct sample_t {
                    signed char tag;
                    unsigned long long int count;
                };

                long int accumulate(int long base, long unsigned long int count) {
                    return base + (long int)count;
                }

                int main() {
                    struct sample_t sample;
                    sample.tag = (char signed)40;
                    sample.count = (int unsigned long long)2;
                    count_t copied = sample.count;
                    return (int)accumulate((long signed int)sample.tag, (unsigned long long int)copied);
                }
            """.trimIndent()
        )

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val generated = result.generatedUnits.single().text
        assertTrue(generated.contains("typedef unsigned long long count_t;"), generated)
        assertTrue(generated.contains("signed char tag;"), generated)
        assertTrue(generated.contains("unsigned long long count;"), generated)
        assertTrue(generated.contains("long accumulate(long base, unsigned long long count)"), generated)
        assertTrue(generated.contains("(signed char)40"), generated)

        val directory = Files.createTempDirectory("cplus-integer-specifiers-e2e")
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
        assertEquals(42, execution.waitFor(), executionOutput)
    }

    @Test
    fun everyCIntegerRankAndSignednessRoundTripsThroughAnIndependentCAbiCaller() {
        org.junit.jupiter.api.Assumptions.assumeTrue(
            System.getProperty("os.name").contains("linux", ignoreCase = true),
            "This fixture asserts the Linux LP64 data model"
        )
        val source = """
            pub struct parsed_integer_record {
                char plain_char;
                signed char signed_char;
                unsigned char unsigned_char;
                short signed_short;
                unsigned short unsigned_short;
                int signed_int;
                unsigned int unsigned_int;
                long signed_long;
                unsigned long unsigned_long;
                long long signed_long_long;
                unsigned long long unsigned_long_long;
            };

            pub char pass_plain_char(char value) { return value; }
            pub signed char pass_signed_char(signed char value) { return value; }
            pub unsigned char pass_unsigned_char(unsigned char value) { return value; }
            pub short signed int pass_signed_short(short signed int value) { return value; }
            pub int unsigned short pass_unsigned_short(int unsigned short value) { return value; }
            pub int signed pass_signed_int(int signed value) { return value; }
            pub unsigned int pass_unsigned_int(unsigned int value) { return value; }
            pub int long pass_signed_long(long int value) { return value; }
            pub long unsigned int pass_unsigned_long(unsigned long int value) { return value; }
            pub long int long pass_signed_long_long(long long int value) { return value; }
            pub unsigned long long int pass_unsigned_long_long(unsigned long long int value) { return value; }
        """.trimIndent()
        val result = CPlusCompiler().compileText(Files.createTempFile("cplus-primitive-abi", ".cp"), source)

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val model = requireNotNull(result.semanticModel)
        val expectedTypes = linkedMapOf(
            "pass_plain_char" to "char",
            "pass_signed_char" to "signed char",
            "pass_unsigned_char" to "unsigned char",
            "pass_signed_short" to "short",
            "pass_unsigned_short" to "unsigned short",
            "pass_signed_int" to "int",
            "pass_unsigned_int" to "unsigned int",
            "pass_signed_long" to "long",
            "pass_unsigned_long" to "unsigned long",
            "pass_signed_long_long" to "long long",
            "pass_unsigned_long_long" to "unsigned long long"
        )
        expectedTypes.forEach { (functionName, typeName) ->
            val function = model.functions.getValue(functionName)
            assertEquals(typeName, function.returnType.name, functionName)
            assertEquals(typeName, function.parameters.single().type.name, functionName)
        }
        fun canonicalId(functionName: String) =
            model.canonicalTypeId(model.functions.getValue(functionName).returnType)
        assertNotEquals(canonicalId("pass_plain_char"), canonicalId("pass_signed_char"))
        assertNotEquals(canonicalId("pass_signed_char"), canonicalId("pass_unsigned_char"))
        assertNotEquals(canonicalId("pass_signed_short"), canonicalId("pass_unsigned_short"))
        assertNotEquals(canonicalId("pass_signed_int"), canonicalId("pass_unsigned_int"))
        assertNotEquals(canonicalId("pass_signed_long"), canonicalId("pass_signed_long_long"))
        assertNotEquals(canonicalId("pass_unsigned_long"), canonicalId("pass_unsigned_long_long"))

        val directory = Files.createTempDirectory("cplus-primitive-abi-e2e")
        val header = directory.resolve("primitive_api.h").also {
            it.writeText(result.generatedHeaders.single().text)
        }
        val generated = directory.resolve("generated.c").also { it.writeText(result.generatedUnits.single().text) }
        val caller = directory.resolve("caller.c").also {
            it.writeText(
                """
                    #include <stddef.h>
                    #include "${header.fileName}"
                    _Static_assert(sizeof(long) == 8, "Linux x86_64 long ABI");
                    _Static_assert(sizeof(long long) == 8, "long long ABI");
                    _Static_assert(sizeof(struct parsed_integer_record) == 48, "record size");
                    _Static_assert(offsetof(struct parsed_integer_record, signed_short) == 4, "short offset");
                    _Static_assert(offsetof(struct parsed_integer_record, signed_int) == 8, "int offset");
                    _Static_assert(offsetof(struct parsed_integer_record, signed_long) == 16, "long offset");
                    _Static_assert(offsetof(struct parsed_integer_record, signed_long_long) == 32, "long long offset");
                    int main(void) {
                        long signed_long_value = -((long)1 << 40);
                        unsigned long unsigned_long_value = (unsigned long)1 << 55;
                        long long signed_long_long_value = -((long long)1 << 50);
                        unsigned long long unsigned_long_long_value = 1ULL << 63;
                        if (pass_plain_char('Q') != 'Q') return 1;
                        if (pass_signed_char((signed char)-97) != (signed char)-97) return 2;
                        if (pass_unsigned_char((unsigned char)250) != (unsigned char)250) return 3;
                        if (pass_signed_short((short)-12345) != (short)-12345) return 4;
                        if (pass_unsigned_short((unsigned short)60000) != (unsigned short)60000) return 5;
                        if (pass_signed_int(-1234567) != -1234567) return 6;
                        if (pass_unsigned_int(4000000001U) != 4000000001U) return 7;
                        if (pass_signed_long(signed_long_value) != signed_long_value) return 8;
                        if (pass_unsigned_long(unsigned_long_value) != unsigned_long_value) return 9;
                        if (pass_signed_long_long(signed_long_long_value) != signed_long_long_value) return 10;
                        if (pass_unsigned_long_long(unsigned_long_long_value) != unsigned_long_long_value) return 11;
                        return 0;
                    }
                """.trimIndent()
            )
        }
        val executable = directory.resolve("primitive-abi")
        val compile = ProcessBuilder(
            "cc", "-std=c17", "-I", directory.toString(), generated.toString(), caller.toString(),
            "-o", executable.toString()
        ).redirectErrorStream(true).start()
        val compilerOutput = compile.inputStream.bufferedReader().readText()
        assertEquals(0, compile.waitFor(), compilerOutput)
        val execution = ProcessBuilder(executable.toString()).redirectErrorStream(true).start()
        val executionOutput = execution.inputStream.bufferedReader().readText()
        assertEquals(0, execution.waitFor(), executionOutput)
    }

    @Test
    fun floatingPrimitivesAndLongDoubleRoundTripThroughAnIndependentCCaller() {
        val source = """
            pub struct floating_abi_record {
                float single_value;
                double double_value;
                long double extended_value;
            };

            pub float round_trip_float(float value) { return value; }
            pub double round_trip_double(double value) { return value; }
            pub long double round_trip_long_double(long double value) { return value; }
            pub floating_abi_record round_trip_floating_record(floating_abi_record value) { return value; }
        """.trimIndent()
        val result = CPlusCompiler().compileText(Files.createTempFile("cplus-floating-abi", ".cp"), source)

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val model = requireNotNull(result.semanticModel)
        assertEquals("float", model.functions.getValue("round_trip_float").returnType.name)
        assertEquals("double", model.functions.getValue("round_trip_double").returnType.name)
        assertEquals("long double", model.functions.getValue("round_trip_long_double").returnType.name)
        assertEquals("long double", model.structs.getValue("floating_abi_record").fields.last().symbol.type.name)

        val directory = Files.createTempDirectory("cplus-floating-abi-caller")
        val header = directory.resolve("floating_api.h").also { it.writeText(result.generatedHeaders.single().text) }
        val generated = directory.resolve("generated.c").also { it.writeText(result.generatedUnits.single().text) }
        val caller = directory.resolve("caller.c").also {
            it.writeText(
                """
                    #include <stddef.h>
                    #include "${header.fileName}"
                    _Static_assert(sizeof(float) == 4 && _Alignof(float) == 4, "float ABI");
                    _Static_assert(sizeof(double) == 8 && _Alignof(double) == 8, "double ABI");
                    _Static_assert(sizeof(long double) == 16 && _Alignof(long double) == 16, "long double ABI");
                    _Static_assert(sizeof(struct floating_abi_record) == 32, "aggregate size");
                    _Static_assert(offsetof(struct floating_abi_record, extended_value) == 16, "long double field offset");
                    int main(void) {
                        struct floating_abi_record value = { 1.25f, 2.5, 19.25L };
                        long double scalar = 123456789.125L;
                        if (round_trip_float(value.single_value) != value.single_value) return 1;
                        if (round_trip_double(value.double_value) != value.double_value) return 2;
                        if (round_trip_long_double(scalar) != scalar) return 3;
                        value = round_trip_floating_record(value);
                        if (value.single_value != 1.25f || value.double_value != 2.5 || value.extended_value != 19.25L) return 4;
                        return 0;
                    }
                """.trimIndent()
            )
        }
        val executable = directory.resolve("floating-abi-caller")
        val compile = ProcessBuilder(
            "cc", "-std=c17", "-I", directory.toString(), generated.toString(), caller.toString(), "-o", executable.toString()
        ).redirectErrorStream(true).start()
        val compilerOutput = compile.inputStream.bufferedReader().readText()
        assertEquals(0, compile.waitFor(), compilerOutput)
        val execution = ProcessBuilder(executable.toString()).redirectErrorStream(true).start()
        val executionOutput = execution.inputStream.bufferedReader().readText()
        assertEquals(0, execution.waitFor(), executionOutput)
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
    fun reflectiveCpxReceivesSemanticTypeIdentityAndAbiLayout() {
        var observedTypeId: cplus.semantic.TypeId? = null
        var observedSize: Long? = null
        val expander = CpxExpander(
            evaluator = ComptimeEvaluator { _, template, bindings, context ->
                val type = bindings.values.single() as ComptimeValue.CtType
                observedTypeId = type.typeId
                observedSize = type.typeId?.let { context.reflection.descriptor(it)?.layout?.size }
                ComptimeEvaluationResult(template.render(bindings))
            }
        )
        val compiler = CPlusCompiler(CompilerContext(cpxExpander = expander))
        val source = """
            struct layout_sample {
                int value;
                char marker;
            };
            comptime cpx<expr> inspect(type T) {
                return { int generated() { return 0; } };
            }
            inspect(layout_sample);
            int main() { return generated(); }
        """.trimIndent()

        val result = compiler.compileText(Files.createTempFile("cplus-reflection-layout", ".cp"), source)

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        assertTrue(observedTypeId != null)
        assertEquals(8L, observedSize)
    }

    @Test
    fun reflectiveCpxCanonicalizesAndDescribesBuiltinIntegerTypes() {
        var observedTypeName: String? = null
        var observedCanonicalSyntax: String? = null
        var observedKind: String? = null
        var observedSize: Long? = null
        val expander = CpxExpander(
            evaluator = ComptimeEvaluator { _, template, bindings, context ->
                val type = bindings.values.single() as ComptimeValue.CtType
                observedCanonicalSyntax = type.canonicalSyntax
                val descriptor = type.typeId?.let(context.reflection::descriptor)
                observedTypeName = descriptor?.name
                observedKind = descriptor?.kind
                observedSize = descriptor?.layout?.size
                ComptimeEvaluationResult(template.render(bindings))
            }
        )
        val compiler = CPlusCompiler(CompilerContext(cpxExpander = expander))
        val source = """
            comptime cpx<expr> inspect(type T) {
                return { int generated() { return 0; } };
            }
            inspect(unsigned long long int);
            int main() { return generated(); }
        """.trimIndent()

        val result = compiler.compileText(Files.createTempFile("cplus-reflection-primitive", ".cp"), source)

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        assertEquals("unsigned long long", observedCanonicalSyntax)
        assertEquals("unsigned long long", observedTypeName)
        assertEquals("primitive", observedKind)
        assertEquals(8L, observedSize)
    }

    @Test
    fun independentCCallerUsesGeneratedHeaderForScalarsAggregatesCallbacksAndVarargs() {
        val source = """
            pub struct c_api_pair {
                int left;
                int right;
            };

            @export_name("cplus_add_values") pub int add_values(int left, int right) { return left + right; }

            pub int sum_pair(struct c_api_pair pair) {
                return pair.left + pair.right;
            }

            pub struct c_api_pair make_pair(int base) {
                struct c_api_pair pair;
                pair.left = base;
                pair.right = base + 1;
                return pair;
            }

            pub int read_pointer(struct c_api_pair* pair) {
                return pair->left + pair->right;
            }

            pub int apply_callback(int (*callback)(int value), int value) {
                return callback(value);
            }

            pub int first_variadic(int value, ...) { return value; }

            pub thread_local int tls_value;
            pub int increment_tls() { tls_value = tls_value + 1; return tls_value; }
        """.trimIndent()
        val result = CPlusCompiler().compileText(Files.createTempFile("cplus-c-caller", ".cp"), source)

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val directory = Files.createTempDirectory("cplus-c-caller-e2e")
        directory.resolve("api.h").writeText(result.generatedHeaders.single().text)
        val generated = directory.resolve("api.c").also { it.writeText(result.generatedUnits.single().text) }
        val caller = directory.resolve("caller.c").also {
            it.writeText(
                """
                    #include "api.h"
                    static int triple(int value) { return value * 3; }
                    int main(void) {
                        struct c_api_pair pair = { 2, 3 };
                        struct c_api_pair made = make_pair(6);
                        return cplus_add_values(4, 5) == 9 &&
                            sum_pair(pair) == 5 &&
                            made.left == 6 && made.right == 7 &&
                            read_pointer(&pair) == 5 &&
                            apply_callback(triple, 4) == 12 &&
                            first_variadic(7, 99) == 7 &&
                            increment_tls() == 1 &&
                            increment_tls() == 2 ? 0 : 1;
                    }
                """.trimIndent()
            )
        }
        val executable = directory.resolve("caller")
        val compile = ProcessBuilder("cc", "-std=c17", "-I", directory.toString(), generated.toString(), caller.toString(), "-o", executable.toString())
            .redirectErrorStream(true)
            .start()
        val output = compile.inputStream.bufferedReader().readText()
        assertEquals(0, compile.waitFor(), output)
        assertEquals(0, ProcessBuilder(executable.toString()).redirectErrorStream(true).start().waitFor())
    }

    @Test
    fun reflectionCanDriveAGeneratedDeclarationAfterStabilization() {
        val expander = CpxExpander(
            evaluator = ComptimeEvaluator { _, _, bindings, context ->
                val type = bindings.values.single() as ComptimeValue.CtType
                val fieldCount = type.typeId
                    ?.let { context.reflection.descriptor(it) }
                    ?.fields
                    ?.size
                    ?: 0
                ComptimeEvaluationResult("int generated_field_count() { return $fieldCount; }")
            }
        )
        val compiler = CPlusCompiler(CompilerContext(cpxExpander = expander))
        val source = """
            struct reflected_record {
                int left;
                int right;
            };
            comptime cpx<expr> derive(type T) {
                return { int generated_field_count() { return 0; } };
            }
            derive(reflected_record);
            int main() { return generated_field_count() == 2 ? 0 : 1; }
        """.trimIndent()
        val result = compiler.compileText(Files.createTempFile("cplus-reflection-generated", ".cp"), source)

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val directory = Files.createTempDirectory("cplus-reflection-generated-e2e")
        val cFile = directory.resolve("program.c").also { it.writeText(result.generatedUnits.single().text) }
        val executable = directory.resolve("program")
        val compile = ProcessBuilder("cc", "-std=c17", cFile.toString(), "-o", executable.toString())
            .redirectErrorStream(true)
            .start()
        val output = compile.inputStream.bufferedReader().readText()
        assertEquals(0, compile.waitFor(), output)
        assertEquals(0, ProcessBuilder(executable.toString()).redirectErrorStream(true).start().waitFor())
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
    fun importedSourceTypesCompileAndRunThroughGeneratedC() {
        val directory = Files.createTempDirectory("cplus-imported-source-types-e2e")
        val appDirectory = Files.createDirectories(directory.resolve("app"))
        val libraryDirectory = Files.createDirectories(appDirectory.resolve("stdlib"))
        val library = libraryDirectory.resolve("io.cp").also {
            it.writeText(
                """
                    package stdlib;
                    pub struct Point { int x; };
                    pub typedef int Coord;
                    pub int pointValue() {
                        struct Point point;
                        point.x = 3;
                        return point.x;
                    }
                """.trimIndent()
            )
        }
        val main = appDirectory.resolve("main.cp").also {
            it.writeText(
                """
                    import { Coord as LocalCoord } from stdlib/io;
                    import { Point as LocalPoint, pointValue } from "./stdlib/io.cp";
                    import stdlib/io as geo;

                    int main() {
                        LocalPoint* localPoint;
                        geo.Point* qualifiedPoint;
                        LocalCoord localCoordinate = 2;
                        geo.Coord qualifiedCoordinate = 3;
                        return pointValue() + localCoordinate + qualifiedCoordinate +
                            sizeof(LocalPoint) + sizeof(geo.Point);
                    }
                """.trimIndent()
            )
        }

        val result = CPlusCompiler().compile(CompileRequest(listOf(main, library)))

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val generated = result.generatedUnits.single().text
        assertTrue(generated.contains("localPoint"), generated)
        assertTrue(generated.contains("qualifiedPoint"), generated)
        assertTrue("typedef int Coord;" in generated, generated)
        val cFile = directory.resolve("program.c").also { it.writeText(generated) }
        val executable = directory.resolve("program")
        val compileProcess = ProcessBuilder("cc", "-std=c17", cFile.toString(), "-o", executable.toString())
            .redirectErrorStream(true)
            .start()
        val compileOutput = compileProcess.inputStream.bufferedReader().readText()
        assertEquals(0, compileProcess.waitFor(), compileOutput)

        val execution = ProcessBuilder(executable.toString()).redirectErrorStream(true).start()
        val executionOutput = execution.inputStream.bufferedReader().readText()
        assertEquals(16, execution.waitFor(), executionOutput)
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
