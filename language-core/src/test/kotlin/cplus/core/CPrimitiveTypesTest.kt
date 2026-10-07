package cplus.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CPrimitiveTypesTest {
    @Test
    fun canonicalizesCSpellingsAndExposesRankAndSignedness() {
        assertEquals("int", CPrimitiveTypes.canonicalName("signed int"))
        assertEquals("short", CPrimitiveTypes.canonicalName("signed short int"))
        assertEquals("unsigned long long", CPrimitiveTypes.canonicalName("int unsigned long long"))

        assertEquals(CIntegerRank.CHAR, CPrimitiveTypes.typeInfo("signed char")?.rank)
        assertEquals(CIntegerSignedness.PLAIN, CPrimitiveTypes.typeInfo("char")?.signedness)
        assertEquals(CIntegerSignedness.UNSIGNED, CPrimitiveTypes.typeInfo("unsigned long")?.signedness)
        assertEquals(CIntegerRank.LONG_LONG, CPrimitiveTypes.typeInfo("unsigned long long")?.rank)
        assertNull(CPrimitiveTypes.typeInfo("long double"))
    }

    @Test
    fun rejectsInvalidSequencesAndKeepsStandardTypedefsDistinctFromPrimitives() {
        assertNull(CPrimitiveTypes.canonicalizeSpecifierSequence(listOf("unsigned", "signed", "int")))
        assertNull(CPrimitiveTypes.canonicalizeSpecifierSequence(listOf("short", "long")))
        assertTrue(CPrimitiveTypes.isKnownTypeName("size_t"))
        assertTrue(CPrimitiveTypes.isInteger("ptrdiff_t"))
        assertFalse(CPrimitiveTypes.isInteger("max_align_t"))
    }
}
