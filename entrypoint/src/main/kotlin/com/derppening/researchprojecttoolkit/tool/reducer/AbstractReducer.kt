package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.defects4j.TestCaseCollector
import com.derppening.researchprojecttoolkit.model.EntrypointSpec
import com.derppening.researchprojecttoolkit.tool.ReducerContext
import com.derppening.researchprojecttoolkit.tool.facade.typesolver.PartitionedTypeSolver
import com.derppening.researchprojecttoolkit.tool.reachability.ReachableReason
import com.derppening.researchprojecttoolkit.tool.transform.*
import com.derppening.researchprojecttoolkit.util.*
import com.github.javaparser.ast.AccessSpecifier
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.Node
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.body.TypeDeclaration
import com.github.javaparser.ast.body.VariableDeclarator
import com.github.javaparser.ast.expr.*
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt
import com.github.javaparser.ast.type.Type
import com.github.javaparser.resolution.declarations.*
import com.github.javaparser.resolution.types.ResolvedReferenceType
import hk.ust.cse.castle.toolkit.jvm.jsl.currentRuntime
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.stream.Collectors
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.io.path.Path
import kotlin.io.path.bufferedWriter
import kotlin.jvm.optionals.getOrNull

abstract class AbstractReducer(
    protected val classpath: String,
    protected val sourcesClasspath: String,
    protected val sourceRoots: List<Path>,
    protected val entrypoints: List<EntrypointSpec>,
    protected val enableAssertions: Boolean,
    protected val enabledOptimizations: Set<Optimization>,
    threads: Int?
) {

    enum class Optimization {
        ASSIGNED_TYPES_NARROWING,
        USE_GENERICS_SOLVER;

        companion object {

            val ALL: Set<Optimization> = values().toSet()
        }
    }

    class ConcurrentProcessingQueue<InT : Any, OutT : Any>(private val comparator: Comparator<InT>) {

        private val lk = ReentrantReadWriteLock()
        private val inFlight = mutableListOf<InT>()
        private val accumulated = ConcurrentSkipListMap<InT, OutT>(comparator) as MutableMap<InT, OutT>

        val entries: SortedSet<Map.Entry<InT, OutT>>
            get() = accumulated.entries.toSortedSet(Comparator.comparing(Map.Entry<InT, OutT>::key, comparator))
        val keys: SortedSet<InT>
            get() = accumulated.keys.toSortedSet(comparator)
        val values: Collection<OutT>
            get() = accumulated.values.toList()

        fun isQueueEmpty(): Boolean = lk.read { inFlight.isEmpty() }

        fun push(e: InT): Unit = lk.write { inFlight.add(e) }
        fun pushAll(elements: Collection<InT>): Unit = lk.write { inFlight.addAll(elements) }

        fun put(e: InT, v: OutT): Boolean = accumulated.putIfAbsent(e, v) != null

        fun pop(e: InT): Unit = lk.write { inFlight.remove(e) }
        fun popAll(elements: Collection<InT>): Unit = lk.write { inFlight.removeAll(elements) }
    }

    protected val threads = threads ?: currentRuntime.availableProcessors()

    internal val context by lazy {
        val sourceRootTypeSolvers = getTypeSolversForSourceRoot(sourceRoots)
        val classpathTypeSolvers = getTypeSolversForClasspath(classpath)
        val sourcesClasspathTypeSolvers = getTypeSolversForSourceRoot(sourcesClasspath.split(':').map { Path(it) })
        val unifiedTypeSolver = PartitionedTypeSolver(
            sourceRootTypeSolvers,
            classpathTypeSolvers + sourcesClasspathTypeSolvers + jreTypeSolver,
            true
        )

        ReducerContext(sourceRoots, unifiedTypeSolver, enabledOptimizations)
    }

    /**
     * The working set of classes.
     */
    val classesWorkingSet = context.classes
        .filter { it.fullyQualifiedName.isPresent }
        .toSortedSet(TYPE_DECL_COMPARATOR)
            as MutableSet<TypeDeclaration<*>>

    internal val cuFromClassesWorkingSet: List<CompilationUnit>
        get() = runInForkJoinPool(threads) {
            val cus = classesWorkingSet
                .parallelStream()
                .filter { it.isTopLevelType && it.findCompilationUnit().get().primaryType.get() === it }
                .map { it.findCompilationUnit().get() }
                .collect(Collectors.toList())

            assert(cus.distinctBy { it.storage.get().path }.size == cus.size)

            cus
        }

    internal val taggedCUs by lazy {
        LOGGER.info("Running tag-reduction pipeline on compilation units")

        runInForkJoinPool(threads) {
            taggingReductionPipeline(cuFromClassesWorkingSet)
        }
    }

    /**
     * The working set of [CompilationUnit].
     *
     * This field affects the set of files which will be outputted by [getTransformedCompilationUnits].
     */
    private val cuWorkingSet: List<CompilationUnit>
        get() = runInForkJoinPool(threads) {
            taggedCUs
                .also { LOGGER.info("Cloning compilation units for reduction") }
                .parallelStream()
                .map { it.clone() }
                .collect(Collectors.toList())
                .also { LOGGER.info("Running mutate-reduction pipeline on compilation units") }
                .let { mutatingReductionPipeline(it) }
        }

    /**
     * The set of classes which was initially loaded.
     */
    private val originalClassSet = runInForkJoinPool(threads) {
        context.loadedCompilationUnits
            .parallelStream()
            .map { it.clone() }
            .flatMap { it.findAll<TypeDeclaration<*>>().stream() }
            .filter { it.fullyQualifiedName.isPresent }
            .collect(Collectors.toCollection { ConcurrentSkipListSet(TYPE_DECL_COMPARATOR) })
            .let { it as Set<TypeDeclaration<*>> }
    }

    abstract fun run(parallelism: Int = threads)

    /**
     * The read-only transform pass(es) to apply to each compilation unit.
     */
    internal abstract val taggingReductionPipeline: PhasedPipeline

    /**
     * The mutating transform pass(es) to apply to each compilation unit.
     */
    internal abstract val mutatingReductionPipeline: PhasedPipeline

    protected fun resolveTypesToDeclAsSequence(
        scope: Node,
        filter: (Node) -> Boolean = { true }
    ): Sequence<Pair<Node, ResolvedReferenceTypeDeclaration>> {
        return context.resolveRefTypeNodeDeclsAsSequence(scope, filter)
            .mapNotNull { (node, resolvedDecl) -> resolvedDecl?.let { node to it } }
    }

    /**
     * Creates a [Map] which matches all [Type] under the scope to its respective [ResolvedReferenceTypeDeclaration].
     */
    protected fun resolveCallExprsToDeclAsSequence(
        scope: Node,
        filter: (Expression) -> Boolean = { true }
    ): Sequence<Pair<Expression, ResolvedMethodLikeDeclaration>> {
        return context.resolveCallExprDeclsAsSequence(scope, filter)
            .mapNotNull { (node, resolvedDecl) -> resolvedDecl?.let { node to it } }
    }

    /**
     * Creates a [Map] which matches all [MethodCallExpr] and [ObjectCreationExpr] under the [scope] to its respective
     * [ResolvedMethodLikeDeclaration].
     */
    protected fun resolveCallExprsToDecl(
        scope: Node,
        filter: (Expression) -> Boolean = { true }
    ): Map<Expression, ResolvedMethodLikeDeclaration> {
        return resolveCallExprsToDeclAsSequence(scope, filter)
            .associateTo(NodeRangeTreeMap(context)) { it }
    }

    protected fun resolveExplicitCtorStmtsToDeclAsSequence(
        scope: Node
    ): Sequence<Pair<ExplicitConstructorInvocationStmt, ResolvedConstructorDeclaration>> {
        return context.resolveExplicitCtorStmtsAsSequence(scope)
            .mapNotNull { (node, resolvedDecl) -> resolvedDecl?.let { node to it } }
    }

    /**
     * Creates a [Map] which matches all [ExplicitConstructorInvocationStmt] under the scope to its respective
     * [ResolvedConstructorDeclaration].
     */
    protected fun resolveExplicitCtorStmtsToDecl(scope: Node): SortedMap<ExplicitConstructorInvocationStmt, ResolvedConstructorDeclaration> {
        return resolveExplicitCtorStmtsToDeclAsSequence(scope)
            .associateTo(NodeRangeTreeMap(context)) { it }
    }

    protected fun resolveFieldAccessExprsToDeclAsSequence(
        scope: Node,
        filter: (FieldAccessExpr) -> Boolean = { true }
    ): Sequence<Pair<FieldAccessExpr, ResolvedValueDeclaration>> {
        return scope.asSequence()
            .filterIsInstance<FieldAccessExpr>()
            .filter(filter)
            .mapNotNull { node ->
                (context.resolveDeclarationOrNull<ResolvedDeclaration>(node) as? ResolvedValueDeclaration)
                    ?.let { node to it }
            }
    }

    /**
     * Creates a [Sequence] which matches all [NameExpr] under the [scope] to its respective
     * [VariableDeclarator].
     */
    protected fun resolveNameExprsToDeclAsSequence(
        scope: Node,
        filter: (NameExpr) -> Boolean = { true }
    ): Sequence<Pair<NameExpr, ResolvedValueDeclaration>> {
        return scope.asSequence()
            .filterIsInstance<NameExpr>()
            .filter(filter)
            .mapNotNull { node ->
                (context.resolveDeclarationOrNull<ResolvedDeclaration>(node) as? ResolvedValueDeclaration)
                    ?.let { node to it }
            }
    }

    /**
     * Returns all statically-resolved maybe-reachable methods with their cause.
     */
    protected fun getStaticallyResolvedMethodsWithCause(
        methodCallExprs: Map<Expression, ResolvedMethodLikeDeclaration>
    ): List<Pair<ResolvedMethodLikeDeclaration, ReachableReason>> {
        return methodCallExprs
            .map { (expr, decl) ->
                decl to ReachableReason.ReferencedBySymbolName(expr)
            }
    }

    /**
     * Returns all explicitly-invoked constructors with their cause of inclusion (i.e. explicitly invoked by a
     * [ExplicitConstructorInvocationStmt]).
     */
    protected fun getExplicitCtorsWithCause(
        explicitCtorStmts: Map<ExplicitConstructorInvocationStmt, ResolvedConstructorDeclaration>
    ): List<Pair<ResolvedMethodLikeDeclaration, ReachableReason>> {
        return explicitCtorStmts
            .map { (stmt, decl) ->
                decl to ReachableReason.ReferencedByCtorCallByExplicitStmt(stmt)
            }
    }

    /**
     * Determines whether if [expr], when used as the [scope expression of a method call][MethodCallExpr.scope], can be
     * statically resolved to one and only one method declaration.
     *
     * For example, the following will evaluate to true:
     * ```
     * new Object().clone();  // Always evaluates to java.lang.Object.clone()
     * ```
     *
     * And the following will evaluate to false (assuming a non-static context):
     * ```
     * foo();  // May evaluate to the static target of the method call, or any overriding implementation
     * ```
     *
     * If this cannot be determined statically (i.e. without the use of dynamic resolution), conservatively returns
     * `false`.
     */
    protected fun isExprTypeStaticForMethodCallExpr(expr: Expression?): Boolean {
        return when (expr) {
            // ArrayCreationExpr always has a type of T[], and the methods/fields it exposes is the same regardless of
            // the type of T
            is ArrayCreationExpr -> true

            is AnnotationExpr,
            is BinaryExpr,
            is ClassExpr,
            is InstanceOfExpr,
            is LiteralExpr,
            is SuperExpr,
            is UnaryExpr -> true

            // ObjectCreationExpr always has a type of T,
            is ObjectCreationExpr -> !expr.anonymousClassBody.isPresent

            is EnclosedExpr -> isExprTypeStaticForMethodCallExpr(expr.inner)
            is ConditionalExpr -> {
                isExprTypeStaticForMethodCallExpr(expr.thenExpr) && isExprTypeStaticForMethodCallExpr(expr.elseExpr)
            }

            else -> false
        }
    }

    protected fun getOverriddenMethodsWithCause(
        overriddenMethods: Pair<ResolvedMethodDeclaration, Set<ResolvedMethodDeclaration>>
    ): List<Pair<ResolvedMethodLikeDeclaration, ReachableReason>> =
        overriddenMethods.second
            .map { overriddenMethods.first to ReachableReason.TransitiveOverriddenCallTarget(it) }

    /**
     * Remaps maybe-reachable fields to its reachable reason.
     */
    protected fun mapToReason(
        node: Node,
        resolvedDecl: ResolvedReferenceTypeDeclaration
    ): ReachableReason {
        return if (node.parentNode.getOrNull() is ObjectCreationExpr) {
            ReachableReason.ReferencedBySymbolName(node.parentNode.get() as ObjectCreationExpr)
        } else {
            context.getReachableReasonFromNode(node, resolvedDecl)
        }
    }

    /**
     * Converts a resolved reasons mapping to use AST declarations.
     */
    protected fun mapResolvedMethodsToAst(
        mapping: List<Pair<ResolvedMethodLikeDeclaration, ReachableReason>>
    ): SortedMap<ResolvedMethodLikeDeclaration, out Set<ReachableReason>> {
        return TreeMap<_, MutableSet<ReachableReason>>(ResolvedCallableDeclComparator(context)).apply {
            mapping.forEach { (resolvedDecl, reason) ->
                getOrPut(resolvedDecl) { mutableSetOf() }.add(reason)
            }
        }
    }

    /**
     * Remaps maybe-reachable fields to its reachable reason.
     */
    protected fun mapToReason(
        expr: Expression,
        @Suppress("UNUSED_PARAMETER") resolvedDecl: ResolvedValueDeclaration
    ): ReachableReason = ReachableReason.ReferencedBySymbolName(expr)

    internal fun getTransformedCompilationUnits(
        compilationUnits: Collection<CompilationUnit>,
        outDir: Path,
        baseDir: Path? = null,
        mergeSourceRoots: Boolean = false,
        sourceRootMapping: MutableMap<Path, Path> = mutableMapOf()
    ): List<CompilationUnit> {
        require(compilationUnits.all { it.storage.isPresent })
        if (mergeSourceRoots) {
            val cuSourceRelPaths = compilationUnits
                .map { it.storage.get() }
                .map { it.sourceRoot.relativize(it.path) }

            require(cuSourceRelPaths.distinct() == cuSourceRelPaths)
        }

        val sourceRootGroups = compilationUnits.groupBy { it.storage.get().sourceRoot }
        sourceRootMapping.clear()
        when {
            mergeSourceRoots -> sourceRootGroups.keys.associateWith { outDir }
            baseDir != null -> sourceRootGroups.keys.associateWith { outDir.resolve(baseDir.relativize(it)) }
            else -> sourceRootGroups.keys.withIndex().associate { it.value to outDir.resolve("srcRoot${it.index}") }
        }.let { sourceRootMapping.putAll(it) }

        outDir.resolve("source-mapping.txt").bufferedWriter().use { writer ->
            sourceRootMapping.forEach { (src, out) ->
                writer.appendLine("$src -> $out")
            }
        }

        return compilationUnits
            .parallelStream()
            .filter { it.types.isNotEmpty() }
            .map {
                val sourceLocation = it.storage.get()
                val relPathToSourceRoot = sourceLocation.sourceRoot.relativize(sourceLocation.path)
                val targetLocation =
                    checkNotNull(sourceRootMapping[sourceLocation.sourceRoot]).resolve(relPathToSourceRoot)

                it.setStorage(targetLocation, sourceLocation.encoding)
            }
            .collect(Collectors.toList())
    }

    /**
     * Transforms all relevant [CompilationUnit] by removing or dummying unused classes, methods and fields, and
     * setting the [CompilationUnit.storage] of each compilation unit to a path relative to [outDir].
     *
     * By default, this preserves the location of each source file within each source root. The optional parameters can
     * be used to change this behavior.
     *
     * To save the compilation units, one can use `it.storage.get().save()` to output the compilation unit to the file.
     *
     * @param baseDir If specified, uses the given directory as the base directory to relativize all source roots. For
     * instance, given source roots `[/workspace/a, /workspace/b]` and base directory `/workspace`, the resulting source
     * roots will be outputted into `$outDir/a` and `$outDir/b` respectively.
     * @param mergeSourceRoots If `true`, merges all source roots into a single directory. The package root will be
     * [outDir].
     * @param sourceRootMapping If specified, puts the mapping between the source roots to its output directory into the
     * given [Map].
     * @return The [List] of [CompilationUnit] after applying the reduction pipeline.
     */
    fun getTransformedCompilationUnits(
        outDir: Path,
        baseDir: Path? = null,
        mergeSourceRoots: Boolean = false,
        sourceRootMapping: MutableMap<Path, Path> = mutableMapOf()
    ): List<CompilationUnit> = getTransformedCompilationUnits(
        cuWorkingSet,
        outDir,
        baseDir,
        mergeSourceRoots,
        sourceRootMapping
    )

    /**
     * @return The set of all top-level classes from this [AbstractReducer].
     */
    internal fun getAndDumpAllTopLevelClasses(
        filter: (TypeDeclaration<*>) -> Boolean = { true }
    ): Set<TypeDeclaration<*>> {
        return originalClassSet
            .parallelStream()
            .filter { it.canBeReferencedByName }
            .filter(filter)
            .collect(Collectors.toCollection { ConcurrentSkipListSet(TYPE_DECL_COMPARATOR) })
    }

    /**
     * Finds all methods related to JUnit's execution which may not be statically reachable.
     */
    protected fun findJUnitRelatedMethods(
        entrypointMethods: Collection<ResolvedMethodDeclaration>,
        allMethods: Collection<ResolvedMethodDeclaration>
    ): List<ResolvedMethodDeclaration> {
        val isTestClass = entrypointMethods.any { methodDecl ->
            methodDecl.toTypedAstOrNull<MethodDeclaration>(null)
                ?.annotations
                ?.any {
                    val annoDecl = context.resolveDeclaration<ResolvedAnnotationDeclaration>(it)
                    annoDecl.qualifiedName in JUNIT_TEST_ANNO_QNAME
                } == true
        }

        return if (isTestClass) {
            allMethods.filter { methodDecl ->
                methodDecl.toTypedAstOrNull<MethodDeclaration>(null)
                    ?.annotations
                    ?.any {
                        val annoDecl = context.resolveDeclaration<ResolvedAnnotationDeclaration>(it)
                        annoDecl.qualifiedName in JUNIT_BEFOREAFTER_ANNO_QNAME
                    } == true
            }
        } else emptyList()
    }

    /**
     * Finds all methods related to JUnit's execution which may not be statically reachable.
     */
    protected fun findJUnitRelatedMethods(
        typeDecl: ResolvedReferenceTypeDeclaration
    ): List<ResolvedMethodDeclaration> {
        if (typeDecl.isAnnotation) {
            return emptyList()
        }

        val allMethods = context.symbolSolverCache
            .getAllMethods(typeDecl)
            .map { it.declaration }

        return allMethods.filter { methodDecl ->
            methodDecl.toTypedAstOrNull<MethodDeclaration>(null)
                ?.annotations
                ?.any {
                    val annoDecl = context.resolveDeclaration<ResolvedAnnotationDeclaration>(it)
                    annoDecl.qualifiedName in JUNIT_BEFOREAFTER_ANNO_QNAME
                } == true
        }
    }

    /**
     * Finds all methods related to JUnit 3's execution which may not be statically reachable.
     */
    protected fun findJUnit3RelatedMethods(
        possiblyTestClass: ResolvedReferenceTypeDeclaration,
        allMethods: Collection<ResolvedMethodDeclaration>
    ): List<ResolvedMethodDeclaration> {
        val isTestClass = context.symbolSolverCache.getAllAncestors(possiblyTestClass)
            .any { it.qualifiedName == JUNIT3_TESTCASE_QNAME }

        return if (isTestClass) {
            allMethods.filter { it.name in JUNIT3_BEFOREAFTER_METHODS }
        } else emptyList()
    }

    /**
     * Finds all methods related to JUnit 3's execution which may not be statically reachable.
     */
    protected fun findJUnit3RelatedMethods(
        possiblyTestClass: ResolvedReferenceTypeDeclaration
    ): List<ResolvedMethodDeclaration> {
        if (possiblyTestClass.isAnnotation) {
            return emptyList()
        }

        return findJUnit3RelatedMethods(
            possiblyTestClass,
            context.symbolSolverCache.getAllMethods(possiblyTestClass).map { it.declaration }
        )
    }

    /**
     * Finds all methods related to [java.io.Serializable] which may not be statically reachable.
     */
    protected fun findSerializableMethods(
        resolvedTypeDecl: ResolvedReferenceTypeDeclaration,
        allAncestors: List<ResolvedReferenceType> = context.symbolSolverCache.getAllAncestors(resolvedTypeDecl)
    ): List<ResolvedMethodDeclaration> {
        if (allAncestors.none { it.qualifiedName == SERIALIZABLE_QNAME }) {
            return emptyList()
        }

        val methods = context.symbolSolverCache.getDeclaredMethods(resolvedTypeDecl)

        return buildList {
            // private void writeObject(java.io.ObjectOutputStream out)
            //    throws IOException;
            methods.singleOrNull { method ->
                method.name == WRITEOBJECT_METHOD_NAME &&
                        method.numberOfParams == 1 &&
                        method.getParam(0).type
                            .let { it.isReferenceType && it.asReferenceType().qualifiedName == OBJECTOUTPUTSTREAM_QNAME } &&
                        method.specifiedExceptions
                            .map { it.asReferenceType().qualifiedName }
                            .let { exceptionsQNames -> arrayOf(IOEXCEPTION_QNAME).all { it in exceptionsQNames } } &&
                        method.returnType.isVoid &&
                        method.accessSpecifier() == AccessSpecifier.PRIVATE

            }?.also { add(it) }

            // private void readObject(java.io.ObjectInputStream in)
            //    throws IOException, ClassNotFoundException;
            methods.singleOrNull { method ->
                method.name == READOBJECT_METHOD_NAME &&
                        method.numberOfParams == 1 &&
                        method.getParam(0).type
                            .let { it.isReferenceType && it.asReferenceType().qualifiedName == OBJECTINPUTSTREAM_QNAME } &&
                        method.specifiedExceptions
                            .map { it.asReferenceType().qualifiedName }
                            .let { exceptionsQNames ->
                                arrayOf(IOEXCEPTION_QNAME, CLASSNOTFOUNDEXCEPTION_QNAME).all { it in exceptionsQNames }
                            } &&
                        method.returnType.isVoid &&
                        method.accessSpecifier() == AccessSpecifier.PRIVATE
            }?.also { add(it) }

            // private void readObjectNoData()
            //    throws ObjectStreamException;
            methods.singleOrNull { method ->
                method.name == READOBJECTNODATA_METHOD_NAME &&
                        method.numberOfParams == 0 &&
                        method.specifiedExceptions
                            .map { it.asReferenceType().qualifiedName }
                            .let { exceptionsQNames -> arrayOf(OBJECTSTREAMEXCEPTION_QNAME).all { it in exceptionsQNames } } &&
                        method.returnType.isVoid &&
                        method.accessSpecifier() == AccessSpecifier.PRIVATE
            }?.also { add(it) }

            // ANY-ACCESS-MODIFIER Object writeReplace() throws ObjectStreamException;
            methods.singleOrNull { method ->
                method.name == WRITEREPLACE_METHOD_NAME &&
                        method.numberOfParams == 0 &&
                        method.specifiedExceptions
                            .map { it.asReferenceType().qualifiedName }
                            .let { exceptionsQNames -> arrayOf(OBJECTSTREAMEXCEPTION_QNAME).all { it in exceptionsQNames } } &&
                        method.returnType.let { it.isReferenceType && it.asReferenceType().isJavaLangObject }
            }?.also { add(it) }

            // ANY-ACCESS-MODIFIER Object readResolve() throws ObjectStreamException;
            methods.singleOrNull { method ->
                method.name == READRESOLVE_METHOD_NAME &&
                        method.numberOfParams == 0 &&
                        method.specifiedExceptions
                            .map { it.asReferenceType().qualifiedName }
                            .let { exceptionsQNames -> arrayOf(OBJECTSTREAMEXCEPTION_QNAME).all { it in exceptionsQNames } } &&
                        method.returnType.let { it.isReferenceType && it.asReferenceType().isJavaLangObject }
            }?.also { add(it) }
        }
    }

    /**
     * Finds all statically reachable methods in a test class as specified by [entrypoint].
     */
    protected fun findStaticallyReachableMethodsInTestClass(
        entrypoint: EntrypointSpec,
        resolvedEntrypointDecl: ResolvedReferenceTypeDeclaration
    ): List<ResolvedMethodLikeDeclaration> {
        val allAncestors = resolvedEntrypointDecl
            .let {
                context.symbolSolverCache.getAllAncestors(it).map { it.toResolvedTypeDeclaration() } + it
            }
        val ctorDecls = allAncestors
            .flatMap { ancestorClass ->
                if (TestCaseCollector.isJUnit3TestClass(ancestorClass)) {
                    val noArgCtor = ancestorClass.constructors
                        .singleOrNull {
                            it.numberOfParams == 0
                        }
                    val oneArgCtor = ancestorClass.constructors
                        .singleOrNull {
                            it.numberOfParams == 1 && it.getParam(0).type.let { it.isReferenceType && it.asReferenceType().qualifiedName == "java.lang.String" }
                        }

                    listOfNotNull(noArgCtor, oneArgCtor)
                } else emptyList()
            }

        val allMethodDecls = context.symbolSolverCache.getAllMethods(resolvedEntrypointDecl)
            .map { it.declaration }
        val methodDecls = when (entrypoint) {
            is EntrypointSpec.ClassInput -> allMethodDecls
            is EntrypointSpec.MethodInput -> {
                val methodsWithMatchingName = allMethodDecls
                    .filter { it.name == entrypoint.methodName }

                if (methodsWithMatchingName.isEmpty()) {
                    LOGGER.error("Cannot find method with name `${entrypoint.methodName}` in class `${resolvedEntrypointDecl.qualifiedName}` or its superclasses")
                }

                val modernJUnitMethods = findJUnitRelatedMethods(methodsWithMatchingName, allMethodDecls)
                val legacyJUnitMethods = findJUnit3RelatedMethods(resolvedEntrypointDecl, allMethodDecls)

                methodsWithMatchingName + modernJUnitMethods + legacyJUnitMethods
            }
        }

        return ctorDecls + methodDecls
    }

    companion object {

        private val LOGGER = Logger<AbstractReducer>()

        /**
         * Fully-qualified names of annotations which JUnit observes as a test method.
         */
        private val JUNIT_TEST_ANNO_QNAME = listOf(
            "org.junit.Test",
            "org.junit.jupiter.api.Test",
            "org.junit.jupiter.api.RepeatedTest",
            "org.junit.jupiter.params.ParameterizedTest",
            "org.junit.jupiter.api.TestFactory"
        )

        /**
         * Fully-qualified names of annotations which JUnit observes as a method which should be executed before all
         * tests in a test class.
         */
        private val JUNIT_BEFOREALL_ANNO_QNAME = listOf(
            "org.junit.BeforeClass",
            "org.junit.jupiter.api.BeforeAll"
        )

        /**
         * Fully-qualified names of annotations which JUnit observes as a method which should be executed before each
         * test in a test class.
         */
        private val JUNIT_BEFOREEACH_ANNO_QNAME = listOf(
            "org.junit.Before",
            "org.junit.jupiter.api.BeforeEach"
        )

        /**
         * Fully-qualified names of annotations which JUnit observes as a method which should be executed after each
         * test in a test class.
         */
        private val JUNIT_AFTEREACH_ANNO_QNAME = listOf(
            "org.junit.After",
            "org.junit.jupiter.api.AfterEach"
        )

        /**
         * Fully-qualified names of annotations which JUnit observes as a method which should be executed after all
         * test in a test class.
         */
        private val JUNIT_AFTERALL_ANNO_QNAME = listOf(
            "org.junit.AfterClass",
            "org.junit.jupiter.api.AfterAll"
        )

        /**
         * Convenience member for all annotations which JUnit observes as pre-/post-test case execution action.
         */
        private val JUNIT_BEFOREAFTER_ANNO_QNAME by lazy {
            JUNIT_BEFOREALL_ANNO_QNAME +
                    JUNIT_BEFOREEACH_ANNO_QNAME +
                    JUNIT_AFTEREACH_ANNO_QNAME +
                    JUNIT_AFTERALL_ANNO_QNAME
        }

        /**
         * Fully-qualified name of the JUnit 3 `TestCase` class.
         */
        private val JUNIT3_TESTCASE_QNAME = "junit.framework.TestCase"

        /**
         * Name of methods which JUnit 3 observes as pre-/post-test case execution action.
         */
        private val JUNIT3_BEFOREAFTER_METHODS = listOf("setUp", "tearDown")

        /**
         * Fully-qualified name of [java.io.Serializable].
         */
        private val SERIALIZABLE_QNAME = java.io.Serializable::class.java.name

        private const val READOBJECT_METHOD_NAME = "readObject"
        private const val READOBJECTNODATA_METHOD_NAME = "readObjectNoData"
        private const val READRESOLVE_METHOD_NAME = "readResolve"
        private const val WRITEOBJECT_METHOD_NAME = "writeObject"
        private const val WRITEREPLACE_METHOD_NAME = "writeReplace"
        private val CLASSNOTFOUNDEXCEPTION_QNAME = java.lang.ClassNotFoundException::class.java.name
        private val IOEXCEPTION_QNAME = java.io.IOException::class.java.name
        private val OBJECTINPUTSTREAM_QNAME = java.io.ObjectInputStream::class.java.name
        private val OBJECTOUTPUTSTREAM_QNAME = java.io.ObjectOutputStream::class.java.name
        private val OBJECTSTREAMEXCEPTION_QNAME = java.io.ObjectStreamException::class.java.name
    }
}