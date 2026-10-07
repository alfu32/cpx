package cplus.semantic

enum class ForeignDeclarationKind {
    TYPE,
    FUNCTION,
    GLOBAL,
    ENUM_VALUE
}

data class CHeaderDeclaration(
    val name: String,
    val kind: ForeignDeclarationKind,
    val typeName: String? = null,
    val parameterTypes: List<String> = emptyList(),
    val isVariadic: Boolean = false
)

/**
 * Parses the deliberately small, declaration-only subset used by configured
 * C imports. Unsupported preprocessor and declaration forms stay outside the
 * semantic catalogue instead of being guessed.
 */
class CHeaderImportService(
    private val configuredHeaders: Map<String, String> = defaultHeaders
) {
    fun declarations(module: String): Map<String, CHeaderDeclaration> =
        configuredHeaders[module].orEmpty().let(::parse)

    fun unsupportedPreprocessorLines(module: String): List<String> = configuredHeaders[module]
        .orEmpty()
        .lineSequence()
        .map(String::trim)
        .filter { it.startsWith("#") }
        .toList()

    private fun parse(text: String): Map<String, CHeaderDeclaration> {
        val declarations = linkedMapOf<String, CHeaderDeclaration>()
        val functionPattern = Regex(
            """(?m)^\s*(?:extern\s+)?([A-Za-z_][A-Za-z0-9_\s\*]*?)\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(([^)]*)\)\s*;"""
        )
        functionPattern.findAll(text).forEach { match ->
            val returnType = normalizeType(match.groupValues[1])
            val rawParameters = match.groupValues[3].trim()
            val variadic = rawParameters.split(',').any { it.trim() == "..." }
            val parameters = rawParameters
                .split(',')
                .map(String::trim)
                .filter { it.isNotEmpty() && it != "..." && it != "void" }
                .map(::parameterType)
            declarations[match.groupValues[2]] = CHeaderDeclaration(
                match.groupValues[2],
                ForeignDeclarationKind.FUNCTION,
                returnType,
                parameters,
                variadic
            )
        }
        val typedefPattern = Regex(
            """(?m)^\s*typedef\s+([A-Za-z_][A-Za-z0-9_\s\*]*)\s+([A-Za-z_][A-Za-z0-9_]*)\s*;"""
        )
        typedefPattern.findAll(text).forEach { match ->
            declarations[match.groupValues[2]] = CHeaderDeclaration(
                match.groupValues[2],
                ForeignDeclarationKind.TYPE,
                normalizeType(match.groupValues[1])
            )
        }
        val globalPattern = Regex(
            """(?m)^\s*extern\s+([A-Za-z_][A-Za-z0-9_\s\*]*)\s+([A-Za-z_][A-Za-z0-9_]*)\s*;"""
        )
        globalPattern.findAll(text).forEach { match ->
            if (match.groupValues[2] !in declarations) {
                declarations[match.groupValues[2]] = CHeaderDeclaration(
                    match.groupValues[2],
                    ForeignDeclarationKind.GLOBAL,
                    normalizeType(match.groupValues[1])
                )
            }
        }
        return declarations
    }

    private fun parameterType(parameter: String): String {
        val normalized = normalizeType(parameter)
        return Regex("^(.+?)(?:\\s+[A-Za-z_][A-Za-z0-9_]*)?$")
            .matchEntire(normalized)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.ifEmpty { normalized }
            ?: normalized
    }

    private fun normalizeType(type: String): String = type
        .trim()
        .replace(Regex("\\s+"), " ")
        .removePrefix("extern ")
        .trim()

    companion object {
        private val defaultHeaders = mapOf(
            "c.stdio" to """
                extern int puts(const char* text);
            """.trimIndent(),
            "c.math" to """
                extern double sqrt(double value);
                extern double sin(double value);
                extern double cos(double value);
                extern double pow(double left, double right);
            """.trimIndent(),
            "c.stdlib" to """
                extern int abs(int value);
                extern void* malloc(size_t size);
                extern void free(void* pointer);
            """.trimIndent()
        )
    }
}
