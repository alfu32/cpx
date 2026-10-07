package cplus.compiler

enum class CCompilerKind { GCC, CLANG, TCC, UNKNOWN }

data class CCompilerCapabilities(
    val kind: CCompilerKind,
    val supportsNoDefaultLibraries: Boolean,
    val supportsIntegratedAssembler: Boolean,
    val supportsC17: Boolean
)

object CCompilerToolchains {
    fun classify(executable: String): CCompilerCapabilities = when {
        executable.endsWith("clang") || executable.endsWith("clang-17") -> CCompilerCapabilities(CCompilerKind.CLANG, true, true, true)
        executable.endsWith("gcc") || executable == "cc" -> CCompilerCapabilities(CCompilerKind.GCC, true, true, true)
        executable.endsWith("tcc") -> CCompilerCapabilities(CCompilerKind.TCC, false, true, true)
        else -> CCompilerCapabilities(CCompilerKind.UNKNOWN, false, false, false)
    }

    fun validate(target: TargetAbiDescriptor, compiler: String, selfHosted: Boolean): List<String> {
        val capabilities = classify(compiler)
        return buildList {
            if (!capabilities.supportsC17) add("downstream C compiler '$compiler' does not advertise C17 support")
            if (selfHosted && !capabilities.supportsNoDefaultLibraries) add("downstream C compiler '$compiler' cannot enforce self-hosted no-default-library policy")
            if (target.objectFormat == "elf" && capabilities.kind == CCompilerKind.UNKNOWN) add("unknown compiler adapter for ELF target")
        }
    }
}
