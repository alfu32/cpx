package cplus.compiler

import cplus.core.Diagnostic
import cplus.core.DiagnosticSeverity
import java.nio.file.Files
import java.nio.file.Path

data class SdkLayout(
    val root: Path,
    val stdSource: Path,
    val libcSource: Path,
    val libcInclude: Path,
    val runtimeSource: Path,
    val platformApi: Path,
    val platformSource: Path,
    val abiDescriptor: Path,
    val intrinsicsSource: Path,
    val startupSource: Path
)

data class SdkResolution(
    val manifest: SdkManifest,
    val layout: SdkLayout,
    val externalSysroot: Path?
)

data class SdkResolutionResult(
    val resolution: SdkResolution?,
    val diagnostics: List<Diagnostic>
) {
    val isSuccessful: Boolean
        get() = resolution != null && diagnostics.none { it.severity == DiagnosticSeverity.ERROR }
}

object SdkResolver {
    fun resolve(manifest: SdkManifest, target: TargetInfo, externalSysroot: Path? = null): SdkResolutionResult {
        val root = manifest.path.parent?.parent
            ?: return failure("SDK manifest has no SDK root: ${manifest.path}", "SDK008")
        val targetName = target.targetTriple
        val layout = SdkLayout(
            root = root,
            stdSource = root.resolve("std/src"),
            libcSource = root.resolve("libc/src"),
            libcInclude = root.resolve("libc/include"),
            runtimeSource = root.resolve("runtime/src"),
            platformApi = root.resolve("platform/api"),
            platformSource = root.resolve("platform/${targetName.substringBefore('-')}"),
            abiDescriptor = root.resolve("abi/$targetName.toml"),
            intrinsicsSource = root.resolve("intrinsics/intrinsics.cp"),
            startupSource = root.resolve("startup/$targetName")
        )
        val diagnostics = mutableListOf<Diagnostic>()
        listOf(
            "std source" to layout.stdSource,
            "libc source" to layout.libcSource,
            "libc include" to layout.libcInclude,
            "runtime source" to layout.runtimeSource,
            "platform API" to layout.platformApi,
            "platform source" to layout.platformSource,
            "ABI descriptor" to layout.abiDescriptor,
            "intrinsics source" to layout.intrinsicsSource,
            "startup source" to layout.startupSource
        ).forEach { (label, path) ->
            val directoryComponent = label in setOf(
                "std source",
                "libc source",
                "libc include",
                "runtime source",
                "platform API",
                "platform source",
                "startup source"
            )
            val valid = if (directoryComponent) {
                Files.isDirectory(path) && Files.list(path).use { it.findAny().isPresent }
            } else {
                Files.isRegularFile(path)
            }
            if (!valid) {
                diagnostics += Diagnostic(
                    DiagnosticSeverity.ERROR,
                    "SDK component '$label' is missing for target '$targetName': $path",
                    null,
                    "SDK008"
                )
            }
        }
        val normalizedSysroot = externalSysroot?.toAbsolutePath()?.normalize()
        if (normalizedSysroot != null && !Files.isDirectory(normalizedSysroot)) {
            diagnostics += Diagnostic(
                DiagnosticSeverity.ERROR,
                "external sysroot is not a directory: $normalizedSysroot",
                null,
                "SDK009"
            )
        }
        return if (diagnostics.isEmpty()) {
            SdkResolutionResult(SdkResolution(manifest, layout, normalizedSysroot), emptyList())
        } else {
            SdkResolutionResult(null, diagnostics)
        }
    }

    private fun failure(message: String, code: String): SdkResolutionResult =
        SdkResolutionResult(null, listOf(Diagnostic(DiagnosticSeverity.ERROR, message, null, code)))
}
