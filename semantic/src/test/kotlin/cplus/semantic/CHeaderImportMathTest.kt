package cplus.semantic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CHeaderImportMathTest {
    @Test
    fun catalogsEveryC17RealMathFunctionVariantWithItsDeclaredTypes() {
        val baseNames = listOf(
            "acos", "asin", "atan", "atan2", "cos", "sin", "tan", "acosh", "asinh", "atanh",
            "cosh", "sinh", "tanh", "exp", "exp2", "expm1", "frexp", "ilogb", "ldexp", "log",
            "log10", "log1p", "log2", "logb", "modf", "scalbn", "scalbln", "cbrt", "fabs", "hypot",
            "pow", "sqrt", "erf", "erfc", "lgamma", "tgamma", "ceil", "floor", "nearbyint", "rint",
            "round", "trunc", "lrint", "llrint", "lround", "llround", "fmod", "remainder", "remquo",
            "copysign", "nan", "nextafter", "nexttoward", "fdim", "fmax", "fmin", "fma"
        )
        val expectedNames = baseNames.flatMap { name -> listOf("${name}f", name, "${name}l") }.toSet()
        val declarations = CHeaderImportService().declarations("c.math")

        assertEquals(expectedNames, declarations.keys)
        assertTrue(declarations.values.all { it.kind == ForeignDeclarationKind.FUNCTION })
        assertEquals("long double", declarations.getValue("nexttowardl").parameterTypes[1])
        assertEquals("int*", declarations.getValue("frexpf").parameterTypes[1])
        assertEquals("long long int", declarations.getValue("llroundl").typeName)
        assertEquals("double*", declarations.getValue("modf").parameterTypes[1])
    }
}
