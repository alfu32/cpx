package cplus.semantic

import cplus.core.*
import java.util.IdentityHashMap

internal data class ModuleTypeReference(
    val moduleName: String,
    val type: AstTypeRef
)

internal object ModuleTypeReferenceCollector {
    fun collect(program: AstProgram): List<ModuleTypeReference> {
        val moduleByDeclaration = IdentityHashMap<AstDeclaration, String>()
        if (program.modules.isEmpty()) {
            program.declarations.forEach { moduleByDeclaration[it] = "<main>" }
        } else {
            program.modules.forEach { module ->
                module.declarations.forEach { declaration -> moduleByDeclaration[declaration] = module.name }
            }
        }

        val references = mutableListOf<ModuleTypeReference>()

        fun collectType(moduleName: String, type: AstTypeRef) {
            references += ModuleTypeReference(moduleName, type)
            type.functionParameters.orEmpty().forEach { collectType(moduleName, it.type) }
        }

        fun collectExpression(moduleName: String, expression: AstExpression) {
            when (expression) {
                is AstStringTemplate -> expression.parts.filterIsInstance<AstStringExpressionPart>()
                    .forEach { collectExpression(moduleName, it.expression) }
                is AstUnary -> collectExpression(moduleName, expression.operand)
                is AstBinary -> {
                    collectExpression(moduleName, expression.left)
                    collectExpression(moduleName, expression.right)
                }
                is AstConditional -> {
                    collectExpression(moduleName, expression.condition)
                    collectExpression(moduleName, expression.thenBranch)
                    collectExpression(moduleName, expression.elseBranch)
                }
                is AstUpdate -> collectExpression(moduleName, expression.operand)
                is AstSizeOf -> {
                    expression.operand?.let { collectExpression(moduleName, it) }
                    expression.targetType?.let { collectType(moduleName, it) }
                }
                is AstAbiQuery -> {
                    expression.operand?.let { collectExpression(moduleName, it) }
                    expression.targetType?.let { collectType(moduleName, it) }
                }
                is AstCast -> {
                    collectType(moduleName, expression.target)
                    collectExpression(moduleName, expression.operand)
                }
                is AstCall -> {
                    collectExpression(moduleName, expression.callee)
                    expression.arguments.forEach { collectExpression(moduleName, it) }
                }
                is AstMemberAccess -> collectExpression(moduleName, expression.receiver)
                is AstIndexAccess -> {
                    collectExpression(moduleName, expression.receiver)
                    collectExpression(moduleName, expression.index)
                }
                is AstParenthesized -> collectExpression(moduleName, expression.expression)
                is AstIntegerLiteral,
                is AstBooleanLiteral,
                is AstFloatLiteral,
                is AstStringLiteral,
                is AstCharacterLiteral,
                is AstIdentifier,
                is AstErrorExpression -> Unit
            }
        }

        lateinit var collectStatement: (String, AstStatement) -> Unit

        fun collectFunction(moduleName: String, function: AstFunction) {
            collectType(moduleName, function.returnType)
            function.parameters.forEach { collectType(moduleName, it.type) }
            function.body?.let { collectStatement(moduleName, it) }
        }

        collectStatement = { moduleName, statement ->
            when (statement) {
                is AstBlock -> statement.statements.forEach { collectStatement(moduleName, it) }
                is AstReturn -> statement.expression?.let { collectExpression(moduleName, it) }
                is AstExpressionStatement -> collectExpression(moduleName, statement.expression)
                is AstDefer -> collectExpression(moduleName, statement.expression)
                is AstIf -> {
                    collectExpression(moduleName, statement.condition)
                    collectStatement(moduleName, statement.thenBranch)
                    statement.elseBranch?.let { collectStatement(moduleName, it) }
                }
                is AstWhile -> {
                    collectExpression(moduleName, statement.condition)
                    collectStatement(moduleName, statement.body)
                }
                is AstFor -> {
                    statement.initializer?.let { collectStatement(moduleName, it) }
                    statement.condition?.let { collectExpression(moduleName, it) }
                    statement.increment?.let { collectExpression(moduleName, it) }
                    collectStatement(moduleName, statement.body)
                }
                is AstVariableDeclaration -> {
                    collectType(moduleName, statement.type)
                    statement.initializer?.let { collectExpression(moduleName, it) }
                }
                is AstInnerFunction -> collectFunction(moduleName, statement.function)
                is AstBreak, is AstContinue -> Unit
            }
        }

        fun collectDeclaration(moduleName: String, declaration: AstDeclaration) {
            when (declaration) {
                is AstAlias -> collectType(moduleName, declaration.target)
                is AstStruct -> {
                    declaration.fields.forEach { collectType(moduleName, it.type) }
                    declaration.methods.forEach { collectFunction(moduleName, it) }
                }
                is AstTrait -> {
                    collectType(moduleName, AstTypeRef(declaration.targetName, false, 0, declaration.targetOrigin))
                    declaration.methods.forEach { method ->
                        collectType(moduleName, method.returnType)
                        method.parameters.filterNot { it.isReceiver }.forEach { collectType(moduleName, it.type) }
                        method.body?.let { collectStatement(moduleName, it) }
                    }
                }
                is AstUnion -> declaration.fields.forEach { collectType(moduleName, it.type) }
                is AstFunction -> collectFunction(moduleName, declaration)
                is AstGlobalVariable -> {
                    collectType(moduleName, declaration.type)
                    declaration.initializer?.let { collectExpression(moduleName, it) }
                }
                is AstPackage,
                is AstEnum,
                is AstImport,
                is AstComptimeFunction,
                is AstCpxInvocation -> Unit
            }
        }

        program.declarations.forEach { declaration ->
            collectDeclaration(moduleByDeclaration[declaration] ?: "<main>", declaration)
        }
        return references
    }
}
