package com.derppening.researchprojecttoolkit.tool

import com.derppening.researchprojecttoolkit.model.VariableLikeDeclaration
import com.derppening.researchprojecttoolkit.tool.facade.FuzzySymbolSolver
import com.derppening.researchprojecttoolkit.tool.facade.typesolver.PartitionedTypeSolver
import com.derppening.researchprojecttoolkit.tool.reachability.ReachableReason
import com.derppening.researchprojecttoolkit.tool.reducer.AbstractReducer
import com.derppening.researchprojecttoolkit.util.*
import com.derppening.researchprojecttoolkit.visitor.ASTPathGenerator
import com.github.javaparser.JavaParser
import com.github.javaparser.ParseResult
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.Node
import com.github.javaparser.ast.body.*
import com.github.javaparser.ast.expr.*
import com.github.javaparser.ast.nodeTypes.*
import com.github.javaparser.ast.nodeTypes.modifiers.NodeWithFinalModifier
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt
import com.github.javaparser.ast.type.ClassOrInterfaceType
import com.github.javaparser.ast.type.Type
import com.github.javaparser.resolution.declarations.*
import com.github.javaparser.resolution.logic.InferenceVariableType
import com.github.javaparser.resolution.model.typesystem.LazyType
import com.github.javaparser.resolution.model.typesystem.NullType
import com.github.javaparser.resolution.types.*
import com.github.javaparser.symbolsolver.JavaSymbolSolver
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserAnonymousClassDeclaration
import com.github.javaparser.symbolsolver.resolution.typeinference.TypeHelper
import hk.ust.cse.castle.toolkit.jvm.util.RuntimeAssertions.unreachable
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.locks.ReentrantLock
import java.util.stream.Collectors
import java.util.stream.Stream
import kotlin.concurrent.withLock
import kotlin.jvm.optionals.getOrDefault
import kotlin.jvm.optionals.getOrNull

/**
 * Context of the reducer.
 *
 * @param sourceRoots The list of source roots to be parsed by the reducer.
 * @property typeSolver The type solver for the reducer.
 */
class ReducerContext(
    private val sourceRoots: Collection<Path>,
    val typeSolver: PartitionedTypeSolver,
    private val enabledOptimizations: Set<AbstractReducer.Optimization> = AbstractReducer.Optimization.ALL
) {

    /**
     * The [JavaSymbolSolver] derived from [typeSolver].
     */
    val symbolSolver: FuzzySymbolSolver
        get() = FuzzySymbolSolver(typeSolver, symbolSolverCache, enabledOptimizations)

    /**
     * Creates a fresh instance of [JavaParser] for thread-safe parsing.
     */
    val parser: JavaParser
        get() = JavaParser(createParserConfiguration(symbolSolver))

    val compilationUnits by lazy {
        LOGGER.info("Loading compilation units")

        sourceRoots.stream()
            .map { SourceRootCompilationUnitsParser(it) { parser }.getAll() }
            .flatMap { it.entries.stream() }
            .map {
                if (!it.value.isSuccessful) {
                    LOGGER.warn("Failed to parse ${it.key}")
                    it.value.problems.forEach {
                        LOGGER.warn(it.verboseMessage)
                    }
                }

                it
            }
            .parallel()
            .collect(
                Collectors.toConcurrentMap(
                    Map.Entry<Path, ParseResult<CompilationUnit>>::key,
                    Map.Entry<Path, ParseResult<CompilationUnit>>::value
                )
            )
            .let { it as Map<Path, ParseResult<CompilationUnit>> }
    }

    val loadedCompilationUnits by lazy {
        compilationUnits.values
            .parallelStream()
            .filter { it.isSuccessful }
            .map { it.result.get() }
            .collect(Collectors.toList())
            .let { it as List<CompilationUnit> }
    }

    val symbolSolverCache = SymbolSolverCache()

    fun getCUByPrimaryTypeName(typeName: String): CompilationUnit? {
        return loadedCompilationUnits
            .singleOrNull {
                it.primaryType
                    .flatMap { it.fullyQualifiedName }
                    .map { it == typeName }
                    .orElse(false)
            }
    }

    fun getCUByPath(relPath: Path): CompilationUnit? {
        return loadedCompilationUnits
            .singleOrNull { cu ->
                cu.storage
                    .map { it.sourceRoot.relativize(it.path) }
                    .map { it == relPath }
                    .getOrDefault(false)
            }
    }

    val classes by lazy {
        LOGGER.info("Caching class resolution result")

        loadedCompilationUnits.parallelStream()
            .map { it.findAll<TypeDeclaration<*>>() }
            .flatMap { it.stream() }
            .filter { it.fullyQualifiedName.isPresent && it.canBeReferencedByName }
            .collect(Collectors.toCollection { TreeSet(TYPE_DECL_COMPARATOR) })
            .let { it as Set<TypeDeclaration<*>> }
    }

    private val classPackagePrefixes by lazy {
        LOGGER.info("Caching class package prefixes")

        loadedCompilationUnits.parallelStream()
            .unordered()
            .map { it.packageDeclaration.map { it.nameAsString }.getOrDefault("") }
            .distinct()
            .flatMap { packageName ->
                packageName.split('.')
                    .runningReduce { acc, it ->
                        "$acc.$it"
                    }
                    .stream()
            }
            .collect(Collectors.toSet())
            .let { it as Set<String> }
    }

    private val directAncestorsMapping: SortedMap<ResolvedReferenceType, SortedSet<ResolvedReferenceTypeDeclaration>> by lazy {
        LOGGER.info("Caching direct-ancestors class hierarchy mapping")

        classes.parallelStream()
            .map {
                val resolvedDecl = it.resolve()
                resolvedDecl to symbolSolverCache.getAncestors(resolvedDecl)
            }
            .flatMap { (typeDecl, ancestors) ->
                ancestors.stream().map { it to typeDecl }
            }
            .collect(
                Collectors.groupingByConcurrent(
                    { it.first },
                    { ConcurrentSkipListMap(RESOLVED_REF_TYPE_COMPARATOR) },
                    Collectors.mapping(
                        { it.second },
                        Collectors.toCollection { ConcurrentSkipListSet(RESOLVED_TYPE_DECL_COMPARATOR) }
                    )
                )
            )
            .mapValuesTo(TreeMap(RESOLVED_REF_TYPE_COMPARATOR)) { (_, v) -> TreeSet(v) }
    }

    private val allAncestorsMapping: SortedMap<ResolvedReferenceType, SortedSet<ResolvedReferenceTypeDeclaration>> by lazy {
        LOGGER.info("Caching all-ancestors class hierarchy mapping")

        classes.parallelStream()
            .map {
                val resolvedDecl = it.resolve()
                resolvedDecl to symbolSolverCache.getAllAncestors(resolvedDecl)
            }
            .flatMap { (typeDecl, ancestors) ->
                ancestors.stream().map { it to typeDecl }
            }
            .collect(
                Collectors.groupingByConcurrent(
                    { it.first },
                    { ConcurrentSkipListMap(RESOLVED_REF_TYPE_COMPARATOR) },
                    Collectors.mapping(
                        { it.second },
                        Collectors.toCollection { ConcurrentSkipListSet(RESOLVED_TYPE_DECL_COMPARATOR) }
                    )
                )
            )
            .mapValuesTo(TreeMap(RESOLVED_REF_TYPE_COMPARATOR)) { (_, v) -> TreeSet(v) }
    }

    private val anonDirectAncestorsMapping: SortedMap<ResolvedReferenceType, SortedSet<out ResolvedReferenceTypeDeclaration>> by lazy {
        LOGGER.info("Caching direct-ancestors anonymous class hierarchy mapping")

        classes.parallelStream()
            .flatMap { it.findAll<ObjectCreationExpr> { it.anonymousClassBody.isPresent }.stream() }
            .map {
                val resolvedDecl = JavaParserAnonymousClassDeclaration(it, typeSolver)
                resolvedDecl to symbolSolverCache.getAncestors(resolvedDecl)
            }
            .flatMap { (typeDecl, ancestors) ->
                ancestors.stream().map { it to typeDecl }
            }
            .collect(
                Collectors.groupingByConcurrent(
                    { it.first },
                    { ConcurrentSkipListMap(RESOLVED_REF_TYPE_COMPARATOR) },
                    Collectors.mapping(
                        { it.second },
                        Collectors.toCollection { ConcurrentSkipListSet(RESOLVED_TYPE_DECL_COMPARATOR) }
                    )
                )
            )
            .mapValuesTo(TreeMap(RESOLVED_REF_TYPE_COMPARATOR)) { (_, v) -> TreeSet(v) }
    }

    private val anonAllAncestorsMapping: SortedMap<ResolvedReferenceType, SortedSet<out ResolvedReferenceTypeDeclaration>> by lazy {
        LOGGER.info("Caching all-ancestors anonymous class hierarchy mapping")

        classes.parallelStream()
            .flatMap { it.findAll<ObjectCreationExpr> { it.anonymousClassBody.isPresent }.stream() }
            .map {
                val resolvedDecl = JavaParserAnonymousClassDeclaration(it, typeSolver)
                resolvedDecl to symbolSolverCache.getAllAncestors(resolvedDecl)
            }
            .flatMap { (typeDecl, ancestors) ->
                ancestors.stream().map { it to typeDecl }
            }
            .collect(
                Collectors.groupingByConcurrent(
                    { it.first },
                    { ConcurrentSkipListMap(RESOLVED_REF_TYPE_COMPARATOR) },
                    Collectors.mapping(
                        { it.second },
                        Collectors.toCollection { ConcurrentSkipListSet(RESOLVED_TYPE_DECL_COMPARATOR) }
                    )
                )
            )
            .mapValuesTo(TreeMap(RESOLVED_REF_TYPE_COMPARATOR)) { (_, v) -> TreeSet(v) }
    }

    val enumConstAllAncestorsMapping by lazy {
        LOGGER.info("Caching all-ancestors enum constant class hierarchy mapping")

        classes.parallelStream()
            .filter { it.isEnumDeclaration }
            .flatMap { it.asEnumDeclaration().entries.stream() }
            .filter { it.classBody.isNonEmpty }
            .map {
                val resolvedDecl = resolveDeclaration<ResolvedEnumConstantDeclaration>(it)
                resolvedDecl to resolvedDecl.type
                    .asReferenceType()
                    .let { listOf(it) + symbolSolverCache.getAllAncestors(it) }
            }
            .flatMap { (enumConstantDecl, ancestors) ->
                ancestors.stream().map { it to enumConstantDecl }
            }
            .collect(
                Collectors.groupingByConcurrent(
                    { it.first },
                    { ConcurrentSkipListMap(RESOLVED_REF_TYPE_COMPARATOR) },
                    Collectors.mapping(
                        { it.second },
                        Collectors.toCollection { ConcurrentSkipListSet(RESOLVED_VALUE_COMPARATOR) }
                    )
                )
            )
            .mapValuesTo(TreeMap(RESOLVED_REF_TYPE_COMPARATOR)) { (_, v) -> TreeSet(v) }
    }

    /**
     * Obtains the [Node] declaring the [ResolvedValueDeclaration].
     */
    private fun getAstNodeFromResolvedVarDecl(resolvedDecl: ResolvedValueDeclaration): VariableLikeDeclaration? {
        return resolvedDecl.toTypedAstOrNull<Node>(this)?.let { astNode ->
            when (astNode) {
                is NodeWithVariables<*> -> astNode.variables.single { it.nameAsString == resolvedDecl.name }
                else -> astNode
            }.let { VariableLikeDeclaration.fromNodeOrNull(it) }
        }
    }

    /**
     * Obtains the [Node] which this [assignExpr] assigns to.
     */
    internal fun getAssignExprTargetAst(assignExpr: AssignExpr): VariableLikeDeclaration? {
        fun unpackToVarExpr(expr: Expression): Expression? {
            return when (expr) {
                is ArrayAccessExpr -> unpackToVarExpr(expr.getBaseExpression())
                is CastExpr -> unpackToVarExpr(expr.expression)
                is EnclosedExpr -> unpackToVarExpr(expr.inner)
                is NameExpr,
                is FieldAccessExpr -> expr

                is MethodCallExpr -> null
                else -> TODO("Don't know how to unpack `$expr` (${expr::class.simpleName}) into a variable access expression")
            }
        }

        val unpackedTargetExpr = unpackToVarExpr(assignExpr.target)

        return assignExpr
            .runCatching {
                when (unpackedTargetExpr) {
                    is NameExpr -> symbolSolver.resolveDeclaration<ResolvedValueDeclaration>(unpackedTargetExpr)
                    is FieldAccessExpr -> symbolSolver.resolveDeclaration<ResolvedFieldDeclaration>(unpackedTargetExpr)
                    null -> null
                    else -> error("${unpackedTargetExpr::class.simpleName} is not a candidate expression type on target of assignment")
                }
            }
            .map { resolvedValueDecl ->
                resolvedValueDecl?.let { getAstNodeFromResolvedVarDecl(it) }
            }
            .onFailure { tr ->
                assignExpr.target.emitErrorMessage(tr)
            }
            .getOrNull()
    }

    private val varAssignmentMapping by lazy {
        LOGGER.info("Caching variable reassignment data")

        compilationUnits.values
            .stream()
            .map { it.result.getOrNull() }
            .filter { it != null }
            .let {
                @Suppress("UNCHECKED_CAST")
                it as Stream<CompilationUnit>
            }
            .flatMap { it.findAll<AssignExpr>().stream() }
            .map { assignExpr ->
                val astDecl = getAssignExprTargetAst(assignExpr)

                assignExpr to astDecl
            }
            .filter { it.second != null }
            .let {
                @Suppress("UNCHECKED_CAST")
                it as Stream<Pair<AssignExpr, VariableLikeDeclaration>>
            }
            .collect(
                Collectors.groupingByConcurrent(
                    { it.second.node.astBasedId },
                    Collectors.toSet()
                )
            )
            .forEach { (_, groups) ->
                val varDecl = groups.first().second.node
                val assignExprs = groups.mapTo(NodeRangeTreeSet()) { it.first }

                when (varDecl) {
                    is Parameter -> varDecl.reassignmentsData = assignExprs
                    is VariableDeclarator -> varDecl.reassignmentsData = assignExprs
                    else -> unreachable("Cannot assign reassignment data to node of type ${varDecl::class.simpleName}")
                }
            }
    }

    private val boxedPrimNames by lazy {
        ResolvedPrimitiveType.values().associateBy { it.boxTypeQName }
    }

    /**
     * Returns this [ResolvedType] with all array levels removed.
     */
    private tailrec fun ResolvedType.baseComponentType(): ResolvedType =
        if (this !is ResolvedArrayType) this else componentType.baseComponentType()

    /**
     * Returns all types which are assigned to the value represented by [resolvedDecl].
     *
     * Note that this only considers variable-like declarations, i.e. [fields][FieldDeclaration],
     * [parameters][Parameter] and [local variables][VariableDeclarationExpr].
     */
    fun getAssignedTypesOf(resolvedDecl: ResolvedValueDeclaration): Set<ResolvedType> {
        // Values which are not variable-like cannot be reassigned
        val varLikeDecl = resolvedDecl.toAst()
            .map { mapNodeInLoadedSet(it) }
            .map { decl ->
                if (decl is NodeWithVariables<*>) {
                    decl.variables.single { resolvedDecl.name == it.nameAsString }
                } else decl
            }
            .mapNotNull { VariableLikeDeclaration.fromNodeOrNull(it) }
            .getOrNull()
            ?: return emptySet()

        val baseType = when (varLikeDecl) {
            is VariableLikeDeclaration.ClassField,
            is VariableLikeDeclaration.LocalVariable -> {
                val astDecl = varLikeDecl.node as VariableDeclarator

                astDecl.initializer
                    .mapNotNull { initExpr ->
                        initExpr.runCatching {
                            symbolSolver.calculateType(this)
                        }.recoverCatching { tr ->
                            // If the initializer is an ArrayInitializerExpr, take the declared type of the variable
                            if (initExpr is ArrayInitializerExpr) {
                                symbolSolver.toResolvedType(astDecl.type)
                            } else {
                                throw tr
                            }
                        }.onFailure { tr ->
                            initExpr.emitErrorMessage(tr)
                        }.getOrNull()
                    }
                    .getOrNull()
            }

            is VariableLikeDeclaration.ForEachVariable -> {
                val varDecl = varLikeDecl.node
                val forEachStmt = varLikeDecl.forEachStmt

                val varDeclType = symbolSolver.toResolvedType<ResolvedType>(varDecl.type)
                val iterableType = symbolSolver.calculateType(forEachStmt.iterable)
                val elemType = when {
                    iterableType.isArray -> {
                        iterableType.asArrayType().componentType
                    }

                    iterableType.isReferenceType -> {
                        val iterableRefType = iterableType.asReferenceType()
                        val iterableAncestor = iterableRefType
                            .takeIf { it.qualifiedName == java.lang.Iterable::class.java.name }
                            ?: iterableRefType.allInterfacesAncestors.singleOrNull { it.qualifiedName == java.lang.Iterable::class.java.name }
                            ?: error("Cannot find java.lang.Iterable in all interface ancestors for `${iterableType.describe()}` - Found ${iterableType.asReferenceType().allInterfacesAncestors.map { it.describe() }}")

                        symbolSolver.symbolSolverCache.getDeclaredMethods(iterableAncestor)
                            .single {
                                it.name == "iterator" &&
                                        it.noParams == 0 &&
                                        it.returnType().let { it.isReferenceType && it.asReferenceType().qualifiedName == java.util.Iterator::class.java.name }
                            }
                            .returnType()
                            .asReferenceType()
                            .typeParametersValues()
                            .single()
                    }

                    else -> error("Iterable of ForEachStmt ${forEachStmt.iterable} must be Iterable or an array type")
                }

                when {
                    varDeclType.isAssignableBy(elemType) -> elemType
                    elemType.isAssignableBy(varDeclType) -> varDeclType
                    else -> {
                        // Something is probably wrong with isAssignable for these two operands - Just use elemType by
                        // default

                        elemType
                    }
                }
            }

            else -> null
        }

        // Final parameters/local variables cannot be reassigned
        if (varLikeDecl !is VariableLikeDeclaration.ClassField && (varLikeDecl as? NodeWithFinalModifier<*>)?.isFinal == true) {
            return setOfNotNull(baseType)
        }

        varAssignmentMapping

        val assignExprTypes = varLikeDecl.reassignmentsData
            .mapNotNull {
                runCatching {
                    symbolSolver.calculateType(it.value)
                }.onFailure { tr ->
                    it.emitErrorMessage(tr)
                }.getOrNull()
            }

        return (assignExprTypes + baseType).filterNotNull().toSortedSet(RESOLVED_TYPE_COMPARATOR)
    }

    private fun getOverriddenMethodsImpl(
        method: ResolvedMethodDeclaration,
        stopAtAbstractBoundary: Boolean,
        baseTypeDecl: ResolvedReferenceTypeDeclaration,
        methodTypeCtx: ResolvedReferenceTypeDeclaration
    ): SortedSet<ResolvedMethodDeclaration> {
        val enumConstScopeOrNull = method.toAst()
            .flatMap { it.parentNode }
            .mapNotNull { it as? EnumConstantDeclaration }
            .getOrNull()
        val methodInType = when {
            baseTypeDecl.isAnonymousClass -> {
                // If the baseTypeDecl is an anonymous class, we need to manually solve the extended type, since this
                // may be necessary for us to resolve type parameters.
                // For example, for an object which extends `Comparator<Integer>`, only using the type declaration for
                // resolution is insufficient as the method signature will turn from `compare(T,T)` to i
                // `compare(Integer,Integer)`.
                val resolvedBaseType = method.toAst()
                    .flatMap { it.parentNode }
                    .flatMap { optionalOf(it as? ObjectCreationExpr) }
                    .map { it.type }
                    .map { toResolvedType<ResolvedReferenceType>(it) }
                    .get()

                getOverriddenMethodInType(method, resolvedBaseType, this, cache = symbolSolverCache)
            }
            enumConstScopeOrNull != null || method.declaringType().qualifiedName != baseTypeDecl.qualifiedName -> {
                getOverriddenMethodInType(method, baseTypeDecl, this, methodTypeCtx, cache = symbolSolverCache)
            }
            else -> null
        }

        val initialSet = buildCollection(TreeSet<ResolvedMethodDeclaration>(ResolvedCallableDeclComparator(this))) {
            methodInType?.also { add(it) }
        }
        return if (stopAtAbstractBoundary && methodInType?.isAbstract == true) {
            initialSet
        } else {
            symbolSolverCache.getAncestors(baseTypeDecl)
                .fold(initialSet) { acc, it ->
                    acc.apply {
                        addAll(getOverriddenMethodsImpl(method, stopAtAbstractBoundary, it.toResolvedTypeDeclaration(), methodTypeCtx))
                    }
                }
        }
    }

    /**
     * Returns the set of [ResolvedMethodDeclaration] which this [method] overrides.
     *
     * @param stopAtAbstractBoundary If `true`, do not return methods which are overridden by another abstract method.
     * @param methodTypeCtx The context of the type to consider. Used when finding overridden methods for a class
     * inheriting the declaring type of [method].
     */
    fun getOverriddenMethods(
        method: ResolvedMethodDeclaration,
        stopAtAbstractBoundary: Boolean,
        methodTypeCtx: ResolvedReferenceTypeDeclaration = method.declaringType()
    ): Set<ResolvedMethodDeclaration> {
        require(isMethodInheritedFromClass(methodTypeCtx, method)) {
            "Class `${methodTypeCtx.qualifiedName}` does not inherit `${method.qualifiedSignature}`"
        }

        return if (method.isStatic) {
            emptySet()
        } else {
            getOverriddenMethodsImpl(method, stopAtAbstractBoundary, methodTypeCtx, methodTypeCtx)
        }
    }

    /**
     * Returns the set of all [ResolvedMethodDeclaration] which this [method] is overridden by.
     */
    fun getOverridingMethods(
        method: ResolvedMethodDeclaration,
        stopAtFirstBoundary: Boolean = false
    ): Set<ResolvedMethodDeclaration> {
        return if (method.isStatic) {
            emptySet()
        } else {
            allAncestorsMapping[createResolvedRefType(method.declaringType())].orEmpty()
                .mapNotNull { getOverridingMethodInType(method, it, this, cache = symbolSolverCache) }
                .filter {
                    if (stopAtFirstBoundary) {
                        getOverriddenMethods(it, true).let {
                            it.size == 1 && it.single().qualifiedSignature == method.qualifiedSignature
                        }
                    } else true
                }
                .toSortedSet { o1, o2 -> o1.qualifiedSignature.compareTo(o2.qualifiedSignature) }
        }
    }

    /**
     * Whether `this` and [other] shares the same class declaration location.
     */
    fun ResolvedReferenceTypeDeclaration.isSameClassAs(
        other: ResolvedReferenceTypeDeclaration
    ): Boolean {
        if (isAnonymousClass != other.isAnonymousClass) {
            return false
        }

        return if (isAnonymousClass) {
            val classDeclAst = toTypedAstOrNull<ObjectCreationExpr>(this@ReducerContext)
            val methodDeclTypeAst = other.toTypedAstOrNull<ObjectCreationExpr>(this@ReducerContext)

            classDeclAst != null && methodDeclTypeAst != null && classDeclAst == methodDeclTypeAst
        } else {
            qualifiedName == other.qualifiedName
        }
    }

    /**
     * Whether the [class][classDecl] inherits the implementation of the [method][methodDecl].
     */
    fun isMethodInheritedFromClass(
        classDecl: ResolvedReferenceTypeDeclaration,
        methodDecl: ResolvedMethodDeclaration
    ): Boolean {
        val methodDeclType = methodDecl.declaringType()
        val methodDeclQName by lazy(LazyThreadSafetyMode.NONE) { methodDeclType.qualifiedName }

        /**
         * Whether [type] is the same type or an ancestor type of the
         * [declaring type][ResolvedMethodDeclaration.declaringType] of [methodDecl].
         */
        fun isAncestorOrSelf(type: ResolvedReferenceType): Boolean {
            val typeDecl = type.toResolvedTypeDeclaration()
            if (typeDecl.isSameClassAs(methodDeclType)) {
                return true
            }

            return methodDeclQName in symbolSolverCache.getAllAncestors(typeDecl).map { it.qualifiedName }
        }

        if (classDecl.isAnonymousClass || methodDeclType.isAnonymousClass) {
            if (classDecl.isSameClassAs(methodDeclType)) {
                return true
            }
        }

        return if (methodDeclQName == classDecl.qualifiedName) {
            true
        } else if (getOverridingMethodsInType(methodDecl, classDecl, this, cache = symbolSolverCache).isNotEmpty()) {
            false
        } else {
            val ancestors = symbolSolverCache.getAncestors(classDecl).let { classAncestors ->
                if (classDecl.isInterface) {
                    classAncestors.filter(::isAncestorOrSelf)
                } else {
                    classAncestors.singleOrNull { it.toResolvedTypeDeclaration().isClass }
                        ?.takeIf(::isAncestorOrSelf)
                        ?.let { listOf(it) }
                        ?: classAncestors.filter(::isAncestorOrSelf)
                }
            }

            ancestors.any { isMethodInheritedFromClass(it.toResolvedTypeDeclaration(), methodDecl) }
        }
    }

    /**
     * Whether the [enum constant][enumConstDecl] inherits the implementation of the [method][methodDecl].
     */
    fun isMethodInheritedFromClass(
        enumConstDecl: ResolvedEnumConstantDeclaration,
        methodDecl: ResolvedMethodDeclaration
    ): Boolean {
        return getOverridingMethodsInEnumConst(
            methodDecl,
            enumConstDecl,
            this,
            cache = symbolSolverCache
        ).isEmpty()
    }

    /**
     * Returns the [Set] of all [ResolvedMethodDeclaration] within a library which [methodDecl] overrides.
     */
    fun getLibraryMethodOverrides(methodDecl: ResolvedMethodDeclaration): Set<ResolvedMethodDeclaration> {
        return getOverriddenMethods(methodDecl, false)
            .filterNot { typeSolver.isSolvedBySourceSolvers(it.declaringType()) }
            .toSortedSet(ResolvedCallableDeclComparator(this))
    }

    /**
     * Whether [methodDecl] overrides any [ResolvedMethodDeclaration] present in a library.
     */
    fun isMethodOverridesLibraryMethod(methodDecl: ResolvedMethodDeclaration): Boolean =
        getLibraryMethodOverrides(methodDecl).isNotEmpty()

    /**
     * Returns the set of all [ResolvedReferenceTypeDeclaration] (including the declaring type of [methodDecl]) whose
     * method with the same signature as [methodDecl] is dependent on the implementation provided by this class.
     */
    fun getMethodImplDependentClasses(methodDecl: ResolvedMethodDeclaration): Set<ResolvedReferenceTypeDeclaration> =
        getSubtypesOfType(createResolvedRefType(methodDecl.declaringType()))
            .filter { isMethodInheritedFromClass(it.asReferenceType().toResolvedTypeDeclaration(), methodDecl) }
            .map { it.asReferenceType().toResolvedTypeDeclaration() }
            .toSortedSet(RESOLVED_TYPE_DECL_COMPARATOR)
            .also { it.add(methodDecl.declaringType()) }

    /**
     * If not a basic type declarable in the source code, resolves it into one (or more, in cases of union types).
     *
     * The concrete type(s) is always returned. To get all possible (i.e. assignable) types, use [getSubtypesOfType] on
     * the returned [Set].
     *
     * If a type cannot be resolved due to no matching types (e.g. [ResolvedIntersectionType] cannot be resolved because
     * no type matches the given intersection), the returned [Set] will be empty.
     */
    fun normalizeType(type: ResolvedType): Set<ResolvedType> {
        return when (type) {
            is ResolvedArrayType, is ResolvedPrimitiveType -> {
                // All are declarable in source code
                listOf(type)
            }

            is InferenceVariableType -> (type.equivalentTypes + type.superTypes).flatMap { normalizeType(it) }
            is LazyType -> normalizeType(type.concrete)
            is NullType -> {
                // `null` is only assignable to a reference type
                listOf(ResolvedObjectType(typeSolver))
            }

            is ResolvedIntersectionType -> {
                // Intersection type are types which extends/implements multiple classes/interfaces
                // Resolved types are all classes which have the listed classes as supertypes
                classes
                    .filter { typeDecl ->
                        val exts = (typeDecl as? NodeWithExtends<*>)
                            ?.extendedTypes
                            .orEmpty()
                        val impls = (typeDecl as? NodeWithImplements<*>)
                            ?.implementedTypes
                            .orEmpty()
                        val supers = (exts + impls)
                            .map { it.resolve() as ResolvedReferenceType }
                            .flatMap { symbolSolverCache.getAllAncestors(it) }
                            .toSortedSet(RESOLVED_TYPE_COMPARATOR)

                        type.elements.all { it in supers }
                    }
                    .map { createResolvedRefType(it.resolve()) }
            }

            is ResolvedLambdaConstraintType -> normalizeType(type.bound)
            is ResolvedReferenceType -> {
                listOf(type.transformTypeParameters {
                    normalizeType(it).singleOrNull() ?: run {
                        LOGGER.warn("Don't know how to normalize ${it.describe()} into a single type")
                        it
                    }
                })
            }

            is ResolvedTypeVariable -> {
                val tp = type.asTypeParameter()
                when {
                    // JavaParser checks the presence of <? super E> in hasUpperBound
                    tp.hasUpperBound() -> ResolvedObjectType(typeSolver)
                    // JavaParser checks the presence of <? extends E> in hasLowerBound
                    tp.hasLowerBound() -> tp.lowerBound
                    else -> ResolvedObjectType(typeSolver)
                }.let { listOf(it) }
            }

            is ResolvedUnionType -> type.elements.flatMap { normalizeType(it) }
            is ResolvedVoidType -> listOf(type)
            is ResolvedWildcard -> {
                when {
                    type.isSuper -> ResolvedObjectType(typeSolver)
                    type.isExtends -> type.boundedType
                    else -> ResolvedObjectType(typeSolver)
                }.let { listOf(it) }
            }

            else -> error("Don't know how to normalize a ResolvedType of type ${type::class.qualifiedName}")
        }.mapTo(TreeSet(RESOLVED_TYPE_COMPARATOR)) { it }
    }

    /**
     * Flattens [type] such that it can be expressed by a single, possibly nested, source-representable type.
     *
     * Type variables will be converted into bounded generics where possible.
     *
     * The returned type could be non-compilable after substitution, for instance when the type is broadened.
     */
    fun flattenType(type: ResolvedType): ResolvedType {
        return when (type) {
            is InferenceVariableType -> flattenType(type.equivalentType())
            is ResolvedArrayType, is ResolvedPrimitiveType, is ResolvedReferenceType -> type
            is LazyType -> flattenType(type.concrete)
            is NullType -> {
                // `null` is only assignable to a reference type
                ResolvedObjectType(typeSolver)
            }

            is ResolvedIntersectionType -> {
                TypeHelper.leastUpperBound(type.elements.toSet())
            }

            is ResolvedLambdaConstraintType -> flattenType(type.bound)
            is ResolvedTypeVariable -> {
                val tp = type.asTypeParameter()
                when {
                    // JavaParser checks the presence of <? super E> in hasUpperBound
                    tp.hasUpperBound() -> ResolvedObjectType(typeSolver)
                    // JavaParser checks the presence of <? extends E> in hasLowerBound
                    tp.hasLowerBound() -> tp.lowerBound
                    else -> ResolvedObjectType(typeSolver)
                }
            }

            is ResolvedUnionType -> {
                val refTypes = type.elements.map { it.asReferenceType() }
                val allClassAncestors = refTypes
                    .flatMap { it.allClassesAncestors }
                    .filterNot { it.isJavaLangObject }
                    .toSortedSet(RESOLVED_REF_TYPE_COMPARATOR)
                val allIfaceAncestors = refTypes
                    .flatMap { it.allInterfacesAncestors }
                    .filterNot { it.isJavaLangObject }
                    .toSortedSet(RESOLVED_REF_TYPE_COMPARATOR)

                if (allClassAncestors.isNotEmpty()) {
                    checkNotNull(allClassAncestors.maxByOrNull { it.classLevel })
                } else if (allIfaceAncestors.isNotEmpty()) {
                    val ancestorsByLevel = allIfaceAncestors.groupBy { it.classLevel }
                    val shallowestLevel = ancestorsByLevel.keys.minOrNull()!!
                    val shallowestAncestors = checkNotNull(ancestorsByLevel[shallowestLevel])

                    if (shallowestAncestors.size == 1) {
                        shallowestAncestors.single()
                    } else {
                        LOGGER.warn("Multiple types share the same level - Returning Object")
                        ResolvedObjectType(typeSolver)
                    }
                } else {
                    ResolvedObjectType(typeSolver)
                }
            }

            is ResolvedVoidType -> type
            is ResolvedWildcard -> {
                when {
                    // JavaParser checks the presence of <? super E> in hasUpperBound
                    type.isSuper -> ResolvedObjectType(typeSolver)
                    // JavaParser checks the presence of <? extends E> in hasLowerBound
                    type.isExtends -> type.boundedType
                    else -> ResolvedObjectType(typeSolver)
                }
            }

            else -> error("Don't know how to flatten a ResolvedType of type ${type::class.qualifiedName}")
        }
    }

    /**
     * Retrieves all supertypes of [type].
     */
    fun getSupertypesOfType(type: ResolvedType): Set<ResolvedType> {
        return when (type) {
            is LazyType -> getSupertypesOfType(type.concrete)
            is NullType -> emptySet()
            is ResolvedArrayType -> {

                val baseComponentType = type.baseComponentType()
                val componentAssociatedSupertypes = getSupertypesOfType(baseComponentType)
                val arrayLevel = type.arrayLevel()

                val componentTypeArrays = componentAssociatedSupertypes.map { ResolvedArrayType(it, arrayLevel) }
                val objectTypeArrays = List(arrayLevel) {
                    ResolvedArrayType(ResolvedObjectType(typeSolver), arrayLevel + 1)
                }

                (componentTypeArrays + objectTypeArrays).toSet()
            }

            is ResolvedIntersectionType -> type.elements
                .flatMap { symbolSolverCache.getAllAncestors(it.asReferenceType()) }
                .toSortedSet(RESOLVED_REF_TYPE_TP_COMPARATOR)

            is ResolvedLambdaConstraintType -> getSupertypesOfType(type.bound)
            is ResolvedPrimitiveType -> emptySet()
            is ResolvedReferenceType -> symbolSolverCache.getAllAncestors(type)
                .toSortedSet(RESOLVED_REF_TYPE_TP_COMPARATOR)

            is ResolvedTypeVariable -> getSupertypesOfType(flattenType(type))
            is ResolvedUnionType -> type.elements
                .flatMap { symbolSolverCache.getAllAncestors(it.asReferenceType()) }
                .toSortedSet(RESOLVED_REF_TYPE_TP_COMPARATOR)

            is ResolvedVoidType -> emptySet()
            else -> error("Don't know how to get supertypes for a ResolvedType of type ${type::class.qualifiedName}")
        }
    }

    /**
     * Retrieves the subtypes of [type], including anonymous classes.
     *
     * For efficiency purposes, subtypes will only be discovered at a best-effort basis.
     *
     * @param onlyDirect If `true`, only include direct subtypes.
     */
    fun getSubtypesOfType(type: ResolvedType, onlyDirect: Boolean = false): SortedSet<ResolvedType> {
        return when (type) {
            is InferenceVariableType -> {
                if (flattenType(type) == type) {
                    normalizeType(type)
                } else {
                    getSubtypesOfType(flattenType(type), onlyDirect = onlyDirect)
                }
            }

            is LazyType -> getSubtypesOfType(type.concrete, onlyDirect = onlyDirect)
            is NullType -> emptySet()
            is ResolvedArrayType -> {
                getSubtypesOfType(type.baseComponentType(), onlyDirect = onlyDirect)
                    .map { ResolvedArrayType(it, type.arrayLevel()) }
            }

            is ResolvedIntersectionType -> normalizeType(type)
            is ResolvedLambdaConstraintType -> getSubtypesOfType(type.bound, onlyDirect = onlyDirect)
            is ResolvedPrimitiveType -> emptySet()
            is ResolvedReferenceType -> {
                val namedAncestors = (if (onlyDirect) directAncestorsMapping else allAncestorsMapping)[type]
                    .orEmpty()
                    .map { createResolvedRefType(it) }
                val anonAncestors = (if (onlyDirect) anonDirectAncestorsMapping else anonAllAncestorsMapping)[type]
                    .orEmpty()
                    .map { createResolvedRefType(it) }

                namedAncestors + anonAncestors
            }

            is ResolvedTypeVariable -> getSubtypesOfType(flattenType(type), onlyDirect = onlyDirect)
            is ResolvedUnionType -> type.elements.flatMap { getSubtypesOfType(it, onlyDirect = onlyDirect) }
            is ResolvedVoidType -> emptySet()
            is ResolvedWildcard -> {
                when {
                    type.isSuper -> listOf(ResolvedObjectType(typeSolver))
                    type.isExtends -> getSubtypesOfType(type.boundedType, onlyDirect = onlyDirect)
                    else -> listOf(ResolvedObjectType(typeSolver))
                }
            }

            else -> error("Don't know how to get subtypes for a ResolvedType of type ${type::class.qualifiedName}")
        }.mapTo(TreeSet(RESOLVED_TYPE_COMPARATOR)) { it }
    }

    private val errorEmitLock = ReentrantLock()
    private val emittedErrorsForNodes = ConcurrentSkipListSet(NodeAstComparator()) as SortedSet<Node>
    private fun Node.emitErrorMessage(tr: Throwable) {
        val cu = findCompilationUnit()
            .flatMap { it.storage }
            .map { it.path.toString() }
            .getOrNull()
            ?: "(source)"

        if (this is FieldAccessExpr && this.toString() in classPackagePrefixes) {
            LOGGER.debug(
                "Assuming symbol `{}` ({}) in {} ({}) is a package name",
                this,
                this::class.simpleName,
                fullRangeString,
                cu
            )
            return
        }

        val directAncestor = ancestorsAsSequence()
            .lastOrNull { it is FieldAccessExpr || it is MethodCallExpr }
        if (directAncestor?.runCatching { symbolSolver.resolveDeclaration<ResolvedDeclaration>(this) }?.isSuccess == true) {
            LOGGER.debug(
                "Assuming symbol `{}` ({}) in {} ({}) is a package name",
                this,
                this::class.simpleName,
                fullRangeString,
                cu
            )
            return
        }

        errorEmitLock.withLock {
            LOGGER.error("Unable to resolve symbol `$this` (${this::class.simpleName}) in $fullRangeString ($cu)", tr)
            if (emittedErrorsForNodes.add(this)) {
                LOGGER.error("AST:")
                this.astToString(indent = 2, showChildren = false)
                    .split('\n')
                    .forEach {
                        LOGGER.error(it)
                    }
            }
        }
    }

    fun <T : ResolvedDeclaration> resolveDeclarationImpl(node: Node, clazz: Class<T>): Result<T> =
        node.runCatching { symbolSolver.resolveDeclaration(this, clazz) }
            .onFailure { node.emitErrorMessage(it) }

    inline fun <reified T : ResolvedDeclaration> resolveDeclarationOrNull(node: Node): T? =
        resolveDeclarationImpl(node, T::class.java).getOrNull()

    inline fun <reified T : ResolvedDeclaration> resolveDeclaration(node: Node): T =
        resolveDeclarationImpl(node, T::class.java).getOrThrow()

    fun <T : ResolvedType> toResolvedTypeImpl(javaparserType: Type, clazz: Class<T>): Result<T> =
        javaparserType.runCatching { symbolSolver.toResolvedType(this, clazz) }
            .onFailure { javaparserType.emitErrorMessage(it) }

    inline fun <reified T : ResolvedType> toResolvedTypeOrNull(javaparserType: Type): T? =
        toResolvedTypeImpl(javaparserType, T::class.java).getOrNull()

    inline fun <reified T : ResolvedType> toResolvedType(javaparserType: Type): T =
        toResolvedTypeImpl(javaparserType, T::class.java).getOrThrow()

    fun calculateTypeImpl(expression: Expression): Result<ResolvedType> =
        expression.runCatching { symbolSolver.calculateType(this) }
            .onFailure { expression.emitErrorMessage(it) }

    fun calculateTypeOrNull(expression: Expression): ResolvedType? =
        calculateTypeImpl(expression).getOrNull()

    fun calculateType(expression: Expression): ResolvedType =
        calculateTypeImpl(expression).getOrThrow()

    /**
     * Resolves the declaration of all [Type] nodes under this [Node].
     */
    fun resolveRefTypeNodeDeclsAsSequence(
        node: Node,
        filter: (Node) -> Boolean = { true }
    ): Sequence<Pair<Node, ResolvedReferenceTypeDeclaration?>> {
        return node.asSequence()
            .filter { childNode ->
                when (childNode) {
                    is ClassOrInterfaceType -> {
                        if (!filter(childNode)) {
                            return@filter false
                        }

                        childNode.parentNode
                            .map {
                                // Do not include types which are actually parts of a qualified name
                                it !is ClassOrInterfaceType || childNode in it.typeArguments.getOrDefault(emptyList())
                            }
                            .getOrDefault(false)
                    }

                    is Expression -> {
                        if (!filter(childNode)) {
                            return@filter false
                        }

                        when (childNode) {
                            is AnnotationExpr -> true
                            is FieldAccessExpr -> childNode.parentNode.map { it is FieldAccessExpr }.get()
                            is NameExpr -> true
                            else -> false
                        }
                    }

                    else -> false
                }
            }
            .mapNotNull { childNode ->
                when (childNode) {
                    is ClassOrInterfaceType -> {
                        val resolvedType = toResolvedTypeOrNull<ResolvedType>(childNode)

                        if (resolvedType == null || resolvedType.isReferenceType) {
                            childNode to resolvedType?.asReferenceType()?.toResolvedTypeDeclaration()
                        } else null
                    }

                    is Expression -> {
                        val resolvedDecl = resolveDeclarationOrNull<ResolvedDeclaration>(childNode)

                        if (resolvedDecl is ResolvedReferenceTypeDeclaration?) {
                            childNode to resolvedDecl
                        } else null
                    }

                    else -> unreachable()
                }
            }
            .distinctBy { it.first.astBasedId }
    }

    /**
     * Resolves all declarations of all expressions which invokes a method or constructor call under this [node].
     */
    fun resolveCallExprDeclsAsSequence(
        node: Node,
        filter: (Expression) -> Boolean = { true }
    ): Sequence<Pair<Expression, ResolvedMethodLikeDeclaration?>> {
        return node.asSequence()
            .filterIsInstance<Expression>()
            .filter {
                if (!filter(it)) {
                    return@filter false
                }

                it is MethodCallExpr || it is ObjectCreationExpr
            }
            .mapNotNull {
                val resolvedDecl = resolveDeclarationOrNull<ResolvedDeclaration>(it)

                if (resolvedDecl is ResolvedMethodLikeDeclaration?) {
                    it to resolvedDecl
                } else null
            }
    }

    fun resolveExplicitCtorStmtsAsSequence(
        node: Node
    ): Sequence<Pair<ExplicitConstructorInvocationStmt, ResolvedConstructorDeclaration?>> {
        return node.asSequence()
            .filterIsInstance<ExplicitConstructorInvocationStmt>()
            .mapNotNull {
                val resolvedDecl = resolveDeclarationOrNull<ResolvedDeclaration>(it)

                if (resolvedDecl is ResolvedConstructorDeclaration?) {
                    it to resolvedDecl
                } else null
            }
    }

    private val loadedCUByPath by lazy {
        loadedCompilationUnits.associateBy { it.storage.map { it.path }.get() }
    }

    /**
     * Finds the [node] in [loadedCompilationUnits].
     */
    fun <N : Node> mapNodeInLoadedSet(node: N): N {
        val nodeCU = node.findCompilationUnit().get()
        val nodeStoragePath = nodeCU.storage.map { it.path }.get()

        val mappedCU = loadedCUByPath[nodeStoragePath]

        return if (mappedCU === nodeCU) {
            node
        } else {
            ASTPathGenerator.forNode(node)(checkNotNull(mappedCU))
        }
    }

    fun getReachableReasonFromNode(node: Node, reachableDecl: BodyDeclaration<*>): ReachableReason.DirectlyReferenced {
        return when (node) {
            is Expression -> {
                if (reachableDecl is TypeDeclaration<*>) {
                    ReachableReason.ReferencedByExprType(node)
                } else {
                    ReachableReason.ReferencedBySymbolName(node)
                }
            }
            is Type -> {
                if (node is NodeWithSimpleName<*>) {
                    ReachableReason.ReferencedByTypeName(node)
                } else {
                    TODO("Unhandled ReachableReason: `$node` (${node::class.simpleName})")
                }
            }
            else -> unreachable()
        }
    }

    fun getReachableReasonFromNode(node: Node, reachableDecl: ResolvedDeclaration): ReachableReason {
        return when (node) {
            is Expression -> {
                if (!node.isObjectCreationExpr && reachableDecl.isType) {
                    ReachableReason.ReferencedByExprType(node)
                } else {
                    ReachableReason.ReferencedBySymbolName(node)
                }
            }
            is Type -> {
                val topLvlClassType = node.ancestorsAsSequence().filterIsInstance<ClassOrInterfaceType>().lastOrNull()
                if (topLvlClassType != null && topLvlClassType.asSequence(Node.TreeTraversal.BREADTHFIRST).any { it === node }) {
                    return ReachableReason.TransitiveNestedTypeName(topLvlClassType)
                }

                if (node is NodeWithSimpleName<*>) {
                    ReachableReason.ReferencedByTypeName(node)
                } else {
                    TODO("Unhandled ReachableReason: `$node` (${node::class.simpleName})")
                }
            }
            else -> unreachable()
        }
    }

    companion object {

        private val LOGGER = Logger<ReducerContext>()

        /**
         * Extracts the left-most type of a [ClassOrInterfaceType].
         *
         * The left-most type may not be a "type" per-se, but it is the top-level package or type.
         */
        tailrec fun extractLeftMostType(type: ClassOrInterfaceType): ClassOrInterfaceType =
            if (!type.scope.isPresent) type else extractLeftMostType(type.scope.get())
    }
}