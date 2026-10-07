package cplus.cli

import cplus.compiler.CPlusCompiler
import cplus.compiler.CompileRequest
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
        if (args.isEmpty() || args.first() in setOf("-h", "--help", "help")) {
            printUsage()
            return 0
        }

        val command = args.first()
        return when (command) {
            "transcode", "emit-c" -> transcode(args.drop(1))
            "check" -> check(args.drop(1))
            "ast" -> ast(args.drop(1))
            "expand" -> expand(args.drop(1))
            "build" -> build(args.drop(1))
            "run" -> runProgram(args.drop(1))
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
        val result = CPlusCompiler().compile(
            CompileRequest(parsed.sources, cSources = parsed.cSources, cLibraries = parsed.libraries, cIncludeDirectories = parsed.includeDirectories)
        )
        printDiagnostics(result.diagnostics, parsed.sources.first())
        if (!result.isSuccessful) return 1
        val generated = result.generatedUnits.singleOrNull()?.text ?: return 2
        if (parsed.output == null) {
            print(generated)
        } else {
            parsed.output.writeText(generated)
        }
        parsed.headerOutput?.let { headerPath ->
            val header = result.generatedHeaders.singleOrNull()?.text ?: return 2
            headerPath.parent?.let { Files.createDirectories(it) }
            headerPath.writeText(header)
        }
        return 0
    }

    private fun check(arguments: List<String>): Int {
        val parsed = parseFileArguments(arguments) ?: return 2
        val result = CPlusCompiler().compile(
            CompileRequest(parsed.sources, cSources = parsed.cSources, cLibraries = parsed.libraries, cIncludeDirectories = parsed.includeDirectories)
        )
        printDiagnostics(result.diagnostics, parsed.sources.first())
        if (result.isSuccessful) println("OK: ${parsed.sources.joinToString(", ")}")
        return if (result.isSuccessful) 0 else 1
    }

    private fun ast(arguments: List<String>): Int {
        val parsed = parseFileArguments(arguments) ?: return 2
        val result = CPlusCompiler().compile(
            CompileRequest(parsed.sources, cSources = parsed.cSources, cLibraries = parsed.libraries, cIncludeDirectories = parsed.includeDirectories)
        )
        printDiagnostics(result.diagnostics, parsed.sources.first())
        val artifact = result.artifacts.singleOrNull() ?: return 1
        println(AstPrinter().print(artifact.ast))
        return if (result.isSuccessful) 0 else 1
    }

    private fun expand(arguments: List<String>): Int {
        val parsed = parseFileArguments(arguments) ?: return 2
        val result = CPlusCompiler().compile(
            CompileRequest(parsed.sources, cSources = parsed.cSources, cLibraries = parsed.libraries, cIncludeDirectories = parsed.includeDirectories)
        )
        printDiagnostics(result.diagnostics, parsed.sources.first())
        val artifact = result.artifacts.singleOrNull() ?: return 1
        println(AstPrinter().print(artifact.ast))
        return if (result.isSuccessful) 0 else 1
    }

    private fun build(arguments: List<String>): Int {
        val parsed = parseFileArguments(arguments) ?: return 2
        val executable = parsed.output ?: parsed.sources.first().resolveSibling(parsed.sources.first().nameWithoutExtension)
        return buildExecutable(
            parsed.sources,
            parsed.cSources,
            executable,
            parsed.headerOutput,
            parsed.libraries,
            parsed.includeDirectories
        )
    }

    private fun runProgram(arguments: List<String>): Int {
        val parsed = parseFileArguments(arguments) ?: return 2
        val temporaryDirectory = Files.createTempDirectory("cplus-run")
        val executable = temporaryDirectory.resolve(parsed.sources.first().nameWithoutExtension)
        val buildExitCode = buildExecutable(
            parsed.sources,
            parsed.cSources,
            executable,
            parsed.headerOutput,
            parsed.libraries,
            parsed.includeDirectories
        )
        if (buildExitCode != 0) return buildExitCode
        val process = ProcessBuilder(executable.toString()).inheritIO().start()
        return process.waitFor()
    }

    private fun lsp(arguments: List<String>): Int {
        if (arguments.isNotEmpty()) {
            System.err.println("lsp accepts no positional arguments and communicates over stdin/stdout")
            return 2
        }
        return LspServer().run(System.`in`, System.out)
    }

    private fun buildExecutable(
        sources: List<Path>,
        cSources: List<Path>,
        executable: Path,
        headerOutput: Path? = null,
        libraries: List<String> = emptyList(),
        includeDirectories: List<Path> = emptyList()
    ): Int {
        val compiler = CPlusCompiler()
        val result = compiler.compile(
            CompileRequest(
                sources,
                cSources = cSources,
                cLibraries = libraries,
                cIncludeDirectories = includeDirectories
            )
        )
        printDiagnostics(result.diagnostics, sources.first())
        if (!result.isSuccessful) return 1
        val generated = result.generatedUnits.singleOrNull()?.text ?: return 2
        val cFile = executable.resolveSibling("${executable.fileName}.c")
        cFile.parent?.let { Files.createDirectories(it) }
        executable.parent?.let { Files.createDirectories(it) }
        cFile.writeText(generated)
        headerOutput?.let { headerPath ->
            val header = result.generatedHeaders.singleOrNull()?.text ?: return 2
            headerPath.parent?.let { Files.createDirectories(it) }
            headerPath.writeText(header)
        }
        val process = try {
            val dependencySources = result.cSourceDependencies.map { it.path.toString() }
            val includeFlags = includeDirectories
                .map { it.toAbsolutePath().normalize().toString() }
                .distinct()
                .flatMap { listOf("-I", it) }
            val libraryFlags = result.cLinkDependencies.map { dependency ->
                when (dependency.kind) {
                    cplus.compiler.CLinkDependencyKind.LOCAL -> dependency.value
                    cplus.compiler.CLinkDependencyKind.FOREIGN -> "-l${dependency.value}"
                }
            }
            ProcessBuilder(
                listOf("cc", "-std=c17") + includeFlags + listOf(cFile.toString()) +
                    dependencySources + libraryFlags + listOf("-o", executable.toString())
            )
                .redirectErrorStream(true)
                .start()
        } catch (error: java.io.IOException) {
            System.err.println("unable to start C compiler 'cc': ${error.message}")
            return 2
        }
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        if (output.isNotBlank()) {
            val remapped = compiler.remapCCompilerDiagnostics(result, cFile, output)
            if (remapped.isEmpty()) {
                print(output)
            } else {
                printCCompilerDiagnostics(remapped)
            }
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

    private fun parseFileArguments(arguments: List<String>): FileArguments? {
        var source: Path? = null
        val sources = mutableListOf<Path>()
        val cSources = mutableListOf<Path>()
        val libraries = mutableListOf<String>()
        val includeDirectories = mutableListOf<Path>()
        var output: Path? = null
        var headerOutput: Path? = null
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
        if (source == null) {
            System.err.println("a source file is required")
            return null
        }
        return FileArguments(listOf(source) + sources, cSources, output, headerOutput, libraries, includeDirectories)
    }

    private fun printDiagnostics(diagnostics: List<Diagnostic>, source: Path) {
        if (diagnostics.isEmpty()) return
        val text = if (Files.exists(source)) source.readText() else ""
        val lineIndex = LineIndex.from(text)
        diagnostics.forEach { diagnostic ->
            val position = diagnostic.range?.let { lineIndex.positionAt(it.startOffset) }
            val location = position?.let { ":${it.line}:${it.column}" } ?: ""
            val code = diagnostic.code?.let { " [$it]" } ?: ""
            System.err.println("$source$location: ${diagnostic.severity.name.lowercase()}$code: ${diagnostic.message}")
        }
    }

    private fun printUsage(stream: java.io.PrintStream = System.out) {
        stream.println("C+ CLI transcoder")
        stream.println("usage: cplus <command> <source.cp> [other.cp ...] [--c-source <file>] [--library <name-or-path>] [--include-dir <dir>] [--output <file>] [--header <file>]")
        stream.println()
        stream.println("commands:")
        stream.println("  transcode   translate one C+ source file to C")
        stream.println("  emit-c      alias for transcode")
        stream.println("  check       parse and semantically validate one source file")
        stream.println("  ast         print the normalized AST")
        stream.println("  expand      print the post-CPX normalized AST")
        stream.println("  build       transcode and compile one source file with cc")
        stream.println("  run         build and execute one source file")
        stream.println("  lsp         serve compiler diagnostics over stdio JSON-RPC")
    }

    private data class FileArguments(
        val sources: List<Path>,
        val cSources: List<Path>,
        val output: Path?,
        val headerOutput: Path?,
        val libraries: List<String>,
        val includeDirectories: List<Path>
    )
}

internal class AstPrinter {
    fun print(program: AstProgram): String = buildString {
        appendLine("Program")
        program.declarations.forEach { declaration -> appendDeclaration(declaration, 1) }
    }

    private fun StringBuilder.appendDeclaration(declaration: AstDeclaration, depth: Int) {
        indent(depth)
        when (declaration) {
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
                "Import ${declaration.module} ${declaration.alias?.let { "as $it " } ?: ""}{${declaration.names.joinToString(", ")}}"
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
