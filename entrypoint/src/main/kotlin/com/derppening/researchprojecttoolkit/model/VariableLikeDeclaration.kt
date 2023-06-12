package com.derppening.researchprojecttoolkit.model

import com.derppening.researchprojecttoolkit.tool.reassignmentsData
import com.derppening.researchprojecttoolkit.util.Logger
import com.derppening.researchprojecttoolkit.util.ancestorsAsSequence
import com.github.javaparser.ast.Node
import com.github.javaparser.ast.body.CallableDeclaration
import com.github.javaparser.ast.body.EnumConstantDeclaration
import com.github.javaparser.ast.body.FieldDeclaration
import com.github.javaparser.ast.body.VariableDeclarator
import com.github.javaparser.ast.expr.AssignExpr
import com.github.javaparser.ast.expr.VariableDeclarationExpr
import com.github.javaparser.ast.stmt.CatchClause
import com.github.javaparser.ast.stmt.ForEachStmt
import com.github.javaparser.ast.body.Parameter as JavaParserParameter

/**
 * A declaration which can be assigned to like a variable.
 */
sealed class VariableLikeDeclaration {

    abstract val node: Node
    abstract val reassignmentsData: Set<AssignExpr>

    /**
     * A variable declared within the parameter list of a method or constructor.
     */
    data class CallableParameter(val param: JavaParserParameter) : VariableLikeDeclaration() {

        /**
         * The [CallableDeclaration] which this parameter is a part of.
         */
        val callableDecl get() = param.parentNode.map { it as CallableDeclaration<*> }.get()

        init {
            check(callableDecl.parameters.any { it === param })
        }

        override val node get() = param
        override val reassignmentsData get() = param.reassignmentsData
    }

    /**
     * A variable declared as part of a [CatchClause].
     */
    data class CatchClauseParameter(val param: JavaParserParameter) : VariableLikeDeclaration() {

        /**
         * The [CatchClause] which this parameter is a part of.
         */
        val catchClause get() = param.parentNode.map { it as CatchClause }.get()

        init {
            check(catchClause.parameter === param)
        }

        override val node get() = param
        override val reassignmentsData get() = param.reassignmentsData
    }

    /**
     * A variable declared as part of a class.
     */
    data class ClassField(val varDecl: VariableDeclarator) : VariableLikeDeclaration() {

        /**
         * The [FieldDeclaration] which this variable is a part of.
         */
        val fieldDecl = checkNotNull(varDecl.parentNode.get() as? FieldDeclaration)

        init {
            check(fieldDecl.variables.any { it === varDecl })
        }

        override val node get() = varDecl
        override val reassignmentsData get() = varDecl.reassignmentsData
    }

    /**
     * A variable declared as part of the variable for a [ForEachStmt].
     */
    data class ForEachVariable(val varDecl: VariableDeclarator) : VariableLikeDeclaration() {

        /**
         * The [ForEachStmt] which this variable is a part of.
         */
        val forEachStmt = checkNotNull(varDecl.ancestorsAsSequence().drop(1).firstOrNull() as? ForEachStmt)

        init {
            check(forEachStmt.variableDeclarator === varDecl)
        }

        override val node get() = varDecl
        override val reassignmentsData get() = varDecl.reassignmentsData
    }

    /**
     * A variable declared within a body.
     */
    data class LocalVariable(val varDecl: VariableDeclarator) : VariableLikeDeclaration() {

        /**
         * The [VariableDeclarationExpr] which this variable is a part of.
         */
        val varDeclExpr = checkNotNull(varDecl.parentNode.get() as? VariableDeclarationExpr)

        init {
            check(varDeclExpr.variables.any { it === varDecl })
        }

        override val node get() = varDecl
        override val reassignmentsData get() = varDecl.reassignmentsData
    }

    companion object {

        private val LOGGER = Logger<VariableLikeDeclaration>()

        fun fromNodeOrNull(n: Node): VariableLikeDeclaration? {
            return when (n) {
                is EnumConstantDeclaration -> {
                    // Enum Constants are not variable-like because they cannot be reassigned
                    null
                }

                is JavaParserParameter -> {
                    if (n.parentNode.map { it is CatchClause }.get()) {
                        CatchClauseParameter(n)
                    } else {
                        CallableParameter(n)
                    }
                }

                is VariableDeclarator -> {
                    if (n.parentNode.map { it is FieldDeclaration }.get()) {
                        ClassField(n)
                    } else if (n.ancestorsAsSequence().drop(1).firstOrNull() is ForEachStmt) {
                        ForEachVariable(n)
                    } else {
                        LocalVariable(n)
                    }
                }

                else -> {
                    LOGGER.warn("Unhandled node type ${n::class.simpleName}")
                    null
                }
            }
        }
        fun fromNode(n: Node): VariableLikeDeclaration =
            fromNodeOrNull(n) ?: error("Cannot convert node of type ${n::class.simpleName} to VariableLikeDeclaration")
    }
}