package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.model.*
import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.tool.reachability.ReachableReason
import com.derppening.researchprojecttoolkit.tool.transform.*
import com.derppening.researchprojecttoolkit.util.*
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.Node
import com.github.javaparser.ast.body.*
import com.github.javaparser.ast.expr.*
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations
import com.github.javaparser.ast.nodeTypes.NodeWithArguments
import com.github.javaparser.ast.nodeTypes.NodeWithExtends
import com.github.javaparser.ast.nodeTypes.NodeWithImplements
import com.github.javaparser.ast.stmt.CatchClause
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt
import com.github.javaparser.ast.type.ClassOrInterfaceType
import com.github.javaparser.ast.type.PrimitiveType
import com.github.javaparser.resolution.declarations.*
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.DefaultConstructorDeclaration
import hk.ust.cse.castle.toolkit.jvm.util.RuntimeAssertions
import java.nio.file.Path
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.jvm.optionals.getOrDefault
import kotlin.jvm.optionals.getOrNull

/**
 * [AbstractReducer] which only retains sources that are executed.
 */
class CoverageBasedReducer(
    classpath: String,
    sourcesClasspath: String,
    sourceRoots: List<Path>,
    entrypoints: List<EntrypointSpec>,
    enableAssertions: Boolean,
    threads: Int?,
    projectRev: Defects4JWorkspace.ProjectRev,
    testCase: TestCase
) : AbstractBaselineReducer(
    classpath,
    sourcesClasspath,
    sourceRoots,
    entrypoints,
    enableAssertions,
    threads,
    projectRev,
    testCase
) {

    private val queueLock = ReentrantLock()
    private val noDeclInFlight = queueLock.newCondition()

    private val typeQueue = ConcurrentProcessingQueue<_, TypeDeclaration<*>>(RESOLVED_TYPE_DECL_COMPARATOR)
    private val fieldQueue = ConcurrentProcessingQueue<_, Node>(RESOLVED_VALUE_COMPARATOR)
    private val callableQueue = ConcurrentProcessingQueue<_, CallableDeclaration<*>>(ResolvedCallableDeclComparator(context))
    private val annoExprQueue = ConcurrentProcessingQueue<_, Unit>(NodeAstComparator<AnnotationExpr>())
    private val argExprQueue = ConcurrentProcessingQueue<_, Unit>(NodeAstComparator<Expression>())
    private val initDeclQueue = ConcurrentProcessingQueue<_, Unit>(NodeAstComparator<InitializerDeclaration>())

    private fun hasDeclInFlight(): Boolean = queueLock.withLock {
        !typeQueue.isQueueEmpty() ||
                !fieldQueue.isQueueEmpty() ||
                !callableQueue.isQueueEmpty() ||
                !annoExprQueue.isQueueEmpty() ||
                !argExprQueue.isQueueEmpty() ||
                !initDeclQueue.isQueueEmpty()
    }

    private fun ExecutorService.queueJob(
        decl: ResolvedMethodLikeDeclaration,
        reasons: Set<ReachableReason>
    ) {
        if (reasons.isEmpty()) return

        callableQueue.push(decl)

        runCatching {
            submit { processMethod(decl, reasons, this) }
        }.onFailure { tr ->
            LOGGER.error("Error while submitting Job(reachableDecl=${decl.qualifiedSignature}) to executor", tr)
        }
    }

    private fun ExecutorService.queueJob(
        decl: ResolvedMethodLikeDeclaration,
        reason: ReachableReason
    ) = queueJob(decl, setOf(reason))

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
        reasons: Set<ReachableReason>
    ) {
        if (reasons.isEmpty()) return

        typeQueue.push(decl)

        runCatching {
            submit { processClass(decl, reasons, this) }
        }.onFailure { tr ->
            LOGGER.error("Error while submitting Job(reachableDecl=${decl.qualifiedName}) to executor", tr)
        }
    }

    private fun ExecutorService.queueJob(
        decl: ResolvedReferenceTypeDeclaration,
        reason: ReachableReason
    ) = queueJob(decl, setOf(reason))

    private fun ExecutorService.queueJob(nodeWithAnnotations: NodeWithAnnotations<*>) {
        annoExprQueue.pushAll(nodeWithAnnotations.annotations)

        nodeWithAnnotations.annotations.forEach {
            runCatching {
                submit { processAnnotation(it, this) }
            }.onFailure { tr ->
                LOGGER.error("Error while submitting Job(annotationExpr=${it}) to executor", tr)
            }
        }
    }

    private fun ExecutorService.queueJob(nodeWithArguments: NodeWithArguments<*>) {
        argExprQueue.pushAll(nodeWithArguments.arguments)

        nodeWithArguments.arguments.forEach {
            runCatching {
                submit { processArgument(it, this) }
            }.onFailure { tr ->
                LOGGER.error("Error while submitting Job(annotationExpr=${it}) to executor", tr)
            }
        }
    }

    private fun ExecutorService.queueJob(initDecl: InitializerDeclaration) {
        initDeclQueue.push(initDecl)

        runCatching {
            submit { processInitDecl(initDecl, this) }
        }.onFailure { tr ->
            LOGGER.error("Error while submitting Job(annotationExpr=${initDecl}) to executor", tr)
        }
    }

    private fun resolveOverriddenMethodsToDecl(
        scope: MethodDeclaration
    ): Pair<ResolvedMethodDeclaration, Set<ResolvedMethodDeclaration>>? {
        if (scope.isUsedInCoverageOrNull(defaultIfMissingCov = false, defaultIfUnsoundCov = false) != true) {
            return null
        }

        val resolvedDecl = context.resolveDeclaration<ResolvedMethodDeclaration>(scope)
        return resolvedDecl to context.getOverriddenMethods(resolvedDecl, true)
    }

    /**
     * Returns all dynamically-resolved maybe-reachable methods with their cause.
     */
    private fun getDynamicallyResolvedMethodsWithCause(
        methodCallExprs: Map<Expression, ResolvedMethodLikeDeclaration>
    ): List<Pair<ResolvedMethodLikeDeclaration, ReachableReason>> {
        return methodCallExprs
            .mapValues { (expr, methodDecl) ->
                when (methodDecl) {
                    is ResolvedConstructorDeclaration -> {
                        emptySet()
                    }

                    is ResolvedMethodDeclaration -> {
                        // If the type of the scope expression is dynamically dispatched, include its overriding methods
                        // as well as transitive targets
                        if (methodDecl.isStatic) {
                            emptySet()
                        } else if (isExprTypeStaticForMethodCallExpr((expr as MethodCallExpr).scope.getOrNull())) {
                            emptySet()
                        } else {
                            context.getOverridingMethods(methodDecl)
                        }
                    }

                    else -> error("Unknown method-like declaration")
                }
            }.flatMap { (expr, methodDecls) ->
                methodDecls.map {
                    it to ReachableReason.TransitiveMethodCallTarget(expr as MethodCallExpr)
                }
            }
    }

    /**
     * Returns all resolved reachable methods with their cause.
     */
    private fun getResolvedMethodsWithCause(
        methodCallExprs: Map<Expression, ResolvedMethodLikeDeclaration>
    ): List<Pair<ResolvedMethodLikeDeclaration, ReachableReason>> {
        return getStaticallyResolvedMethodsWithCause(methodCallExprs) +
                getDynamicallyResolvedMethodsWithCause(methodCallExprs)
    }

    private fun shouldNodeBeProcessed(node: Node): Boolean {
        // Process all nodes within ExplicitCtorInvocationStmt, since these may be retained for delegation to superclass
        // constructor
        val explicitCtorStmtCtx = node.findAncestor(ExplicitConstructorInvocationStmt::class.java).getOrNull()
        if (explicitCtorStmtCtx != null && !explicitCtorStmtCtx.isThis) {
            return true
        }

        // Standard Implementation - Process the node if it is not marked for dummy or removal
        return !node.isUnusedForDummyData && !node.isUnusedForRemovalData
    }

    /**
     * Find all relevant nodes under [node] and adds them for processing by [executor].
     *
     * @param ignoreProcessWhitelist If `true`, indicates that all children of [node] must be included.
     */
    private fun findAndAddNodesForProcessing(
        node: Node,
        executor: ExecutorService,
        ignoreProcessWhitelist: Boolean = false
    ) {
        val overrideReason = when {
            node is ExplicitConstructorInvocationStmt || node.findAncestor(ExplicitConstructorInvocationStmt::class.java).isPresent -> {
                val explicitCtorCtx = node as? ExplicitConstructorInvocationStmt
                    ?: node.findAncestor(ExplicitConstructorInvocationStmt::class.java).get()

                if (explicitCtorCtx.isUnusedForDummyData || explicitCtorCtx.isUnusedForRemovalData) {
                    ReachableReason.TransitiveExplicitCtorArgument(explicitCtorCtx)
                } else null
            }
            ignoreProcessWhitelist -> {
                when {
                    ExecutableDeclaration.createOrNull(node) != null -> {
                        ReachableReason.TransitiveNodeByExecDecl(ExecutableDeclaration.create(node))
                    }

                    node is ClassOrInterfaceType -> ReachableReason.TransitiveNodeByTypeName(node)
                    node is PrimitiveType -> null
                    node is AnnotationExpr -> null
                    node is AnnotationMemberDeclaration -> null
                    else -> {
                        LOGGER.warn("Unknown node type ${node::class.simpleName}")
                        null
                    }
                }
            }
            else -> null
        }

        fun remapReason(reason: ReachableReason): ReachableReason {
            return when {
                // Do not convert TransitiveCallTarget reasons to TransitiveNodeByExecDecl, since
                // TransitiveCallTarget is weaker reachability-wise (it marks a method as dummyable, as opposed
                // to TransitiveNodeByExecDecl which cannot be dummied if the dependent node is needed)
                overrideReason != null && reason !is ReachableReason.TransitiveCallTarget -> {
                    overrideReason
                }
                else -> reason
            }
        }

        resolveTypesToDeclAsSequence(node) {
            if (ignoreProcessWhitelist) return@resolveTypesToDeclAsSequence true
            if (it is AnnotationExpr) return@resolveTypesToDeclAsSequence true

            // Explicitly include the type if it is the parameter of a Catch clause
            // This should be considered as part of the Try statement instead of as an independent block
            val parentCatchCtx = it.findAncestor(CatchClause::class.java).getOrNull()
            if (parentCatchCtx?.parameter?.type === it) return@resolveTypesToDeclAsSequence true

            val cu = it.findCompilationUnit().get()
            it.findAncestor<Node> { ReferenceTypeLikeDeclaration.createOrNull(it) != null }
                ?.let { ReferenceTypeLikeDeclaration.create(it) }
                ?.let { it.jacocoCoverageData to it.coberturaCoverageData }
                ?.let { (_, coberturaCov) ->
                    !it.isUnusedInCoverage(coberturaCov?.lines, cu.jacocoCoverageData?.first?.lines, false)
                }
                ?: true
        }.forEach { (node, reachableDecl) ->
            executor.queueJob(reachableDecl, remapReason(mapToReason(node, reachableDecl)))
        }

        resolveFieldAccessExprsToDeclAsSequence(node) {
            ignoreProcessWhitelist || shouldNodeBeProcessed(it)
        }.forEach { (node, reachableDecl) ->
            executor.queueJob(reachableDecl, remapReason(mapToReason(node, reachableDecl)))
        }

        resolveNameExprsToDeclAsSequence(node) {
            ignoreProcessWhitelist || shouldNodeBeProcessed(it)
        }.filter {
            it.second.hasQualifiedName
        }.forEach { (node, reachableDecl) ->
            executor.queueJob(reachableDecl, remapReason(mapToReason(node, reachableDecl)))
        }

        val callExprResolvedNodes = resolveCallExprsToDecl(node) {
            ignoreProcessWhitelist || shouldNodeBeProcessed(it)
        }

        val overriddenResolvedNodes = when (node) {
            is MethodDeclaration -> resolveOverriddenMethodsToDecl(node)
            else -> null
        }

        val allReachableCallables =
            getResolvedMethodsWithCause(callExprResolvedNodes) +
                    overriddenResolvedNodes?.let { getOverriddenMethodsWithCause(it) }.orEmpty()
        val reachableCallablesWithReason = mapResolvedMethodsToAst(allReachableCallables)

        reachableCallablesWithReason.forEach { (reachableDecl, reasons) ->
            executor.queueJob(reachableDecl, reasons.map { remapReason(it) }.toSet())
        }
    }

    private fun processNodesInScope(scope: TypeDeclaration<*>, executor: ExecutorService) {
        val scopeResolvedType = context.resolveDeclaration<ResolvedReferenceTypeDeclaration>(scope)

        // Mark annotations as reachable
        executor.queueJob(scope as NodeWithAnnotations<*>)

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

                val reason = if (parentNode in extendedTypes || parentNode in implementedTypes) {
                    ReachableReason.TransitiveClassSupertype(scope)
                } else {
                    val parentTypeName = k.findAncestor(ClassOrInterfaceType::class.java)
                        .orElseThrow { NoSuchElementException("No ClassOrInterfaceType in AST ancestors\n${k.astToString(showChildren = false)}") }

                    ReachableReason.TransitiveNestedTypeName(parentTypeName)
                }

                executor.queueJob(v, reason)
            }

        // If this class is a nested class, mark its AST parent as reachable
        // If this class is non-static as well, analyze the type as well
        // The parent will be loaded since non-static nested classes has an implicit dependency on it
        if (scope.isNestedType) {
            val nestParentType = scope.findAncestor(TypeDeclaration::class.java).get()
            if (scope.isStatic) {
                nestParentType.inclusionReasonsData.add(ReachableReason.NestParent(scope))
                executor.queueJob(nestParentType as NodeWithAnnotations<*>)
            } else {
                executor.queueJob(
                    context.resolveDeclaration<ResolvedReferenceTypeDeclaration>(nestParentType),
                    ReachableReason.NestParent(scope)
                )
            }
        }

        // Mark all fields and initializers of typeDecl as reachable
        scope.fields
            .flatMap { it.variables }
            .forEach {
                executor.queueJob(
                    context.resolveDeclaration<ResolvedFieldDeclaration>(it),
                    ReachableReason.TransitiveClassMemberOfType(scope)
                )
            }

        // Mark all initializer blocks of typeDecl as reachable
        val refLikeDecl = ReferenceTypeLikeDeclaration.create(scope)
        if (refLikeDecl.isBaselineLoadedData != false) {
            scope.members
                .filterIsInstance<InitializerDeclaration>()
                .filter {
                    if (it.isStatic) {
                        refLikeDecl.isBaselineLoadedData == true
                    } else {
                        refLikeDecl.isBaselineCreatedData == true
                    }
                }
                .forEach {
                    executor.queueJob(it)
                }
        }

        // If this class has any method which overrides an abstract library method, mark those methods as transitively
        // reachable
        scope.methods
            .asSequence()
            .map { context.resolveDeclaration<ResolvedMethodDeclaration>(it) }
            .filter { context.isMethodOverridesLibraryMethod(it) }
            .filter { resolvedDecl ->
                context.getOverriddenMethods(resolvedDecl, true)
                    .all { it.isAbstract }
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

        // If the class has final fields which are not initialized, include all constructors which (1) have body
        // statements, (2) does not delegate to other constructors in the same class
        val finalUninitializedFields = scope.fields
            .filter { !it.isStatic && it.isFinal }
            .flatMap { it.variables }
            .filterNot { it.initializer.isPresent }
        if (finalUninitializedFields.isNotEmpty()) {
            scope.constructors
                .filter { ctorDecl ->
                    ctorDecl.explicitCtorInvocationStmt?.isThis != true
                }
                .forEach {
                    executor.queueJob(
                        context.resolveDeclaration<ResolvedConstructorDeclaration>(it),
                        ReachableReason.TransitiveCtorForClass(scope, null)
                    )
                }
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
    }

    private fun processClass(
        resolvedDecl: ResolvedReferenceTypeDeclaration,
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

            val keyExists = typeQueue.put(resolvedDecl, astDecl)
            astDecl.inclusionReasonsData.addAll(inclusionReasons)
            if (keyExists) {
                return
            }

            LOGGER.info("Processing ${resolvedDecl.qualifiedName}")

            processNodesInScope(astDecl, executor)
        } catch (ex: Throwable) {
            LOGGER.error("Unexpected error while executing Job(resolvedDecl=${resolvedDecl.describeQName()})", ex)
        } finally {
            typeQueue.pop(resolvedDecl)

            queueLock.withLock {
                if (!hasDeclInFlight()) {
                    noDeclInFlight.signal()
                }
            }
        }
    }

    private fun processNodesInScope(scope: EnumConstantDeclaration, executor: ExecutorService) {
        findAndAddNodesForProcessing(scope as Node, executor)

        val ctorDecl = context.symbolSolver.resolveConstructorDeclarationFromEnumConstant(scope)
        executor.queueJob(ctorDecl, ReachableReason.ReferencedByCtorCallByEnumConstant(scope))

        val astTypeDecl = checkNotNull(scope.containingType as? ReferenceTypeLikeDeclaration.TypeDecl).node
        val resolvedTypeDecl = context.resolveDeclaration<ResolvedEnumDeclaration>(astTypeDecl)

        executor.queueJob(resolvedTypeDecl, setOf(ReachableReason.TransitiveDeclaringTypeOfMember(scope)))
    }

    private fun processNodesInScope(scope: VariableDeclarator, executor: ExecutorService) {
        check(scope.parentNode.map { it is FieldDeclaration }.getOrDefault(false))

        findAndAddNodesForProcessing(scope as Node, executor, true)

        val fieldDecl = scope.parentNode.map { it as FieldDeclaration }.get()

        findAndAddNodesForProcessing(fieldDecl.elementType, executor, true)
        executor.queueJob(fieldDecl as NodeWithAnnotations<*>)

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
                    ReachableReason.TransitiveDeclaringTypeOfMember(fieldDecl, scope)
                )
            }
        }
    }

    private fun processNodesInScope(scope: AnnotationMemberDeclaration, executor: ExecutorService) {
        findAndAddNodesForProcessing(scope as Node, executor, true)
    }

    private fun processField(
        resolvedDecl: ResolvedValueDeclaration,
        inclusionReasons: Set<ReachableReason>,
        executor: ExecutorService
    ) {
        try {
            val astDecl = resolvedDecl.toTypedAstOrNull<Node>(context)
                ?.let {
                    if (it is FieldDeclaration) {
                        it.variables.single { it.nameAsString == resolvedDecl.name }
                    } else it
                }
                ?.takeIf { decl ->
                    decl.findCompilationUnit()
                        .flatMap { it.storage }
                        .map { it.path in context.compilationUnits }
                        .getOrDefault(false)
                }
                ?: return

            val keyExists = fieldQueue.put(resolvedDecl, astDecl)
            when (astDecl) {
                is EnumConstantDeclaration -> astDecl.inclusionReasonsData.addAll(inclusionReasons)
                is VariableDeclarator -> astDecl.inclusionReasonsData.addAll(inclusionReasons)
                is AnnotationMemberDeclaration -> {
                    // No-Op: Inclusion Reasons are inherited from its annotation class
                }
                is BodyDeclaration<*> -> TODO()
                else -> RuntimeAssertions.unreachable()
            }
            if (keyExists) {
                return
            }

            LOGGER.info("Processing ${resolvedDecl.describeQName(false)}")

            when (astDecl) {
                is AnnotationMemberDeclaration -> processNodesInScope(astDecl, executor)
                is EnumConstantDeclaration -> processNodesInScope(astDecl, executor)
                is VariableDeclarator -> processNodesInScope(astDecl, executor)
                else -> RuntimeAssertions.unreachable("Don't know how to process node of type ${astDecl::class.simpleName}")
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

    private fun processNodesInScope(scope: CallableDeclaration<*>, executor: ExecutorService) {
        findAndAddNodesForProcessing(scope, executor)
    }

    private fun processMethodTransitively(
        resolvedDecl: ResolvedMethodLikeDeclaration,
        astDecl: CallableDeclaration<*>,
        existingReasons: List<ReachableReason>,
        executor: ExecutorService
    ) {
        if (existingReasons.any { it is ReachableReason.DirectlyReferenced }) {
            return
        }

        context.resolveRefTypeNodeDeclsAsSequence(astDecl) { isNodeInCallableHeader(it) }
            .forEach { (_, resolvedTypeDecl) ->
                resolvedTypeDecl?.let {
                    // Any types in the callable header needs to be processed as a full entity, because the class may
                    // have transitive supertypes, annotations, delegating constructors, overriding methods, and/or
                    // final fields that need to be considered
                    executor.queueJob(it, ReachableReason.TransitiveCallableHeader(astDecl) )
                }
            }

        val refLikeDecl = astDecl.containingType
        val refLikeDeclReason = ReachableReason.TransitiveDeclaringTypeOfMember(astDecl)
        when (refLikeDecl) {
            is ReferenceTypeLikeDeclaration.AnonClassDecl -> {
                check(astDecl is MethodDeclaration)

                executor.queueJob(
                    context.resolveDeclaration<ResolvedReferenceTypeDeclaration>(refLikeDecl.node),
                    refLikeDeclReason
                )
            }

            is ReferenceTypeLikeDeclaration.EnumConstDecl -> {
                check(astDecl is MethodDeclaration)

                executor.queueJob(
                    context.resolveDeclaration<ResolvedEnumConstantDeclaration>(refLikeDecl.node),
                    refLikeDeclReason
                )
            }

            is ReferenceTypeLikeDeclaration.TypeDecl -> {
                executor.queueJob(
                    context.resolveDeclaration<ResolvedReferenceTypeDeclaration>(refLikeDecl.node),
                    refLikeDeclReason
                )
            }
        }

        when (resolvedDecl) {
            is ResolvedConstructorDeclaration -> {
                val ctorDecl = astDecl.asConstructorDeclaration()
                val astTypeDecl = checkNotNull(refLikeDecl as ReferenceTypeLikeDeclaration.TypeDecl).node
                val resolvedTypeDecl = context.resolveDeclaration<ResolvedReferenceTypeDeclaration>(refLikeDecl.node)

                val explicitCtorInvocationStmt = ctorDecl.explicitCtorInvocationStmt
                if (explicitCtorInvocationStmt != null) {
                    val reason = if (explicitCtorInvocationStmt.isThis) {
                        ReachableReason.TransitiveCtorForClass(astTypeDecl, ctorDecl)
                    } else {
                        ReachableReason.TransitiveCtorForSubclass(astTypeDecl, ctorDecl)
                    }

                    executor.queueJob(
                        context.resolveDeclaration<ResolvedConstructorDeclaration>(explicitCtorInvocationStmt),
                        reason
                    )

                    executor.queueJob(explicitCtorInvocationStmt)
                } else {
                    with(context) {
                        findSuperclassNoArgConstructor(astTypeDecl, resolvedTypeDecl)?.let {
                            executor.queueJob(
                                it,
                                ReachableReason.TransitiveCtorForSubclass(
                                    astTypeDecl.asClassOrInterfaceDeclaration(),
                                    ctorDecl
                                )
                            )
                        }
                    }
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
            if (resolvedDecl is DefaultConstructorDeclaration<*>) {
                executor.queueJob(resolvedDecl.declaringType(), inclusionReasons)
                return
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

            val existingReasons = astDecl.inclusionReasonsData.synchronizedWith { toList() }

            val keyExists = callableQueue.put(resolvedDecl, astDecl)
            astDecl.inclusionReasonsData.addAll(inclusionReasons)

            // There are two passes - Full and Transitive Pass
            // We early-return if:
            // - A full pass has been performed
            // - An transitive pass has been performed, and `baselineReasons` do not add any directly-reachable reasons
            if (keyExists) {
                if (existingReasons.any { it is ReachableReason.DirectlyReferenced }) {
                    return
                }
                if (existingReasons.none { it is ReachableReason.DirectlyReferenced } && inclusionReasons.none { it is ReachableReason.DirectlyReferenced }) {
                    return
                }
            }

            LOGGER.info("Processing ${resolvedDecl.qualifiedSignature}")

            processMethodTransitively(resolvedDecl, astDecl, existingReasons, executor)

            val baselineReasons = inclusionReasons.filterIsInstance<ReachableReason.ByBaselineMethod>()
            if (baselineReasons.isEmpty()) {
                return
            }

            LOGGER.info("Processing ${resolvedDecl.qualifiedSignature} (!!)")

            check(baselineReasons.map { it.bytecodeMethod }.all { it.reachable })

            processNodesInScope(astDecl, executor)
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

    private fun processAnnotation(
        annotationExpr: AnnotationExpr,
        executor: ExecutorService
    ) {
        try {
            val keyExists = annoExprQueue.put(annotationExpr, Unit)
            if (keyExists) {
                return
            }

            findAndAddNodesForProcessing(annotationExpr, executor, true)
        } catch (ex: Throwable) {
            LOGGER.error("Unexpected error while executing Job(annotationExpr=${annotationExpr})", ex)
        } finally {
            annoExprQueue.pop(annotationExpr)

            queueLock.withLock {
                if (!hasDeclInFlight()) {
                    noDeclInFlight.signal()
                }
            }
        }
    }

    private fun processArgument(
        argExpr: Expression,
        executor: ExecutorService
    ) {
        try {
            val keyExists = argExprQueue.put(argExpr, Unit)
            if (keyExists) {
                return
            }

            findAndAddNodesForProcessing(argExpr, executor, true)
        } catch (ex: Throwable) {
            LOGGER.error("Unexpected error while executing Job(argExpr=${argExpr})", ex)
        } finally {
            argExprQueue.pop(argExpr)

            queueLock.withLock {
                if (!hasDeclInFlight()) {
                    noDeclInFlight.signal()
                }
            }
        }
    }

    private fun processInitDecl(
        initDecl: InitializerDeclaration,
        executor: ExecutorService
    ) {
        try {
            val keyExists = initDeclQueue.put(initDecl, Unit)
            if (keyExists) {
                return
            }

            findAndAddNodesForProcessing(initDecl, executor, true)
        } catch (ex: Throwable) {
            LOGGER.error("Unexpected error while executing Job(initDecl=${initDecl})", ex)
        } finally {
            initDeclQueue.pop(initDecl)

            queueLock.withLock {
                if (!hasDeclInFlight()) {
                    noDeclInFlight.signal()
                }
            }
        }
    }

    internal fun preprocessCUs() {
        val preprocessPipeline = PopulateCoverageData(context, coverage, baselineDir) andThen
                PopulateReachabilityInfo(context, coverage, baselineDir)

        // Before queuing methods for processing, populate baseline load/creation data first
        runInForkJoinPool(threads) {
            context.loadedCompilationUnits
                .parallelStream()
                .forEachOrdered { preprocessPipeline(it) }
        }
    }

    private fun processInitialSet(cu: CompilationUnit, executor: ExecutorService) {
        when (val coverageSource = coverage.getCUSource(cu, allSourceClasses, allTestClasses)) {
            is CoverageSource.CoberturaCoverage,
            is CoverageSource.JacocoCoverage -> {
                cu.findAll<CallableDeclaration<*>>()
                    .forEach {
                        val execDecl = ExecutableDeclaration.create(it)
                        val bytecodeMethod = execDecl.associatedBytecodeMethodData

                        if (bytecodeMethod != null && bytecodeMethod.reachable) {
                            val resolvedDecl = context.resolveDeclarationOrNull<ResolvedMethodLikeDeclaration>(it)
                            resolvedDecl?.also {
                                executor.queueJob(
                                    resolvedDecl,
                                    ReachableReason.ByBaselineMethod(bytecodeMethod)
                                )
                            }
                        }
                    }
            }

            is CoverageSource.ClassLoader -> {
                val primaryType = cu.primaryType.get()
                val fqn = primaryType.fullyQualifiedName.get()

                val entrypointsInCU = entrypoints
                    .filter { it.testClass.className == fqn }
                if (entrypointsInCU.isNotEmpty()) {
                    entrypointsInCU
                        .flatMap { entrypoint ->
                            val resolvedTypeDecl = context.resolveDeclaration<ResolvedReferenceTypeDeclaration>(primaryType)

                            findStaticallyReachableMethodsInTestClass(entrypoint, resolvedTypeDecl).map {
                                val bytecodeMethod = BytecodeMethod(
                                    it.className,
                                    it.name,
                                    it.getJvmDescriptor(),
                                    true
                                )

                                it to setOf(ReachableReason.ByBaselineMethod(bytecodeMethod))
                            }
                        }
                        .forEach { (methodDecl, reasons) ->
                            executor.queueJob(methodDecl, reasons)
                        }
                }

                val isPresent = fqn in coverageSource.value
                cu.findAll<TypeDeclaration<*>>()
                    .forEach { typeDecl ->
                        if (isPresent) {
                            val resolvedTypeDecl = context.resolveDeclaration<ResolvedReferenceTypeDeclaration>(typeDecl)

                            executor.queueJob(resolvedTypeDecl, ReachableReason.ByBaselineClass(typeDecl.fullyQualifiedName.get()))

                            // Assume that all methods in the type are used as well
                            with(context.symbolSolverCache) {
                                getDeclaredMethods(resolvedTypeDecl) + getConstructors(resolvedTypeDecl)
                            }.forEach {
                                val bytecodeMethod = BytecodeMethod(
                                    it.className,
                                    it.name,
                                    it.getJvmDescriptor(),
                                    true
                                )

                                executor.queueJob(it, ReachableReason.ByBaselineMethod(bytecodeMethod))
                            }
                        } else {
                            typeDecl.transformDecisionData = NodeTransformDecision.DUMMY
                        }
                    }
            }
        }
    }

    override fun run(parallelism: Int) {
        val executor = if (parallelism > 1) {
            Executors.newFixedThreadPool(parallelism)
        } else {
            Executors.newSingleThreadExecutor()
        }

        try {
            // Before queuing methods for processing, populate baseline load/creation data first
            preprocessCUs()

            context.loadedCompilationUnits
                .parallelStream()
                .forEach {
                    processInitialSet(it, executor)
                }

            while (hasDeclInFlight()) {
                queueLock.withLock {
                    noDeclInFlight.await()
                }
            }
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
            phase { TagGroundTruthUnusedDecls(context, enableAssertions) }
            phase { TagUnusedImportsByResolution(context) }
        }

    override val mutatingReductionPipeline: PhasedPipeline
        get() = PhasedPipeline {
            phase { RemoveUnusedNodes(context, enableAssertions) }
        }

    companion object {

        private val LOGGER = Logger<CoverageBasedReducer>()
    }
}