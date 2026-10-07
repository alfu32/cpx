package cplus.cli

import cplus.compiler.CPlusCompiler
import cplus.compiler.TextSource
import cplus.core.DiagnosticSeverity
import cplus.core.LineIndex
import cplus.core.SourceRange
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.ArrayDeque

internal class LspServer(
    private val compiler: CPlusCompiler = CPlusCompiler()
) {
    private val workspace = LspWorkspace()
    private var shutdownRequested = false

    fun run(input: InputStream, output: OutputStream): Int {
        val inputStream = input.buffered()
        val outputStream = output.buffered()
        while (true) {
            val body = readMessage(inputStream) ?: break
            val message = runCatching { Json.parse(body) as? Map<*, *> }.getOrNull() ?: continue
            val method = message["method"] as? String ?: continue
            val id = message["id"]
            val params = message["params"] as? Map<*, *> ?: emptyMap<String, Any?>()
            when (method) {
                "initialize" -> {
                    writeMessage(outputStream, response(id, initializeResult()))
                }
                "initialized", "$/cancelRequest" -> Unit
                "shutdown" -> {
                    shutdownRequested = true
                    writeMessage(outputStream, response(id, null))
                }
                "exit" -> return 0
                "textDocument/didOpen" -> {
                    didOpen(params)
                }
                "textDocument/didChange" -> {
                    didChange(params)
                }
                "textDocument/didClose" -> {
                    didClose(params, outputStream)
                }
                "textDocument/semanticTokens/full" -> {
                    if (id != null) writeMessage(outputStream, response(id, semanticTokens(params)))
                }
                "textDocument/completion" -> {
                    if (id != null) writeMessage(outputStream, response(id, completion(params)))
                }
                "textDocument/hover" -> {
                    if (id != null) writeMessage(outputStream, response(id, hover(params)))
                }
                "textDocument/definition" -> {
                    if (id != null) writeMessage(outputStream, response(id, definition(params)))
                }
                "textDocument/references" -> {
                    if (id != null) writeMessage(outputStream, response(id, references(params)))
                }
                "textDocument/signatureHelp" -> {
                    if (id != null) writeMessage(outputStream, response(id, signatureHelp(params)))
                }
                else -> {
                    if (id != null) {
                        writeMessage(outputStream, errorResponse(id, -32601, "method not found: $method"))
                    }
                }
            }
            if (method == "textDocument/didOpen" || method == "textDocument/didChange") {
                val uri = (params["textDocument"] as? Map<*, *>)?.get("uri") as? String
                if (uri != null) publishDiagnostics(uri, outputStream)
            }
            outputStream.flush()
            if (shutdownRequested && method != "exit" && method != "shutdown") break
        }
        outputStream.flush()
        return 0
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

    private fun didClose(params: Map<*, *>, output: OutputStream) {
        val document = params["textDocument"] as? Map<*, *> ?: return
        val uri = document["uri"] as? String ?: return
        workspace.close(uri)
        writeMessage(output, publish(uri, emptyList()))
    }

    private fun publishDiagnostics(uri: String, output: OutputStream) {
        val document = workspace.get(uri) ?: return
        val result = compileWorkspace(document)
        val diagnostics = result.diagnostics
            .filter { diagnostic ->
                val sourcePath = diagnostic.range?.let { compiler.sourcePathFor(it.file) }
                sourcePath == null || sourcePath == document.path.toAbsolutePath().normalize()
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
        writeMessage(output, publish(uri, diagnostics))
    }

    private fun initializeResult(): Map<String, Any?> = linkedMapOf(
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
            "completionProvider" to linkedMapOf(
                "triggerCharacters" to listOf(".", "-")
            ),
            "signatureHelpProvider" to linkedMapOf(
                "triggerCharacters" to listOf("(", ",")
            )
        ),
        "serverInfo" to linkedMapOf(
            "name" to "cplus",
            "version" to "0.1.0"
        )
    )

    private fun semanticTokens(params: Map<*, *>): Map<String, Any?> {
        val document = params["textDocument"] as? Map<*, *> ?: return linkedMapOf("data" to emptyList<Int>())
        val uri = document["uri"] as? String ?: return linkedMapOf("data" to emptyList<Int>())
        val open = workspace.get(uri) ?: return linkedMapOf("data" to emptyList<Int>())
        return linkedMapOf(
            "data" to SemanticTokenService.encode(compileWorkspace(open))
        )
    }

    private fun completion(params: Map<*, *>): Map<String, Any?> {
        val request = requestDocument(params) ?: return linkedMapOf("isIncomplete" to false, "items" to emptyList<Any>())
        val result = compileWorkspace(request.document)
        val items = LspLanguageService.completion(result, request.document.text, request.position).map { item ->
            linkedMapOf<String, Any?>(
                "label" to item.label,
                "kind" to item.kind,
                "detail" to item.detail
            )
        }
        return linkedMapOf("isIncomplete" to false, "items" to items)
    }

    private fun hover(params: Map<*, *>): Map<String, Any?>? {
        val request = requestDocument(params) ?: return null
        val result = compileWorkspace(request.document)
        val hover = LspLanguageService.hover(result, request.document.text, request.position) ?: return null
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
        val navigation = LspLanguageService.navigation(
            compileWorkspace(request.document),
            request.document.text,
            request.position
        ) ?: return null
        val definition = navigation.definition ?: return null
        return location(request.document.uri, definition, request.document.text)
    }

    private fun references(params: Map<*, *>): List<Map<String, Any?>> {
        val request = requestDocument(params) ?: return emptyList()
        val includeDeclaration = ((params["context"] as? Map<*, *>)?.get("includeDeclaration") as? Boolean) ?: true
        val navigation = LspLanguageService.navigation(
            compileWorkspace(request.document),
            request.document.text,
            request.position,
            includeDeclaration
        ) ?: return emptyList()
        return navigation.references.map { range -> location(request.document.uri, range, request.document.text) }
    }

    private fun signatureHelp(params: Map<*, *>): Map<String, Any?>? {
        val request = requestDocument(params) ?: return null
        val signature = LspLanguageService.signatureHelp(
            compileWorkspace(request.document),
            request.document.text,
            request.position
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

    private fun location(uri: String, range: SourceRange, text: String): Map<String, Any?> = linkedMapOf(
        "uri" to uri,
        "range" to lspRange(range, text)
    )

    private fun requestDocument(params: Map<*, *>): DocumentRequest? {
        val document = params["textDocument"] as? Map<*, *> ?: return null
        val uri = document["uri"] as? String ?: return null
        val open = workspace.get(uri) ?: return null
        val position = parsePosition(params["position"] as? Map<*, *>) ?: return null
        return DocumentRequest(open, position)
    }

    private fun compileWorkspace(document: WorkspaceDocument): cplus.compiler.CompileResult {
        val sources = linkedMapOf<Path, String>()
        fun addSource(path: Path, text: String) {
            sources[path.toAbsolutePath().normalize()] = text
        }

        workspace.snapshot().forEach { open -> addSource(open.path, open.text) }
        if (sources.isEmpty()) addSource(document.path, document.text)

        val pending = ArrayDeque(sources.keys)
        while (pending.isNotEmpty()) {
            val sourcePath = pending.removeFirst()
            val sourceText = sources.getValue(sourcePath)
            IMPORT_MODULE.findAll(sourceText).forEach { match ->
                val reference = (match.groups[1]?.value ?: match.groups[2]?.value)?.trim() ?: return@forEach
                if (reference.startsWith("c.")) return@forEach
                val imported = resolveImportedSource(sourcePath, reference) ?: return@forEach
                val normalized = imported.toAbsolutePath().normalize()
                if (normalized !in sources && Files.isRegularFile(normalized)) {
                    sources[normalized] = runCatching { Files.readString(normalized) }.getOrNull() ?: return@forEach
                    pending.addLast(normalized)
                }
            }
        }
        return compiler.compileTextWorkspace(
            sources.map { (path, text) -> TextSource(path, text) }
        )
    }

    private fun resolveImportedSource(source: Path, reference: String): Path? {
        val isPathImport = reference.startsWith("./") ||
            reference.startsWith("../") ||
            reference.startsWith("/") ||
            reference.endsWith(".cp")
        if (isPathImport) {
            val path = runCatching { Path.of(reference) }.getOrNull() ?: return null
            val candidates = listOfNotNull(
                source.parent?.resolve(path),
                Path.of("").toAbsolutePath().normalize().resolve(path)
            )
            return candidates.firstOrNull { Files.isRegularFile(it) }
        }
        val moduleName = reference
            .substringAfterLast('/')
            .substringAfterLast('.')
            .removeSuffix(".cp")
        val sibling = source.parent?.resolve("$moduleName.cp")
        if (sibling != null && Files.isRegularFile(sibling)) return sibling
        val directory = source.parent ?: return null
        return runCatching {
            Files.walk(directory).use { paths ->
                paths
                    .filter { candidate ->
                        Files.isRegularFile(candidate) && candidate.fileName.toString() == "$moduleName.cp"
                    }
                    .findFirst()
                    .orElse(null)
            }
        }.getOrNull()
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
        private val IMPORT_MODULE = Regex("""\bfrom\s+(?:"([^"]+)"|([^\s;]+))""")

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
            output.write("Content-Length: ${payload.size}\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
            output.write(payload)
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
