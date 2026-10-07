package cplus.compiler

import java.nio.file.Files
import java.nio.file.Path

data class CHeaderProfile(val profile: LibcProfile, val headers: List<String>)

object CHeaderProfileGenerator {
    fun inspect(includeRoot: Path, profile: LibcProfile = LibcProfile.C17): CHeaderProfile =
        CHeaderProfile(profile, LibcProfileCatalogue.statuses(includeRoot).filter { it.status == "delivered" }.map { it.header }.sorted())

    fun emitIndex(includeRoot: Path, output: Path, profile: LibcProfile = LibcProfile.C17) {
        val headers = inspect(includeRoot, profile).headers
        output.parent?.let(Files::createDirectories)
        Files.writeString(output, buildString {
            appendLine("/* C+ SDK ${profile.name} header index; generated deterministically. */")
            headers.forEach { appendLine("#include <$it>") }
        })
    }
}
