package cplus.compiler

import java.nio.file.Files
import java.nio.file.Path
import java.io.File
import kotlin.io.path.deleteIfExists

/**
 * The source-level evidence required before the C17 profile can be called
 * delivered. Header presence alone is deliberately insufficient: the report
 * also checks the runtime implementation set, target-specific context
 * support, and independently compiled C fixtures.
 */
object C17ConformanceAudit {
    private val runtimeSources = listOf(
        "libc.core" to "libc_core.c",
        "stdio" to "stdio.c",
        "format" to "format.c",
        "time" to "time.c",
        "math" to "math.c",
        "complex.arithmetic" to "complex_arithmetic.c",
        "ctype" to "ctype.c",
        "locale" to "locale.c",
        "signal" to "signal.c",
        "wide" to "wide.c",
        "wctype" to "wctype.c",
        "filesystem" to "fs.c"
    )

    fun inspect(resolution: SdkResolution, target: TargetInfo): ConformanceReport {
        val cases = mutableListOf<ConformanceCase>()
        val descriptorResult = resolution.targetDescriptor?.let { TargetDescriptorResult(it, emptyList()) }
            ?: TargetRegistry.load(resolution.layout.abiDescriptor)
        val descriptor = descriptorResult.descriptor

        if (descriptor == null) {
            val details = descriptorResult.diagnostics.joinToString("; ") { it.message }
            return ConformanceReport(
                listOf(
                    ConformanceCase(
                        "target.descriptor",
                        ConformanceArea.ABI,
                        target.targetTriple,
                        target.buildProfile.libc.name.lowercase(),
                        "fail",
                        details.ifBlank { "target descriptor is unavailable" }
                    )
                )
            )
        }

        if (target.buildProfile.libc != LibcProfile.C17) {
            return ConformanceReport(
                listOf(
                    ConformanceCase(
                        "libc.c17.profile",
                        ConformanceArea.LIBC,
                        descriptor.targetTriple,
                        target.buildProfile.libc.name.lowercase(),
                        "unsupported",
                        "the C17 audit requires the selected libc profile to be c17"
                    )
                )
            )
        }

        LibcProfileCatalogue.statuses(resolution.layout.libcInclude).forEach { header ->
            cases += ConformanceCase(
                "libc.header.${header.header.removeSuffix(".h")}",
                ConformanceArea.LIBC,
                descriptor.targetTriple,
                "c17",
                if (header.status == "delivered") "pass" else "fail",
                if (header.status == "delivered") "SDK header exists" else "SDK header is missing"
            )
        }

        runtimeSources.forEach { (id, file) ->
            val path = resolution.layout.runtimeSource.resolve(file)
            val supported = id != "complex.arithmetic" || "c17_complex" in descriptor.features
            val present = Files.isRegularFile(path)
            cases += ConformanceCase(
                "runtime.source.$id",
                ConformanceArea.RUNTIME,
                descriptor.targetTriple,
                "c17",
                when {
                    !supported -> "unsupported"
                    present -> "pass"
                    else -> "fail"
                },
                when {
                    !supported -> "complex arithmetic runtime helpers are not enabled for this target"
                    present -> path.toString()
                    else -> "runtime source is missing: $path"
                }
            )
        }

        val runtimePlan = RuntimeLinker.plan(resolution, target)
        cases += ConformanceCase(
            "runtime.link-plan",
            ConformanceArea.RUNTIME,
            descriptor.targetTriple,
            "c17",
            if (runtimePlan.isSuccessful) "pass" else "fail",
            runtimePlan.diagnostics.joinToString("; ").ifBlank { "runtime source and startup plan is available" }
        )

        val setjmpPath = when {
            descriptor.os == "linux" && descriptor.architecture == "x86_64" ->
                resolution.layout.runtimeSource.resolve("setjmp-x86_64.S")
            descriptor.os == "linux" && descriptor.architecture == "aarch64" ->
                resolution.layout.runtimeSource.resolve("setjmp-aarch64.S")
            descriptor.os == "windows" && descriptor.architecture == "x86_64" ->
                resolution.layout.runtimeSource.resolve("setjmp-windows-x86_64.S")
            else -> null
        }
        val supportsSetjmp = setjmpPath != null
        val setjmpPresent = setjmpPath?.let(Files::isRegularFile) == true
        cases += ConformanceCase(
            "libc.setjmp-context",
            ConformanceArea.LIBC,
            descriptor.targetTriple,
            "c17",
            when {
                !supportsSetjmp -> "unsupported"
                setjmpPresent -> "pass"
                else -> "fail"
            },
            when {
                supportsSetjmp && setjmpPresent -> "${descriptor.os} ${descriptor.architecture} context adapter is present"
                supportsSetjmp -> "${descriptor.os} ${descriptor.architecture} context adapter is missing: $setjmpPath"
                else -> "target-specific setjmp/longjmp adapter is not implemented for this target"
            }
        )

        C17ConformanceFixtures.all.map(C17Fixture::sourceName).forEach { fixture ->
            val path = resolution.layout.root.resolve("conformance/c17").resolve(fixture)
            cases += ConformanceCase(
                "fixture.source.${fixture.removeSuffix(".c")}",
                ConformanceArea.LIBC,
                descriptor.targetTriple,
                "c17",
                if (Files.isRegularFile(path)) "pass" else "fail",
                if (Files.isRegularFile(path)) path.toString() else "independent C fixture is missing: $path"
            )
        }

        return ConformanceReport(cases)
    }
}

data class C17Fixture(
    val id: String,
    val sourceName: String,
    val supported: (TargetAbiDescriptor) -> Boolean,
    val stdin: ByteArray = byteArrayOf(),
    val expectedStdout: String? = null,
    val expectedStderr: String? = null
)

object C17ConformanceFixtures {
    val all: List<C17Fixture> = listOf(
        C17Fixture(
            "basic",
            "c17-basic.c",
            { descriptor ->
                (descriptor.os == "linux" && descriptor.architecture in setOf("x86_64", "aarch64")) ||
                    descriptor.targetTriple == "windows-x86_64"
            }
        ),
        C17Fixture(
            "context",
            "c17-context.c",
            { descriptor ->
                (descriptor.os == "linux" && descriptor.architecture in setOf("x86_64", "aarch64")) ||
                    descriptor.targetTriple == "windows-x86_64"
            }
        ),
        C17Fixture(
            "stdio",
            "c17-stdio.c",
            { descriptor ->
                (descriptor.os == "linux" && descriptor.architecture in setOf("x86_64", "aarch64")) ||
                    descriptor.targetTriple == "windows-x86_64"
            },
            byteArrayOf('A'.code.toByte(), 0xff.toByte()),
            "P:ok\nV:8\nline\n>",
            "F:9\nW:ok\n!"
        ),
        C17Fixture(
            "complex-types",
            "c17-complex-types.c",
            { descriptor -> "c17_complex" in descriptor.features }
        ),
        C17Fixture(
            "tgmath",
            "c17-tgmath.c",
            { descriptor -> "c17_complex" in descriptor.features }
        )
    )
}

internal object C17TargetRunner {
    fun commandPrefix(
        targetTriple: String,
        hostOs: String = System.getProperty("os.name"),
        hostArch: String = System.getProperty("os.arch"),
        searchPath: List<Path> = System.getenv("PATH").orEmpty()
            .split(File.pathSeparator)
            .filter(String::isNotBlank)
            .map(Path::of)
    ): List<String>? {
        val targetParts = targetTriple.split('-', limit = 2)
        if (targetParts.size != 2) return null
        val targetOs = targetParts[0]
        val targetArch = targetParts[1]
        val normalizedHostOs = when {
            hostOs.contains("linux", ignoreCase = true) -> "linux"
            hostOs.contains("windows", ignoreCase = true) -> "windows"
            hostOs.contains("mac", ignoreCase = true) || hostOs.contains("darwin", ignoreCase = true) -> "darwin"
            else -> "unknown"
        }
        val normalizedHostArch = when (hostArch.lowercase()) {
            "amd64", "x86_64" -> "x86_64"
            "arm64", "aarch64" -> "aarch64"
            else -> hostArch.lowercase()
        }
        if (targetOs == normalizedHostOs && targetArch == normalizedHostArch) return emptyList()

        val emulators = when (targetTriple) {
            "linux-aarch64" -> listOf("qemu-aarch64", "qemu-aarch64-static")
            "linux-x86_64" -> listOf("qemu-x86_64", "qemu-x86_64-static")
            else -> return null
        }
        val executable = searchPath.asSequence()
            .flatMap { directory -> emulators.asSequence().map(directory::resolve) }
            .firstOrNull(Files::isExecutable)
            ?: return null
        return listOf(executable.toAbsolutePath().normalize().toString())
    }
}

object C17ConformanceRunner {
    fun run(resolution: SdkResolution, target: TargetInfo): ConformanceReport {
        val initial = C17ConformanceAudit.inspect(resolution, target)
        val descriptorResult = resolution.targetDescriptor?.let { TargetDescriptorResult(it, emptyList()) }
            ?: TargetRegistry.load(resolution.layout.abiDescriptor)
        val descriptor = descriptorResult.descriptor ?: return initial
        val cases = initial.cases.toMutableList()
        val plan = RuntimeLinker.plan(resolution, target).plan ?: return initial
        val fixtureRoot = resolution.layout.root.resolve("conformance/c17")
        val runnerPrefix = C17TargetRunner.commandPrefix(target.targetTriple)
        val temporaryRoot = runCatching { Files.createTempDirectory("cplus-c17-conformance") }.getOrNull()
            ?: return initial.withCase(
                ConformanceCase(
                    "fixture.execution.environment",
                    ConformanceArea.LIBC,
                    descriptor.targetTriple,
                    "c17",
                    "fail",
                    "unable to create a temporary fixture directory"
                )
            )

        try {
            C17ConformanceFixtures.all.forEach { fixture ->
                if (!fixture.supported(descriptor)) {
                    cases += ConformanceCase(
                        "fixture.execution.${fixture.id}",
                        ConformanceArea.LIBC,
                        descriptor.targetTriple,
                        "c17",
                        "unsupported",
                        "fixture is outside the claimed target execution matrix"
                    )
                    return@forEach
                }
                if (runnerPrefix == null) {
                    cases += ConformanceCase(
                        "fixture.execution.${fixture.id}",
                        ConformanceArea.LIBC,
                        descriptor.targetTriple,
                        "c17",
                        "unsupported",
                        "no executable runner is available for target '${target.targetTriple}'"
                    )
                    if (fixture.expectedStdout != null || fixture.expectedStderr != null) {
                        cases += ConformanceCase(
                            "fixture.streams.${fixture.id}",
                            ConformanceArea.LIBC,
                            descriptor.targetTriple,
                            "c17",
                            "unsupported",
                            "stream behavior requires executing the target fixture"
                        )
                    }
                    return@forEach
                }
                val source = fixtureRoot.resolve(fixture.sourceName)
                val executable = temporaryRoot.resolve(
                    if (descriptor.os == "windows") "${fixture.id}.exe" else fixture.id
                )
                if (!Files.isRegularFile(source)) {
                    cases += ConformanceCase(
                        "fixture.execution.${fixture.id}",
                        ConformanceArea.LIBC,
                        descriptor.targetTriple,
                        "c17",
                        "fail",
                        "fixture source is missing: $source"
                    )
                    return@forEach
                }
                val link = LinkDriver.link(
                    LinkRequest(source, executable, target, resolution),
                    plan
                )
                if (!link.isSuccessful) {
                    cases += ConformanceCase(
                        "fixture.execution.${fixture.id}",
                        ConformanceArea.LIBC,
                        descriptor.targetTriple,
                        "c17",
                        "fail",
                        "independent C fixture did not compile: ${link.output.trim()}"
                    )
                    return@forEach
                }
                val process = runCatching { ProcessBuilder(runnerPrefix + executable.toString()).start() }
                    .getOrElse { error ->
                        cases += ConformanceCase(
                            "fixture.execution.${fixture.id}",
                            ConformanceArea.LIBC,
                            descriptor.targetTriple,
                            "c17",
                            "fail",
                            "unable to start fixture: ${error.message ?: error::class.simpleName}"
                        )
                        return@forEach
                    }
                process.outputStream.use { it.write(fixture.stdin) }
                val stdout = process.inputStream.readBytes().toString(Charsets.UTF_8)
                val stderr = process.errorStream.readBytes().toString(Charsets.UTF_8)
                val output = (stdout + stderr).trim()
                val exitCode = process.waitFor()
                cases += ConformanceCase(
                    "fixture.execution.${fixture.id}",
                    ConformanceArea.LIBC,
                    descriptor.targetTriple,
                    "c17",
                    if (exitCode == 0) "pass" else "fail",
                    if (exitCode == 0) "independent C fixture executed successfully" else "fixture exited $exitCode${output.takeIf(String::isNotBlank)?.let { ": $it" }.orEmpty()}"
                )
                if (fixture.expectedStdout != null || fixture.expectedStderr != null) {
                    val stdoutMatches = fixture.expectedStdout == null || stdout == fixture.expectedStdout
                    val stderrMatches = fixture.expectedStderr == null || stderr == fixture.expectedStderr
                    cases += ConformanceCase(
                        "fixture.streams.${fixture.id}",
                        ConformanceArea.LIBC,
                        descriptor.targetTriple,
                        "c17",
                        if (stdoutMatches && stderrMatches) "pass" else "fail",
                        if (stdoutMatches && stderrMatches) "stdin/stdout/stderr match the fixture contract"
                        else "standard-channel mismatch: stdout=${stdout.replace("\n", "\\n")}; stderr=${stderr.replace("\n", "\\n")}"
                    )
                }
                if (exitCode == 0) {
                    val audit = RuntimeDependencyAuditor.inspect(executable, descriptor, target.buildProfile)
                    cases += ConformanceCase(
                        "fixture.dependencies.${fixture.id}",
                        ConformanceArea.RUNTIME,
                        descriptor.targetTriple,
                        "c17",
                        if (audit.isSuccessful) "pass" else "fail",
                        audit.diagnostics.joinToString("; ").ifBlank { "no undeclared host or compiler-runtime dependency observed" }
                    )
                }
            }
        } finally {
            Files.walk(temporaryRoot).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Path::deleteIfExists)
            }
        }
        return ConformanceReport(cases)
    }

    private fun ConformanceReport.withCase(case: ConformanceCase): ConformanceReport =
        ConformanceReport(cases + case)
}
