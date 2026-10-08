package cplus.compiler

import cplus.core.Diagnostic
import cplus.core.DiagnosticSeverity
import cplus.semantic.CHeaderMacro
import cplus.semantic.CHeaderSourceLocation
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

data class HeaderMacroDefinition(
    val name: String,
    val parameters: String?,
    val replacement: String,
    val source: Path?,
    val line: Int?
)

data class CHeaderPreprocessResult(
    val header: Path,
    val text: String,
    val includedFiles: Set<Path>,
    val macros: List<HeaderMacroDefinition>,
    val command: List<String>,
    val diagnostics: List<Diagnostic>
) {
    val isSuccessful: Boolean get() = diagnostics.none { it.severity == DiagnosticSeverity.ERROR }
}

fun CHeaderPreprocessResult.semanticMacros(): List<CHeaderMacro> = macros.map { macro ->
    CHeaderMacro(macro.name, macro.parameters, macro.replacement, macro.source, macro.line)
}

fun CHeaderPreprocessResult.semanticSourceLineOrigins(): Map<Int, CHeaderSourceLocation> {
    val marker = Regex("""^\s*#(?:line\s+)?\s*(\d+)\s+\"((?:\\.|[^\"])*)\".*$""")
    val origins = linkedMapOf<Int, CHeaderSourceLocation>()
    var currentPath: Path? = null
    var currentLine: Int? = null
    text.lineSequence().forEachIndexed { index, line ->
        val match = marker.matchEntire(line)
        if (match != null) {
            val pathText = match.groupValues[2].replace("\\\\", "\\").replace("\\\"", "\"")
            currentPath = pathText.takeIf { it.isNotEmpty() && !it.startsWith("<") }
                ?.let { runCatching { Path.of(it).toAbsolutePath().normalize() }.getOrNull() }
            currentLine = match.groupValues[1].toIntOrNull()
        } else {
            val path = currentPath
            val sourceLine = currentLine
            if (path != null && sourceLine != null) origins[index + 1] = CHeaderSourceLocation(path, sourceLine)
            currentLine = currentLine?.plus(1)
        }
    }
    return origins
}

/** Runs the selected C driver's real preprocessor; it does not interpret C itself. */
class CHeaderPreprocessor(
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    private val maximumOutputBytes: Int = DEFAULT_MAXIMUM_OUTPUT_BYTES
) {
    init {
        require(timeoutMillis > 0)
        require(maximumOutputBytes > 0)
    }

    fun preprocess(header: Path, environment: HeaderEnvironment): CHeaderPreprocessResult {
        val normalizedHeader = header.toAbsolutePath().normalize()
        if (!Files.isRegularFile(normalizedHeader)) {
            return failure(normalizedHeader, "CIMP010", "C header does not exist: ${normalizedHeader}")
        }
        val kind = CCompilerToolchains.classify(environment.cCompiler).kind
        val targetError = validateTargetDriver(environment, kind)
        if (targetError != null) return failure(normalizedHeader, "CIMP011", targetError)

        val relativeHeader = relativeHeaderPath(normalizedHeader, environment.includeSearchRoots)
            ?: return failure(normalizedHeader, "CIMP010", "C header is outside configured include roots: ${normalizedHeader}")
        val directory = try {
            Files.createTempDirectory("cplus-header-preprocess-")
        } catch (error: Exception) {
            return failure(normalizedHeader, "CIMP012", "unable to create preprocessor workspace: ${error.message}")
        }
        val input = directory.resolve("header_probe.c")
        return try {
            Files.writeString(input, "#include <$relativeHeader>\n")
            val command = command(environment, input)
            val process = try {
                ProcessBuilder(command).redirectErrorStream(true).start()
            } catch (error: Exception) {
                return failure(normalizedHeader, "CIMP013", "unable to start selected preprocessor '${environment.cCompiler}': ${error.message}", command)
            }
            val output = capture(process)
            when {
                output.timedOut -> failure(normalizedHeader, "CIMP014", "preprocessing exceeded ${timeoutMillis}ms", command)
                output.limitExceeded -> failure(normalizedHeader, "CIMP015", "preprocessor output exceeded $maximumOutputBytes bytes", command)
                output.exitCode != 0 -> failure(
                    normalizedHeader, "CIMP016",
                    "preprocessor failed with exit code ${output.exitCode}: ${output.text.trim()}", command
                )
                else -> CHeaderPreprocessResult(
                    normalizedHeader,
                    output.text,
                    includedFiles(output.text, input),
                    macroDefinitions(output.text, environment.includeSearchRoots),
                    command,
                    emptyList()
                )
            }
        } catch (error: Exception) {
            failure(normalizedHeader, "CIMP017", "unable to preprocess C header: ${error.message}")
        } finally {
            runCatching { Files.deleteIfExists(input) }
            runCatching { Files.deleteIfExists(directory) }
        }
    }

    internal fun command(environment: HeaderEnvironment, input: Path): List<String> {
        val compiler = environment.cCompiler
        val kind = CCompilerToolchains.classify(compiler).kind
        val includeFlags = when (kind) {
            CCompilerKind.MSVC, CCompilerKind.CLANG_CL -> environment.includeSearchRoots.map { "/I$it" }
            else -> environment.includeSearchRoots.flatMap { listOf("-I", it.toString()) }
        }
        val abiFlags = CCompilerToolchains.targetAbiFlags(environment.abi, compiler)
        return when (kind) {
            CCompilerKind.MSVC, CCompilerKind.CLANG_CL -> listOf(
                compiler, "/nologo", "/std:c17", "/X", "/E", "/PD", "/TC", "/Tc$input"
            ) + abiFlags + includeFlags
            CCompilerKind.GCC, CCompilerKind.CLANG, CCompilerKind.TCC -> {
                val sysroot = environment.externalSysroot?.let { listOf("--sysroot=$it") }.orEmpty()
                listOf(compiler, "-E", "-dD", "-nostdinc", "-x", "c", "-std=${environment.target.cDialect}") +
                    CCompilerToolchains.targetFlags(environment.target, compiler) +
                    sysroot + abiFlags + includeFlags + listOf(input.toString())
            }
            CCompilerKind.UNKNOWN -> listOf(compiler)
        }
    }

    private fun validateTargetDriver(environment: HeaderEnvironment, kind: CCompilerKind): String? {
        if (kind == CCompilerKind.UNKNOWN) {
            return "unsupported C preprocessor '${environment.cCompiler}' for target '${environment.target.targetTriple}'"
        }
        val hostTarget = defaultHostTargetTriple()
        val compilerName = environment.cCompiler.substringAfterLast('/').substringAfterLast('\\')
        val supportsTarget = when (kind) {
            CCompilerKind.TCC, CCompilerKind.MSVC, CCompilerKind.CLANG_CL ->
                environment.target.targetTriple == hostTarget
            CCompilerKind.GCC -> environment.target.targetTriple == hostTarget || when (environment.target.targetTriple) {
                "windows-x86_64" -> compilerName.contains("x86_64-w64-mingw32")
                "windows-aarch64" -> compilerName.contains("aarch64-w64-mingw32")
                "linux-aarch64" -> compilerName.contains("aarch64-linux-gnu")
                else -> compilerName.contains(environment.target.targetTriple.replace('-', '_'))
            }
            CCompilerKind.CLANG -> environment.target.targetTriple == hostTarget ||
                CCompilerToolchains.targetFlags(environment.target, environment.cCompiler).isNotEmpty()
            CCompilerKind.UNKNOWN -> false
        }
        return if (supportsTarget) null else
            "C preprocessor '${environment.cCompiler}' cannot establish target '${environment.target.targetTriple}' macros"
    }

    private data class CapturedProcess(
        val exitCode: Int,
        val text: String,
        val timedOut: Boolean = false,
        val limitExceeded: Boolean = false
    )

    private fun capture(process: Process): CapturedProcess {
        val bytes = AtomicReference<ByteArray?>()
        val readError = AtomicReference<Throwable?>()
        val limitExceeded = AtomicReference(false)
        val reader = Thread {
            try {
                val captured = ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                process.inputStream.use { stream ->
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        if (captured.size() + count > maximumOutputBytes) {
                            limitExceeded.set(true)
                            process.destroyForcibly()
                            break
                        }
                        captured.write(buffer, 0, count)
                    }
                }
                bytes.set(captured.toByteArray())
            } catch (error: Throwable) {
                readError.set(error)
            }
        }.apply { isDaemon = true; name = "cplus-header-preprocessor-output"; start() }
        val completed = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
        if (!completed) process.destroyForcibly()
        process.waitFor(2, TimeUnit.SECONDS)
        reader.join(2_000)
        if (!completed) return CapturedProcess(-1, "", timedOut = true)
        if (limitExceeded.get()) return CapturedProcess(process.exitValue(), "", limitExceeded = true)
        readError.get()?.let { throw IOException("unable to read preprocessor output", it) }
        if (reader.isAlive) throw IOException("preprocessor output reader did not terminate")
        return CapturedProcess(process.exitValue(), bytes.get()?.toString(Charsets.UTF_8).orEmpty())
    }

    private fun macroDefinitions(text: String, roots: List<Path>): List<HeaderMacroDefinition> {
        val marker = Regex("""^\s*#(?:line\s+)?\s*(\d+)\s+"((?:\\.|[^"])*)".*$""")
        val definition = Regex("""^\s*#\s*define\s+([A-Za-z_][A-Za-z0-9_]*)(?:\(([^)]*)\))?\s*(.*)$""")
        val undefinition = Regex("""^\s*#\s*undef\s+([A-Za-z_][A-Za-z0-9_]*)\s*$""")
        val normalizedRoots = roots.map { it.toAbsolutePath().normalize() }
        val output = linkedMapOf<String, HeaderMacroDefinition>()
        var currentPath: Path? = null
        var currentLine: Int? = null
        text.lineSequence().forEach { line ->
            val lineMarker = marker.matchEntire(line)
            if (lineMarker != null) {
                currentLine = lineMarker.groupValues[1].toIntOrNull()
                val pathText = unescapeMarkerPath(lineMarker.groupValues[2])
                currentPath = pathText.takeIf { !it.startsWith("<") }
                    ?.let { runCatching { Path.of(it).toAbsolutePath().normalize() }.getOrNull() }
                return@forEach
            }
            val source = currentPath
            if (source != null && normalizedRoots.any(source::startsWith)) {
                undefinition.matchEntire(line)?.let { output.remove(it.groupValues[1]) }
                definition.matchEntire(line)?.let { macro ->
                    val name = macro.groupValues[1]
                    output[name] = HeaderMacroDefinition(
                        name,
                        macro.groups[2]?.value,
                        macro.groupValues[3],
                        source,
                        currentLine
                    )
                }
            }
            currentLine = currentLine?.plus(1)
        }
        return output.values.toList()
    }

    private fun includedFiles(text: String, input: Path): Set<Path> {
        val marker = Regex("""^\s*#(?:line\s+)?\s*\d+\s+"((?:\\.|[^"])*)".*$""")
        val probe = input.toAbsolutePath().normalize()
        return text.lineSequence().mapNotNull { line ->
            val value = marker.matchEntire(line)?.groupValues?.get(1) ?: return@mapNotNull null
            if (value.startsWith("<")) return@mapNotNull null
            runCatching { Path.of(unescapeMarkerPath(value)).toAbsolutePath().normalize() }
                .getOrNull()?.takeIf { it != probe && Files.isRegularFile(it) }
        }.toCollection(linkedSetOf())
    }

    private fun unescapeMarkerPath(value: String): String =
        value.replace("\\\\", "\\").replace("\\\"", "\"")

    private fun relativeHeaderPath(header: Path, roots: List<Path>): String? = roots.firstNotNullOfOrNull { root ->
        val normalizedRoot = root.toAbsolutePath().normalize()
        if (!header.startsWith(normalizedRoot)) return@firstNotNullOfOrNull null
        val relative = normalizedRoot.relativize(header).joinToString("/")
        relative.takeIf {
            it.isNotBlank() && it.split('/').all { segment -> segment !in setOf(".", "..") } &&
                it.none { character -> character in "<>\n\r" }
        }
    }

    private fun failure(header: Path, code: String, message: String, command: List<String> = emptyList()) =
        CHeaderPreprocessResult(
            header, "", emptySet(), emptyList(), command,
            listOf(Diagnostic(DiagnosticSeverity.ERROR, message, null, code))
        )

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 20_000L
        const val DEFAULT_MAXIMUM_OUTPUT_BYTES = 16 * 1024 * 1024
    }
}
