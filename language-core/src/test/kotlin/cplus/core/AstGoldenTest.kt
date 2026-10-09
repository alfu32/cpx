package cplus.core

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AstGoldenTest {
    @Test
    fun baselineProgramNormalizesToStableAstShape() {
        val text = "struct point_t { int x; }; int main() { return 0; }"
        val source = SourceFile(SourceFileId(1), Path.of("golden.cp"), text, 1)
        val parsed = Parser(Lexer().lex(source)).parse()
        val ast = AstBuilder().build(parsed.syntax)

        assertTrue(parsed.diagnostics.isEmpty(), parsed.diagnostics.joinToString())
        assertEquals(listOf("point_t", "main"), ast.declarations.map {
            when (it) {
                is AstStruct -> it.name
                is AstTrait -> it.targetName
                is AstPackage -> it.name
                is AstAlias -> it.name
                is AstUnion -> it.name
                is AstEnum -> it.name
                is AstFunction -> it.name
                is AstGlobalVariable -> it.name
                is AstComptimeFunction -> it.name
                is AstCpxInvocation -> it.name
                is AstImport -> it.module
            }
        })
        val structure = ast.declarations.first() as AstStruct
        assertEquals("int", structure.fields.single().type.name)
        assertEquals("x", structure.fields.single().name)
    }

    @Test
    fun parsesExplicitTraitBlocksAndBuildsAstWithReceiverFormsAndOrigins() {
        val text = """
            comptime trait counter_t {
                int read(self) { return self.value; }
                void increment(self*) { self->value += 1; }
            }
            pub comptime trait int {
                int doubled(self) { return self * 2; }
            }
        """.trimIndent()
        val source = SourceFile(SourceFileId(901), Path.of("traits.cp"), text, 1)
        val parsed = Parser(Lexer().lex(source)).parse()
        val traits = parsed.syntax.declarations.filterIsInstance<SyntaxTrait>()
        val astTraits = AstBuilder().build(parsed.syntax).declarations.filterIsInstance<AstTrait>()

        assertTrue(parsed.diagnostics.isEmpty(), parsed.diagnostics.joinToString())
        assertEquals(listOf("counter_t", "int"), traits.map { it.targetName })
        assertEquals(listOf(false, true), traits.map { it.isPublic })
        assertEquals(listOf("read", "increment"), traits.first().methods.map { it.name })
        assertTrue(traits.first().methods.all { it.isMethod && it.body != null })
        assertEquals(listOf(false, true), traits.first().methods.map { it.parameters.first().isPointerReceiver })
        assertEquals(text.indexOf("counter_t"), traits.first().targetOrigin.primaryRange?.startOffset)
        assertEquals(text.indexOf("int read"), traits.first().methods.first().range.startOffset)
        assertEquals("counter_t", astTraits.first().targetName)
        assertEquals("counter_t", astTraits.first().methods.first().ownerName)
        assertTrue(astTraits.first().methods.first().parameters.first().isReceiver)
    }

    @Test
    fun rejectsInvalidTraitFormsAndRecoversAtFollowingDeclarations() {
        val invalid = listOf(
            "comptime traits<counter_t> { int read(self) { return 0; } }",
            "comptime trait<counter_t> { int read(self) { return 0; } }",
            "comptime trait counter_t { int missingReceiver() { return 0; } }",
            "comptime trait counter_t { int repeated(self, self*) { return 0; } }",
            "comptime trait counter_t { int field; }",
            "comptime trait counter_t { int staticMethod() { return 0; } }",
            "comptime trait counter_t { comptime trait nested_t { int f(self) { return 0; } } }",
            "comptime trait counter_t { int declaration(self); }"
        )

        invalid.forEachIndexed { index, traitSource ->
            val text = "$traitSource int afterTrait() { return 1; }"
            val source = SourceFile(SourceFileId(910 + index), Path.of("invalid-trait-$index.cp"), text, 1)
            val parsed = Parser(Lexer().lex(source)).parse()
            assertTrue(parsed.diagnostics.isNotEmpty(), "expected a diagnostic for: $traitSource")
            assertTrue(
                parsed.syntax.declarations.any { it is SyntaxFunction && it.name == "afterTrait" },
                "parser did not recover after: $traitSource; ${parsed.diagnostics.joinToString()}"
            )
        }
    }

    @Test
    fun parserCombinesMultiTokenPrimitiveTypeSpecifiers() {
        val text = "long long wide; unsigned long long count; int main() { return 0; }"
        val source = SourceFile(SourceFileId(8), Path.of("primitive-types.cp"), text, 1)
        val parsed = Parser(Lexer().lex(source)).parse()
        val ast = AstBuilder().build(parsed.syntax)

        assertTrue(parsed.diagnostics.isEmpty(), parsed.diagnostics.joinToString())
        val globals = ast.declarations.filterIsInstance<AstGlobalVariable>()
        assertEquals(listOf("long long", "unsigned long long"), globals.map { it.type.name })
    }

    @Test
    fun parserCanonicalizesCIntegerSpecifierOrderAndOptionalInt() {
        val text = """
            signed char signedByte;
            unsigned char unsignedByte;
            char signed signedByteAfter;
            char unsigned unsignedByteAfter;
            signed short int signedShort;
            int unsigned short unsignedShort;
            int signed signedInt;
            long signed int signedLong;
            int long unsigned unsignedLong;
            signed long long int signedLongLong;
            int unsigned long long unsignedLongLong;
            signed plainSigned;
            unsigned plainUnsigned;
            int main() { return 0; }
        """.trimIndent()
        val source = SourceFile(SourceFileId(11), Path.of("integer-specifiers.cp"), text, 1)
        val parsed = Parser(Lexer().lex(source)).parse()
        val ast = AstBuilder().build(parsed.syntax)

        assertTrue(parsed.diagnostics.isEmpty(), parsed.diagnostics.joinToString())
        assertEquals(
            listOf(
                "signed char", "unsigned char", "signed char", "unsigned char",
                "short", "unsigned short", "int", "long", "unsigned long", "long long",
                "unsigned long long", "int", "unsigned int"
            ),
            ast.declarations.filterIsInstance<AstGlobalVariable>().map { it.type.name }
        )
    }

    @Test
    fun parserKeepsAssignmentFromFunctionCallAsExpression() {
        val text = "int identity(int value) { return value; } int main() { int result; result = identity(7); return result; }"
        val source = SourceFile(SourceFileId(40), Path.of("function-call-assignment.cp"), text, 1)
        val parsed = Parser(Lexer().lex(source)).parse()

        assertTrue(parsed.diagnostics.isEmpty(), parsed.diagnostics.joinToString())
    }

    @Test
    fun parserConsumesFourTokenIntegerSpecifierAndRejectsConflictingSigns() {
        val text = "unsigned long long int count; unsigned signed int invalid;"
        val source = SourceFile(SourceFileId(12), Path.of("integer-specifier-errors.cp"), text, 1)
        val parsed = Parser(Lexer().lex(source)).parse()
        val ast = AstBuilder().build(parsed.syntax)

        assertEquals(listOf("PARSE102"), parsed.diagnostics.map { it.code })
        assertEquals(
            listOf("unsigned long long", "int"),
            ast.declarations.filterIsInstance<AstGlobalVariable>().map { it.type.name }
        )
    }

    @Test
    fun parserRetainsTypeAndPointerQualifiers() {
        val text = "const char* text; volatile int* const value; int main(const char * const input) { return 0; }"
        val source = SourceFile(SourceFileId(9), Path.of("qualified-types.cp"), text, 1)
        val parsed = Parser(Lexer().lex(source)).parse()
        val ast = AstBuilder().build(parsed.syntax)

        assertTrue(parsed.diagnostics.isEmpty(), parsed.diagnostics.joinToString())
        val textType = ast.declarations.filterIsInstance<AstGlobalVariable>().first { it.name == "text" }.type
        assertEquals(setOf("const"), textType.qualifiers)
        assertEquals(emptySet(), textType.pointerQualifiers.single())
        val valueType = ast.declarations.filterIsInstance<AstGlobalVariable>().first { it.name == "value" }.type
        assertEquals(setOf("volatile"), valueType.qualifiers)
        assertEquals(listOf(setOf("const")), valueType.pointerQualifiers)
        val parameter = ast.declarations.filterIsInstance<AstFunction>().single { it.name == "main" }.parameters.single()
        assertEquals(setOf("const"), parameter.type.qualifiers)
        assertEquals(listOf(setOf("const")), parameter.type.pointerQualifiers)
    }

    @Test
    fun parserRetainsFunctionPointerDeclarators() {
        val text = "int apply(int (*callback)(int value), int value) { return callback(value); }"
        val source = SourceFile(SourceFileId(10), Path.of("function-pointers.cp"), text, 1)
        val parsed = Parser(Lexer().lex(source)).parse()
        val ast = AstBuilder().build(parsed.syntax)

        assertTrue(parsed.diagnostics.isEmpty(), parsed.diagnostics.joinToString())
        val callback = ast.declarations.filterIsInstance<AstFunction>().single().parameters.first()
        assertEquals("callback", callback.name)
        assertEquals(1, callback.type.functionPointerDepth)
        assertEquals("int", callback.type.name)
        assertEquals(listOf("value"), callback.type.functionParameters!!.map { it.name })
        assertEquals("int", callback.type.functionParameters!!.single().type.name)
    }

    @Test
    fun unsupportedFunctionReturnDeclaratorsRecoverAtTheNextDeclaration() {
        val text = "int (*factory())(int); int later() { return 0; }"
        val source = SourceFile(SourceFileId(11), Path.of("unsupported-declarator.cp"), text, 1)
        val parsed = Parser(Lexer().lex(source)).parse()

        assertTrue(parsed.diagnostics.any { it.code == "PARSE410" }, parsed.diagnostics.joinToString())
        assertEquals(listOf("later"), parsed.syntax.declarations.filterIsInstance<SyntaxFunction>().map { it.name })
    }

    @Test
    fun astArenaProvidesStableAddressableNodes() {
        val range = SourceRange(SourceFileId(1), 0, 1)
        val first = AstIntegerLiteral("1", Origin.Direct(range))
        val second = AstIntegerLiteral("2", Origin.Direct(range))
        val arena = AstArena()
        val id = arena.add(first)

        assertEquals(1, arena.size)
        assertEquals(first, arena[id])
        assertEquals(first, arena.replace(id, second))
        assertEquals(second, arena[id])
    }

    @Test
    fun malformedFixtureProducesRecoverableDiagnostic() {
        val text = "int main( { return 0; }"
        val source = SourceFile(SourceFileId(2), Path.of("malformed.cp"), text, 1)
        val parsed = Parser(Lexer().lex(source)).parse()

        assertTrue(parsed.diagnostics.any { it.code == "PARSE100" || it.code == "PARSE001" })
    }

    @Test
    fun cpxDeclarationAndInvocationRemainStructuredUntilExpansion() {
        val text = """
            comptime cpx<decl> optional(type T) {
                return {
                    struct optional_{T}_t { T value; };
                };
            }
            optional(int);
        """.trimIndent()
        val source = SourceFile(SourceFileId(3), Path.of("cpx.cp"), text, 1)
        val parsed = Parser(Lexer().lex(source)).parse()

        assertTrue(parsed.diagnostics.isEmpty(), parsed.diagnostics.joinToString())
        val definition = parsed.syntax.declarations[0] as SyntaxComptimeFunction
        val invocation = parsed.syntax.declarations[1] as SyntaxCpxInvocation
        assertEquals("optional", definition.name)
        assertEquals("decl", definition.category)
        assertEquals("T", definition.parameters.single().name)
        assertTrue(definition.template.contains("optional_{T}_t"))
        assertEquals(listOf("int"), invocation.arguments)
        assertEquals(listOf("optional"), invocation.targetComponents)
    }

    @Test
    fun qualifiedComptimeInvocationPreservesItsStructuredTarget() {
        val source = SourceFile(
            SourceFileId(31),
            Path.of("qualified-cpx.cp"),
            "boxes.box(int);",
            1
        )
        val parsed = Parser(Lexer().lex(source)).parse()

        assertTrue(parsed.diagnostics.isEmpty(), parsed.diagnostics.joinToString())
        val invocation = parsed.syntax.declarations.single() as SyntaxCpxInvocation
        assertEquals("boxes.box", invocation.name)
        assertEquals(listOf("boxes", "box"), invocation.targetComponents)
        assertEquals(listOf("int"), invocation.arguments)
        assertEquals(source.text, source.text.substring(invocation.range.startOffset, invocation.range.endOffset))
    }

    @Test
    fun importsPreserveSelectiveNamesQualifiedModuleAndAlias() {
        val text = "import { add, subtract } from math.arithmetic as arithmetic; int main() { return 0; }"
        val source = SourceFile(SourceFileId(4), Path.of("imports.cp"), text, 1)
        val parsed = Parser(Lexer().lex(source)).parse()

        assertTrue(parsed.diagnostics.isEmpty(), parsed.diagnostics.joinToString())
        val import = parsed.syntax.declarations.first() as SyntaxImport
        assertEquals(listOf("add", "subtract"), import.names)
        assertEquals("math.arithmetic", import.module)
        assertEquals("arithmetic", import.alias)
        val astImport = AstBuilder().build(parsed.syntax).declarations.first() as AstImport
        assertEquals("arithmetic", astImport.alias)
    }

    @Test
    fun importsSupportRelativePathsPackagePathsAndSelectiveAliases() {
        val text = """
            import { add as sum } from ./module_helpers.cp;
            import { fs as fs1 } from "some/ref.cp";
            import { fs } from stdlib/io;
            int main() { return 0; }
        """.trimIndent()
        val source = SourceFile(SourceFileId(6), Path.of("imports.cp"), text, 1)
        val parsed = Parser(Lexer().lex(source)).parse()

        assertTrue(parsed.diagnostics.isEmpty(), parsed.diagnostics.joinToString())
        val imports = parsed.syntax.declarations.filterIsInstance<SyntaxImport>()
        assertEquals("./module_helpers.cp", imports[0].module)
        assertEquals(mapOf("add" to "sum"), imports[0].nameAliases)
        assertEquals("some/ref.cp", imports[1].module)
        assertEquals(mapOf("fs" to "fs1"), imports[1].nameAliases)
        assertEquals("stdlib/io", imports[2].module)
    }

    @Test
    fun packageDeclarationRemainsAStructuredModuleBoundary() {
        val source = SourceFile(SourceFileId(5), Path.of("package.cp"), "package collections.core; int main() { return 0; }", 1)
        val parsed = Parser(Lexer().lex(source)).parse()

        assertTrue(parsed.diagnostics.isEmpty(), parsed.diagnostics.joinToString())
        val packageDeclaration = AstBuilder().build(parsed.syntax).declarations.first() as AstPackage
        assertEquals("collections.core", packageDeclaration.name)
    }

    @Test
    fun expressionParserRetainsCallsMemberAccessAndIndexingAsStructure() {
        val source = SourceFile(
            SourceFileId(6),
            Path.of("expressions.cp"),
            "struct vector { int value; int get(self) { return self.value; } }; int main() { return vector.get()[0]; }",
            1
        )
        val parsed = Parser(Lexer().lex(source)).parse()
        val ast = AstBuilder().build(parsed.syntax)

        assertTrue(parsed.diagnostics.isEmpty(), parsed.diagnostics.joinToString())
        val structure = ast.declarations.filterIsInstance<AstStruct>().single()
        assertEquals("get", structure.methods.single().name)
        val returned = (ast.declarations.filterIsInstance<AstFunction>().single().body as AstBlock)
            .statements.single() as AstReturn
        val indexed = returned.expression as AstIndexAccess
        assertEquals("get", (indexed.receiver as AstCall).callee.let { it as AstMemberAccess }.member)
    }

    @Test
    fun parserRecoversFromIncompleteMemberCallAndContinuesWithLaterDeclarations() {
        val source = SourceFile(
            SourceFileId(7),
            Path.of("recovery.cp"),
            "int main() { return foo.bar( ; } int later() { return 0; }",
            1
        )
        val parsed = Parser(Lexer().lex(source)).parse()

        assertTrue(parsed.diagnostics.any { it.code == "PARSE001" })
        assertEquals(listOf("main", "later"), parsed.syntax.declarations.filterIsInstance<SyntaxFunction>().map { it.name })
    }
}
