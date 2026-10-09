package cplus.compiler

import cplus.core.Lexer
import cplus.core.Parser
import cplus.core.SourceFile
import cplus.core.SourceFileId
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class SdkMetadataTraitTest {
    @Test
    fun comptimeFunctionExportMetadataPreservesCallableKindAndParameterKinds() {
        val source = SourceFile(
            SourceFileId(78),
            Path.of("comptime.cp"),
            "pub comptime cpx<decl> box(type T) { return { struct box_{T}_t { T value; }; }; }",
            1
        )
        val declaration = Parser(Lexer().lex(source)).parse().syntax.declarations.single()

        assertEquals(
            listOf("comptime-function:box:decl(type:T)"),
            SdkMetadataCache.declarationExports(declaration)
        )
        assertEquals(
            listOf("comptime:box:decl"),
            SdkMetadataCache.declarationMetadata(declaration)
        )
    }

    @Test
    fun traitMetadataExportsMethodsAsExtensionsRatherThanFreeFunctionsOrTypes() {
        val source = SourceFile(
            SourceFileId(77),
            Path.of("trait.cp"),
            "pub comptime trait int { int doubled(self) { return self * 2; } }",
            1
        )
        val trait = Parser(Lexer().lex(source)).parse().syntax.declarations.single()

        assertEquals(
            listOf("trait:int", "extension-method:int.doubled(int):int"),
            SdkMetadataCache.declarationMetadata(trait)
        )
        assertEquals(listOf("extension:int.doubled"), SdkMetadataCache.declarationExports(trait))
    }
}
