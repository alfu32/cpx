package cplus.semantic

import kotlin.test.Test
import kotlin.test.assertEquals

class ScopeTableTest {
    @Test
    fun lookupWalksParentsAndPrefersNearestShadowingBinding() {
        val scopes = ScopeTable()
        val packageScope = scopes.create(ScopeKind.PACKAGE)
        val functionScope = scopes.create(ScopeKind.FUNCTION, packageScope)
        val blockScope = scopes.create(ScopeKind.BLOCK, functionScope)

        scopes.define(packageScope, "value", SymbolId(1))
        scopes.define(packageScope, "packageOnly", SymbolId(4))
        scopes.define(functionScope, "value", SymbolId(2))
        scopes.define(blockScope, "local", SymbolId(3))

        assertEquals(listOf(SymbolId(2)), scopes.lookup(blockScope, "value"))
        assertEquals(listOf(SymbolId(3)), scopes.lookup(blockScope, "local"))
        assertEquals(listOf(SymbolId(4)), scopes.lookup(blockScope, "packageOnly"))
    }
}
