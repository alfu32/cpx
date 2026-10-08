package cplus.compiler

import cplus.semantic.CHeaderImportService
import cplus.semantic.CSourceUnit
import cplus.semantic.SemanticAnalyzer
import cplus.core.AstBuilder
import cplus.core.Lexer
import cplus.core.Parser
import cplus.core.SourceFile
import cplus.core.SourceFileId
import kotlin.test.assertEquals
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.nio.file.Files

class CHeaderSdkCorpusTest {
    @Test
    fun inventoriesEveryDeliveredSdkHeaderAsSupportedOpaqueOrUnsupported() {
        val compiler = findExecutable("cc") ?: return
        val manifest = assertNotNull(SdkManifestLoader.load(SdkManifestLocator.defaultManifestPath()).manifest)
        val target = TargetInfo(targetTriple = defaultHostTargetTriple())
        val sdk = assertNotNull(SdkResolver.resolve(manifest, target).resolution)
        val abi = assertNotNull(TargetRegistry.load(sdk.layout.abiDescriptor).descriptor)
        val environment = HeaderEnvironment.create(
            CompileRequest(emptyList(), target = target, cCompiler = compiler),
            sdk,
            abi
        )
        val headers = Files.list(sdk.layout.libcInclude).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".h") }
                .sorted()
                .toList()
        }
        assertTrue(headers.isNotEmpty(), "selected SDK has no delivered C headers")
        val preprocessor = CHeaderPreprocessor()
        val service = CHeaderImportService()
        val inventory = headers.map { header ->
            val result = preprocessor.preprocess(header, environment)
            assertTrue(result.isSuccessful, "${header.fileName}: ${result.diagnostics.joinToString()}")
            val declarations = service.sourceDeclarations(
                result.text,
                result.semanticMacros(),
                result.semanticSourceLineOrigins()
            )
            val supported = declarations.values.count { it.unsupportedReason == null }
            val unsupported = declarations.values.filter { it.unsupportedReason != null }
            val opaqueMacros = result.macros.count { it.parameters != null }
            "${header.fileName}: supported=$supported, unsupported=${unsupported.size}, opaqueMacros=$opaqueMacros"
        }
        println("C HEADER SDK INVENTORY (${headers.size} headers)\n" + inventory.joinToString("\n"))
        assertTrue(inventory.all { it.contains("supported=") && it.contains("unsupported=") })
        assertTrue(headers.any { it.fileName.toString() == "stdio.h" })
        assertTrue(headers.any { it.fileName.toString() == "math.h" })
        assertTrue(headers.any { it.fileName.toString() == "complex.h" })
        assertTrue(headers.any { it.fileName.toString() == "stdint.h" })
        assertTrue(headers.any { it.fileName.toString() == "stdarg.h" })
    }

    @Test
    fun preprocessesAndIndexesRequiredDeliveredSdkHeaders() {
        val compiler = findExecutable("cc") ?: return
        val manifest = assertNotNull(SdkManifestLoader.load(SdkManifestLocator.defaultManifestPath()).manifest)
        val target = TargetInfo(targetTriple = defaultHostTargetTriple())
        val sdk = assertNotNull(SdkResolver.resolve(manifest, target).resolution)
        val abi = assertNotNull(TargetRegistry.load(sdk.layout.abiDescriptor).descriptor)
        val environment = HeaderEnvironment.create(
            CompileRequest(emptyList(), target = target, cCompiler = compiler),
            sdk,
            abi
        )
        val discovery = CHeaderDiscovery()
        val service = CHeaderImportService()
        val expectedSymbols = mapOf(
            "c.stdio" to setOf("printf", "fprintf", "vsnprintf", "fgetc", "puts"),
            "c.math" to setOf("sqrt", "sqrtf", "sqrtl", "frexp", "fma", "cplus_math_isfinite"),
            "c.complex" to setOf("cabs", "cabsf", "cabsl", "cpow", "cpowf", "cpowl"),
            "c.stdint" to setOf("int8_t", "uint64_t"),
            "c.stdarg" to setOf("va_list")
        )

        expectedSymbols.forEach { (module, expected) ->
            val discovered = discovery.discover(module, environment)
            assertTrue(discovered.isSuccessful, "$module: ${discovered.diagnostics.joinToString()}")
            val preprocessed = assertNotNull(discovered.preprocessed)
            val declarations = service.sourceDeclarations(
                preprocessed.text,
                preprocessed.semanticMacros(),
                preprocessed.semanticSourceLineOrigins()
            )
            val absent = expected - declarations.keys
            assertTrue(absent.isEmpty(), "$module omitted $absent; saw ${declarations.keys}")
            val unsupported = declarations.values.filter { it.unsupportedReason != null }
            assertFalse(unsupported.any { it.name in expected }, "$module has unsupported required symbols: $unsupported")
            assertTrue(
                declarations.values.any { it.sourceRange != null },
                "$module has no source-mapped declarations"
            )
            if (module in setOf("c.stdio", "c.math", "c.complex", "c.stdint", "c.stdarg")) {
                val consumer = SourceFile(
                    SourceFileId(900 + expectedSymbols.keys.indexOf(module)),
                    java.nio.file.Path.of("${module.replace('.', '_')}.cp"),
                    "int main() { return 0; }",
                    1
                )
                val semantic = SemanticAnalyzer().analyze(
                    AstBuilder().build(Parser(Lexer().lex(consumer)).parse().syntax),
                    knownModules = setOf(module),
                    targetFeatures = abi.features,
                    targetName = target.targetTriple,
                    foreignSources = listOf(
                        CSourceUnit(
                            SourceFile(SourceFileId(950 + expectedSymbols.keys.indexOf(module)), preprocessed.header, preprocessed.text, 1),
                            module,
                            preprocessed.semanticMacros(),
                            preprocessed.semanticSourceLineOrigins()
                        )
                    )
                )
                val semanticErrors = semantic.diagnostics.filter { it.severity == cplus.core.DiagnosticSeverity.ERROR }
                println("$module semantic audit errors: ${semanticErrors.joinToString { "${it.code}: ${it.message}" }}")
                assertTrue(
                    semanticErrors.isEmpty(),
                    "$module declarations are parseable but not semantically importable: ${semanticErrors.joinToString { "${it.code}: ${it.message}" }}"
                )
            }
        }
    }

    @Test
    fun discoversTemporaryCustomHeaderWithoutKotlinNameRegistrationOrBodyLocals() {
        val compiler = findExecutable("cc") ?: return
        val manifest = assertNotNull(SdkManifestLoader.load(SdkManifestLocator.defaultManifestPath()).manifest)
        val target = TargetInfo(targetTriple = defaultHostTargetTriple())
        val sdk = assertNotNull(SdkResolver.resolve(manifest, target).resolution)
        val abi = assertNotNull(TargetRegistry.load(sdk.layout.abiDescriptor).descriptor)
        val includeRoot = java.nio.file.Files.createTempDirectory("cplus-custom-header-include")
        val demo = java.nio.file.Files.createDirectories(includeRoot.resolve("demo"))
        val header = demo.resolve("coucou.h")
        java.nio.file.Files.writeString(
            header,
            "int coucou(void) { int body_local = 12; return body_local; }\n"
        )
        val environment = HeaderEnvironment.create(
            CompileRequest(emptyList(), target = target, cIncludeDirectories = listOf(includeRoot), cCompiler = compiler),
            sdk,
            abi
        )
        val discovered = CHeaderDiscovery().discover("c.demo.coucou", environment)
        assertTrue(discovered.isSuccessful, discovered.diagnostics.joinToString())
        val preprocessed = assertNotNull(discovered.preprocessed)
        val declarations = CHeaderImportService().sourceDeclarations(
            preprocessed.text,
            preprocessed.semanticMacros(),
            preprocessed.semanticSourceLineOrigins()
        )

        val coucou = declarations.getValue("coucou")
        assertEquals("coucou", coucou.name)
        assertTrue("body_local" !in declarations)
        assertTrue(coucou.sourceRange != null)
        assertEquals(header.toAbsolutePath().normalize(), coucou.externalSource)
        assertEquals(1, coucou.externalLine)
        assertNull(CHeaderImportService().declarations("c.demo.coucou")["coucou"])
    }

    private fun findExecutable(name: String): String? = System.getenv("PATH").orEmpty()
        .split(java.io.File.pathSeparator)
        .asSequence()
        .map { java.nio.file.Path.of(it).resolve(name) }
        .firstOrNull(java.nio.file.Files::isExecutable)
        ?.toString()
}
