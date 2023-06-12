package com.derppening.researchprojecttoolkit.tool.reachability

import com.derppening.researchprojecttoolkit.model.BytecodeMethod
import com.derppening.researchprojecttoolkit.model.EntrypointSpec
import com.derppening.researchprojecttoolkit.model.ExecutableDeclaration
import com.derppening.researchprojecttoolkit.model.ReferenceTypeLikeDeclaration
import com.derppening.researchprojecttoolkit.util.*
import com.github.javaparser.ast.Node
import com.github.javaparser.ast.body.*
import com.github.javaparser.ast.expr.Expression
import com.github.javaparser.ast.expr.MethodCallExpr
import com.github.javaparser.ast.nodeTypes.NodeWithSimpleName
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt
import com.github.javaparser.ast.type.ClassOrInterfaceType
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration
import hk.ust.cse.castle.toolkit.jvm.util.RuntimeAssertions.unreachable
import kotlin.jvm.optionals.getOrDefault
import kotlin.jvm.optionals.getOrNull

sealed class ReachableReason {

    abstract class DirectlyReferenced : ReachableReason()
    abstract class TransitivelyReferenced : ReachableReason()

    abstract fun toReasonString(): String

    //<editor-fold desc="Directly Referenced Reasons">

    abstract class ByBaseline : DirectlyReferenced()

    /**
     *
     */
    data class ByBaselineMethod(val bytecodeMethod: BytecodeMethod) : ByBaseline() {

        override fun toReasonString(): String = "By-Baseline-Method `${bytecodeMethod}`"
    }

    data class ByBaselineClass(val className: String) : ByBaseline() {

        override fun toReasonString(): String = "By-Baseline-Class `$className`"
    }

    /**
     * The declaration is included because it is directly related to an entrypoint.
     */
    abstract class ByEntrypoint : DirectlyReferenced()

    /**
     * The declaration is specified by the [EntrypointSpec].
     */
    data class Entrypoint(val entrypoint: EntrypointSpec) : ByEntrypoint() {

        override fun toReasonString(): String = "Entrypoint($entrypoint)"
    }

    /**
     * Type is specified by the method containing [EntrypointSpec].
     */
    data class EntrypointClass(val entrypoint: EntrypointSpec) : ByEntrypoint() {

        override fun toReasonString(): String = "Entrypoint-Class($entrypoint)"
    }

    /**
     * The declaration is included because [node] statically resolves to it.
     */
    abstract class DirectlyReferencedByNode : DirectlyReferenced() {

        abstract val node: Node
    }

    /**
     * The declaration reachable because [node] references the type in an unspecified way.
     */
    data class ReferencedByUnspecifiedNode(override val node: Node) : DirectlyReferencedByNode() {

        override fun toReasonString(): String {
            val enclosingClassName = node.findCompilationUnit()
                .flatMap { it.primaryType }
                .flatMap { it.fullyQualifiedName }
                .get()

            val strRep = when (node) {
                is ClassOrInterfaceDeclaration -> "${node::class.simpleName} (`${enclosingClassName}` ${node.fullRangeString})"
                else -> {
                    "${node::class.simpleName} `$node` (`${enclosingClassName}` ${node.fullRangeString})"
                }
            }

            return "Referenced-by-UnspecifiedNode $strRep"
        }
    }

    /**
     * The declaration is reachable because [namedNode] references the type by its name.
     */
    data class ReferencedByTypeName(val namedNode: NodeWithSimpleName<out Node>) : DirectlyReferencedByNode() {

        override val node get() = namedNode as Node

        override fun toReasonString(): String {
            val enclosingClassName = node.findCompilationUnit()
                .flatMap { it.primaryType }
                .flatMap { it.fullyQualifiedName }
                .getOrNull()

            val strRep = when (node) {
                is ClassOrInterfaceDeclaration -> "${node::class.simpleName} (`${enclosingClassName}` ${node.fullRangeString})"
                else -> {
                    "${node::class.simpleName} `$node` (`${enclosingClassName}` ${node.fullRangeString})"
                }
            }

            return "Referenced-by-TypeName $strRep"
        }
    }

    /**
     * The declaration is reachable because [expr] references the member field, method, or constructor by its name.
     */
    data class ReferencedBySymbolName(val expr: Expression) : DirectlyReferencedByNode() {

        override val node get() = expr

        override fun toReasonString(): String {
            val enclosingClassName = expr.findCompilationUnit()
                .flatMap { it.primaryType }
                .flatMap { it.fullyQualifiedName }
                .getOrNull()

            val strRep = "${expr::class.simpleName} `$expr` (`${enclosingClassName}` ${expr.fullRangeString})"

            return "Referenced-by-SymbolName $strRep"
        }
    }

    /**
     * The declaration is reachable because it is the solved type of [expr].
     */
    data class ReferencedByExprType(val expr: Expression) : DirectlyReferencedByNode() {

        override val node get() = expr

        override fun toReasonString(): String {
            val enclosingClassName = expr.findCompilationUnit()
                .flatMap { it.primaryType }
                .flatMap { it.fullyQualifiedName }
                .getOrNull()

            val strRep = "${expr::class.simpleName} `$expr` (`${enclosingClassName}` ${expr.fullRangeString})"

            return "Referenced-by-Expr-Type $strRep"
        }
    }

    /**
     * The declaration is reachable because [enumConstant] invokes this constructor.
     */
    data class ReferencedByCtorCallByEnumConstant(val enumConstant: EnumConstantDeclaration) : DirectlyReferencedByNode() {

        override val node get() = enumConstant

        override fun toReasonString(): String =
            "Referenced-by-CtorCallByEnumConstant `${enumConstant.getQualifiedName(null)}`"
    }

    /**
     * The declaration is reachable because [explicitConstructorInvocationStmt] invokes this constructor.
     */
    data class ReferencedByCtorCallByExplicitStmt(
        val explicitConstructorInvocationStmt: ExplicitConstructorInvocationStmt
    ) : DirectlyReferencedByNode() {

        override val node get() = explicitConstructorInvocationStmt

        override fun toReasonString(): String {
            val enclosingCtorSignature = explicitConstructorInvocationStmt
                .findAncestor(ConstructorDeclaration::class.java).get()
                .getQualifiedSignature(null)

            val strRep = "`$explicitConstructorInvocationStmt` (`${enclosingCtorSignature}` ${explicitConstructorInvocationStmt.fullRangeString})"

            return "Referenced-by-ExplicitCtorInvocationStmt $strRep"
        }
    }

    /**
     * The declaration is included because it is a supertype of [classDecl].
     */
    data class ClassSupertype(val classDecl: TypeDeclaration<*>) : DirectlyReferencedByNode() {

        override val node get() = classDecl

        override fun toReasonString(): String {
            val enclosingClassName = classDecl.findCompilationUnit()
                .flatMap { it.primaryType }
                .flatMap { it.fullyQualifiedName }
                .getOrNull()

            val strRep = "${classDecl::class.simpleName} (`${enclosingClassName}` ${classDecl.fullRangeString})"

            return "Supertype-of $strRep"
        }
    }

    //</editor-fold>

    //<editor-fold desc="Transitively Referenced Reasons">

    /**
     * A [TransitivelyReferenced] declaration whose inclusion policy depends on one or more dependent nodes.
     */
    abstract class DependentTransitiveReference<out N : Node> : TransitivelyReferenced() {

        /**
         * Checks whether the dependent node(s) satisfy the given [predicate].
         *
         * If the there is only one dependent node, the [merger] argument is ignored.
         */
        protected abstract fun checkDependents(
            predicate: PredicateKt<in N>,
            merger: (Collection<Pair<N, Boolean>>) -> Boolean
        ): Boolean
    }

    /**
     * A [TransitivelyReferenced] declaration whose inclusion policy depends on a single dependent node.
     */
    abstract class SingleDependentTransitiveReference<out N : Node> : DependentTransitiveReference<N>() {

        /**
         * The node which this declaration's inclusion policy depends on.
         */
        abstract val dependentNode: N

        override fun checkDependents(
            predicate: PredicateKt<in N>,
            merger: (Collection<Pair<N, Boolean>>) -> Boolean
        ): Boolean = checkDependentNode(predicate)

        fun checkDependentNode(predicate: PredicateKt<in N>): Boolean =
            predicate(dependentNode)
    }

    /**
     * A [TransitivelyReferenced] declaration whose inclusion policy depends on more than one dependent nodes.
     */
    abstract class MultipleDependentTransitiveReference<out N : Node> : DependentTransitiveReference<N>() {

        /**
         * The nodes which this declaration's inclusion policy depends on.
         */
        abstract val dependentNodes: Collection<N>

        public override fun checkDependents(
            predicate: PredicateKt<in N>,
            merger: (Collection<Pair<N, Boolean>>) -> Boolean
        ): Boolean {
            val mapped = dependentNodes.map { it to predicate(it) }

            return if (mapped.size == 1) {
                mapped.single().second
            } else {
                merger(mapped)
            }
        }
    }

    /**
     * The declaration is included because this is a member of the [annoDecl].
     */
    data class TransitiveAnnotationMember(val annoDecl: AnnotationDeclaration) : SingleDependentTransitiveReference<AnnotationDeclaration>() {

        override val dependentNode: AnnotationDeclaration
            get() = annoDecl

        override fun toReasonString(): String {
            return "Transitive-Annotation-Member-of ${annoDecl.fullyQualifiedName.get()}"
        }
    }

    /**
     * The declaration is included because the declaration declares the scope of [memberDecl].
     */
    data class TransitiveDeclaringTypeOfMember(
        val memberDecl: BodyDeclaration<*>,
        val varDecl: VariableDeclarator? = null
    ) : SingleDependentTransitiveReference<Node>() {

        init {
            check(memberDecl.let { it !is TypeDeclaration<*> && (it !is FieldDeclaration || varDecl != null) })
            check(memberDecl.parentNode.map { ReferenceTypeLikeDeclaration.createOrNull(it) != null }.getOrDefault(false))
        }

        override val dependentNode: Node
            get() = varDecl ?: memberDecl

        override fun toReasonString(): String {
            val qname = when (memberDecl) {
                is CallableDeclaration<*> -> memberDecl.getQualifiedSignature(null)
                is EnumConstantDeclaration -> memberDecl.getQualifiedName(null)
                is FieldDeclaration -> varDecl!!.getQualifiedName(null)
                else -> unreachable("memberDecl is of unknown node type ${memberDecl::class.simpleName}")
            }

            val strRep = "${memberDecl::class.simpleName} (`$qname` ${(varDecl ?: memberDecl).fullRangeString})"

            return "Transitive-DeclaringType-of `$strRep`"
        }
    }

    /**
     * The declaration is included because the declaration is a class member of [typeDecl].
     */
    data class TransitiveClassMemberOfType(val refLikeDecl: ReferenceTypeLikeDeclaration<*>)
        : SingleDependentTransitiveReference<Node>() {

        constructor(typeDecl: TypeDeclaration<*>) : this(ReferenceTypeLikeDeclaration.create(typeDecl))

        override val dependentNode: Node
            get() = refLikeDecl.node

        override fun toReasonString(): String = "Transitive-ClassMember-of `${refLikeDecl.nameWithScope}` ${refLikeDecl.node.fullRangeString}"
    }

    /**
     * The declaration is included because it is the nest parent of [classDecl].
     */
    data class NestParent(val classDecl: TypeDeclaration<*>) : SingleDependentTransitiveReference<TypeDeclaration<*>>() {

        override val dependentNode: TypeDeclaration<*>
            get() = classDecl

        override fun toReasonString(): String {
            val enclosingClassName = classDecl.fullyQualifiedName.get()
            val strRep = "${classDecl::class.simpleName} (`${enclosingClassName}` ${classDecl.fullRangeString})"

            return "NestParent-of $strRep"
        }
    }

    interface TransitiveCallTarget

    /**
     * The declaration is included because [resolvedMethodDecl] overrides it.
     */
    data class TransitiveOverriddenCallTarget(
        val resolvedMethodDecl: ResolvedMethodDeclaration
    ) : TransitivelyReferenced(), TransitiveCallTarget {

        val methodDecl get() = resolvedMethodDecl.toTypedAstOrNull<MethodDeclaration>(null)

        override fun toReasonString(): String = "Transitive-Overridden-MethodCall `${resolvedMethodDecl.qualifiedSignature}`"
    }

    /**
     * The declaration is included because [callExpr] potentially invokes this method as part of dynamic dispatch.
     */
    data class TransitiveMethodCallTarget(val callExpr: MethodCallExpr)
        : SingleDependentTransitiveReference<MethodCallExpr>(), TransitiveCallTarget {

        override val dependentNode: MethodCallExpr
            get() = callExpr

        override fun toReasonString(): String = "Transitive-Target-of-MethodCall `$callExpr` (${callExpr.fullRangeString})"
    }

    /**
     * The declaration is included because it overrides a method within a library, meaning that the library may call
     * this method via dynamic dispatch.
     */
    data class TransitiveLibraryCallTarget(
        val baseDecls: Set<ResolvedMethodDeclaration>
    ) : TransitivelyReferenced(), TransitiveCallTarget {

        override fun toReasonString(): String = "Transitive-Target-of-LibraryMethod `${baseDecls.joinToString("; ") { it.qualifiedSignature }}`"
    }

    /**
     * The declaration is included because the constructor is required for the class to compile.
     */
    abstract class TransitiveCtor : SingleDependentTransitiveReference<Node>() {

        abstract val classDecl: TypeDeclaration<*>
        abstract val ctorDecl: ConstructorDeclaration?

        override val dependentNode: Node
            get() = ctorDecl ?: classDecl
    }

    /**
     * The declaration is included because the constructor is required for the class to compile because its superclass
     * does not have a no-argument constructor.
     */
    data class TransitiveCtorForClass(
        override val classDecl: TypeDeclaration<*>,
        override val ctorDecl: ConstructorDeclaration?
    ) : TransitiveCtor() {

        override fun toReasonString(): String = "Transitive-Ctor-for-Class `${classDecl.fullyQualifiedName.getOrNull()}`"
    }

    /**
     * The declaration is included because the constructor is invoked (explicitly or implicitly) by a subclass
     * constructor.
     */
    data class TransitiveCtorForSubclass(
        override val classDecl: TypeDeclaration<*>,
        override val ctorDecl: ConstructorDeclaration?
    ) : TransitiveCtor() {

        override fun toReasonString(): String = "Transitive-Ctor-for-Subclass `${classDecl.fullyQualifiedName.getOrNull()}`"
    }

    /**
     * The node is included because it is a part of [execDecl], and [execDecl] is included.
     */
    data class TransitiveNodeByExecDecl(val execDecl: ExecutableDeclaration<*>) : SingleDependentTransitiveReference<BodyDeclaration<*>>() {

        override val dependentNode: BodyDeclaration<*>
            get() = with(execDecl.node) {
                if (this is BodyDeclaration<*>) this else findAncestor(BodyDeclaration::class.java).get()
            }

        override fun toReasonString(): String = "Transitive-Node-by-ExecDecl `${execDecl.qualifiedSignature ?: execDecl.qualifiedName}` (${execDecl.node.fullRangeString})"
    }

    /**
     * The node is included because it is a part of [typeName], and [typeName] is included.
     */
    data class TransitiveNodeByTypeName(val typeName: ClassOrInterfaceType) : SingleDependentTransitiveReference<ClassOrInterfaceType>() {

        override val dependentNode: ClassOrInterfaceType
            get() = typeName

        override fun toReasonString(): String = "Transitive-Node-by-TypeName `$typeName` (${typeName.fullRangeString})"
    }

    /**
     * The type declaration is included because the type is included as part of a full type name.
     *
     * The difference between this and [ReferencedByTypeName] is that [ReferencedByTypeName] refers to a type name which
     * is used in the top-level, such as the type of a variable declaration or class supertype, whereas this refers to a
     * type name which is nested as part of a larger type name, such as a type argument.
     */
    data class TransitiveNestedTypeName(val typeName: ClassOrInterfaceType) : SingleDependentTransitiveReference<ClassOrInterfaceType>() {

        override val dependentNode: ClassOrInterfaceType
            get() = typeName

        override fun toReasonString(): String = "Transitive-Nested-TypeName of `$typeName` (${typeName.fullRangeString})"
    }

    /**
     * The declaration is included because the declaration is included as part of the header of a [CallableDeclaration].
     */
    data class TransitiveCallableHeader(val callableDecl: CallableDeclaration<*>) : SingleDependentTransitiveReference<CallableDeclaration<*>>() {

        override val dependentNode: CallableDeclaration<*>
            get() = callableDecl

        override fun toReasonString(): String = "Transitive-Callable-Header of `${dependentNode.getQualifiedSignature(null)}`"
    }

    /**
     * The declaration is included because the declaration is included as part of an
     * [ExplicitConstructorInvocationStmt].
     */
    data class TransitiveExplicitCtorArgument(
        val explicitCtorInvocationStmt: ExplicitConstructorInvocationStmt
    ) : SingleDependentTransitiveReference<ExplicitConstructorInvocationStmt>() {

        override val dependentNode: ExplicitConstructorInvocationStmt
            get() = explicitCtorInvocationStmt

        override fun toReasonString(): String = "Transitive-Arg-for-ExplicitCtor of `${dependentNode.findAncestor(ConstructorDeclaration::class.java).getOrNull()?.getQualifiedSignature(null)}`"
    }

    /**
     * The declaration is included because it is a supertype of [classDecl].
     *
     * This differs from [ClassSupertype] in that it will delegate to `isUnusedForSuperclassRemovalData` to
     * determine usability of this node.
     */
    data class TransitiveClassSupertype(val classDecl: TypeDeclaration<*>) : SingleDependentTransitiveReference<TypeDeclaration<*>>() {

        override val dependentNode: TypeDeclaration<*>
            get() = classDecl

        override fun toReasonString(): String {
            val enclosingClassName = classDecl.findCompilationUnit()
                .flatMap { it.primaryType }
                .flatMap { it.fullyQualifiedName }
                .get()

            val strRep = "${classDecl::class.simpleName} (`${enclosingClassName}` ${classDecl.fullRangeString})"

            return "Transitive-Supertype-of $strRep"
        }
    }

    //</editor-fold>
}