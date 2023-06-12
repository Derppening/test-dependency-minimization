package com.derppening.researchprojecttoolkit.model

import com.derppening.researchprojecttoolkit.util.*
import com.github.javaparser.ast.Node
import com.github.javaparser.ast.body.ConstructorDeclaration
import com.github.javaparser.ast.body.InitializerDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.body.VariableDeclarator
import kotlin.jvm.optionals.getOrNull

sealed class ExecutableDeclaration<N : Node> {

    abstract val node: N
    abstract val executableUnit: ExecutableNode<*>?
    abstract val name: String
    abstract val signature: String?
    abstract val qualifiedName: String
    abstract val qualifiedSignature: String?

    data class CtorDecl(override val node: ConstructorDeclaration) : ExecutableDeclaration<ConstructorDeclaration>() {

        override val executableUnit: ExecutableNode.Stmt
            get() = ExecutableNode.Stmt(node.body)
        override val name: String
            get() = node.nameAsString
        override val qualifiedName: String
            get() = node.findAncestor<Node> { ReferenceTypeLikeDeclaration.createOrNull(it) != null }
                .let { ReferenceTypeLikeDeclaration.create(checkNotNull(it)) }
                .nameWithScope
                .let { "$it.$name" }
        override val signature: String
            get() = node.signature.asString()
        override val qualifiedSignature: String
            get() = node.getQualifiedSignature(null)
    }

    data class MethodDecl(override val node: MethodDeclaration) : ExecutableDeclaration<MethodDeclaration>() {

        override val executableUnit: ExecutableNode.Stmt?
            get() = node.body.map { ExecutableNode.Stmt(it) }.getOrNull()
        override val name: String
            get() = node.nameAsString
        override val qualifiedName: String
            get() = node.findAncestor<Node> { ReferenceTypeLikeDeclaration.createOrNull(it) != null }
                .let { ReferenceTypeLikeDeclaration.create(checkNotNull(it)) }
                .nameWithScope
                .let { "$it.$name" }
        override val signature: String
            get() = node.signature.asString()
        override val qualifiedSignature: String
            get() = node.getQualifiedSignature(null)
    }

    data class FieldVariable(override val node: VariableDeclarator) : ExecutableDeclaration<VariableDeclarator>() {

        init {
            check(node.isFieldVar) { "Node must reference a field variable\n${node.astToString(showChildren = false)}" }
        }

        override val executableUnit: ExecutableNode.Expr?
            get() = node.initializer.map { ExecutableNode.Expr(it) }.getOrNull()
        override val name: String
            get() = node.nameAsString
        override val qualifiedName: String
            get() = node.getQualifiedName(null)
        override val signature: String?
            get() = null
        override val qualifiedSignature: String?
            get() = null
    }

    data class InitializerDecl(override val node: InitializerDeclaration) : ExecutableDeclaration<InitializerDeclaration>() {

        override val executableUnit: ExecutableNode.Stmt
            get() = ExecutableNode.Stmt(node.body)
        override val name: String
            get() = if (node.isStatic) "<clinit>" else "<init>"
        override val qualifiedName: String
            get() = node.findAncestor<Node> { ReferenceTypeLikeDeclaration.createOrNull(it) != null }
                .let { ReferenceTypeLikeDeclaration.create(checkNotNull(it)) }
                .nameWithScope
                .let { "$it.$name" }
        override val signature: String
            get() = "$name()"
        override val qualifiedSignature: String?
            get() = node.findAncestor<Node> { ReferenceTypeLikeDeclaration.createOrNull(it) != null }
                ?.let { ReferenceTypeLikeDeclaration.create(it) }
                ?.nameWithScope
                ?.let { "$it.$signature" }
    }

    companion object {

        fun createOrNull(node: Node): ExecutableDeclaration<*>? {
            return when (node) {
                is ConstructorDeclaration -> CtorDecl(node)
                is MethodDeclaration ->  MethodDecl(node)
                is VariableDeclarator -> if (node.isFieldVar) FieldVariable(node) else null
                is InitializerDeclaration -> InitializerDecl(node)
                else -> null
            }
        }

        fun create(node: Node): ExecutableDeclaration<*> = checkNotNull(createOrNull(node)) {
            "Cannot create an instance of ExecutableDeclaration from ${node::class.simpleName}"
        }
    }
}