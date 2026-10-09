package cplus.cli

import cplus.compiler.CPlusCompiler
import cplus.compiler.CompileRequest
import cplus.compiler.HeaderEnvironment
import cplus.compiler.ImportIndex
import cplus.compiler.SdkManifestLocator
import cplus.compiler.SdkManifestLoader
import cplus.compiler.SdkResolver
import cplus.compiler.TargetInfo
import cplus.compiler.TargetRegistry
import cplus.compiler.TextSource
import cplus.compiler.defaultHostTargetTriple
import cplus.core.DiagnosticSeverity
import cplus.core.LineIndex
import cplus.core.SourceRange
import cplus.compiler.ModuleSourceResolver
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal class LspServer(
    private val compiler: CPlusCompiler = CPlusCompiler()
) {
    private val workspace = LspWorkspace()
    private val workspaceRoots = linkedSetOf<Path>()
    private val configuredIncludeDirectories = linkedSetOf<Path>()
    private val importIndex = ImportIndex()
    private var sdkManifest: Path = SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize()
    private var shutdownRequested = false
    private data class PendingRequest(
        val responded: AtomicBoolean = AtomicBoolean(false),
        val future: AtomicReference<Future<*>?> = AtomicReference(null)
    )
    private val pendingRequests = ConcurrentHashMap<Any, PendingRequest>()
    private var requestExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    fun run(input: InputStream, output: OutputStream): Int {
        requestExecutor = Executors.newSingleThreadExecutor()
        pendingRequests.clear()
        val inputStream = input.buffered()
        val outputStream = output.buffered()
        loop@ while (true) {
            val body = readMessage(inputStream) ?: break
            val message = runCatching { Json.parse(body) as? Map<*, *> }.getOrNull() ?: continue
            val method = message["method"] as? String ?: continue
            val id = message["id"]
            val params = message["params"] as? Map<*, *> ?: emptyMap<String, Any?>()
            when (method) {
                "initialize" -> {
                    writeMessage(outputStream, response(id, initializeResult(params)))
                }
                "initialized" -> Unit
                "$/cancelRequest" -> cancelRequest(params, outputStream)
                "shutdown" -> {
                    shutdownRequested = true
                    writeMessage(outputStream, response(id, null))
                }
                "exit" -> break@loop
                "textDocument/didOpen" -> {
                    didOpen(params)
                }
                "textDocument/didChange" -> {
                    didChange(params)
                }
                "textDocument/didClose" -> {
                    didClose(params, outputStream)
                }
                "workspace/didChangeWatchedFiles" -> {
                    didChangeWatchedFiles(outputStream)
                }
                "textDocument/semanticTokens/full" -> {
                    if (id != null) scheduleRequest(id, method, params, outputStream)
                }
                "textDocument/completion" -> {
                    if (id != null) scheduleRequest(id, method, params, outputStream)
                }
                "textDocument/codeAction" -> {
                    if (id != null) scheduleRequest(id, method, params, outputStream)
                }
                "textDocument/hover" -> {
                    if (id != null) scheduleRequest(id, method, params, outputStream)
                }
                "textDocument/definition" -> {
                    if (id != null) scheduleRequest(id, method, params, outputStream)
                }
                "textDocument/references" -> {
                    if (id != null) scheduleRequest(id, method, params, outputStream)
                }
                "textDocument/documentSymbol" -> {
                    if (id != null) scheduleRequest(id, method, params, outputStream)
                }
                "textDocument/rename" -> {
                    if (id != null) scheduleRequest(id, method, params, outputStream)
                }
                "textDocument/signatureHelp" -> {
                    if (id != null) scheduleRequest(id, method, params, outputStream)
                }
                else -> {
                    if (id != null) {
                        writeMessage(outputStream, errorResponse(id, -32601, "method not found: $method"))
                    }
                }
            }
            if (method == "textDocument/didOpen" || method == "textDocument/didChange") {
                val uri = (params["textDocument"] as? Map<*, *>)?.get("uri") as? String
                if (uri != null) scheduleDiagnosticsForChangedDocument(uri, outputStream)
            }
            outputStream.flush()
            if (method == "exit" || (shutdownRequested && method != "shutdown")) break@loop
        }
        requestExecutor.shutdown()
        if (!requestExecutor.awaitTermination(2, TimeUnit.MINUTES)) requestExecutor.shutdownNow()
        outputStream.flush()
        return 0
    }

    private fun scheduleRequest(id: Any, method: String, params: Map<*, *>, output: OutputStream) {
        val token = PendingRequest()
        pendingRequests.put(id, token)?.let { previous ->
            previous.responded.set(true)
            previous.future.get()?.cancel(true)
        }
        val uri = (params["textDocument"] as? Map<*, *>)?.get("uri") as? String
        val requestedVersion = uri?.let { workspace.get(it)?.version }
        val future = requestExecutor.submit {
            val result = runCatching { requestResult(method, params) }
            if (token.responded.get()) return@submit
            val currentVersion = uri?.let { workspace.get(it)?.version }
            val message = when {
                result.isFailure -> errorResponse(id, -32603, result.exceptionOrNull()?.message ?: "request failed")
                uri != null && requestedVersion != currentVersion -> errorResponse(id, -32801, "document changed while request was running")
                else -> response(id, result.getOrNull())
            }
            if (token.responded.compareAndSet(false, true)) writeMessage(output, message)
            pendingRequests.remove(id, token)
        }
        token.future.set(future)
    }

    private fun requestResult(method: String, params: Map<*, *>): Any? = when (method) {
        "textDocument/semanticTokens/full" -> semanticTokens(params)
        "textDocument/completion" -> completion(params)
        "textDocument/codeAction" -> codeActions(params)
        "textDocument/hover" -> hover(params)
        "textDocument/definition" -> definition(params)
        "textDocument/references" -> references(params)
        "textDocument/documentSymbol" -> documentSymbols(params)
        "textDocument/rename" -> rename(params)
        "textDocument/signatureHelp" -> signatureHelp(params)
        else -> null
    }

    private fun cancelRequest(params: Map<*, *>, output: OutputStream) {
        val id = params["id"] ?: return
        val pending = pendingRequests[id] ?: return
        pending.future.get()?.cancel(true)
        if (pending.responded.compareAndSet(false, true)) {
            writeMessage(output, errorResponse(id, -32800, "request cancelled"))
        }
        pendingRequests.remove(id, pending)
    }

    private fun scheduleDiagnostics(uri: String, output: OutputStream) {
        val requested = workspace.get(uri) ?: return
        requestExecutor.submit {
            val result = compileWorkspace(requested)
            val current = workspace.get(uri)
            if (current?.version != requested.version) return@submit
            connectedOpenDocuments(current).forEach { document ->
                if (workspace.get(document.uri)?.version == document.version) {
                    publishDiagnostics(result, document, output)
                }
            }
        }
    }

    private fun scheduleDiagnosticsForChangedDocument(uri: String, output: OutputStream) {
        val changed = workspace.get(uri) ?: return
        val changedPath = changed.path.toAbsolutePath().normalize()
        workspace.snapshot().filter { document ->
            document.path.toAbsolutePath().normalize() == changedPath ||
                connectedOpenDocuments(document).any { dependency ->
                    dependency.path.toAbsolutePath().normalize() == changedPath
                }
        }.forEach { affected -> scheduleDiagnostics(affected.uri, output) }
    }

    private fun didOpen(params: Map<*, *>) {
        val document = params["textDocument"] as? Map<*, *> ?: return
        val uri = document["uri"] as? String ?: return
        val text = document["text"] as? String ?: return
        val version = (document["version"] as? Number)?.toInt() ?: 0
        val path = pathForUri(uri) ?: return
        workspace.open(uri, path, version, text)
    }

    private fun didChange(params: Map<*, *>) {
        val document = params["textDocument"] as? Map<*, *> ?: return
        val uri = document["uri"] as? String ?: return
        val changes = params["contentChanges"] as? List<*> ?: return
        val parsedChanges = changes.mapNotNull(::parseChange)
        if (parsedChanges.size != changes.size) return
        val version = (document["version"] as? Number)?.toInt() ?: workspace.get(uri)?.version ?: 0
        workspace.change(uri, version, parsedChanges)
    }

    private fun didChangeWatchedFiles(output: OutputStream) {
        // Imports and transitive C headers affect every open document's semantic view.
        workspace.snapshot().forEach { document -> scheduleDiagnostics(document.uri, output) }
    }

    private fun didClose(params: Map<*, *>, output: OutputStream) {
        val document = params["textDocument"] as? Map<*, *> ?: return
        val uri = document["uri"] as? String ?: return
        val openDocument = workspace.get(uri)
        val affected = openDocument?.let(::connectedOpenDocuments).orEmpty()
        workspace.close(uri)
        writeMessage(output, publish(uri, emptyList()))
        affected.filterNot { it.uri == uri }.forEach { remaining ->
            scheduleDiagnostics(remaining.uri, output)
        }
    }

    private fun publishDiagnostics(
        result: cplus.compiler.CompileResult,
        document: WorkspaceDocument,
        output: OutputStream
    ) {
        val diagnostics = result.diagnostics
            .filter { diagnostic ->
                val sourcePath = diagnostic.range?.let { compiler.sourcePathFor(it.file) }
                sourcePath == null || sourcePath.toAbsolutePath().normalize() == document.path.toAbsolutePath().normalize()
            }
            .map { diagnostic ->
            val range = diagnostic.range?.let { sourceRange -> lspRange(sourceRange, document.text) }
                ?: zeroRange()
            linkedMapOf<String, Any?>(
                "range" to range,
                "severity" to severity(diagnostic.severity),
                "source" to "cplus",
                "message" to diagnostic.message
            ).also { values -> diagnostic.code?.let { values["code"] = it } }
        }
        writeMessage(output, publish(document.uri, diagnostics))
    }

    private fun initializeResult(params: Map<*, *>): Map<String, Any?> {
        val roots = (params["workspaceFolders"] as? List<*>)
            .orEmpty()
            .mapNotNull { (it as? Map<*, *>)?.get("uri") as? String }
            .mapNotNull(::pathForUri)
        val rootUri = (params["rootUri"] as? String)?.let(::pathForUri)
        val rootPath = (params["rootPath"] as? String)?.let { runCatching { Path.of(it) }.getOrNull() }
        val initializationOptions = params["initializationOptions"] as? Map<*, *>
        val requestedSdk = (initializationOptions?.get("sdkManifest") as? String)
            ?.let { runCatching { Path.of(it) }.getOrNull() }
        if (requestedSdk != null) sdkManifest = requestedSdk.toAbsolutePath().normalize()
        workspaceRoots.clear()
        workspaceRoots += roots.ifEmpty { listOfNotNull(rootUri, rootPath) }.map { it.toAbsolutePath().normalize() }
        configuredIncludeDirectories.clear()
        (initializationOptions?.get("includeDirectories") as? List<*>)
            .orEmpty().mapNotNull { it as? String }.mapNotNull { raw ->
                runCatching {
                    val path = Path.of(raw)
                    (if (path.isAbsolute) path else workspaceRoots.firstOrNull()?.resolve(path) ?: path)
                        .toAbsolutePath().normalize()
                }.getOrNull()
            }.forEach(configuredIncludeDirectories::add)
        return linkedMapOf(
        "capabilities" to linkedMapOf(
                "textDocumentSync" to 1,
            "semanticTokensProvider" to linkedMapOf(
                "legend" to linkedMapOf(
                    "tokenTypes" to SemanticTokenService.tokenTypes,
                    "tokenModifiers" to emptyList<String>()
                ),
                "full" to true
            ),
            "hoverProvider" to true,
            "definitionProvider" to true,
            "referencesProvider" to true,
            "documentSymbolProvider" to true,
            "renameProvider" to true,
            "codeActionProvider" to linkedMapOf(
                "codeActionKinds" to listOf("quickfix"),
                "resolveProvider" to false
            ),
            "completionProvider" to linkedMapOf(
                "triggerCharacters" to listOf(".", "/", "{", ",", "\"")
            ),
            "signatureHelpProvider" to linkedMapOf(
                "triggerCharacters" to listOf("(", ",")
            )
        ),
            "serverInfo" to linkedMapOf(
                "name" to "cplus",
                "version" to Version.current.gitShortCommit,
                "cplusBuild" to linkedMapOf(
                    "gitCommit" to Version.current.gitCommit,
                    "gitShortCommit" to Version.current.gitShortCommit,
                    "gitCommitDate" to Version.current.gitCommitDate,
                    "buildDate" to Version.current.buildDate,
                    "codename" to Version.current.codename
                )
            )
        )
    }

    private fun semanticTokens(params: Map<*, *>): Map<String, Any?> {
        val document = params["textDocument"] as? Map<*, *> ?: return linkedMapOf("data" to emptyList<Int>())
        val uri = document["uri"] as? String ?: return linkedMapOf("data" to emptyList<Int>())
        val open = workspace.get(uri) ?: return linkedMapOf("data" to emptyList<Int>())
        return linkedMapOf(
            "data" to SemanticTokenService.encode(compileWorkspace(open), open.path)
        )
    }

    private fun completion(params: Map<*, *>): Map<String, Any?> {
        val request = requestDocument(params) ?: return linkedMapOf("isIncomplete" to false, "items" to emptyList<Any>())
        val context = ImportCompletionContextFinder.find(request.document.text, request.position)
        if (context != null) {
            val index = discoverImports(request.document)
            val overlays = workspace.snapshot().associate { it.path.toAbsolutePath().normalize() to it.text }
            val resolver = moduleSourceResolver(request.document.path)
            val canonicalProvider = context.provider?.let { reference ->
                val importedPath = resolver.resolveImport(request.document.path, reference, overlays.keys)
                val providerFromPath = importedPath?.let { path ->
                    index.exports.firstOrNull { export ->
                        runCatching { Path.of(URI.create(export.sourceUri)).toAbsolutePath().normalize() == path.toAbsolutePath().normalize() }
                            .getOrDefault(false)
                    }?.provider
                }
                providerFromPath ?: when {
                    reference.startsWith("stdlib/") -> "std." + reference.removePrefix("stdlib/").replace('/', '.')
                    reference.startsWith("std/") -> "std." + reference.removePrefix("std/").replace('/', '.')
                    reference.startsWith("c/") -> "c." + reference.removePrefix("c/").replace('/', '.')
                    else -> reference.replace('/', '.')
                }
            }
            val pathProviders = if (context.kind == ImportCompletionKind.PROVIDER) {
                sourcePathProviders(request.document, overlays.keys)
            } else emptyList()
            val items = LspLanguageService.importCompletion(context, index, canonicalProvider, pathProviders)
                .map { completionItem(it, request.document.text) }
            return linkedMapOf("isIncomplete" to false, "items" to items)
        }
        val result = compileWorkspace(request.document)
        val ordinaryIdentifier = ImportCompletionContextFinder.identifier(request.document.text, request.position)
        val indexedExports = if (ordinaryIdentifier != null) discoverImports(request.document) else null
        val items = LspLanguageService.completion(
            result,
            request.document.text,
            request.position,
            indexedExports,
            request.document.path
        ).map { item ->
            completionItem(item, request.document.text)
        }
        return linkedMapOf("isIncomplete" to false, "items" to items)
    }

    private fun completionItem(item: CompletionItem, text: String): Map<String, Any?> = linkedMapOf<String, Any?>(
        "label" to item.label,
        "kind" to item.kind,
        "detail" to item.detail
    ).also { values ->
        item.documentation?.let { values["documentation"] = linkedMapOf("kind" to "markdown", "value" to it) }
        item.sortText?.let { values["sortText"] = it }
        val range = item.replacementRange
        val insertText = item.insertText
        if (range != null && insertText != null) {
            values["textEdit"] = linkedMapOf(
                "range" to lspRange(SourceRange(cplus.core.SourceFileId(0), range.startOffset, range.endOffset), text),
                "newText" to insertText
            )
        }
        if (item.additionalTextEdits.isNotEmpty()) {
            values["additionalTextEdits"] = item.additionalTextEdits.map { edit ->
                linkedMapOf(
                    "range" to lspRange(
                        SourceRange(cplus.core.SourceFileId(0), edit.range.startOffset, edit.range.endOffset),
                        text
                    ),
                    "newText" to edit.newText
                )
            }
        }
    }

    private fun codeActions(params: Map<*, *>): List<Map<String, Any?>> {
        val document = requestedDocument(params) ?: return emptyList()
        val only = ((params["context"] as? Map<*, *>)?.get("only") as? List<*>)
            ?.mapNotNull { it as? String }
        if (only != null && only.none { "quickfix".startsWith(it) || it.startsWith("quickfix.") }) {
            return emptyList()
        }
        val rangeValue = params["range"] as? Map<*, *> ?: return emptyList()
        val range = parseRange(rangeValue) ?: return emptyList()
        val lineIndex = LineIndex.from(document.text)
        val requestedRange = ImportTextRange(
            lineIndex.offsetAt(cplus.core.SourcePosition(range.start.line + 1, range.start.character + 1)),
            lineIndex.offsetAt(cplus.core.SourcePosition(range.end.line + 1, range.end.character + 1))
        )
        if (requestedRange.startOffset !in 0..document.text.length ||
            requestedRange.endOffset !in requestedRange.startOffset..document.text.length
        ) return emptyList()
        val result = compileWorkspace(document)
        val actions = LspLanguageService.importQuickFixes(
            result,
            document.text,
            document.path,
            discoverImports(document),
            requestedRange
        )
        return actions.map { action ->
            val edits = action.edits.map { edit ->
                linkedMapOf("range" to lspRange(
                    SourceRange(cplus.core.SourceFileId(0), edit.range.startOffset, edit.range.endOffset),
                    document.text
                ), "newText" to edit.newText)
            }
            linkedMapOf(
                "title" to action.title,
                "kind" to "quickfix",
                "diagnostics" to listOf(linkedMapOf(
                    "range" to lspRange(
                        SourceRange(cplus.core.SourceFileId(0), action.diagnosticRange.startOffset, action.diagnosticRange.endOffset),
                        document.text
                    ),
                    "severity" to 1,
                    "source" to "cplus",
                    "code" to action.diagnosticCode,
                    "message" to action.diagnosticMessage
                )),
                "edit" to linkedMapOf(
                    "documentChanges" to listOf(linkedMapOf(
                        "textDocument" to linkedMapOf("uri" to document.uri, "version" to document.version),
                        "edits" to edits
                    ))
                )
            )
        }
    }

    private fun discoverImports(document: WorkspaceDocument): cplus.compiler.ImportIndexResult {
        val roots = (workspaceRoots + configuredIncludeDirectories +
            listOfNotNull(document.path.toAbsolutePath().normalize().parent)).toList()
        val sdkRoot = sdkManifest.parent?.parent
        val overlays = workspace.snapshot().associate { it.path.toAbsolutePath().normalize() to it.text }
        val headerEnvironment = runCatching {
            val loaded = SdkManifestLoader.load(sdkManifest).manifest ?: return@runCatching null
            val target = TargetInfo(targetTriple = defaultHostTargetTriple())
            val sdk = SdkResolver.resolve(loaded, target).resolution ?: return@runCatching null
            val abi = TargetRegistry.load(sdk.layout.abiDescriptor).descriptor ?: return@runCatching null
            HeaderEnvironment.create(
                CompileRequest(emptyList(), target = target, sdkManifest = sdkManifest, cIncludeDirectories = roots),
                sdk,
                abi
            )
        }.getOrNull()
        return importIndex.build(roots, sdkRoot, overlays, headerEnvironment)
    }

    private fun sourcePathProviders(document: WorkspaceDocument, overlays: Set<Path>): List<String> {
        val parent = document.path.toAbsolutePath().normalize().parent ?: return emptyList()
        val sdkRoot = sdkManifest.parent?.parent?.toAbsolutePath()?.normalize()
        return moduleSourceResolver(document.path).candidatePaths(overlays)
            .asSequence()
            .map { it.toAbsolutePath().normalize() }
            .filter { it != document.path.toAbsolutePath().normalize() }
            .filter { sdkRoot == null || !it.startsWith(sdkRoot) }
            .mapNotNull { path -> runCatching { parent.relativize(path).toString().replace('\\', '/') }.getOrNull() }
            .map { relative -> if (relative.startsWith("../")) relative else "./$relative" }
            .distinct()
            .sorted()
            .toList()
    }

    private fun hover(params: Map<*, *>): Map<String, Any?>? {
        val request = requestDocument(params) ?: return null
        val result = compileWorkspace(request.document)
        val hover = LspLanguageService.hover(
            result,
            request.document.text,
            request.position,
            request.document.path,
            discoverImports(request.document)
        ) ?: return null
        return linkedMapOf(
            "contents" to linkedMapOf(
                "kind" to "markdown",
                "value" to hover.markdown
            ),
            "range" to lspRange(hover.range, request.document.text)
        )
    }

    private fun definition(params: Map<*, *>): Map<String, Any?>? {
        val request = requestDocument(params) ?: return null
        val result = compileWorkspace(request.document)
        val navigation = LspLanguageService.navigation(
            result,
            request.document.text,
            request.position,
            sourcePath = request.document.path,
            sourcePathFor = compiler::sourcePathFor,
            sourceTextFor = ::sourceTextFor,
            importIndex = discoverImports(request.document)
        ) ?: return null
        navigation.externalDefinition?.let { external ->
            val path = external.path.toAbsolutePath().normalize()
            val text = runCatching { Files.readString(path) }.getOrNull() ?: return null
            val lines = text.lineSequence().toList()
            val lineIndex = external.line - 1
            val lineText = lines.getOrNull(lineIndex) ?: return null
            val column = lineText.indexOf(external.name).takeIf { it >= 0 } ?: return null
            val startOffset = LineIndex.from(text).offsetAt(cplus.core.SourcePosition(external.line, column + 1))
            val range = SourceRange(cplus.core.SourceFileId(0), startOffset, startOffset + external.name.length)
            return linkedMapOf("uri" to path.toUri().toString(), "range" to lspRange(range, text))
        }
        val definition = navigation.definition ?: return null
        return location(definition, request.document)
    }

    private fun references(params: Map<*, *>): List<Map<String, Any?>> {
        val request = requestDocument(params) ?: return emptyList()
        val includeDeclaration = ((params["context"] as? Map<*, *>)?.get("includeDeclaration") as? Boolean) ?: true
        val navigation = LspLanguageService.navigation(
            compileWorkspace(request.document),
            request.document.text,
            request.position,
            includeDeclaration,
            request.document.path,
            compiler::sourcePathFor,
            ::sourceTextFor,
            discoverImports(request.document)
        ) ?: return emptyList()
        val locations = navigation.references.map { range -> location(range, request.document) }.toMutableList()
        if (includeDeclaration) {
            navigation.externalDefinition?.let(::externalLocation)?.let(locations::add)
        }
        return locations
    }

    private fun documentSymbols(params: Map<*, *>): List<Map<String, Any?>> {
        val document = requestedDocument(params) ?: return emptyList()
        val model = compileWorkspace(document).semanticModel ?: return emptyList()
        val normalizedPath = document.path.toAbsolutePath().normalize()
        val declarations = model.symbols.mapNotNull { symbol ->
            val range = symbol.origin.primaryRange ?: return@mapNotNull null
            val path = compiler.sourcePathFor(range.file)?.toAbsolutePath()?.normalize()
            if (path != normalizedPath) return@mapNotNull null
            linkedMapOf(
                "name" to symbol.name,
                "kind" to lspSymbolKind(symbol.kind),
                "location" to location(range, document)
            )
        }
        val fixtures = model.testFixtures.mapNotNull { fixture ->
            val range = fixture.fixture.origin.primaryRange ?: return@mapNotNull null
            val path = compiler.sourcePathFor(range.file)?.toAbsolutePath()?.normalize()
            if (path != normalizedPath) return@mapNotNull null
            linkedMapOf(
                "name" to fixture.fixture.description,
                "kind" to 12,
                "location" to location(range, document)
            )
        }
        return declarations + fixtures
    }

    private fun rename(params: Map<*, *>): Map<String, Any?>? {
        val request = requestDocument(params) ?: return null
        val newName = params["newName"] as? String ?: return null
        if (!newName.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) return null
        val result = compileWorkspace(request.document)
        val navigation = LspLanguageService.navigation(
            result,
            request.document.text,
            request.position,
            includeDeclaration = true,
            sourcePath = request.document.path,
            sourcePathFor = compiler::sourcePathFor,
            sourceTextFor = ::sourceTextFor
        ) ?: return null
        val definitionPath = navigation.definition?.file?.let(compiler::sourcePathFor)
            ?.toAbsolutePath()?.normalize() ?: request.document.path.toAbsolutePath().normalize()
        if (!isEditableWorkspaceSource(definitionPath)) return null
        val edits = navigation.references.mapNotNull { range ->
            val path = compiler.sourcePathFor(range.file)?.toAbsolutePath()?.normalize()
                ?: request.document.path.toAbsolutePath().normalize()
            if (!isEditableWorkspaceSource(path)) return@mapNotNull null
            path to range
        }.groupBy({ it.first }, { it.second }).map { (path, ranges) ->
            val open = workspace.snapshot().firstOrNull { it.path.toAbsolutePath().normalize() == path }
            val uri = open?.uri ?: path.toUri().toString()
            val text = open?.text ?: runCatching { Files.readString(path) }.getOrDefault("")
            uri to ranges.map { range ->
                linkedMapOf<String, Any?>("range" to lspRange(range, text), "newText" to newName)
            }
        }.toMap()
        return linkedMapOf("changes" to edits)
    }

    private fun isEditableWorkspaceSource(path: Path): Boolean {
        val normalized = path.toAbsolutePath().normalize()
        val sdkRoot = SdkManifestLocator.defaultManifestPath().toAbsolutePath().normalize()
            .parent?.parent?.toAbsolutePath()?.normalize()
        return normalized.fileName.toString().endsWith(".cp") && (sdkRoot == null || !normalized.startsWith(sdkRoot))
    }

    private fun signatureHelp(params: Map<*, *>): Map<String, Any?>? {
        val request = requestDocument(params) ?: return null
        val signature = LspLanguageService.signatureHelp(
            compileWorkspace(request.document),
            request.document.text,
            request.position,
            request.document.path
        ) ?: return null
        return linkedMapOf(
            "signatures" to listOf(
                linkedMapOf<String, Any?>(
                    "label" to signature.label,
                    "parameters" to signature.parameters.map { parameter ->
                        linkedMapOf<String, Any?>(
                            "label" to parameter.label,
                            "documentation" to parameter.documentation
                        )
                    }
                )
            ),
            "activeSignature" to 0,
            "activeParameter" to signature.activeParameter
        )
    }

    private fun location(range: SourceRange, fallback: WorkspaceDocument): Map<String, Any?> {
        val path = compiler.sourcePathFor(range.file)?.toAbsolutePath()?.normalize()
        val open = path?.let { candidate ->
            workspace.snapshot().firstOrNull { it.path.toAbsolutePath().normalize() == candidate }
        }
        val uri = open?.uri ?: path?.toUri()?.toString() ?: fallback.uri
        val text = open?.text ?: path?.let { runCatching { Files.readString(it) }.getOrNull() } ?: fallback.text
        return linkedMapOf("uri" to uri, "range" to lspRange(range, text))
    }

    private fun externalLocation(definition: ExternalDefinition): Map<String, Any?>? {
        val path = definition.path.toAbsolutePath().normalize()
        val text = runCatching { Files.readString(path) }.getOrNull() ?: return null
        val line = text.lineSequence().drop(definition.line - 1).firstOrNull() ?: return null
        val column = line.indexOf(definition.name).takeIf { it >= 0 } ?: return null
        val offset = LineIndex.from(text).offsetAt(cplus.core.SourcePosition(definition.line, column + 1))
        return linkedMapOf(
            "uri" to path.toUri().toString(),
            "range" to lspRange(SourceRange(cplus.core.SourceFileId(0), offset, offset + definition.name.length), text)
        )
    }

    private fun requestedDocument(params: Map<*, *>): WorkspaceDocument? {
        val document = params["textDocument"] as? Map<*, *> ?: return null
        val uri = document["uri"] as? String ?: return null
        return workspace.get(uri)
    }

    private fun sourceTextFor(file: cplus.core.SourceFileId): String? {
        val path = compiler.sourcePathFor(file)?.toAbsolutePath()?.normalize() ?: return null
        return workspace.snapshot().firstOrNull { it.path.toAbsolutePath().normalize() == path }?.text
            ?: runCatching { Files.readString(path) }.getOrNull()
    }

    private fun lspSymbolKind(kind: cplus.semantic.SymbolKind): Int = when (kind) {
        cplus.semantic.SymbolKind.STRUCT, cplus.semantic.SymbolKind.UNION, cplus.semantic.SymbolKind.ALIAS,
        cplus.semantic.SymbolKind.FOREIGN_TYPE -> 5
        cplus.semantic.SymbolKind.METHOD -> 6
        cplus.semantic.SymbolKind.FIELD -> 8
        cplus.semantic.SymbolKind.ENUM -> 10
        cplus.semantic.SymbolKind.FUNCTION, cplus.semantic.SymbolKind.FOREIGN -> 12
        else -> 13
    }

    private fun requestDocument(params: Map<*, *>): DocumentRequest? {
        val document = params["textDocument"] as? Map<*, *> ?: return null
        val uri = document["uri"] as? String ?: return null
        val open = workspace.get(uri) ?: return null
        val position = parsePosition(params["position"] as? Map<*, *>) ?: return null
        return DocumentRequest(open, position)
    }

    private fun compileWorkspace(document: WorkspaceDocument): cplus.compiler.CompileResult {
        val overlays = workspace.snapshot().associate { it.path.toAbsolutePath().normalize() to it.text }
        val resolution = moduleSourceResolver(document.path).resolveClosure(listOf(document.path), overlays)
        return compiler.compileTextWorkspace(
            resolution.modules.map { TextSource(it.path, it.text) },
            sdkManifest = sdkManifest,
            cIncludeDirectories = (workspaceRoots + configuredIncludeDirectories +
                listOfNotNull(document.path.toAbsolutePath().normalize().parent)).toList()
        )
    }

    private fun connectedOpenDocuments(root: WorkspaceDocument): List<WorkspaceDocument> {
        val overlays = workspace.snapshot().associate { it.path.toAbsolutePath().normalize() to it.text }
        val paths = moduleSourceResolver(root.path).resolveClosure(listOf(root.path), overlays).paths.toSet()
        return workspace.snapshot().filter { it.path.toAbsolutePath().normalize() in paths }
    }

    private fun moduleSourceResolver(documentPath: Path): ModuleSourceResolver {
        val sdkRoot = sdkManifest.parent?.parent
        val roots = workspaceRoots + configuredIncludeDirectories +
            listOfNotNull(documentPath.toAbsolutePath().normalize().parent)
        return ModuleSourceResolver(roots.toList(), sdkRoot)
    }

    private fun response(id: Any?, result: Any?): Map<String, Any?> = linkedMapOf(
        "jsonrpc" to "2.0",
        "id" to id,
        "result" to result
    )

    private fun errorResponse(id: Any?, code: Int, message: String): Map<String, Any?> = linkedMapOf(
        "jsonrpc" to "2.0",
        "id" to id,
        "error" to linkedMapOf("code" to code, "message" to message)
    )

    private fun publish(uri: String, diagnostics: List<Map<String, Any?>>): Map<String, Any?> = linkedMapOf(
        "jsonrpc" to "2.0",
        "method" to "textDocument/publishDiagnostics",
        "params" to linkedMapOf(
            "uri" to uri,
            "diagnostics" to diagnostics
        )
    )

    private fun lspRange(range: SourceRange, text: String): Map<String, Any?> {
        val index = LineIndex.from(text)
        val start = index.positionAt(range.startOffset.coerceIn(0, text.length))
        val end = index.positionAt(range.endOffset.coerceIn(range.startOffset, text.length))
        return linkedMapOf(
            "start" to linkedMapOf("line" to start.line - 1, "character" to start.column - 1),
            "end" to linkedMapOf("line" to end.line - 1, "character" to end.column - 1)
        )
    }

    private fun zeroRange(): Map<String, Any?> = linkedMapOf(
        "start" to linkedMapOf("line" to 0, "character" to 0),
        "end" to linkedMapOf("line" to 0, "character" to 0)
    )

    private fun severity(severity: DiagnosticSeverity): Int = when (severity) {
        DiagnosticSeverity.ERROR -> 1
        DiagnosticSeverity.WARNING -> 2
        DiagnosticSeverity.INFO -> 3
    }

    private fun pathForUri(uri: String): Path? = runCatching {
        if (uri.startsWith("file:")) Path.of(URI.create(uri)) else Path.of(uri)
    }.getOrNull()

    private fun parseChange(value: Any?): LspTextChange? {
        val change = value as? Map<*, *> ?: return null
        val text = change["text"] as? String ?: return null
        val rangeValue = change["range"]
        val range = (rangeValue as? Map<*, *>)?.let { parseRange(it) }
        if (rangeValue != null && range == null) return null
        return LspTextChange(range, text)
    }

    private fun parseRange(value: Map<*, *>): LspTextRange? {
        val start = parsePosition(value["start"] as? Map<*, *>) ?: return null
        val end = parsePosition(value["end"] as? Map<*, *>) ?: return null
        return LspTextRange(start, end)
    }

    private fun parsePosition(value: Map<*, *>?): LspPosition? {
        value ?: return null
        val line = (value["line"] as? Number)?.toInt() ?: return null
        val character = (value["character"] as? Number)?.toInt() ?: return null
        return LspPosition(line, character)
    }

    private data class DocumentRequest(
        val document: WorkspaceDocument,
        val position: LspPosition
    )

    companion object {
        private fun readMessage(input: InputStream): String? {
            var contentLength: Int? = null
            while (true) {
                val line = readLine(input) ?: return null
                if (line.isEmpty()) break
                val separator = line.indexOf(':')
                if (separator > 0 && line.substring(0, separator).equals("Content-Length", ignoreCase = true)) {
                    contentLength = line.substring(separator + 1).trim().toIntOrNull()
                }
            }
            val length = contentLength ?: return null
            val bytes = ByteArray(length)
            var offset = 0
            while (offset < length) {
                val count = input.read(bytes, offset, length - offset)
                if (count < 0) return null
                offset += count
            }
            return bytes.toString(StandardCharsets.UTF_8)
        }

        private fun readLine(input: InputStream): String? {
            val bytes = ByteArrayOutputStream()
            while (true) {
                val next = input.read()
                if (next < 0) return if (bytes.size() == 0) null else bytes.toString(StandardCharsets.UTF_8)
                if (next == '\n'.code) break
                bytes.write(next)
            }
            return bytes.toString(StandardCharsets.UTF_8).removeSuffix("\r")
        }

        private fun writeMessage(output: OutputStream, message: Map<String, Any?>) {
            val payload = Json.stringify(message).toByteArray(StandardCharsets.UTF_8)
            synchronized(output) {
                output.write("Content-Length: ${payload.size}\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
                output.write(payload)
                output.flush()
            }
        }
    }
}

private object Json {
    fun parse(text: String): Any? = Parser(text).parseValue()

    fun stringify(value: Any?): String = when (value) {
        null -> "null"
        is String -> quote(value)
        is Number, is Boolean -> value.toString()
        is Map<*, *> -> value.entries.joinToString(prefix = "{", postfix = "}") { (key, item) ->
            "${quote(key.toString())}:${stringify(item)}"
        }
        is Iterable<*> -> value.joinToString(prefix = "[", postfix = "]", transform = ::stringify)
        else -> quote(value.toString())
    }

    private fun quote(value: String): String = buildString {
        append('"')
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                else -> if (character.code < 0x20) append("\\u%04x".format(character.code)) else append(character)
            }
        }
        append('"')
    }

    private class Parser(private val text: String) {
        private var index = 0

        fun parseValue(): Any? {
            skipWhitespace()
            return when (text.getOrNull(index)) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> consume("true", true)
                'f' -> consume("false", false)
                'n' -> consume("null", null)
                else -> parseNumber()
            }
        }

        private fun parseObject(): Map<String, Any?> {
            expect('{')
            val result = linkedMapOf<String, Any?>()
            skipWhitespace()
            if (peek('}')) {
                index++
                return result
            }
            while (true) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                expect(':')
                result[key] = parseValue()
                skipWhitespace()
                if (peek('}')) {
                    index++
                    return result
                }
                expect(',')
            }
        }

        private fun parseArray(): List<Any?> {
            expect('[')
            val result = mutableListOf<Any?>()
            skipWhitespace()
            if (peek(']')) {
                index++
                return result
            }
            while (true) {
                result += parseValue()
                skipWhitespace()
                if (peek(']')) {
                    index++
                    return result
                }
                expect(',')
            }
        }

        private fun parseString(): String {
            expect('"')
            return buildString {
                while (index < text.length) {
                    when (val character = text[index++]) {
                        '"' -> return@buildString
                        '\\' -> when (val escaped = text[index++]) {
                            '"', '\\', '/' -> append(escaped)
                            'b' -> append('\b')
                            'f' -> append('\u000C')
                            'n' -> append('\n')
                            'r' -> append('\r')
                            't' -> append('\t')
                            'u' -> append(text.substring(index, index + 4).toInt(16).toChar()).also { index += 4 }
                            else -> error("invalid JSON escape")
                        }
                        else -> append(character)
                    }
                }
            }
        }

        private fun parseNumber(): Number {
            val start = index
            while (index < text.length && text[index] !in " \\t\\r\\n,]}") index++
            val value = text.substring(start, index)
            return if (value.any { it == '.' || it == 'e' || it == 'E' }) value.toDouble() else value.toLong()
        }

        private fun <T> consume(expected: String, value: T): T {
            check(text.startsWith(expected, index)) { "invalid JSON value" }
            index += expected.length
            return value
        }

        private fun skipWhitespace() {
            while (text.getOrNull(index)?.isWhitespace() == true) index++
        }

        private fun peek(expected: Char): Boolean = text.getOrNull(index) == expected

        private fun expect(expected: Char) {
            skipWhitespace()
            check(text.getOrNull(index) == expected) { "expected '$expected'" }
            index++
        }
    }
}
