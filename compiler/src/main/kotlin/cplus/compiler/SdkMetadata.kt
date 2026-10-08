package cplus.compiler

import cplus.core.*
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Base64

data class SdkSourceMetadata(
    val path: String,
    val contentHash: String,
    val declarations: List<String>,
    val exports: List<String>,
    val documentation: List<String>,
    val comptimeSignatures: List<String>
)

data class SdkSemanticMetadata(
    val schemaVersion: Int,
    val sdkIdentity: String,
    val targetTriple: String,
    val sources: List<SdkSourceMetadata>
)

data class SdkMetadataResult(
    val metadata: SdkSemanticMetadata?,
    val diagnostics: List<Diagnostic>,
    val rebuilt: Boolean
) {
    val isSuccessful: Boolean
        get() = metadata != null && diagnostics.none { it.severity == DiagnosticSeverity.ERROR }
}

object SdkMetadataCache {
    private const val SCHEMA_VERSION = 1
    private const val HEADER = "CPLUS_SDK_METADATA"

    fun loadOrBuild(resolution: SdkResolution): SdkMetadataResult {
        val sourcePaths = sourcePaths(resolution.layout)
        val snapshots = sourcePaths.map { path ->
            Triple(
                path,
                resolution.layout.root.toAbsolutePath().normalize()
                    .relativize(path.toAbsolutePath().normalize()),
                sha256(Files.readAllBytes(path))
            )
        }
        val expectedIdentity = sdkIdentity(resolution.manifest)
        val cachePath = resolution.layout.root.resolve("cache/metadata/${resolution.targetTriple()}.meta")
        val cached = read(cachePath)
        if (cached != null && cached.schemaVersion == SCHEMA_VERSION && cached.sdkIdentity == expectedIdentity && cached.targetTriple == resolution.targetTriple() && matches(cached, snapshots)) {
            return SdkMetadataResult(cached, emptyList(), rebuilt = false)
        }

        val built = build(resolution, snapshots)
        if (!built.isSuccessful) return built
        return try {
            Files.createDirectories(cachePath.parent)
            Files.writeString(cachePath, serialize(built.metadata!!))
            built.copy(rebuilt = true)
        } catch (error: Exception) {
            SdkMetadataResult(
                built.metadata,
                listOf(error("unable to write SDK metadata cache '$cachePath': ${error.message ?: error::class.simpleName}", "SDK011")),
                rebuilt = true
            )
        }
    }

    private fun build(
        resolution: SdkResolution,
        snapshots: List<Triple<Path, Path, String>>
    ): SdkMetadataResult {
        val diagnostics = mutableListOf<Diagnostic>()
        val entries = snapshots.map { (path, relative, contentHash) ->
            val source = SourceFile(
                SourceFileId(entriesId(relative)),
                path,
                Files.readString(path),
                1
            )
            val parsed = Parser(Lexer().lex(source)).parse()
            diagnostics += parsed.diagnostics.filter { it.severity == DiagnosticSeverity.ERROR }.map {
                error("SDK source '${relative.toUnixString()}' is invalid: ${it.message}", "SDK010")
            }
            val declarations = parsed.syntax.declarations.flatMap(::declarationMetadata)
            val exports = parsed.syntax.declarations.filter { it.isPublic }.map(::declarationName)
            val documentation = source.text.lineSequence()
                .map(String::trim)
                .filter { it.startsWith("///") }
                .map { it.removePrefix("///").trim() }
                .toList()
            val comptimeSignatures = parsed.syntax.declarations
                .filterIsInstance<SyntaxComptimeFunction>()
                .map { function ->
                    "${function.name}:${function.category}(${function.parameters.joinToString(",") { "${it.kind}:${it.name}" }})"
                }
            SdkSourceMetadata(
                relative.toUnixString(),
                contentHash,
                declarations.sorted(),
                exports.sorted(),
                documentation,
                comptimeSignatures.sorted()
            )
        }
        if (diagnostics.isNotEmpty()) return SdkMetadataResult(null, diagnostics, rebuilt = true)
        return SdkMetadataResult(
            SdkSemanticMetadata(SCHEMA_VERSION, sdkIdentity(resolution.manifest), resolution.targetTriple(), entries.sortedBy { it.path }),
            emptyList(),
            rebuilt = true
        )
    }

    private fun declarationMetadata(declaration: SyntaxDeclaration): List<String> = when (declaration) {
        is SyntaxStruct -> listOf("struct:${declaration.name}") +
            declaration.fields.map { "field:${declaration.name}.${it.name}:${typeText(it.type)}" } +
            declaration.methods.map { method ->
                "method:${declaration.name}.${method.name}(${method.parameters.joinToString(",") { typeText(it.type) }})"
            }
        is SyntaxUnion -> listOf("union:${declaration.name}") + declaration.fields.map { "field:${declaration.name}.${it.name}:${typeText(it.type)}" }
        is SyntaxEnum -> listOf("enum:${declaration.name}") + declaration.values.map { "enum-value:${declaration.name}.${it.name}" }
        is SyntaxFunction -> listOf("function:${declaration.name}:${typeText(declaration.returnType)}")
        is SyntaxGlobalVariable -> listOf("global:${declaration.name}:${typeText(declaration.type)}")
        is SyntaxAlias -> listOf("alias:${declaration.name}:${typeText(declaration.target)}")
        is SyntaxComptimeFunction -> listOf("comptime:${declaration.name}:${declaration.category}")
        else -> emptyList()
    }

    private fun declarationName(declaration: SyntaxDeclaration): String = when (declaration) {
        is SyntaxStruct -> declaration.name
        is SyntaxUnion -> declaration.name
        is SyntaxEnum -> declaration.name
        is SyntaxFunction -> declaration.name
        is SyntaxGlobalVariable -> declaration.name
        is SyntaxAlias -> declaration.name
        is SyntaxComptimeFunction -> declaration.name
        else -> declaration::class.simpleName ?: "declaration"
    }

    private fun typeText(type: TypeSyntax): String = buildString {
        if (type.declarationKind != "named") append(type.declarationKind).append(' ')
        append(type.name)
        repeat(type.pointerDepth) { append('*') }
    }

    private fun sourcePaths(layout: SdkLayout): List<Path> = listOf(
        layout.stdSource,
        layout.libcSource,
        layout.runtimeSource,
        layout.platformApi,
        layout.platformSource,
        layout.intrinsicsSource.parent,
        layout.startupSource
    ).flatMap { root ->
        if (root == null || !Files.isDirectory(root)) emptyList()
        else Files.walk(root).use { paths -> paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".cp") }.toList() }
    }.distinct().sortedBy(Path::toString)

    private fun matches(metadata: SdkSemanticMetadata, snapshots: List<Triple<Path, Path, String>>): Boolean =
        metadata.sources.map { it.path to it.contentHash } == snapshots
            .map { it.second.toUnixString() to it.third }
            .sortedBy { it.first }

    private fun read(path: Path): SdkSemanticMetadata? = runCatching {
        if (!Files.isRegularFile(path)) return@runCatching null
        val lines = Files.readAllLines(path)
        if (lines.firstOrNull() != HEADER) return@runCatching null
        val values = lines.drop(1).filterNot { it.startsWith("source\t") }.associate { line ->
            val separator = line.indexOf('=')
            line.substring(0, separator) to line.substring(separator + 1)
        }
        val schema = values["schema"]?.toIntOrNull() ?: return@runCatching null
        val sources = lines.drop(1).filter { it.startsWith("source\t") }.mapNotNull { line ->
            val fields = line.split('\t')
            if (fields.size != 7) return@mapNotNull null
            SdkSourceMetadata(
                decode(fields[1]),
                fields[2],
                decodeList(fields[3]),
                decodeList(fields[4]),
                decodeList(fields[5]),
                decodeList(fields[6])
            )
        }
        SdkSemanticMetadata(schema, values["sdk"] ?: return@runCatching null, values["target"] ?: return@runCatching null, sources)
    }.getOrNull()

    private fun serialize(metadata: SdkSemanticMetadata): String = buildString {
        append("$HEADER\n")
        append("schema=${metadata.schemaVersion}\n")
        append("sdk=${metadata.sdkIdentity}\n")
        append("target=${metadata.targetTriple}\n")
        metadata.sources.sortedBy { it.path }.forEach { source ->
            append("source\t")
                .append(encode(source.path)).append('\t')
                .append(source.contentHash).append('\t')
                .append(encodeList(source.declarations)).append('\t')
                .append(encodeList(source.exports)).append('\t')
                .append(encodeList(source.documentation)).append('\t')
                .append(encodeList(source.comptimeSignatures)).append('\n')
        }
    }

    private fun encode(value: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray())

    private fun decode(value: String): String = String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)

    private fun encodeList(values: List<String>): String = encode(values.joinToString("\u001f"))

    private fun decodeList(value: String): List<String> = decode(value).takeIf { it.isNotEmpty() }?.split('\u001f').orEmpty()

    private fun sdkIdentity(manifest: SdkManifest): String = listOf(
        manifest.sdkVersion,
        manifest.languageAbiVersion,
        manifest.runtimeAbiVersion,
        manifest.cplusAbiVersion,
        manifest.libcProfileVersion,
        manifest.contentHash
    ).joinToString(":")

    private fun entriesId(relative: Path): Int = relative.toString().hashCode()

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte) }

    private fun error(message: String, code: String): Diagnostic = Diagnostic(DiagnosticSeverity.ERROR, message, null, code)

    private fun SdkResolution.targetTriple(): String = layout.abiDescriptor.fileName.toString().removeSuffix(".toml")
    private fun Path.toUnixString(): String = toString().replace('\\', '/')
}
