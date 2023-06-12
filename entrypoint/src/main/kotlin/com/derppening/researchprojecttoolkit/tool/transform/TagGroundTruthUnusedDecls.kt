package com.derppening.researchprojecttoolkit.tool.transform

import com.derppening.researchprojecttoolkit.model.ReferenceTypeLikeDeclaration
import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.tool.reachability.ReachableReason
import com.derppening.researchprojecttoolkit.tool.reducer.AbstractBaselineReducer.Companion.isUsedInCoverageOrNull
import com.derppening.researchprojecttoolkit.util.*
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.Node
import com.github.javaparser.ast.body.*
import com.github.javaparser.ast.expr.Expression
import com.github.javaparser.ast.expr.ObjectCreationExpr
import com.github.javaparser.ast.type.ClassOrInterfaceType
import com.github.javaparser.resolution.declarations.ResolvedConstructorDeclaration
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration
import com.github.javaparser.resolution.declarations.ResolvedMethodLikeDeclaration
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration
import hk.ust.cse.castle.toolkit.jvm.util.RuntimeAssertions.unreachable
import java.util.*

/**
 * Tags all declarations in the ground truth that are not reachable and is not a dependency of a reachable declaration.
 */
class TagGroundTruthUnusedDecls(
    override val reducerContext: ReducerContext,
    private val enableAssertions: Boolean
) : TagUnusedDecls() {

    /**
     * Checks whether [node] is directly or transitively-dependently reachable.
     */
    private fun isTransitiveDependentReachable(node: Node) =
        isTransitiveDependentReachable(reducerContext, node, enableAssertions, noCache = false)

    /**
     * Whether [typeDecl] is reachable.
     */
    private fun isTypeReachable(typeDecl: TypeDeclaration<*>): Boolean =
        isTypeReachable(reducerContext, typeDecl, enableAssertions, noCache = false)

    private fun markBodyDeclsAsUnreachable(typeDecl: TypeDeclaration<*>) =
        TagStaticAnalysisMemberUnusedDecls.markBodyDeclsAsUnreachable(reducerContext, typeDecl)

    private fun removeOverrideAnnotation(methodDecl: MethodDeclaration) =
        TagStaticAnalysisMemberUnusedDecls.removeOverrideAnnotation(reducerContext, methodDecl)

    private fun markInheritedNestedClasses(typeDecl: TypeDeclaration<*>) =
        TagStaticAnalysisMemberUnusedDecls.markInheritedNestedClasses(reducerContext, typeDecl)

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

        reachableTypes
            .onEach { it.transformDecisionData = NodeTransformDecision.NO_OP }
            .filter { ReferenceTypeLikeDeclaration.create(it).isBaselineLoadedData != true }
            .forEach { typeDecl ->
                typeDecl.members.filterIsInstance<InitializerDeclaration>()
                    .forEach { it.transformDecisionData = NodeTransformDecision.DUMMY }
            }

        // Also mark all members of classes acting as nest parents as unreachable
        reachableTypes
            .filter { TagStaticAnalysisMemberUnusedDecls.isUnusedForSupertypeRemoval(it) }
            .forEach {
                it.isUnusedForSupertypeRemovalData = true

                markBodyDeclsAsUnreachable(it)
                markInheritedNestedClasses(it)
            }

        return reachableTypes
    }

    private fun tryRemoveOverrideAnnotation(
        methodDecl: MethodDeclaration,
        overriddenMethods: Set<ResolvedMethodDeclaration>
    ) {
        fun canRemoveOverrideAnnotation(): Boolean {
            // Remove the `@Override` annotation if it does not override any method
            if (overriddenMethods.isEmpty()) {
                return true
            }

            val allAstMethodsInHierarchy = overriddenMethods
                .map { it.toTypedAstOrNull<MethodDeclaration>(reducerContext) }

            return allAstMethodsInHierarchy
                .takeIf { allMethods -> allMethods.all { it != null } }
                ?.map { checkNotNull(it) }
                ?.let { allMethods ->
                    allMethods.none { isTransitiveDependentReachable(it) }
                } == true
        }

        if (canRemoveOverrideAnnotation()) {
            removeOverrideAnnotation(methodDecl)
        }
    }

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
    ): NodeTransformDecision = decideForMethod(reducerContext, methodDecl, resolvedMethodDecl, enableAssertions, noCache = false)

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

        /**
         * Whether this [callableDecl] is a candidate for removal.
         *
         * A [CallableDeclaration] is a candidate for removal if it is not
         * [directly referenced][ReachableReason.DirectlyReferenced], or it does not
         * [override a library method][ReachableReason.TransitiveLibraryCallTarget].
         */
        private fun isCandidateForRemovalOrDummy(
            callableDecl: CallableDeclaration<*>
        ): Boolean {
            return callableDecl.inclusionReasonsData.synchronizedWith {
                none { it is ReachableReason.DirectlyReferenced }
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
                NodeTransformDecision.DUMMY
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

            val parentType = ctorDecl.findAncestor(TypeDeclaration::class.java).get()
            if (!isTypeReachable(
                    reducerContext,
                    parentType,
                    enableAssertions,
                    transitiveBacktrace.copy(),
                    noCache = noCache
            )) {
                return NodeTransformDecision.REMOVE
            }

            if (!isCandidateForRemovalOrDummy(ctorDecl)) {
                return NodeTransformDecision.NO_OP
            }

            val inclusionReasons = ctorDecl.inclusionReasonsData.synchronizedWith { toList() }

            if (inclusionReasons.isEmpty()) {
                return NodeTransformDecision.REMOVE
            }

            // Whether the constructor is only included transitively because its own class or a subclass requires it
            // to invoke a superclass constructor
            val isCtorTransitiveOnly = inclusionReasons.all { it is ReachableReason.TransitiveCtor }

            if (isCtorTransitiveOnly) {
                if (!enableAssertions) {
                    return NodeTransformDecision.REMOVE
                }

                return if (isTransitiveDependentReachable(
                        reducerContext,
                        ctorDecl,
                        true,
                        transitiveBacktrace.copy(),
                        noCache = noCache
                )) {
                    NodeTransformDecision.DUMMY
                } else {
                    NodeTransformDecision.REMOVE
                }
            }

            return NodeTransformDecision.NO_OP
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

            var result = NodeTransformDecision.NO_OP

            val overriddenMethods = reducerContext.getOverriddenMethods(resolvedMethodDecl, true)

            // If the BlockStmt of the method is unused, the method is implicitly dummy-able
            methodDecl.body.ifPresent {
                if (it.isUnusedData) {
                    result = NodeTransformDecision.DUMMY
                }
            }

            // If this method is part of the baseline, unconditionally keep it
            val inclusionReasons = methodDecl.inclusionReasonsData.synchronizedWith { toList() }
            if (inclusionReasons.any { it is ReachableReason.ByBaseline }) {
                return NodeTransformDecision.NO_OP
            }

            // If this method purely exists to satisfy compilability, dummy it
            if (inclusionReasons.isNotEmpty() && inclusionReasons.all { it is ReachableReason.DirectlyReferencedByNode || it is ReachableReason.TransitiveLibraryCallTarget }) {
                result = NodeTransformDecision.DUMMY
            }
            if (isTransitiveDependentReachable(reducerContext, methodDecl, enableAssertions, transitiveBacktrace.copy(), noCache)) {
                return result
            }

            val parentType = methodDecl.findAncestor(TypeDeclaration::class.java).get()
            if (!isTypeReachable(
                    reducerContext,
                    parentType,
                    enableAssertions,
                    transitiveBacktrace.copy(),
                    noCache = noCache
                )) {
                return NodeTransformDecision.REMOVE
            }

            // Handle methods in anon classes - Always remove unused callables in anon classes
            val anonClassResult = tryRemoveOrDummyMethodInTypeLikeDecl<ObjectCreationExpr>(reducerContext, methodDecl, resolvedMethodDecl, overriddenMethods)
            if (anonClassResult != NodeTransformDecision.NO_OP) {
                return anonClassResult
            }

            // Similarly, handle methods in the body of enum constants
            val enumConstResult = tryRemoveOrDummyMethodInTypeLikeDecl<EnumConstantDeclaration>(reducerContext, methodDecl, resolvedMethodDecl, overriddenMethods)
            if (enumConstResult != NodeTransformDecision.NO_OP) {
                return enumConstResult
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

                transitiveBacktrace.add(methodDecl)
                if (decideForMethod(
                        reducerContext,
                        overriddenMethodAst,
                        directlyOverriddenMethod,
                        enableAssertions,
                        transitiveBacktrace,
                        noCache = noCache
                ) != NodeTransformDecision.REMOVE) {
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

            val inclusionReasons = fieldVarDecl.inclusionReasonsData.synchronizedWith { toList() }

            if (inclusionReasons.filterIsInstance<ReachableReason.DirectlyReferenced>().any()) {
                return NodeTransformDecision.NO_OP
            }

            val declaringType = fieldVarDecl.findAncestor(TypeDeclaration::class.java).get()
            if (!isTypeReachable(
                reducerContext,
                declaringType,
                enableAssertions,
                transitiveBacktrace.copy(),
                noCache = noCache
            )) {
                return NodeTransformDecision.REMOVE
            }

            if (!fieldVarDecl.initializer.isPresent && inclusionReasons.isEmpty()) {
                return NodeTransformDecision.REMOVE
            }

            // A field declaration can be removed even if it has an initializer, provided that the declaring type only
            // acts a nest parent, and all nested classes are static.
            // This is because static nested classes can be loaded without loading its nest parent, so any side effects
            // from field initializers will not be executed.
            val typeInclusionReasons = declaringType.inclusionReasonsData.synchronizedWith { toList() }
            if (typeInclusionReasons.all { it is ReachableReason.NestParent && it.classDecl.isStatic }) {
                return NodeTransformDecision.REMOVE
            }

            return NodeTransformDecision.NO_OP
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
            // Return cached result if it is present
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
                    transitiveBacktrace.copy(),
                    noCache = noCache
            )) {
                NodeTransformDecision.REMOVE
            } else {
                NodeTransformDecision.NO_OP
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

            // Baseline reachable nodes are always reachable
            if (inclusionReasons.filterIsInstance<ReachableReason.ByBaseline>().any()) {
                return true
            }

            // If node is part of an annotation member declaration, it is transitively reachable if the declaring
            // annotation declaration is reachable
            if (node.findAncestor(AnnotationMemberDeclaration::class.java).isPresent) {
                if (isTypeReachable(
                    reducerContext,
                    node.findAncestor(AnnotationDeclaration::class.java).get(),
                    enableAssertions,
                    transitiveBacktrace.copy(),
                    noCache = noCache
                )) {
                    return true
                }
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

            // If node is the type of a field, it is transitively reachable if any variable of the field is reachable
            if (node is ClassOrInterfaceType && node.findAncestor(FieldDeclaration::class.java).isPresent) {
                if (isTypeReachable(
                        reducerContext,
                        node.findAncestor(TypeDeclaration::class.java).get(),
                        enableAssertions,
                        transitiveBacktrace.copy(),
                        noCache = noCache
                )) {
                    val fieldDecl = node.findAncestor(FieldDeclaration::class.java).get()
                    if (fieldDecl.variables.any {
                        decideForFieldDecl(
                            reducerContext,
                            it,
                            enableAssertions,
                            transitiveBacktrace.copy(),
                            noCache = noCache
                        ) == NodeTransformDecision.NO_OP
                    }) {
                        return true
                    }
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
                                transitiveBacktrace.copy(),
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
                        isTypeReachable(
                            reducerContext,
                            it.typeName.findAncestor(TypeDeclaration::class.java).get(),
                            enableAssertions,
                            transitiveBacktrace.copy(),
                            noCache = noCache
                        )
                    }

                    is ReachableReason.TransitiveCallableHeader -> {
                        val callableDecl = it.dependentNode
                        if (callableDecl.isUsedInCoverageOrNull(
                                defaultIfMissingCov = false,
                                defaultIfUnsoundCov = false
                        ) == true) {
                            true
                        } else {
                            when (callableDecl) {
                                is ConstructorDeclaration -> {
                                    decideForConstructor(
                                        reducerContext,
                                        callableDecl,
                                        enableAssertions,
                                        transitiveBacktrace.copy(),
                                        noCache = noCache
                                    ) != NodeTransformDecision.REMOVE
                                }

                                is MethodDeclaration -> {
                                    decideForMethod(
                                        reducerContext,
                                        callableDecl,
                                        enableAssertions,
                                        transitiveBacktrace.copy(),
                                        noCache = noCache
                                    ) != NodeTransformDecision.REMOVE
                                }

                                else -> unreachable()
                            }
                        }
                    }

                    is ReachableReason.TransitiveExplicitCtorArgument -> {
                        decideForConstructor(
                            reducerContext,
                            it.dependentNode.findAncestor(ConstructorDeclaration::class.java).get(),
                            enableAssertions,
                            transitiveBacktrace.copy(),
                            noCache = noCache
                        ) != NodeTransformDecision.REMOVE
                    }

                    is ReachableReason.TransitiveClassSupertype -> {
                        !TagStaticAnalysisMemberUnusedDecls.isUnusedForSupertypeRemoval(it.dependentNode)
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
                        // TransitiveClassMemberOfType is only transitively reachable if the node is a field variable,
                        // and the field variable is not removed
                        when (node) {
                            is VariableDeclarator -> {
                                decideForFieldDecl(
                                    reducerContext,
                                    node,
                                    enableAssertions,
                                    transitiveBacktrace.copy(),
                                    noCache = noCache
                                ) != NodeTransformDecision.REMOVE
                            }

                            else -> false
                        }
                    }

                    is ReachableReason.TransitiveDeclaringTypeOfMember -> {
                        // TransitiveDeclaringTypeOfMember are never transitively reachable, since if they are used they
                        // will also be directly referenced in some way
                        false
                    }

                    is ReachableReason.TransitiveMethodCallTarget -> {
                        // TransitiveMethodCallTarget are never transitively reachable, since these are handled by
                        // baseline coverage
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

            // Reachable if the type is loaded or created
            if (ReferenceTypeLikeDeclaration.create(typeDecl).let { it.isBaselineLoadedData == true || it.isBaselineCreatedData == true }) {
                return true
            }

            // Reachable if the type is directly referenced by any node
            val directlyReferenced = typeDecl.inclusionReasonsData
                .synchronizedWith {
                    any { it is ReachableReason.DirectlyReferenced }
                }
            if (directlyReferenced) {
                return true
            }

            // Reachable if any transitive reason depends on another node, and the node is directly referenced
            if (isTransitiveDependentReachable(
                    reducerContext,
                    typeDecl,
                    enableAssertions,
                    transitiveBacktrace.copy(),
                    noCache = noCache
            )) {
                return true
            }

            return false
        }
    }
}