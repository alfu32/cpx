package cplus.semantic

import cplus.core.SourceFile

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
    val isVariadic: Boolean = false,
    val sourceRange: IntRange? = null
)

data class CSourceUnit(
    val source: SourceFile,
    val moduleName: String
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

    fun isKnownModule(module: String): Boolean = module in configuredHeaders

    fun sourceDeclarations(text: String): Map<String, CHeaderDeclaration> =
        parse(text, allowFunctionDefinitions = true)

    fun unsupportedPreprocessorLines(module: String): List<String> = configuredHeaders[module]
        .orEmpty()
        .lineSequence()
        .map(String::trim)
        .filter { it.startsWith("#") }
        .toList()

    private fun parse(text: String, allowFunctionDefinitions: Boolean = false): Map<String, CHeaderDeclaration> {
        val declarations = linkedMapOf<String, CHeaderDeclaration>()
        val functionPattern = Regex(
            """(?m)^\s*(?:extern\s+)?([A-Za-z_][A-Za-z0-9_\s\*]*?)\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(([^)]*)\)\s*${if (allowFunctionDefinitions) "(?:;|\\{)" else ";"}"""
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
                variadic,
                match.range
            )
        }
        val typedefPattern = Regex(
            """(?m)^\s*typedef\s+([A-Za-z_][A-Za-z0-9_\s\*]*)\s+([A-Za-z_][A-Za-z0-9_]*)\s*;"""
        )
        typedefPattern.findAll(text).forEach { match ->
            declarations[match.groupValues[2]] = CHeaderDeclaration(
                match.groupValues[2],
                ForeignDeclarationKind.TYPE,
                normalizeType(match.groupValues[1]),
                sourceRange = match.range
            )
        }
        val globalPattern = Regex(
            """(?m)^\s*(?:extern\s+)?([A-Za-z_][A-Za-z0-9_\s\*]*?)\s+([A-Za-z_][A-Za-z0-9_]*)\s*(?:=\s*[^;]+)?\s*;"""
        )
        val globalText = if (allowFunctionDefinitions) stripBracedBodies(text) else text
        globalPattern.findAll(globalText).forEach { match ->
            if (match.groupValues[2] !in declarations) {
                declarations[match.groupValues[2]] = CHeaderDeclaration(
                    match.groupValues[2],
                    ForeignDeclarationKind.GLOBAL,
                    normalizeType(match.groupValues[1]),
                    sourceRange = match.range
                )
            }
        }
        return declarations
    }

    private fun stripBracedBodies(text: String): String {
        val characters = text.toCharArray()
        var depth = 0
        text.forEachIndexed { index, character ->
            when {
                character == '{' -> {
                    depth++
                    characters[index] = ' '
                }
                character == '}' && depth > 0 -> {
                    depth--
                    characters[index] = ' '
                }
                depth > 0 && character != '\n' -> characters[index] = ' '
            }
        }
        return String(characters)
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
                extern int printf(const char* format, ...);
                extern int fprintf(FILE* stream, const char* format, ...);
                extern int sprintf(char* buffer, const char* format, ...);
                extern int snprintf(char* buffer, size_t size, const char* format, ...);
                extern int vprintf(const char* format, va_list arguments);
                extern int vfprintf(FILE* stream, const char* format, va_list arguments);
                extern int vsprintf(char* buffer, const char* format, va_list arguments);
                extern int vsnprintf(char* buffer, size_t size, const char* format, va_list arguments);
                extern int puts(const char* text);
                extern int putchar(int character);
                extern int getchar(void);
                extern FILE* fopen(const char* path, const char* mode);
                extern int fclose(FILE* stream);
                extern int fflush(FILE* stream);
                extern int fgetc(FILE* stream);
                extern int fputc(int character, FILE* stream);
                extern size_t fread(void* buffer, size_t size, size_t count, FILE* stream);
                extern size_t fwrite(const void* buffer, size_t size, size_t count, FILE* stream);
            """.trimIndent(),
            "c.stddef" to """
                typedef unsigned long size_t;
                typedef long ptrdiff_t;
                typedef long max_align_t;
            """.trimIndent(),
            "c.math" to """
                extern double sqrt(double value);
                extern double sin(double value);
                extern double cos(double value);
                extern double pow(double left, double right);
                extern double fabs(double value);
                extern double floor(double value);
                extern double ceil(double value);
                extern double exp(double value);
                extern double log(double value);
            """.trimIndent(),
            "c.stdlib" to """
                extern int abs(int value);
                extern long labs(long value);
                extern int atoi(const char* text);
                extern long strtol(const char* text, char** end, int base);
                extern unsigned long strtoul(const char* text, char** end, int base);
                extern void* malloc(size_t size);
                extern void* calloc(size_t count, size_t size);
                extern void* realloc(void* pointer, size_t size);
                extern void free(void* pointer);
                extern int rand(void);
                extern void srand(unsigned int seed);
                extern void exit(int status);
            """.trimIndent(),
            "c.string" to """
                extern size_t strlen(const char* text);
                extern char* strcpy(char* destination, const char* source);
                extern char* strncpy(char* destination, const char* source, size_t count);
                extern int strcmp(const char* left, const char* right);
                extern int strncmp(const char* left, const char* right, size_t count);
                extern void* memcpy(void* destination, const void* source, size_t count);
                extern void* memmove(void* destination, const void* source, size_t count);
                extern void* memset(void* destination, int value, size_t count);
                extern char* strchr(const char* text, int character);
                extern char* strstr(const char* text, const char* pattern);
            """.trimIndent(),
            "c.ctype" to """
                extern int isalnum(int character);
                extern int isalpha(int character);
                extern int isdigit(int character);
                extern int isspace(int character);
                extern int islower(int character);
                extern int isupper(int character);
                extern int tolower(int character);
                extern int toupper(int character);
            """.trimIndent(),
            "c.time" to """
                typedef long time_t;
                typedef long clock_t;
                extern time_t time(time_t* result);
                extern double difftime(time_t end, time_t start);
                extern clock_t clock(void);
            """.trimIndent(),
            "c.stdint" to """
                typedef signed char int8_t;
                typedef unsigned char uint8_t;
                typedef short int16_t;
                typedef unsigned short uint16_t;
                typedef int int32_t;
                typedef unsigned int uint32_t;
                typedef long long int64_t;
                typedef unsigned long long uint64_t;
            """.trimIndent(),
            "c.stdarg" to """
                typedef void* va_list;
            """.trimIndent()
        )
    }
}
