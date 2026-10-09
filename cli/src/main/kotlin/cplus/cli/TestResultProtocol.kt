package cplus.cli

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

internal data class TestProtocolResult(
    val assertions: List<Boolean>,
    val completed: Boolean,
    val error: String? = null
) {
    val passed: Int get() = assertions.count { it }
    val failed: Int get() = assertions.size - passed
    val total: Int get() = assertions.size
}

/** Incremental bounded parser for the private child result channel; it never reads user stdout. */
internal object TestResultProtocol {
    private const val PREFIX = "CPLUS-TEST"
    private const val VERSION = "1"
    const val DEFAULT_MAX_RECORD_BYTES = 4096

    fun parse(
        input: InputStream,
        expectedIdentity: String,
        maxRecordBytes: Int = DEFAULT_MAX_RECORD_BYTES
    ): TestProtocolResult {
        require(maxRecordBytes > 0)
        val assertions = mutableListOf<Boolean>()
        var began = false
        var completed = false
        var error: String? = null
        var line = ByteArrayOutputStream()

        fun fail(message: String) {
            if (error == null) error = message
        }

        fun consumeRecord(bytes: ByteArray) {
            val text = try {
                StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString()
            } catch (_: Exception) {
                fail("result protocol contains invalid UTF-8")
                return
            }
            if (completed) {
                fail("result protocol contains data after completion")
                return
            }
            val fields = text.split('\t')
            if (fields.size < 3 || fields[0] != PREFIX || fields[1] != VERSION) {
                fail("result protocol has an invalid prefix or unsupported version")
                return
            }
            when (fields[2]) {
                "BEGIN" -> {
                    if (began || assertions.isNotEmpty() || fields.size != 4) {
                        fail("result protocol has a misplaced or malformed BEGIN record")
                    } else if (fields[3] != encodeIdentity(expectedIdentity)) {
                        fail("result protocol fixture identity does not match the selected fixture")
                    } else began = true
                }
                "ASSERT" -> {
                    if (!began || fields.size != 5) {
                        fail("result protocol has a misplaced or malformed ASSERT record")
                        return
                    }
                    val sequence = fields[3].toLongOrNull()
                    if (sequence != assertions.size.toLong() + 1L) {
                        fail("result protocol assertion sequence is not strictly increasing")
                        return
                    }
                    when (fields[4]) {
                        "PASS" -> assertions += true
                        "FAIL" -> assertions += false
                        else -> fail("result protocol assertion outcome must be PASS or FAIL")
                    }
                }
                "COMPLETE" -> {
                    if (!began || fields.size != 6) {
                        fail("result protocol has a misplaced or malformed COMPLETE record")
                        return
                    }
                    val passed = fields[3].toIntOrNull()
                    val failed = fields[4].toIntOrNull()
                    val total = fields[5].toIntOrNull()
                    if (passed != assertions.count { it } || failed != assertions.count { !it } ||
                        total != assertions.size || total != (passed ?: -1) + (failed ?: -1)
                    ) {
                        fail("result protocol completion totals do not match its assertion records")
                    } else completed = true
                }
                else -> fail("result protocol contains unknown record '${fields[2]}'")
            }
        }

        try {
            while (error == null) {
                val next = input.read()
                if (next < 0) break
                if (next == '\n'.code) {
                    consumeRecord(line.toByteArray())
                    line = ByteArrayOutputStream()
                } else {
                    if (line.size() >= maxRecordBytes) {
                        fail("result protocol record exceeds the $maxRecordBytes-byte limit")
                    } else line.write(next)
                }
            }
        } catch (exception: Exception) {
            fail("unable to read result protocol: ${exception.message ?: exception::class.simpleName}")
        }
        if (error == null && line.size() > 0) fail("result protocol ends with a truncated record")
        if (error == null && !began) fail("result protocol is missing its BEGIN record")
        if (error == null && !completed) fail("result protocol is missing its COMPLETE record")
        return TestProtocolResult(assertions, completed && error == null, error)
    }

    private fun encodeIdentity(identity: String): String {
        val bytes = identity.toByteArray(StandardCharsets.UTF_8)
        val hex = "0123456789ABCDEF"
        return buildString {
            bytes.forEach { raw ->
                val byte = raw.toInt() and 0xff
                if (byte in 'a'.code..'z'.code || byte in 'A'.code..'Z'.code || byte in '0'.code..'9'.code ||
                    byte == '_'.code || byte == '-'.code || byte == '.'.code || byte == '/'.code || byte == ':'.code
                ) append(byte.toChar())
                else {
                    append('%')
                    append(hex[byte ushr 4])
                    append(hex[byte and 15])
                }
            }
        }
    }
}
