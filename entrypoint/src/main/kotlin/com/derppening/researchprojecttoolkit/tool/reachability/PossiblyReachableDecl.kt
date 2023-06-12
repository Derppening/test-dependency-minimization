package com.derppening.researchprojecttoolkit.tool.reachability

import com.derppening.researchprojecttoolkit.model.ReferenceTypeLikeDeclaration
import com.derppening.researchprojecttoolkit.util.containingType
import com.derppening.researchprojecttoolkit.util.getQualifiedName
import com.derppening.researchprojecttoolkit.util.getQualifiedSignature
import com.github.javaparser.ast.body.*
import hk.ust.cse.castle.toolkit.jvm.util.RuntimeAssertions

/**
 * A possibly reachable declaration.
 */
class PossiblyReachableDecl private constructor(
    val decl: BodyDeclaration<*>,
    val reason: ReachableReason,
    val nestedDecl: VariableDeclarator?
) {

    constructor(decl: BodyDeclaration<*>, reason: ReachableReason) : this(decl, reason, null)
    constructor(decl: VariableDeclarator, reason: ReachableReason) :
            this(decl.parentNode.map { checkNotNull(it as? FieldDeclaration) }.get(), reason, decl)

    /**
     * The [ReferenceTypeLikeDeclaration] which houses [decl], or [decl] if it is already a [TypeDeclaration].
     */
    val typeDecl: ReferenceTypeLikeDeclaration<*>
        get() = when (decl) {
            is TypeDeclaration -> ReferenceTypeLikeDeclaration.create(decl)
            is CallableDeclaration -> decl.containingType
            is FieldDeclaration -> decl.containingType
            is EnumConstantDeclaration -> decl.containingType
            else -> TODO("Unknown classDecl impl for decl of type ${decl::class.simpleName}")
        }

    private val qualifiedName
        get() = when (decl) {
            is TypeDeclaration<*> -> decl.fullyQualifiedName.get()
            is CallableDeclaration<*> -> decl.getQualifiedSignature(null)
            is FieldDeclaration -> nestedDecl!!.getQualifiedName(null)
            else -> RuntimeAssertions.unreachable()
        }

    override fun toString(): String {
        return "PossiblyReachableDecl(`$qualifiedName`, reason=$reason)"
    }
}