package cplus.backend

import cplus.core.*
import cplus.semantic.*

class CDependencyCollector {
    fun collect(
        program: AstProgram,
        semantic: SemanticModel,
        requiresStringTemplateRuntime: Boolean
    ): List<String> {
        val includes = linkedSetOf<String>()
        program.declarations
            .filterIsInstance<AstImport>()
            .forEach { import -> includeForModule(import.module)?.let(includes::add) }

        semantic.foreignTypes.values.forEach { type -> includeForTypeName(type.externalName)?.let(includes::add) }
        semantic.foreignFunctions.values.forEach { function ->
            includeForType(function.returnType, includes)
            function.parameters.forEach { parameter -> includeForType(parameter.type, includes) }
        }
        semantic.foreignGlobals.values.forEach { symbol -> includeForType(symbol.type, includes) }

        if (requiresStringTemplateRuntime) {
            // __cplus_format is supplied by the compiler-support runtime;
            // self-hosted profiles must not pull hosted stdio implicitly.
        }
        if (program.containsAbiQuery()) includes += "stddef.h"
        return includes.sorted()
    }

    private fun AstProgram.containsAbiQuery(): Boolean = declarations.any { declaration ->
        fun expression(expression: AstExpression): Boolean = when (expression) {
            is AstAbiQuery -> true
            is AstUnary -> expression(expression.operand)
            is AstBinary -> expression(expression.left) || expression(expression.right)
            is AstConditional -> expression(expression.condition) || expression(expression.thenBranch) || expression(expression.elseBranch)
            is AstUpdate -> expression(expression.operand)
            is AstSizeOf -> expression.operand?.let(::expression) == true
            is AstCast -> expression(expression.operand)
            is AstCall -> expression(expression.callee) || expression.arguments.any(::expression)
            is AstMemberAccess -> expression(expression.receiver)
            is AstIndexAccess -> expression(expression.receiver) || expression(expression.index)
            is AstParenthesized -> expression(expression.expression)
            is AstStringTemplate -> expression.parts.filterIsInstance<AstStringExpressionPart>().any { expression(it.expression) }
            else -> false
        }
        fun statement(statement: AstStatement): Boolean = when (statement) {
            is AstBlock -> statement.statements.any(::statement)
            is AstReturn -> statement.expression?.let(::expression) == true
            is AstExpressionStatement -> expression(statement.expression)
            is AstDefer -> expression(statement.expression)
            is AstIf -> expression(statement.condition) || statement.thenBranch.let(::statement) || statement.elseBranch?.let(::statement) == true
            is AstWhile -> expression(statement.condition) || statement.body.let(::statement)
            is AstFor -> statement.initializer?.let(::statement) == true || statement.condition?.let(::expression) == true || statement.increment?.let(::expression) == true || statement.body.let(::statement)
            is AstVariableDeclaration -> statement.initializer?.let(::expression) == true
            else -> false
        }
        when (declaration) {
            is AstGlobalVariable -> declaration.initializer?.let(::expression) == true
            is AstFunction -> declaration.body?.let(::statement) == true
            is AstStruct -> declaration.methods.any { it.body?.let(::statement) == true }
            else -> false
        }
    }

    fun collectPublic(program: AstProgram, unit: CTranslationUnit): List<String> {
        val includes = linkedSetOf<String>()
        program.declarations
            .filterIsInstance<AstImport>()
            .filter { it.isPublic }
            .forEach { import -> includeForModule(import.module)?.let(includes::add) }

        val aggregates = if (unit.aggregateDeclarations.isNotEmpty()) {
            unit.aggregateDeclarations
        } else {
            buildList<CAggregateDeclaration> {
                addAll(unit.structs)
                addAll(unit.unions)
            }
        }
        aggregates.filter { it.isPublic }.forEach { aggregate ->
            aggregate.fields.forEach { field -> includeForCType(field.type, includes) }
        }
        unit.aliases.filter { it.isPublic }.forEach { alias -> includeForCType(alias.target, includes) }
        unit.globals.filter { it.isPublic }.forEach { global -> includeForCType(global.type, includes) }
        unit.functions.filter { it.isPublic }.forEach { function ->
            includeForCType(function.returnType, includes)
            function.parameters.forEach { parameter -> includeForCType(parameter.type, includes) }
        }
        return includes.sorted()
    }

    private fun includeForType(type: cplus.semantic.CType, includes: MutableSet<String>) {
        when (type) {
            is PrimitiveType -> includeForTypeName(type.name)?.let(includes::add)
            is ForeignType -> includeForTypeName(type.externalName)?.let(includes::add)
            is PointerType -> includeForType(type.pointee, includes)
            is ArrayType -> includeForType(type.element, includes)
            is AliasType -> includeForType(type.target, includes)
            is FunctionType -> {
                includeForType(type.returnType, includes)
                type.parameterTypes.forEach { includeForType(it, includes) }
            }
            else -> Unit
        }
    }

    private fun includeForTypeName(name: String): String? = when (name) {
        "size_t", "ptrdiff_t", "max_align_t" -> "stddef.h"
        "FILE", "fpos_t" -> "stdio.h"
        else -> null
    }

    private fun includeForCType(type: CType, includes: MutableSet<String>) {
        when (type) {
            is CType.Named -> includeForTypeName(type.name)?.let(includes::add)
            is CType.Primitive -> if (type.name in setOf("size_t", "ptrdiff_t", "max_align_t")) includes += "stddef.h"
            is CType.FunctionPointer -> {
                includeForCType(type.returnType, includes)
                type.parameterTypes.forEach { includeForCType(it, includes) }
            }
            is CType.Struct, is CType.Union, is CType.Enum, CType.Unknown -> Unit
        }
    }

    private fun includeForModule(module: String): String? = when (module) {
        "c.stdio" -> "stdio.h"
        "c.stddef" -> "stddef.h"
        "c.stdlib" -> "stdlib.h"
        "c.math" -> "math.h"
        "c.string" -> "string.h"
        "c.ctype" -> "ctype.h"
        "c.time" -> "time.h"
        "c.stdint" -> "stdint.h"
        "c.stdarg" -> "stdarg.h"
        else -> null
    }
}
