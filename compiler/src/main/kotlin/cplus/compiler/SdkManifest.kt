package cplus.compiler

import cplus.core.Diagnostic
import cplus.core.DiagnosticSeverity
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

data class SdkManifest(
    val path: Path,
    val sdkVersion: String,
    val languageAbiVersion: String,
    val runtimeAbiVersion: String,
    val cplusAbiVersion: String,
    val libcProfileVersion: String,
    val contentHash: String
) {
    val identity: SdkManifestIdentity
        get() = SdkManifestIdentity(
            path,
            sdkVersion,
            languageAbiVersion,
            runtimeAbiVersion,
            cplusAbiVersion,
            libcProfileVersion,
            contentHash
        )
}

data class SdkManifestIdentity(
    val path: Path,
    val sdkVersion: String,
    val languageAbiVersion: String,
    val runtimeAbiVersion: String,
    val cplusAbiVersion: String,
    val libcProfileVersion: String,
    val contentHash: String
)

data class SdkManifestLoadResult(
    val manifest: SdkManifest?,
    val diagnostics: List<Diagnostic>
) {
    val isSuccessful: Boolean
        get() = manifest != null && diagnostics.none { it.severity == DiagnosticSeverity.ERROR }
}

object SdkManifestContract {
    const val CURRENT_LANGUAGE_ABI_VERSION = "1"
    const val CURRENT_RUNTIME_ABI_VERSION = "1"
    const val CURRENT_CPLUS_ABI_VERSION = "1"
    const val CURRENT_LIBC_PROFILE_VERSION = "c17-1"
}

object SdkManifestLocator {
    fun defaultManifestPath(): Path {
        val configured = System.getProperty("cplus.sdk.manifest")
            ?.takeIf(String::isNotBlank)
            ?.let(Path::of)
        if (configured != null) return configured

        var directory = Path.of("").toAbsolutePath().normalize()
        while (true) {
            val candidate = directory.resolve("sdk/manifest/sdk.toml")
            if (Files.isRegularFile(candidate)) return candidate
            val parent = directory.parent ?: break
            directory = parent
        }
        return Path.of("sdk", "manifest", "sdk.toml")
    }
}

object SdkManifestLoader {
    private val entryPattern = Regex("""^([A-Za-z_][A-Za-z0-9_]*)\s*=\s*"([^"]*)"$""")
    private val requiredKeys = listOf(
        "sdk_version",
        "language_abi_version",
        "runtime_abi_version",
        "cplus_abi_version",
        "libc_profile_version"
    )

    fun load(path: Path): SdkManifestLoadResult {
        val normalized = path.toAbsolutePath().normalize()
        if (!Files.isRegularFile(normalized)) {
            return failure(
                "SDK manifest does not exist or is not a regular file: $normalized",
                "SDK001"
            )
        }
        val bytes = try {
            Files.readAllBytes(normalized)
        } catch (error: Exception) {
            return failure(
                "unable to read SDK manifest '$normalized': ${error.message ?: error::class.simpleName}",
                "SDK001"
            )
        }
        val values = linkedMapOf<String, String>()
        val diagnostics = mutableListOf<Diagnostic>()
        bytes.toString(Charsets.UTF_8).lineSequence().forEachIndexed { index, rawLine ->
            val line = rawLine.substringBefore('#').trim()
            if (line.isEmpty()) return@forEachIndexed
            val match = entryPattern.matchEntire(line)
            if (match == null) {
                diagnostics += Diagnostic(
                    DiagnosticSeverity.ERROR,
                    "invalid SDK manifest entry on line ${index + 1}; expected quoted key/value syntax",
                    null,
                    "SDK002"
                )
                return@forEachIndexed
            }
            val key = match.groupValues[1]
            if (key in values) {
                diagnostics += Diagnostic(
                    DiagnosticSeverity.ERROR,
                    "duplicate SDK manifest key '$key'",
                    null,
                    "SDK002"
                )
            } else {
                values[key] = match.groupValues[2]
            }
        }
        if (diagnostics.isNotEmpty()) return SdkManifestLoadResult(null, diagnostics)

        requiredKeys.forEach { key ->
            if (values[key].isNullOrBlank()) {
                diagnostics += Diagnostic(
                    DiagnosticSeverity.ERROR,
                    "SDK manifest is missing required key '$key'",
                    null,
                    "SDK003"
                )
            }
        }
        if (diagnostics.isNotEmpty()) return SdkManifestLoadResult(null, diagnostics)

        val runtimeAbi = values.getValue("runtime_abi_version")
        if (runtimeAbi != SdkManifestContract.CURRENT_RUNTIME_ABI_VERSION) {
            diagnostics += Diagnostic(
                DiagnosticSeverity.ERROR,
                "SDK runtime ABI version '$runtimeAbi' is incompatible with compiler runtime ABI version '${SdkManifestContract.CURRENT_RUNTIME_ABI_VERSION}'",
                null,
                "SDK004"
            )
        }
        if (diagnostics.isNotEmpty()) return SdkManifestLoadResult(null, diagnostics)

        return SdkManifestLoadResult(
            SdkManifest(
                normalized,
                values.getValue("sdk_version"),
                values.getValue("language_abi_version"),
                runtimeAbi,
                values.getValue("cplus_abi_version"),
                values.getValue("libc_profile_version"),
                sha256(bytes)
            ),
            emptyList()
        )
    }

    private fun failure(message: String, code: String): SdkManifestLoadResult =
        SdkManifestLoadResult(null, listOf(Diagnostic(DiagnosticSeverity.ERROR, message, null, code)))

    private fun sha256(bytes: ByteArray): String = MessageDigest
        .getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte) }
}
