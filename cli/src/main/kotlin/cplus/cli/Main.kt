package cplus.cli

import cplus.compiler.CPlusCompiler
import cplus.compiler.CompileRequest
import cplus.core.*
import java.nio.file.Files
import java.nio.file.Path
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
            else -> {
                System.err.println("unknown command '$command'")
                printUsage(System.err)
                2
            }
        }
    }

    private fun transcode(arguments: List<String>): Int {
        val parsed = parseFileArguments(arguments) ?: return 2
        val result = CPlusCompiler().compile(CompileRequest(listOf(parsed.source)))
        printDiagnostics(result.diagnostics, parsed.source)
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
        val result = CPlusCompiler().compile(CompileRequest(listOf(parsed.source)))
        printDiagnostics(result.diagnostics, parsed.source)
        if (result.isSuccessful) println("OK: ${parsed.source}")
        return if (result.isSuccessful) 0 else 1
    }

    private fun ast(arguments: List<String>): Int {
        val parsed = parseFileArguments(arguments) ?: return 2
        val result = CPlusCompiler().compile(CompileRequest(listOf(parsed.source)))
        printDiagnostics(result.diagnostics, parsed.source)
        val artifact = result.artifacts.singleOrNull() ?: return 1
        println(AstPrinter().print(artifact.ast))
        return if (result.isSuccessful) 0 else 1
    }

    private fun parseFileArguments(arguments: List<String>): FileArguments? {
        var source: Path? = null
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
                    if (source != null) {
                        System.err.println("only one source file is supported in the initial vertical slice")
                        return null
                    }
                    source = Path.of(argument)
                    index++
                }
            }
        }
        if (source == null) {
            System.err.println("a source file is required")
            return null
        }
        return FileArguments(source, output)
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
        stream.println("usage: cplus <command> <source.cp> [--output <file>]")
        stream.println()
        stream.println("commands:")
        stream.println("  transcode   translate one C+ source file to C")
        stream.println("  emit-c      alias for transcode")
        stream.println("  check       parse and semantically validate one source file")
        stream.println("  ast         print the normalized AST")
    }

    private data class FileArguments(val source: Path, val output: Path?)
}

private class AstPrinter {
    fun print(program: AstProgram): String = buildString {
        appendLine("Program")
        program.declarations.forEach { declaration -> appendDeclaration(declaration, 1) }
    }

    private fun StringBuilder.appendDeclaration(declaration: AstDeclaration, depth: Int) {
        indent(depth)
        when (declaration) {
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
