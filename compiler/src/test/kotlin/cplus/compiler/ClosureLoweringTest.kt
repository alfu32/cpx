package cplus.compiler

import cplus.core.*
import cplus.semantic.PrimitiveType
import cplus.semantic.Symbol
import cplus.semantic.SymbolId
import cplus.semantic.SymbolKind
import cplus.semantic.Visibility
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClosureLoweringTest {
    @Test
    fun lowersClosuresInsideTraitMethodsWithoutUnwrappingTheTrait() {
        val origin = Origin.Direct(SourceRange(SourceFileId(34), 0, 1))
        val nested = function("nested", AstBlock(listOf(AstReturn(AstIdentifier("value", origin), origin)), origin), origin)
        val method = AstFunction(
            AstTypeRef("int", false, 0, origin),
            "read",
            listOf(AstParameter(AstTypeRef("int", false, 0, origin), "value", origin = origin)),
            AstBlock(listOf(AstInnerFunction(nested, origin), AstReturn(AstIntegerLiteral("0", origin), origin)), origin),
            origin = origin
        )
        val trait = AstTrait("Counter", origin, listOf(method), origin)

        val result = AstClosureLowerer().lower(AstProgram(listOf(trait), origin))

        assertTrue(result.isSuccessful, result.diagnostics.toString())
        val loweredTrait = result.program.declarations.filterIsInstance<AstTrait>().single()
        assertEquals("Counter", loweredTrait.targetName)
        assertEquals(origin, loweredTrait.targetOrigin)
        assertEquals(origin, loweredTrait.origin)
        assertEquals("read", loweredTrait.methods.single().name)
        assertEquals(origin, loweredTrait.methods.single().origin)
        assertTrue(loweredTrait.methods.single().body.toString().contains("AstInnerFunction").not())
        assertTrue(result.program.declarations.filterIsInstance<AstFunction>().any { it.name.contains("nested") })
    }

    @Test
    fun plannerCreatesEnvironmentAndHoistedFunctionForCapturedState() {
        val origin = Origin.Direct(SourceRange(SourceFileId(35), 0, 1))
        val inner = function(
            "add",
            AstBlock(
                listOf(AstReturn(AstBinary(AstIdentifier("x", origin), "+", AstIdentifier("y", origin), origin), origin),),
                origin
            ),
            origin
        )
        val x = Symbol(SymbolId(10), "x", SymbolKind.VARIABLE, PrimitiveType(cplus.semantic.TypeId(1), "int"), origin, Visibility.PRIVATE)

        val plan = ClosurePlanner().plan("outer", inner, mapOf("x" to x), mutableBindings = setOf("x"))

        assertTrue(plan.isSuccessful)
        assertEquals("outer__add__env_t", plan.environment?.name)
        assertEquals(listOf("x"), plan.environment?.fields?.map { it.name })
        assertEquals(CaptureMode.REFERENCE, plan.hoistedFunction.captures.single().mode)
        assertEquals("env", plan.hoistedFunction.environmentParameterName)
    }

    @Test
    fun plannerRejectsEscapingReferenceCapture() {
        val origin = Origin.Direct(SourceRange(SourceFileId(36), 0, 1))
        val inner = function("read", AstBlock(listOf(AstReturn(AstIdentifier("x", origin), origin)), origin), origin)
        val x = Symbol(SymbolId(11), "x", SymbolKind.VARIABLE, PrimitiveType(cplus.semantic.TypeId(2), "int"), origin)

        val plan = ClosurePlanner().plan("outer", inner, mapOf("x" to x), mutableBindings = setOf("x"), escapesScope = true)

        assertTrue(plan.diagnostics.any { it.code == "CLOSURE001" })
    }

    private fun function(name: String, body: AstStatement, origin: Origin) = AstFunction(
        AstTypeRef("int", false, 0, origin),
        name,
        listOf(AstParameter(AstTypeRef("int", false, 0, origin), "y", origin = origin)),
        body,
        origin = origin
    )
}
