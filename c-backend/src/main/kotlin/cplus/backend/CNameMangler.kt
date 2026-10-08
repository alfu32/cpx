package cplus.backend

import cplus.semantic.MethodSymbol
import cplus.semantic.CType
import cplus.semantic.Symbol

interface CNameMangler {
    fun nameOf(symbol: Symbol): String

    fun methodName(owner: CType, method: MethodSymbol): String

    fun extensionMethodName(method: MethodSymbol): String = method.symbol.externalName ?:
        "__cplus_ext_${method.definingModule}_${method.owner.name}_${method.symbol.name}"
}

class DefaultCNameMangler : CNameMangler {
    override fun nameOf(symbol: Symbol): String = symbol.externalName ?: symbol.name

    override fun methodName(owner: CType, method: MethodSymbol): String =
        "${sanitize(owner.name)}__${sanitize(method.symbol.name)}"

    override fun extensionMethodName(method: MethodSymbol): String = method.symbol.externalName ?:
        "__cplus_ext_${sanitize(method.definingModule)}_${sanitize(method.owner.name)}_${sanitize(method.symbol.name)}"

    private fun sanitize(name: String): String = buildString {
        name.forEach { character ->
            if (character.isLetterOrDigit() || character == '_') append(character) else append('_')
        }
    }
}
