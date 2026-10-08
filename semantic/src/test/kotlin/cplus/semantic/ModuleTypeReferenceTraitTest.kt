package cplus.semantic

import cplus.core.*
import kotlin.test.Test
import kotlin.test.assertEquals

class ModuleTypeReferenceTraitTest {
    @Test
    fun traitTargetAndMethodBodyTypesAreCollectedWithoutFlatteningMethods() {
        val origin = Origin.Direct(SourceRange(SourceFileId(78), 0, 1))
        val bodyType = AstTypeRef("ImportedValue", true, 0, origin)
        val method = AstFunction(
            AstTypeRef("int", false, 0, origin),
            "inspect",
            listOf(
                AstParameter(AstTypeRef("self", false, 0, origin), "self", isReceiver = true, origin = origin),
                AstParameter(bodyType, "value", origin = origin)
            ),
            AstBlock(listOf(AstVariableDeclaration(bodyType, "copy", null, origin)), origin),
            origin = origin
        )
        val trait = AstTrait("ImportedTarget", origin, listOf(method), origin)

        val references = ModuleTypeReferenceCollector.collect(AstProgram(listOf(trait), origin))

        assertEquals(listOf("ImportedTarget", "int", "ImportedValue", "ImportedValue"), references.map { it.type.name })
    }
}
