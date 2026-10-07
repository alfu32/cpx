package cplus.compiler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AbiAttributeTest {
    @Test
    fun preservesAbiAndExternalLinkNameSeparatelyFromSourceName() {
        val result = CPlusCompiler().compileText(
            java.nio.file.Path.of("abi_attributes.cp"),
            "@abi(system) @link_name(\"foreign_entry\") int local_entry() { return 0; }"
        )

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val symbol = requireNotNull(result.semanticModel).functions.getValue("local_entry").symbol
        assertEquals(cplus.semantic.AbiKind.SYSTEM, symbol.abi)
        assertEquals("foreign_entry", symbol.externalName)
        assertTrue("foreign_entry()" in result.generatedUnits.single().text)
    }
}
