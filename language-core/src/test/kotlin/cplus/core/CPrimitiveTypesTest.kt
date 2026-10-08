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
        assertEquals("__int128", CPrimitiveTypes.canonicalName("signed __int128"))
        assertEquals("unsigned __int128", CPrimitiveTypes.canonicalName("unsigned __int128"))
        assertEquals(CIntegerRank.INT128, CPrimitiveTypes.typeInfo("__int128")?.rank)
        assertEquals("long double", CPrimitiveTypes.canonicalName("long double"))
        assertEquals(CFloatingRank.LONG_DOUBLE, CPrimitiveTypes.typeInfo("long double")?.floatingRank)
        assertTrue(CPrimitiveTypes.isNumeric("long double"))
    }

    @Test
    fun rejectsInvalidSequencesAndKeepsStandardTypedefsDistinctFromPrimitives() {
        assertNull(CPrimitiveTypes.canonicalizeSpecifierSequence(listOf("unsigned", "signed", "int")))
        assertNull(CPrimitiveTypes.canonicalizeSpecifierSequence(listOf("short", "long")))
        assertNull(CPrimitiveTypes.canonicalizeSpecifierSequence(listOf("long", "__int128")))
        assertTrue(CPrimitiveTypes.isKnownTypeName("size_t"))
        assertTrue(CPrimitiveTypes.isInteger("ptrdiff_t"))
        assertFalse(CPrimitiveTypes.isInteger("max_align_t"))
    }

    @Test
    fun canonicalizesComplexTypesWithoutTreatingThemAsRealScalars() {
        assertEquals("float _Complex", CPrimitiveTypes.canonicalName("_Complex float"))
        assertEquals("double _Complex", CPrimitiveTypes.canonicalName("double _Complex"))
        assertEquals("long double _Complex", CPrimitiveTypes.canonicalName("_Complex long double"))
        assertEquals("long double _Complex", CPrimitiveTypes.canonicalName("long _Complex double"))
        assertEquals(CPrimitiveKind.COMPLEX, CPrimitiveTypes.typeInfo("double _Complex")?.kind)
        assertEquals("long double", CPrimitiveTypes.typeInfo("long double _Complex")?.componentTypeName)
        assertTrue(CPrimitiveTypes.isKnownTypeName("float _Complex"))
        assertTrue(CPrimitiveTypes.isComplex("double _Complex"))
        assertFalse(CPrimitiveTypes.isNumeric("double _Complex"))
        assertNull(CPrimitiveTypes.canonicalName("int _Complex"))
        assertNull(CPrimitiveTypes.canonicalName("_Complex"))
        assertNull(CPrimitiveTypes.canonicalName("float _Complex _Complex"))
    }
}
