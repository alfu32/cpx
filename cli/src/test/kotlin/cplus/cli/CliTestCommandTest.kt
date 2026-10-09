package cplus.cli

import cplus.compiler.LibcProfile
import cplus.compiler.RuntimeProfile
import cplus.compiler.CompilationMode
import cplus.compiler.CompileRequest
import cplus.compiler.CPlusCompiler
import cplus.core.Origin
import java.nio.file.Files
import java.nio.file.Path
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CliTestCommandTest {
    @Test
    fun parsesOrderedNormalizedDeduplicatedRootsAndPathsWithSpaces() {
        val directory = Files.createTempDirectory("cplus test cli")
        val first = Files.writeString(directory.resolve("first root.cp"), "int main() { return 0; }")
        val second = Files.writeString(directory.resolve("second.cp"), "int main() { return 0; }")

        val parsed = TestCommandArguments.parse(
            listOf("first root.cp", "./first root.cp", "second.cp"),
            workingDirectory = directory
        )

        assertEquals(listOf(first.toAbsolutePath().normalize(), second.toAbsolutePath().normalize()), parsed?.roots)
        assertEquals(30, parsed?.timeoutSeconds)
    }

    @Test
    fun delimiterAllowsOptionLookingRootAndQuotedWildcardIsNeverExpanded() {
        val directory = Files.createTempDirectory("cplus-test-cli-glob")
        val literalGlob = Files.writeString(directory.resolve("*.cp"), "int main() { return 0; }")
        val other = Files.writeString(directory.resolve("other.cp"), "int main() { return 0; }")

        val glob = TestCommandArguments.parse(listOf("*.cp"), directory)
        val afterDelimiter = TestCommandArguments.parse(listOf("--", "*.cp", "other.cp"), directory)

        assertEquals(listOf(literalGlob.toAbsolutePath().normalize()), glob?.roots)
        assertEquals(listOf(literalGlob.toAbsolutePath().normalize(), other.toAbsolutePath().normalize()), afterDelimiter?.roots)
    }

    @Test
    fun parsesTimeoutSharedSdkAndTargetConfiguration() {
        val directory = Files.createTempDirectory("cplus-test-cli-config")
        val root = Files.writeString(directory.resolve("main.cp"), "int main() { return 0; }")
        val parsed = TestCommandArguments.parse(
            listOf(
                "main.cp", "--timeout", "9", "--sdk", "sdk/manifest/sdk.toml", "--target", "linux-x86_64",
                "--runtime", "freestanding", "--libc", "none", "--c-compiler", "clang",
                "--sysroot", "sysroot", "-I", "include", "--c-source", "shim.c", "-lm"
            ),
            directory
        )

        assertEquals(listOf(root.toAbsolutePath().normalize()), parsed?.roots)
        assertEquals(9, parsed?.timeoutSeconds)
        assertEquals(directory.resolve("sdk/manifest/sdk.toml").toAbsolutePath().normalize(), parsed?.sdkManifest)
        assertEquals(directory.resolve("sysroot").toAbsolutePath().normalize(), parsed?.externalSysroot)
        assertEquals("linux-x86_64", parsed?.target?.targetTriple)
        assertEquals(RuntimeProfile.FREESTANDING, parsed?.target?.buildProfile?.runtime)
        assertEquals(LibcProfile.NONE, parsed?.target?.buildProfile?.libc)
        assertEquals("clang", parsed?.cCompiler)
        assertEquals(listOf("m"), parsed?.libraries)
    }

    @Test
    fun rejectsMissingRootsFilesInvalidTimeoutAndOutputOptions() {
        val directory = Files.createTempDirectory("cplus-test-cli-invalid")
        val root = Files.writeString(directory.resolve("main.cp"), "int main() { return 0; }")

        assertNull(TestCommandArguments.parse(emptyList(), directory))
        assertNull(TestCommandArguments.parse(listOf("missing.cp"), directory))
        assertNull(TestCommandArguments.parse(listOf("'*.cp'"), directory))
        assertNull(TestCommandArguments.parse(listOf(root.toString(), "--timeout", "0"), directory))
        assertNull(TestCommandArguments.parse(listOf(root.toString(), "--timeout", "1.5"), directory))
        assertNull(TestCommandArguments.parse(listOf(root.toString(), "--output", "out"), directory))
        assertNull(TestCommandArguments.parse(listOf(root.toString(), "--mystery"), directory))
    }

    @Test
    fun projectConfigurationDoesNotInsertAnImplicitRoot() {
        val directory = Files.createTempDirectory("cplus-test-cli-project")
        val manifest = Files.writeString(directory.resolve("cplus.toml"), "[project]\nentry = \"src/main.cp\"\n")
        val root = Files.writeString(directory.resolve("explicit.cp"), "int main() { return 0; }")

        val parsed = TestCommandArguments.parse(listOf("explicit.cp", "--project", "cplus.toml"), directory)

        assertEquals(listOf(root.toAbsolutePath().normalize()), parsed?.roots)
        assertEquals(manifest.toAbsolutePath().normalize(), parsed?.projectManifest)
        assertTrue(parsed?.workspaceManifest == null)
    }

    @Test
    fun buildsIndependentProductsForRootsWithDuplicateMainAndDoesNotSelectImportedFixtures() {
        val directory = Files.createTempDirectory("cplus-cli-test-roots")
        val provider = Files.writeString(
            directory.resolve("provider.cp"),
            "pub int helper() { return 1; } test imported fixture { assert(1); }"
        )
        val first = Files.writeString(
            directory.resolve("first.cp"),
            "import { helper } from \"./provider.cp\"; int main() { return 0; } test first fixture { assert(helper()); }"
        )
        val second = Files.writeString(
            directory.resolve("second.cp"),
            "int main() { return 0; } test second fixture { assert(1); }"
        )
        val broken = Files.writeString(directory.resolve("broken.cp"), "int main( {")
        val capturedOut = ByteArrayOutputStream()
        val capturedErr = ByteArrayOutputStream()
        val originalOut = System.out
        val originalErr = System.err
        val arguments = requireNotNull(TestCommandArguments.parse(listOf(broken.toString(), first.toString(), second.toString())))
        val temporary = Files.createTempDirectory("cplus-test-products")
        val builds = try {
            System.setOut(PrintStream(capturedOut))
            System.setErr(PrintStream(capturedErr))
            Cli().buildTestProducts(arguments, temporary)
        } finally {
            System.setOut(originalOut)
            System.setErr(originalErr)
            Files.walk(temporary).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }

        assertEquals(listOf(broken, first, second), builds.map { it.root })
        assertTrue(builds[0].exitCode != 0, capturedErr.toString())
        assertEquals(0, builds[1].exitCode, "expected a successful root build: ${capturedOut}; ${capturedErr}")
        assertEquals(0, builds[2].exitCode, "expected a successful root build: ${capturedOut}; ${capturedErr}")
        assertEquals(1, builds[1].fixtures.size, "imported fixture was incorrectly selected")
        assertEquals(1, builds[2].fixtures.size)
        assertTrue(Files.isRegularFile(provider))
    }

    @Test
    fun testCommandRunsFixturesAndReturnsFailureForFailedAssertions() {
        val directory = Files.createTempDirectory("cplus test command")
        val source = Files.writeString(
            directory.resolve("fixture root.cp"),
            "int main() { return 0; } test cli fixture { assert(1); assert(\"expected failure\", 0); }"
        )
        val capturedOut = ByteArrayOutputStream()
        val capturedErr = ByteArrayOutputStream()
        val originalOut = System.out
        val originalErr = System.err
        val status = try {
            System.setOut(PrintStream(capturedOut))
            System.setErr(PrintStream(capturedErr))
            Cli().run(listOf("test", source.toString()))
        } finally {
            System.setOut(originalOut)
            System.setErr(originalErr)
        }

        assertEquals(1, status, capturedErr.toString())
        assertTrue(capturedOut.toString().contains("cli fixture"), capturedOut.toString())
        assertTrue(capturedOut.toString().contains("expected failure"), capturedOut.toString())
        assertTrue(capturedOut.toString().contains("asserts passed 1 / failed 1 / total 2"), capturedOut.toString())
        assertTrue(capturedOut.toString().contains("::: ${source}: passed 1 / failed 1 / total 2; errors 0"), capturedOut.toString())
        assertTrue(capturedOut.toString().contains("::: total: passed 1 / failed 1 / total 2; errors 0"), capturedOut.toString())
        assertTrue(capturedErr.toString().isEmpty(), capturedErr.toString())
    }

    @Test
    fun reportSeparatesUnterminatedUserOutputAndMarksEmptyFixture() {
        val directory = Files.createTempDirectory("cplus-test-output-boundary")
        val source = Files.writeString(
            directory.resolve("output.cp"),
            "import { printf } from c.stdio; int main() { return 0; } test empty fixture { printf(\"unterminated\"); }"
        )
        val capturedOut = ByteArrayOutputStream()
        val originalOut = System.out
        val status = try {
            System.setOut(PrintStream(capturedOut))
            Cli().run(listOf("test", source.toString()))
        } finally {
            System.setOut(originalOut)
        }

        assertEquals(0, status, capturedOut.toString())
        assertTrue(capturedOut.toString().contains("unterminated\n... EMPTY"), capturedOut.toString())
        assertTrue(capturedOut.toString().contains("passed 0 / failed 0 / total 0; errors 0"), capturedOut.toString())
    }

    @Test
    fun rejectsNonNativeTestExecutionTargetExplicitly() {
        val directory = Files.createTempDirectory("cplus-test-cross-target")
        val source = Files.writeString(directory.resolve("main.cp"), "int main() { return 0; }")
        val target = if (cplus.compiler.defaultHostTargetTriple() == "linux-x86_64") "windows-x86_64" else "unsupported-target"
        val capturedErr = ByteArrayOutputStream()
        val originalErr = System.err
        val status = try {
            System.setErr(PrintStream(capturedErr))
            Cli().run(listOf("test", source.toString(), "--target", target))
        } finally {
            System.setErr(originalErr)
        }

        assertEquals(2, status)
        assertTrue(capturedErr.toString().contains("requires a native runnable target"), capturedErr.toString())
    }

    @Test
    fun nativeProductsRunAssertionsOncePreserveOrderLoopsDefersMainAndIsolateFixtures() {
        val directory = Files.createTempDirectory("cplus-test-native-semantics")
        val supportsInt128 = cplus.compiler.defaultHostTargetTriple() == "linux-x86_64"
        val wideImports = if (supportsInt128) ", i128, u128" else ""
        val wideAssertions = if (supportsInt128) {
            """
                i128 signedWide = ((i128)1) << 100;
                u128 unsignedWide = ((u128)1) << 120;
                assertEquals(signedWide, ((i128)1) << 100);
                assertEquals(unsignedWide, ((u128)1) << 120);
            """.trimIndent()
        } else ""
        val source = Files.writeString(
            directory.resolve("native semantics.cp"),
            """
                import { i32, u32$wideImports } from std.fixed_width;
                int calls = 0;
                int next_value() { calls += 1; return calls; }
                int main() { return 23; }

                test native assertion semantics {
                    int index = 0;
                    while (index < 2) {
                        assert(next_value() != 0);
                        index += 1;
                    }
                    assert("loop mutations are visible", calls == 2);
                    assertEquals(next_value(), 3);
                    assertEquals("operands evaluate left-to-right once", next_value(), next_value() - 1);
                    assert(calls == 5);
                    int deferred = 0;
                    {
                        defer deferred = 9;
                    }
                    assertEquals(9, deferred);
                    assertEquals(23, main());
                    i32 signedValue = 42;
                    u32 unsignedValue = 42;
                    assertEquals(signedValue, 42);
                    assertEquals(unsignedValue, 42);
                    $wideAssertions
                }

                test fixture state is isolated {
                    assertEquals(calls, 0);
                }
            """.trimIndent()
        )
        val captured = runCli(listOf("test", source.toString()))

        assertEquals(0, captured.status, "${captured.stderr}\n${captured.stdout}")
        val expectedAssertions = if (supportsInt128) 13 else 11
        assertTrue(
            captured.stdout.contains("::: total: passed $expectedAssertions / failed 0 / total $expectedAssertions; errors 0"),
            captured.stdout
        )

        if (!supportsInt128) {
            val unsupported = Files.writeString(
                directory.resolve("unsupported int128.cp"),
                "import { i128 } from std.fixed_width; test unsupported width { i128 value; assert(value); }"
            )
            val diagnostic = runCli(listOf("test", unsupported.toString()))
            assertEquals(1, diagnostic.status, "${diagnostic.stderr}\n${diagnostic.stdout}")
            assertTrue(diagnostic.stderr.contains("SEM411"), diagnostic.stderr)
        }
    }

    @Test
    fun nativeCrashAndTimeoutRetainValidatedAssertionResultsAndReportExecutionErrors() {
        val directory = Files.createTempDirectory("cplus-test-native-failures")
        val crashSource = Files.writeString(
            directory.resolve("crash.cp"),
            "import { abort } from c.stdlib; int main() { return 0; } test crashing fixture { assert(1); abort(); }"
        )
        val crash = runCli(listOf("test", crashSource.toString()))
        assertEquals(1, crash.status, "${crash.stderr}\n${crash.stdout}")
        assertTrue(crash.stdout.contains("passed 1 / failed 0 / total 1; errors 1"), crash.stdout)

        val timeoutSource = Files.writeString(
            directory.resolve("timeout.cp"),
            "int main() { return 0; } test timed fixture { assert(1); while (1) { } }"
        )
        val timeout = runCli(listOf("test", timeoutSource.toString(), "--timeout", "1"))
        assertEquals(1, timeout.status, "${timeout.stderr}\n${timeout.stdout}")
        assertTrue(timeout.stdout.contains("passed 1 / failed 0 / total 1; errors 1"), timeout.stdout)
        assertTrue(timeout.stderr.contains("timed out"), timeout.stderr)
    }

    @Test
    fun nativeTypeDiagnosticsMapBackToTheFixtureOperand() {
        val directory = Files.createTempDirectory("cplus-test-assertion-source-map")
        val source = Files.writeString(
            directory.resolve("operand diagnostic.cp"),
            """
                struct record_t { int value; };
                test non-scalar assertion {
                    record_t record;
                    assert(record);
                }
            """.trimIndent()
        )
        val result = runCli(listOf("test", source.toString()))

        assertEquals(1, result.status, "${result.stderr}\n${result.stdout}")
        assertTrue(result.stderr.contains("[SEM531]"), result.stderr)
        assertTrue(result.stderr.contains("$source:4:"), result.stderr)
        assertTrue(result.stderr.contains("assert condition must have scalar type"), result.stderr)
        assertTrue(result.stdout.contains("errors 1"), result.stdout)
    }

    @Test
    fun importedGeneratorTypesAndFixturesExecuteOnlyForExplicitRoots() {
        val directory = Files.createTempDirectory("cplus-test-imported-generator")
        val provider = Files.writeString(
            directory.resolve("box provider.cp"),
            """
                comptime cpx<decl> make_value(type T) {
                    return { struct value_{T}_t { T item; }; };
                }
                pub comptime cpx<decl> box(type T) {
                    return {
                        make_value(T);
                        struct box_{T}_t { T value; };
                        test generated box fixture {
                            struct box_int_t item;
                            struct value_int_t generatedValue;
                            item.value = 42;
                            generatedValue.item = 42;
                            assert(item.value != 0);
                            assert("generated member is visible", item.value == 42);
                            assertEquals(42, item.value);
                            assertEquals("generated type equality", 6 * 7, item.value);
                            assertEquals(42, generatedValue.item);
                        }
                    };
                }
                test provider-owned fixture { assert(0); }
            """.trimIndent()
        )
        val main = Files.writeString(
            directory.resolve("main.cp"),
            """
                import { box as makeBox } from "./box provider.cp";
                makeBox(int);
                int main() { return 0; }
                test client box fixture {
                    struct box_int_t item;
                    item.value = 7;
                    assert(item.value);
                    assert("client generated member", item.value == 7);
                    assertEquals(7, item.value);
                    assertEquals("client generated equality", 3 + 4, item.value);
                }
            """.trimIndent()
        )
        val compiled = CPlusCompiler().compile(CompileRequest(listOf(main), mode = CompilationMode.TEST))
        assertTrue(compiled.isSuccessful, compiled.diagnostics.joinToString())
        assertTrue(compiled.semanticModel!!.structs.containsKey("value_int_t"))
        val generatedFixture = assertNotNull(
            compiled.semanticModel?.testFixtures?.firstOrNull { it.fixture.description == "generated box fixture" }
        )
        assertTrue(generatedFixture.fixture.origin is Origin.Expansion)
        val generatedAssertionOrigin = generatedFixture.fixture.body.statements.first().origin
        assertTrue(
            compiled.generatedUnits.single().sourceMap.any { it.origin == generatedAssertionOrigin },
            "generated fixture assertions should retain their expansion mapping"
        )

        val clientOnly = runCli(listOf("test", main.toString()))
        assertEquals(0, clientOnly.status, "${clientOnly.stderr}\n${clientOnly.stdout}")
        assertTrue(clientOnly.stdout.contains("generated box fixture"), clientOnly.stdout)
        assertTrue(clientOnly.stdout.contains("client box fixture"), clientOnly.stdout)
        assertTrue(!clientOnly.stdout.contains("provider-owned fixture"), clientOnly.stdout)
        assertTrue(clientOnly.stdout.contains("::: total: passed 9 / failed 0 / total 9; errors 0"), clientOnly.stdout)

        Files.writeString(
            provider,
            Files.readString(provider).replace(
                "struct box_{T}_t { T value; };",
                "struct box_{T}_t { long value; };"
            )
        )
        val afterProviderEdit = runCli(listOf("test", main.toString()))
        assertEquals(0, afterProviderEdit.status, "${afterProviderEdit.stderr}\n${afterProviderEdit.stdout}")
        assertTrue(afterProviderEdit.stdout.contains("::: total: passed 9 / failed 0 / total 9; errors 0"), afterProviderEdit.stdout)

        val bothRoots = runCli(listOf("test", main.toString(), provider.toString()))
        assertEquals(1, bothRoots.status, "${bothRoots.stderr}\n${bothRoots.stdout}")
        assertTrue(bothRoots.stdout.contains("provider-owned fixture"), bothRoots.stdout)
        assertTrue(bothRoots.stdout.contains("::: total: passed 9 / failed 1 / total 10; errors 0"), bothRoots.stdout)
    }

    private data class CliCapture(val status: Int, val stdout: String, val stderr: String)

    private fun runCli(arguments: List<String>): CliCapture {
        val stdout = ByteArrayOutputStream()
        val stderr = ByteArrayOutputStream()
        val originalOut = System.out
        val originalErr = System.err
        val status = try {
            System.setOut(PrintStream(stdout))
            System.setErr(PrintStream(stderr))
            Cli().run(arguments)
        } finally {
            System.setOut(originalOut)
            System.setErr(originalErr)
        }
        return CliCapture(status, stdout.toString(), stderr.toString())
    }
}
