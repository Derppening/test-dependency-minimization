package com.derppening.researchprojecttoolkit.tool.transform

import com.derppening.researchprojecttoolkit.tool.ReducerContext
import com.derppening.researchprojecttoolkit.tool.initFieldVarData
import com.derppening.researchprojecttoolkit.util.findAll
import com.derppening.researchprojecttoolkit.util.resolveDeclaration
import com.derppening.researchprojecttoolkit.util.toTypedAstOrNull
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.ConstructorDeclaration
import com.github.javaparser.ast.body.FieldDeclaration
import com.github.javaparser.ast.body.InitializerDeclaration
import com.github.javaparser.ast.body.TypeDeclaration
import com.github.javaparser.ast.expr.AssignExpr
import com.github.javaparser.ast.expr.FieldAccessExpr
import com.github.javaparser.ast.expr.NameExpr
import com.github.javaparser.ast.stmt.ExpressionStmt
import com.github.javaparser.resolution.declarations.ResolvedFieldDeclaration

class TagClassFieldInitializers(override val reducerContext: ReducerContext) : TransformPass {

    private fun processStmt(exprStmt: ExpressionStmt, typeDecl: TypeDeclaration<*>) {
        val expr = exprStmt.expression
        if (expr !is AssignExpr) {
            return
        }

        when (val targetExpr = expr.target) {
            is FieldAccessExpr,
            is NameExpr -> {
                if (targetExpr is FieldAccessExpr && !targetExpr.scope.isThisExpr) {
                    return
                }

                val resolvedField = runCatching {
                    reducerContext.symbolSolver.resolveDeclaration<ResolvedFieldDeclaration>(targetExpr)
                }.getOrNull() ?: return
                val fieldAst = resolvedField.toTypedAstOrNull<FieldDeclaration>(reducerContext)
                    ?: return

                if (fieldAst !in typeDecl.fields) {
                    return
                }

                expr.initFieldVarData = fieldAst.variables.single { it.nameAsString == resolvedField.name }
            }
            else -> {}
        }
    }

    private fun processCtor(ctorDecl: ConstructorDeclaration, typeDecl: TypeDeclaration<*>) {
        ctorDecl.findAll<ExpressionStmt> { it.expression.isAssignExpr }
            .forEach { processStmt(it, typeDecl) }
    }

    private fun processInit(initDecl: InitializerDeclaration, typeDecl: TypeDeclaration<*>) {
        initDecl.findAll<ExpressionStmt> { it.expression.isAssignExpr }
            .forEach { processStmt(it, typeDecl) }
    }

    private fun processTypeDecl(typeDecl: TypeDeclaration<*>) {
        val ctorDecls = typeDecl.constructors
        val initDecls = typeDecl.members.filterIsInstance<InitializerDeclaration>()

        ctorDecls.forEach { processCtor(it, typeDecl) }
        initDecls.forEach { processInit(it, typeDecl) }
    }

    override fun transform(cu: CompilationUnit) {
        verifyIsInvokedOnce(cu)

        val types = cu.findAll<TypeDeclaration<*>>()

        types.forEach {
            processTypeDecl(it)
        }
    }
}