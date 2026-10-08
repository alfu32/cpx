package cplus.semantic

import cplus.core.AstBuilder
import cplus.core.AstFunction
import cplus.core.AstModule
import cplus.core.AstProgram
import cplus.core.Lexer
import cplus.core.Parser
import cplus.core.SourceFile
import cplus.core.SourceFileId
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SemanticTypeTest {
    @Test
    fun sourceTypeCatalogueSeparatesModuleOwnershipFromPublicExports() {
        val text = """
            pub typedef int public_count_t;
            typedef int private_count_t;
            pub struct public_record_t { int value; };
            struct private_record_t { int value; };
            pub union public_choice_t { int integer; float decimal; };
            union private_choice_t { int value; };
            pub enum public_tag_t { TAG_PUBLIC };
            enum private_tag_t { TAG_PRIVATE };
        """.trimIndent()
        val source = SourceFile(SourceFileId(29), Path.of("types.cp"), text, 1)
        val parsed = Parser(Lexer().lex(source)).parse()
        assertTrue(parsed.diagnostics.isEmpty(), parsed.diagnostics.joinToString())
        val declarations = AstBuilder().build(parsed.syntax).declarations
        val program = AstProgram(
            declarations,
            parsed.syntax.origin,
            listOf(AstModule("types", declarations), AstModule("consumer", emptyList()))
        )

        val result = SemanticAnalyzer().analyze(program)

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val catalogue = result.model!!.sourceTypeCatalogue
        val ownedTypes = catalogue.declarationsByModule.getValue("types")
        assertEquals(
            setOf(
                "public_count_t", "private_count_t", "public_record_t", "private_record_t",
                "public_choice_t", "private_choice_t", "public_tag_t", "private_tag_t"
            ),
            ownedTypes.keys
        )
        assertEquals(
            setOf("public_count_t", "public_record_t", "public_choice_t", "public_tag_t"),
            catalogue.exportsByModule.getValue("types").keys
        )
        assertEquals(SymbolKind.ALIAS, ownedTypes.getValue("public_count_t").kind)
        assertEquals(SymbolKind.STRUCT, ownedTypes.getValue("public_record_t").kind)
        assertEquals(SymbolKind.UNION, ownedTypes.getValue("public_choice_t").kind)
        assertEquals(SymbolKind.ENUM, ownedTypes.getValue("public_tag_t").kind)
        assertEquals(Visibility.PRIVATE, ownedTypes.getValue("private_count_t").visibility)
    }

    @Test
    fun rejectsUnimportedWorkspaceTypesButKeepsLocalForwardReferences() {
        val clientText = """
            struct LocalHolder { LocalLater* next; SharedType* shared; };
            struct LocalLater { int value; };
            int consume(SharedType* value) { return sizeof(struct SharedType); }
            int main() { SharedType* value; return consume(value); }
        """.trimIndent()
        val providerText = "pub struct SharedType { int value; };"
        val clientSource = SourceFile(SourceFileId(30), Path.of("client.cp"), clientText, 1)
        val providerSource = SourceFile(SourceFileId(31), Path.of("types.cp"), providerText, 1)
        val clientParsed = Parser(Lexer().lex(clientSource)).parse()
        val providerParsed = Parser(Lexer().lex(providerSource)).parse()
        assertTrue(clientParsed.diagnostics.isEmpty(), clientParsed.diagnostics.joinToString())
        assertTrue(providerParsed.diagnostics.isEmpty(), providerParsed.diagnostics.joinToString())
        val clientDeclarations = AstBuilder().build(clientParsed.syntax).declarations
        val providerDeclarations = AstBuilder().build(providerParsed.syntax).declarations
        val program = AstProgram(
            clientDeclarations + providerDeclarations,
            clientParsed.syntax.origin,
            listOf(AstModule("client", clientDeclarations), AstModule("types", providerDeclarations))
        )

        val result = SemanticAnalyzer().analyze(program)

        assertEquals(4, result.diagnostics.count { it.code == "SEM410" }, result.diagnostics.joinToString())
        assertTrue(result.diagnostics.none { it.message.contains("LocalLater") }, result.diagnostics.joinToString())
        assertTrue(!result.isSuccessful)
    }

    @Test
    fun selectivelyImportsAndRenamesTypesFromLaterMixedExportModule() {
        val clientText = """
            import { Record as Item, count_t as Count, make_count } from "./types.cp";
            struct Holder { Item* item; Count count; };
            int consume(Count value) { return value; }
            int main() {
                Item item;
                Count value = make_count();
                return consume(value);
            }
        """.trimIndent()
        val providerText = """
            pub struct Record { int value; };
            pub typedef long count_t;
            pub int make_count() { return 9; }
        """.trimIndent()
        val clientSource = SourceFile(SourceFileId(32), Path.of("client.cp"), clientText, 1)
        val providerSource = SourceFile(SourceFileId(33), Path.of("types.cp"), providerText, 1)
        val clientParsed = Parser(Lexer().lex(clientSource)).parse()
        val providerParsed = Parser(Lexer().lex(providerSource)).parse()
        assertTrue(clientParsed.diagnostics.isEmpty(), clientParsed.diagnostics.joinToString())
        assertTrue(providerParsed.diagnostics.isEmpty(), providerParsed.diagnostics.joinToString())
        val clientDeclarations = AstBuilder().build(clientParsed.syntax).declarations
        val providerDeclarations = AstBuilder().build(providerParsed.syntax).declarations
        val program = AstProgram(
            clientDeclarations + providerDeclarations,
            clientParsed.syntax.origin,
            listOf(AstModule("client", clientDeclarations), AstModule("types", providerDeclarations))
        )

        val result = SemanticAnalyzer().analyze(program, knownModules = setOf("types"))

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val model = result.model!!
        val clientBindings = model.moduleTypeBindings.getValue("client")
        assertEquals("Record", clientBindings.getValue("Item").declarationName)
        assertEquals("types", clientBindings.getValue("Item").ownerModule)
        assertEquals("STRUCT", clientBindings.getValue("Item").symbol.kind.name)
        assertEquals("count_t", clientBindings.getValue("Count").declarationName)
        assertEquals("make_count", model.moduleFunctions.getValue("client").keys.single { it == "make_count" })
        val importedItemId = clientBindings.getValue("Item").symbol.id
        val moduleScopes = model.scopes.all.filter { it.kind == ScopeKind.MODULE }
        assertEquals(1, moduleScopes.count { it.bindings["Item"] == listOf(importedItemId) })
        val packageScope = model.scopes.all.single { it.kind == ScopeKind.PACKAGE }
        assertFalse("Record" in packageScope.bindings)
        assertFalse("count_t" in packageScope.bindings)
    }

    @Test
    fun selectivelyImportsFromTypeOnlyModuleWithoutFunctionImportErrors() {
        val clientText = """
            import { Record as Item } from "./types.cp";
            int main() { Item value; return 0; }
        """.trimIndent()
        val providerText = "pub struct Record { int value; };"
        val clientSource = SourceFile(SourceFileId(38), Path.of("client.cp"), clientText, 1)
        val providerSource = SourceFile(SourceFileId(39), Path.of("types.cp"), providerText, 1)
        val clientParsed = Parser(Lexer().lex(clientSource)).parse()
        val providerParsed = Parser(Lexer().lex(providerSource)).parse()
        assertTrue(clientParsed.diagnostics.isEmpty(), clientParsed.diagnostics.joinToString())
        assertTrue(providerParsed.diagnostics.isEmpty(), providerParsed.diagnostics.joinToString())
        val clientDeclarations = AstBuilder().build(clientParsed.syntax).declarations
        val providerDeclarations = AstBuilder().build(providerParsed.syntax).declarations
        val program = AstProgram(
            clientDeclarations + providerDeclarations,
            clientParsed.syntax.origin,
            listOf(AstModule("client", clientDeclarations), AstModule("types", providerDeclarations))
        )

        val result = SemanticAnalyzer().analyze(program, knownModules = setOf("types"))

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        assertEquals("Record", result.model!!.moduleTypeBindings.getValue("client").getValue("Item").declarationName)
    }

    @Test
    fun resolvesQualifiedTypesThroughModuleAliasAcrossTypePositions() {
        val clientText = """
            import types as geo;
            typedef geo.Point* PointRef;
            struct Holder { geo.Point* point; geo.Coord coordinate; };
            int consume(geo.Point* point, geo.Coord coordinate) {
                return sizeof(geo.Point) > 0 ? point->x : (geo.Coord) coordinate;
            }
            int main() {
                geo.Point point;
                geo.Coord coordinate = 4;
                return consume(&point, coordinate);
            }
        """.trimIndent()
        val providerText = """
            pub struct Point { int x; };
            pub typedef int Coord;
        """.trimIndent()
        val clientSource = SourceFile(SourceFileId(42), Path.of("client.cp"), clientText, 1)
        val providerSource = SourceFile(SourceFileId(43), Path.of("types.cp"), providerText, 1)
        val clientParsed = Parser(Lexer().lex(clientSource)).parse()
        val providerParsed = Parser(Lexer().lex(providerSource)).parse()
        assertTrue(clientParsed.diagnostics.isEmpty(), clientParsed.diagnostics.joinToString())
        assertTrue(providerParsed.diagnostics.isEmpty(), providerParsed.diagnostics.joinToString())
        val clientDeclarations = AstBuilder().build(clientParsed.syntax).declarations
        val providerDeclarations = AstBuilder().build(providerParsed.syntax).declarations
        val program = AstProgram(
            clientDeclarations + providerDeclarations,
            clientParsed.syntax.origin,
            listOf(AstModule("client", clientDeclarations), AstModule("types", providerDeclarations))
        )

        val result = SemanticAnalyzer().analyze(program, knownModules = setOf("types"))

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        assertEquals("types", result.model!!.moduleTypeAliases.getValue("client").getValue("geo"))
    }

    @Test
    fun diagnosesMissingAndPrivateQualifiedTypes() {
        val clientText = """
            import types as geo;
            int main() { geo.PrivateType value; geo.MissingType missing; return 0; }
        """.trimIndent()
        val providerText = "struct PrivateType { int value; };"
        val clientSource = SourceFile(SourceFileId(44), Path.of("client.cp"), clientText, 1)
        val providerSource = SourceFile(SourceFileId(45), Path.of("types.cp"), providerText, 1)
        val clientParsed = Parser(Lexer().lex(clientSource)).parse()
        val providerParsed = Parser(Lexer().lex(providerSource)).parse()
        assertTrue(clientParsed.diagnostics.isEmpty(), clientParsed.diagnostics.joinToString())
        assertTrue(providerParsed.diagnostics.isEmpty(), providerParsed.diagnostics.joinToString())
        val clientDeclarations = AstBuilder().build(clientParsed.syntax).declarations
        val providerDeclarations = AstBuilder().build(providerParsed.syntax).declarations
        val program = AstProgram(
            clientDeclarations + providerDeclarations,
            clientParsed.syntax.origin,
            listOf(AstModule("client", clientDeclarations), AstModule("types", providerDeclarations))
        )

        val result = SemanticAnalyzer().analyze(program, knownModules = setOf("types"))

        assertEquals(1, result.diagnostics.count { it.code == "SEM404" }, result.diagnostics.joinToString())
        assertEquals(1, result.diagnostics.count { it.code == "SEM406" }, result.diagnostics.joinToString())
    }

    @Test
    fun diagnosesPrivateAndMissingSelectiveTypeImports() {
        val clientText = """
            import { PrivateType as Hidden, MissingType } from "./types.cp";
            int main() { Hidden value; return 0; }
        """.trimIndent()
        val providerText = "struct PrivateType { int value; };"
        val clientSource = SourceFile(SourceFileId(34), Path.of("client.cp"), clientText, 1)
        val providerSource = SourceFile(SourceFileId(35), Path.of("types.cp"), providerText, 1)
        val clientParsed = Parser(Lexer().lex(clientSource)).parse()
        val providerParsed = Parser(Lexer().lex(providerSource)).parse()
        assertTrue(clientParsed.diagnostics.isEmpty(), clientParsed.diagnostics.joinToString())
        assertTrue(providerParsed.diagnostics.isEmpty(), providerParsed.diagnostics.joinToString())
        val clientDeclarations = AstBuilder().build(clientParsed.syntax).declarations
        val providerDeclarations = AstBuilder().build(providerParsed.syntax).declarations
        val program = AstProgram(
            clientDeclarations + providerDeclarations,
            clientParsed.syntax.origin,
            listOf(AstModule("client", clientDeclarations), AstModule("types", providerDeclarations))
        )

        val result = SemanticAnalyzer().analyze(program, knownModules = setOf("types"))

        assertEquals(1, result.diagnostics.count { it.code == "SEM406" }, result.diagnostics.joinToString())
        assertEquals(1, result.diagnostics.count { it.code == "SEM404" }, result.diagnostics.joinToString())
        assertTrue(result.diagnostics.none { it.code == "SEM410" }, result.diagnostics.joinToString())
    }

    @Test
    fun diagnosesSelectiveTypeAliasCollisionWithLocalType() {
        val clientText = """
            import { Record as LocalRecord } from "./types.cp";
            struct LocalRecord { int own; };
            int main() { return 0; }
        """.trimIndent()
        val providerText = "pub struct Record { int value; };"
        val clientSource = SourceFile(SourceFileId(36), Path.of("client.cp"), clientText, 1)
        val providerSource = SourceFile(SourceFileId(37), Path.of("types.cp"), providerText, 1)
        val clientParsed = Parser(Lexer().lex(clientSource)).parse()
        val providerParsed = Parser(Lexer().lex(providerSource)).parse()
        assertTrue(clientParsed.diagnostics.isEmpty(), clientParsed.diagnostics.joinToString())
        assertTrue(providerParsed.diagnostics.isEmpty(), providerParsed.diagnostics.joinToString())
        val clientDeclarations = AstBuilder().build(clientParsed.syntax).declarations
        val providerDeclarations = AstBuilder().build(providerParsed.syntax).declarations
        val program = AstProgram(
            clientDeclarations + providerDeclarations,
            clientParsed.syntax.origin,
            listOf(AstModule("client", clientDeclarations), AstModule("types", providerDeclarations))
        )

        val result = SemanticAnalyzer().analyze(program, knownModules = setOf("types"))

        assertEquals(1, result.diagnostics.count { it.code == "SEM405" }, result.diagnostics.joinToString())
    }

    @Test
    fun diagnosesSelectiveTypeAndFunctionAliasCollision() {
        val clientText = """
            import { Record as Shared, make_value as Shared } from "./types.cp";
            int main() { return 0; }
        """.trimIndent()
        val providerText = """
            pub struct Record { int value; };
            pub int make_value() { return 1; }
        """.trimIndent()
        val clientSource = SourceFile(SourceFileId(40), Path.of("client.cp"), clientText, 1)
        val providerSource = SourceFile(SourceFileId(41), Path.of("types.cp"), providerText, 1)
        val clientParsed = Parser(Lexer().lex(clientSource)).parse()
        val providerParsed = Parser(Lexer().lex(providerSource)).parse()
        assertTrue(clientParsed.diagnostics.isEmpty(), clientParsed.diagnostics.joinToString())
        assertTrue(providerParsed.diagnostics.isEmpty(), providerParsed.diagnostics.joinToString())
        val clientDeclarations = AstBuilder().build(clientParsed.syntax).declarations
        val providerDeclarations = AstBuilder().build(providerParsed.syntax).declarations
        val program = AstProgram(
            clientDeclarations + providerDeclarations,
            clientParsed.syntax.origin,
            listOf(AstModule("client", clientDeclarations), AstModule("types", providerDeclarations))
        )

        val result = SemanticAnalyzer().analyze(program, knownModules = setOf("types"))

        assertEquals(1, result.diagnostics.count { it.code == "SEM405" }, result.diagnostics.joinToString())
    }

    @Test
    fun cSourceSymbolsExposeForeignToolingInformation() {
        val cText = "int helper_value(void) { return 12; }"
        val cSource = SourceFile(SourceFileId(18), Path.of("helper.c"), cText, 1)
        val cplusText = "int main() { return helper_value(); }"
        val source = SourceFile(SourceFileId(19), Path.of("main.cp"), cplusText, 1)
        val parsed = Parser(Lexer().lex(source)).parse()

        val result = SemanticAnalyzer().analyze(
            AstBuilder().build(parsed.syntax),
            foreignSources = listOf(CSourceUnit(cSource, "c.source.helper"))
        )

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val model = result.model!!
        val helper = model.foreignFunctions.getValue("helper_value")
        assertEquals(SymbolKind.FOREIGN, helper.symbol.kind)
        assertEquals("int", model.functionSignature("helper_value")!!.returnType.name)
        assertEquals(helper.symbol, model.lookup("helper_value"))
        assertEquals(cSource.id, helper.symbol.origin.primaryRange!!.file)
        assertEquals(cText.indexOf("int helper_value"), helper.symbol.origin.primaryRange!!.startOffset)
    }

    @Test
    fun sourceCStructTypedefRetainsTargetLayoutFields() {
        val cText = "typedef struct Node { int value; struct Node *next; } Node;"
        val cSource = SourceFile(SourceFileId(28), Path.of("node.h"), cText, 1)
        val text = "int read(Node* node) { return node->value; }"
        val source = SourceFile(SourceFileId(29), Path.of("main.cp"), text, 1)
        val parsed = Parser(Lexer().lex(source)).parse()
        val result = SemanticAnalyzer().analyze(
            AstBuilder().build(parsed.syntax),
            foreignSources = listOf(CSourceUnit(cSource, "c.model"))
        )

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val type = result.model!!.foreignTypes.getValue("Node")
        val structure = assertNotNull(type.underlyingType as? StructType)
        assertEquals(listOf("value", "next"), structure.fields.map { it.symbol.name })
        assertEquals("int", structure.fields.first().symbol.type.name)
    }

    @Test
    fun opaqueCStructAllowsPointersButRejectsByValueUse() {
        val cSource = SourceFile(SourceFileId(30), Path.of("opaque.h"), "struct Opaque;", 1)
        val pointerSource = SourceFile(
            SourceFileId(31), Path.of("pointer.cp"),
            "int inspect(Opaque* value) { return 0; }", 1
        )
        val pointerProgram = AstBuilder().build(Parser(Lexer().lex(pointerSource)).parse().syntax)
        assertEquals(1, (pointerProgram.declarations.filterIsInstance<AstFunction>().single().parameters.single().type).pointerDepth)
        val pointerResult = SemanticAnalyzer().analyze(
            pointerProgram,
            foreignSources = listOf(CSourceUnit(cSource, "c.opaque"))
        )
        assertTrue(pointerResult.isSuccessful, pointerResult.diagnostics.joinToString())

        val byValueSource = SourceFile(
            SourceFileId(32), Path.of("by-value.cp"),
            "int inspect(Opaque value) { return 0; }", 1
        )
        val byValueResult = SemanticAnalyzer().analyze(
            AstBuilder().build(Parser(Lexer().lex(byValueSource)).parse().syntax),
            foreignSources = listOf(CSourceUnit(cSource, "c.opaque"))
        )
        assertTrue(byValueResult.diagnostics.any { it.code == "SEM414" }, byValueResult.diagnostics.joinToString())
    }

    @Test
    fun cCallbackTypedefBecomesAFunctionPointerType() {
        val cSource = SourceFile(
            SourceFileId(33), Path.of("callback.h"),
            "typedef int (*callback_t)(const char *text, long count);", 1
        )
        val source = SourceFile(
            SourceFileId(34), Path.of("callback.cp"),
            "int invoke(callback_t callback) { return 0; }", 1
        )
        val result = SemanticAnalyzer().analyze(
            AstBuilder().build(Parser(Lexer().lex(source)).parse().syntax),
            foreignSources = listOf(CSourceUnit(cSource, "c.callback"))
        )

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val callback = assertNotNull(result.model!!.foreignTypes.getValue("callback_t").underlyingType as? PointerType)
        assertEquals(listOf("char*", "long"), (callback.pointee as FunctionType).parameterTypes.map { it.name })
    }

    @Test
    fun cyclicCTypeAliasesReceiveUnsupportedDeclarationDiagnostic() {
        val cSource = SourceFile(
            SourceFileId(35), Path.of("cycle.h"),
            "typedef second_t first_t; typedef first_t second_t;", 1
        )
        val source = SourceFile(SourceFileId(36), Path.of("cycle.cp"), "int main() { return 0; }", 1)
        val result = SemanticAnalyzer().analyze(
            AstBuilder().build(Parser(Lexer().lex(source)).parse().syntax),
            foreignSources = listOf(CSourceUnit(cSource, "c.cycle"))
        )

        assertTrue(result.diagnostics.any { it.code == "SEM413" && it.message.contains("cyclic") })
    }

    @Test
    fun compilerSpecificBitFieldLayoutIsRejectedExplicitly() {
        val cSource = SourceFile(
            SourceFileId(37), Path.of("bits.h"),
            "struct Flags { unsigned int ready:1; };", 1
        )
        val source = SourceFile(SourceFileId(38), Path.of("bits.cp"), "int main() { return 0; }", 1)
        val result = SemanticAnalyzer().analyze(
            AstBuilder().build(Parser(Lexer().lex(source)).parse().syntax),
            foreignSources = listOf(CSourceUnit(cSource, "c.bits"))
        )

        assertTrue(result.diagnostics.any { it.code == "SEM413" && it.message.contains("bit-field") })
    }

    @Test
    fun safeCObjectMacrosAreTypedForeignConstants() {
        val cSource = SourceFile(SourceFileId(39), Path.of("values.h"), "", 1)
        val source = SourceFile(
            SourceFileId(40), Path.of("values.cp"),
            "int main() { return ANSWER; }", 1
        )
        val result = SemanticAnalyzer().analyze(
            AstBuilder().build(Parser(Lexer().lex(source)).parse().syntax),
            foreignSources = listOf(
                CSourceUnit(
                    cSource,
                    "c.values",
                    listOf(
                        CHeaderMacro("ANSWER", null, "42", cSource.path, 2),
                        CHeaderMacro("RATE", null, "1.5", cSource.path, 3),
                        CHeaderMacro("TITLE", null, "\"hello\"", cSource.path, 4)
                    )
                )
            )
        )

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val model = result.model!!
        val answer = model.foreignGlobals.getValue("ANSWER")
        assertEquals("int", answer.type.name)
        assertEquals("42", answer.constantExpression)
        assertEquals(cSource.path, answer.externalSource)
        assertEquals(2, answer.externalLine)
        assertEquals("double", model.foreignGlobals.getValue("RATE").type.name)
        assertEquals("char*", model.foreignGlobals.getValue("TITLE").type.name)
    }

    @Test
    fun cHeaderGlobalDeclarationsExcludeStaticAndFunctionLocalState() {
        val cText = """
            extern const char *label;
            extern int *global_pointer;
            int global_count = 3;
            static int private_header_state;
            int helper(void) { int function_local = 7; return function_local; }
        """.trimIndent()
        val cSource = SourceFile(SourceFileId(41), Path.of("globals.h"), cText, 1)
        val source = SourceFile(SourceFileId(42), Path.of("globals.cp"), "int main() { return 0; }", 1)
        val result = SemanticAnalyzer().analyze(
            AstBuilder().build(Parser(Lexer().lex(source)).parse().syntax),
            foreignSources = listOf(CSourceUnit(cSource, "c.globals"))
        )

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val globals = result.model!!.foreignGlobals
        assertEquals("char*", globals.getValue("label").type.name)
        assertEquals("int*", globals.getValue("global_pointer").type.name)
        assertEquals("int", globals.getValue("global_count").type.name)
        assertTrue("private_header_state" !in globals)
        assertTrue("function_local" !in globals)
    }

    @Test
    fun discoveredForeignFunctionRetainsOriginalHeaderPathAndLine() {
        val headerPath = Path.of("temporary-sdk/include/demo/coucou.h").toAbsolutePath().normalize()
        val sourcePath = Path.of("temporary-sdk/preprocessed/coucou.i").toAbsolutePath().normalize()
        val cSource = SourceFile(
            SourceFileId(43), sourcePath,
            "int coucou(void) { int body_local = 1; return body_local; }", 1
        )
        val source = SourceFile(SourceFileId(44), Path.of("main.cp"), "int main() { return 0; }", 1)
        val result = SemanticAnalyzer().analyze(
            AstBuilder().build(Parser(Lexer().lex(source)).parse().syntax),
            foreignSources = listOf(
                CSourceUnit(
                    cSource,
                    "c.demo.coucou",
                    sourceLineOrigins = mapOf(1 to CHeaderSourceLocation(headerPath, 17))
                )
            )
        )

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val symbol = result.model!!.foreignFunctions.getValue("coucou").symbol
        assertEquals(headerPath, symbol.externalSource)
        assertEquals(17, symbol.externalLine)
    }

    @Test
    fun unsupportedHeaderPreprocessorContentRemainsDiagnostic() {
        val service = CHeaderImportService(mapOf("c.test" to "#define MAGIC 1\n"))
        val text = "import { MAGIC } from c.test; int main() { return 0; }"
        val source = SourceFile(SourceFileId(7), Path.of("header-boundary.cp"), text, 1)
        val parsed = Parser(Lexer().lex(source)).parse()

        val result = SemanticAnalyzer(service).analyze(AstBuilder().build(parsed.syntax))
        assertTrue(result.diagnostics.any { it.code == "SEM409" }, result.diagnostics.joinToString())
        assertTrue(!result.isSuccessful)
    }

    @Test
    fun packageAndModuleNamesFormDeterministicQualifiedSymbols() {
        val text = "package demo.core; pub int main() { return 0; }"
        val source = SourceFile(SourceFileId(6), Path.of("package-symbols.cp"), text, 1)
        val parsed = Parser(Lexer().lex(source)).parse()
        assertTrue(parsed.diagnostics.isEmpty(), parsed.diagnostics.joinToString())

        val result = SemanticAnalyzer().analyze(AstBuilder().build(parsed.syntax))
        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val model = result.model!!
        assertEquals("demo.core", model.modulePackages["<main>"])
        assertEquals(setOf("<main>"), model.packageModules["demo.core"])
        assertEquals("demo.core::<main>::main", model.functions.getValue("main").symbol.qualifiedName.value)
        assertEquals(Visibility.PUBLIC, model.functions.getValue("main").symbol.visibility)
    }

    @Test
    fun aliasesResolveAcrossSourceOrder() {
        val text = """
            count_t value;
            typedef int count_t;

            int main() {
                return value;
            }
        """.trimIndent()
        val source = SourceFile(SourceFileId(3), Path.of("forward-alias.cp"), text, 1)
        val parsed = Parser(Lexer().lex(source)).parse()
        assertTrue(parsed.diagnostics.isEmpty(), parsed.diagnostics.joinToString())

        val result = SemanticAnalyzer().analyze(AstBuilder().build(parsed.syntax))
        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        assertEquals("count_t", result.model!!.symbols.first { it.name == "value" }.type.name)
    }

    @Test
    fun cyclicAliasesProduceAStableDiagnostic() {
        val text = "typedef b_t a_t; typedef a_t b_t; int main() { return 0; }"
        val source = SourceFile(SourceFileId(4), Path.of("cyclic-alias.cp"), text, 1)
        val parsed = Parser(Lexer().lex(source)).parse()

        val result = SemanticAnalyzer().analyze(AstBuilder().build(parsed.syntax))
        assertTrue(result.diagnostics.any { it.code == "SEM110" }, result.diagnostics.joinToString())
        assertTrue(!result.isSuccessful)
    }

    @Test
    fun callResolutionChecksArgumentTypes() {
        val text = """
            int identity(int value) {
                return value;
            }

            int main() {
                return identity("wrong");
            }
        """.trimIndent()
        val source = SourceFile(SourceFileId(5), Path.of("call-types.cp"), text, 1)
        val parsed = Parser(Lexer().lex(source)).parse()

        val result = SemanticAnalyzer().analyze(AstBuilder().build(parsed.syntax))
        assertTrue(result.diagnostics.any { it.code == "SEM306" }, result.diagnostics.joinToString())
        assertTrue(!result.isSuccessful)
    }

    @Test
    fun nonAssignableExpressionProducesAssignmentDiagnostic() {
        val text = "int main() { return 1 = 2; }"
        val source = SourceFile(SourceFileId(12), Path.of("invalid-assignment.cp"), text, 1)
        val parsed = Parser(Lexer().lex(source)).parse()

        val result = SemanticAnalyzer().analyze(AstBuilder().build(parsed.syntax))

        assertTrue(result.diagnostics.any { it.code == "SEM307" }, result.diagnostics.joinToString())
        assertTrue(!result.isSuccessful)
    }

    @Test
    fun initializerTypesAndPointerArithmeticAreValidated() {
        val text = """
            int invalid_global = "wrong";

            int main() {
                int value = "wrong";
                return value * &value;
            }
        """.trimIndent()
        val source = SourceFile(SourceFileId(13), Path.of("initializer-pointer-errors.cp"), text, 1)
        val parsed = Parser(Lexer().lex(source)).parse()

        val result = SemanticAnalyzer().analyze(AstBuilder().build(parsed.syntax))

        assertTrue(result.diagnostics.count { it.code == "SEM308" } >= 2, result.diagnostics.joinToString())
        assertTrue(result.diagnostics.any { it.code == "SEM316" }, result.diagnostics.joinToString())
        assertTrue(!result.isSuccessful)
    }

    @Test
    fun equivalentAliasAndPointerSpellingSharesCanonicalTypeIdentity() {
        val text = """
            typedef int count_t;
            int* pointer_a;
            count_t* pointer_b;

            int one(int value) {
                return value;
            }

            count_t two(count_t value) {
                return value;
            }

            int main() {
                return one(two(3));
            }
        """.trimIndent()
        val source = SourceFile(SourceFileId(2), Path.of("canonical-types.cp"), text, 1)
        val parsed = Parser(Lexer().lex(source)).parse()
        assertTrue(parsed.diagnostics.isEmpty(), parsed.diagnostics.joinToString())

        val result = SemanticAnalyzer().analyze(AstBuilder().build(parsed.syntax))
        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val model = result.model!!
        val pointerA = model.symbols.first { it.name == "pointer_a" }.type
        val pointerB = model.symbols.first { it.name == "pointer_b" }.type
        assertEquals(model.canonicalTypeId(pointerA), model.canonicalTypeId(pointerB))
        assertEquals(
            model.canonicalTypeId(model.functions.getValue("one").signature),
            model.canonicalTypeId(model.functions.getValue("two").signature)
        )
    }

    @Test
    fun primitiveCanonicalIdentityPreservesSignednessAndIntegerRank() {
        val text = """
            char plain_char;
            signed char signed_char;
            unsigned char unsigned_char;
            short signed_short;
            unsigned short unsigned_short;
            int signed_int;
            unsigned int unsigned_int;
            long signed_long;
            unsigned long unsigned_long;
            long long signed_long_long;
            unsigned long long unsigned_long_long;
            long first(long int value) { return value; }
            long second(int long value) { return value; }
            long third(long long value) { return value; }
            int main() { return 0; }
        """.trimIndent()
        val source = SourceFile(SourceFileId(23), Path.of("primitive-identities.cp"), text, 1)
        val parsed = Parser(Lexer().lex(source)).parse()
        assertTrue(parsed.diagnostics.isEmpty(), parsed.diagnostics.joinToString())

        val result = SemanticAnalyzer().analyze(AstBuilder().build(parsed.syntax))

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val model = result.model!!
        fun canonicalId(name: String) = model.canonicalTypeId(model.symbols.first { it.name == name }.type)

        assertNotEquals(canonicalId("plain_char"), canonicalId("signed_char"))
        assertNotEquals(canonicalId("signed_char"), canonicalId("unsigned_char"))
        assertNotEquals(canonicalId("signed_short"), canonicalId("unsigned_short"))
        assertNotEquals(canonicalId("signed_short"), canonicalId("signed_int"))
        assertNotEquals(canonicalId("signed_int"), canonicalId("unsigned_int"))
        assertNotEquals(canonicalId("signed_int"), canonicalId("signed_long"))
        assertNotEquals(canonicalId("signed_long"), canonicalId("unsigned_long"))
        assertNotEquals(canonicalId("signed_long"), canonicalId("signed_long_long"))
        assertNotEquals(canonicalId("unsigned_long"), canonicalId("unsigned_long_long"))
        assertEquals(
            canonicalId("signed_int"),
            model.canonicalTypeId(PrimitiveType(TypeId(-100), "signed int"))
        )
        assertEquals(
            canonicalId("signed_short"),
            model.canonicalTypeId(PrimitiveType(TypeId(-101), "signed short"))
        )
        assertEquals(
            model.canonicalTypeId(model.functions.getValue("first").signature),
            model.canonicalTypeId(model.functions.getValue("second").signature)
        )
        assertNotEquals(
            model.canonicalTypeId(model.functions.getValue("first").signature),
            model.canonicalTypeId(model.functions.getValue("third").signature)
        )
    }

    @Test
    fun pointerCompatibilityDoesNotCollapseDistinctIntegerTypes() {
        val text = """
            int takes_signed_char(signed char* value) { return 0; }
            int takes_long(long* value) { return 0; }
            int main() {
                char plain;
                long long wide;
                takes_signed_char(&plain);
                takes_long(&wide);
                return 0;
            }
        """.trimIndent()
        val source = SourceFile(SourceFileId(24), Path.of("primitive-pointer-compatibility.cp"), text, 1)
        val parsed = Parser(Lexer().lex(source)).parse()
        assertTrue(parsed.diagnostics.isEmpty(), parsed.diagnostics.joinToString())

        val result = SemanticAnalyzer().analyze(AstBuilder().build(parsed.syntax))

        assertEquals(2, result.diagnostics.count { it.code == "SEM306" }, result.diagnostics.joinToString())
    }

    @Test
    fun comptimeTypeIdentityRetainsAliasAndCanonicalIds() {
        val text = """
            typedef int count_t;
            struct item { int value; };
            count_t count;
            struct item item_value;
            int main() { return count + item_value.value; }
        """.trimIndent()
        val source = SourceFile(SourceFileId(22), Path.of("comptime-type-identity.cp"), text, 1)
        val parsed = Parser(Lexer().lex(source)).parse()

        val result = SemanticAnalyzer().analyze(AstBuilder().build(parsed.syntax))

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val model = result.model!!
        val alias = model.resolveComptimeTypeIdentity("count_t")!!
        val primitive = model.resolveComptimeTypeIdentity("int")!!
        val structure = model.resolveComptimeTypeIdentity("struct item")!!
        assertNotEquals(alias.typeId, alias.canonicalTypeId)
        assertEquals(primitive.canonicalTypeId, alias.canonicalTypeId)
        assertEquals("int", alias.canonicalText)
        assertEquals(structure.typeId, structure.canonicalTypeId)
        assertEquals("item", structure.canonicalText)
    }

    @Test
    fun functionDeclarationsProduceCanonicalFunctionTypes() {
        val text = """
            int add(int left, int right) {
                return left + right;
            }

            int main() {
                return add(1, 2);
            }
        """.trimIndent()
        val source = SourceFile(SourceFileId(1), Path.of("function-types.cp"), text, 1)
        val parsed = Parser(Lexer().lex(source)).parse()
        assertTrue(parsed.diagnostics.isEmpty(), parsed.diagnostics.joinToString())

        val result = SemanticAnalyzer().analyze(AstBuilder().build(parsed.syntax))
        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
        val add = result.model!!.functions.getValue("add")
        val signature = assertIs<FunctionType>(add.symbol.type)
        assertEquals(listOf("int", "int"), signature.parameterTypes.map { it.name })
        assertEquals("int", signature.returnType.name)
        assertEquals(signature, add.signature)
    }

    @Test
    fun functionPointerPreservesPointerReturnType() {
        val text = """
            void* identity(void* context) { return context; }
            void* invoke(void* (*callback)(void* context), void* context) {
                return callback(context);
            }
            int main() { return invoke(identity, (void*)0) != (void*)0; }
        """.trimIndent()
        val source = SourceFile(SourceFileId(30), Path.of("function-pointer-pointer-return.cp"), text, 1)
        val parsed = Parser(Lexer().lex(source)).parse()
        assertTrue(parsed.diagnostics.isEmpty(), parsed.diagnostics.joinToString())

        val result = SemanticAnalyzer().analyze(AstBuilder().build(parsed.syntax))

        assertTrue(result.isSuccessful, result.diagnostics.joinToString())
    }
}
