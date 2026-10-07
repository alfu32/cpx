package cplus.backend

import cplus.core.AstBuilder
import cplus.core.AstModule
import cplus.core.AstProgram
import cplus.core.Lexer
import cplus.core.Parser
import cplus.core.SourceFile
import cplus.core.SourceFileId
import cplus.semantic.SemanticAnalyzer
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class CLowererModuleTypeTest {
    @Test
    fun emitsOriginalCNamesForSelectiveAndQualifiedImportedTypes() {
        val clientText = """
            import { Point as LocalPoint, Coord as LocalCoord } from "./types.cp";
            import types as geo;
            typedef geo.Point* PointRef;
            typedef LocalPoint* ImportedPointRef;
            struct Holder {
                LocalPoint* importedPoint;
                geo.Point* qualifiedPoint;
                LocalCoord importedCoord;
                geo.Coord qualifiedCoord;
            };
            int main() {
                LocalPoint imported;
                geo.Point qualified;
                ImportedPointRef importedPointer = (LocalPoint*) &imported;
                LocalCoord importedValue = (LocalCoord) 1;
                geo.Coord qualifiedValue = 2;
                return imported.x + qualified.x + importedValue + qualifiedValue + importedPointer->x;
            }
        """.trimIndent()
        val providerText = """
            pub struct Point { int x; };
            pub typedef int Coord;
        """.trimIndent()
        val clientSource = SourceFile(SourceFileId(50), Path.of("client.cp"), clientText, 1)
        val providerSource = SourceFile(SourceFileId(51), Path.of("types.cp"), providerText, 1)
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
        val semantic = SemanticAnalyzer().analyze(program, knownModules = setOf("types"))
        assertTrue(semantic.isSuccessful, semantic.diagnostics.joinToString())

        val lowered = CLowerer(semantic.model!!).lower(program)
        assertTrue(lowered.diagnostics.isEmpty(), lowered.diagnostics.joinToString())
        val generated = CEmitter().emit(lowered.unit).text

        assertTrue("struct Point* importedPoint;" in generated, generated)
        assertTrue("struct Point* qualifiedPoint;" in generated, generated)
        assertTrue("Coord importedCoord;" in generated, generated)
        assertTrue("Coord qualifiedCoord;" in generated, generated)
        assertTrue("typedef int Coord;" in generated, generated)
        assertTrue("typedef struct Point* PointRef;" in generated, generated)
        assertTrue("typedef struct Point* ImportedPointRef;" in generated, generated)
        assertTrue(generated.indexOf("typedef int Coord;") < generated.indexOf("struct Holder {"), generated)
    }
}
