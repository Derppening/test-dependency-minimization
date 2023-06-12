package com.derppening.researchprojecttoolkit.model

import com.github.javaparser.ast.Node
import com.github.javaparser.ast.expr.Expression
import com.github.javaparser.ast.stmt.Statement

sealed class ExecutableNode<N : Node> {

    abstract val node: N

    data class Expr(override val node: Expression) : ExecutableNode<Expression>()
    data class Stmt(override val node: Statement) : ExecutableNode<Statement>()

    companion object {

        fun createOrNull(node: Node): ExecutableNode<*>? {
            return when (node) {
                is Expression -> Expr(node)
                is Statement -> Stmt(node)
                else -> null
            }
        }

        fun create(node: Node): ExecutableNode<*> =
            checkNotNull(createOrNull(node)) {
                "Cannot create an instance of ExecutableNode with ${node::class.simpleName}"
            }
    }
}