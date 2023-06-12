package com.derppening.researchprojecttoolkit.model

import com.github.javaparser.ast.Node
import com.github.javaparser.ast.type.ClassOrInterfaceType
import com.github.javaparser.ast.expr.AnnotationExpr as JavaParserAnnotationExpr
import com.github.javaparser.ast.expr.FieldAccessExpr as JavaParserFieldAccessExpr
import com.github.javaparser.ast.expr.NameExpr as JavaParserNameExpr

sealed class ReferenceTypeLikeNode<N : Node> {

    abstract val node: N
    abstract val nameAsString: String
    abstract val nameWithScope: String

    data class FieldAccessExpr(override val node: JavaParserFieldAccessExpr) : ReferenceTypeLikeNode<JavaParserFieldAccessExpr>() {

        override val nameAsString: String
            get() = node.nameAsString
        override val nameWithScope: String
            get() = node.toString()
    }

    data class NameExpr(override val node: JavaParserNameExpr) : ReferenceTypeLikeNode<JavaParserNameExpr>() {

        override val nameAsString: String
            get() = node.nameAsString
        override val nameWithScope: String
            get() = node.nameAsString
    }

    data class ClassOrIfaceType(override val node: ClassOrInterfaceType) : ReferenceTypeLikeNode<ClassOrInterfaceType>() {

        override val nameAsString: String
            get() = node.nameAsString
        override val nameWithScope: String
            get() = node.nameWithScope
    }

    data class AnnotationExpr(override val node: JavaParserAnnotationExpr) : ReferenceTypeLikeNode<JavaParserAnnotationExpr>() {

        override val nameAsString: String
            get() = node.nameAsString
        override val nameWithScope: String
            get() = node.toString()
    }

    companion object {

        fun createOrNull(node: Node): ReferenceTypeLikeNode<*>? {
            return when (node) {
                is JavaParserFieldAccessExpr -> FieldAccessExpr(node)
                is JavaParserNameExpr -> NameExpr(node)
                is ClassOrInterfaceType -> ClassOrIfaceType(node)
                is JavaParserAnnotationExpr -> AnnotationExpr(node)
                else -> null
            }
        }

        fun create(node: Node): ReferenceTypeLikeNode<*> =
            requireNotNull(createOrNull(node)) {
                "Unsupported node type `${node::class.simpleName}`"
            }
    }
}