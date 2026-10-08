package cplus.compiler

import cplus.comptime.structuralFingerprint
import cplus.core.*
import cplus.semantic.CHeaderImportService
import cplus.semantic.ForeignDeclarationKind
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

enum class ImportExportKind {
    FUNCTION, VALUE, STRUCT, UNION, ENUM, TYPE_ALIAS, ENUM_VALUE, C_FUNCTION, C_VALUE, C_TYPE
}

enum class ImportVisibility { PUBLIC }

/** A discoverable export; indexing it never binds it into a consumer's scope. */
data class ImportExport(
    val identity: String,
    val name: String,
    val kind: ImportExportKind,
    val signature: String,
    val documentation: String?,
    val provider: String,
    val importReference: String,
    val visibility: ImportVisibility,
    val sourceUri: String,
    val sourceRange: SourceRange?,
    val configurationFingerprint: String
)

data class ImportIndexResult(
    val exports: List<ImportExport>,
    val diagnostics: List<Diagnostic>,
    val configurationFingerprint: String,
    val cacheHit: Boolean = false
)

/** Expanded declarations supplied by an already validated compiler result, never produced by a scan. */
data class ValidatedImportExpansion(
    val sourceFingerprint: String,
    val expandedProgram: SyntaxProgram
)

/** Builds a syntax-backed inventory from source roots and, when configured, real C headers. */
class ImportIndex(
    private val maximumCandidates: Int = ModuleSourceResolver.DEFAULT_MAXIMUM_CANDIDATES
) {
    private data class CacheEntry(
        val result: ImportIndexResult,
        val dependencyFingerprints: Map<Path, String>
    )

    private val cache = object : LinkedHashMap<String, CacheEntry>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?): Boolean = size > 8
    }

    @Synchronized
    fun build(
        sourceRoots: List<Path>,
        sdkRoot: Path? = null,
        overlays: Map<Path, String> = emptyMap(),
        headerEnvironment: HeaderEnvironment? = null,
        validatedExpansions: Map<Path, ValidatedImportExpansion> = emptyMap()
    ): ImportIndexResult {
        val roots = (sourceRoots + listOfNotNull(sdkRoot?.resolve("std/src")))
            .map { it.toAbsolutePath().normalize() }.distinct()
        val resolver = ModuleSourceResolver(roots, sdkRoot, maximumCandidates)
        val candidates = resolver.candidatePaths(overlays.keys.toSet())
        val overlayByPath = overlays.mapKeys { it.key.toAbsolutePath().normalize() }
        val normalizedExpansions = validatedExpansions.mapKeys { it.key.toAbsolutePath().normalize() }
        val sourceTexts = candidates.associate { path ->
            val normalized = path.toAbsolutePath().normalize()
            normalized to (overlayByPath[normalized] ?: runCatching { Files.readString(normalized) }.getOrNull())
        }
        val configFingerprint = fingerprint(buildString {
            roots.forEach { append(it).append('\n') }
            append("sdk=").append(sdkRoot?.toAbsolutePath()?.normalize()).append('\n')
            sdkRoot?.resolve("manifest/sdk.toml")?.let { append("sdkManifest=").append(fileFingerprint(it)).append('\n') }
            append("target=").append(headerEnvironment?.target?.targetTriple).append('\n')
            append("driver=").append(headerEnvironment?.cCompiler).append('\n')
            append("abi=").append(headerEnvironment?.abi).append('\n')
            headerEnvironment?.includeSearchRoots?.forEach { append("include=").append(it).append('\n') }
            headerEnvironment?.let { append("profile=").append(it.target.buildProfile).append('\n') }
            headerEnvironment?.cCompiler?.let(::compilerFingerprint)?.let { append("compilerIdentity=").append(it).append('\n') }
        })
        val headerFiles = headerEnvironment?.let(::discoverHeaderFiles).orEmpty()
        val inputFingerprint = fingerprint(buildString {
            append(configFingerprint).append('\n')
            candidates.sortedBy(Path::toString).forEach { path ->
                val normalized = path.toAbsolutePath().normalize()
                val text = sourceTexts[normalized]
                append("source=").append(normalized).append(':').append(text?.let(::sourceFingerprint) ?: "missing").append('\n')
            }
            headerFiles.forEach { path ->
                append("header=").append(path).append(':').append(fileFingerprint(path)).append('\n')
            }
            normalizedExpansions.toSortedMap(compareBy(Path::toString)).forEach { (path, expansion) ->
                append("expansion=").append(path.toAbsolutePath().normalize()).append(':')
                    .append(expansion.sourceFingerprint).append(':')
                    .append(structuralFingerprint(expansion.expandedProgram)).append('\n')
            }
        })
        cache[inputFingerprint]?.let { cached ->
            if (cached.dependencyFingerprints.all { (path, expected) -> fileFingerprint(path) == expected }) {
                return cached.result.copy(cacheHit = true)
            }
            cache.remove(inputFingerprint)
        }
        val exports = mutableListOf<ImportExport>()
        val diagnostics = mutableListOf<Diagnostic>()
        val parsedModules = linkedMapOf<Path, Pair<String, SyntaxProgram>>()
        val requestedCModules = linkedSetOf<String>()
        val dependencies = linkedSetOf<Path>()
        candidates.forEach { path ->
            val normalized = path.toAbsolutePath().normalize()
            val text = sourceTexts[normalized] ?: return@forEach
            val source = SourceFile(SourceFileId(normalized.toString().hashCode()), normalized, text, 0)
            val parsed = Parser(Lexer().lex(source)).parse()
            if (parsed.diagnostics.any { it.severity == DiagnosticSeverity.ERROR }) {
                diagnostics += parsed.diagnostics
                return@forEach
            }
            val provider = providerFor(normalized, roots, sdkRoot, parsed.syntax)
            val expectedSourceFingerprint = sourceFingerprint(text)
            val expandedProgram = normalizedExpansions[normalized]
                ?.takeIf { it.sourceFingerprint == expectedSourceFingerprint }
                ?.expandedProgram
            val exportProgram = expandedProgram ?: parsed.syntax
            parsedModules[normalized] = provider to exportProgram
            val contentFingerprint = fingerprint(configFingerprint + normalized + text + (expandedProgram?.let(::structuralFingerprint).orEmpty()))
            syntaxExports(exportProgram, text, normalized, provider, contentFingerprint)
                .let(exports::addAll)
        }

        if (headerEnvironment != null) {
            requestedCModules += parsedModules.values.flatMap { (_, program) ->
                program.declarations.filterIsInstance<SyntaxImport>()
                    .map(SyntaxImport::module).filter { it.startsWith("c.") || it.startsWith("c/") }
            }
            val cModules = (requestedCModules + discoverHeaderModules(headerEnvironment)).distinct().sorted()
            val discovery = CHeaderDiscovery()
            val parser = CHeaderImportService()
            cModules.forEach { module ->
                val result = discovery.discover(module, headerEnvironment)
                if (!result.isSuccessful) {
                    if (module in requestedCModules) diagnostics += result.diagnostics
                    return@forEach
                }
                val prepared = result.preprocessed ?: return@forEach
                dependencies += prepared.includedFiles.map { it.toAbsolutePath().normalize() }
                val declarations = parser.sourceDeclarations(
                    prepared.text, prepared.semanticMacros(), prepared.semanticSourceLineOrigins()
                )
                declarations.values.filter { it.unsupportedReason == null }.forEach { declaration ->
                    val kind = when (declaration.kind) {
                        ForeignDeclarationKind.FUNCTION -> ImportExportKind.C_FUNCTION
                        ForeignDeclarationKind.GLOBAL, ForeignDeclarationKind.ENUM_VALUE -> ImportExportKind.C_VALUE
                        ForeignDeclarationKind.TYPE -> ImportExportKind.C_TYPE
                    }
                    val signature = when (declaration.kind) {
                        ForeignDeclarationKind.FUNCTION -> "${declaration.typeName ?: "void"} ${declaration.name}(${declaration.parameterTypes.joinToString(", ")}${if (declaration.isVariadic) if (declaration.parameterTypes.isEmpty()) "..." else ", ..." else ""})"
                        ForeignDeclarationKind.TYPE -> declaration.typeName ?: declaration.name
                        else -> declaration.typeName?.let { "$it ${declaration.name}" } ?: declaration.name
                    }
                    val location = declaration.externalSource ?: result.header ?: return@forEach
                    val sourceRange = declaration.externalLine?.let { line ->
                        runCatching {
                            val sourceText = Files.readString(location)
                            val starts = lineStarts(sourceText)
                            val start = starts.getOrNull(line - 1) ?: return@runCatching null
                            val end = sourceText.indexOf('\n', start).let { if (it < 0) sourceText.length else it }
                            SourceRange(SourceFileId(location.toString().hashCode()), start, end)
                        }.getOrNull()
                    }
                    exports += ImportExport(
                        identity = "${module}:${declaration.kind}:${declaration.name}",
                        name = declaration.name,
                        kind = kind,
                        signature = signature,
                        documentation = null,
                        provider = module,
                        importReference = module,
                        visibility = ImportVisibility.PUBLIC,
                        sourceUri = location.toUri().toString(),
                        sourceRange = sourceRange,
                        configurationFingerprint = fingerprint(configFingerprint + module + prepared.header + prepared.text)
                    )
                }
            }
        }
        val result = ImportIndexResult(
            exports.distinctBy(ImportExport::identity).sortedWith(compareBy(ImportExport::provider, ImportExport::name, ImportExport::kind)),
            diagnostics,
            configFingerprint
        )
        cache[inputFingerprint] = CacheEntry(
            result,
            dependencies.associateWith(::fileFingerprint)
        )
        return result
    }

    private fun syntaxExports(
        program: SyntaxProgram,
        text: String,
        path: Path,
        provider: String,
        fingerprint: String
    ): List<ImportExport> {
        val result = mutableListOf<ImportExport>()
        fun add(name: String, kind: ImportExportKind, signature: String, range: SourceRange, docOffset: Int = range.startOffset) {
            val documentation = leadingDocumentation(text, docOffset)
            result += ImportExport(
                identity = "${path.toAbsolutePath().normalize()}:$kind:$name",
                name = name,
                kind = kind,
                signature = signature,
                documentation = documentation,
                provider = provider,
                importReference = provider,
                visibility = ImportVisibility.PUBLIC,
                sourceUri = path.toUri().toString(),
                sourceRange = range,
                configurationFingerprint = fingerprint
            )
        }
        program.declarations.filter(SyntaxDeclaration::isPublic).forEach { declaration ->
            when (declaration) {
                is SyntaxFunction -> add(declaration.name, ImportExportKind.FUNCTION,
                    "${typeText(declaration.returnType)} ${declaration.name}(${declaration.parameters.joinToString(", ") { "${typeText(it.type)} ${it.name}" }})",
                    declaration.range, declaration.range.startOffset)
                is SyntaxGlobalVariable -> add(declaration.name, ImportExportKind.VALUE,
                    "${typeText(declaration.type)} ${declaration.name}", declaration.range)
                is SyntaxStruct -> {
                    add(declaration.name, ImportExportKind.STRUCT, "struct ${declaration.name}", declaration.range)
                    declaration.fields.forEach { add(it.name, ImportExportKind.VALUE,
                        "${typeText(it.type)} ${declaration.name}.${it.name}", it.range, declaration.range.startOffset) }
                    declaration.methods.filter(SyntaxFunction::isPublic).forEach { method -> add(method.name,
                        ImportExportKind.FUNCTION, "${typeText(method.returnType)} ${declaration.name}.${method.name}(...)", method.range) }
                }
                is SyntaxUnion -> {
                    add(declaration.name, ImportExportKind.UNION, "union ${declaration.name}", declaration.range)
                    declaration.fields.forEach { add(it.name, ImportExportKind.VALUE,
                        "${typeText(it.type)} ${declaration.name}.${it.name}", it.range, declaration.range.startOffset) }
                }
                is SyntaxEnum -> {
                    add(declaration.name, ImportExportKind.ENUM, "enum ${declaration.name}", declaration.range)
                    declaration.values.forEach { add(it.name, ImportExportKind.ENUM_VALUE,
                        "${declaration.name}.${it.name}", it.range, declaration.range.startOffset) }
                }
                is SyntaxAlias -> add(declaration.name, ImportExportKind.TYPE_ALIAS,
                    "type ${declaration.name} = ${typeText(declaration.target)}", declaration.range)
                else -> Unit // CPX invocations are intentionally not executed or guessed during indexing.
            }
        }
        return result
    }

    private fun providerFor(path: Path, roots: List<Path>, sdkRoot: Path?, program: SyntaxProgram): String {
        val root = if (sdkRoot != null && path.startsWith(sdkRoot.resolve("std/src"))) sdkRoot.resolve("std/src")
            else roots.firstOrNull(path::startsWith) ?: path.parent
        val relative = root.relativize(path).toString().replace('\\', '/').removeSuffix(".cp")
        val prefix = if (sdkRoot != null && root == sdkRoot.resolve("std/src")) "std." else ""
        val declaredPackage = program.declarations.filterIsInstance<SyntaxPackage>().firstOrNull()?.name
        return prefix + (declaredPackage ?: relative.replace('/', '.'))
    }

    private fun leadingDocumentation(text: String, declarationOffset: Int): String? {
        if (declarationOffset !in 0..text.length) return null
        var before = text.substring(0, declarationOffset).trimEnd()
        while (Regex("(?:pub|static|extern|inline|thread_local)\\s*$").containsMatchIn(before)) {
            before = before.replace(Regex("(?:pub|static|extern|inline|thread_local)\\s*$"), "").trimEnd()
        }
        val lines = before.lineSequence().toList().asReversed().takeWhile { it.trimStart().startsWith("///") }.asReversed()
        return lines.takeIf { it.isNotEmpty() }?.joinToString("\n") { it.trim().removePrefix("///").trim() }
    }

    private fun typeText(type: TypeSyntax): String = buildString {
        if (type.declarationKind != "named") append(type.declarationKind).append(' ')
        append(type.name)
        repeat(type.pointerDepth) { append('*') }
    }

    private fun lineStarts(text: String): IntArray = buildList {
        add(0)
        text.forEachIndexed { index, character -> if (character == '\n') add(index + 1) }
    }.toIntArray()

    private fun discoverHeaderModules(environment: HeaderEnvironment): Set<String> {
        val modules = linkedSetOf<String>()
        discoverHeaderFiles(environment).forEach { header ->
            val root = environment.includeSearchRoots.firstOrNull(header::startsWith) ?: return@forEach
            val modulePath = root.relativize(header).toString().replace('\\', '/').removeSuffix(".h")
            if (modulePath.split('/').all(HEADER_SEGMENT::matches)) modules += "c." + modulePath.replace('/', '.')
        }
        return modules
    }

    private fun discoverHeaderFiles(environment: HeaderEnvironment): List<Path> {
        val headers = linkedSetOf<Path>()
        for (root in environment.includeSearchRoots) {
            val remaining = maximumCandidates - headers.size
            if (!Files.isDirectory(root) || remaining <= 0) continue
            runCatching {
                Files.walk(root).use { paths ->
                    paths.filter { path ->
                        val relative = root.relativize(path)
                        relative.none { it.toString() in EXCLUDED_DIRECTORIES } &&
                            Files.isRegularFile(path) && path.fileName.toString().endsWith(".h")
                    }.map { it.toAbsolutePath().normalize() }
                        .sorted()
                        .limit(remaining.toLong())
                        .forEach(headers::add)
                }
            }
        }
        return headers.toList()
    }

    private fun fingerprint(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun fileFingerprint(path: Path): String = runCatching {
        MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))
            .joinToString("") { "%02x".format(it) }
    }.getOrElse { "missing" }

    private fun compilerFingerprint(compiler: String): String {
        val executable = runCatching {
            val direct = Path.of(compiler)
            if (Files.isRegularFile(direct)) direct else {
                System.getenv("PATH").orEmpty().split(java.io.File.pathSeparator)
                    .map { Path.of(it).resolve(compiler) }.firstOrNull(Files::isRegularFile)
            }
        }.getOrNull() ?: return compiler
        val normalized = executable.toAbsolutePath().normalize()
        val identity = runCatching {
            "${Files.size(normalized)}:${Files.getLastModifiedTime(normalized).toMillis()}"
        }.getOrDefault("missing")
        return fingerprint("$compiler:$normalized:$identity")
    }

    companion object {
        private val HEADER_SEGMENT = Regex("[A-Za-z_][A-Za-z0-9_-]*")
        private val EXCLUDED_DIRECTORIES = setOf(".git", ".hg", ".svn", ".gradle", "build", "node_modules", "target", "dist")
        fun sourceFingerprint(text: String): String = MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
