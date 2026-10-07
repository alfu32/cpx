package cplus.backend

import cplus.semantic.MethodSymbol
import cplus.semantic.StructType
import cplus.semantic.Symbol

interface CNameMangler {
    fun nameOf(symbol: Symbol): String

    fun methodName(owner: StructType, method: MethodSymbol): String
}

class DefaultCNameMangler : CNameMangler {
    override fun nameOf(symbol: Symbol): String = symbol.externalName ?: symbol.name

    override fun methodName(owner: StructType, method: MethodSymbol): String =
        "${sanitize(owner.name)}__${sanitize(method.symbol.name)}"

    private fun sanitize(name: String): String = buildString {
        name.forEach { character ->
            if (character.isLetterOrDigit() || character == '_') append(character) else append('_')
        }
    }
}
