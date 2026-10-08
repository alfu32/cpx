package cplus.compiler

import cplus.core.Diagnostic
import cplus.core.DiagnosticSeverity
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

data class SdkDoctorReport(
    val target: String,
    val checks: List<String>,
    val diagnostics: List<Diagnostic>
) {
    val isSuccessful: Boolean get() = diagnostics.none { it.severity == DiagnosticSeverity.ERROR }
}

object SdkDoctor {
    fun inspect(manifestPath: Path, target: TargetInfo = TargetInfo()): SdkDoctorReport {
        val checks = mutableListOf<String>()
        val manifest = SdkManifestLoader.load(manifestPath)
        if (!manifest.isSuccessful) return SdkDoctorReport(target.targetTriple, checks, manifest.diagnostics)
        checks += "manifest"
        val resolution = SdkResolver.resolve(manifest.manifest!!, target)
        if (!resolution.isSuccessful) return SdkDoctorReport(target.targetTriple, checks, resolution.diagnostics)
        checks += "layout"
        val descriptor = TargetRegistry.load(resolution.resolution!!)
        if (!descriptor.isSuccessful) return SdkDoctorReport(target.targetTriple, checks, descriptor.diagnostics)
        checks += "target descriptor"
        val intrinsics = IntrinsicRegistry.load(resolution.resolution.layout.intrinsicCatalogue)
        if (!intrinsics.isSuccessful) return SdkDoctorReport(target.targetTriple, checks, intrinsics.diagnostics)
        checks += "intrinsic catalogue"
        val profileDiagnostics = BuildProfileValidator.validate(target.buildProfile, manifest.manifest!!)
        if (profileDiagnostics.isNotEmpty()) return SdkDoctorReport(target.targetTriple, checks, profileDiagnostics)
        val runtimePlan = RuntimeLinker.plan(resolution.resolution, target)
        if (!runtimePlan.isSuccessful) return SdkDoctorReport(target.targetTriple, checks, runtimePlan.diagnostics)
        checks += "runtime link plan"
        val metadata = SdkMetadataCache.loadOrBuild(resolution.resolution)
        if (!metadata.isSuccessful) return SdkDoctorReport(target.targetTriple, checks, metadata.diagnostics)
        checks += if (metadata.rebuilt) "metadata rebuilt" else "metadata cache"
        return SdkDoctorReport(target.targetTriple, checks, emptyList())
    }
}

data class SdkPackageEntry(val path: String, val hash: String, val bytes: Long)

object SdkPackageIndex {
    fun build(root: Path, excludedPaths: Set<Path> = emptySet()): List<SdkPackageEntry> {
        if (!Files.isDirectory(root)) return emptyList()
        val normalizedRoot = root.toAbsolutePath().normalize()
        val excluded = (excludedPaths + root.resolve("sdk-package.index"))
            .map { it.toAbsolutePath().normalize() }
            .toSet()
        return Files.walk(root).use { stream ->
            stream.filter { path ->
                if (!Files.isRegularFile(path)) return@filter false
                val normalizedPath = path.toAbsolutePath().normalize()
                if (normalizedPath in excluded) return@filter false
                val relative = normalizedRoot.relativize(normalizedPath)
                relative.none { it.toString() == "cache" }
            }
                .map { path ->
                    val relative = normalizedRoot.relativize(path.toAbsolutePath().normalize())
                    SdkPackageEntry(relative.toString().replace('\\', '/'), sha256(path), Files.size(path))
                }
                .sorted(compareBy(SdkPackageEntry::path))
                .toList()
        }
    }

    fun serialize(entries: List<SdkPackageEntry>): String = buildString {
        append("CPLUS_SDK_PACKAGE_INDEX\n")
        entries.sortedBy { it.path }.forEach { append("${it.hash}\t${it.bytes}\t${it.path}\n") }
    }

    private fun sha256(path: Path): String = MessageDigest.getInstance("SHA-256")
        .digest(Files.readAllBytes(path))
        .joinToString("") { "%02x".format(it) }
}
