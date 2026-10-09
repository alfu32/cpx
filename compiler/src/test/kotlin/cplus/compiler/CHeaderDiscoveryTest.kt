package cplus.compiler

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CHeaderDiscoveryTest {
    @Test
    fun resolvesLogicalAndSlashModulePathsWithoutEscapingRoots() {
        val root = Files.createTempDirectory("cplus-header-roots")
        Files.createDirectories(root.resolve("demo"))
        Files.writeString(root.resolve("demo/api.h"), "int api(void);\n")
        val environment = environment(root, "cc")
        val discovery = CHeaderDiscovery()

        val dotted = discovery.discover("c.demo.api", environment)
        val slashed = discovery.discover("c/demo/api", environment)
        val traversal = discovery.discover("c../outside", environment)
        val missing = discovery.discover("c.demo.absent", environment)

        assertTrue(dotted.isSuccessful, dotted.diagnostics.joinToString())
        assertTrue(slashed.isSuccessful, slashed.diagnostics.joinToString())
        assertEquals(dotted.header, slashed.header)
        assertFalse(traversal.isSuccessful)
        assertFalse(missing.isSuccessful)
    }

    @Test
    fun selectedPreprocessorHonorsIncludesConditionalsAndPathSpaces() {
        val parent = Files.createTempDirectory("cplus header discovery ")
        val root = Files.createDirectories(parent.resolve("include root with spaces"))
        val nested = Files.createDirectories(root.resolve("demo/nested"))
        val header = Files.createDirectories(root.resolve("demo")).resolve("api.h")
        val nestedHeader = nested.resolve("detail.h")
        Files.writeString(nestedHeader, "#define DETAIL_VALUE 9\n")
        Files.writeString(
            header,
            """
            #ifndef DEMO_API_H
            #define DEMO_API_H
            #define HEADER_VALUE 33
            #define REDEFINED_VALUE 1
            #undef REDEFINED_VALUE
            #define REDEFINED_VALUE 2
            #define ZERO_ARGUMENT_MACRO() 7
            #if 0
            #define INACTIVE_MACRO 9
            #endif
            #include "nested/detail.h"
            #if defined(__linux__)
            #define ACTIVE_PLATFORM 1
            #else
            #define INACTIVE_PLATFORM 1
            #endif
            static inline int header_answer(void) { return HEADER_VALUE + DETAIL_VALUE; }
            #endif
            """.trimIndent() + "\n"
        )

        val available = listOf("cc", "gcc", "clang", "tcc")
            .mapNotNull(::findExecutable)
            .distinct()
        assertTrue(available.isNotEmpty(), "expected at least one supported local C driver")
        for (compiler in available) {
            val result = CHeaderDiscovery().discover("c.demo.api", environment(root, compiler))
            val output = assertNotNull(result.preprocessed)
            val linuxHost = defaultHostTargetTriple().startsWith("linux-")
            assertTrue(result.isSuccessful, "$compiler: ${result.diagnostics.joinToString()}")
            assertTrue(output.text.contains("header_answer"), compiler)
            assertEquals(linuxHost, output.text.contains("ACTIVE_PLATFORM"), compiler)
            assertEquals(!linuxHost, output.text.contains("INACTIVE_PLATFORM"), compiler)
            assertTrue(output.includedFiles.contains(header.toAbsolutePath().normalize()), compiler)
            assertTrue(output.includedFiles.contains(nestedHeader.toAbsolutePath().normalize()), compiler)
            assertTrue(
                output.macros.any { it.name == "HEADER_VALUE" && it.source == header.toAbsolutePath().normalize() },
                "$compiler did not retain the selected header macro origin"
            )
            assertTrue(output.macros.any { it.name == "DETAIL_VALUE" }, compiler)
            assertEquals("2", output.macros.single { it.name == "REDEFINED_VALUE" }.replacement.trim(), compiler)
            assertFalse(output.macros.any { it.name == "INACTIVE_MACRO" }, compiler)
            assertEquals("", output.macros.single { it.name == "ZERO_ARGUMENT_MACRO" }.parameters, compiler)
            val declarations = cplus.semantic.CHeaderImportService()
                .sourceDeclarations(output.text, output.semanticMacros())
            assertEquals("int", declarations.getValue("REDEFINED_VALUE").typeName, compiler)
            assertEquals("2", declarations.getValue("REDEFINED_VALUE").constantExpression, compiler)
            assertTrue("INACTIVE_MACRO" !in declarations, compiler)
            assertTrue("ZERO_ARGUMENT_MACRO" !in declarations, compiler)
            assertTrue(output.command.none { it == "include root with spaces" }, "include paths must be one argv item")
        }
    }

    @Test
    fun resolvesHeadersByConfiguredIncludeRootOrder() {
        val firstRoot = Files.createDirectories(Files.createTempDirectory("cplus-header-first").resolve("demo"))
            .parent
        val secondRoot = Files.createDirectories(Files.createTempDirectory("cplus-header-second").resolve("demo"))
            .parent
        Files.writeString(firstRoot.resolve("demo/api.h"), "#define SELECTED_ROOT 1\n")
        Files.writeString(secondRoot.resolve("demo/api.h"), "#define SELECTED_ROOT 2\n")
        val env = environment(firstRoot, "cc").copy(includeDirectories = listOf(firstRoot, secondRoot))

        val result = CHeaderDiscovery().discover("c.demo.api", env)

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        assertEquals(firstRoot.resolve("demo/api.h").toAbsolutePath().normalize(), result.header)
        assertTrue(result.preprocessed?.macros?.any { it.name == "SELECTED_ROOT" && it.replacement == "1" } == true)
    }

    @Test
    fun constructsSeparatePreprocessorCommandsForSupportedDriverFamilies() {
        val root = Files.createTempDirectory("cplus-header-command")
        val header = Files.writeString(root.resolve("api.h"), "int api(void);\n")
        val sdk = assertNotNull(SdkManifestLoader.load(SdkManifestLocator.defaultManifestPath()).manifest)
        val resolution = assertNotNull(SdkResolver.resolve(sdk, TargetInfo()).resolution)
        val descriptor = assertNotNull(TargetRegistry.load(resolution.layout.abiDescriptor).descriptor)
        val include = Files.createDirectories(root.resolve("configured include"))
        val preprocessor = CHeaderPreprocessor()
        val cases = listOf(
            "gcc" to listOf("-E", "-dD", "-nostdinc"),
            "clang" to listOf("-E", "-dD", "-nostdinc"),
            "tcc" to listOf("-E", "-dD", "-nostdinc"),
            "cl.exe" to listOf("/E", "/PD", "/X", "/I$include"),
            "clang-cl.exe" to listOf("/E", "/PD", "/X", "/I$include")
        )

        for ((driver, expectedFlags) in cases) {
            val env = HeaderEnvironment.create(
                CompileRequest(
                    emptyList(),
                    cIncludeDirectories = listOf(include),
                    cCompiler = driver
                ),
                resolution,
                descriptor
            )
            val command = preprocessor.command(env, header)
            expectedFlags.forEach { flag -> assertTrue(command.contains(flag), "$driver missing $flag: $command") }
            assertTrue(command.any { it.contains(header.toString()) }, "$driver did not include input: $command")
        }
    }

    @Test
    fun rejectsAnUnknownDriverAndReportsPreprocessorFailures() {
        val root = Files.createTempDirectory("cplus-header-failures")
        val header = Files.writeString(root.resolve("broken.h"), "#include <missing-dependency.h>\n")
        val unknown = CHeaderPreprocessor().preprocess(header, environment(root, "cplus-unknown"))
        assertFalse(unknown.isSuccessful)
        assertEquals("CIMP011", unknown.diagnostics.single().code)

        val gcc = findExecutable("cc")
        if (gcc != null) {
            val failed = CHeaderPreprocessor().preprocess(header, environment(root, gcc))
            assertFalse(failed.isSuccessful)
            assertEquals("CIMP016", failed.diagnostics.single().code)
        }
    }

    @Test
    fun boundsPreprocessorOutputAndExecutionTime() {
        if (System.getProperty("os.name").contains("windows", ignoreCase = true)) return
        val root = Files.createTempDirectory("cplus-header-process-limits")
        val header = Files.writeString(root.resolve("api.h"), "int api(void);\n")
        val noisyDriver = root.resolve("noisy-gcc")
        Files.writeString(noisyDriver, "#!/bin/sh\nprintf '0123456789abcdef0123456789abcdef0123456789abcdef'\n")
        assertTrue(noisyDriver.toFile().setExecutable(true))
        val outputLimit = CHeaderPreprocessor(maximumOutputBytes = 16).preprocess(header, environment(root, noisyDriver.toString()))
        assertEquals("CIMP015", outputLimit.diagnostics.single().code)

        val slowDriver = root.resolve("slow-gcc")
        Files.writeString(slowDriver, "#!/bin/sh\nexec sleep 2\n")
        assertTrue(slowDriver.toFile().setExecutable(true))
        val timeLimit = CHeaderPreprocessor(timeoutMillis = 100).preprocess(header, environment(root, slowDriver.toString()))
        assertEquals("CIMP014", timeLimit.diagnostics.single().code)
    }

    private fun environment(include: Path, compiler: String): HeaderEnvironment {
        val manifest = assertNotNull(SdkManifestLoader.load(SdkManifestLocator.defaultManifestPath()).manifest)
        val target = TargetInfo(targetTriple = defaultHostTargetTriple())
        val sdk = assertNotNull(SdkResolver.resolve(manifest, target).resolution)
        val abi = assertNotNull(TargetRegistry.load(sdk.layout.abiDescriptor).descriptor)
        return HeaderEnvironment.create(
            CompileRequest(emptyList(), target = target, cIncludeDirectories = listOf(include), cCompiler = compiler),
            sdk,
            abi
        )
    }

    private fun findExecutable(name: String): String? {
        val path = System.getenv("PATH").orEmpty().split(java.io.File.pathSeparator)
        val executableNames = if (System.getProperty("os.name").contains("windows", ignoreCase = true)) {
            listOf(name, "$name.exe")
        } else {
            listOf(name)
        }
        return path.asSequence()
            .flatMap { directory -> executableNames.asSequence().map { Path.of(directory).resolve(it) } }
            .firstOrNull { Files.isRegularFile(it) && Files.isExecutable(it) }
            ?.toString()
    }
}
