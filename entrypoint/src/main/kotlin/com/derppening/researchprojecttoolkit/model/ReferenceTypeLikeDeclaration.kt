package com.derppening.researchprojecttoolkit.model

import com.derppening.researchprojecttoolkit.util.canBeReferencedByName
import com.derppening.researchprojecttoolkit.util.containingType
import com.github.javaparser.ast.Node
import com.github.javaparser.ast.body.BodyDeclaration
import com.github.javaparser.ast.body.EnumConstantDeclaration
import com.github.javaparser.ast.body.EnumDeclaration
import com.github.javaparser.ast.body.TypeDeclaration
import com.github.javaparser.ast.expr.ObjectCreationExpr
import kotlin.jvm.optionals.getOrDefault
import kotlin.jvm.optionals.getOrNull

sealed class ReferenceTypeLikeDeclaration<N : Node> {

    abstract val node: N
    abstract val nameAsString: String?
    abstract val nameWithScope: String?
    abstract val members: List<BodyDeclaration<*>>

    abstract val canBeReferencedByName: Boolean

    data class TypeDecl(override val node: TypeDeclaration<*>) : ReferenceTypeLikeDeclaration<TypeDeclaration<*>>() {

        override val nameAsString: String
            get() = node.nameAsString
        override val nameWithScope: String?
            get() = node.fullyQualifiedName.getOrNull()
        override val members: List<BodyDeclaration<*>>
            get() = node.members
        override val canBeReferencedByName: Boolean
            get() = node.canBeReferencedByName
    }

    data class AnonClassDecl(override val node: ObjectCreationExpr) : ReferenceTypeLikeDeclaration<ObjectCreationExpr>() {

        override val nameAsString: String?
            get() = null
        override val nameWithScope: String?
            get() = null
        override val members: List<BodyDeclaration<*>>
            get() = node.anonymousClassBody.getOrDefault(emptyList())
        override val canBeReferencedByName = false
    }

    data class EnumConstDecl(override val node: EnumConstantDeclaration) : ReferenceTypeLikeDeclaration<EnumConstantDeclaration>() {

        override val nameAsString: String
            get() = node.nameAsString
        override val nameWithScope: String
            get() = node.findAncestor(EnumDeclaration::class.java).get().fullyQualifiedName.getOrNull() + "." + node.nameAsString
        override val members: List<BodyDeclaration<*>>
            get() = node.classBody
        override val canBeReferencedByName: Boolean
            get() = node.containingType.canBeReferencedByName
    }

    companion object {

        fun createOrNull(node: Node): ReferenceTypeLikeDeclaration<*>? {
            return when (node) {
                is TypeDeclaration<*> -> TypeDecl(node)
                is ObjectCreationExpr -> {
                    if (node.anonymousClassBody.isPresent) {
                        AnonClassDecl(node)
                    } else null
                }
                is EnumConstantDeclaration -> {
                    if (node.classBody.isNonEmpty) {
                        EnumConstDecl(node)
                    } else null
                }
                else -> null
            }
        }

        fun create(node: Node): ReferenceTypeLikeDeclaration<*> = requireNotNull(createOrNull(node)) {
            "Unsupported node type `${node::class.simpleName}`"
        }
    }
}