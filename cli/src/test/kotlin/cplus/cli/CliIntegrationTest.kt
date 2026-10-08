package cplus.cli

import cplus.compiler.SdkManifestLocator
import cplus.compiler.SdkManifestLoader
import cplus.compiler.SdkResolver
import cplus.compiler.RuntimeLinker
import cplus.compiler.TargetInfo
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CliIntegrationTest {
    @Test
    fun runtimeInspectReportsOnlyTheSelectedBuildLinkPlan() {
        val manifestPath = SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize()
        val target = TargetInfo(targetTriple = "linux-x86_64")
        val manifest = SdkManifestLoader.load(manifestPath).manifest!!
        val resolution = SdkResolver.resolve(manifest, target).resolution!!
        val expected = RuntimeLinker.plan(resolution, target).plan!!

        val output = captureStdout {
            assertEquals(0, Cli().run(listOf("runtime", "inspect", "--target", "LINUX-X86_64", "--sdk", manifestPath.toString())))
        }

        expected.startupSources.forEach { assertTrue(output.contains("startup: ${it.toAbsolutePath().normalize()}")) }
        expected.runtimeSources.forEach { assertTrue(output.contains("source: ${it.toAbsolutePath().normalize()}")) }
        assertTrue(output.contains("compiler-flag: -nostdlib"))
        assertTrue(!output.contains("cplus_runtime.h"))
    }

    @Test
    fun sdkInspectionCommandsUseSelectedManifestAndTargetArtifacts() {
        val manifest = SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize()
        val sdkArgs = listOf("--sdk", manifest.toString())

        val doctor = captureStdout {
            assertEquals(0, Cli().run(listOf("sdk", "doctor") + sdkArgs + listOf("--target", "windows-x86_64")))
        }
        assertTrue(doctor.contains("ok: target descriptor"))
        val target = captureStdout {
            assertEquals(0, Cli().run(listOf("target", "windows-x86_64") + sdkArgs))
        }
        assertTrue(target.contains("windows-x86_64: windows/x86_64"))
        val abi = captureStdout {
            assertEquals(0, Cli().run(listOf("abi", "verify") + sdkArgs + listOf("--target", "WINDOWS-X86_64")))
        }
        assertTrue(abi.contains("verified 1 target ABI descriptor"))
        val libc = captureStdout {
            assertEquals(0, Cli().run(listOf("libc") + sdkArgs))
        }
        assertTrue(libc.contains("C17 headers: 22"))
    }

    @Test
    fun sdkInspectionRejectsMalformedSelectedManifest() {
        val directory = Files.createTempDirectory("cplus-cli-invalid-sdk")
        val manifest = directory.resolve("sdk.toml").also { it.writeText("not a manifest") }
        val diagnostics = captureStderr {
            assertEquals(1, Cli().run(listOf("target", "list", "--sdk", manifest.toString())))
        }
        assertTrue(diagnostics.contains("SDK002"))
    }

    @Test
    fun sdkPackageIndexUsesTheSelectedSdkRootAndExplicitOutput() {
        val root = Files.createTempDirectory("cplus-cli-sdk-package")
        val manifest = Files.createDirectories(root.resolve("manifest")).resolve("sdk.toml").also {
            it.writeText(
                """
                    sdk_version = "test"
                    language_abi_version = "1"
                    runtime_abi_version = "1"
                    cplus_abi_version = "1"
                    libc_profile_version = "c17-1"
                """.trimIndent()
            )
        }
        val marker = root.resolve("marker.txt").also { it.writeText("package-me") }
        val output = root.resolveSibling("${root.fileName}-index.txt")

        assertEquals(0, Cli().run(listOf("sdk", "package", output.toString(), "--sdk", manifest.toString())))
        val index = output.readText()
        assertTrue(index.startsWith("CPLUS_SDK_PACKAGE_INDEX"))
        assertTrue(index.contains("manifest/sdk.toml"))
        assertTrue(index.contains("marker.txt"))

        val inRootOutput = root.resolve("sdk-package.index")
        assertEquals(0, Cli().run(listOf("sdk", "package", inRootOutput.toString(), "--sdk", manifest.toString())))
        val firstInRootIndex = inRootOutput.readText()
        assertEquals(0, Cli().run(listOf("sdk", "package", inRootOutput.toString(), "--sdk", manifest.toString())))
        assertEquals(firstInRootIndex, inRootOutput.readText())
        assertTrue("sdk-package.index" !in firstInRootIndex)
    }

    @Test
    fun auditInspectsTheExecutableProducedByBuild() {
        val directory = Files.createTempDirectory("cplus-cli-audit")
        val source = directory.resolve("main.cp").also { it.writeText("int main() { return 0; }") }
        val windowsHost = System.getProperty("os.name").contains("windows", ignoreCase = true)
        val executable = directory.resolve(if (windowsHost) "program.exe" else "program")
        val manifest = SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize()
        assertEquals(0, Cli().run(listOf("build", source.toString(), "--output", executable.toString())))

        val output = captureStdout {
            assertEquals(
                0,
                Cli().run(
                    listOf(
                        "audit", executable.toString(), "--target", if (windowsHost) "WINDOWS-X86_64" else "LINUX-X86_64", "--sdk", manifest.toString(),
                        "--runtime", "cplus", "--libc", "c17"
                    )
                )
            )
        }
        assertTrue(output.contains("observed:"))
    }

    @Test
    fun linuxClangBuildProducesAndAuditsAnExecutableWithoutHostRuntimeDependencies() {
        assumeTrue(System.getProperty("os.name").contains("linux", ignoreCase = true))
        val clangAvailable = runCatching {
            ProcessBuilder("clang", "--version").start().waitFor() == 0
        }.getOrDefault(false)
        assumeTrue(clangAvailable, "Clang is not installed")

        val directory = Files.createTempDirectory("cplus-cli-clang-static")
        val source = directory.resolve("main.cp").also {
            it.writeText("int main() { return 12 - 12; }")
        }
        val executable = directory.resolve("program")
        val buildOutput = captureStdout {
            assertEquals(
                0,
                Cli().run(
                    listOf(
                        "build", source.toString(), "--target", "linux-x86_64",
                        "--c-compiler", "clang", "--output", executable.toString()
                    )
                )
            )
        }
        assertTrue("argument unused" !in buildOutput, buildOutput)
        assertEquals(0, ProcessBuilder(executable.toString()).start().waitFor())

        val manifest = SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize()
        val auditOutput = captureStdout {
            assertEquals(
                0,
                Cli().run(
                    listOf(
                        "audit", executable.toString(), "--target", "linux-x86_64",
                        "--sdk", manifest.toString(), "--runtime", "cplus", "--libc", "c17"
                    )
                )
            )
        }
        assertTrue(auditOutput.contains("observed:"), auditOutput)

    }

    @Test
    fun windowsMinGWBuildProducesPeWithoutCrtAndRunsTlsUnderWineWhenAvailable() {
        assumeTrue(System.getProperty("os.name").contains("linux", ignoreCase = true))
        val compiler = "x86_64-w64-mingw32-gcc"
        val inspector = "x86_64-w64-mingw32-objdump"
        assumeTrue(commandAvailable(compiler), "MinGW cross-compiler is not installed")
        assumeTrue(commandAvailable(inspector), "MinGW PE inspection tools are not installed")

        val directory = Files.createTempDirectory("cplus-cli-windows-pe")
        val source = directory.resolve("main.cp").also {
            it.writeText(
                """
                    thread_local int tls_value = 41;
                    int main() {
                        if (tls_value != 41) return 1;
                        tls_value = 73;
                        return tls_value == 73 ? 0 : 2;
                    }
                """.trimIndent()
            )
        }
        val executable = directory.resolve("program.exe")
        val buildOutput = captureStdout {
            assertEquals(
                0,
                Cli().run(
                    listOf(
                        "build", source.toString(), "--target", "windows-x86_64",
                        "--c-compiler", compiler, "--output", executable.toString()
                    )
                )
            )
        }
        assertTrue(executable.exists(), buildOutput)

        val inspect = ProcessBuilder(inspector, "-p", executable.toString())
            .redirectErrorStream(true).start()
        val imports = inspect.inputStream.bufferedReader().readText()
        assertEquals(0, inspect.waitFor(), imports)
        assertEquals(1, Regex("DLL Name:").findAll(imports).count(), imports)
        assertTrue("DLL Name: KERNEL32.dll" in imports, imports)
        listOf("msvcrt", "ucrt", "WaitOnAddress", "WakeByAddress", "emutls", "chkstk")
            .forEach { forbidden -> assertTrue(forbidden !in imports.lowercase(), imports) }

        if (commandAvailable("wine") && commandAvailable("wineboot")) {
            val winePrefix = directory.resolve("wine-prefix")
            val wineLog = directory.resolve("wine.log")
            fun wineProcess(command: String): Process = ProcessBuilder(command, "--init")
                .redirectErrorStream(true)
                .redirectOutput(wineLog.toFile())
                .apply {
                    environment()["WINEPREFIX"] = winePrefix.toString()
                    environment()["WINEDLLOVERRIDES"] = "mscoree,mshtml="
                    environment()["WINEDEBUG"] = "-all"
                }
                .start()

            val initialize = wineProcess("wineboot")
            assertTrue(initialize.waitFor(60, TimeUnit.SECONDS), wineLog.readText())
            assertEquals(0, initialize.exitValue(), wineLog.readText())
            val execute = ProcessBuilder("wine", executable.toString())
                .redirectErrorStream(true)
                .redirectOutput(wineLog.toFile())
                .apply {
                    environment()["WINEPREFIX"] = winePrefix.toString()
                    environment()["WINEDLLOVERRIDES"] = "mscoree,mshtml="
                    environment()["WINEDEBUG"] = "-all"
                }
                .start()
            assertTrue(execute.waitFor(30, TimeUnit.SECONDS), wineLog.readText())
            assertEquals(0, execute.exitValue(), wineLog.readText())
        }
    }

    @Test
    fun linuxAarch64ClangBuildProducesStaticTlsProduct() {
        assumeTrue(System.getProperty("os.name").contains("linux", ignoreCase = true))
        assumeTrue(commandAvailable("clang"), "Clang is not installed")
        assumeTrue(commandAvailable("readelf") && commandAvailable("nm"), "ELF inspection tools are not installed")
        val linkerAvailable = System.getenv("PATH").orEmpty()
            .split(java.io.File.pathSeparator)
            .filter(String::isNotBlank)
            .map(Path::of)
            .any { directory -> runCatching {
                Files.isExecutable(directory.resolve("ld.lld")) ||
                    Files.newDirectoryStream(directory, "ld.lld-*").use { entries ->
                        entries.any { path -> path.fileName.toString().removePrefix("ld.lld-").toIntOrNull() != null && Files.isExecutable(path) }
                    }
            }.getOrDefault(false) }
        assumeTrue(linkerAvailable, "LLD is not installed")

        val source = Path.of("examples/module_main.cp").toAbsolutePath().normalize()
        assumeTrue(Files.isRegularFile(source), "module example is missing")
        val directory = Files.createTempDirectory("cplus-cli-aarch64-static")
        val executable = directory.resolve("module_main")
        val buildOutput = captureStdout {
            assertEquals(
                0,
                Cli().run(
                    listOf(
                        "build", source.toString(), "--target", "linux-aarch64",
                        "--c-compiler", "clang", "--output", executable.toString()
                    )
                )
            )
        }
        assertTrue("argument unused" !in buildOutput, buildOutput)

        fun inspect(vararg command: String): String {
            val process = ProcessBuilder(*command).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(0, process.waitFor(), output)
            return output
        }

        val header = inspect("readelf", "-h", executable.toString())
        assertTrue("ELF64" in header && "AArch64" in header, header)
        val programHeaders = inspect("readelf", "-l", executable.toString())
        assertTrue("TLS" in programHeaders, programHeaders)
        assertTrue("Requesting program interpreter" !in programHeaders, programHeaders)
        assertTrue("No dynamic section" in inspect("readelf", "-d", executable.toString()))
        assertTrue(inspect("nm", "-u", executable.toString()).isBlank())

        val manifest = SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize()
        val auditOutput = captureStdout {
            assertEquals(
                0,
                Cli().run(
                    listOf(
                        "audit", executable.toString(), "--target", "linux-aarch64",
                        "--sdk", manifest.toString(), "--runtime", "cplus", "--libc", "c17"
                    )
                )
            )
        }
        assertTrue(auditOutput.contains("observed:"), auditOutput)

        val aarch64Runner = System.getenv("PATH").orEmpty()
            .split(java.io.File.pathSeparator)
            .filter(String::isNotBlank)
            .asSequence()
            .flatMap { path -> sequenceOf("qemu-aarch64", "qemu-aarch64-static").map { Path.of(path).resolve(it) } }
            .firstOrNull(Files::isExecutable)
        if (aarch64Runner != null) {
            val run = ProcessBuilder(aarch64Runner.toString(), executable.toString())
                .redirectErrorStream(true)
                .start()
            val output = run.inputStream.bufferedReader().readText()
            assertEquals(0, run.waitFor(), output)
            assertTrue("Result: 12" in output, output)
        }
    }

    private fun commandAvailable(command: String): Boolean = runCatching {
        ProcessBuilder(command, "--version").start().waitFor() == 0
    }.getOrDefault(false)

    @Test
    fun astAndExpandInspectDifferentCompilerPhaseRepresentations() {
        val directory = Files.createTempDirectory("cplus-cli-inspection")
        val source = directory.resolve("main.cp").also {
            it.writeText(
                """
                    comptime cpx<decl> box(type T) {
                        return { struct box_{T}_t { T value; }; };
                    }
                    box(int);
                    int main() { return 0; }
                """.trimIndent()
            )
        }
        val astOutput = captureStdout { assertEquals(0, Cli().run(listOf("ast", source.toString()))) }
        val expandedOutput = captureStdout { assertEquals(0, Cli().run(listOf("expand", source.toString()))) }

        assertTrue(astOutput.contains("Comptime decl box"))
        assertTrue(astOutput.contains("CpxInvocation box(int)"))
        assertTrue(!astOutput.contains("Struct box_int_t"))
        assertTrue(expandedOutput.contains("Struct box_int_t"))
        assertTrue(!expandedOutput.contains("CpxInvocation"))
    }

    @Test
    fun lspServesInitializationAndCompilerDiagnosticsOverStdio() {
        val directory = Files.createTempDirectory("cplus-cli-lsp")
        val source = directory.resolve("main.cp")
        val uri = source.toUri().toString()
        val input = listOf(
            """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}""",
            """{"jsonrpc":"2.0","method":"textDocument/didOpen","params":{"textDocument":{"uri":"$uri","version":1,"text":"void invalid_value; int main() { return 0; }"}}}""",
            """{"jsonrpc":"2.0","id":2,"method":"shutdown","params":null}""",
            """{"jsonrpc":"2.0","method":"exit"}"""
        ).joinToString(separator = "") { message -> frame(message) }
        val output = ByteArrayOutputStream()

        assertEquals(
            0,
            LspServer().run(ByteArrayInputStream(input.toByteArray()), output)
        )

        val responses = output.toString(Charsets.UTF_8)
        assertTrue(responses.contains("\"serverInfo\""))
        assertTrue(responses.contains("textDocument/publishDiagnostics"))
        assertTrue(responses.contains("LOW402"))
        assertTrue(responses.contains(uri))
    }

    @Test
    fun lspCompilesOpenWorkspaceImportsForDiagnostics() {
        val directory = Files.createTempDirectory("cplus-cli-lsp-modules")
        val helper = directory.resolve("module_helpers.cp")
        val main = directory.resolve("module_main.cp")
        val helperUri = helper.toUri().toString()
        val mainUri = main.toUri().toString()
        val helperText = "pub int add(int left, int right) { return left + right; }"
        val mainText = "import { add } from ./module_helpers.cp; int main() { return add(7, 5); }"
        val input = listOf(
            """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}""",
            """{"jsonrpc":"2.0","method":"textDocument/didOpen","params":{"textDocument":{"uri":"$helperUri","version":1,"text":"$helperText"}}}""",
            """{"jsonrpc":"2.0","method":"textDocument/didOpen","params":{"textDocument":{"uri":"$mainUri","version":1,"text":"$mainText"}}}""",
            """{"jsonrpc":"2.0","id":2,"method":"shutdown","params":null}""",
            """{"jsonrpc":"2.0","method":"exit"}"""
        ).joinToString(separator = "") { message -> frame(message) }
        val output = ByteArrayOutputStream()

        assertEquals(0, LspServer().run(ByteArrayInputStream(input.toByteArray()), output))

        val responses = output.toString(Charsets.UTF_8)
        assertTrue(!responses.contains("module import 'module_helpers' cannot be resolved"), responses)
        assertTrue(responses.contains("\"uri\":\"$mainUri\""))
    }

    @Test
    fun lspLoadsClosedImportedModulesFromDiskForDiagnostics() {
        val directory = Files.createTempDirectory("cplus-cli-lsp-disk-modules")
        val helper = directory.resolve("module_helpers.cp").also {
            it.writeText("pub int add(int left, int right) { return left + right; }")
        }
        val main = directory.resolve("module_main.cp")
        val mainUri = main.toUri().toString()
        val mainText = "import { add } from module_helpers; int main() { return add(7, 5); }"
        val input = listOf(
            """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}""",
            """{"jsonrpc":"2.0","method":"textDocument/didOpen","params":{"textDocument":{"uri":"$mainUri","version":1,"text":"$mainText"}}}""",
            """{"jsonrpc":"2.0","id":2,"method":"shutdown","params":null}""",
            """{"jsonrpc":"2.0","method":"exit"}"""
        ).joinToString(separator = "") { message -> frame(message) }
        val output = ByteArrayOutputStream()

        assertEquals(0, LspServer().run(ByteArrayInputStream(input.toByteArray()), output))

        val responses = output.toString(Charsets.UTF_8)
        assertTrue(!responses.contains("module import 'module_helpers' cannot be resolved"), responses)
        assertTrue(responses.contains("\"uri\":\"$mainUri\""))
        assertTrue(helper.exists())
    }

    @Test
    fun lspMapsNavigationSymbolsTokensAndRenameAcrossImportedSources() {
        val directory = Files.createTempDirectory("cplus-cli-lsp-cross-source")
        val helper = directory.resolve("module_helpers.cp").also {
            it.writeText("pub int add(int left, int right) { return left + right; }")
        }
        val main = directory.resolve("module_main.cp")
        val helperUri = helper.toUri().toString()
        val mainUri = main.toUri().toString()
        val helperText = "pub int add(int left, int right) { return left + right; }"
        val mainText = "// from ./nonexistent.cp\nimport { add } from ./module_helpers.cp;\nint main() { return add(2, 3); }\n"
        main.writeText(mainText)
        val callLine = 2
        val callColumn = mainText.lineSequence().elementAt(callLine).indexOf("add")
        val messages = listOf(
            """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}""",
            """{"jsonrpc":"2.0","method":"textDocument/didOpen","params":{"textDocument":{"uri":"$helperUri","version":1,"text":"$helperText"}}}""",
            """{"jsonrpc":"2.0","method":"textDocument/didOpen","params":{"textDocument":{"uri":"$mainUri","version":1,"text":"${mainText.replace("\n", "\\n")}"}}}""",
            """{"jsonrpc":"2.0","id":3,"method":"textDocument/definition","params":{"textDocument":{"uri":"$mainUri"},"position":{"line":$callLine,"character":$callColumn}}}""",
            """{"jsonrpc":"2.0","id":4,"method":"textDocument/references","params":{"textDocument":{"uri":"$mainUri"},"position":{"line":$callLine,"character":$callColumn},"context":{"includeDeclaration":true}}}""",
            """{"jsonrpc":"2.0","id":5,"method":"textDocument/documentSymbol","params":{"textDocument":{"uri":"$helperUri"}}}""",
            """{"jsonrpc":"2.0","id":6,"method":"textDocument/rename","params":{"textDocument":{"uri":"$mainUri"},"position":{"line":$callLine,"character":$callColumn},"newName":"sum"}}""",
            """{"jsonrpc":"2.0","id":7,"method":"textDocument/semanticTokens/full","params":{"textDocument":{"uri":"$helperUri"}}}""",
            """{"jsonrpc":"2.0","id":8,"method":"textDocument/hover","params":{"textDocument":{"uri":"$mainUri"},"position":{"line":$callLine,"character":$callColumn}}}""",
            """{"jsonrpc":"2.0","id":9,"method":"textDocument/completion","params":{"textDocument":{"uri":"$mainUri"},"position":{"line":$callLine,"character":${callColumn + 3}}}}""",
            """{"jsonrpc":"2.0","id":10,"method":"textDocument/references","params":{"textDocument":{"uri":"$helperUri"},"position":{"line":0,"character":8},"context":{"includeDeclaration":true}}}""",
            """{"jsonrpc":"2.0","id":11,"method":"shutdown","params":null}""",
            """{"jsonrpc":"2.0","method":"exit"}"""
        )
        val output = ByteArrayOutputStream()

        assertEquals(0, LspServer().run(ByteArrayInputStream(messages.joinToString(separator = "", transform = ::frame).toByteArray()), output))

        val responses = output.toString(Charsets.UTF_8)
        assertTrue(responses.contains("\"id\":3, \"result\":{\"uri\":\"$helperUri\", \"range\":"), responses)
        assertTrue(responses.contains("\"id\":3, \"result\":{\"uri\":\"$helperUri\", \"range\":{\"start\":{\"line\":0, \"character\":8}"), responses)
        assertTrue(responses.contains("\"id\":4, \"result\":[") && responses.contains("\"uri\":\"$mainUri\""), responses)
        assertTrue(responses.contains("\"id\":5, \"result\":[") && responses.contains("\"name\":\"add\""), responses)
        assertTrue(responses.contains("\"id\":6, \"result\":{\"changes\":"), responses)
        assertTrue(responses.contains("\"newText\":\"sum\""), responses)
        assertTrue(responses.contains("\"character\":8}, \"end\":{\"line\":0, \"character\":11}"), responses)
        assertTrue(responses.contains("\"id\":7, \"result\":{\"data\":["), responses)
        assertTrue(responses.contains("\"id\":8, \"result\":{\"contents\":"), responses)
        assertTrue(responses.contains("\"id\":9, \"result\":{\"isIncomplete\":false, \"items\":[") && responses.contains("\"label\":\"add\""), responses)
        assertTrue(responses.contains("\"id\":10, \"result\":[") && responses.contains("\"uri\":\"$mainUri\""), responses)
    }

    @Test
    fun lspPublishesDiagnosticsToOpenImportedDocuments() {
        val directory = Files.createTempDirectory("cplus-cli-lsp-imported-diagnostics")
        val helper = directory.resolve("helper.cp")
        val main = directory.resolve("main.cp")
        val helperUri = helper.toUri().toString()
        val mainUri = main.toUri().toString()
        val helperText = "pub int get_value() { return missing_value; }"
        val mainText = "import { get_value } from ./helper.cp; int main() { return get_value(); }"
        val input = listOf(
            """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}""",
            """{"jsonrpc":"2.0","method":"textDocument/didOpen","params":{"textDocument":{"uri":"$helperUri","version":1,"text":"$helperText"}}}""",
            """{"jsonrpc":"2.0","method":"textDocument/didOpen","params":{"textDocument":{"uri":"$mainUri","version":1,"text":"$mainText"}}}""",
            """{"jsonrpc":"2.0","id":2,"method":"shutdown","params":null}""",
            """{"jsonrpc":"2.0","method":"exit"}"""
        ).joinToString(separator = "", transform = ::frame)
        val output = ByteArrayOutputStream()

        assertEquals(0, LspServer().run(ByteArrayInputStream(input.toByteArray()), output))

        val responses = output.toString(Charsets.UTF_8)
        assertTrue(responses.contains("\"uri\":\"$mainUri\""))
        assertTrue(responses.split("\"uri\":\"$helperUri\", \"diagnostics\":[{").size - 1 >= 2, responses)
        assertTrue(responses.contains("missing_value"), responses)
    }

    @Test
    fun lspDoesNotMergeUnrelatedOpenProgramsIntoTheActiveWorkspace() {
        val directory = Files.createTempDirectory("cplus-cli-lsp-unrelated")
        val first = directory.resolve("first.cp")
        val second = directory.resolve("second.cp")
        val firstUri = first.toUri().toString()
        val secondUri = second.toUri().toString()
        val source = "int main() { return 0; }"
        val input = listOf(
            """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}""",
            """{"jsonrpc":"2.0","method":"textDocument/didOpen","params":{"textDocument":{"uri":"$firstUri","version":1,"text":"$source"}}}""",
            """{"jsonrpc":"2.0","method":"textDocument/didOpen","params":{"textDocument":{"uri":"$secondUri","version":1,"text":"$source"}}}""",
            """{"jsonrpc":"2.0","id":2,"method":"shutdown","params":null}""",
            """{"jsonrpc":"2.0","method":"exit"}"""
        ).joinToString(separator = "") { message -> frame(message) }
        val output = ByteArrayOutputStream()

        assertEquals(0, LspServer().run(ByteArrayInputStream(input.toByteArray()), output))

        val responses = output.toString(Charsets.UTF_8)
        assertTrue(!responses.contains("duplicate function 'main'"), responses)
        assertTrue(responses.contains("\"uri\":\"$firstUri\""))
        assertTrue(responses.contains("\"uri\":\"$secondUri\""))
    }

    @Test
    fun lspPublishesSemanticTokensFromTheCompilerFrontEnd() {
        val directory = Files.createTempDirectory("cplus-cli-semantic-tokens")
        val source = directory.resolve("main.cp")
        val uri = source.toUri().toString()
        val input = listOf(
            """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}""",
            """{"jsonrpc":"2.0","method":"textDocument/didOpen","params":{"textDocument":{"uri":"$uri","version":1,"text":"struct User { int value; }; int main() { User item; return item.value; }"}}}""",
            """{"jsonrpc":"2.0","id":2,"method":"textDocument/semanticTokens/full","params":{"textDocument":{"uri":"$uri"}}}""",
            """{"jsonrpc":"2.0","id":3,"method":"shutdown","params":null}""",
            """{"jsonrpc":"2.0","method":"exit"}"""
        ).joinToString(separator = "") { message -> frame(message) }
        val output = ByteArrayOutputStream()

        assertEquals(0, LspServer().run(ByteArrayInputStream(input.toByteArray()), output))

        val responses = output.toString(Charsets.UTF_8)
        assertTrue(responses.contains("semanticTokensProvider"))
        assertTrue(responses.contains("\"id\":2"))
        assertTrue(responses.contains("\"data\":["))
    }

    @Test
    fun lspCompletionAndHoverUseSemanticSymbols() {
        val directory = Files.createTempDirectory("cplus-cli-language-service")
        val source = directory.resolve("main.cp")
        val uri = source.toUri().toString()
        val text = "int main() { return 0; }"
        val input = listOf(
            """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}""",
            """{"jsonrpc":"2.0","method":"textDocument/didOpen","params":{"textDocument":{"uri":"$uri","version":1,"text":"$text"}}}""",
            """{"jsonrpc":"2.0","id":2,"method":"textDocument/completion","params":{"textDocument":{"uri":"$uri"},"position":{"line":0,"character":6}}}""",
            """{"jsonrpc":"2.0","id":3,"method":"textDocument/hover","params":{"textDocument":{"uri":"$uri"},"position":{"line":0,"character":5}}}""",
            """{"jsonrpc":"2.0","id":4,"method":"shutdown","params":null}""",
            """{"jsonrpc":"2.0","method":"exit"}"""
        ).joinToString(separator = "") { message -> frame(message) }
        val output = ByteArrayOutputStream()

        assertEquals(0, LspServer().run(ByteArrayInputStream(input.toByteArray()), output))

        val responses = output.toString(Charsets.UTF_8)
        assertTrue(responses.contains("\"id\":2"))
        assertTrue(responses.contains("\"label\":\"main\""))
        assertTrue(responses.contains("\"id\":3"))
        assertTrue(responses.contains("main"))
    }

    @Test
    fun lspNavigationUsesCompilerReferenceIndex() {
        val directory = Files.createTempDirectory("cplus-cli-navigation")
        val source = directory.resolve("main.cp")
        val uri = source.toUri().toString()
        val text = "int answer() { return 7; } int main() { return answer(); }"
        val input = listOf(
            """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}""",
            """{"jsonrpc":"2.0","method":"textDocument/didOpen","params":{"textDocument":{"uri":"$uri","version":1,"text":"$text"}}}""",
            """{"jsonrpc":"2.0","id":2,"method":"textDocument/definition","params":{"textDocument":{"uri":"$uri"},"position":{"line":0,"character":52}}}""",
            """{"jsonrpc":"2.0","id":3,"method":"textDocument/references","params":{"textDocument":{"uri":"$uri"},"position":{"line":0,"character":52},"context":{"includeDeclaration":true}}}""",
            """{"jsonrpc":"2.0","id":4,"method":"shutdown","params":null}""",
            """{"jsonrpc":"2.0","method":"exit"}"""
        ).joinToString(separator = "") { message -> frame(message) }
        val output = ByteArrayOutputStream()

        assertEquals(0, LspServer().run(ByteArrayInputStream(input.toByteArray()), output))

        val responses = output.toString(Charsets.UTF_8)
        assertTrue(responses.contains("definitionProvider"))
        assertTrue(responses.contains("referencesProvider"))
        assertTrue(responses.contains("\"id\":2"))
        assertTrue(responses.contains("\"id\":3"))
        assertTrue(responses.contains("\"uri\":\"$uri\""))
    }

    @Test
    fun lspForeignSymbolsParticipateInCompletionHoverAndSignatureHelp() {
        val directory = Files.createTempDirectory("cplus-cli-foreign-language-service")
        val source = directory.resolve("main.cp")
        val uri = source.toUri().toString()
        val text = "import { printf } from c.stdio; int main() { return printf(0); }"
        val printfOffset = text.indexOf("printf(0")
        val input = listOf(
            """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}""",
            """{"jsonrpc":"2.0","method":"textDocument/didOpen","params":{"textDocument":{"uri":"$uri","version":1,"text":"$text"}}}""",
            """{"jsonrpc":"2.0","id":2,"method":"textDocument/completion","params":{"textDocument":{"uri":"$uri"},"position":{"line":0,"character":${printfOffset + 3}}}}""",
            """{"jsonrpc":"2.0","id":3,"method":"textDocument/hover","params":{"textDocument":{"uri":"$uri"},"position":{"line":0,"character":$printfOffset}}}""",
            """{"jsonrpc":"2.0","id":4,"method":"textDocument/signatureHelp","params":{"textDocument":{"uri":"$uri"},"position":{"line":0,"character":${printfOffset + 7}}}}""",
            """{"jsonrpc":"2.0","id":5,"method":"shutdown","params":null}""",
            """{"jsonrpc":"2.0","method":"exit"}"""
        ).joinToString(separator = "") { message -> frame(message) }
        val output = ByteArrayOutputStream()

        assertEquals(0, LspServer().run(ByteArrayInputStream(input.toByteArray()), output))

        val responses = output.toString(Charsets.UTF_8)
        assertTrue(responses.contains("signatureHelpProvider"))
        assertTrue(responses.contains("\"label\":\"printf\""))
        assertTrue(responses.contains("printf("))
        assertTrue(responses.contains("fn(...)->int"))
    }

    @Test
    fun buildEmitsCHeaderAndExecutableWithExpectedBehavior() {
        val directory = Files.createTempDirectory("cplus-cli-build")
        val source = directory.resolve("main.cp").also {
            it.writeText(
                """
                    pub int answer() {
                        return 7;
                    }

                    int main() {
                        return answer();
                    }
                """.trimIndent()
            )
        }
        val requestedExecutable = directory.resolve("program")
        val executable = if (System.getProperty("os.name").contains("windows", ignoreCase = true)) {
            directory.resolve("program.exe")
        } else requestedExecutable
        val header = directory.resolve("program.h")

        val exitCode = Cli().run(
            listOf(
                "build",
                source.toString(),
                "--output",
                requestedExecutable.toString(),
                "--header",
                header.toString()
            )
        )

        assertEquals(0, exitCode)
        assertTrue(executable.exists())
        assertTrue(executable.resolveSibling("program.c").exists())
        assertTrue(header.exists())
        assertTrue(header.readText().contains("int answer();"))
        assertEquals(7, ProcessBuilder(executable.toString()).start().waitFor())
    }

    @Test
    fun runDiscoversLocalImportedModulesFromTheEntryPoint() {
        val directory = Files.createTempDirectory("cplus-cli-run-modules")
        val helper = directory.resolve("module_helpers.cp").also {
            it.writeText(
                """
                    pub int add(int left, int right) {
                        return left + right;
                    }
                """.trimIndent()
            )
        }
        val main = directory.resolve("module_main.cp").also {
            it.writeText(
                """
                    import { add } from module_helpers;

                    int main() {
                        return add(7, 5);
                    }
                """.trimIndent()
            )
        }

        assertEquals(12, Cli().run(listOf("run", main.toString())))
        assertTrue(helper.exists())
    }

    @Test
    fun runResolvesPathAndPackageImportsForSourceTypes() {
        val directory = Files.createTempDirectory("cplus-cli-run-imported-types")
        val standardLibrary = Files.createDirectories(directory.resolve("stdlib"))
        val helper = standardLibrary.resolve("io.cp").also {
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
        val main = directory.resolve("main.cp").also {
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

        assertEquals(16, Cli().run(listOf("run", main.toString())))
        assertTrue(helper.exists())
    }

    @Test
    fun runSupportsExplicitFixedWidthSdkModuleImport() {
        val sdkRoot = SdkManifestLocator.defaultManifestPath()
            .toAbsolutePath().normalize().parent!!.parent!!
        val fixedWidthModule = sdkRoot.resolve("std/src/fixed_width.cp")
        val directory = Files.createTempDirectory("cplus-cli-fixed-width-import")
        val main = directory.resolve("main.cp").also {
            it.writeText(
                """
                    import { i8, i32, u64 } from std.fixed_width;
                    i32 add_wide(i32 left, u64 right) { return left + (i32)right; }
                    int main() { i8 small; return add_wide(1, 2); }
                """.trimIndent()
            )
        }

        assertEquals(3, Cli().run(listOf("run", main.toString())))
        assertTrue(fixedWidthModule.exists())
    }

    @Test
    fun projectAndWorkspaceManifestsShareOneSourceModelAcrossCommands() {
        val directory = Files.createTempDirectory("cplus-cli-project-manifest")
        val app = Files.createDirectories(directory.resolve("app"))
        val modules = Files.createDirectories(directory.resolve("modules"))
        modules.resolve("math.cp").writeText("pub int add(int left, int right) { return left + right; }")
        app.resolve("main.cp").writeText(
            "import { add } from math; int main() { return add(7, 5); }"
        )
        val project = directory.resolve("cplus.toml").also {
            it.writeText(
                """
                    [project]
                    entry = "app/main.cp"
                    source_roots = ["modules"]
                """.trimIndent()
            )
        }
        val workspace = directory.resolve("cplus.workspace.toml").also {
            it.writeText(
                """
                    [workspace]
                    entry = "app/main.cp"
                    members = ["modules"]
                """.trimIndent()
            )
        }
        val projectOption = listOf("--project", project.toString())

        assertEquals(0, Cli().run(listOf("check") + projectOption + listOf("--target", "LINUX-X86_64")))
        val generatedC = directory.resolve("generated/main.c")
        val generatedHeader = directory.resolve("generated/include/main.h")
        val sourceMap = directory.resolve("generated/maps/main.map")
        assertEquals(
            0,
            Cli().run(
                listOf("transcode") + projectOption + listOf(
                    "--output", generatedC.toString(),
                    "--header", generatedHeader.toString(),
                    "--map", sourceMap.toString()
                )
            )
        )
        assertTrue(generatedC.exists())
        assertTrue(generatedHeader.exists())
        assertTrue(sourceMap.readText().contains("app/main.cp"))
        assertTrue(sourceMap.readText().contains("modules/math.cp"))
        val executable = directory.resolve(
            if (System.getProperty("os.name").contains("windows", ignoreCase = true)) "project-program.exe" else "project-program"
        )
        val buildMap = directory.resolve("build/project.map")
        assertEquals(
            0,
            Cli().run(listOf("build") + projectOption + listOf("--output", executable.toString(), "--map", buildMap.toString()))
        )
        assertEquals(12, ProcessBuilder(executable.toString()).start().waitFor())
        assertTrue(buildMap.exists())
        val runOutput = directory.resolve(
            "run/${if (System.getProperty("os.name").contains("windows", ignoreCase = true)) "project-program.exe" else "project-program"}"
        )
        assertEquals(12, Cli().run(listOf("run") + projectOption + listOf("--output", runOutput.toString())))
        assertTrue(runOutput.exists())
        assertEquals(0, Cli().run(listOf("check", "--workspace", workspace.toString())))
    }

    @Test
    fun newScaffoldsProjectAndLeavesNonEmptyDirectoryUntouched() {
        val parent = Files.createTempDirectory("cplus-cli-new")
        val project = parent.resolve("nested/app")

        val created = captureStdout {
            assertEquals(0, Cli().run(listOf("new", project.toString())))
        }
        assertTrue(created.contains("created C+ project"))
        assertTrue(Files.isDirectory(project))
        assertTrue(Files.exists(project.resolve("cplus.toml")))
        assertTrue(Files.exists(project.resolve("src/main.cp")))
        assertTrue(Files.exists(project.resolve("README.md")))
        assertEquals(0, Cli().run(listOf("check", "--project", project.resolve("cplus.toml").toString())))

        val existing = Files.createDirectories(parent.resolve("existing"))
        val sentinel = existing.resolve("keep.txt").also { it.writeText("do not change") }
        val warning = captureStderr {
            assertEquals(1, Cli().run(listOf("new", existing.toString())))
        }
        assertTrue(warning.contains("warning: project directory is not empty"))
        assertEquals("do not change", sentinel.readText())
        assertTrue(Files.list(existing).use { paths -> paths.count() == 1L })
    }

    @Test
    fun helpListsJvmSdkOverrideAndExplicitOverrideIsHonored() {
        val help = captureStdout {
            assertEquals(0, Cli().run(listOf("--help")))
        }
        assertTrue(help.contains("-Dcplus.sdk.manifest=<path>"))
        assertTrue(help.contains("place before -jar"))

        val previous = System.getProperty("cplus.sdk.manifest")
        val override = Files.createTempDirectory("cplus-test-sdk").resolve("manifest/sdk.toml")
        try {
            System.setProperty("cplus.sdk.manifest", override.toString())
            assertEquals(override, SdkManifestLocator.defaultManifestPath())
        } finally {
            if (previous == null) System.clearProperty("cplus.sdk.manifest")
            else System.setProperty("cplus.sdk.manifest", previous)
        }
    }

    @Test
    fun checkAcceptsNormalizedSdkTargetRuntimeSysrootAndNativeInputs() {
        val directory = Files.createTempDirectory("cplus-cli-normalized-options")
        val source = directory.resolve("main.cp").also { it.writeText("int main() { return 0; }") }
        val cSource = directory.resolve("native.c").also { it.writeText("int native_helper(void) { return 0; }") }
        val includeDirectory = Files.createDirectories(directory.resolve("include"))
        val sdkManifest = SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize()

        assertEquals(
            0,
            Cli().run(
                listOf(
                    "check", source.toString(),
                    "--target", "LINUX-X86_64",
                    "--runtime", "CPLUS",
                    "--libc", "C17",
                    "--sdk", sdkManifest.toString(),
                    "--sysroot", directory.toString(),
                    "--c-source", cSource.toString(),
                    "--include-dir", includeDirectory.toString(),
                    "--library", "m",
                    "--c-compiler", "cc"
                )
            )
        )
    }

    @Test
    fun runCleansTemporaryProductsAndPreservesProgramExitStatus() {
        val directory = Files.createTempDirectory("cplus-cli-run-cleanup")
        val source = directory.resolve("main.cp").also { it.writeText("int main() { return 23; }") }
        val originalTemporaryRoot = System.getProperty("java.io.tmpdir")
        val temporaryRoot = Files.createDirectories(directory.resolve("temporary"))
        try {
            System.setProperty("java.io.tmpdir", temporaryRoot.toString())
            assertEquals(23, Cli().run(listOf("run", source.toString())))
            assertTrue(Files.list(temporaryRoot).use { paths -> paths.noneMatch { it.fileName.toString().startsWith("cplus-run") } })
            source.writeText("void invalid_value; int main() { return 0; }")
            val diagnostics = captureStderr { assertEquals(1, Cli().run(listOf("run", source.toString()))) }
            assertTrue(diagnostics.isNotBlank())
            assertTrue(Files.list(temporaryRoot).use { paths -> paths.noneMatch { it.fileName.toString().startsWith("cplus-run") } })
        } finally {
            if (originalTemporaryRoot == null) System.clearProperty("java.io.tmpdir")
            else System.setProperty("java.io.tmpdir", originalTemporaryRoot)
        }
    }

    @Test
    fun selfHostedRuntimeProvidesStringTemplateFormatterWithoutHostedStdio() {
        val directory = Files.createTempDirectory("cplus-cli-runtime-format")
        val source = directory.resolve("main.cp").also {
            it.writeText("import { printf } from c.stdio; int main() { printf(\"value=${'$'}{7}\\n\"); return 0; }")
        }

        assertEquals(0, Cli().run(listOf("build", source.toString(), "--output", directory.resolve("program").toString())))
    }

    @Test
    fun checkReportsFailureWithNonzeroExitCode() {
        val directory = Files.createTempDirectory("cplus-cli-check")
        val source = directory.resolve("invalid.cp").also {
            it.writeText("void invalid_value; int main() { return 0; }")
        }

        assertEquals(1, Cli().run(listOf("check", source.toString())))
    }

    @Test
    fun cliAndLspPublishTheSameInvalidIntegerSpecifierDiagnostic() {
        val directory = Files.createTempDirectory("cplus-integer-diagnostic-parity")
        val source = directory.resolve("invalid.cp").also {
            it.writeText("unsigned signed int invalid; int main() { return 0; }")
        }
        val uri = source.toUri().toString()
        val cliDiagnostics = captureStderr {
            assertEquals(1, Cli().run(listOf("check", source.toString())))
        }
        val input = listOf(
            """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}""",
            """{"jsonrpc":"2.0","method":"textDocument/didOpen","params":{"textDocument":{"uri":"$uri","version":1,"text":"unsigned signed int invalid; int main() { return 0; }"}}}""",
            """{"jsonrpc":"2.0","id":2,"method":"shutdown","params":null}""",
            """{"jsonrpc":"2.0","method":"exit"}"""
        ).joinToString(separator = "") { message -> frame(message) }
        val output = ByteArrayOutputStream()

        assertEquals(0, LspServer().run(ByteArrayInputStream(input.toByteArray()), output))

        val lspDiagnostics = output.toString(Charsets.UTF_8)
        val message = "invalid primitive type specifier sequence 'unsigned signed int'"
        assertTrue(cliDiagnostics.contains("[PARSE102]"), cliDiagnostics)
        assertTrue(lspDiagnostics.contains("\"code\":\"PARSE102\""), lspDiagnostics)
        assertTrue(cliDiagnostics.contains(message), cliDiagnostics)
        assertTrue(lspDiagnostics.contains(message), lspDiagnostics)
    }

    @Test
    fun cliAndLspAgreeOnPrivateAndUnimportedSourceTypeDiagnostics() {
        data class DiagnosticCase(
            val name: String,
            val mainText: String,
            val helperText: String,
            val code: String,
            val message: String
        )
        val cases = listOf(
            DiagnosticCase(
                "private",
                "import { Hidden } from ./types.cp; int main() { Hidden item; return 0; }",
                "struct Hidden { int value; };",
                "SEM406",
                "imported type 'Hidden' is not public in module './types.cp'"
            ),
            DiagnosticCase(
                "unimported",
                "import { makeValue } from ./types.cp; int main() { Point item; return makeValue(); }",
                "pub struct Point { int value; }; pub int makeValue() { return 0; }",
                "SEM410",
                "type 'Point' belongs to module 'types' and is not imported into module 'main'"
            )
        )

        cases.forEach { testCase ->
            val directory = Files.createTempDirectory("cplus-type-import-parity-${testCase.name}")
            val mainText = testCase.mainText
            val main = directory.resolve("main.cp").also { it.writeText(mainText) }
            directory.resolve("types.cp").writeText(testCase.helperText)
            val cliDiagnostics = captureStderr {
                assertEquals(1, Cli().run(listOf("check", main.toString())))
            }
            val uri = main.toUri().toString()
            val input = listOf(
                """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}""",
                """{"jsonrpc":"2.0","method":"textDocument/didOpen","params":{"textDocument":{"uri":"$uri","version":1,"text":"$mainText"}}}""",
                """{"jsonrpc":"2.0","id":2,"method":"shutdown","params":null}""",
                """{"jsonrpc":"2.0","method":"exit"}"""
            ).joinToString(separator = "") { message -> frame(message) }
            val output = ByteArrayOutputStream()

            assertEquals(0, LspServer().run(ByteArrayInputStream(input.toByteArray()), output))

            val lspDiagnostics = output.toString(Charsets.UTF_8)
            assertTrue(cliDiagnostics.contains("[${testCase.code}]"), cliDiagnostics)
            assertTrue(lspDiagnostics.contains("\"code\":\"${testCase.code}\""), lspDiagnostics)
            assertTrue(cliDiagnostics.contains(testCase.message), cliDiagnostics)
            assertTrue(lspDiagnostics.contains(testCase.message), lspDiagnostics)
        }
    }

    private fun frame(message: String): String =
        "Content-Length: ${message.toByteArray(Charsets.UTF_8).size}\r\n\r\n$message"

    private fun captureStdout(action: () -> Unit): String {
        val original = System.out
        val captured = ByteArrayOutputStream()
        System.setOut(PrintStream(captured, true, Charsets.UTF_8))
        return try {
            action()
            captured.toString(Charsets.UTF_8)
        } finally {
            System.setOut(original)
        }
    }

    private fun captureStderr(action: () -> Unit): String {
        val original = System.err
        val captured = ByteArrayOutputStream()
        System.setErr(PrintStream(captured, true, Charsets.UTF_8))
        return try {
            action()
            captured.toString(Charsets.UTF_8)
        } finally {
            System.setErr(original)
        }
    }
}
