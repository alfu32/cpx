package cplus.compiler

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ImportIndexTest {
    @Test
    fun indexesOnlyPublicSourceDeclarationsWithProviderAndSourceMetadata() {
        val root = Files.createTempDirectory("cplus-import-index")
        val provider = Files.createDirectories(root.resolve("math")).resolve("numbers.cp")
        Files.writeString(provider, """
            /// Add two integers.
            pub int add(int left, int right) { return left + right; }
            int private_helper() { return 0; }
            pub struct Pair { int left; int right; };
            pub enum Direction { North, South };
            pub typedef int Number;
        """.trimIndent())

        val result = ImportIndex().build(listOf(root))

        assertTrue(result.diagnostics.isEmpty(), result.diagnostics.joinToString { "${it.code}: ${it.message}" })
        val add = result.exports.singleOrNull { it.name == "add" }
        assertTrue(add != null, "exports=${result.exports}; diagnostics=${result.diagnostics}")
        requireNotNull(add)
        assertEquals(ImportExportKind.FUNCTION, add.kind)
        assertEquals("math.numbers", add.importReference)
        assertEquals("int add(int left, int right)", add.signature)
        assertEquals("Add two integers.", add.documentation)
        assertTrue(add.sourceUri.endsWith("/math/numbers.cp"))
        assertTrue(add.sourceRange != null)
        assertFalse(result.exports.any { it.name == "private_helper" })
        assertTrue(result.exports.any { it.name == "Pair" && it.kind == ImportExportKind.STRUCT })
        assertTrue(result.exports.any { it.name == "left" && it.signature == "int Pair.left" })
        assertTrue(result.exports.any { it.name == "North" && it.kind == ImportExportKind.ENUM_VALUE })
        assertTrue(result.exports.any { it.name == "Number" && it.kind == ImportExportKind.TYPE_ALIAS })
    }

    @Test
    fun keepsSameNamedProvidersSeparateAndFingerprintTracksSourceChanges() {
        val root = Files.createTempDirectory("cplus-import-index-ambiguous")
        val first = Files.createDirectories(root.resolve("first")).resolve("api.cp")
        val second = Files.createDirectories(root.resolve("second")).resolve("api.cp")
        Files.writeString(first, "pub int open() { return 1; }")
        Files.writeString(second, "pub long open() { return 2; }")

        val initial = ImportIndex().build(listOf(root))
        assertEquals(setOf("first.api", "second.api"), initial.exports.map { it.provider }.toSet())
        assertEquals(2, initial.exports.count { it.name == "open" })
        val oldFingerprint = initial.exports.first().configurationFingerprint

        Files.writeString(first, "pub int open() { return 3; }")
        val changed = ImportIndex().build(listOf(root))
        assertNotEquals(oldFingerprint, changed.exports.first { it.provider == "first.api" }.configurationFingerprint)
        assertFalse(changed.exports.any { it.identity == initial.exports.first { e -> e.provider == "first.api" }.identity && it.provider != "first.api" })
    }

    @Test
    fun indexesSelectedSdkStdModulesAndHonorsOverlays() {
        val sdk = Files.createTempDirectory("cplus-import-index-sdk")
        val stdRoot = Files.createDirectories(sdk.resolve("std/src"))
        val path = stdRoot.resolve("io.cp")
        Files.writeString(path, "pub int stale() { return 0; }")
        val overlay = "pub int open_file() { return 0; }"

        val result = ImportIndex().build(emptyList(), sdk, mapOf(path to overlay))

        assertTrue(result.exports.any { it.provider == "std.io" && it.name == "open_file" })
        assertFalse(result.exports.any { it.name == "stale" })
    }

    @Test
    fun discoversCHeaderExportsOnlyFromImportedRealHeader() {
        val root = Files.createTempDirectory("cplus-import-index-c")
        Files.writeString(root.resolve("main.cp"), "int main() { return 0; }")
        val manifest = requireNotNull(SdkManifestLoader.load(SdkManifestLocator.defaultManifestPath()).manifest)
        val target = TargetInfo(targetTriple = defaultHostTargetTriple())
        val sdk = requireNotNull(SdkResolver.resolve(manifest, target).resolution)
        val abi = requireNotNull(TargetRegistry.load(sdk.layout.abiDescriptor).descriptor)
        val driver = listOf("cc", "gcc", "clang", "tcc").firstOrNull(::available) ?: return
        val environment = HeaderEnvironment.create(CompileRequest(emptyList(), target = target, cCompiler = driver), sdk, abi)

        val result = ImportIndex().build(listOf(root), headerEnvironment = environment)

        assertTrue(result.diagnostics.isEmpty(), result.diagnostics.joinToString())
        val printf = result.exports.single { it.provider == "c.stdio" && it.name == "printf" && it.kind == ImportExportKind.C_FUNCTION }
        assertTrue(printf.sourceUri.endsWith("/stdio.h"))
        assertTrue(printf.sourceRange != null)
    }

    @Test
    fun usesOnlySourceMatchedValidatedExpansionMetadataForGeneratedExports() {
        val root = Files.createTempDirectory("cplus-import-index-cpx")
        val module = root.resolve("generated.cp")
        val source = "maker();"
        Files.writeString(module, source)
        val expandedSource = cplus.core.SourceFile(cplus.core.SourceFileId(91), module, "pub int generated_value() { return 7; }", 0)
        val expanded = cplus.core.Parser(cplus.core.Lexer().lex(expandedSource)).parse()

        val accepted = ImportIndex().build(
            listOf(root),
            validatedExpansions = mapOf(module to ValidatedImportExpansion(ImportIndex.sourceFingerprint(source), expanded.syntax))
        )
        val stale = ImportIndex().build(
            listOf(root),
            validatedExpansions = mapOf(module to ValidatedImportExpansion("stale", expanded.syntax))
        )

        assertTrue(accepted.exports.any { it.name == "generated_value" })
        assertFalse(stale.exports.any { it.name == "generated_value" })
    }

    private fun available(name: String): Boolean = System.getenv("PATH").orEmpty()
        .split(java.io.File.pathSeparator).any { Files.isExecutable(java.nio.file.Path.of(it).resolve(name)) }
}
