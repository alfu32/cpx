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

private class Cli {
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
            "build" -> build(args.drop(1))
            "run" -> runProgram(args.drop(1))
            else -> {
                System.err.println("unknown command '$command'")
                printUsage(System.err)
                2
            }
        }
    }

    private fun transcode(arguments: List<String>): Int {
        val parsed = parseFileArguments(arguments) ?: return 2
        val result = CPlusCompiler().compile(CompileRequest(parsed.sources))
        printDiagnostics(result.diagnostics, parsed.sources.first())
        if (!result.isSuccessful) return 1
        val generated = result.generatedUnits.singleOrNull()?.text
            ?: run {
                System.err.println("transcode currently accepts exactly one source file")
                return 2
            }
        if (parsed.output == null) {
            print(generated)
        } else {
            parsed.output.writeText(generated)
        }
        return 0
    }

    private fun check(arguments: List<String>): Int {
        val parsed = parseFileArguments(arguments) ?: return 2
        val result = CPlusCompiler().compile(CompileRequest(parsed.sources))
        printDiagnostics(result.diagnostics, parsed.sources.first())
        if (result.isSuccessful) println("OK: ${parsed.sources.joinToString(", ")}")
        return if (result.isSuccessful) 0 else 1
    }

    private fun ast(arguments: List<String>): Int {
        val parsed = parseFileArguments(arguments) ?: return 2
        val result = CPlusCompiler().compile(CompileRequest(parsed.sources))
        printDiagnostics(result.diagnostics, parsed.sources.first())
        val artifact = result.artifacts.singleOrNull() ?: return 1
        println(AstPrinter().print(artifact.ast))
        return if (result.isSuccessful) 0 else 1
    }

    private fun build(arguments: List<String>): Int {
        val parsed = parseFileArguments(arguments) ?: return 2
        val executable = parsed.output ?: parsed.sources.first().resolveSibling(parsed.sources.first().nameWithoutExtension)
        return buildExecutable(parsed.sources, executable)
    }

    private fun runProgram(arguments: List<String>): Int {
        val parsed = parseFileArguments(arguments) ?: return 2
        val temporaryDirectory = Files.createTempDirectory("cplus-run")
        val executable = temporaryDirectory.resolve(parsed.sources.first().nameWithoutExtension)
        val buildExitCode = buildExecutable(parsed.sources, executable)
        if (buildExitCode != 0) return buildExitCode
        val process = ProcessBuilder(executable.toString()).inheritIO().start()
        return process.waitFor()
    }

    private fun buildExecutable(sources: List<Path>, executable: Path): Int {
        val result = CPlusCompiler().compile(CompileRequest(sources))
        printDiagnostics(result.diagnostics, sources.first())
        if (!result.isSuccessful) return 1
        val generated = result.generatedUnits.singleOrNull()?.text
            ?: run {
                System.err.println("build currently accepts exactly one source file")
                return 2
            }
        val cFile = executable.resolveSibling("${executable.fileName}.c")
        cFile.parent?.let { Files.createDirectories(it) }
        executable.parent?.let { Files.createDirectories(it) }
        cFile.writeText(generated)
        val process = try {
            ProcessBuilder("cc", "-std=c17", cFile.toString(), "-o", executable.toString())
                .redirectErrorStream(true)
                .start()
        } catch (error: java.io.IOException) {
            System.err.println("unable to start C compiler 'cc': ${error.message}")
            return 2
        }
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        if (output.isNotBlank()) print(output)
        if (exitCode == 0) println("built ${executable.toAbsolutePath()}")
        return exitCode
    }

    private fun parseFileArguments(arguments: List<String>): FileArguments? {
        var source: Path? = null
        val sources = mutableListOf<Path>()
        var output: Path? = null
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
                else -> {
                    if (source == null) source = Path.of(argument)
                    else sources.add(Path.of(argument))
                    index++
                }
            }
        }
        if (source == null) {
            System.err.println("a source file is required")
            return null
        }
        return FileArguments(listOf(source) + sources, output)
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
        stream.println("usage: cplus <command> <source.cp> [other.cp ...] [--output <file>]")
        stream.println()
        stream.println("commands:")
        stream.println("  transcode   translate one C+ source file to C")
        stream.println("  emit-c      alias for transcode")
        stream.println("  check       parse and semantically validate one source file")
        stream.println("  ast         print the normalized AST")
        stream.println("  build       transcode and compile one source file with cc")
        stream.println("  run         build and execute one source file")
    }

    private data class FileArguments(val sources: List<Path>, val output: Path?)
}

private class AstPrinter {
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
        }
    }

    private fun expression(expression: AstExpression): String = when (expression) {
        is AstIntegerLiteral -> expression.text
        is AstStringLiteral -> expression.text
        is AstCharacterLiteral -> expression.text
        is AstIdentifier -> expression.name
        is AstUnary -> "${expression.operator}${expression(expression.operand)}"
        is AstBinary -> "(${expression(expression.left)} ${expression.operator} ${expression(expression.right)})"
        is AstCall -> "${expression(expression.callee)}(${expression.arguments.joinToString(", ") { argument -> expression(argument) }})"
        is AstMemberAccess -> "${expression(expression.receiver)}.${expression.member}"
        is AstParenthesized -> "(${expression(expression.expression)})"
        is AstErrorExpression -> "<error>"
    }

    private fun StringBuilder.indent(depth: Int) {
        repeat(depth) { append("  ") }
    }
}
