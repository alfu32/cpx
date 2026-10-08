package cplus.compiler

enum class CCompilerKind { GCC, CLANG, CLANG_CL, MSVC, TCC, UNKNOWN }

data class CCompilerCapabilities(
    val kind: CCompilerKind,
    val supportsNoDefaultLibraries: Boolean,
    val supportsIntegratedAssembler: Boolean,
    val supportsC17: Boolean
)

object CCompilerToolchains {
    private val INT128_PROBE_SOURCE = """
        #if !defined(__SIZEOF_INT128__)
        #error compiler does not define a 128-bit integer type
        #endif
        _Static_assert(sizeof(__int128) == 16, "signed int128 width");
        _Static_assert(sizeof(unsigned __int128) == 16, "unsigned int128 width");
        _Static_assert(_Alignof(__int128) == 16, "signed int128 alignment");
        _Static_assert(_Alignof(unsigned __int128) == 16, "unsigned int128 alignment");
        __int128 signed_value(__int128 value) { return value + 1; }
        unsigned __int128 unsigned_value(unsigned __int128 value) { return value + 1; }
    """.trimIndent()

    fun classify(executable: String): CCompilerCapabilities {
        val name = executable.substringAfterLast('/').substringAfterLast('\\').lowercase()
        return when {
            name == "clang-cl" || name == "clang-cl.exe" -> CCompilerCapabilities(CCompilerKind.CLANG_CL, true, true, true)
            name == "cl" || name == "cl.exe" -> CCompilerCapabilities(CCompilerKind.MSVC, true, false, true)
            name == "clang" || name == "clang.exe" || name.startsWith("clang-") -> CCompilerCapabilities(CCompilerKind.CLANG, true, true, true)
            name.endsWith("gcc") || name.endsWith("gcc.exe") || name == "cc" || name == "cc.exe" -> CCompilerCapabilities(CCompilerKind.GCC, true, true, true)
            name.endsWith("tcc") || name.endsWith("tcc.exe") -> CCompilerCapabilities(CCompilerKind.TCC, false, true, true)
            else -> CCompilerCapabilities(CCompilerKind.UNKNOWN, false, false, false)
        }
    }

    /**
     * Selects a C compiler driver without selecting a C standard library. The
     * runtime link plan supplies the C+ runtime and only the target OS import
     * libraries; the selected driver is merely the C syntax/object tool.
     */
    fun resolve(target: TargetInfo, requested: String? = null): String {
        requested?.takeIf(String::isNotBlank)?.let { return it }
        System.getenv("CPLUS_C_COMPILER")?.takeIf(String::isNotBlank)?.let { return it }

        val candidates = when {
            target.targetTriple == "windows-x86_64" && !isWindowsHost() -> listOf("x86_64-w64-mingw32-gcc", "clang")
            target.targetTriple == "windows-aarch64" && !isWindowsHost() -> listOf("aarch64-w64-mingw32-gcc", "clang")
            target.osName() == "windows" && isWindowsHost() -> listOf("clang-cl", "cl", "clang")
            target.targetTriple == "linux-aarch64" -> listOf("aarch64-linux-gnu-gcc", "clang")
            else -> listOf("cc", "clang", "gcc")
        }
        return candidates.firstOrNull(::isOnPath) ?: candidates.first()
    }

    fun targetFlags(target: TargetInfo, compiler: String): List<String> {
        val capabilities = classify(compiler)
        if (capabilities.kind == CCompilerKind.MSVC || capabilities.kind == CCompilerKind.CLANG_CL) return emptyList()
        val name = compiler.substringAfterLast('/').substringAfterLast('\\').lowercase()
        if (target.osName() == "windows" && capabilities.kind == CCompilerKind.CLANG &&
            !name.contains("mingw") && !name.contains("w64")) {
            return listOf("--target=${if (target.targetTriple == "windows-aarch64") "aarch64-pc-windows-msvc" else "x86_64-pc-windows-msvc"}")
        }
        if (target.targetTriple == "linux-aarch64" && capabilities.kind == CCompilerKind.CLANG) {
            return listOf("--target=aarch64-unknown-linux-gnu")
        }
        return emptyList()
    }

    fun isMsvcStyle(compiler: String): Boolean = classify(compiler).kind in setOf(CCompilerKind.MSVC, CCompilerKind.CLANG_CL)

    /**
     * The current int128 ABI contract is deliberately limited to Linux x86_64
     * and GCC/Clang-compatible C drivers. The probe verifies the selected
     * driver's width, alignment, and C17 declaration support before linking.
     */
    fun supportsInt128(target: TargetAbiDescriptor, compiler: String): Boolean {
        if ("int128" !in target.features || target.targetTriple != "linux-x86_64") return false
        if (classify(compiler).kind !in setOf(CCompilerKind.GCC, CCompilerKind.CLANG)) return false
        val executableName = compiler.substringAfterLast('/').substringAfterLast('\\').lowercase()
        if (executableName.contains("mingw") || executableName.contains("w64")) return false

        val process = try {
            ProcessBuilder(compiler, "-std=c17", "-x", "c", "-fsyntax-only", "-")
                .redirectErrorStream(true)
                .start()
        } catch (_: Exception) {
            return false
        }
        return try {
            process.outputStream.bufferedWriter().use { it.write(INT128_PROBE_SOURCE) }
            if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly()
                false
            } else {
                process.inputStream.bufferedReader().use { it.readText() }
                process.exitValue() == 0
            }
        } catch (_: Exception) {
            process.destroyForcibly()
            false
        }
    }

    fun validateTargetFeatures(target: TargetAbiDescriptor, compiler: String): List<String> = buildList {
        if ("int128" in target.features && !supportsInt128(target, compiler)) {
            add(
                "target '${target.targetTriple}' advertises int128, but C compiler '$compiler' " +
                    "does not satisfy the verified 128-bit width/alignment ABI contract"
            )
        }
    }

    private fun TargetInfo.osName(): String = targetTriple.substringBefore('-')

    private fun isWindowsHost(): Boolean = System.getProperty("os.name").contains("windows", ignoreCase = true)

    private fun isOnPath(executable: String): Boolean {
        val candidate = java.nio.file.Path.of(executable)
        if (candidate.parent != null) return java.nio.file.Files.isExecutable(candidate)
        val path = System.getenv("PATH") ?: return false
        val extensions = if (isWindowsHost()) listOf("", ".exe", ".cmd", ".bat") else listOf("")
        return path.split(java.io.File.pathSeparator).any { directory ->
            extensions.any { extension -> java.nio.file.Files.isRegularFile(java.nio.file.Path.of(directory, executable + extension)) }
        }
    }

    fun validate(target: TargetAbiDescriptor, compiler: String, selfHosted: Boolean): List<String> {
        val capabilities = classify(compiler)
        return buildList {
            if (!capabilities.supportsC17) add("downstream C compiler '$compiler' does not advertise C17 support")
            if (selfHosted && !capabilities.supportsNoDefaultLibraries) add("downstream C compiler '$compiler' cannot enforce self-hosted no-default-library policy")
            if (capabilities.kind == CCompilerKind.UNKNOWN) add("unknown compiler adapter for target '${target.targetTriple}'")
        }
    }
}
