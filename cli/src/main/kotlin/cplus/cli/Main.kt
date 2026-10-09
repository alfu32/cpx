package cplus.cli

import cplus.compiler.CPlusCompiler
import cplus.compiler.CompileRequest
import cplus.compiler.CompilationMode
import cplus.compiler.BuildProfile
import cplus.compiler.C17ConformanceRunner
import cplus.compiler.LibcProfile
import cplus.compiler.RuntimeProfile
import cplus.compiler.RuntimeLinker
import cplus.compiler.RuntimeDependencyAuditor
import cplus.compiler.RuntimeHelperCatalogue
import cplus.compiler.SdkDoctor
import cplus.compiler.SdkManifestLoader
import cplus.compiler.SdkPackageIndex
import cplus.compiler.SdkResolver
import cplus.compiler.TargetRegistry
import cplus.compiler.IntrinsicRegistry
import cplus.compiler.LinkDriver
import cplus.compiler.LinkRequest
import cplus.compiler.ModuleSourceResolver
import cplus.compiler.SdkManifestLocator
import cplus.compiler.TargetInfo
import cplus.compiler.defaultHostTargetTriple
import cplus.core.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val exitCode = Cli().run(args.toList())
    if (exitCode != 0) exitProcess(exitCode)
}

internal class Cli {
    fun run(args: List<String>): Int {
        if (args.firstOrNull() in setOf("--version", "-V")) {
            printVersion()
            return 0
        }
        if (args.isEmpty() || args.first() in setOf("-h", "--help", "help")) {
            printUsage()
            return 0
        }

        val command = args.first()
        return when (command) {
            "version" -> if (args.size == 1) {
                printVersion()
                0
            } else {
                System.err.println("usage: cplus version")
                2
            }
            "transcode", "emit-c" -> transcode(args.drop(1))
            "new" -> newProject(args.drop(1))
            "check" -> check(args.drop(1))
            "ast" -> ast(args.drop(1))
            "expand" -> expand(args.drop(1))
            "build" -> build(args.drop(1))
            "run" -> runProgram(args.drop(1))
            "test" -> testCommand(args.drop(1))
            "sdk" -> sdk(args.drop(1))
            "target" -> target(args.drop(1))
            "abi" -> abi(args.drop(1))
            "runtime" -> runtime(args.drop(1))
            "libc" -> libc(args.drop(1))
            "audit" -> audit(args.drop(1))
            "lsp" -> lsp(args.drop(1))
            else -> {
                System.err.println("unknown command '$command'")
                printUsage(System.err)
                2
            }
        }
    }

    private fun transcode(arguments: List<String>): Int {
        val parsed = parseFileArguments(arguments) ?: return 2
        val compiler = CPlusCompiler()
        val result = compiler.compile(
            CompileRequest(
                parsed.sources,
                cSources = parsed.cSources,
                cLibraries = parsed.libraries,
                cIncludeDirectories = parsed.includeDirectories,
                sdkManifest = parsed.sdkManifest,
                externalSysroot = parsed.externalSysroot,
                target = parsed.target,
                cCompiler = parsed.cCompiler
            )
        )
        printDiagnostics(result.diagnostics, parsed.sources.first())
        if (!result.isSuccessful) return 1
        val generated = result.generatedUnits.singleOrNull()?.text ?: return 2
        if (parsed.output == null) {
            print(generated)
        } else {
            parsed.output.parent?.let(Files::createDirectories)
            parsed.output.writeText(generated)
        }
        parsed.headerOutput?.let { headerPath ->
            val header = result.generatedHeaders.singleOrNull()?.text ?: return 2
            headerPath.parent?.let { Files.createDirectories(it) }
            headerPath.writeText(header)
        }
        parsed.mapOutput?.let { mapPath ->
            val generatedUnit = result.generatedUnits.singleOrNull() ?: return 2
            mapPath.parent?.let(Files::createDirectories)
            mapPath.writeText(serializeSourceMap(generatedUnit, result.artifacts, parsed.sourceBase, compiler::sourcePathFor))
        }
        return 0
    }

    private fun newProject(arguments: List<String>): Int {
        if (arguments.size != 1 || arguments.single().startsWith("-")) {
            System.err.println("usage: cplus new <directory>")
            return 2
        }

        val projectDirectory = Path.of(arguments.single()).toAbsolutePath().normalize()
        val generatedFiles = linkedMapOf(
            Path.of("cplus.toml") to """
                [project]
                entry = "src/main.cp"
                source_roots = ["src"]
            """.trimIndent() + "\n",
            Path.of("src/main.cp") to """
                int main() {
                    return 0;
                }
            """.trimIndent() + "\n",
            Path.of("README.md") to """
                # C+ project

                Build and run this project with:

                ```sh
                cplus check --project cplus.toml
                cplus run --project cplus.toml
                ```
            """.trimIndent() + "\n"
        )

        if (Files.exists(projectDirectory) && !Files.isDirectory(projectDirectory)) {
            System.err.println("project path exists and is not a directory: $projectDirectory")
            return 1
        }
        val isNonEmpty = Files.isDirectory(projectDirectory) && Files.list(projectDirectory).use { entries ->
            entries.findAny().isPresent
        }
        if (isNonEmpty) {
            System.err.println("warning: project directory is not empty; leaving it unchanged: $projectDirectory")
            return 1
        }

        return try {
            Files.createDirectories(projectDirectory.resolve("src"))
            generatedFiles.forEach { (relativePath, contents) ->
                val output = projectDirectory.resolve(relativePath)
                Files.createDirectories(output.parent)
                Files.writeString(output, contents)
            }
            println("created C+ project at $projectDirectory")
            0
        } catch (error: Exception) {
            System.err.println("unable to create C+ project at '$projectDirectory': ${error.message}")
            1
        }
    }

    private fun check(arguments: List<String>): Int {
        val parsed = parseFileArguments(arguments) ?: return 2
        val result = CPlusCompiler().compile(
            CompileRequest(
                parsed.sources,
                cSources = parsed.cSources,
                cLibraries = parsed.libraries,
                cIncludeDirectories = parsed.includeDirectories,
                sdkManifest = parsed.sdkManifest,
                externalSysroot = parsed.externalSysroot,
                target = parsed.target,
                cCompiler = parsed.cCompiler
            )
        )
        printDiagnostics(result.diagnostics, parsed.sources.first())
        if (result.isSuccessful) println("OK: ${parsed.sources.joinToString(", ")}")
        return if (result.isSuccessful) 0 else 1
    }

    private fun ast(arguments: List<String>): Int {
        val parsed = parseFileArguments(arguments) ?: return 2
        val result = CPlusCompiler().compile(
            CompileRequest(
                parsed.sources,
                cSources = parsed.cSources,
                cLibraries = parsed.libraries,
                cIncludeDirectories = parsed.includeDirectories,
                sdkManifest = parsed.sdkManifest,
                externalSysroot = parsed.externalSysroot,
                target = parsed.target,
                cCompiler = parsed.cCompiler
            )
        )
        printDiagnostics(result.diagnostics, parsed.sources.first())
        val artifact = result.artifacts.singleOrNull() ?: return 1
        println(AstPrinter().print(AstBuilder().build(artifact.parsed.syntax)))
        return if (result.isSuccessful) 0 else 1
    }

    private fun expand(arguments: List<String>): Int {
        val parsed = parseFileArguments(arguments) ?: return 2
        val result = CPlusCompiler().compile(
            CompileRequest(
                parsed.sources,
                cSources = parsed.cSources,
                cLibraries = parsed.libraries,
                cIncludeDirectories = parsed.includeDirectories,
                sdkManifest = parsed.sdkManifest,
                externalSysroot = parsed.externalSysroot,
                target = parsed.target,
                cCompiler = parsed.cCompiler
            )
        )
        printDiagnostics(result.diagnostics, parsed.sources.first())
        val artifact = result.artifacts.singleOrNull() ?: return 1
        val expanded = artifact.expandedSyntax ?: return 1
        println(AstPrinter().print(AstBuilder().build(expanded)))
        return if (result.isSuccessful) 0 else 1
    }

    private fun build(arguments: List<String>): Int {
        val parsed = parseFileArguments(arguments) ?: return 2
        val executable = executablePath(
            parsed.output ?: parsed.sources.first().resolveSibling(parsed.sources.first().nameWithoutExtension),
            parsed.target
        )
        return buildExecutable(
            parsed.sources,
            parsed.cSources,
            executable,
            parsed.headerOutput,
            parsed.libraries,
            parsed.includeDirectories,
            parsed.sdkManifest,
            parsed.externalSysroot,
            parsed.target,
            parsed.cCompiler,
            parsed.mapOutput,
            parsed.sourceBase
        )
    }

    private fun runProgram(arguments: List<String>): Int {
        val parsed = parseFileArguments(arguments) ?: return 2
        val ownsTemporaryDirectory = parsed.output == null
        val temporaryDirectory = if (ownsTemporaryDirectory) {
            runCatching { Files.createTempDirectory("cplus-run") }.getOrElse {
                System.err.println("unable to create temporary run directory: ${it.message}")
                return 2
            }
        } else null
        val executable = executablePath(
            parsed.output ?: temporaryDirectory!!.resolve(parsed.sources.first().nameWithoutExtension),
            parsed.target
        )
        return try {
            val buildExitCode = buildExecutable(
                parsed.sources,
                parsed.cSources,
                executable,
                parsed.headerOutput,
                parsed.libraries,
                parsed.includeDirectories,
                parsed.sdkManifest,
                parsed.externalSysroot,
                parsed.target,
                parsed.cCompiler,
                parsed.mapOutput,
                parsed.sourceBase
            )
            if (buildExitCode != 0) return buildExitCode
            try {
                ProcessBuilder(executable.toString()).inheritIO().start().waitFor()
            } catch (error: java.io.IOException) {
                System.err.println("unable to start program '${executable.fileName}': ${error.message}")
                2
            }
        } finally {
            temporaryDirectory?.let(::deleteTemporaryProduct)
        }
    }

    private fun testCommand(arguments: List<String>): Int {
        val parsed = TestCommandArguments.parse(arguments) ?: return 2
        if (!isNativeRunnableTarget(parsed.target.targetTriple)) {
            System.err.println("test execution requires a native runnable target; '${parsed.target.targetTriple}' does not match '${defaultHostTargetTriple()}'")
            return 2
        }
        val temporaryDirectory = runCatching { Files.createTempDirectory("cplus-test") }.getOrElse {
            System.err.println("unable to create temporary test directory: ${it.message}")
            return 2
        }
        try {
            val builds = buildTestProducts(parsed, temporaryDirectory)
            var totalPassed = 0
            var totalFailed = 0
            var totalErrors = 0
            var cancelled = false
            builds.forEachIndexed { fileIndex, build ->
                if (cancelled) return@forEachIndexed
                val fixtureCount = if (build.exitCode == 0) build.fixtures.size else 0
                println("::: [${fileIndex + 1}/${builds.size}] ${build.root}")
                var filePassed = 0
                var fileFailed = 0
                var fileErrors = 0
                if (build.exitCode != 0) {
                    System.err.println("test product build failed for ${build.root} (exit ${build.exitCode})")
                    fileErrors++
                } else if (fixtureCount == 0) {
                    println("... NO TESTS")
                } else {
                    build.fixtures.forEachIndexed { fixtureIndex, fixture ->
                        if (cancelled) return@forEachIndexed
                        if (Thread.currentThread().isInterrupted) {
                            cancelled = true
                            return@forEachIndexed
                        }
                        println("... [${fixtureIndex + 1}/$fixtureCount] ${fixture.description}")
                        val result = TestProcessRunner().run(
                            build.executable,
                            fixture.identity,
                            parsed.timeoutSeconds,
                            temporaryDirectory
                        )
                        val protocol = result.protocol
                        filePassed += protocol?.passed ?: 0
                        fileFailed += protocol?.failed ?: 0
                        if (result.error != null) {
                            fileErrors++
                            System.err.println("fixture execution error: ${result.error}")
                            if (result.error == "fixture execution was interrupted") cancelled = true
                        }
                        println("... asserts passed ${protocol?.passed ?: 0} / failed ${protocol?.failed ?: 0} / total ${protocol?.total ?: 0}; errors ${if (result.error == null) 0 else 1}")
                    }
                }
                totalPassed += filePassed
                totalFailed += fileFailed
                totalErrors += fileErrors
                println("::: asserts passed $filePassed / failed $fileFailed / total ${filePassed + fileFailed}; errors $fileErrors")
            }
            println("::: final report")
            println("::: total passed $totalPassed / failed $totalFailed / total ${totalPassed + totalFailed}; errors $totalErrors")
            if (cancelled) return 130
            return if (totalFailed == 0 && totalErrors == 0) 0 else 1
        } finally {
            deleteTemporaryProduct(temporaryDirectory)
        }
    }

    private fun isNativeRunnableTarget(targetTriple: String): Boolean =
        targetTriple.equals(defaultHostTargetTriple(), ignoreCase = true)

    internal fun buildTestProducts(arguments: TestCommandArguments, temporaryDirectory: Path): List<TestRootBuildResult> =
        arguments.roots.mapIndexed { index, root ->
            val executable = executablePath(temporaryDirectory.resolve("root-$index").resolve("test-product"), arguments.target)
            var metadata: cplus.backend.CTestProductMetadata? = null
            val workspace = (arguments.projectManifest ?: arguments.workspaceManifest)?.let(::loadWorkspaceManifest)
            val manifestInvalid = (arguments.projectManifest != null || arguments.workspaceManifest != null) && workspace == null
            val sdkRoot = arguments.sdkManifest.toAbsolutePath().normalize().parent?.parent
            val sourceClosure = if (manifestInvalid) listOf(root) else discoverModuleSources(
                listOf(root), workspace?.sourceRoots.orEmpty(), sdkRoot
            ).map { it.toAbsolutePath().normalize() }.distinct()
            val exitCode = if (manifestInvalid) {
                1
            } else {
                buildExecutable(
                    sources = sourceClosure,
                    cSources = arguments.cSources,
                    executable = executable,
                    libraries = arguments.libraries,
                    includeDirectories = arguments.includeDirectories,
                    sdkManifest = arguments.sdkManifest,
                    externalSysroot = arguments.externalSysroot,
                    target = arguments.target,
                    cCompiler = arguments.cCompiler?.let { normalizeCompiler(it, Path.of("").toAbsolutePath().normalize()) },
                    sourceBase = root.toAbsolutePath().normalize().parent ?: Path.of(".").toAbsolutePath(),
                    mode = CompilationMode.TEST,
                    rootSources = listOf(root),
                    testProductSink = { metadata = it }
                )
            }
            TestRootBuildResult(root, executable, exitCode, metadata?.fixtures.orEmpty())
        }

    private fun executablePath(path: Path, target: TargetInfo): Path =
        if (target.targetTriple.substringBefore('-') == "windows" &&
            !path.fileName.toString().endsWith(".exe", ignoreCase = true)
        ) path.resolveSibling("${path.fileName}.exe") else path

    private fun deleteTemporaryProduct(directory: Path) {
        try {
            Files.walk(directory).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        } catch (error: java.io.IOException) {
            System.err.println("unable to clean temporary run directory: ${error.message}")
        }
    }

    private fun lsp(arguments: List<String>): Int {
        if (arguments.isNotEmpty()) {
            System.err.println("lsp accepts no positional arguments and communicates over stdin/stdout")
            return 2
        }
        return LspServer().run(System.`in`, System.out)
    }

    private fun sdk(arguments: List<String>): Int {
        return when (arguments.firstOrNull() ?: "doctor") {
            "doctor", "verify" -> {
                val options = arguments.drop(1)
                val manifest = optionValue(options, "--sdk")?.let(Path::of)
                    ?: SdkManifestLocator.defaultManifestPath()
                val target = TargetInfo(targetTriple = (optionValue(options, "--target") ?: defaultHostTargetTriple()).lowercase())
                val report = SdkDoctor.inspect(manifest, target)
                report.checks.forEach { println("ok: $it") }
                report.diagnostics.forEach { System.err.println("error [${it.code}]: ${it.message}") }
                if (report.isSuccessful) 0 else 1
            }
            "package" -> {
                val options = arguments.drop(1)
                val manifest = optionValue(options, "--sdk")?.let(Path::of)
                    ?: SdkManifestLocator.defaultManifestPath()
                val root = resolveSdkRoot(manifest) ?: return 1
                val output = positionalArguments(options, setOf("--sdk")).firstOrNull()?.let(Path::of)
                    ?: root.resolve("sdk-package.index")
                output.parent?.let(Files::createDirectories)
                output.writeText(SdkPackageIndex.serialize(SdkPackageIndex.build(root, setOf(output))))
                println("wrote ${output.toAbsolutePath()}")
                0
            }
            else -> {
                System.err.println("sdk expects doctor, verify, or package")
                2
            }
        }
    }

    private fun target(arguments: List<String>): Int {
        val manifest = optionValue(arguments, "--sdk")?.let(Path::of) ?: SdkManifestLocator.defaultManifestPath()
        val root = resolveSdkRoot(manifest) ?: return 1
        val name = positionalArguments(arguments, setOf("--sdk")).firstOrNull { it != "list" }
        val paths = if (name == null || name == "list") TargetRegistry.list(root.resolve("abi")) else listOf(root.resolve("abi/$name.toml"))
        paths.forEach { path ->
            val result = TargetRegistry.load(path)
            val descriptor = result.descriptor
            if (descriptor == null) result.diagnostics.forEach { System.err.println("error [${it.code}]: ${it.message}") }
            else println("${descriptor.targetTriple}: ${descriptor.os}/${descriptor.architecture} ${descriptor.objectFormat} ${descriptor.abi}")
        }
        return if (paths.all { TargetRegistry.load(it).isSuccessful }) 0 else 1
    }

    private fun abi(arguments: List<String>): Int {
        if (arguments.firstOrNull() !in setOf("verify", "show")) {
            System.err.println("abi expects verify or show")
            return 2
        }
        val manifest = optionValue(arguments, "--sdk")?.let(Path::of) ?: SdkManifestLocator.defaultManifestPath()
        val root = resolveSdkRoot(manifest) ?: return 1
        val targetName = optionValue(arguments, "--target")?.lowercase()
        val descriptors = TargetRegistry.list(root.resolve("abi"))
        val selected = if (targetName == null) descriptors else descriptors.filter { it.fileName.toString() == "$targetName.toml" }
        if (selected.isEmpty()) {
            System.err.println("no ABI descriptor found for target '${targetName ?: "<none>"}'")
            return 1
        }
        val failures = selected.map { TargetRegistry.load(it) }.filterNot { it.isSuccessful }
        failures.flatMap { it.diagnostics }.forEach { System.err.println("error [${it.code}]: ${it.message}") }
        if (failures.isEmpty()) println("verified ${selected.size} target ABI descriptor${if (selected.size == 1) "" else "s"}")
        return if (failures.isEmpty()) 0 else 1
    }

    private fun runtime(arguments: List<String>): Int {
        val options = if (arguments.firstOrNull() == "inspect") arguments.drop(1) else arguments
        val targetName = optionValue(options, "--target") ?: defaultHostTargetTriple()
        val runtimeName = optionValue(options, "--runtime") ?: "cplus"
        val libcName = optionValue(options, "--libc") ?: "c17"
        val manifestPath = optionValue(options, "--sdk")?.let(Path::of)
            ?: SdkManifestLocator.defaultManifestPath()
        val runtimeProfile = parseRuntime(runtimeName, "--runtime") ?: return 2
        val libcProfile = parseLibc(libcName, "--libc") ?: return 2
        val manifestResult = SdkManifestLoader.load(manifestPath)
        if (!manifestResult.isSuccessful) {
            manifestResult.diagnostics.forEach { System.err.println("error [${it.code}]: ${it.message}") }
            return 1
        }
        val target = TargetInfo(
            targetTriple = targetName.lowercase(),
            buildProfile = BuildProfile(runtimeProfile, libcProfile)
        )
        val resolution = SdkResolver.resolve(manifestResult.manifest!!, target)
        if (!resolution.isSuccessful) {
            resolution.diagnostics.forEach { System.err.println("error [${it.code}]: ${it.message}") }
            return 1
        }
        val plan = RuntimeLinker.plan(resolution.resolution!!, target)
        if (!plan.isSuccessful) {
            plan.diagnostics.forEach { System.err.println("error [${it.code}]: ${it.message}") }
            return 1
        }
        val runtimePlan = plan.plan ?: return 1
        println("runtime: ${runtimePlan.profile.name.lowercase()}")
        runtimePlan.startupSources.forEach { println("startup: ${it.toAbsolutePath().normalize()}") }
        runtimePlan.runtimeSources.forEach { println("source: ${it.toAbsolutePath().normalize()}") }
        runtimePlan.compilerFlags.forEach { println("compiler-flag: $it") }
        runtimePlan.linkerFlags.forEach { println("linker-flag: $it") }
        return 0
    }

    private fun optionValue(arguments: List<String>, option: String): String? =
        arguments.windowed(2).firstOrNull { it[0] == option }?.get(1)

    private fun resolveSdkRoot(manifestPath: Path): Path? {
        val loaded = SdkManifestLoader.load(manifestPath)
        if (!loaded.isSuccessful) {
            loaded.diagnostics.forEach { System.err.println("error [${it.code}]: ${it.message}") }
            return null
        }
        val manifest = loaded.manifest ?: return null
        return manifest.path.parent?.parent ?: run {
            System.err.println("SDK manifest path has no SDK root: ${manifest.path}")
            null
        }
    }

    private fun positionalArguments(arguments: List<String>, valueOptions: Set<String>): List<String> = buildList {
        var index = 0
        while (index < arguments.size) {
            val argument = arguments[index]
            if (argument in valueOptions) index += 2
            else if (argument.startsWith("--")) index++
            else {
                add(argument)
                index++
            }
        }
    }

    private fun libc(arguments: List<String>): Int {
        if (arguments.firstOrNull() == "test") return libcTest(arguments.drop(1))
        val manifest = optionValue(arguments, "--sdk")?.let(Path::of) ?: SdkManifestLocator.defaultManifestPath()
        val root = resolveSdkRoot(manifest) ?: return 1
        val include = root.resolve("libc/include")
        val files = if (Files.isDirectory(include)) Files.list(include).use { it.filter(Files::isRegularFile).sorted().toList() } else emptyList()
        if (files.isEmpty()) {
            System.err.println("SDK libc include directory is missing or empty: $include")
            return 1
        }
        println("C17 headers: ${files.size}")
        files.forEach { println(it.fileName) }
        return if (files.isNotEmpty()) 0 else 1
    }

    private fun libcTest(arguments: List<String>): Int {
        val targetName = optionValue(arguments, "--target")?.lowercase()
            ?: defaultHostTargetTriple()
        val target = TargetInfo(targetTriple = targetName)
        val manifestPath = optionValue(arguments, "--sdk")?.let(Path::of)
            ?: SdkManifestLocator.defaultManifestPath()
        val manifestResult = SdkManifestLoader.load(manifestPath)
        if (!manifestResult.isSuccessful) {
            manifestResult.diagnostics.forEach { System.err.println("error [${it.code}]: ${it.message}") }
            return 1
        }
        val resolutionResult = SdkResolver.resolve(manifestResult.manifest!!, target)
        if (!resolutionResult.isSuccessful) {
            resolutionResult.diagnostics.forEach { System.err.println("error [${it.code}]: ${it.message}") }
            return 1
        }
        val report = C17ConformanceRunner.run(resolutionResult.resolution!!, target)
        report.cases.forEach { check ->
            println("[${check.status.uppercase()}] ${check.id}: ${check.notes}")
        }
        println(
            "C17 conformance: pass=${report.passed.size} fail=${report.failed.size} " +
                "unsupported=${report.unsupported.size} planned=${report.planned.size}"
        )
        return if (report.isComplete) 0 else 1
    }

    private fun audit(arguments: List<String>): Int {
        val binary = positionalArguments(arguments, setOf("--target", "--sdk", "--runtime", "--libc"))
            .firstOrNull { it != "inspect" }?.let(Path::of)?.toAbsolutePath()?.normalize()
        if (binary == null) {
            System.err.println("audit requires a binary path")
            return 2
        }
        val targetName = (optionValue(arguments, "--target") ?: defaultHostTargetTriple()).lowercase()
        val runtime = parseRuntime(optionValue(arguments, "--runtime") ?: "cplus", "--runtime") ?: return 2
        val libc = parseLibc(optionValue(arguments, "--libc") ?: "c17", "--libc") ?: return 2
        val manifest = optionValue(arguments, "--sdk")?.let(Path::of) ?: SdkManifestLocator.defaultManifestPath()
        val manifestResult = SdkManifestLoader.load(manifest)
        if (!manifestResult.isSuccessful) {
            manifestResult.diagnostics.forEach { System.err.println("error [${it.code}]: ${it.message}") }
            return 1
        }
        val loadedManifest = manifestResult.manifest ?: return 1
        val target = TargetInfo(targetTriple = targetName, buildProfile = BuildProfile(runtime, libc))
        val resolution = SdkResolver.resolve(loadedManifest, target)
        if (!resolution.isSuccessful) {
            resolution.diagnostics.forEach { System.err.println("error [${it.code}]: ${it.message}") }
            return 1
        }
        val profileDiagnostics = cplus.compiler.BuildProfileValidator.validate(target.buildProfile, loadedManifest)
        if (profileDiagnostics.isNotEmpty()) {
            profileDiagnostics.forEach { System.err.println("error [${it.code}]: ${it.message}") }
            return 1
        }
        val descriptor = TargetRegistry.load(resolution.resolution!!.layout.abiDescriptor).descriptor ?: return 1
        val report = RuntimeDependencyAuditor.inspect(binary, descriptor, target.buildProfile)
        println("observed: ${report.observed.sorted().joinToString(", ")}")
        report.diagnostics.forEach { System.err.println("error: $it") }
        return if (report.isSuccessful) 0 else 1
    }

    private fun buildExecutable(
        sources: List<Path>,
        cSources: List<Path>,
        executable: Path,
        headerOutput: Path? = null,
        libraries: List<String> = emptyList(),
        includeDirectories: List<Path> = emptyList(),
        sdkManifest: Path = SdkManifestLocator.defaultManifestPath(),
        externalSysroot: Path? = null,
        target: TargetInfo = TargetInfo(),
        cCompiler: String? = null,
        mapOutput: Path? = null,
        sourceBase: Path = Path.of("").toAbsolutePath().normalize(),
        mode: CompilationMode = CompilationMode.NORMAL,
        rootSources: List<Path> = sources,
        testProductSink: ((cplus.backend.CTestProductMetadata?) -> Unit)? = null
    ): Int {
        val compiler = CPlusCompiler()
        val result = compiler.compile(
            CompileRequest(
                sources,
                cSources = cSources,
                cLibraries = libraries,
                cIncludeDirectories = includeDirectories,
                sdkManifest = sdkManifest,
                externalSysroot = externalSysroot,
                target = target,
                cCompiler = cCompiler,
                mode = mode,
                rootSources = rootSources
            )
        )
        printDiagnostics(result.diagnostics, sources.first())
        if (!result.isSuccessful) return 1
        testProductSink?.invoke(result.generatedUnits.singleOrNull()?.let { result.artifacts.singleOrNull()?.lowered?.unit?.testProduct })
        val generated = result.generatedUnits.singleOrNull()?.text ?: return 2
        val sdkResolution = result.sdkResolution ?: return 2
        val runtimeHelpers = result.artifacts.flatMap { it.lowered?.unit?.runtimeDependencies.orEmpty() }.toSet()
        val runtimePlan = RuntimeLinker.plan(sdkResolution, target, runtimeHelpers)
        printDiagnostics(runtimePlan.diagnostics, sources.first())
        if (!runtimePlan.isSuccessful) return 1
        val runtime = runtimePlan.plan!!
        val runtimeDiagnostics = RuntimeHelperCatalogue.validate(runtimeHelpers, runtime)
        printDiagnostics(runtimeDiagnostics, sources.first())
        if (runtimeDiagnostics.any { it.severity == DiagnosticSeverity.ERROR }) return 1
        val executableName = executable.fileName.toString()
        val sourceStem = if (executableName.endsWith(".exe", ignoreCase = true)) executableName.dropLast(4) else executableName
        val cFile = executable.resolveSibling("$sourceStem.c")
        cFile.parent?.let { Files.createDirectories(it) }
        executable.parent?.let { Files.createDirectories(it) }
        cFile.writeText(generated)
        mapOutput?.let { mapPath ->
            val generatedUnit = result.generatedUnits.singleOrNull() ?: return 2
            mapPath.parent?.let(Files::createDirectories)
            mapPath.writeText(serializeSourceMap(generatedUnit, result.artifacts, sourceBase, compiler::sourcePathFor))
        }
        headerOutput?.let { headerPath ->
            val header = result.generatedHeaders.singleOrNull()?.text ?: return 2
            headerPath.parent?.let { Files.createDirectories(it) }
            headerPath.writeText(header)
        }
        val linkResult = try {
            LinkDriver.link(
                LinkRequest(
                    generatedSource = cFile,
                    output = executable,
                    target = target,
                    sdk = sdkResolution,
                    includeDirectories = includeDirectories,
                    sourceDependencies = result.cSourceDependencies.map { it.path },
                    libraries = result.cLinkDependencies,
                    cCompiler = cCompiler
                ),
                runtime
            )
        } catch (error: java.io.IOException) {
            System.err.println("unable to start target C compiler: ${error.message}")
            return 2
        }
        val output = linkResult.output
        val exitCode = linkResult.exitCode
        if (output.isNotBlank()) {
            val remapped = compiler.remapCCompilerDiagnostics(result, cFile, output)
            if (remapped.isEmpty()) {
                print(output)
            } else {
                printCCompilerDiagnostics(remapped)
            }
        }
        if (exitCode != 0) {
            System.err.println("target C link command: ${linkResult.command.joinToString(" ")}")
        }
        if (exitCode == 0) println("built ${executable.toAbsolutePath()}")
        return exitCode
    }

    private fun printCCompilerDiagnostics(diagnostics: List<cplus.compiler.RemappedCCompilerDiagnostic>) {
        diagnostics.forEach { diagnostic ->
            val source = diagnostic.source
            val range = diagnostic.sourceRange
            val location = if (source != null && range != null) {
                val position = LineIndex.from(source.text).positionAt(range.startOffset)
                "${source.path}:${position.line}:${position.column}"
            } else {
                "${diagnostic.generated.path}:${diagnostic.generated.line}:${diagnostic.generated.column}"
            }
            val generated = " (generated ${diagnostic.generated.path}:${diagnostic.generated.line}:${diagnostic.generated.column})"
            System.err.println("$location: ${diagnostic.severity.name.lowercase()} [${if (diagnostic.origin == null) "CCOMP002" else "CCOMP001"}]: ${diagnostic.message}$generated")
        }
    }

    private fun serializeSourceMap(
        unit: cplus.backend.GeneratedCUnit,
        artifacts: List<cplus.compiler.CompilationArtifacts>,
        baseDirectory: Path,
        resolveSourcePath: (SourceFileId) -> Path?
    ): String = buildString {
        val sourcePaths = artifacts.associate { it.source.id to it.source.path.toAbsolutePath().normalize() }
        unit.sourceMap.forEach { mapping ->
            val range = mapping.origin.primaryRange ?: return@forEach
            val sourcePath = (resolveSourcePath(range.file)?.toAbsolutePath()?.normalize() ?: sourcePaths[range.file])?.let { path ->
                runCatching { baseDirectory.relativize(path).toString() }.getOrDefault(path.toString())
                    .replace('\\', '/')
            } ?: "source-${range.file.value}"
            appendLine(
                "${mapping.generatedLine}:${mapping.generatedStartOffset}-${mapping.generatedEndOffset}" +
                    " -> $sourcePath:${range.startOffset}-${range.endOffset}"
            )
        }
    }

    private fun parseFileArguments(arguments: List<String>): FileArguments? {
        var source: Path? = null
        val sources = mutableListOf<Path>()
        var projectManifest: Path? = null
        var workspaceManifest: Path? = null
        val cSources = mutableListOf<Path>()
        val libraries = mutableListOf<String>()
        val includeDirectories = mutableListOf<Path>()
        var sdkManifest: Path? = null
        var externalSysroot: Path? = null
        var runtime: RuntimeProfile? = null
        var libc: LibcProfile? = null
        var targetTriple = defaultHostTargetTriple()
        var cCompiler: String? = null
        var output: Path? = null
        var headerOutput: Path? = null
        var mapOutput: Path? = null
        var index = 0
        while (index < arguments.size) {
            when (val argument = arguments[index]) {
                "-o", "--output" -> {
                    val value = arguments.getOrNull(index + 1)
                    if (value == null) {
                        System.err.println("missing output path after $argument")
                        return null
                    }
                    output = Path.of(value)
                    index += 2
                }
                "--header" -> {
                    val value = arguments.getOrNull(index + 1)
                    if (value == null) {
                        System.err.println("missing header output path after $argument")
                        return null
                    }
                    headerOutput = Path.of(value)
                    index += 2
                }
                "--map" -> {
                    val value = arguments.getOrNull(index + 1)
                    if (value == null) {
                        System.err.println("missing source-map path after $argument")
                        return null
                    }
                    mapOutput = Path.of(value)
                    index += 2
                }
                "--c-source", "--c-file" -> {
                    val value = arguments.getOrNull(index + 1)
                    if (value == null) {
                        System.err.println("missing C source path after $argument")
                        return null
                    }
                    cSources.add(Path.of(value))
                    index += 2
                }
                "--library", "-l" -> {
                    val value = arguments.getOrNull(index + 1)
                    if (value == null) {
                        System.err.println("missing library name or path after $argument")
                        return null
                    }
                    libraries += value
                    index += 2
                }
                "--include-dir", "-I" -> {
                    val value = arguments.getOrNull(index + 1)
                    if (value == null) {
                        System.err.println("missing include directory after $argument")
                        return null
                    }
                    includeDirectories.add(Path.of(value))
                    index += 2
                }
                "--sdk", "--sdk-manifest" -> {
                    val value = arguments.getOrNull(index + 1)
                    if (value == null) {
                        System.err.println("missing SDK manifest path after $argument")
                        return null
                    }
                    sdkManifest = Path.of(value)
                    index += 2
                }
                "--project", "--workspace" -> {
                    val value = arguments.getOrNull(index + 1)
                    if (value == null) {
                        System.err.println("missing manifest path after $argument")
                        return null
                    }
                    if (argument == "--project") projectManifest = Path.of(value) else workspaceManifest = Path.of(value)
                    index += 2
                }
                "--runtime" -> {
                    val value = arguments.getOrNull(index + 1)
                    runtime = parseRuntime(value, argument) ?: return null
                    index += 2
                }
                "--sysroot" -> {
                    val value = arguments.getOrNull(index + 1)
                    if (value == null) {
                        System.err.println("missing sysroot path after $argument")
                        return null
                    }
                    externalSysroot = Path.of(value)
                    index += 2
                }
                "--libc" -> {
                    val value = arguments.getOrNull(index + 1)
                    libc = parseLibc(value, argument) ?: return null
                    index += 2
                }
                "--target" -> {
                    val value = arguments.getOrNull(index + 1)
                    if (value.isNullOrBlank()) {
                        System.err.println("missing target triple after $argument")
                        return null
                    }
                    targetTriple = value.trim().lowercase()
                    index += 2
                }
                "--c-compiler" -> {
                    val value = arguments.getOrNull(index + 1)
                    if (value.isNullOrBlank()) {
                        System.err.println("missing compiler path after $argument")
                        return null
                    }
                    cCompiler = value
                    index += 2
                }
                else -> {
                    if (argument.startsWith("-l") && argument.length > 2) {
                        libraries += argument.removePrefix("-l")
                    } else if (argument.startsWith("-I") && argument.length > 2) {
                        includeDirectories.add(Path.of(argument.removePrefix("-I")))
                    } else if (source == null) source = Path.of(argument)
                    else sources.add(Path.of(argument))
                    index++
                }
            }
        }
        if (projectManifest != null && workspaceManifest != null) {
            System.err.println("choose either --project or --workspace, not both")
            return null
        }
        val manifestPath = projectManifest ?: workspaceManifest
        val manifest = if (manifestPath == null) null else loadWorkspaceManifest(manifestPath) ?: return null
        val manifestEntry = manifest?.let { it.baseDirectory.resolve(it.entry).normalize() }
        if (source == null && manifestEntry != null) source = manifestEntry
        if (source == null) {
            System.err.println("a source file is required")
            return null
        }
        val selectedRuntime = runtime ?: RuntimeProfile.CPLUS
        val selectedLibc = libc ?: if (selectedRuntime == RuntimeProfile.FREESTANDING) {
            LibcProfile.NONE
        } else {
            LibcProfile.C17
        }
        val workingDirectory = Path.of("").toAbsolutePath().normalize()
        val sdkManifestPath = normalizePath(sdkManifest ?: SdkManifestLocator.defaultManifestPath(), workingDirectory)
        val sdkRoot = sdkManifestPath.toAbsolutePath().normalize().parent?.parent
        val sourceBase = manifest?.baseDirectory ?: workingDirectory
        val discoveredSources = discoverModuleSources(
            listOf(source) + sources,
            manifest?.sourceRoots.orEmpty(),
            sdkRoot
        )
        return FileArguments(
            discoveredSources.map { normalizePath(it, workingDirectory) }.distinct(),
            cSources.map { normalizePath(it, workingDirectory) }.distinct(),
            output?.let { normalizePath(it, workingDirectory) },
            headerOutput?.let { normalizePath(it, workingDirectory) },
            mapOutput?.let { normalizePath(it, workingDirectory) },
            libraries.map { normalizeLibrary(it, workingDirectory) },
            includeDirectories.map { normalizePath(it, workingDirectory) }.distinct(),
            sdkManifestPath,
            externalSysroot?.let { normalizePath(it, workingDirectory) },
            TargetInfo(buildProfile = BuildProfile(selectedRuntime, selectedLibc), targetTriple = targetTriple),
            cCompiler?.let { normalizeCompiler(it, workingDirectory) },
            sourceBase
        )
    }

    private fun normalizePath(path: Path, base: Path): Path =
        (if (path.isAbsolute) path else base.resolve(path)).toAbsolutePath().normalize()

    private fun normalizeLibrary(value: String, base: Path): String {
        val pathLike = value.contains('/') || value.contains('\\') || value.startsWith(".") ||
            value.endsWith(".a", true) || value.endsWith(".so", true) ||
            Regex(".*\\.so(?:\\..*)?$", RegexOption.IGNORE_CASE).matches(value) ||
            value.endsWith(".dylib", true) || value.endsWith(".lib", true) || value.endsWith(".dll", true)
        return if (pathLike) normalizePath(Path.of(value), base).toString() else value
    }

    private fun normalizeCompiler(value: String, base: Path): String {
        val path = Path.of(value)
        val pathLike = path.isAbsolute || value.contains('/') || value.contains('\\')
        return if (pathLike) normalizePath(path, base).toString() else value
    }

    private fun parseRuntime(value: String?, option: String): RuntimeProfile? = when (value?.lowercase()) {
        "freestanding" -> RuntimeProfile.FREESTANDING
        "cplus" -> RuntimeProfile.CPLUS
        "system" -> RuntimeProfile.SYSTEM
        else -> {
            System.err.println("invalid runtime profile '${value ?: ""}' after $option; expected freestanding, cplus, or system")
            null
        }
    }

    private fun parseLibc(value: String?, option: String): LibcProfile? = when (value?.lowercase()) {
        "none" -> LibcProfile.NONE
        "c17" -> LibcProfile.C17
        "c23" -> LibcProfile.C23
        else -> {
            System.err.println("invalid libc profile '${value ?: ""}' after $option; expected none, c17, or c23")
            null
        }
    }

    private fun discoverModuleSources(requested: List<Path>, sourceRoots: List<Path>, sdkRoot: Path?): List<Path> {
        return ModuleSourceResolver(sourceRoots, sdkRoot).resolveClosure(requested).paths
    }

    private fun loadWorkspaceManifest(path: Path): WorkspaceManifest? {
        val normalized = path.toAbsolutePath().normalize()
        if (!Files.isRegularFile(normalized)) {
            System.err.println("project/workspace manifest does not exist: $normalized")
            return null
        }
        val contents = runCatching { Files.readString(normalized) }.getOrElse {
            System.err.println("unable to read project/workspace manifest '$normalized': ${it.message}")
            return null
        }
        val values = linkedMapOf<String, String>()
        var section: String? = null
        contents.lineSequence().forEachIndexed { index, rawLine ->
            val line = rawLine.substringBefore('#').trim()
            if (line.isEmpty()) return@forEachIndexed
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length - 1).trim()
                if (section !in setOf("project", "workspace")) {
                    System.err.println("unsupported manifest section on line ${index + 1}: $line")
                    return null
                }
                return@forEachIndexed
            }
            val assignment = MANIFEST_ASSIGNMENT.matchEntire(line)
            if (section == null || assignment == null) {
                System.err.println("invalid manifest entry on line ${index + 1}: $line")
                return null
            }
            val key = assignment.groupValues[1]
            if (key !in setOf("entry", "source_roots", "members")) {
                System.err.println("unsupported manifest key '$key' on line ${index + 1}")
                return null
            }
            if (key in values) {
                System.err.println("duplicate manifest key '$key' on line ${index + 1}")
                return null
            }
            values[key] = assignment.groupValues[2].trim()
        }
        val entry = parseManifestString(values["entry"] ?: "")?.takeIf(String::isNotBlank)
        if (entry == null) {
            System.err.println("manifest '$normalized' must declare entry = \"...\"")
            return null
        }
        val roots = mutableListOf<Path>()
        for (key in listOf("source_roots", "members")) {
            val raw = values[key] ?: continue
            val parsed = parseManifestStringArray(raw)
            if (parsed == null) {
                System.err.println("manifest key '$key' must be an array of quoted paths")
                return null
            }
            roots += parsed.map { normalized.parent.resolve(it).normalize() }
        }
        return WorkspaceManifest(normalized.parent, entry, roots.distinct())
    }

    private fun parseManifestString(value: String): String? =
        MANIFEST_STRING.matchEntire(value)?.groupValues?.get(1)

    private fun parseManifestStringArray(value: String): List<String>? {
        if (!value.startsWith("[") || !value.endsWith("]")) return null
        val body = value.substring(1, value.length - 1).trim()
        if (body.isEmpty()) return emptyList()
        val entries = MANIFEST_STRING.findAll(body).toList()
        val residue = MANIFEST_STRING.replace(body, "").replace(",", "").trim()
        return entries.map { it.groupValues[1] }.takeIf { residue.isEmpty() }
    }

    private fun printDiagnostics(diagnostics: List<Diagnostic>, source: Path) {
        if (diagnostics.isEmpty()) return
        val text = if (Files.exists(source)) normalizeSourceText(source.readText()) else ""
        val lineIndex = LineIndex.from(text)
        diagnostics.forEach { diagnostic ->
            val position = diagnostic.range?.let { lineIndex.positionAt(it.startOffset) }
            val location = position?.let { ":${it.line}:${it.column}" } ?: ""
            val code = diagnostic.code?.let { " [$it]" } ?: ""
            System.err.println("$source$location: ${diagnostic.severity.name.lowercase()}$code: ${diagnostic.message}")
        }
    }

    private fun printUsage(stream: java.io.PrintStream = System.out) {
        printVersion(stream)
        stream.println()
        stream.println("C+ CLI transcoder")
        stream.println("usage: cplus <command> <source.cp> [other.cp ...] [--project <cplus.toml> | --workspace <cplus.workspace.toml>] [--target <triple>] [--runtime <profile>] [--libc <profile>] [--c-compiler <path>] [--sdk <manifest>] [--sysroot <dir>] [--c-source <file>] [--library <name-or-path>] [--include-dir <dir>] [--output <file>] [--header <file>] [--map <file>]")
        stream.println()
        stream.println("commands:")
        stream.println("  version     print build and source-control version metadata")
        stream.println("  new         scaffold a project in a new directory")
        stream.println("  transcode   translate one C+ source file to C")
        stream.println("  emit-c      alias for transcode")
        stream.println("  check       parse and semantically validate one source or project")
        stream.println("  ast         print the normalized AST")
        stream.println("  expand      print the post-CPX normalized AST")
        stream.println("  build       transcode and compile one source file with the target C driver")
        stream.println("  run         build and execute one source file")
        stream.println("  test        build and run source test fixtures")
        stream.println("  sdk         verify, inspect, or index the source SDK")
        stream.println("  target      list or inspect target ABI descriptors")
        stream.println("  abi         verify target ABI descriptors")
        stream.println("  runtime     inspect runtime sources")
        stream.println("  libc        list delivered libc headers or run C17 conformance [test]")
        stream.println("  audit       inspect binary runtime dependencies [--target <triple>]")
        stream.println("  lsp         serve compiler diagnostics over stdio JSON-RPC")
        stream.println()
        stream.println("JVM options (place before -jar):")
        stream.println("  -Dcplus.sdk.manifest=<path>  select or override the SDK manifest")
    }

    private fun printVersion(stream: java.io.PrintStream = System.out) {
        val version = Version.current
        stream.println("C+ CLI version")
        stream.println("  git commit:       ${version.gitCommit}")
        stream.println("  short commit:     ${version.gitShortCommit}")
        stream.println("  commit date:      ${version.gitCommitDate}")
        stream.println("  build date:       ${version.buildDate}")
        stream.println("  codename:         ${version.codename}")
    }

    private data class FileArguments(
        val sources: List<Path>,
        val cSources: List<Path>,
        val output: Path?,
        val headerOutput: Path?,
        val mapOutput: Path?,
        val libraries: List<String>,
        val includeDirectories: List<Path>,
        val sdkManifest: Path,
        val externalSysroot: Path?,
        val target: TargetInfo,
        val cCompiler: String?,
        val sourceBase: Path
    )

    private data class WorkspaceManifest(
        val baseDirectory: Path,
        val entry: String,
        val sourceRoots: List<Path>
    )

    private companion object {
        val MANIFEST_ASSIGNMENT = Regex("""([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.+)""")
        val MANIFEST_STRING = Regex(""""([^"\\]*(?:\\.[^"\\]*)*)"""")
    }
}

internal class AstPrinter {
    fun print(program: AstProgram): String = buildString {
        appendLine("Program")
        program.declarations.forEach { declaration -> appendDeclaration(declaration, 1) }
    }

    private fun StringBuilder.appendDeclaration(declaration: AstDeclaration, depth: Int) {
        indent(depth)
        when (declaration) {
            is AstTestFixture -> {
                appendLine("TestFixture ${declaration.description}")
                appendStatement(declaration.body, depth + 1)
            }
            is AstPackage -> appendLine("Package ${declaration.name}")
            is AstAlias -> appendLine("Alias ${declaration.target.name} ${declaration.name}")
            is AstUnion -> {
                appendLine("Union ${declaration.name}")
                declaration.fields.forEach {
                    indent(depth + 1)
                    appendLine("Field ${it.type.name} ${it.name}")
                }
            }
            is AstEnum -> {
                appendLine("Enum ${declaration.name}")
                declaration.values.forEach {
                    indent(depth + 1)
                    appendLine("Value ${it.name}${it.value?.let { value -> " = $value" } ?: ""}")
                }
            }
            is AstStruct -> {
                appendLine("Struct ${declaration.name}")
                declaration.fields.forEach {
                    indent(depth + 1)
                    appendLine("Field ${it.type.name} ${it.name}")
                }
                declaration.methods.forEach {
                    indent(depth + 1)
                    appendLine("Method ${it.returnType.name} ${it.name}(${it.parameters.joinToString(", ") { parameter -> parameter.name }})")
                }
            }
            is AstTrait -> {
                appendLine("${if (declaration.isPublic) "Public " else ""}Trait ${declaration.targetName}")
                declaration.methods.forEach { method ->
                    indent(depth + 1)
                    append("Method ${method.returnType.name} ${method.name}(")
                    appendLine(method.parameters.joinToString(", ") { parameter ->
                        val parameterType = if (parameter.isReceiver) declaration.targetName else parameter.type.name
                        val pointerDepth = if (parameter.isReceiver && parameter.isPointerReceiver) 1 else parameter.type.pointerDepth
                        "${parameterType}${"*".repeat(pointerDepth)} ${parameter.name}"
                    } + ")")
                    method.body?.let { appendStatement(it, depth + 2) }
                }
            }
            is AstGlobalVariable -> {
                appendLine("Global ${declaration.type.name} ${declaration.name}")
            }
            is AstFunction -> {
                appendLine("Function ${declaration.returnType.name} ${declaration.name}")
                declaration.parameters.forEach {
                    indent(depth + 1)
                    appendLine("Parameter ${it.type.name} ${it.name}")
                }
                declaration.body?.let { appendStatement(it, depth + 1) }
            }
            is AstComptimeFunction -> appendLine("Comptime ${declaration.category} ${declaration.name}")
            is AstCpxInvocation -> appendLine("CpxInvocation ${declaration.name}(${declaration.arguments.joinToString(", ")})")
            is AstImport -> appendLine(
                "Import ${declaration.module} ${declaration.alias?.let { "as $it " } ?: ""}{${declaration.names.joinToString(", ") { name ->
                    declaration.nameAliases[name]?.let { "$name as $it" } ?: name
                }}}"
            )
        }
    }

    private fun StringBuilder.appendStatement(statement: AstStatement, depth: Int) {
        indent(depth)
        when (statement) {
            is AstBlock -> {
                appendLine("Block")
                statement.statements.forEach { appendStatement(it, depth + 1) }
            }
            is AstReturn -> appendLine("Return ${statement.expression?.let(::expression) ?: ""}")
            is AstExpressionStatement -> appendLine("Expression ${expression(statement.expression)}")
            is AstAssertion -> appendLine("Assertion ${statement.kind} ${statement.operandSourceText.joinToString(", ")}")
            is AstDefer -> appendLine("Defer ${expression(statement.expression)}")
            is AstIf -> {
                appendLine("If ${expression(statement.condition)}")
                appendStatement(statement.thenBranch, depth + 1)
                statement.elseBranch?.let {
                    indent(depth)
                    appendLine("Else")
                    appendStatement(it, depth + 1)
                }
            }
            is AstWhile -> {
                appendLine("While ${expression(statement.condition)}")
                appendStatement(statement.body, depth + 1)
            }
            is AstFor -> {
                appendLine("For ${statement.condition?.let(::expression) ?: ""}")
                statement.initializer?.let {
                    indent(depth + 1)
                    appendLine("Initializer")
                    appendStatement(it, depth + 2)
                }
                statement.increment?.let {
                    indent(depth + 1)
                    appendLine("Increment ${expression(it)}")
                }
                appendStatement(statement.body, depth + 1)
            }
            is AstBreak -> appendLine("Break")
            is AstContinue -> appendLine("Continue")
            is AstVariableDeclaration -> appendLine("Variable ${statement.type.name} ${statement.name}")
            is AstInnerFunction -> {
                appendLine("InnerFunction ${statement.function.returnType.name} ${statement.function.name}")
                statement.function.body?.let { appendStatement(it, depth + 1) }
            }
        }
    }

    private fun expression(expression: AstExpression): String = when (expression) {
        is AstIntegerLiteral -> expression.text
        is AstBooleanLiteral -> expression.text
        is AstFloatLiteral -> expression.text
        is AstStringLiteral -> expression.text
        is AstStringTemplate -> expression.parts.joinToString(separator = "", prefix = "\"", postfix = "\"") { part ->
            when (part) {
                is AstStringTextPart -> part.text
                is AstStringExpressionPart -> "${'$'}{${expression(part.expression)}}"
            }
        }
        is AstCharacterLiteral -> expression.text
        is AstIdentifier -> expression.name
        is AstUnary -> "${expression.operator}${expression(expression.operand)}"
        is AstBinary -> "(${expression(expression.left)} ${expression.operator} ${expression(expression.right)})"
        is AstConditional -> "(${expression(expression.condition)} ? ${expression(expression.thenBranch)} : ${expression(expression.elseBranch)})"
        is AstUpdate -> if (expression.prefix) {
            "${expression.operator}${expression(expression.operand)}"
        } else {
            "${expression(expression.operand)}${expression.operator}"
        }
        is AstSizeOf -> expression.targetType?.let { "sizeof(${it.name})" }
            ?: "sizeof(${expression(expression.operand!!)})"
        is AstAbiQuery -> when (expression.query) {
            "offsetof" -> "offsetof(${expression.targetType?.name}, ${expression.fieldName})"
            else -> "${expression.query}(${expression.targetType?.name ?: expression(expression.operand!!)})"
        }
        is AstCast -> "(${expression.target.name})${expression(expression.operand)}"
        is AstCall -> "${expression(expression.callee)}(${expression.arguments.joinToString(", ") { argument -> expression(argument) }})"
        is AstMemberAccess -> "${expression(expression.receiver)}.${expression.member}"
        is AstIndexAccess -> "${expression(expression.receiver)}[${expression(expression.index)}]"
        is AstParenthesized -> "(${expression(expression.expression)})"
        is AstErrorExpression -> "<error>"
    }

    private fun StringBuilder.indent(depth: Int) {
        repeat(depth) { append("  ") }
    }
}
