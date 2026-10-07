package cplus.compiler

import cplus.comptime.ComptimeTargetInfo
import cplus.core.Diagnostic
import cplus.core.DiagnosticSeverity

enum class RuntimeProfile {
    FREESTANDING,
    CPLUS,
    SYSTEM
}

enum class LibcProfile {
    NONE,
    C17,
    C23
}

data class BuildProfile(
    val runtime: RuntimeProfile = RuntimeProfile.CPLUS,
    val libc: LibcProfile = LibcProfile.C17
)

object BuildProfileValidator {
    fun validate(profile: BuildProfile, sdk: SdkManifest): List<Diagnostic> = buildList {
        if (profile.runtime == RuntimeProfile.FREESTANDING && profile.libc != LibcProfile.NONE) {
            add(error("freestanding runtime requires --libc=none", "SDK005"))
        }
        if (profile.runtime != RuntimeProfile.FREESTANDING && profile.libc == LibcProfile.NONE) {
            add(error("${profile.runtime.cliName} runtime requires a hosted libc profile", "SDK005"))
        }
        val manifestProfile = sdk.libcProfileVersion.substringBefore('-').lowercase()
        if (profile.libc != LibcProfile.NONE && profile.libc.cliName != manifestProfile) {
            add(error(
                "libc profile '${profile.libc.cliName}' is not provided by SDK profile '${sdk.libcProfileVersion}'",
                "SDK006"
            ))
        }
        if (profile.libc == LibcProfile.C23) {
            add(error("C23 libc profile is not implemented in this SDK", "SDK007"))
        }
    }

    fun toComptimeTarget(target: TargetInfo, descriptor: TargetAbiDescriptor? = null): ComptimeTargetInfo =
        descriptor?.toComptimeTarget(target) ?: ComptimeTargetInfo(
            cDialect = target.cDialect,
            runtimeProfile = target.buildProfile.runtime.cliName,
            libcProfile = target.buildProfile.libc.cliName,
            libcProfiles = setOf(target.buildProfile.libc.cliName)
        )

    private fun error(message: String, code: String): Diagnostic = Diagnostic(
        DiagnosticSeverity.ERROR,
        message,
        null,
        code
    )
}

private val RuntimeProfile.cliName: String
    get() = name.lowercase()

private val LibcProfile.cliName: String
    get() = name.lowercase()
