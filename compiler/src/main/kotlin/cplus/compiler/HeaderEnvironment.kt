package cplus.compiler

import java.nio.file.Path
import java.nio.file.Files

/**
 * Immutable request-specific inputs used to discover C declarations. It is
 * attached to the resolved SDK so every compilation path and downstream tool
 * sees the same target, search order, and selected C driver.
 */
data class HeaderEnvironment(
    val sdkRoot: Path,
    val sdkLibcInclude: Path,
    val sdkRuntimeInclude: Path,
    val includeDirectories: List<Path>,
    val externalSysroot: Path?,
    val target: TargetInfo,
    val abi: TargetAbiDescriptor,
    val cCompiler: String
) {
    val includeSearchRoots: List<Path> = buildList {
        add(sdkLibcInclude)
        add(sdkRuntimeInclude)
        addAll(includeDirectories)
        externalSysroot?.let { sysroot ->
            add(sysroot.resolve("usr/include").takeIf(Files::isDirectory))
            add(sysroot.resolve("include").takeIf(Files::isDirectory))
        }
    }.filterNotNull().map { it.toAbsolutePath().normalize() }.distinct()

    init {
        require(abi.targetTriple == target.targetTriple) {
            "header environment ABI '${abi.targetTriple}' does not match target '${target.targetTriple}'"
        }
    }

    companion object {
        fun create(
            request: CompileRequest,
            sdk: SdkResolution,
            abi: TargetAbiDescriptor
        ): HeaderEnvironment = HeaderEnvironment(
            sdkRoot = sdk.layout.root.toAbsolutePath().normalize(),
            sdkLibcInclude = sdk.layout.libcInclude,
            sdkRuntimeInclude = sdk.layout.runtimeInclude,
            includeDirectories = request.cIncludeDirectories.toList(),
            externalSysroot = request.externalSysroot?.toAbsolutePath()?.normalize(),
            target = request.target,
            abi = abi,
            cCompiler = CCompilerToolchains.resolve(request.target, request.cCompiler)
        )
    }
}
