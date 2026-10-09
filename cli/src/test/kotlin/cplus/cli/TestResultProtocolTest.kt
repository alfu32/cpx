package cplus.cli

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TestResultProtocolTest {
    @Test
    fun parsesPassFailAndPercentEncodedFixtureIdentity() {
        val parsed = parse(
            "CPLUS-TEST\t1\tBEGIN\tfixture%09%C3%A9\n" +
                "CPLUS-TEST\t1\tASSERT\t1\tPASS\n" +
                "CPLUS-TEST\t1\tASSERT\t2\tFAIL\n" +
                "CPLUS-TEST\t1\tCOMPLETE\t1\t1\t2\n",
            "fixture\té"
        )

        assertTrue(parsed.completed)
        assertNull(parsed.error)
        assertEquals(listOf(true, false), parsed.assertions)
        assertEquals(1, parsed.passed)
        assertEquals(1, parsed.failed)
        assertEquals(2, parsed.total)
    }

    @Test
    fun acceptsZeroAssertionCompletion() {
        val parsed = parse("CPLUS-TEST\t1\tBEGIN\tfixture\nCPLUS-TEST\t1\tCOMPLETE\t0\t0\t0\n", "fixture")
        assertTrue(parsed.completed)
        assertEquals(0, parsed.total)
    }

    @Test
    fun rejectsWrongIdentityVersionSequenceAndCompletionTotals() {
        val invalidRecords = listOf(
            "CPLUS-TEST\t1\tBEGIN\tother\nCPLUS-TEST\t1\tCOMPLETE\t0\t0\t0\n" to "fixture identity",
            "CPLUS-TEST\t9\tBEGIN\tfixture\n" to "version",
            "CPLUS-TEST\t1\tBEGIN\tfixture\nCPLUS-TEST\t1\tBEGIN\tfixture\n" to "BEGIN",
            "CPLUS-TEST\t1\tBEGIN\tfixture\nCPLUS-TEST\t1\tASSERT\t2\tPASS\n" to "sequence",
            "CPLUS-TEST\t1\tBEGIN\tfixture\nCPLUS-TEST\t1\tASSERT\t1\tPASS\nCPLUS-TEST\t1\tCOMPLETE\t0\t0\t1\n" to "totals"
        )
        invalidRecords.forEach { (records, message) ->
            val parsed = parse(records, "fixture")
            assertFalse(parsed.completed, records)
            assertTrue(assertNotNull(parsed.error).contains(message), parsed.error)
        }
    }

    @Test
    fun preservesValidAssertionPrefixWhenCompletionIsTruncatedOrMissing() {
        val prefix = "CPLUS-TEST\t1\tBEGIN\tfixture\nCPLUS-TEST\t1\tASSERT\t1\tFAIL\n"
        val missing = parse(prefix, "fixture")
        val truncated = parse(prefix + "CPLUS-TEST\t1\tCOMPL", "fixture")

        assertEquals(listOf(false), missing.assertions)
        assertEquals(listOf(false), truncated.assertions)
        assertTrue(assertNotNull(missing.error).contains("missing its COMPLETE"))
        assertTrue(assertNotNull(truncated.error).contains("truncated record"))
    }

    @Test
    fun preservesValidPrefixWhenLaterRecordIsMalformed() {
        val parsed = parse(
            "CPLUS-TEST\t1\tBEGIN\tfixture\nCPLUS-TEST\t1\tASSERT\t1\tPASS\nmalformed\n",
            "fixture"
        )

        assertEquals(listOf(true), parsed.assertions)
        assertFalse(parsed.completed)
        assertNotNull(parsed.error)
    }

    @Test
    fun rejectsMalformedOversizedAndPostCompletionRecords() {
        val oversized = parse("x".repeat(32), "fixture", maxRecordBytes = 16)
        val invalidUtf8 = TestResultProtocol.parse(ByteArrayInputStream(byteArrayOf(0xff.toByte(), '\n'.code.toByte())), "fixture")
        val trailing = parse(
            "CPLUS-TEST\t1\tBEGIN\tfixture\nCPLUS-TEST\t1\tCOMPLETE\t0\t0\t0\nnoise\n",
            "fixture"
        )

        assertTrue(assertNotNull(oversized.error).contains("exceeds"))
        assertTrue(assertNotNull(invalidUtf8.error).contains("UTF-8"))
        assertTrue(assertNotNull(trailing.error).contains("after completion"))
    }

    @Test
    fun userOutputIsNotAnInputToProtocolParsing() {
        val userOutput = "SUCCESS\nCPLUS-TEST\t1\tASSERT\t1\tFAIL\n"
        val actualControlChannel = "CPLUS-TEST\t1\tBEGIN\tfixture\nCPLUS-TEST\t1\tCOMPLETE\t0\t0\t0\n"

        val parsed = parse(actualControlChannel, "fixture")

        assertTrue(userOutput.contains("FAIL"))
        assertTrue(parsed.completed)
        assertEquals(0, parsed.total)
    }

    private fun parse(records: String, identity: String, maxRecordBytes: Int = TestResultProtocol.DEFAULT_MAX_RECORD_BYTES) =
        TestResultProtocol.parse(
            ByteArrayInputStream(records.toByteArray(StandardCharsets.UTF_8)),
            identity,
            maxRecordBytes
        )
}
