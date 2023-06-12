package com.derppening.researchprojecttoolkit.tool.transform

import com.derppening.researchprojecttoolkit.model.ExecutableDeclaration
import com.derppening.researchprojecttoolkit.model.ReferenceTypeLikeDeclaration
import com.derppening.researchprojecttoolkit.model.ReferenceTypeLikeNode
import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.tool.reachability.ReachableReason
import com.derppening.researchprojecttoolkit.util.*
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.Node
import com.github.javaparser.ast.body.*
import com.github.javaparser.ast.expr.AssignExpr
import com.github.javaparser.ast.expr.Expression
import com.github.javaparser.ast.expr.ObjectCreationExpr
import com.github.javaparser.resolution.declarations.ResolvedConstructorDeclaration
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration
import com.github.javaparser.resolution.declarations.ResolvedMethodLikeDeclaration
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration
import hk.ust.cse.castle.toolkit.jvm.util.RuntimeAssertions.unreachable
import java.util.*
import kotlin.jvm.optionals.getOrNull

class TagStaticAnalysisMemberUnusedDecls(
    override val reducerContext: ReducerContext,
    private val enableAssertions: Boolean
) : TagUnusedDecls() {

    private fun isTypeReachable(typeDecl: TypeDeclaration<*>): Boolean =
        isTypeReachable(reducerContext, typeDecl, enableAssertions = enableAssertions, noCache = false)

    private fun markBodyDeclsAsUnreachable(typeDecl: TypeDeclaration<*>) =
        markBodyDeclsAsUnreachable(reducerContext, typeDecl)

    private fun removeOverrideAnnotation(methodDecl: MethodDeclaration) =
        removeOverrideAnnotation(reducerContext, methodDecl)

    private fun markInheritedNestedClasses(typeDecl: TypeDeclaration<*>) =
        markInheritedNestedClasses(reducerContext, typeDecl)

    private fun decideForConstructor(
        ctorDecl: ConstructorDeclaration,
        resolvedCtorDecl: ResolvedConstructorDeclaration = reducerContext.resolveDeclaration(ctorDecl)
    ): NodeTransformDecision = decideForConstructor(
        reducerContext,
        ctorDecl,
        resolvedCtorDecl,
        enableAssertions = enableAssertions,
        noCache = false
    )

    private fun decideForMethod(
        methodDecl: MethodDeclaration,
        resolvedMethodDecl: ResolvedMethodDeclaration = reducerContext.resolveDeclaration(methodDecl)
    ): NodeTransformDecision = decideForMethod(
        reducerContext,
        methodDecl,
        resolvedMethodDecl,
        enableAssertions = enableAssertions,
        noCache = false
    )

    /**
     * Removes all types in [namedTypesInCU] if the type is unreachable, and no nested type within it is reachable.
     */
    private fun removeUnreachableTypes(namedTypesInCU: Collection<TypeDeclaration<*>>): Collection<TypeDeclaration<*>> {
        // A type is definitely-unreachable if it is unreachable, and no nested type within it is reachable
        val (reachableTypes, unreachableTypes) = namedTypesInCU
            .partition { isTypeReachable(it) }

        unreachableTypes.forEach {
            it.transformDecisionData = NodeTransformDecision.REMOVE

            // Mark all class members (except nested classes) as unreachable
            markBodyDeclsAsUnreachable(it)
        }

        // Also mark all members of classes acting as nest parents as unreachable
        reachableTypes
            .onEach {
                it.transformDecisionData = NodeTransformDecision.NO_OP
            }
            .filter {
                isUnusedForSupertypeRemoval(it)
            }
            .forEach {
                it.isUnusedForSupertypeRemovalData = true

                markBodyDeclsAsUnreachable(it)
                markInheritedNestedClasses(it)
            }

        return reachableTypes
    }

    /**
     * Tries to remove the `@Override` annotation for the [methodDecl].
     *
     * @param overriddenMethods All methods which this [methodDecl] overrides.
     * @return `true` if the `@Override` annotation is marked for removal.
     */
    private fun tryRemoveOverrideAnnotation(
        methodDecl: MethodDeclaration,
        overriddenMethods: Set<ResolvedMethodDeclaration>
    ): Boolean {
        fun canRemoveOverrideAnnotation(): Boolean {
            // Remove the `@Override` annotation if it does not override any method
            if (overriddenMethods.isEmpty()) {
                return true
            }

            val allAstMethodsInHierarchy = overriddenMethods
                .map { it.toTypedAstOrNull<MethodDeclaration>(reducerContext) }
            return if (allAstMethodsInHierarchy.all { it != null }) {
                allAstMethodsInHierarchy
                    .filterNotNull()
                    .all {
                        decideForMethod(it) == NodeTransformDecision.REMOVE
                    }
            } else false
        }

        return if (canRemoveOverrideAnnotation()) {
            removeOverrideAnnotation(methodDecl)
            true
        } else false
    }

    private fun tryRemoveOrDummy(callableDecl: CallableDeclaration<*>) {
        val resolvedMethodDecl = reducerContext.resolveDeclaration<ResolvedMethodLikeDeclaration>(callableDecl)

        when (callableDecl) {
            is ConstructorDeclaration -> {
                resolvedMethodDecl as ResolvedConstructorDeclaration

                callableDecl.transformDecisionData = decideForConstructor(callableDecl, resolvedMethodDecl)
            }
            is MethodDeclaration -> {
                resolvedMethodDecl as ResolvedMethodDeclaration

                callableDecl.transformDecisionData = decideForMethod(callableDecl, resolvedMethodDecl)

                tryRemoveOverrideAnnotation(callableDecl, reducerContext.getOverriddenMethods(resolvedMethodDecl, true))
            }
            else -> unreachable()
        }
    }

    private fun tryRemove(fieldDecl: VariableDeclarator) {
        fieldDecl.transformDecisionData = decideForFieldDecl(reducerContext, fieldDecl, enableAssertions, noCache = false)
    }

    private fun tryRemove(enumConstantDecl: EnumConstantDeclaration) {
        enumConstantDecl.transformDecisionData = decideForEnumConstant(reducerContext, enumConstantDecl, enableAssertions, noCache = false)
    }

    override fun transform(cu: CompilationUnit) {
        verifyIsInvokedOnce(cu)

        val reachableTypes = removeUnreachableTypes(cu.findAll { it.canBeReferencedByName })

        val cuCallables: List<CallableDeclaration<*>> = reachableTypes
            .flatMap { it.constructors + it.methods }
            .filter { it.containingType.canBeReferencedByName }
        val cuFields = reachableTypes
            .flatMap { it.fields }
            .filter { it.containingType.canBeReferencedByName }
            .flatMap { it.variables }

        cuCallables.forEach { tryRemoveOrDummy(it) }
        cuFields.forEach { tryRemove(it) }

        val cuEnumConstants = reachableTypes
            .flatMap { (it as? EnumDeclaration)?.entries.orEmpty() }
            .filter { it.containingType.canBeReferencedByName }
        val cuEnumBodyCallables = cuEnumConstants
            .flatMap { it.classBody.filterIsInstance<CallableDeclaration<*>>() }
            .map { it.asMethodDeclaration() }
            .filter { it.containingType.canBeReferencedByName }
        val cuEnumBodyFields = cuEnumConstants
            .flatMap { it.classBody.filterIsInstance<FieldDeclaration>() }
            .map { it.asFieldDeclaration() }
            .filter { it.containingType.canBeReferencedByName }
            .flatMap { it.variables }

        cuEnumConstants.forEach { tryRemove(it) }
        cuEnumBodyCallables.forEach { tryRemoveOrDummy(it) }
        cuEnumBodyFields.forEach { tryRemove(it) }

        val reachableAnonClasses = cu.findAll<ObjectCreationExpr> {
            it.anonymousClassBody.isPresent && !it.isUnusedForDummyData && !it.isUnusedForRemovalData
        }

        val anonClassCallables = reachableAnonClasses
            .flatMap { objectExpr ->
                objectExpr.anonymousClassBody.get()
                    .filterIsInstance<CallableDeclaration<*>>()
                    .filter { it.parentNode.get() == objectExpr }
            }
        val anonClassFields = reachableAnonClasses
            .flatMap { objectExpr ->
                objectExpr.anonymousClassBody.get()
                    .filterIsInstance<FieldDeclaration>()
                    .filter { it.parentNode.get() == objectExpr }
                    .flatMap { it.variables }
            }

        anonClassCallables.forEach { tryRemoveOrDummy(it) }
        anonClassFields.forEach { tryRemove(it) }
    }

    companion object {

        fun removeOverrideAnnotation(reducerContext: ReducerContext, methodDecl: MethodDeclaration) {
            methodDecl.annotations
                .singleOrNull {
                    reducerContext.resolveDeclaration<ResolvedReferenceTypeDeclaration>(it)
                        .qualifiedName == "java.lang.Override"
                }
                ?.let {
                    it.transformDecisionData = NodeTransformDecision.REMOVE
                }
        }

        /**
         * Marks all members in [typeDecl] as removable, except for nested classes.
         */
        fun markBodyDeclsAsUnreachable(reducerContext: ReducerContext, typeDecl: TypeDeclaration<*>) {
            typeDecl.members
                .forEach { bodyDecl ->
                    when (bodyDecl) {
                        is CallableDeclaration<*> -> {
                            bodyDecl.transformDecisionData = NodeTransformDecision.REMOVE

                            if (bodyDecl is MethodDeclaration) {
                                removeOverrideAnnotation(reducerContext, bodyDecl)
                            }
                        }
                        is FieldDeclaration -> {
                            bodyDecl.variables
                                .forEach { it.transformDecisionData = NodeTransformDecision.REMOVE }
                        }
                        is InitializerDeclaration -> {
                            bodyDecl.transformDecisionData = NodeTransformDecision.DUMMY
                        }
                        is EnumConstantDeclaration -> {
                            bodyDecl.transformDecisionData = NodeTransformDecision.REMOVE
                        }
                        else -> Unit
                    }
                }
        }

        /**
         * Mark all reference types which references a superclass nested type by unqualified name for replacement.
         */
        fun markInheritedNestedClasses(reducerContext: ReducerContext, typeDecl: TypeDeclaration<*>) {
            val resolvedTypeDecl = reducerContext.resolveDeclaration<ResolvedReferenceTypeDeclaration>(typeDecl)
            val nestedClassNodes = reducerContext.resolveRefTypeNodeDeclsAsSequence(typeDecl)
                .mapNotNull { (k, v) ->
                    v?.let { ReferenceTypeLikeNode.create(k) to it }
                }
                .associateTo(TreeMap(Comparator.comparing(ReferenceTypeLikeNode<*>::node, NodeAstComparator()))) { (k, v) ->
                    k to v
                }

            val allAncestorsQName = reducerContext.symbolSolverCache
                .getAllAncestors(resolvedTypeDecl)
                .map { it.qualifiedName }

            // If the qualified name of this class is a substring of any ancestors, mark it for rewrite
            nestedClassNodes.forEach { (node, resolvedDecl) ->
                val nestedClassQName = resolvedDecl.qualifiedName

                if (allAncestorsQName.any { nestedClassQName.startsWith(it) }) {
                    node.rewriteNestedTypeNameData = resolvedDecl
                }
            }
        }

        /**
         * Tries to remove or dummy a method within a [TypeDeclaration]-like structure by inheriting the removal/dummy
         * status of its parent declaration.
         *
         * For instance, a method within an [ObjectCreationExpr] will inherit the dummy/removal status of the method or
         * field it is a part of.
         */
        private inline fun <reified N : Node> tryRemoveOrDummyMethodInTypeLikeDecl(
            reducerContext: ReducerContext,
            methodDecl: MethodDeclaration,
            resolvedMethodDecl: ResolvedMethodDeclaration,
            overriddenMethods: Set<ResolvedMethodDeclaration>
        ): NodeTransformDecision {
            val namedAstClassAncestorIdx = methodDecl.ancestorsAsSequence().indexOfFirst { it is TypeDeclaration<*> }
            val astBodyAncestorIdx = methodDecl.ancestorsAsSequence().indexOfFirst { it is N }

            if (astBodyAncestorIdx == -1 || namedAstClassAncestorIdx <= astBodyAncestorIdx) {
                return NodeTransformDecision.NO_OP
            }
            if (overriddenMethods.isEmpty()) {
                return NodeTransformDecision.REMOVE
            }
            if (reducerContext.isMethodOverridesLibraryMethod(resolvedMethodDecl)) {
                return NodeTransformDecision.NO_OP
            }

            val containerDecl = when (val bodyDecl = methodDecl.ancestorsAsSequence().filterIsInstance<N>().first()) {
                is BodyDeclaration<*> -> bodyDecl.parentContainer!!
                is Expression -> bodyDecl.parentContainer!!
                else -> unreachable("Don't know how to obtain parent container for node type ${bodyDecl::class.simpleName}")
            }

            // Inherit the dummy/removal status of the parent container
            return if (containerDecl.isUnusedForDummyData || containerDecl.isUnusedForRemovalData) {
                NodeTransformDecision.REMOVE
            } else {
                NodeTransformDecision.NO_OP
            }
        }

        internal fun decideForConstructor(
            reducerContext: ReducerContext,
            ctorDecl: ConstructorDeclaration,
            enableAssertions: Boolean,
            noCache: Boolean
        ): NodeTransformDecision = decideForConstructor(
            reducerContext,
            ctorDecl,
            enableAssertions,
            NodeRangeTreeSet(),
            noCache = noCache
        )

        private fun decideForConstructor(
            reducerContext: ReducerContext,
            ctorDecl: ConstructorDeclaration,
            enableAssertions: Boolean,
            transitiveBacktrace: SortedSet<Node>,
            noCache: Boolean
        ): NodeTransformDecision = decideForConstructor(
            reducerContext,
            ctorDecl,
            reducerContext.resolveDeclaration(ctorDecl),
            enableAssertions,
            transitiveBacktrace,
            noCache = noCache
        )

        private fun decideForConstructor(
            reducerContext: ReducerContext,
            ctorDecl: ConstructorDeclaration,
            resolvedCtorDecl: ResolvedConstructorDeclaration,
            enableAssertions: Boolean,
            noCache: Boolean
        ): NodeTransformDecision = decideForConstructor(
            reducerContext,
            ctorDecl,
            resolvedCtorDecl,
            enableAssertions,
            NodeRangeTreeSet(),
            noCache = noCache
        )

        private fun decideForConstructor(
            reducerContext: ReducerContext,
            ctorDecl: ConstructorDeclaration,
            @Suppress("UNUSED_PARAMETER") resolvedCtorDecl: ResolvedConstructorDeclaration,
            enableAssertions: Boolean,
            transitiveBacktrace: SortedSet<Node>,
            noCache: Boolean
        ): NodeTransformDecision {
            if (!noCache) {
                // Return cached result if it is present
                ctorDecl.transformDecisionData?.let { return it }
            }

            if (ctorDecl in transitiveBacktrace) {
                return NodeTransformDecision.REMOVE
            }

            val isTransitiveReachable = isTransitiveDependentReachable(
                reducerContext,
                ctorDecl,
                true,
                transitiveBacktrace,
                noCache = noCache
            )

            val inclusionReasons = ctorDecl.inclusionReasonsData.synchronizedWith { toList() }

            if (isTransitiveReachable) {
                return if (inclusionReasons.filterIsInstance<ReachableReason.DirectlyReferenced>().any()) {
                    NodeTransformDecision.NO_OP
                } else {
                    NodeTransformDecision.DUMMY
                }
            }

            if (inclusionReasons.filterIsInstance<ReachableReason.TransitiveCtorForClass>().any()) {
                val typeReachable = isTypeReachable(
                    reducerContext,
                    ctorDecl.findAncestor(TypeDeclaration::class.java).get(),
                    enableAssertions,
                    transitiveBacktrace.copy(),
                    noCache = noCache
                )

                if (typeReachable) {
                    return NodeTransformDecision.DUMMY
                }
            }

            return NodeTransformDecision.REMOVE
        }

        internal fun decideForMethod(
            reducerContext: ReducerContext,
            methodDecl: MethodDeclaration,
            enableAssertions: Boolean,
            noCache: Boolean
        ) = decideForMethod(
            reducerContext,
            methodDecl,
            reducerContext.resolveDeclaration(methodDecl),
            enableAssertions,
            NodeRangeTreeSet(),
            noCache = noCache
        )

        private fun decideForMethod(
            reducerContext: ReducerContext,
            methodDecl: MethodDeclaration,
            enableAssertions: Boolean,
            transitiveBacktrace: SortedSet<Node>,
            noCache: Boolean
        ) = decideForMethod(
            reducerContext,
            methodDecl,
            reducerContext.resolveDeclaration(methodDecl),
            enableAssertions,
            transitiveBacktrace,
            noCache = noCache
        )
        private fun decideForMethod(
            reducerContext: ReducerContext,
            methodDecl: MethodDeclaration,
            resolvedMethodDecl: ResolvedMethodDeclaration,
            enableAssertions: Boolean,
            noCache: Boolean
        ): NodeTransformDecision = decideForMethod(
            reducerContext,
            methodDecl,
            resolvedMethodDecl,
            enableAssertions,
            NodeRangeTreeSet(),
            noCache = noCache
        )

        private fun decideForMethod(
            reducerContext: ReducerContext,
            methodDecl: MethodDeclaration,
            resolvedMethodDecl: ResolvedMethodDeclaration,
            enableAssertions: Boolean,
            transitiveBacktrace: SortedSet<Node>,
            noCache: Boolean
        ): NodeTransformDecision {
            if (!noCache) {
                // Return cached result if it is present
                methodDecl.transformDecisionData?.let { return it }
            }

            if (methodDecl in transitiveBacktrace) {
                return NodeTransformDecision.REMOVE
            }

            val overriddenMethods = reducerContext.getOverriddenMethods(resolvedMethodDecl, true)

            // If this method is part of the entrypoint, unconditionally keep it
            val inclusionReasons = methodDecl.inclusionReasonsData.synchronizedWith { toList() }
            if (inclusionReasons.any { it is ReachableReason.ByEntrypoint }) {
                return NodeTransformDecision.NO_OP
            }

            // We ignore all nodes which was traversed by isTransitiveDependentReachable except ourselves, because later
            // we need to determine the reachability of methods in the same class hierarchy, and the presence of other
            // nodes may affect this determination.
            if (isTransitiveDependentReachable(
                    reducerContext,
                    methodDecl,
                    enableAssertions,
                    transitiveBacktrace.copy(),
                    noCache
                )
            ) {
                return NodeTransformDecision.NO_OP
            }
            transitiveBacktrace.add(methodDecl)

            when (val declaringType = methodDecl.containingType) {
                is ReferenceTypeLikeDeclaration.AnonClassDecl -> {
                    // Handle methods in anon classes - Always remove unused callables in anon classes
                    val anonClassResult = tryRemoveOrDummyMethodInTypeLikeDecl<ObjectCreationExpr>(
                        reducerContext,
                        methodDecl,
                        resolvedMethodDecl,
                        overriddenMethods
                    )
                    if (anonClassResult != NodeTransformDecision.NO_OP) {
                        return anonClassResult
                    }
                }

                is ReferenceTypeLikeDeclaration.EnumConstDecl -> {
                    // Similarly, handle methods in the body of enum constants
                    val enumConstResult = tryRemoveOrDummyMethodInTypeLikeDecl<EnumConstantDeclaration>(
                        reducerContext,
                        methodDecl,
                        resolvedMethodDecl,
                        overriddenMethods
                    )
                    if (enumConstResult != NodeTransformDecision.NO_OP) {
                        return enumConstResult
                    }
                }

                is ReferenceTypeLikeDeclaration.TypeDecl -> {
                    val typeResult = isTypeReachable(
                        reducerContext,
                        declaringType.node,
                        enableAssertions,
                        transitiveBacktrace.copy(),
                        noCache = noCache
                    )
                    if (!typeResult) {
                        return NodeTransformDecision.REMOVE
                    }
                }
            }

            val resolvedTypesUsingImplFromMethod = reducerContext.getMethodImplDependentClasses(resolvedMethodDecl)
                .filter { it.toTypedAstOrNull<TypeDeclaration<*>>(reducerContext) != null }

            /**
             * Determines whether the [directlyOverriddenMethod] is needed in [methodDepClass].
             */
            fun isMethodRequiredInType(
                directlyOverriddenMethod: ResolvedMethodDeclaration,
                methodDepClass: ResolvedReferenceTypeDeclaration
            ): Boolean {
                val overriddenMethodAst = directlyOverriddenMethod.toTypedAst<MethodDeclaration>(reducerContext)

                if (decideForMethod(
                        reducerContext,
                        overriddenMethodAst,
                        directlyOverriddenMethod,
                        enableAssertions,
                        transitiveBacktrace,
                        noCache = noCache
                    ) != NodeTransformDecision.REMOVE
                ) {
                    return true
                }

                return reducerContext.getOverriddenMethods(directlyOverriddenMethod, true, methodDepClass)
                    .any { overriddenMethod ->
                        (!reducerContext.typeSolver.isSolvedBySourceSolvers(overriddenMethod.declaringType()) && overriddenMethod.isAbstract) ||
                                overriddenMethod.toTypedAstOrNull<MethodDeclaration>(reducerContext)
                                    ?.let {
                                        decideForMethod(
                                            reducerContext,
                                            it,
                                            overriddenMethod,
                                            enableAssertions,
                                            transitiveBacktrace,
                                            noCache = noCache
                                        ) != NodeTransformDecision.REMOVE
                                    } == true
                    }
            }

            // If any type inherits our implementation and is reachable, dummy the method
            if (resolvedTypesUsingImplFromMethod.any { isMethodRequiredInType(resolvedMethodDecl, it) }) {
                return NodeTransformDecision.DUMMY
            }

            // If we only override abstract methods, and the overridden methods are either from libraries or is
            // compile-time used, dummy this method
            if (overriddenMethods.isNotEmpty() && overriddenMethods.all { it.isAbstract }) {
                val overridesLib = overriddenMethods.any { !reducerContext.typeSolver.isSolvedBySourceSolvers(it.declaringType()) }
                if (overridesLib) {
                    return NodeTransformDecision.DUMMY
                }

                // Get all classes which depends on the implementation of any overridden method or ourselves. If any
                // class uses any of these method, it means that that particular (abstract) method needs to be retained,
                // and since we inherit from that abstract method, we need to provide our implementation
                val definitelyCompileTimeUsed = overriddenMethods
                    .flatMap { overriddenMethod ->
                        reducerContext.getMethodImplDependentClasses(overriddenMethod).map { overriddenMethod to it }
                    }
                    .filter { reducerContext.typeSolver.isSolvedBySourceSolvers(it.second) }
                    .any { (overriddenMethod, methodDepClass) ->
                        isMethodRequiredInType(overriddenMethod, methodDepClass)
                    }

                if (definitelyCompileTimeUsed) {
                    return NodeTransformDecision.DUMMY
                }
            }

            return NodeTransformDecision.REMOVE
        }

        internal fun decideForFieldDecl(
            reducerContext: ReducerContext,
            fieldVarDecl: VariableDeclarator,
            enableAssertions: Boolean,
            noCache: Boolean
        ): NodeTransformDecision = decideForFieldDecl(
            reducerContext,
            fieldVarDecl,
            enableAssertions,
            NodeRangeTreeSet(),
            noCache
        )

        private fun decideForFieldDecl(
            reducerContext: ReducerContext,
            fieldVarDecl: VariableDeclarator,
            enableAssertions: Boolean,
            transitiveBacktrace: SortedSet<Node>,
            noCache: Boolean
        ): NodeTransformDecision {
            // Return cached result if it is present
            if (!noCache) {
                fieldVarDecl.transformDecisionData?.let { return it }
            }

            if (fieldVarDecl in transitiveBacktrace) {
                return NodeTransformDecision.REMOVE
            }

            val fieldDecl = fieldVarDecl.parentNode.get() as FieldDeclaration

            if (isTransitiveDependentReachable(reducerContext, fieldVarDecl, enableAssertions, transitiveBacktrace, noCache = noCache)) {
                return NodeTransformDecision.NO_OP
            }

            val containingType = fieldVarDecl.findAncestor(TypeDeclaration::class.java).get()

            // First we determine whether the containing type is only used for static nested classes.
            // If they are, remove them, because loading static classes does not initialize any fields in our class.
            val containingTypeReasons = containingType.inclusionReasonsData.synchronizedWith { toList() }
            val containingTypeNestParentReasons = containingTypeReasons.filterIsInstance<ReachableReason.NestParent>()
            if (containingTypeNestParentReasons.size == containingTypeReasons.size) {
                if (containingTypeNestParentReasons.all { it.classDecl.isStatic }) {
                    return NodeTransformDecision.REMOVE
                }
            }

            if (!isTypeReachable(
                    reducerContext,
                    containingType,
                    enableAssertions,
                    // Copy required since isTypeInstantiated will reuse this transitiveBacktrace
                    transitiveBacktrace.copy(),
                    noCache = noCache
            )) {
                return NodeTransformDecision.REMOVE
            }

            // At this point, the field is not directly reachable but its declaring type is reachable

            // If this field is non-static and the declaring type is never instantiated, we can be removed
            if (!fieldDecl.isStatic) {
                if (!isTypeInstantiated(
                        reducerContext,
                        fieldDecl.containingType,
                        null,
                        enableAssertions,
                        transitiveBacktrace,
                        noCache = noCache
                )) {
                    return NodeTransformDecision.REMOVE
                }
            }

            // We keep this field for side effects, i.e. if it contains an initializer or any expression in a
            // constructor or initializer block assigns a value to this variable.
            if (fieldVarDecl.initializer.isPresent) {
                return NodeTransformDecision.NO_OP
            }

            val fieldInitExprs = containingType.members
                .mapNotNull {
                    when (it) {
                        is ConstructorDeclaration -> it.body
                        is InitializerDeclaration -> it.body
                        else -> null
                    }
                }
                .flatMap { it.findAll<AssignExpr>() }
                .filter { it.containsInitFieldVarData }
                .filter { NodeAstComparator.compare(it.initFieldVarData, fieldVarDecl) == 0 }
            return if (fieldInitExprs.isNotEmpty()) {
                NodeTransformDecision.NO_OP
            } else {
                NodeTransformDecision.REMOVE
            }
        }

        internal fun decideForEnumConstant(
            reducerContext: ReducerContext,
            enumConstantDecl: EnumConstantDeclaration,
            enableAssertions: Boolean,
            noCache: Boolean
        ): NodeTransformDecision = decideForEnumConstant(
            reducerContext,
            enumConstantDecl,
            enableAssertions,
            NodeRangeTreeSet(),
            noCache = noCache
        )

        private fun decideForEnumConstant(
            reducerContext: ReducerContext,
            enumConstantDecl: EnumConstantDeclaration,
            enableAssertions: Boolean,
            transitiveBacktrace: SortedSet<Node>,
            noCache: Boolean
        ): NodeTransformDecision {
            if (!noCache) {
                enumConstantDecl.transformDecisionData?.let { return it }
            }

            if (enumConstantDecl in transitiveBacktrace) {
                return NodeTransformDecision.REMOVE
            }

            return if (!isTransitiveDependentReachable(
                    reducerContext,
                    enumConstantDecl,
                    enableAssertions,
                    transitiveBacktrace,
                    noCache = noCache
                )
            ) {
                NodeTransformDecision.REMOVE
            } else {
                NodeTransformDecision.NO_OP
            }
        }

        internal fun isUnusedForSupertypeRemoval(typeDecl: TypeDeclaration<*>): Boolean {
            return with(typeDecl) {
                inclusionReasonsData.synchronizedWith { all { it is ReachableReason.NestParent } } &&
                        members.filterIsInstance<TypeDeclaration<*>>().all { it.isStatic }
            }
        }

        /**
         * Checks whether [node] is directly or transitively-dependently reachable.
         */
        internal fun isTransitiveDependentReachable(
            reducerContext: ReducerContext,
            node: Node,
            enableAssertions: Boolean,
            noCache: Boolean
        ) = isTransitiveDependentReachable(
            reducerContext,
            node,
            enableAssertions,
            NodeRangeTreeSet(),
            noCache = noCache
        )

        private fun isTypeInstantiated(
            reducerContext: ReducerContext,
            refLikeDecl: ReferenceTypeLikeDeclaration<*>,
            methodDecl: MethodDeclaration?,
            enableAssertions: Boolean,
            transitiveBacktrace: SortedSet<Node>,
            noCache: Boolean
        ): Boolean {
            // Check whether the containing type is created by checking if any method-dependent types are created
            return when (refLikeDecl) {
                is ReferenceTypeLikeDeclaration.TypeDecl -> {
                    when (val containingTypeDecl = refLikeDecl.node) {
                        is ClassOrInterfaceDeclaration -> {
                            val relevantResolvedClasses = if (methodDecl != null) {
                                val resolvedMethodDecl = reducerContext.resolveDeclaration<ResolvedMethodDeclaration>(methodDecl)
                                reducerContext.getMethodImplDependentClasses(resolvedMethodDecl)
                            } else {
                                setOf(reducerContext.resolveDeclaration<ResolvedReferenceTypeDeclaration>(containingTypeDecl))
                            }

                            relevantResolvedClasses.any { relevantResolvedClass ->
                                when (val relevantTypeDecl = relevantResolvedClass.toTypedAstOrNull<Node>(reducerContext)
                                    ?.let { ReferenceTypeLikeDeclaration.createOrNull(it) }) {
                                    is ReferenceTypeLikeDeclaration.AnonClassDecl -> {
                                        isTransitiveDependentReachable(
                                            reducerContext,
                                            relevantTypeDecl.node,
                                            enableAssertions,
                                            transitiveBacktrace,
                                            noCache = noCache
                                        )
                                    }

                                    is ReferenceTypeLikeDeclaration.EnumConstDecl -> {
                                        decideForEnumConstant(
                                            reducerContext,
                                            relevantTypeDecl.node,
                                            enableAssertions,
                                            transitiveBacktrace,
                                            noCache = noCache
                                        ) == NodeTransformDecision.NO_OP
                                    }

                                    is ReferenceTypeLikeDeclaration.TypeDecl -> {
                                        val relevantClass = relevantTypeDecl.node
                                        val relevantInclusionReasons = relevantClass.inclusionReasonsData
                                            .synchronizedWith { toList() }

                                        if (relevantInclusionReasons.filterIsInstance<ReachableReason.EntrypointClass>().any()) {
                                            return@any true
                                        }

                                        relevantClass.instanceCreationData.any {
                                            isTransitiveDependentReachable(
                                                reducerContext,
                                                it,
                                                enableAssertions,
                                                transitiveBacktrace,
                                                noCache = noCache
                                            )
                                        }
                                    }

                                    null -> false
                                }
                            }
                        }

                        is EnumDeclaration -> {
                            containingTypeDecl.entries.any {
                                decideForEnumConstant(
                                    reducerContext,
                                    it,
                                    enableAssertions,
                                    transitiveBacktrace,
                                    noCache = noCache
                                ) == NodeTransformDecision.NO_OP
                            }
                        }

                        else -> {
                            true
                        }
                    }
                }

                is ReferenceTypeLikeDeclaration.AnonClassDecl -> {
                    refLikeDecl.node
                        .ancestorsAsSequence()
                        .firstNotNullOf { ExecutableDeclaration.createOrNull(it) }
                        .let {
                            when (it) {
                                is ExecutableDeclaration.CtorDecl -> {
                                    val ctorDecl = it.node
                                    val decision = decideForConstructor(
                                        reducerContext,
                                        ctorDecl,
                                        enableAssertions,
                                        transitiveBacktrace,
                                        noCache = noCache
                                    )

                                    when (decision) {
                                        NodeTransformDecision.NO_OP -> true
                                        NodeTransformDecision.DUMMY -> {
                                            val isInExplicitCtorCtx = ctorDecl.explicitCtorInvocationStmt
                                                ?.findAll<ObjectCreationExpr>()
                                                .orEmpty()
                                                .any { it === refLikeDecl.node }
                                            val isUsedToInitFieldVar = refLikeDecl.node
                                                .findAncestor(AssignExpr::class.java).getOrNull()
                                                ?.containsInitFieldVarData == true

                                            isInExplicitCtorCtx || isUsedToInitFieldVar
                                        }
                                        NodeTransformDecision.REMOVE -> false
                                    }
                                }

                                is ExecutableDeclaration.FieldVariable -> {
                                    decideForFieldDecl(
                                        reducerContext,
                                        it.node,
                                        enableAssertions,
                                        transitiveBacktrace,
                                        noCache = noCache
                                    ) == NodeTransformDecision.NO_OP
                                }

                                is ExecutableDeclaration.InitializerDecl -> {
                                    if (it.node.isStatic) {
                                        isTransitiveDependentReachable(
                                            reducerContext,
                                            it.node,
                                            enableAssertions,
                                            transitiveBacktrace,
                                            noCache = noCache
                                        )
                                    } else {
                                        isTypeInstantiated(
                                            reducerContext,
                                            it.node.containingType,
                                            null,
                                            enableAssertions,
                                            transitiveBacktrace,
                                            noCache = noCache
                                        )
                                    }
                                }

                                is ExecutableDeclaration.MethodDecl -> {
                                    decideForMethod(
                                        reducerContext,
                                        it.node,
                                        enableAssertions,
                                        transitiveBacktrace,
                                        noCache = noCache
                                    ) == NodeTransformDecision.NO_OP
                                }
                            }
                        }
                }

                is ReferenceTypeLikeDeclaration.EnumConstDecl -> {
                    decideForEnumConstant(
                        reducerContext,
                        refLikeDecl.node,
                        enableAssertions,
                        transitiveBacktrace,
                        noCache = noCache
                    ) == NodeTransformDecision.NO_OP
                }
            }
        }

        private fun isContainingTypeOfMethodInstantiated(
            reducerContext: ReducerContext,
            node: Node,
            enableAssertions: Boolean,
            transitiveBacktrace: SortedSet<Node>,
            noCache: Boolean
        ): Boolean {
            val methodDecl = when (node) {
                is MethodDeclaration -> node
                else -> {
                    node.findAncestor(MethodDeclaration::class.java).get()
                }
            }

            return isContainingTypeOfMethodInstantiated(
                reducerContext,
                methodDecl,
                enableAssertions = enableAssertions,
                transitiveBacktrace,
                noCache = noCache
            )
        }

        /**
         * Determines whether the containing type of a method is instantiated.
         */
        private fun isContainingTypeOfMethodInstantiated(
            reducerContext: ReducerContext,
            methodDecl: MethodDeclaration,
            enableAssertions: Boolean,
            transitiveBacktrace: SortedSet<Node>,
            noCache: Boolean
        ): Boolean = isTypeInstantiated(
            reducerContext,
            methodDecl.containingType,
            methodDecl,
            enableAssertions,
            transitiveBacktrace,
            noCache = noCache
        )

        private fun isTransitiveDependentReachable(
            reducerContext: ReducerContext,
            node: Node,
            enableAssertions: Boolean,
            transitiveBacktrace: SortedSet<Node>,
            noCache: Boolean
        ): Boolean {
            if (!transitiveBacktrace.add(node)) {
                return false
            }

            val inclusionReasons = node.inclusionReasonsData.synchronizedWith { toList() }

            if (inclusionReasons.isEmpty()) {
                return false
            }

            if (inclusionReasons.filterIsInstance<ReachableReason.ByEntrypoint>().any()) {
                return true
            }

            // Directly-Referenced nodes are reachable if its dependent node is reachable
            // This should allow us to remove nodes that are dependent on unreachable nodes
            if (inclusionReasons.filterIsInstance<ReachableReason.DirectlyReferencedByNode>().any()) {
                val directlyRef = inclusionReasons.filterIsInstance<ReachableReason.DirectlyReferencedByNode>()
                    .any {
                        isTransitiveDependentReachable(
                            reducerContext,
                            it.node,
                            enableAssertions,
                            transitiveBacktrace,
                            noCache = noCache
                        )
                    }

                if (directlyRef) {
                    return true
                }
            }

            // If this node is a child of a class field declaration (e.g. in the initializer), delegate reachability to
            // whether the field itself is reachable
            val fieldContext = node.findAncestor({ it.isFieldVar }, VariableDeclarator::class.java).getOrNull()
            if (fieldContext != null) {
                return decideForFieldDecl(
                    reducerContext,
                    fieldContext,
                    enableAssertions,
                    transitiveBacktrace,
                    noCache = noCache
                ) != NodeTransformDecision.REMOVE
            }

            // Methods which are transitive (library) call targets should always be included
            if (inclusionReasons.any { it is ReachableReason.TransitiveLibraryCallTarget || it is ReachableReason.TransitiveMethodCallTarget }) {
                val isContainerTypeReachable = isContainingTypeOfMethodInstantiated(
                    reducerContext,
                    node,
                    enableAssertions,
                    transitiveBacktrace,
                    noCache = noCache
                )

                if (isContainerTypeReachable) {
                    return true
                }
            }

            val depTransitiveReasons = inclusionReasons
                .filterIsInstance<ReachableReason.DependentTransitiveReference<*>>()
            return depTransitiveReasons.any {
                when (it) {
                    is ReachableReason.TransitiveCtor -> {
                        it.ctorDecl?.let { ctorDecl ->
                            decideForConstructor(
                                reducerContext,
                                ctorDecl,
                                enableAssertions,
                                transitiveBacktrace,
                                noCache = noCache
                            ) != NodeTransformDecision.REMOVE
                        } ?: run {
                            isTypeReachable(
                                reducerContext,
                                it.classDecl,
                                enableAssertions,
                                transitiveBacktrace.copy(),
                                noCache = noCache
                            )
                        }
                    }
                    is ReachableReason.NestParent -> {
                        isTypeReachable(
                            reducerContext,
                            it.classDecl,
                            enableAssertions,
                            transitiveBacktrace.copy(),
                            noCache = noCache
                        )
                    }
                    is ReachableReason.TransitiveNestedTypeName -> {
                        isTransitiveDependentReachable(
                            reducerContext,
                            it.typeName,
                            enableAssertions,
                            transitiveBacktrace,
                            noCache = noCache
                        )
                    }

                    is ReachableReason.TransitiveCallableHeader -> {
                        when (val callableDecl = it.dependentNode) {
                            is ConstructorDeclaration -> {
                                decideForConstructor(
                                    reducerContext,
                                    callableDecl,
                                    enableAssertions,
                                    transitiveBacktrace,
                                    noCache = noCache
                                ) != NodeTransformDecision.REMOVE
                            }

                            is MethodDeclaration -> {
                                decideForMethod(
                                    reducerContext,
                                    callableDecl,
                                    enableAssertions,
                                    transitiveBacktrace,
                                    noCache = noCache
                                ) != NodeTransformDecision.REMOVE
                            }

                            else -> unreachable()
                        }
                    }

                    is ReachableReason.TransitiveExplicitCtorArgument -> {
                        decideForConstructor(
                            reducerContext,
                            it.dependentNode.findAncestor(ConstructorDeclaration::class.java).get(),
                            enableAssertions,
                            transitiveBacktrace,
                            noCache = noCache
                        ) != NodeTransformDecision.REMOVE
                    }

                    is ReachableReason.TransitiveClassSupertype -> {
                        !isUnusedForSupertypeRemoval(it.dependentNode)
                    }

                    is ReachableReason.TransitiveNodeByExecDecl -> {
                        isTransitiveDependentReachable(
                            reducerContext,
                            it.execDecl.node,
                            enableAssertions,
                            transitiveBacktrace,
                            noCache = noCache
                        )
                    }

                    is ReachableReason.TransitiveNodeByTypeName -> {
                        isTransitiveDependentReachable(
                            reducerContext,
                            it.dependentNode,
                            enableAssertions,
                            transitiveBacktrace,
                            noCache = noCache
                        )
                    }

                    is ReachableReason.TransitiveAnnotationMember -> {
                        isTypeReachable(
                            reducerContext,
                            it.dependentNode,
                            enableAssertions,
                            transitiveBacktrace.copy(),
                            noCache = noCache
                        )
                    }

                    is ReachableReason.TransitiveClassMemberOfType -> {
                        // TransitiveClassMemberOfType is only transitively reachable if the node is part of a field
                        // variable, and the field variable is not removed
                        val fieldDecl = node.ancestorsAsSequence().filterIsInstance<FieldDeclaration>().firstOrNull()
                        if (fieldDecl != null) {
                            if (fieldDecl.isStatic) {
                                isTransitiveDependentReachable(
                                    reducerContext,
                                    it.dependentNode,
                                    enableAssertions,
                                    transitiveBacktrace,
                                    noCache = noCache
                                )
                            } else {
                                isTypeInstantiated(
                                    reducerContext,
                                    it.refLikeDecl,
                                    null,
                                    enableAssertions,
                                    transitiveBacktrace,
                                    noCache = noCache
                                )
                            }
                        } else {
                            false
                        }
                    }

                    is ReachableReason.TransitiveDeclaringTypeOfMember -> {
                        when (val memberDecl = it.memberDecl) {
                            is MethodDeclaration -> {
                                if (memberDecl.isStatic) {
                                    decideForMethod(
                                        reducerContext,
                                        memberDecl,
                                        enableAssertions,
                                        transitiveBacktrace,
                                        noCache = noCache
                                    ) != NodeTransformDecision.REMOVE
                                } else false
                            }
                            is EnumConstantDeclaration -> {
                                decideForEnumConstant(
                                    reducerContext,
                                    memberDecl,
                                    enableAssertions,
                                    transitiveBacktrace,
                                    noCache = noCache
                                ) != NodeTransformDecision.REMOVE
                            }
                            is FieldDeclaration -> {
                                if (memberDecl.isStatic) {
                                    decideForFieldDecl(
                                        reducerContext,
                                        checkNotNull(it.varDecl),
                                        enableAssertions,
                                        transitiveBacktrace,
                                        noCache = noCache
                                    ) != NodeTransformDecision.REMOVE
                                } else false
                            }
                            else -> false
                        }
                    }

                    is ReachableReason.TransitiveMethodCallTarget -> {
                        // Already handled above
                        false
                    }

                    else -> unreachable("Unhandled ReachableReason `${it::class.simpleName}` - ${it.toReasonString()}")
                }
            }
        }

        internal fun isTypeReachable(
            reducerContext: ReducerContext,
            typeDecl: TypeDeclaration<*>,
            enableAssertions: Boolean,
            noCache: Boolean
        ): Boolean = isTypeReachable(
            reducerContext,
            typeDecl,
            enableAssertions,
            NodeRangeTreeSet(),
            noCache
        )

        /**
         * Whether [typeDecl] is reachable.
         *
         * Note: [transitiveBacktrace] must be cloned ([SortedSet.copy]) when invoking this method.
         */
        private fun isTypeReachable(
            reducerContext: ReducerContext,
            typeDecl: TypeDeclaration<*>,
            enableAssertions: Boolean,
            transitiveBacktrace: SortedSet<Node>,
            noCache: Boolean
        ): Boolean {
            // Return cached result if it is present
            if (!noCache) {
                typeDecl.transformDecisionData?.let { return it != NodeTransformDecision.REMOVE }
            }

            if (typeDecl in transitiveBacktrace) {
                return false
            }

            // Reachable if any transitive reason depends on another node, and the node is directly referenced
            return isTransitiveDependentReachable(
                reducerContext,
                typeDecl,
                enableAssertions,
                transitiveBacktrace,
                noCache = noCache
            )
        }
    }
}