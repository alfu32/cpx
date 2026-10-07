package cplus.backend

import cplus.core.Origin

/**
 * Derives a public C header from the lowered C AST.
 *
 * The generator deliberately consumes declarations rather than source text so
 * visibility, lowering, and generated names remain aligned with the emitted
 * implementation.
 */
class CHeaderGenerator {
    fun generate(unit: CTranslationUnit): GeneratedCUnit {
        val output = StringBuilder()
        val mappings = mutableListOf<SourceMapping>()
        var line = 1
        var byteOffset = 0

        fun append(text: String) {
            output.append(text)
            line += text.count { it == '\n' }
            byteOffset += text.toByteArray(Charsets.UTF_8).size
        }

        fun appendLine(text: String = "", origin: Origin? = null) {
            if (origin != null) {
                val lineBytes = text.toByteArray(Charsets.UTF_8).size
                mappings += SourceMapping(line, origin, byteOffset, byteOffset + lineBytes)
            }
            append(text)
            append("\n")
        }

        val aggregates = if (unit.aggregateDeclarations.isNotEmpty()) {
            unit.aggregateDeclarations
        } else {
            buildList<CAggregateDeclaration> {
                addAll(unit.structs)
                addAll(unit.unions)
            }
        }
        val aggregateByKey = aggregates.associateBy(::aggregateKey)
        val aliases = unit.aliases.associateBy { it.name }
        val selectedKeys = linkedSetOf<String>()
        val selectedAliasNames = linkedSetOf<String>()
        val forwardKeys = linkedSetOf<String>()

        lateinit var collectType: (CType) -> Unit
        val collectAggregate: (String, Boolean) -> Unit = { key, byValue ->
            if (byValue) {
                if (selectedKeys.add(key)) {
                    aggregateByKey[key]?.fields?.forEach { field -> collectType(field.type) }
                    forwardKeys.remove(key)
                }
            } else if (key !in selectedKeys) {
                forwardKeys += key
            }
        }
        collectType = { type ->
            when (type) {
                is CType.Struct -> collectAggregate("struct:${type.name}", type.pointerDepth == 0)
                is CType.Union -> collectAggregate("union:${type.name}", type.pointerDepth == 0)
                is CType.Named -> aliases[type.name]?.let {
                    selectedAliasNames += it.name
                    collectType(it.target)
                }
                is CType.FunctionPointer -> {
                    collectType(type.returnType)
                    type.parameterTypes.forEach(collectType)
                }
                is CType.Primitive, is CType.Enum, CType.Unknown -> Unit
            }
        }

        aggregates.filter { it.isPublic }.forEach { aggregate ->
            collectAggregate(aggregateKey(aggregate), true)
        }
        unit.aliases.filter { it.isPublic }.forEach { alias ->
            selectedAliasNames += alias.name
            collectType(alias.target)
        }
        unit.globals.filter { it.isPublic }.forEach { global -> collectType(global.type) }
        unit.functions.filter { it.isPublic }.forEach { function ->
            collectType(function.returnType)
            function.parameters.forEach { parameter -> collectType(parameter.type) }
        }

        val forwardDeclarations = buildList {
            unit.forwardDeclarations
                .filter { "${it.kind.name.lowercase()}:${it.name}" in forwardKeys }
                .forEach { add(it) }
            forwardKeys
                .mapNotNull { key -> aggregateByKey[key]?.let { aggregate ->
                    CForwardDeclaration(tagKind(aggregate), aggregate.name, aggregate.origin)
                } }
                .forEach(::add)
        }.distinctBy { "${it.kind.name}: ${it.name}" }
            .sortedWith(compareBy<CForwardDeclaration> { it.kind.ordinal }.thenBy { it.name })

        appendLine("#pragma once")
        if (usesBool(unit, selectedKeys, selectedAliasNames, aggregateByKey, aliases)) {
            appendLine("#include <stdbool.h>")
        }
        unit.publicIncludes.distinct().sorted().forEach { include -> appendLine("#include <$include>") }
        if (usesBool(unit, selectedKeys, selectedAliasNames, aggregateByKey, aliases) || unit.publicIncludes.isNotEmpty()) appendLine()

        forwardDeclarations.forEach { declaration ->
            appendLine("${declaration.kind.name.lowercase()} ${declaration.name};", declaration.origin)
        }
        if (forwardDeclarations.isNotEmpty()) appendLine()

        aggregates.filter { aggregateKey(it) in selectedKeys }.forEachIndexed { index, aggregate ->
            when (aggregate) {
                is CStructDeclaration -> appendLine("struct ${aggregate.name} {", aggregate.origin)
                is CUnionDeclaration -> appendLine("union ${aggregate.name} {", aggregate.origin)
            }
            aggregate.fields.forEach { field ->
                appendLine("    ${field.type.renderDeclaration(field.name)}${arraySuffix(field.arrayDimensions)};", field.origin)
            }
            appendLine("};", aggregate.origin)
            if (index != selectedKeys.size - 1 || unit.enums.any { it.isPublic } || unit.aliases.any { it.name in selectedAliasNames } || unit.globals.any { it.isPublic } || unit.functions.any { it.isPublic }) {
                appendLine()
            }
        }

        val publicEnums = unit.enums.filter { it.isPublic }
        publicEnums.forEachIndexed { index, enumeration ->
            appendLine("enum ${enumeration.name} {", enumeration.origin)
            enumeration.values.forEach { value ->
                appendLine("    ${value.name}${value.value?.let { " = $it" }.orEmpty()},", value.origin)
            }
            appendLine("};", enumeration.origin)
            if (index != publicEnums.lastIndex || unit.aliases.any { it.name in selectedAliasNames } || unit.globals.any { it.isPublic } || unit.functions.any { it.isPublic }) appendLine()
        }

        val headerAliases = unit.aliases.filter { it.name in selectedAliasNames }
        headerAliases.forEachIndexed { index, alias ->
            appendLine("typedef ${alias.target.renderDeclaration(alias.name)}${arraySuffix(alias.arrayDimensions)};", alias.origin)
            if (index != headerAliases.lastIndex || unit.globals.any { it.isPublic } || unit.functions.any { it.isPublic }) appendLine()
        }

        val publicGlobals = unit.globals.filter { it.isPublic }
        publicGlobals.forEach { global ->
            val threadLocal = if (global.threadLocal) "_Thread_local " else ""
            appendLine("extern ${threadLocal}${global.type.renderDeclaration(global.name)}${arraySuffix(global.arrayDimensions)};", global.origin)
        }
        if (publicGlobals.isNotEmpty() && unit.functions.any { it.isPublic }) appendLine()

        unit.functions.filter { it.isPublic }.forEach { function ->
            appendLine(
                "${function.returnType.render()} ${function.name}(${parameters(function.parameters, function.isVariadic)});",
                function.origin
            )
        }

        return GeneratedCUnit(output.toString(), mappings)
    }

    private fun aggregateKey(aggregate: CAggregateDeclaration): String = when (aggregate) {
        is CStructDeclaration -> "struct:${aggregate.name}"
        is CUnionDeclaration -> "union:${aggregate.name}"
    }

    private fun tagKind(aggregate: CAggregateDeclaration): CTagKind = when (aggregate) {
        is CStructDeclaration -> CTagKind.STRUCT
        is CUnionDeclaration -> CTagKind.UNION
    }

    private fun usesBool(
        unit: CTranslationUnit,
        selectedKeys: Set<String>,
        selectedAliasNames: Set<String>,
        aggregateByKey: Map<String, CAggregateDeclaration>,
        aliases: Map<String, CAliasDeclaration>
    ): Boolean {
        fun typeUsesBool(type: CType): Boolean = when (type) {
            is CType.Primitive -> type.name == "bool"
            is CType.Named -> aliases[type.name]?.let { typeUsesBool(it.target) } == true
            is CType.FunctionPointer -> typeUsesBool(type.returnType) || type.parameterTypes.any(::typeUsesBool)
            else -> false
        }
        return selectedKeys.any { key -> aggregateByKey[key]?.fields?.any { typeUsesBool(it.type) } == true } ||
            unit.aliases.any { it.name in selectedAliasNames && typeUsesBool(it.target) } ||
            unit.globals.any { it.isPublic && typeUsesBool(it.type) } ||
            unit.functions.any { function ->
                function.isPublic && (typeUsesBool(function.returnType) || function.parameters.any { typeUsesBool(it.type) })
            }
    }

    private fun parameters(parameters: List<CParameter>, isVariadic: Boolean): String = buildList {
        addAll(parameters.map { "${it.type.renderDeclaration(it.name)}${arraySuffix(it.arrayDimensions)}" })
        if (isVariadic) add("...")
    }.joinToString(", ")

    private fun arraySuffix(dimensions: List<String>): String = dimensions.joinToString(separator = "") { "[$it]" }
}
