package cplus.cli

import cplus.compiler.CPlusCompiler
import cplus.core.DiagnosticSeverity
import cplus.core.LineIndex
import cplus.core.SourceRange
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Path

internal class LspServer(
    private val compiler: CPlusCompiler = CPlusCompiler()
) {
    private val documents = linkedMapOf<String, OpenDocument>()
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
                "textDocument/hover" -> {
                    if (id != null) writeMessage(outputStream, response(id, null))
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
        documents[uri] = OpenDocument(uri, version, text)
    }

    private fun didChange(params: Map<*, *>) {
        val document = params["textDocument"] as? Map<*, *> ?: return
        val uri = document["uri"] as? String ?: return
        val changes = params["contentChanges"] as? List<*> ?: return
        val fullText = changes.asSequence()
            .mapNotNull { it as? Map<*, *> }
            .mapNotNull { it["text"] as? String }
            .lastOrNull() ?: return
        val version = (document["version"] as? Number)?.toInt() ?: documents[uri]?.version ?: 0
        documents[uri] = OpenDocument(uri, version, fullText)
    }

    private fun didClose(params: Map<*, *>, output: OutputStream) {
        val document = params["textDocument"] as? Map<*, *> ?: return
        val uri = document["uri"] as? String ?: return
        documents.remove(uri)
        writeMessage(output, publish(uri, emptyList()))
    }

    private fun publishDiagnostics(uri: String, output: OutputStream) {
        val document = documents[uri] ?: return
        val path = pathForUri(uri) ?: return
        val result = compiler.compileText(path, document.text)
        val diagnostics = result.diagnostics.map { diagnostic ->
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
            "hoverProvider" to false
        ),
        "serverInfo" to linkedMapOf(
            "name" to "cplus",
            "version" to "0.1.0"
        )
    )

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

    private data class OpenDocument(
        val uri: String,
        val version: Int,
        val text: String
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
