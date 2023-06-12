package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.model.EntrypointSpec
import com.derppening.researchprojecttoolkit.model.ReferenceTypeLikeDeclaration
import com.derppening.researchprojecttoolkit.tool.inclusionReasonsData
import com.derppening.researchprojecttoolkit.tool.lockInclusionReasonsData
import com.derppening.researchprojecttoolkit.tool.reachability.ReachableReason
import com.derppening.researchprojecttoolkit.tool.transform.*
import com.derppening.researchprojecttoolkit.util.*
import com.github.javaparser.ast.Node
import com.github.javaparser.ast.body.*
import com.github.javaparser.ast.expr.*
import com.github.javaparser.ast.nodeTypes.NodeWithExtends
import com.github.javaparser.ast.nodeTypes.NodeWithImplements
import com.github.javaparser.ast.nodeTypes.NodeWithTypeParameters
import com.github.javaparser.ast.type.ClassOrInterfaceType
import com.github.javaparser.resolution.declarations.*
import com.github.javaparser.resolution.types.ResolvedReferenceType
import com.github.javaparser.resolution.types.ResolvedType
import hk.ust.cse.castle.toolkit.jvm.util.RuntimeAssertions.unreachable
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.jvm.optionals.getOrDefault
import kotlin.jvm.optionals.getOrNull

class StaticAnalysisMemberReducer(
    classpath: String,
    sourcesClasspath: String,
    sourceRoots: List<Path>,
    entrypoints: List<EntrypointSpec>,
    enableAssertions: Boolean,
    threads: Int?,
    enabledOptimizations: Set<Optimization> = Optimization.ALL
) : AbstractReducer(
    classpath,
    sourcesClasspath,
    sourceRoots,
    entrypoints,
    enableAssertions,
    enabledOptimizations,
    threads
) {

    private val queueLock = ReentrantLock()
    private val noDeclInFlight = queueLock.newCondition()

    private val staticTypeQueue = ConcurrentProcessingQueue<_, TypeDeclaration<*>>(RESOLVED_TYPE_DECL_COMPARATOR)
    private val nonStaticTypeQueue = ConcurrentProcessingQueue<_, TypeDeclaration<*>>(RESOLVED_TYPE_DECL_COMPARATOR)
    private val fieldQueue = ConcurrentProcessingQueue<_, Node>(RESOLVED_VALUE_COMPARATOR)
    private val callableQueue = ConcurrentProcessingQueue<_, CallableDeclaration<*>>(ResolvedCallableDeclComparator(context))

    private fun hasDeclInFlight(): Boolean = queueLock.withLock {
        !staticTypeQueue.isQueueEmpty() ||
            !nonStaticTypeQueue.isQueueEmpty() ||
            !fieldQueue.isQueueEmpty() ||
            !callableQueue.isQueueEmpty()
    }

    private fun ExecutorService.queueJob(decl: ResolvedMethodLikeDeclaration, reasons: Set<ReachableReason>) {
        if (reasons.isEmpty()) return

        callableQueue.push(decl)

        runCatching {
            submit { processMethod(decl, reasons, this) }
        }.onFailure { tr ->
            LOGGER.error("Error while submitting Job(reachableDecl=${decl.qualifiedSignature}) to executor", tr)
        }
    }

    private fun ExecutorService.queueJob(decl: ResolvedMethodLikeDeclaration, reason: ReachableReason) =
        queueJob(decl, setOf(reason))

    private fun ExecutorService.queueJob(decl: ResolvedValueDeclaration, reasons: Set<ReachableReason>) {
        if (reasons.isEmpty()) return

        // Do not analyze T[].length
        if (decl.isArrayLengthDecl) return

        fieldQueue.push(decl)

        runCatching {
            submit { processField(decl, reasons, this) }
        }.onFailure { tr ->
            LOGGER.error("Error while submitting Job(reachableDecl=${decl.qualifiedName}) to executor", tr)
        }
    }

    private fun ExecutorService.queueJob(decl: ResolvedValueDeclaration, reason: ReachableReason) =
        queueJob(decl, setOf(reason))

    private fun ExecutorService.queueJob(
        decl: ResolvedReferenceTypeDeclaration,
        isStatic: Boolean,
        reasons: Set<ReachableReason>
    ) {
        if (reasons.isEmpty()) return

        if (isStatic) {
            staticTypeQueue.push(decl)
        } else {
            nonStaticTypeQueue.push(decl)
        }

        runCatching {
            submit { processClass(decl, isStatic, reasons, this) }
        }.onFailure { tr ->
            LOGGER.error("Error while submitting Job(reachableDecl=${decl.qualifiedName}) to executor", tr)
        }
    }

    private fun ExecutorService.queueJob(
        decl: ResolvedReferenceTypeDeclaration,
        isStatic: Boolean,
        reason: ReachableReason
    ) = queueJob(decl, isStatic, setOf(reason))

    private fun resolveOverriddenMethodsToDecl(
        scope: MethodDeclaration
    ): Pair<ResolvedMethodDeclaration, Set<ResolvedMethodDeclaration>> {
        val resolvedDecl = context.resolveDeclaration<ResolvedMethodDeclaration>(scope)
        return resolvedDecl to context.getOverriddenMethods(resolvedDecl, true)
    }

    /**
     * Tries to infer all possible types which a [MethodCallExpr] can invoke.
     *
     * @param resolvedMethodDecl The resolved static target of [callExpr].
     * @param callExpr The [MethodCallExpr] which is being invoked.
     * @param scopeExpr The expression to the left-hand side of the call expression.
     * @return [Collection] of all [ResolvedType] which [scopeExpr] may dynamically dispatch to.
     */
    internal fun inferDynamicMethodCallTypes(
        resolvedMethodDecl: ResolvedMethodDeclaration,
        callExpr: MethodCallExpr,
        scopeExpr: Expression?
    ): Set<ResolvedType> {
        val staticScopeType = scopeExpr?.let { context.calculateType(it) }
            ?: context.symbolSolver.getTypeOfThisIn(callExpr)

        if (Optimization.ASSIGNED_TYPES_NARROWING !in enabledOptimizations) {
            return context.getSubtypesOfType(staticScopeType)
        }

        val staticType by lazy(LazyThreadSafetyMode.NONE) {
            staticScopeType
                .let { context.flattenType(it) }
                .let { rawifyType(it, context.typeSolver) }
        }

        fun isLooselyAssignableToStaticType(type: ResolvedType): Boolean {
            val isAssignable = with(context) { staticType.isLooselyAssignableBy(type) }
            if (!isAssignable) {
                LOGGER.debug(
                    "Excluding unassignable type: {} -> {}",
                    rawifyType(type, context.typeSolver).describe(),
                    staticType.describe()
                )
            }

            return isAssignable
        }

        return when (scopeExpr) {
            is ThisExpr,
            null -> {
                context.getSubtypesOfType(context.symbolSolver.getTypeOfThisIn(callExpr))
            }

            is ObjectCreationExpr,
            is StringLiteralExpr,
            is SuperExpr,
            is ClassExpr,
            is BinaryExpr -> {
                emptySet()
            }

            is ArrayAccessExpr -> {
                val scopeDecl = runCatching {
                    context.resolveDeclaration<ResolvedDeclaration>(scopeExpr.getBaseExpression())
                }.getOrNull()

                when (scopeDecl) {
                    is ResolvedValueDeclaration -> {
                        context.getAssignedTypesOf(scopeDecl)
                            .map { if (it.isArray) it.asArrayType().componentType else it }
                            .flatMap { context.getSubtypesOfType(it).toList() + it }
                            .filterNot { it == staticType }
                            .filter { isLooselyAssignableToStaticType(it) }
                            .toSortedSet(RESOLVED_TYPE_COMPARATOR)
                    }

                    null -> {
                        val declaringType = context.calculateType(scopeExpr)
                        context.getSubtypesOfType(declaringType)
                    }

                    else -> emptySet()
                }
            }

            is FieldAccessExpr,
            is NameExpr -> {
                when (val scopeDecl = context.resolveDeclarationOrNull<ResolvedDeclaration>(scopeExpr)) {
                    // For ResolvedParameterDeclaration, the precise type of the variable is not known, so we process it
                    // as-if any subtype of the type of the expression may be passed in
                    is ResolvedParameterDeclaration, null -> {
                        val declaringType = context.calculateType(scopeExpr)
                        context.getSubtypesOfType(declaringType)
                    }

                    is ResolvedValueDeclaration -> {
                        context.getAssignedTypesOf(scopeDecl)
                            .flatMap { context.getSubtypesOfType(it).toList() + it }
                            .filterNot { it == staticType }
                            .filter { isLooselyAssignableToStaticType(it) }
                            .toSortedSet(RESOLVED_TYPE_COMPARATOR)
                    }

                    else -> emptySet()
                }
            }

            is EnclosedExpr -> inferDynamicMethodCallTypes(
                resolvedMethodDecl,
                callExpr,
                scopeExpr.inner
            )

            is ConditionalExpr -> {
                TreeSet(RESOLVED_TYPE_COMPARATOR).apply {
                    addAll(inferDynamicMethodCallTypes(resolvedMethodDecl, callExpr, scopeExpr.thenExpr))
                    addAll(inferDynamicMethodCallTypes(resolvedMethodDecl, callExpr, scopeExpr.elseExpr))
                }
            }

            is CastExpr -> {
                // Determine whether it is an upcast or downcast
                val declaringType = context.calculateType(scopeExpr)
                val castTargetType = context.toResolvedType<ResolvedType>(scopeExpr.type)

                // Regardless of the direction of cast, it may call some method under our hierarchy at the end of the day
                val declaringTypeSubtypes = context.getSubtypesOfType(declaringType)

                if (declaringType == castTargetType) {
                    // Cast to equivalent type - No more candidates
                    declaringTypeSubtypes
                } else if (declaringType.isReferenceType && castTargetType.isReferenceType) {
                    val declaringRefType = declaringType.asReferenceType()
                    val castTargetRefType = castTargetType.asReferenceType()

                    val declaringRefTypeQname = declaringRefType.qualifiedName
                    val castTargetRefTypeQname = castTargetRefType.qualifiedName

                    if (declaringRefTypeQname == castTargetRefTypeQname) {
                        declaringTypeSubtypes
                    } else {
                        val declaringRefTypeAncestorsQnames = context.symbolSolverCache
                            .getAllAncestors(declaringRefType)
                            .mapTo(mutableSetOf()) { it.qualifiedName }
                        val castTargetRefTypeAncestorsQnames = context.symbolSolverCache
                            .getAllAncestors(declaringRefType)
                            .mapTo(mutableSetOf()) { it.qualifiedName }

                        if (castTargetRefTypeQname in declaringRefTypeAncestorsQnames) {
                            // Upcast - Will resolve to the same virtual call, so no more candidates
                            declaringTypeSubtypes
                        } else if (declaringRefTypeQname in castTargetRefTypeAncestorsQnames) {
                            // Downcast - May resolve to any virtual calls under the cast target type
                            context.getSubtypesOfType(castTargetRefType)
                        } else {
                            // Sidecast - I don't think Java allows that, so make it unreachable
                            unreachable("Resolution of side cast from ${declaringRefType.describe()} to ${castTargetRefType.describe()} not implemented")
                        }
                    }
                } else {
                    if (declaringType !is ResolvedReferenceType) {
                        TODO("Resolution of cast target from ${declaringType::class.simpleName} not implemented")
                    }
                    if (castTargetType !is ResolvedReferenceType) {
                        TODO("Resolution of cast target to ${castTargetType::class.simpleName} not implemented")
                    }
                    unreachable()
                }
            }

            is MethodCallExpr -> {
                val declaringType = context.calculateType(scopeExpr)

                context.getSubtypesOfType(declaringType)
            }

            else -> {
                LOGGER.warn("Don't know how to infer dynamic resolution types for `${scopeExpr::class.simpleName}`")

                val declaringType = context.calculateType(scopeExpr)

                context.getSubtypesOfType(declaringType)
            }
        }
    }

    /**
     * Tries to infer all candidate call targets from a method in a given type.
     *
     * @param callExpr The base [MethodCallExpr].
     * @param scopeExpr The expression to the left-hand side of the call expression.
     * @return [Set] of all [ResolvedMethodDeclaration] which may be called if a method statically resolving to
     * [resolvedMethodDecl] is invoked on [scopeExpr].
     */
    private fun inferDynamicMethodCallTargets(
        resolvedMethodDecl: ResolvedMethodDeclaration,
        callExpr: MethodCallExpr,
        scopeExpr: Expression?
    ): Set<ResolvedMethodDeclaration> {
        val staticType = scopeExpr?.let { context.calculateType(it) } ?: context.symbolSolver.getTypeOfThisIn(callExpr)
        val dynamicSubtypes = inferDynamicMethodCallTypes(resolvedMethodDecl, callExpr, scopeExpr)

        // TODO: Need a better explanation
        // If the static type of the scope expression is not the same as the declaring type of the method, include the
        // resolved target of the expression, because this is not handled in the static method call target.
        val isStaticTypeSameAsMethodDeclType = staticType.asReferenceTypeOrNull()
            ?.let { it.toResolvedTypeDeclaration() == resolvedMethodDecl.declaringType() } != false

        return buildCollection(TreeSet(ResolvedCallableDeclComparator(context))) {
            if (!isStaticTypeSameAsMethodDeclType) {
                getOverridingMethodsInType(
                    resolvedMethodDecl,
                    staticType.asReferenceType(),
                    context,
                    traverseClassHierarchy = true,
                    cache = context.symbolSolverCache
                ).also { addAll(it) }
            }

            dynamicSubtypes
                .mapNotNull { it.takeIf { it.isReferenceType }?.asReferenceType()?.typeDeclaration?.getOrNull() }
                .filterNot { it.isAnnotation }
                .flatMap {
                    getOverridingMethodsInType(
                        resolvedMethodDecl,
                        it,
                        context,
                        true,
                        cache = context.symbolSolverCache
                    )
                }
                .also { addAll(it) }

            // We take the static type as well when considering overriding methods in enum constants, because methods in
            // enum constants are not taken into account when determining the set of reachable methods of a type.
            (dynamicSubtypes.toList() + staticType)
                .mapNotNullTo(TreeSet(RESOLVED_REF_TYPE_COMPARATOR)) { it.asReferenceTypeOrNull() }
                .toList()
                .filter {
                    with(context) {
                        createResolvedRefType(resolvedMethodDecl.declaringType()).isLooselyAssignableBy(it)
                    }
                }
                .flatMap { context.enumConstAllAncestorsMapping[it].orEmpty() }
                .flatMap {
                    getOverridingMethodsInEnumConst(
                        resolvedMethodDecl,
                        it,
                        context,
                        true,
                        cache = context.symbolSolverCache
                    )
                }
                .also { addAll(it) }
        }
    }

    /**
     * Tries to infer all candidate call targets from a method in a given type.
     *
     * @param resolvedMethodDecl The method declaration which is statically resolved from [callExpr].
     * @param callExpr The method call expression to infer candidates from
     * @return [Set] of all [ResolvedMethodDeclaration] which may be called if [callExpr] is invoked.
     */
    private fun inferDynamicMethodCallTargets(
        resolvedMethodDecl: ResolvedMethodDeclaration,
        callExpr: MethodCallExpr
    ): Set<ResolvedMethodDeclaration> =
        inferDynamicMethodCallTargets(resolvedMethodDecl, callExpr, callExpr.scope.getOrNull())

    /**
     * Returns all dynamically-resolved maybe-reachable methods with their cause.
     */
    private fun getDynamicallyResolvedMethodsWithCause(
        methodCallExprs: Map<Expression, ResolvedMethodLikeDeclaration>
    ): List<Pair<ResolvedMethodLikeDeclaration, ReachableReason>> {
        return methodCallExprs
            .flatMap { (callExpr, methodDecl) ->
                when (methodDecl) {
                    is ResolvedConstructorDeclaration -> {
                        emptySet()
                    }

                    is ResolvedMethodDeclaration -> {
                        if (methodDecl.isStatic) {
                            emptySet()
                        } else if (isExprTypeStaticForMethodCallExpr((callExpr as MethodCallExpr).scope.getOrNull())) {
                            emptySet()
                        } else {
                            inferDynamicMethodCallTargets(methodDecl, callExpr)
                        }
                    }

                    else -> error("Unknown method-like declaration")
                }.map {
                    it to ReachableReason.TransitiveMethodCallTarget(callExpr as MethodCallExpr)
                }
            }
    }

    /**
     * Returns all resolved maybe-reachable methods with their cause.
     */
    private fun getResolvedMethodsWithCause(
        methodCallExprs: Map<Expression, ResolvedMethodLikeDeclaration>
    ): List<Pair<ResolvedMethodLikeDeclaration, ReachableReason>> =
        getStaticallyResolvedMethodsWithCause(methodCallExprs) + getDynamicallyResolvedMethodsWithCause(methodCallExprs)

    /**
     * Finds all library methods in [methods] and marks them as
     * [transitively-reachable][ReachableReason.TransitiveLibraryCallTarget].
     *
     * For instance: We mark compare(Object, Object) as reachable if it extends from Comparable, since there may be
     * a call to Arrays.sort which invokes this method without any static invocations
     */
    private fun findAndAddLibraryMethodOverridesForProcessing(
        methods: List<MethodDeclaration>,
        executor: ExecutorService
    ) {
        methods.map {
            context.resolveDeclaration<ResolvedMethodDeclaration>(it)
        }
        .mapNotNull { resolvedMethodDecl ->
            context.getLibraryMethodOverrides(resolvedMethodDecl)
                .takeIf { it.isNotEmpty() }
                ?.let {
                    resolvedMethodDecl to it
                }
        }
        .forEach { (resolvedMethodDecl, baseDecls) ->
            executor.queueJob(
                resolvedMethodDecl,
                ReachableReason.TransitiveLibraryCallTarget(baseDecls)
            )
        }
    }

    /**
     * Find all relevant nodes under [node] and adds them for processing by [executor].
     */
    private fun findAndAddNodesForProcessing(node: Node, executor: ExecutorService) {
        resolveTypesToDeclAsSequence(node).forEach { (node, reachableDecl) ->
            executor.queueJob(reachableDecl, true, mapToReason(node, reachableDecl))
        }

        resolveFieldAccessExprsToDeclAsSequence(node).forEach { (node, reachableDecl) ->
            executor.queueJob(reachableDecl, mapToReason(node, reachableDecl))
        }
        resolveNameExprsToDeclAsSequence(node)
            .filter { it.second.hasQualifiedName }
            .forEach { (node, reachableDecl) ->
                executor.queueJob(reachableDecl, mapToReason(node, reachableDecl))
            }

        val overriddenResolvedNodes = if (node is MethodDeclaration) resolveOverriddenMethodsToDecl(node) else null
        val callExprResolvedNodes = resolveCallExprsToDecl(node)
        val explicitCtorStmtResolvedNodes = resolveExplicitCtorStmtsToDecl(node)

        val allReachableCallables =
            getResolvedMethodsWithCause(callExprResolvedNodes) +
                    overriddenResolvedNodes?.let { getOverriddenMethodsWithCause(it) }.orEmpty() +
                    getExplicitCtorsWithCause(explicitCtorStmtResolvedNodes)
        val reachableCallablesWithReason = mapResolvedMethodsToAst(allReachableCallables)

        reachableCallablesWithReason.forEach { (reachableDecl, reasons) ->
            executor.queueJob(reachableDecl, reasons)
        }

        val anonClasses = callExprResolvedNodes.keys
            .filterIsInstance<ObjectCreationExpr>()
            .filter { it.anonymousClassBody.isPresent }

        anonClasses.forEach { anonClass ->
            anonClass.anonymousClassBody.get()
                .filterIsInstance<MethodDeclaration>()
                .let { findAndAddLibraryMethodOverridesForProcessing(it, executor) }
        }

        if (node is EnumConstantDeclaration) {
            node.classBody
                .filterIsInstance<MethodDeclaration>()
                .let { findAndAddLibraryMethodOverridesForProcessing(it, executor) }
        }
    }

    /**
     * Finds nodes which may be related to class/method/field declaration within a [CallableDeclaration] and adds for
     * processing.
     */
    private fun findAndAddNodesInScope(scope: CallableDeclaration<*>, executor: ExecutorService) {
        findAndAddNodesForProcessing(scope as Node, executor)

        when (val refLikeDecl = scope.containingType) {
            is ReferenceTypeLikeDeclaration.AnonClassDecl -> {
                check(scope is MethodDeclaration)

                executor.queueJob(
                    context.resolveDeclaration<ResolvedReferenceTypeDeclaration>(refLikeDecl.node),
                    scope.isStatic,
                    ReachableReason.TransitiveDeclaringTypeOfMember(scope)
                )
            }

            is ReferenceTypeLikeDeclaration.EnumConstDecl -> {
                check(scope is MethodDeclaration)

                executor.queueJob(
                    context.resolveDeclaration<ResolvedEnumConstantDeclaration>(refLikeDecl.node),
                    ReachableReason.TransitiveDeclaringTypeOfMember(scope)
                )
            }

            is ReferenceTypeLikeDeclaration.TypeDecl -> {
                val astTypeDecl = refLikeDecl.node
                val resolvedTypeDecl = context.resolveDeclaration<ResolvedReferenceTypeDeclaration>(refLikeDecl.node)

                if (scope is ConstructorDeclaration) {
                    executor.queueJob(resolvedTypeDecl, false, setOf(ReachableReason.TransitiveDeclaringTypeOfMember(scope)))

                    val explicitCtorInvocationStmt = scope.explicitCtorInvocationStmt
                    if (explicitCtorInvocationStmt != null) {
                        executor.queueJob(
                            context.resolveDeclaration<ResolvedConstructorDeclaration>(explicitCtorInvocationStmt),
                            ReachableReason.ReferencedByCtorCallByExplicitStmt(explicitCtorInvocationStmt)
                        )
                    } else {
                        with(context) {
                            findSuperclassNoArgConstructor(astTypeDecl, resolvedTypeDecl)?.let {
                                executor.queueJob(
                                    it,
                                    ReachableReason.ReferencedByUnspecifiedNode(scope)
                                )
                            }
                        }
                    }
                } else {
                    executor.queueJob(
                        resolvedTypeDecl,
                        scope.isStatic,
                        ReachableReason.TransitiveDeclaringTypeOfMember(scope)
                    )
                }
            }
        }
    }

    private fun findAndAddNodesInScope(scope: TypeDeclaration<*>, isStatic: Boolean, executor: ExecutorService) {
        scope.annotations.forEach {
            findAndAddNodesForProcessing(it, executor)
        }

        if (scope is NodeWithTypeParameters<*>) {
            scope.typeParameters.forEach {
                findAndAddNodesForProcessing(it, executor)
            }
        }

        val scopeResolvedType = context.resolveDeclaration<ResolvedReferenceTypeDeclaration>(scope)

        val allAncestors = context.symbolSolverCache.getAllAncestors(scopeResolvedType)

        // Mark direct supertypes as reachable
        scope
            .let {
                val extendedTypes = (it as? NodeWithExtends<*>)?.extendedTypes.orEmpty()
                val implementedTypes = (it as? NodeWithImplements<*>)?.implementedTypes.orEmpty()

                extendedTypes + implementedTypes
            }
            .flatMap { context.resolveRefTypeNodeDeclsAsSequence(it) }
            .mapNotNull { (k, v) -> v?.let { k to it } }
            .forEach { (k, v) ->
                val extendedTypes = (scope as? NodeWithExtends<*>)?.extendedTypes.orEmpty()
                val implementedTypes = (scope as? NodeWithImplements<*>)?.implementedTypes.orEmpty()
                val parentNode = k.parentNodeForChildren

                if (parentNode in extendedTypes || parentNode in implementedTypes) {
                    executor.queueJob(v, isStatic, ReachableReason.ClassSupertype(scope))
                } else {
                    val parentTypeName = k.findAncestor(ClassOrInterfaceType::class.java)
                        .orElseThrow { NoSuchElementException("No ClassOrInterfaceType in AST ancestors\n${k.astToString(showChildren = false)}") }

                    // A nested type name only requires the declaration, so assume the type is reachable in a static
                    // context
                    executor.queueJob(v, true, ReachableReason.TransitiveNestedTypeName(parentTypeName))
                }
            }

        // If this class is a nested class, mark its AST parent as reachable
        // If this class is non-static as well, analyze the type as well
        // The parent will be loaded since non-static nested classes has an implicit dependency on it
        if (scope.isNestedType) {
            val nestParentType = scope.findAncestor(TypeDeclaration::class.java).get()
            executor.queueJob(
                context.resolveDeclaration(nestParentType),
                scope.isStatic,
                ReachableReason.NestParent(scope)
            )
        }

        // Mark static fields and initializers of typeDecl and its ancestors as reachable
        // Ancestors are reachable because the class will be loaded when JVM resolves class hierarchies
        scope.fields
            .filter { it.isStatic }
            .flatMap { it.variables }
            .forEach {
                executor.queueJob(
                    context.resolveDeclaration<ResolvedFieldDeclaration>(it),
                    ReachableReason.TransitiveClassMemberOfType(scope)
                )
            }
        scope.members
            .filterIsInstance<InitializerDeclaration>()
            .filter { it.isStatic }
            .forEach {
                findAndAddNodesForProcessing(it, executor)
            }

        // If the superclass has a no-argument constructor, include it transitively in case all constructors in the
        // current class is determined to be unused.
        // Otherwise, include all constructors in the current class which delegates to a superclass constructor.
        val superclassNoArgCtor = with(context) { findSuperclassNoArgConstructor(scope, scopeResolvedType) }
        if (superclassNoArgCtor != null) {
            val noArgCtor = scope.constructors
                .singleOrNull { it.parameters.isEmpty() }

            noArgCtor?.let { ctor ->
                executor.queueJob(
                    context.resolveDeclaration<ResolvedConstructorDeclaration>(ctor),
                    ReachableReason.TransitiveCtorForClass(scope, null)
                )
            }

            superclassNoArgCtor.let {
                executor.queueJob(
                    it,
                    ReachableReason.TransitiveCtorForSubclass(scope, noArgCtor)
                )
            }
        } else {
            scope.constructors
                .filter {
                    it.explicitCtorInvocationStmt?.isThis != true
                }
                .forEach { ctor ->
                    executor.queueJob(
                        context.resolveDeclaration<ResolvedConstructorDeclaration>(ctor),
                        ReachableReason.TransitiveCtorForClass(scope, null)
                    )

                    with(context) {
                        findSuperclassDependentConstructor(ctor).let {
                            executor.queueJob(
                                it,
                                ReachableReason.TransitiveCtorForSubclass(scope, ctor)
                            )
                        }
                    }
                }
        }

        // If this class has any method which overrides a library method, mark those methods as transitively reachable
        // For instance: We mark compare(Object, Object) as reachable if it extends from Comparable, since there may be
        // a call to Arrays.sort which invokes this method without any static invocations
        findAndAddLibraryMethodOverridesForProcessing(scope.methods, executor)

        // If the class has final fields which are not initialized, include all constructors which (1) have body
        // statements, (2) does not delegate to other constructors in the same class
        val finalUninitializedFields = scope.fields
            .filter { !it.isStatic && it.isFinal }
            .flatMap { it.variables }
            .filterNot { it.initializer.isPresent }
        if (finalUninitializedFields.isNotEmpty()) {
            scope.constructors
                .filter { ctorDecl ->
                    ctorDecl.body
                        .statements
                        .first
                        .map {
                            !it.isExplicitConstructorInvocationStmt ||
                                    !it.asExplicitConstructorInvocationStmt().isThis
                        }
                        .getOrDefault(false)
                }
                .forEach {
                    executor.queueJob(
                        context.resolveDeclaration<ResolvedConstructorDeclaration>(it),
                        ReachableReason.TransitiveCtorForClass(scope, null)
                    )
                }
        }

        // Mark instance fields and initializers of typeDecl and its ancestors as reachable *if* this is a
        // non-static field
        if (!isStatic) {
            scope.fields
                .filterNot { it.isStatic }
                .flatMap { it.variables }
                .forEach {
                    executor.queueJob(
                        context.resolveDeclaration<ResolvedFieldDeclaration>(it),
                        ReachableReason.TransitiveClassMemberOfType(scope)
                    )
                }
            scope.members
                .filterIsInstance<InitializerDeclaration>()
                .filterNot { it.isStatic }
                .forEach {
                    findAndAddNodesForProcessing(it, executor)
                }
        }

        // If this class is an annotation class, include all members by default
        if (scope.isAnnotationDeclaration) {
            val annoDecl = scope.asAnnotationDeclaration()

            annoDecl.members
                .filter { it.isAnnotationMemberDeclaration }
                .forEach {
                    executor.queueJob(
                        context.resolveDeclaration<ResolvedAnnotationMemberDeclaration>(it),
                        ReachableReason.TransitiveAnnotationMember(annoDecl)
                    )
                }
        }

        // If this class implements the Serializable interface, mark its related methods as transitively reachable
        findSerializableMethods(scopeResolvedType, allAncestors)
            .forEach {
                executor.queueJob(it, ReachableReason.TransitiveLibraryCallTarget(emptySet()))
            }
    }

    /**
     * Finds nodes which may be related to class/method/field declaration within a [VariableDeclarator] and adds for
     * processing.
     */
    private fun findAndAddNodesInScope(scope: VariableDeclarator, executor: ExecutorService) {
        check(scope.parentNode.map { it is FieldDeclaration }.getOrDefault(false))

        val fieldDecl = scope.parentNode.map { it as FieldDeclaration }.get()

        findAndAddNodesForProcessing(scope as Node, executor)
        findAndAddNodesForProcessing(fieldDecl.elementType, executor)

        // Add any annotations on the field for processing
        fieldDecl.annotations.forEach {
            findAndAddNodesForProcessing(it, executor)
        }

        when (val refLikeDecl = fieldDecl.containingType) {
            is ReferenceTypeLikeDeclaration.EnumConstDecl -> {
                executor.queueJob(
                    context.resolveDeclaration<ResolvedEnumConstantDeclaration>(refLikeDecl.node),
                    ReachableReason.TransitiveDeclaringTypeOfMember(fieldDecl, scope)
                )
            }

            is ReferenceTypeLikeDeclaration.AnonClassDecl, is ReferenceTypeLikeDeclaration.TypeDecl -> {
                executor.queueJob(
                    context.resolveDeclaration<ResolvedReferenceTypeDeclaration>(refLikeDecl.node),
                    fieldDecl.isStatic,
                    ReachableReason.TransitiveDeclaringTypeOfMember(fieldDecl, scope)
                )
            }
        }
    }

    private fun findAndAddNodesInScope(scope: EnumConstantDeclaration, executor: ExecutorService) {
        findAndAddNodesForProcessing(scope as Node, executor)

        val ctorDecl = context.symbolSolver.resolveConstructorDeclarationFromEnumConstant(scope)
        executor.queueJob(ctorDecl, setOf(ReachableReason.ReferencedByCtorCallByEnumConstant(scope)))

        val astTypeDecl = checkNotNull(scope.containingType as? ReferenceTypeLikeDeclaration.TypeDecl).node
        val resolvedTypeDecl = context.resolveDeclaration<ResolvedEnumDeclaration>(astTypeDecl)

        executor.queueJob(resolvedTypeDecl, false, setOf(ReachableReason.TransitiveDeclaringTypeOfMember(scope)))
    }

    private fun processNodesInScope(scope: AnnotationMemberDeclaration, executor: ExecutorService) {
        findAndAddNodesForProcessing(scope as Node, executor)
    }

    private fun processClass(
        resolvedDecl: ResolvedReferenceTypeDeclaration,
        isStatic: Boolean,
        inclusionReasons: Set<ReachableReason>,
        executor: ExecutorService
    ) {
        try {
            val astDecl = resolvedDecl.toTypedAstOrNull<TypeDeclaration<*>>(null)
                ?.takeIf { decl ->
                    decl.findCompilationUnit()
                        .flatMap { it.storage }
                        .map { it.path in context.compilationUnits }
                        .getOrDefault(false)
                }
                ?.let { context.mapNodeInLoadedSet(it) }
                ?: return

            val staticKeyExists = staticTypeQueue.put(resolvedDecl, astDecl)
            val keyExists = if (isStatic) {
                staticKeyExists
            } else {
                nonStaticTypeQueue.put(resolvedDecl, astDecl)
            }
            astDecl.inclusionReasonsData.addAll(inclusionReasons)
            if (keyExists) {
                return
            }

            val qname = resolvedDecl.qualifiedName
            LOGGER.info("Processing $qname")

            findAndAddNodesInScope(astDecl, isStatic, executor)
        } catch (ex: Throwable) {
            LOGGER.error("Unexpected error while executing Job(resolvedDecl=${resolvedDecl.describeQName()})", ex)
        } finally {
            if (isStatic) {
                staticTypeQueue.pop(resolvedDecl)
            } else {
                nonStaticTypeQueue.pop(resolvedDecl)
            }

            queueLock.withLock {
                if (!hasDeclInFlight()) {
                    noDeclInFlight.signal()
                }
            }
        }
    }

    private fun processField(
        resolvedDecl: ResolvedValueDeclaration,
        inclusionReasons: Set<ReachableReason>,
        executor: ExecutorService
    ) {
        try {
            val astDecl = resolvedDecl.toAst()
                ?.map {
                    if (it is FieldDeclaration) {
                        it.variables.single { it.nameAsString == resolvedDecl.name }
                    } else it
                }
                ?.getOrNull()
                ?.takeIf { decl ->
                    decl.findCompilationUnit()
                        .flatMap { it.storage }
                        .map { it.path in context.compilationUnits }
                        .getOrDefault(false)
                }
                ?.let { context.mapNodeInLoadedSet(it) }
                ?: return

            val keyExists = fieldQueue.put(resolvedDecl, astDecl)
            when (astDecl) {
                is EnumConstantDeclaration -> astDecl.inclusionReasonsData.addAll(inclusionReasons)
                is VariableDeclarator -> astDecl.inclusionReasonsData.addAll(inclusionReasons)
                is AnnotationMemberDeclaration -> {
                    // No-Op: Inclusion Reasons are inherited from its annotation class
                }
                is BodyDeclaration<*> -> TODO()
                else -> unreachable()
            }
            if (keyExists) {
                return
            }

            val qname = resolvedDecl.describeQName(false)
            LOGGER.info("Processing $qname")

            when (astDecl) {
                is AnnotationMemberDeclaration -> processNodesInScope(astDecl, executor)
                is EnumConstantDeclaration -> findAndAddNodesInScope(astDecl, executor)
                is VariableDeclarator -> findAndAddNodesInScope(astDecl, executor)
                else -> unreachable("Don't know how to process node of type ${astDecl::class.simpleName}")
            }
        } catch (ex: Throwable) {
            LOGGER.error("Unexpected error while executing Job(resolvedDecl=${resolvedDecl.describeQName()})", ex)
        } finally {
            fieldQueue.pop(resolvedDecl)

            queueLock.withLock {
                if (!hasDeclInFlight()) {
                    noDeclInFlight.signal()
                }
            }
        }
    }

    private fun processMethod(
        resolvedDecl: ResolvedMethodLikeDeclaration,
        inclusionReasons: Set<ReachableReason>,
        executor: ExecutorService
    ) {
        try {
            // Handle ResolvedConstructorDeclaration here, since they contain instance creation information
            // This also saves us from passing inclusion reasons into findAndAddNodesInScope
            if (resolvedDecl is ResolvedConstructorDeclaration) {
                val resolvedTypeDecl = resolvedDecl.declaringType()

                if (!resolvedTypeDecl.isAnonymousClass) {
                    val srcObjectCreationExpr = inclusionReasons
                        .filter {
                            (it as? ReachableReason.ReferencedBySymbolName)?.expr?.isObjectCreationExpr == true
                        }
                        .toSet()

                    executor.queueJob(resolvedTypeDecl, false, srcObjectCreationExpr)
                }
            }

            val astDecl = resolvedDecl.toTypedAstOrNull<CallableDeclaration<*>>(null)
                ?.takeIf { decl ->
                    decl.findCompilationUnit()
                        .flatMap { it.storage }
                        .map { it.path in context.compilationUnits }
                        .getOrDefault(false)
                }
                ?.let { context.mapNodeInLoadedSet(it) }
                ?: return

            val keyExists = callableQueue.put(resolvedDecl, astDecl)
            astDecl.inclusionReasonsData.addAll(inclusionReasons)
            if (keyExists) {
                return
            }

            val qsig = if (resolvedDecl.isDeclaringTypeAnonymous) {
                "${resolvedDecl.declaringType().qualifiedName}.${resolvedDecl.getFixedSignature(context)}"
            } else {
                resolvedDecl.getFixedQualifiedSignature(context)
            }
            LOGGER.info("Processing {}", qsig)

            findAndAddNodesInScope(astDecl, executor)
        } catch (ex: Throwable) {
            LOGGER.error("Unexpected error while executing Job(resolvedDecl=${resolvedDecl.qualifiedSignature})", ex)
        } finally {
            callableQueue.pop(resolvedDecl)

            queueLock.withLock {
                if (!hasDeclInFlight()) {
                    noDeclInFlight.signal()
                }
            }
        }
    }

    override fun run(parallelism: Int) {
        val executor = Executors.newFixedThreadPool(parallelism)

        try {
            val initialCallables = entrypoints
                .flatMap { entrypoint ->
                    val primaryType = context.compilationUnits[entrypoint.file]!!
                        .result
                        .flatMap { it.primaryType }
                        .get()
                    val resolvedTypeDecl = context.resolveDeclaration<ResolvedReferenceTypeDeclaration>(primaryType)

                    findStaticallyReachableMethodsInTestClass(entrypoint, resolvedTypeDecl)
                        .map { entrypoint to it }
                }

            initialCallables.forEach { (entrypoint, methodDecl) ->
                val isStatic = when (methodDecl) {
                    is ResolvedConstructorDeclaration -> false
                    is ResolvedMethodDeclaration -> methodDecl.isStatic
                    else -> unreachable()
                }

                executor.queueJob(methodDecl, ReachableReason.Entrypoint(entrypoint))
                executor.queueJob(methodDecl.declaringType(), isStatic, ReachableReason.EntrypointClass(entrypoint))

                // Explicitly include the type of the entrypoint as an entrypoint if the method is inherited from a
                // supertype
                val entrypointClass = entrypoint.testClass.className
                    .takeIf { it != methodDecl.declaringType().qualifiedName }
                    ?.let { context.typeSolver.solveType(it) }
                    ?: return@forEach

                val classReason = ReachableReason.EntrypointClass(entrypoint)
                when (methodDecl) {
                    is ResolvedConstructorDeclaration -> {
                        executor.queueJob(entrypointClass, false, classReason)
                    }
                    is ResolvedMethodDeclaration -> {
                        executor.queueJob(entrypointClass, methodDecl.isStatic, classReason)
                    }
                    else -> unreachable()
                }
            }

            while (hasDeclInFlight()) {
                queueLock.withLock {
                    noDeclInFlight.await()
                }
            }

            val typesToRetain = staticTypeQueue.entries.map { it.key.qualifiedName }
            classesWorkingSet.retainAll { it.fullyQualifiedName.getOrNull() in typesToRetain }
        } finally {
            executor.shutdown()
            check(executor.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS))

            context.loadedCompilationUnits
                .parallelStream()
                .forEach { it.lockInclusionReasonsData() }
        }
    }

    override val taggingReductionPipeline: PhasedPipeline
        get() = PhasedPipeline {
            phase {
                TagImportUsage(context) andThen
                        TagClassFieldInitializers(context) andThen
                        TagThrownCheckedExceptions(context)
            }
            phase { TagStaticAnalysisMemberUnusedDecls(context, enableAssertions) }
            phase { TagUnusedImportsByResolution(context) }
        }

    override val mutatingReductionPipeline: PhasedPipeline
        get() = PhasedPipeline {
            phase { RemoveUnusedNodes(context, enableAssertions) }
        }

    companion object {

        private val LOGGER = Logger<StaticAnalysisMemberReducer>()
    }
}