package cplus.backend

import cplus.core.AstImport
import cplus.core.AstProgram
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
            includes += "stdarg.h"
            includes += "stdio.h"
        }
        return includes.sorted()
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
            is CType.Primitive, is CType.Struct, is CType.Union, is CType.Enum, CType.Unknown -> Unit
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
