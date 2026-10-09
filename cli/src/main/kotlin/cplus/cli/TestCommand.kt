package cplus.cli

import cplus.compiler.BuildProfile
import cplus.compiler.LibcProfile
import cplus.compiler.RuntimeProfile
import cplus.compiler.TargetInfo
import cplus.compiler.defaultHostTargetTriple
import cplus.backend.CTestFixtureMetadata
import java.nio.file.Files
import java.nio.file.Path

internal data class TestCommandArguments(
    val roots: List<Path>,
    val timeoutSeconds: Long = 30,
    val sdkManifest: Path,
    val externalSysroot: Path?,
    val target: TargetInfo,
    val cCompiler: String?,
    val cSources: List<Path>,
    val libraries: List<String>,
    val includeDirectories: List<Path>,
    val projectManifest: Path?,
    val workspaceManifest: Path?
) {
    companion object {
        fun parse(
            arguments: List<String>,
            workingDirectory: Path = Path.of("").toAbsolutePath().normalize(),
            defaultSdkManifest: Path = cplus.compiler.SdkManifestLocator.defaultManifestPath(),
            defaultTarget: String = defaultHostTargetTriple()
        ): TestCommandArguments? {
            fun fail(message: String): Nothing? {
                System.err.println(message)
                return null
            }
            fun path(value: String) = (if (Path.of(value).isAbsolute) Path.of(value) else workingDirectory.resolve(value))
                .toAbsolutePath().normalize()

            val roots = mutableListOf<Path>()
            val cSources = mutableListOf<Path>()
            val libraries = mutableListOf<String>()
            val includes = mutableListOf<Path>()
            var sdk = path(defaultSdkManifest.toString())
            var sysroot: Path? = null
            var runtime = RuntimeProfile.CPLUS
            var libc = LibcProfile.C17
            var target = defaultTarget.lowercase()
            var compiler: String? = null
            var project: Path? = null
            var workspace: Path? = null
            var timeout = 30L
            var optionsEnded = false
            var index = 0
            while (index < arguments.size) {
                val argument = arguments[index]
                if (!optionsEnded && argument == "--") {
                    optionsEnded = true
                    index++
                    continue
                }
                if (!optionsEnded && argument.startsWith("-")) {
                    if (argument in setOf("--output", "-o", "--header", "--map")) {
                        return fail("$argument is not supported by 'test'; test products are temporary")
                    }
                    if (argument in setOf("--sdk", "--sdk-manifest", "--sysroot", "--target", "--runtime", "--libc", "--c-compiler", "--include-dir", "-I", "--c-source", "--c-file", "--library", "-l", "--project", "--workspace", "--timeout")) {
                        val value = arguments.getOrNull(index + 1)?.takeIf(String::isNotBlank)
                            ?: return fail("missing value after $argument")
                        when (argument) {
                            "--sdk", "--sdk-manifest" -> sdk = path(value)
                            "--sysroot" -> sysroot = path(value)
                            "--target" -> target = value.lowercase()
                            "--runtime" -> runtime = parseEnum<RuntimeProfile>(value, argument) ?: return null
                            "--libc" -> libc = parseEnum<LibcProfile>(value, argument) ?: return null
                            "--c-compiler" -> compiler = value
                            "--include-dir", "-I" -> includes.add(path(value))
                            "--c-source", "--c-file" -> cSources.add(path(value))
                            "--library", "-l" -> libraries += value
                            "--project" -> project = path(value)
                            "--workspace" -> workspace = path(value)
                            "--timeout" -> {
                                timeout = value.toLongOrNull()?.takeIf { it > 0 }
                                    ?: return fail("--timeout must be a positive integer in seconds")
                            }
                        }
                        index += 2
                        continue
                    }
                    if (argument.startsWith("-I") && argument.length > 2) {
                        includes.add(path(argument.drop(2)))
                        index++
                        continue
                    }
                    if (argument.startsWith("-l") && argument.length > 2) {
                        libraries += argument.drop(2)
                        index++
                        continue
                    }
                    return fail("unsupported test option '$argument'")
                }
                roots.add(path(argument))
                index++
            }
            if (project != null && workspace != null) return fail("choose either --project or --workspace, not both")
            if (roots.isEmpty()) return fail("usage: cplus test <file.cp> [other.cp ...] [--timeout <seconds>] [shared build options]")
            val normalizedRoots = roots.distinct()
            val missing = normalizedRoots.firstOrNull { !Files.isRegularFile(it) }
            if (missing != null) return fail("test root does not exist or is not a regular file: $missing")
            listOfNotNull(project, workspace).firstOrNull { !Files.isRegularFile(it) }?.let {
                return fail("project/workspace manifest does not exist or is not a regular file: $it")
            }
            return TestCommandArguments(
                normalizedRoots,
                timeout,
                sdk,
                sysroot,
                TargetInfo(buildProfile = BuildProfile(runtime, libc), targetTriple = target),
                compiler,
                cSources.distinct(),
                libraries,
                includes.distinct(),
                project,
                workspace
            )
        }

        private inline fun <reified T : Enum<T>> parseEnum(value: String, option: String): T? =
            enumValues<T>().firstOrNull { enum -> enum.name.equals(value, ignoreCase = true) }
                ?: run {
                    System.err.println("invalid value '$value' for $option")
                    null
                }
    }
}

internal data class TestRootBuildResult(
    val root: Path,
    val executable: Path,
    val exitCode: Int,
    val fixtures: List<CTestFixtureMetadata>
)
