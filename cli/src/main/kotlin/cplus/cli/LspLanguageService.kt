package cplus.cli

import cplus.compiler.CompileResult
import cplus.compiler.ImportExport
import cplus.compiler.ImportExportKind
import cplus.compiler.ImportIndexResult
import cplus.core.LineIndex
import cplus.core.Origin
import cplus.core.SourceRange
import cplus.core.Lexer
import cplus.core.SourceFile
import cplus.core.SourceFileId
import cplus.core.TokenKind
import cplus.core.Token
import cplus.core.AstIdentifier
import cplus.core.AstMemberAccess
import cplus.semantic.AliasType
import cplus.semantic.ArrayType
import cplus.semantic.CType
import cplus.semantic.FunctionSymbol
import cplus.semantic.PointerType
import cplus.semantic.ReferenceKind
import cplus.semantic.StructType
import cplus.semantic.Symbol
import cplus.semantic.SymbolKind
import cplus.semantic.UnionType
import cplus.semantic.MethodSymbol
import java.nio.file.Path

internal data class CompletionItem(
    val label: String,
    val kind: Int,
    val detail: String,
    val documentation: String? = null,
    val replacementRange: ImportTextRange? = null,
    val insertText: String? = null,
    val additionalTextEdits: List<ImportTextEdit> = emptyList(),
    val sortText: String? = null
)

internal data class HoverInfo(
    val markdown: String,
    val range: SourceRange
)

internal data class NavigationInfo(
    val definition: SourceRange?,
    val references: List<SourceRange>,
    val externalDefinition: ExternalDefinition? = null
)

internal data class ExternalDefinition(val path: Path, val line: Int, val name: String)

internal data class ImportQuickFix(
    val title: String,
    val provider: String,
    val edits: List<ImportTextEdit>,
    val diagnosticCode: String,
    val diagnosticMessage: String,
    val diagnosticRange: ImportTextRange
)

internal data class SignatureParameterInfo(
    val label: String,
    val documentation: String? = null
)

internal data class SignatureInfo(
    val label: String,
    val parameters: List<SignatureParameterInfo>,
    val activeParameter: Int
)

internal object LspLanguageService {
    fun importQuickFixes(
        result: CompileResult,
        text: String,
        sourcePath: Path,
        importIndex: ImportIndexResult,
        requestedRange: ImportTextRange? = null
    ): List<ImportQuickFix> {
        val model = result.semanticModel ?: return emptyList()
        val artifact = artifactFor(result, sourcePath) ?: return emptyList()
        val sourceDiagnostics = result.diagnostics.filter { diagnostic ->
            val range = diagnostic.range
            range?.file == artifact.source.id && (requestedRange == null ||
                range.startOffset <= requestedRange.endOffset && requestedRange.startOffset <= range.endOffset)
        }
        if (sourceDiagnostics.any { it.code?.startsWith("PARSE") == true || it.code?.startsWith("LEX") == true }) {
            return emptyList()
        }
        val actions = mutableListOf<ImportQuickFix>()
        sourceDiagnostics.forEach { diagnostic ->
            val unresolved = unresolvedImportName(diagnostic.code, diagnostic.message) ?: return@forEach
            val range = diagnostic.range ?: return@forEach
            val token = artifact.lexed.tokens.singleOrNull { candidate ->
                candidate.kind == TokenKind.IDENTIFIER && candidate.lexeme == unresolved &&
                    candidate.range.startOffset >= range.startOffset && candidate.range.endOffset <= range.endOffset
            } ?: return@forEach
            val callable = diagnostic.code == "SEM301" && model.nodeIds.values
                .filterIsInstance<cplus.core.AstCall>()
                .any { call -> call.callee is AstIdentifier && call.callee.origin.primaryRange == token.range }
            val compatibleKinds = when {
                diagnostic.code in TYPE_DIAGNOSTICS -> TYPE_EXPORTS
                diagnostic.code == "SEM301" && callable -> CALLABLE_EXPORTS
                diagnostic.code == "SEM301" -> VALUE_EXPORTS
                else -> return@forEach
            }
            importIndex.exports.asSequence()
                .filter { it.topLevelBinding && it.name == unresolved && it.kind in compatibleKinds }
                .forEach { export ->
                    val plan = ImportEdits.build(text, export.importReference, export.name) ?: return@forEach
                    val edits = buildList {
                        plan.importEdit?.let(::add)
                        if (plan.symbolReplacement != unresolved) {
                            add(ImportTextEdit(ImportTextRange(token.range.startOffset, token.range.endOffset), plan.symbolReplacement))
                        }
                    }
                    if (edits.isNotEmpty()) {
                        actions += ImportQuickFix(
                            "Import '$unresolved' from ${export.provider}",
                            export.provider,
                            edits,
                            diagnostic.code.orEmpty(),
                            diagnostic.message,
                            ImportTextRange(range.startOffset, range.endOffset)
                        )
                    }
                }
        }
        return actions.distinctBy { it.provider to it.edits }.sortedBy { it.provider }
    }

    fun importCompletion(
        context: ImportCompletionContext,
        index: ImportIndexResult,
        canonicalProvider: String? = context.provider,
        pathProviders: List<String> = emptyList()
    ): List<CompletionItem> {
        val candidates = when (context.kind) {
            ImportCompletionKind.PROVIDER -> {
                val moduleItems = index.exports.filter(ImportExport::topLevelBinding)
                    .groupBy(ImportExport::provider).map { (provider, exports) ->
                    CompletionItem(
                        label = provider,
                        kind = 9,
                        detail = "${exports.size} public exports",
                        documentation = exports.take(8).joinToString("\n") { "`${it.signature}`" },
                        replacementRange = context.replacementRange,
                        insertText = provider
                    )
                }
                val pathItems = pathProviders.map { reference ->
                    CompletionItem(
                        label = reference,
                        kind = 9,
                        detail = "C+ source module",
                        replacementRange = context.replacementRange,
                        insertText = reference
                    )
                }
                (moduleItems + pathItems).filter { it.label.startsWith(context.prefix) }
            }
            ImportCompletionKind.SELECTIVE_NAME -> {
                val provider = canonicalProvider ?: return emptyList()
                index.exports.asSequence()
                    .filter { it.topLevelBinding && it.provider == provider && it.name !in context.existingNames }
                    .filter { it.name.startsWith(context.prefix) }
                    .map { export ->
                        CompletionItem(
                            label = export.name,
                            kind = exportCompletionKind(export.kind),
                            detail = "${export.signature} — ${export.provider}",
                            documentation = export.documentation,
                            replacementRange = context.replacementRange,
                            insertText = export.name
                        )
                    }
                    .toList()
            }
        }
        return candidates.distinctBy { Triple(it.label, it.kind, it.detail) }.sortedBy { it.label }
    }

    fun completion(
        result: CompileResult,
        text: String,
        position: LspPosition,
        importIndex: ImportIndexResult? = null,
        sourcePath: Path? = null
    ): List<CompletionItem> {
        val model = result.semanticModel
        val offset = offsetAt(text, position) ?: return emptyList()
        val before = text.substring(0, offset)
        val member = Regex("([A-Za-z_][A-Za-z0-9_]*)\\s*(?:\\.|->)\\s*([A-Za-z_]\\w*)?$").find(before)
        val prefix = member?.groupValues?.getOrNull(2).orEmpty()
        val candidates = if (member != null && model != null) {
            memberCandidates(
                model,
                member.groupValues[1],
                prefix,
                moduleForSource(model, artifactFor(result, sourcePath)?.source?.id)
            )
        } else if (member == null && model != null) {
            val identifier = Regex("[A-Za-z_]\\w*$").find(before)?.value.orEmpty()
            model.symbols
                .asSequence()
                .filter { it.name.startsWith(identifier) }
                .map { CompletionItem(it.name, completionKind(it.kind), it.type.name, sortText = "0_${it.name}") }
                .toList()
        } else emptyList()
        val identifierContext = if (member == null) ImportCompletionContextFinder.identifier(text, position) else null
        val unimported = if (identifierContext == null || importIndex == null) emptyList() else {
            val visibleNames = visibleNames(result, model, sourcePath)
            importIndex.exports.asSequence()
                .filter { export -> export.topLevelBinding && export.name !in visibleNames }
                .filter { export -> export.name.startsWith(identifierContext.prefix) }
                .mapNotNull { export ->
                    val editPlan = ImportEdits.build(text, export.importReference, export.name) ?: return@mapNotNull null
                    CompletionItem(
                        label = export.name,
                        kind = exportCompletionKind(export.kind),
                        detail = "${export.signature} — ${export.provider}",
                        documentation = export.documentation,
                        replacementRange = identifierContext.replacementRange,
                        insertText = editPlan.symbolReplacement,
                        additionalTextEdits = listOfNotNull(editPlan.importEdit),
                        sortText = "1_${export.provider}_${export.name}"
                    )
                }
                .toList()
        }
        return (candidates + unimported)
            .distinctBy { Triple(it.label, it.kind, it.detail) }
            .sortedWith(compareBy({ it.sortText ?: "0_${it.label}" }, { it.label }, { it.detail }))
    }

    private fun visibleNames(result: CompileResult, model: cplus.semantic.SemanticModel?, sourcePath: Path?): Set<String> {
        val artifact = artifactFor(result, sourcePath) ?: return emptySet()
        val sourceId = artifact.source.id
        val names = model?.symbols.orEmpty()
            .filter { it.origin.primaryRange?.file == sourceId }
            .map(Symbol::name)
            .toMutableSet()
        val declarations = model?.program?.modules?.firstOrNull { module ->
            module.declarations.any { it.origin.primaryRange?.file == sourceId }
        }?.declarations ?: model?.program?.declarations.orEmpty()
        declarations.filterIsInstance<cplus.core.AstImport>().forEach { import ->
            if (import.names.isNotEmpty()) {
                import.names.forEach { name -> names += import.nameAliases[name] ?: name }
            }
            import.alias?.let(names::add)
        }
        return names
    }

    fun hover(result: CompileResult, text: String, position: LspPosition, sourcePath: Path? = null): HoverInfo? {
        val model = result.semanticModel ?: return null
        val artifact = artifactFor(result, sourcePath) ?: return null
        val offset = offsetAt(text, position) ?: return null
        val token = artifact.lexed.tokens.firstOrNull {
            it.range.startOffset <= offset && offset < it.range.endOffset
        } ?: return null
        val symbol = symbolAt(model, token) ?: return null
        val kind = symbol.kind.name.lowercase().replace('_', ' ')
        return HoverInfo(
            "```cplus\n$kind ${symbol.name}: ${symbol.type.name}\n```",
            token.range
        )
    }

    fun navigation(
        result: CompileResult,
        text: String,
        position: LspPosition,
        includeDeclaration: Boolean = true,
        sourcePath: Path? = null,
        sourcePathFor: (SourceFileId) -> Path? = { null },
        sourceTextFor: (SourceFileId) -> String? = { null }
    ): NavigationInfo? {
        val model = result.semanticModel ?: return null
        val artifact = artifactFor(result, sourcePath) ?: return null
        val offset = offsetAt(text, position) ?: return null
        val token = artifact.lexed.tokens.firstOrNull {
            it.range.startOffset <= offset && offset < it.range.endOffset
        } ?: return null
        val symbol = symbolAt(model, token) ?: return null
        val declaration = model.symbols.firstOrNull { it.id == symbol.id }
        val externalDefinition = declaration?.let { resolved ->
            val path = resolved.externalSource
            val line = resolved.externalLine
            if (path != null && line != null) ExternalDefinition(path, line, resolved.name) else null
        }
        val definition = if (externalDefinition == null) {
            declaration?.let { symbolDeclarationRange(result, it, sourcePathFor, sourceTextFor) }
        } else null
        val indexedReferences = model.referenceIndex.referencesTo(symbol.id)
            .mapNotNull { it.origin.primaryRange }
        val references = (if (includeDeclaration) listOfNotNull(definition) else emptyList())
            .plus(indexedReferences)
            .distinct()
            .sortedWith(compareBy<SourceRange>({ it.file.value }, { it.startOffset }, { it.endOffset }))
        return NavigationInfo(definition, references, externalDefinition)
    }

    private fun symbolDeclarationRange(
        result: CompileResult,
        symbol: Symbol,
        sourcePathFor: (SourceFileId) -> Path?,
        sourceTextFor: (SourceFileId) -> String?
    ): SourceRange? {
        val declaration = declarationSourceRange(symbol.origin) ?: return null
        val declarationPath = sourcePathFor(declaration.file)?.toAbsolutePath()?.normalize()
        val text = sourceTextFor(declaration.file)
        val tokens = if (declarationPath != null && text != null) {
            Lexer().lex(SourceFile(declaration.file, declarationPath, text, 0)).tokens
        } else {
            result.artifacts.firstOrNull { it.source.id == declaration.file }?.lexed?.tokens.orEmpty()
        }
        val token = tokens.firstOrNull {
                it.kind == TokenKind.IDENTIFIER && it.lexeme == symbol.name &&
                    it.range.startOffset >= declaration.startOffset &&
                    it.range.endOffset <= declaration.endOffset
            }
        return token?.range ?: declaration
    }

    private fun declarationSourceRange(origin: Origin): SourceRange? = when (origin) {
        is Origin.Direct -> origin.primaryRange
        is Origin.Generated -> declarationSourceRange(origin.cause) ?: origin.primaryRange
        is Origin.Expansion -> declarationSourceRange(origin.definition)
            ?: origin.parent?.let(::declarationSourceRange)
            ?: declarationSourceRange(origin.invocation)
        is Origin.Synthetic -> origin.parent?.let(::declarationSourceRange) ?: origin.primaryRange
    }

    fun signatureHelp(result: CompileResult, text: String, position: LspPosition, sourcePath: Path? = null): SignatureInfo? {
        val model = result.semanticModel ?: return null
        val artifact = artifactFor(result, sourcePath) ?: return null
        val offset = offsetAt(text, position) ?: return null
        val call = model.nodeIds.values
            .filterIsInstance<cplus.core.AstCall>()
            .firstOrNull { contains(it.origin, SourceRange(artifact.source.id, offset, offset)) }
            ?: return null
        val resolvedExtension = model.resolvedMethodCalls[call]?.method?.takeIf(MethodSymbol::isExtension)
        if (resolvedExtension != null) {
            val parameters = buildList {
                add(SignatureParameterInfo("${resolvedExtension.receiverType.name} self"))
                resolvedExtension.parameters.forEach { parameter ->
                    add(SignatureParameterInfo("${parameter.type.name} ${parameter.name}"))
                }
            }
            val activeParameter = call.arguments.indexOfFirst { argument ->
                contains(argument.origin, SourceRange(artifact.source.id, offset, offset))
            }.takeIf { it >= 0 }?.plus(1)
                ?: (call.arguments.size + 1).coerceAtMost(parameters.lastIndex.coerceAtLeast(0))
            return SignatureInfo(
                "${resolvedExtension.symbol.name}(${parameters.joinToString(", ") { it.label }}): ${resolvedExtension.returnType.name}",
                parameters,
                activeParameter
            )
        }
        val function = when (val callee = call.callee) {
            is AstIdentifier -> model.resolveFunction(callee.name)
            is cplus.core.AstMemberAccess -> {
                val receiverType = model.expressionTypes[callee.receiver]
                val owner = aggregateType(receiverType ?: return null)
                (owner as? StructType)?.methods?.firstOrNull { it.symbol.name == callee.member }?.let { method ->
                    FunctionSymbol(method.symbol, method.returnType, method.parameters, signature = method.signature)
                }
            }
            else -> null
        } ?: return null
        val parameters = function.parameters.map { parameter ->
            SignatureParameterInfo("${parameter.type.name} ${parameter.name}")
        }
        val activeParameter = call.arguments.indexOfFirst { argument -> contains(argument.origin, SourceRange(artifact.source.id, offset, offset)) }
            .takeIf { it >= 0 }
            ?: call.arguments.size.coerceAtMost(parameters.lastIndex.coerceAtLeast(0))
        return SignatureInfo(
            "${function.symbol.name}(${parameters.joinToString(", ") { it.label }}): ${function.returnType.name}",
            parameters,
            activeParameter
        )
    }

    private fun artifactFor(result: CompileResult, sourcePath: Path?) =
        if (sourcePath == null) result.artifacts.firstOrNull()
        else result.artifacts.firstOrNull { it.source.path.toAbsolutePath().normalize() == sourcePath.toAbsolutePath().normalize() }

    private fun memberCandidates(
        model: cplus.semantic.SemanticModel,
        receiverName: String,
        prefix: String,
        requestingModule: String
    ): List<CompletionItem> {
        val receiverType = model.symbolNamed(receiverName)?.type
            ?: model.structs[receiverName]
            ?: model.expressionTypes.entries
                .firstOrNull { (expression, _) -> expression is AstIdentifier && expression.name == receiverName }
                ?.value ?: return emptyList()
        val aggregate = aggregateType(receiverType)
        val fields = when (aggregate) {
            is StructType -> aggregate.fields.map { CompletionItem(it.symbol.name, 5, it.symbol.type.name) }
            is UnionType -> aggregate.fields.map { CompletionItem(it.symbol.name, 5, it.symbol.type.name) }
            else -> emptyList()
        }
        val methods = model.visibleMethods(receiverType, requestingModule)
            .map { method ->
                val provider = if (method.isExtension) " — ${method.definingModule}" else ""
                CompletionItem(method.symbol.name, 2, "${method.signature.name}$provider")
            }
        return (fields + methods).filter { it.label.startsWith(prefix) }
    }

    private fun moduleForSource(model: cplus.semantic.SemanticModel, sourceId: SourceFileId?): String {
        if (sourceId == null) return "<main>"
        return model.program.modules.firstOrNull { module ->
            module.declarations.any { declaration -> declaration.origin.primaryRange?.file == sourceId }
        }?.name ?: "<main>"
    }

    private fun aggregateType(type: CType): CType? = when (type) {
        is AliasType -> aggregateType(type.target)
        is PointerType -> aggregateType(type.pointee)
        is StructType, is UnionType -> type
        is ArrayType -> aggregateType(type.element)
        else -> null
    }

    private fun symbolAt(model: cplus.semantic.SemanticModel, token: Token): Symbol? {
        model.resolvedMethodCalls.entries.firstOrNull { (call, resolved) ->
            val member = call.callee as? AstMemberAccess
            resolved.method.isExtension && member?.member == token.lexeme && contains(member.origin, token.range)
        }?.value?.method?.symbol?.let { return it }
        val reference = model.referenceIndex.all().firstOrNull { it.origin.primaryRange == token.range }
        return reference?.let { ref -> model.symbols.firstOrNull { it.id == ref.symbol } }
            ?: model.symbols
                .asSequence()
                .filter { it.name == token.lexeme && contains(it.origin, token.range) }
                .minByOrNull { it.origin.primaryRange?.let { range -> range.endOffset - range.startOffset } ?: Int.MAX_VALUE }
    }

    private fun contains(origin: Origin, range: SourceRange): Boolean {
        val primary = origin.primaryRange ?: return false
        return primary.file == range.file &&
            primary.startOffset <= range.startOffset &&
            primary.endOffset >= range.endOffset
    }

    private fun offsetAt(text: String, position: LspPosition): Int? {
        if (position.line < 0 || position.character < 0) return null
        val offset = runCatching {
            LineIndex.from(text).offsetAt(cplus.core.SourcePosition(position.line + 1, position.character + 1))
        }.getOrNull() ?: return null
        return offset.takeIf { it in 0..text.length }
    }

    private fun completionKind(kind: SymbolKind): Int = when (kind) {
        SymbolKind.STRUCT, SymbolKind.UNION, SymbolKind.ENUM, SymbolKind.ALIAS, SymbolKind.FOREIGN_TYPE -> 7
        SymbolKind.FUNCTION, SymbolKind.FOREIGN -> 3
        SymbolKind.METHOD -> 2
        SymbolKind.FIELD -> 5
        SymbolKind.PARAMETER -> 6
        else -> 6
    }

    private fun exportCompletionKind(kind: ImportExportKind): Int = when (kind) {
        ImportExportKind.FUNCTION, ImportExportKind.COMPTIME_FUNCTION, ImportExportKind.C_FUNCTION -> 3
        ImportExportKind.VALUE, ImportExportKind.ENUM_VALUE, ImportExportKind.C_VALUE -> 6
        ImportExportKind.STRUCT, ImportExportKind.UNION, ImportExportKind.ENUM,
        ImportExportKind.TYPE_ALIAS, ImportExportKind.C_TYPE -> 7
    }

    private fun unresolvedImportName(code: String?, message: String): String? {
        if (code !in TYPE_DIAGNOSTICS && code != "SEM301") return null
        val expectedPrefix = if (code == "SEM301") "unknown identifier" else "unknown"
        if (!message.startsWith(expectedPrefix)) return null
        return Regex("'([^']+)'").find(message)?.groupValues?.getOrNull(1)
    }

    private val TYPE_DIAGNOSTICS = setOf("SEM101", "SEM102", "SEM105", "SEM106")
    private val TYPE_EXPORTS = setOf(
        ImportExportKind.STRUCT, ImportExportKind.UNION, ImportExportKind.ENUM,
        ImportExportKind.TYPE_ALIAS, ImportExportKind.C_TYPE
    )
    private val CALLABLE_EXPORTS = setOf(
        ImportExportKind.FUNCTION,
        ImportExportKind.COMPTIME_FUNCTION,
        ImportExportKind.C_FUNCTION
    )
    private val VALUE_EXPORTS = setOf(
        ImportExportKind.VALUE, ImportExportKind.ENUM_VALUE, ImportExportKind.C_VALUE
    )
}
