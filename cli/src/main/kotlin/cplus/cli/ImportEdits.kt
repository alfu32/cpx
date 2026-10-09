package cplus.cli

import cplus.core.Lexer
import cplus.core.Parser
import cplus.core.SourceFile
import cplus.core.SourceFileId
import cplus.core.SyntaxAlias
import cplus.core.SyntaxComptimeFunction
import cplus.core.SyntaxDeclaration
import cplus.core.SyntaxEnum
import cplus.core.SyntaxFunction
import cplus.core.SyntaxGlobalVariable
import cplus.core.SyntaxImport
import cplus.core.SyntaxPackage
import cplus.core.SyntaxStruct
import cplus.core.SyntaxUnion
import cplus.core.Token
import cplus.core.TokenKind
import java.nio.file.Path

internal data class ImportTextEdit(val range: ImportTextRange, val newText: String)

internal data class ImportEditPlan(
    val importEdit: ImportTextEdit?,
    val symbolReplacement: String
)

/** Pure source edit construction shared by completion and import code actions. */
internal object ImportEdits {
    private val identifier = Regex("[A-Za-z_][A-Za-z0-9_]*")

    fun build(text: String, provider: String, importedName: String): ImportEditPlan? {
        if (!identifier.matches(importedName) || provider.isBlank() || provider.any { it == '\n' || it == '\r' || it == ';' }) {
            return null
        }
        val source = SourceFile(SourceFileId(0), Path.of("lsp-import-edit.cp"), text, 0)
        val lexed = Lexer().lex(source)
        val parsed = Parser(lexed).parse()
        val imports = parsed.syntax.declarations.filterIsInstance<SyntaxImport>()
        if (parsed.diagnostics.any { diagnostic ->
                diagnostic.range?.let { range -> imports.any { it.range.startOffset <= range.endOffset && range.startOffset <= it.range.endOffset } } == true
            }) return null

        val target = normalizeProvider(provider)
        val matching = imports.filter { normalizeProvider(it.module) == target }
        matching.firstOrNull { it.names.isEmpty() }?.let { moduleImport ->
            val alias = moduleImport.alias
            if (alias != null) return ImportEditPlan(null, "$alias.$importedName")
            if (!moduleImport.module.startsWith("./") && !moduleImport.module.startsWith("../")) {
                return ImportEditPlan(null, "${moduleImport.module}.$importedName")
            }
        }
        matching.firstOrNull { importedName in it.names }?.let { selective ->
            return ImportEditPlan(null, selective.nameAliases[importedName] ?: importedName)
        }

        val occupied = declaredNames(parsed.syntax.declarations) + imports.flatMap { import ->
            import.names.flatMap { name -> listOf(name, import.nameAliases[name] ?: name) } + listOfNotNull(import.alias)
        }
        if (importedName in occupied) return null

        val mergeTarget = matching.firstOrNull { it.names.isNotEmpty() }
        if (mergeTarget != null) {
            val tokens = lexed.tokens.filter { token ->
                token.range.startOffset >= mergeTarget.range.startOffset && token.range.endOffset <= mergeTarget.range.endOffset
            }
            val open = tokens.firstOrNull { it.isLexeme("{") } ?: return null
            val close = tokens.firstOrNull { it.isLexeme("}") && it.range.startOffset > open.range.startOffset } ?: return null
            val interior = text.substring(open.range.endOffset, close.range.startOffset)
            val commentOrMultiline = interior.contains("//") || interior.contains("/*") ||
                interior.contains('\n') || interior.contains('\r')
            if (!commentOrMultiline) {
                var insertion = close.range.startOffset
                while (insertion > open.range.endOffset && text[insertion - 1].isWhitespace()) insertion--
                val edit = ImportTextEdit(ImportTextRange(insertion, insertion), ", $importedName")
                return ImportEditPlan(edit, importedName)
            }
        }

        val insertion = insertionOffset(text, parsed.syntax.declarations)
        val lineEnding = lineEnding(text)
        val prefix = if (insertion > 0 && text[insertion - 1] != '\n' && text[insertion - 1] != '\r') lineEnding else ""
        val edit = ImportTextEdit(
            ImportTextRange(insertion, insertion),
            "${prefix}import { $importedName } from ${formatProvider(provider)};$lineEnding"
        )
        return ImportEditPlan(edit, importedName)
    }

    private fun declaredNames(declarations: List<SyntaxDeclaration>): Set<String> = buildSet {
        declarations.forEach { declaration ->
            when (declaration) {
                is SyntaxFunction -> add(declaration.name)
                is SyntaxStruct -> add(declaration.name)
                is SyntaxUnion -> add(declaration.name)
                is SyntaxEnum -> {
                    add(declaration.name)
                    declaration.values.forEach { add(it.name) }
                }
                is SyntaxAlias -> add(declaration.name)
                is SyntaxGlobalVariable -> add(declaration.name)
                is SyntaxComptimeFunction -> add(declaration.name)
                is SyntaxPackage, is SyntaxImport -> Unit
                else -> Unit
            }
        }
    }

    private fun insertionOffset(text: String, declarations: List<SyntaxDeclaration>): Int {
        val anchor = declarations.filter { it is SyntaxPackage || it is SyntaxImport }
            .maxOfOrNull { it.range.endOffset } ?: return if (text.startsWith('\uFEFF')) 1 else 0
        val newline = text.indexOfAny(charArrayOf('\r', '\n'), startIndex = anchor)
        if (newline < 0) return text.length
        return if (text[newline] == '\r' && text.getOrNull(newline + 1) == '\n') newline + 2 else newline + 1
    }

    private fun lineEnding(text: String): String {
        val newline = text.indexOfAny(charArrayOf('\r', '\n'))
        if (newline < 0) return "\n"
        return if (text[newline] == '\r' && text.getOrNull(newline + 1) == '\n') "\r\n" else text[newline].toString()
    }

    private fun normalizeProvider(provider: String): String {
        val normalized = provider.removeSurrounding("\"", "\"").replace('\\', '/')
        return when {
            normalized.startsWith("./") || normalized.startsWith("../") || normalized.startsWith("/") -> normalized
            normalized.startsWith("stdlib/") -> "std." + normalized.removePrefix("stdlib/").replace('/', '.')
            normalized.startsWith("std/") -> "std." + normalized.removePrefix("std/").replace('/', '.')
            normalized.startsWith("c/") -> "c." + normalized.removePrefix("c/").replace('/', '.')
            else -> normalized.replace('/', '.')
        }
    }

    private fun formatProvider(provider: String): String {
        if (provider.startsWith('"') && provider.endsWith('"')) return provider
        if (!(provider.startsWith("./") || provider.startsWith("../")) || provider.none(Char::isWhitespace)) return provider
        return "\"${provider.replace("\\", "\\\\").replace("\"", "\\\"")}\""
    }
}
