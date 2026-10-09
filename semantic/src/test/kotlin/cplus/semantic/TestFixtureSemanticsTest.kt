package cplus.semantic

import cplus.core.*
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TestFixtureSemanticsTest {
    @Test
    fun resolvesFixtureModuleBindingsAndMethodsWithPrivateIndependentScopes() {
        val provider = parse(121, "provider.cp", """
            pub struct Box { int value; };
            pub int identity(int value) { return value; }
        """)
        val client = parse(122, "client.cp", """
            import {Box, identity} from provider;
            int shared;
            comptime trait Box { int doubled(self) { return self.value * 2; } }
            test duplicate description {
                Box item;
                item.value = identity(shared);
                item.doubled();
                return;
            }
            test duplicate description {
                Box other;
                other.value = identity(shared);
                other.doubled();
            }
        """)
        val program = AstProgram(
            provider.declarations + client.declarations,
            client.origin,
            listOf(
                AstModule("provider", provider.declarations),
                AstModule("client", client.declarations)
            )
        )

        val result = SemanticAnalyzer().analyze(program, knownModules = setOf("provider"))

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val model = assertNotNull(result.model)
        assertEquals(2, model.testFixtures.size)
        assertEquals(2, model.testFixtures.map { it.identity }.distinct().size)
        assertTrue(model.testFixtures.all { it.moduleName == "client" })
        model.testFixtures.forEach { fixture ->
            val fixtureScope = model.scopes.get(fixture.scopeId)
            assertEquals(ScopeKind.FUNCTION, fixtureScope.kind)
            assertEquals(ScopeKind.MODULE, model.scopes.get(fixtureScope.parent!!).kind)
            val bodyScope = model.scopes.all.single { it.parent == fixture.scopeId && it.kind == ScopeKind.BLOCK }
            assertTrue(model.scopes.lookup(bodyScope.id, if (fixture == model.testFixtures.first()) "item" else "other").isNotEmpty())
        }
        assertTrue(model.resolvedMethodCalls.values.any { it.method.symbol.name == "doubled" })
        val identity = model.resolveFunction("identity", "client")
        assertNotNull(identity)
        assertTrue(model.referenceIndex.referencesTo(identity.symbol.id).isNotEmpty())
        assertTrue(model.symbolNamed("duplicate description") == null)
        assertTrue(model.declarationCatalogue.named("duplicate description").isEmpty())
    }

    @Test
    fun isolatesFixtureLocalsAndValidatesReturnAndLoopControl() {
        val validFixture = "test one { int local = 1; } test two { return; }"
        val isolation = analyze("int main() { return 0; } $validFixture test uses leaked { return local; }")
        assertTrue(isolation.diagnostics.any { it.code == "SEM301" && it.message.contains("local") }, isolation.diagnostics.joinToString())

        val invalid = analyze("""
            test return value { return 1; }
            test break outside loop { break; }
            test continue outside loop { continue; }
            test valid void return { return; }
        """)
        assertEquals(1, invalid.diagnostics.count { it.code == "SEM202" }, invalid.diagnostics.joinToString())
        assertEquals(1, invalid.diagnostics.count { it.code == "SEM205" }, invalid.diagnostics.joinToString())
        assertEquals(1, invalid.diagnostics.count { it.code == "SEM206" }, invalid.diagnostics.joinToString())
        assertTrue(invalid.model?.testFixtures?.size == 4)
    }

    private fun analyze(text: String): SemanticResult =
        SemanticAnalyzer().analyze(parse(123, "fixture-semantics.cp", text))

    private fun parse(sourceId: Int, path: String, text: String): AstProgram {
        val source = SourceFile(SourceFileId(sourceId), Path.of(path), text.trimIndent(), 1)
        val parsed = Parser(Lexer().lex(source)).parse()
        assertTrue(parsed.diagnostics.isEmpty(), parsed.diagnostics.joinToString())
        return AstBuilder().build(parsed.syntax)
    }
}
