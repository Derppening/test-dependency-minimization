package com.derppening.researchprojecttoolkit.visitor

import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.Node

/**
 * A path trace which specifies how to get from a node with type [N] to a node with type [R].
 */
fun interface NodePathTrace<in N : Node, out R : Node> : (N) -> R {

    /**
     * Joins `this` [NodePathTrace] with [after], such that [after] is executed after `this`.
     *
     * @see java.util.function.Function.andThen
     */
    fun <O : Node> andThen(after: NodePathTrace<R, O>): NodePathTrace<N, O> =
        NodePathTrace { after.invoke(invoke(it)) }

    companion object {

        /**
         * A [NodePathTrace] which returns the current node.
         */
        fun <N : Node> identity(): NodePathTrace<N, N> = NodePathTrace { it }
    }
}

/**
 * Alias for [NodePathTrace] which starts from a [CompilationUnit].
 */
typealias CompilationUnitPathTrace<N> = NodePathTrace<CompilationUnit, N>