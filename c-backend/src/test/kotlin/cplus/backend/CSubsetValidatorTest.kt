package cplus.backend

import cplus.core.Origin
import kotlin.test.Test
import kotlin.test.assertTrue

class CSubsetValidatorTest {
    @Test
    fun rejectsReservedIdentifiersOutsideForeignBoundaries() {
        val origin = Origin.Synthetic(null)
        val unit = CTranslationUnit(
            includes = emptyList(),
            structs = emptyList(),
            unions = emptyList(),
            enums = emptyList(),
            aliases = emptyList(),
            globals = emptyList(),
            functions = listOf(
                CFunction(
                    CType.Primitive("int"),
                    "main",
                    emptyList(),
                    CBlock(listOf(CReturn(CIdentifier("for", origin), origin)), origin),
                    origin
                )
            )
        )

        val diagnostics = CSubsetValidator().validate(unit)

        assertTrue(diagnostics.any { it.code == "LOW406" }, diagnostics.joinToString())
    }

    @Test
    fun rejectsDeclarationsThatCollideWithGeneratedHelpers() {
        val origin = Origin.Synthetic(null)
        val unit = CTranslationUnit(
            includes = emptyList(),
            structs = emptyList(),
            unions = emptyList(),
            enums = emptyList(),
            aliases = emptyList(),
            globals = emptyList(),
            functions = listOf(
                CFunction(CType.Primitive("int"), "__cplus_format", emptyList(), null, origin),
                CFunction(CType.Primitive("int"), "main", emptyList(), null, origin)
            ),
            requiresStringTemplateRuntime = true
        )

        val diagnostics = CSubsetValidator().validate(unit)

        assertTrue(diagnostics.any { it.code == "LOW407" }, diagnostics.joinToString())
    }
}
