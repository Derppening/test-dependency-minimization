package com.derppening.researchprojecttoolkit.tool.facade

import com.derppening.researchprojecttoolkit.tool.ReducerContext.Companion.extractLeftMostType
import com.derppening.researchprojecttoolkit.tool.facade.typesolver.PartitionedTypeSolver
import com.derppening.researchprojecttoolkit.tool.reducer.AbstractReducer
import com.derppening.researchprojecttoolkit.util.*
import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.AccessSpecifier
import com.github.javaparser.ast.DataKey
import com.github.javaparser.ast.Node
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.body.*
import com.github.javaparser.ast.expr.*
import com.github.javaparser.ast.nodeTypes.*
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt
import com.github.javaparser.ast.stmt.SwitchEntry
import com.github.javaparser.ast.type.ClassOrInterfaceType
import com.github.javaparser.ast.type.Type
import com.github.javaparser.ast.type.UnionType
import com.github.javaparser.resolution.MethodAmbiguityException
import com.github.javaparser.resolution.UnsolvedSymbolException
import com.github.javaparser.resolution.declarations.*
import com.github.javaparser.resolution.logic.ConstructorResolutionLogic
import com.github.javaparser.resolution.logic.MethodResolutionLogic
import com.github.javaparser.resolution.model.LambdaArgumentTypePlaceholder
import com.github.javaparser.resolution.model.SymbolReference
import com.github.javaparser.resolution.model.typesystem.ReferenceTypeImpl
import com.github.javaparser.resolution.types.*
import com.github.javaparser.symbolsolver.JavaSymbolSolver
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.*
import hk.ust.cse.castle.toolkit.jvm.util.RuntimeAssertions.unreachable
import java.util.*
import kotlin.jvm.optionals.getOrDefault
import kotlin.jvm.optionals.getOrNull

/**
 * A [JavaSymbolSolver] with fallback resolution algorithms to workaround bugs or unsupported features in the default
 * implementation.
 */
class FuzzySymbolSolver(
    val typeSolver: PartitionedTypeSolver,
    val symbolSolverCache: SymbolSolverCache = SymbolSolverCache(),
    private val enabledOptimizations: Set<AbstractReducer.Optimization> = AbstractReducer.Optimization.ALL
) : JavaSymbolSolver(typeSolver) {

    private object ArrayLengthValueDeclaration : ResolvedValueDeclaration {

        override fun getName(): String = "length"
        override fun getType(): ResolvedType = ResolvedPrimitiveType.INT
    }

    private val facade = JavaParserFacade.get(typeSolver)
    private val genericsSolver = GenericsSolver(this, typeSolver)

    /**
     * Whether the type of this node is resolved.
     */
    private val Node.isTypeResolved: Boolean
        get() = containsData(RESOLVED_TYPE)

    /**
     * The resolved type of this node.
     */
    private var Node.resolvedType: ResolvedType
        set(v) = setData(RESOLVED_TYPE, v)
        get() = getData(RESOLVED_TYPE)

    /**
     * Whether the declaration of this node is resolved.
     */
    private val Node.isDeclResolved: Boolean
        get() = containsData(RESOLVED_DECL)

    /**
     * The resolved declaration of this node.
     */
    private var Node.resolvedDecl: ResolvedDeclaration
        set(v) = setData(RESOLVED_DECL, v)
        get() = getData(RESOLVED_DECL)

    /**
     * Injects this [FuzzySymbolSolver] into [n] if a solver is not already registered.
     */
    private fun injectSolverIfNotPresent(n: Node) {
        n.findCompilationUnit().ifPresent {
            if (!it.containsData(Node.SYMBOL_RESOLVER_KEY)) {
                inject(it)
            }
        }
    }

    private fun solveTpInClassContext(
        retType: ResolvedType,
        scopeType: ResolvedReferenceType?,
        exprContainer: ResolvedTypeParametrizable
    ): ResolvedType {
        return if (AbstractReducer.Optimization.USE_GENERICS_SOLVER in enabledOptimizations) {
            genericsSolver.solveTpInClassContext(retType, scopeType, exprContainer)
        } else retType
    }

    private fun solveTpInMethodContext(
        methodDecl: ResolvedMethodDeclaration,
        scopeType: ResolvedReferenceType?,
        argTypes: List<ResolvedType>,
        typeArgs: List<ResolvedType>?,
        exprContainer: ResolvedTypeParametrizable
    ): ResolvedType {
        return if (AbstractReducer.Optimization.USE_GENERICS_SOLVER in enabledOptimizations) {
            genericsSolver.solveTpInMethodContext(methodDecl, scopeType, argTypes, typeArgs, exprContainer)
        } else methodDecl.returnType
    }

    private fun ResolvedParameterDeclaration.getRawType(declaringMethod: ResolvedMethodLikeDeclaration): ResolvedType {
        val origType = type

        return if (origType.isTypeVariable) {
            solveTpInClassContext(origType, null, declaringMethod)
        } else {
            origType
        }.let {
            rawifyType(it, typeSolver)
        }
    }

    private fun ResolvedMethodLikeDeclaration.mapRawParamTypes(): List<ResolvedType> = parameters.map {
        it.getRawType(this)
    }

    /**
     * @see MethodResolutionLogic.areOverride
     */
    private fun methodOverrides(
        winningCandidate: ResolvedMethodDeclaration,
        other: ResolvedMethodDeclaration
    ): Boolean {
        if (winningCandidate.name != other.name) {
            return false
        }
        if (winningCandidate.numberOfParams != other.numberOfParams) {
            return false
        }
        return winningCandidate.mapRawParamTypes().zip(other.mapRawParamTypes())
            .all { (lhs, rhs) -> lhs == rhs }
    }

    /**
     * Checks whether [methodA] is more specific than [methodB].
     *
     * @see MethodResolutionLogic.isMoreSpecific
     */
    internal fun isMethodMoreSpecific(
        methodA: ResolvedMethodDeclaration,
        methodB: ResolvedMethodDeclaration
    ): Boolean {
        val paramTypesA = methodA.mapRawParamTypes()
        val paramTypesB = methodB.mapRawParamTypes()
        val aIsVararg = methodA.hasVariadicParameter()
        val bIsVararg = methodB.hasVariadicParameter()

        if (!aIsVararg && !bIsVararg) {
            check(paramTypesA.size == paramTypesB.size)
        }

        val minNumParams = minOf(paramTypesA.size, paramTypesB.size)

        var oneMoreSpecificFound = false
        for (i in 0 until minNumParams) {
            val tdA = paramTypesA[i]
            val tdB = paramTypesB[i]

            val aIsAssignableByB = tdA.isAssignableBy(tdB)
            val bIsAssignableByA = tdB.isAssignableBy(tdA)

            if (bIsAssignableByA && !aIsAssignableByB) {
                oneMoreSpecificFound = true
            }
            if (aIsAssignableByB && !bIsAssignableByA) {
                return false
            }

            if (i == minNumParams - 1) {
                if (aIsVararg && bIsVararg) {
                    val tdArrA = tdA.takeIf { it.isArray }?.asArrayType()
                    val tdArrB = tdB.takeIf { it.isArray }?.asArrayType()

                    if (tdArrA != null && tdArrB != null) {
                        val aaIsAssignableByBB = tdArrA.componentType.isAssignableBy(tdArrB.componentType)
                        val bbIsAssignableByAA = tdArrB.componentType.isAssignableBy(tdArrA.componentType)

                        if (bbIsAssignableByAA && !aaIsAssignableByBB) {
                            oneMoreSpecificFound = true
                        }
                        if (aaIsAssignableByBB && !bbIsAssignableByAA) {
                            return false
                        }
                    } else if (tdArrA != null || tdArrB != null) {
                        if (tdArrB?.componentType?.isAssignableBy(tdA) == true) {
                            oneMoreSpecificFound = true
                        }
                    } else {
                        LOGGER.error("isMethodMoreSpecific - Declarations are not vararg and at least one method does not end with an array type")
                        LOGGER.error("  Candidate A: ${methodA.qualifiedSignature} (${paramTypesA.joinToString(", ") { it.describe() }})")
                        LOGGER.error("  Candidate B: ${methodB.qualifiedSignature} (${paramTypesB.joinToString(", ") { it.describe() }})")
                    }
                } else if (!aIsVararg && bIsVararg) {
                    oneMoreSpecificFound = true
                }
            }
        }
        return oneMoreSpecificFound
    }

    /**
     * @see MethodResolutionLogic.findMostApplicableUsage
     */
    private fun findMostApplicableMethod(
        candidates: List<ResolvedMethodDeclaration>
    ): ResolvedMethodDeclaration? {
        if (candidates.isEmpty()) {
            return null
        }
        if (candidates.size == 1) {
            return candidates.single()
        }

        var winningCandidate = candidates[0]

        for (i in 1 until candidates.size) {
            val other = candidates[i]

            if (isMethodMoreSpecific(winningCandidate, other)) {
                // no-op
            } else if (isMethodMoreSpecific(other, winningCandidate)) {
                winningCandidate = other
            } else {
                if (winningCandidate.declaringType().qualifiedName == other.declaringType().qualifiedName) {
                    if (!methodOverrides(winningCandidate, other)) {
                        throw MethodAmbiguityException("Ambiguous method call: cannot find a most applicable method: ${winningCandidate.qualifiedSignature}, ${other.qualifiedSignature}. First declared in ${winningCandidate.declaringType().qualifiedName}")
                    }
                } else {
                    // we expect the methods to be ordered such that inherited methods are later in the list
                }
            }
        }

        return winningCandidate
    }

    /**
     * @see ResolvedReferenceTypeDeclaration.getAncestors
     */
    private fun getAncestorsFallback(resolvedRefTypeDecl: ResolvedReferenceTypeDeclaration): List<ResolvedReferenceType> {
        val classOrIfaceDecl = resolvedRefTypeDecl.toTypedAstOrNull<ClassOrInterfaceDeclaration>(null)
        return if (classOrIfaceDecl?.isLocalClassDeclaration == true) {
            classOrIfaceDecl.let { it.extendedTypes + it.implementedTypes }
                .map { toResolvedType<ResolvedReferenceType>(it) }
        } else {
            symbolSolverCache.getAncestors(resolvedRefTypeDecl)
        }
    }

    /**
     * @see ResolvedReferenceType.getDirectAncestors
     */
    private fun getDirectAncestors(resolvedRefType: ResolvedReferenceType): List<ResolvedReferenceType> {
        // We need to go through the inheritance line and propagate the type parameters
        val ancestors = getAncestorsFallback(resolvedRefType.toResolvedTypeDeclaration())
            .map { resolvedRefType.typeParametersMap().replaceAll(it).asReferenceType() }
            .toMutableList()

        // Conditionally re-insert java.lang.Object as an ancestor.
        val thisTypeDeclaration = resolvedRefType.toResolvedTypeDeclarationOrNull()
        if (thisTypeDeclaration != null) {
            // The superclass of interfaces is always null
            if (thisTypeDeclaration.isClass) {
                val optionalSuperClass = thisTypeDeclaration.asClass().superClass
                val superClassIsJavaLangObject =
                    optionalSuperClass.isPresent && optionalSuperClass.get().isJavaLangObject
                val thisIsJavaLangObject = thisTypeDeclaration.asClass().isJavaLangObject
                if (superClassIsJavaLangObject && !thisIsJavaLangObject) {
                    ancestors.add(optionalSuperClass.get())
                }
            }
        }

        return ancestors
    }

    /**
     * @see ResolvedReferenceType.getAllMethods
     */
    private fun getAllMethodsFallback(resolvedRefType: ResolvedReferenceType): List<ResolvedMethodDeclaration> {
        val refTypeDecl = resolvedRefType.toResolvedTypeDeclarationOrNull()
            ?: error("Type declaration must be present for type `${resolvedRefType.qualifiedName}`")

        // Get the methods declared directly on this.
        val allMethods: MutableList<ResolvedMethodDeclaration> = LinkedList(symbolSolverCache.getDeclaredMethods(refTypeDecl))

        // Also get methods inherited from ancestors.
        getDirectAncestors(resolvedRefType).forEach {
            allMethods.addAll(symbolSolverCache.getAllMethods(it))
        }

        return allMethods
    }

    /**
     * Filters all methods based on its applicability to the method invocation with [argumentTypes].
     *
     * §15.2.2.1 - Identify Potentially Applicable Methods
     */
    private fun <DeclT : ResolvedMethodLikeDeclaration> filterMethodByPotentialApplicability(
        methodLikeDecls: List<DeclT>,
        argumentTypes: List<ResolvedType>,
        scopeType: ResolvedReferenceType,
        node: Node
    ): List<DeclT> {
        check(when (node) {
            is EnumConstantDeclaration,
            is ExplicitConstructorInvocationStmt,
            is MethodCallExpr,
            is ObjectCreationExpr -> true

            else -> false
        })

        val nodeDeclaringType = resolveDeclaration<ResolvedReferenceTypeDeclaration>(node.findAncestor(TypeDeclaration::class.java).get())
        val scopeTypeDecl = scopeType.toResolvedTypeDeclaration()

        return methodLikeDecls
            .filter { methodLikeDecl ->
                // The name of the member is identical to the name of the method in the method invocation.
                when (node) {
                    is EnumConstantDeclaration -> true
                    is ExplicitConstructorInvocationStmt -> true
                    is MethodCallExpr -> node.nameAsString == methodLikeDecl.name
                    is ObjectCreationExpr -> true
                    else -> unreachable()
                }
            }
            .filter { methodLikeDecl ->
                // If the member is a fixed arity method with arity n, the arity of the method invocation is equal to n, and for all i (1 ≤ i ≤ n), the i'th argument of the method invocation is potentially compatible, as defined below, with the type of the i'th parameter of the method.
                // If the member is a variable arity method with arity n, then for all i (1 ≤ i ≤ n-1), the i'th argument of the method invocation is potentially compatible with the type of the i'th parameter of the method; and, where the nth parameter of the method has type T[], one of the following is true:
                //    The arity of the method invocation is equal to n-1.
                //    The arity of the method invocation is equal to n, and the nth argument of the method invocation is potentially compatible with either T or T[].
                //    The arity of the method invocation is m, where m > n, and for all i (n ≤ i ≤ m), the i'th argument of the method invocation is potentially compatible with T.
                if (methodLikeDecl.hasVariadicParameter()) {
                    methodLikeDecl.numberOfParams - 1 <= argumentTypes.size
                } else {
                    methodLikeDecl.numberOfParams == argumentTypes.size
                }
            }
            .filter { methodLikeDecl ->
                // If the method invocation includes explicit type arguments, and the member is a generic method, then the number of type arguments is equal to the number of type parameters of the method.
                (node as? NodeWithTypeArguments<*>)?.typeArguments
                    ?.map { it.size == methodLikeDecl.typeParameters.size }
                    ?.getOrNull()
                    ?: true
            }
            .filter { methodLikeDecl ->
                // The member is accessible (§6.6) to the class or interface in which the method invocation appears.

                val accessSpecifier = methodLikeDecl.accessSpecifier()
                val effectiveAccessSpecifier = when (scopeTypeDecl) {
                    is ResolvedInterfaceDeclaration -> {
                        if (accessSpecifier == AccessSpecifier.NONE) {
                            AccessSpecifier.PUBLIC
                        } else accessSpecifier
                    }
                    else -> accessSpecifier
                }

                when (effectiveAccessSpecifier) {
                    AccessSpecifier.PUBLIC -> true
                    AccessSpecifier.PRIVATE -> {
                        scopeTypeDecl.qualifiedName in arrayOf(
                            nodeDeclaringType.qualifiedName,
                            nodeDeclaringType.getTopLevelType(this).qualifiedName
                        )
                    }
                    AccessSpecifier.PROTECTED -> {
                        nodeDeclaringType.packageName == scopeTypeDecl.packageName ||
                                scopeType in symbolSolverCache.getAllAncestors(nodeDeclaringType)
                    }
                    AccessSpecifier.NONE -> {
                        nodeDeclaringType.packageName == scopeTypeDecl.packageName
                    }
                    else -> unreachable()
                }
            }
    }

    /**
     * Filters the set of [candidate methods][methodLikeDecls] by assignability of [argumentTypes] to the method-like
     * declaration.
     */
    private fun <DeclT : ResolvedMethodLikeDeclaration> filterMethodsByArgTypes(
        methodLikeDecls: List<DeclT>,
        argumentTypes: List<ResolvedType>
    ): List<DeclT> {
        check(methodLikeDecls.all {
            if (it.hasVariadicParameter()) {
                it.numberOfParams - 1 <= argumentTypes.size
            } else {
                it.numberOfParams == argumentTypes.size
            }
        })

        return methodLikeDecls.filter { methodDecl ->
            argumentTypes.mapIndexed { idx, argType ->
                val param = if (idx < methodDecl.numberOfParams) {
                    methodDecl.getParam(idx)
                } else {
                    methodDecl.lastParam
                }
                val paramType = param.type

                when {
                    idx < methodDecl.numberOfParams - 1 -> paramType to argType
                    idx == methodDecl.numberOfParams - 1 -> {
                        if (methodDecl.hasVariadicParameter()) {
                            when (paramType.arrayLevel() - argType.arrayLevel()) {
                                0 -> paramType to argType
                                1 -> paramType.asArrayType().componentType to argType
                                else -> unreachable("Matching vararg param type ${paramType.describe()} from arg type ${argType.describe()}")
                            }
                        } else {
                            paramType to argType
                        }
                    }
                    idx >= methodDecl.numberOfParams -> paramType.asArrayType().componentType to argType
                    else -> unreachable("idx=$idx methodDecl.numberOfParams=${methodDecl.numberOfParams}")
                }
            }
            .map { it.mapBoth { rawifyType(it, typeSolver) } }
            .all { (expected, actual) ->
                expected.isAssignableBy(actual)
            }
        }
    }

    /**
     * Resolves the [method call expression][node] with the given [scopeType].
     */
    private fun resolveMethodCallExprInScope(node: MethodCallExpr, scopeType: ResolvedType): ResolvedDeclaration? {
        when (scopeType) {
            is ResolvedReferenceType -> {
                // Get the types of the arguments - Will be used regardless of what the type declaration is
                val argTypes = node.arguments.map { calculateTypeInternal(it) }

                val scopeTypeDecl = scopeType.toResolvedTypeDeclaration()
                when (scopeTypeDecl) {
                    is ResolvedAnnotationDeclaration -> {
                        // Annotations are simple - Find a member with the correct name
                        val membersMatchingName = scopeTypeDecl.annotationMembers
                            .filter { it.name == node.nameAsString }

                        if (membersMatchingName.size == 1) {
                            return membersMatchingName.single()
                        }

                        // If the "method" is not a member of the annotation, fallback to find the method in
                        // java.lang.Object
                        val methodInObject = resolveMethodCallExprInScope(node, ResolvedObjectType(typeSolver))
                        if (methodInObject != null) {
                            return methodInObject
                        }
                    }
                    is ResolvedEnumDeclaration -> {
                        // Methods can exist either as members of the enum, or members of enum constants
                        // Search from the most-nested scope first

                        // Check whether the scope refers to an enum constant
                        val refFromEnumConstDecl = node.findAncestor<EnumConstantDeclaration> {
                            it.nameAsString in scopeTypeDecl.enumConstants.map { it.name }
                        }

                        // Find the method from within the enum constant (if our scope refers to the constant),
                        // returning it if found
                        if (refFromEnumConstDecl != null) {
                            val result = MethodResolutionLogic.findMostApplicable(
                                refFromEnumConstDecl.classBody.filterIsInstance<MethodDeclaration>()
                                    .map { it.resolve() },
                                node.nameAsString,
                                argTypes,
                                typeSolver
                            )

                            if (result.isSolved) {
                                return result.correspondingDeclaration
                            }
                        }

                        // Find the method from within the enum, returning it if found
                        val refFromEnumDecl = MethodResolutionLogic.findMostApplicable(
                            symbolSolverCache.getDeclaredMethods(scopeTypeDecl).toList(),
                            node.nameAsString,
                            argTypes,
                            typeSolver
                        )
                        if (refFromEnumDecl.isSolved) {
                            return refFromEnumDecl.correspondingDeclaration
                        }
                    }
                    else -> {
                        // We are a class type
                        // God knows why the default algorithm doesn't work, but this implementation will fuzzily solve
                        // for the best candidate (as opposed to perfect match).

                        // First we assume that the scopeType was previously incorrectly solved, so we re-solve it
                        // using the scopeType and argument types we figured out
                        runCatching {
                            val result = MethodResolutionLogic.solveMethodInType(
                                scopeTypeDecl,
                                node.nameAsString,
                                argTypes,
                                false
                            )

                            if (result.isSolved) {
                                if (needsResolveMethodCallWithGenericOverloadWorkaround(result.correspondingDeclaration)) {
                                    throw RuntimeException("Possibly buggy resolution of method call with generic parameter overloads")
                                }

                                return result.correspondingDeclaration
                            }
                        }

                        // If scopeType is an interface, try to solve the method as Object as well
                        if (scopeTypeDecl.isInterface) {
                            runCatching {
                                val result = MethodResolutionLogic.solveMethodInType(
                                    typeSolver.solvedJavaLangObject,
                                    node.nameAsString,
                                    argTypes,
                                    false
                                )

                                if (result.isSolved) {
                                    return result.correspondingDeclaration
                                }
                            }
                        }

                        // Well that didn't work. Use our (fuzzy) algorithm.

                        val allMethods = if (scopeTypeDecl.toTypedAstOrNull<ClassOrInterfaceDeclaration>(null)?.isLocalClassDeclaration == true) {
                            getAllMethodsFallback(scopeType)
                        } else {
                            symbolSolverCache.getAllMethods(scopeType)
                        }

                        // The simplest stuff first - Given the same scope, find methods which have the same name and
                        // same number of parameters
                        // The second condition is for variadic stuff - I really don't want to care about that crap :')
                        val potentiallyApplicableMethods = filterMethodByPotentialApplicability(
                            allMethods,
                            argTypes,
                            scopeType,
                            node
                        )

                        if (potentiallyApplicableMethods.size == 1) {
                            return potentiallyApplicableMethods.single()
                        }

                        // Now we try to match parameter types
                        // Only using isAssignableBy doesn't work, e.g. when we have Class<T> vs Class<E>. Therefore,
                        // we convert the type into the raw form, so we only match the class type.
                        val methodsMatchingParamTypes = filterMethodsByArgTypes(potentiallyApplicableMethods, argTypes)
                        val winningCandidate = findMostApplicableMethod(methodsMatchingParamTypes)
                        if (winningCandidate != null) {
                            return winningCandidate
                        }
                    }
                }

                // Are we a nested/anon type? If so, try methods in the parent type
                val parentTypeOrNull = scopeTypeDecl.runCatching {
                    toAst().getOrNull()
                        ?.ancestors
                        ?.firstOrNull {
                            it is TypeDeclaration<*> ||
                                    (it is ObjectCreationExpr && it.anonymousClassBody.isPresent)
                        }
                }.getOrNull()
                val parentResolvedType = when (parentTypeOrNull) {
                    is TypeDeclaration<*> -> {
                        createResolvedRefType(resolveDeclaration(parentTypeOrNull))
                    }
                    is ObjectCreationExpr -> calculateTypeInternal(parentTypeOrNull)
                    null -> null
                    else -> unreachable()
                }

                if (parentResolvedType != null) {
                    return resolveMethodCallExprInScope(node, parentResolvedType)
                }

                // Otherwise I am out of ideas for now
            }
            is ResolvedTypeVariable -> {
                // Let it failover to use the concrete implementation
            }
            else -> {
                LOGGER.warn("Resolution of MethodCallExpr with scope type ${scopeType.describe()} (${scopeType::class.java}) not implemented")
            }
        }

        return null
    }

    /**
     * Fallback implementation for resolving [MethodCallExpr].
     */
    internal fun resolveMethodCallExprFallback(node: MethodCallExpr): ResolvedDeclaration? {
        // If the node does not have a scope, it is possible that the method name is imported by a static import
        if (!node.hasScope()) {
            val staticImports = node.findCompilationUnit()
                .map { it.imports.filter { importDecl -> importDecl.isStatic } }
                .get()

            // If this method name is imported via a single static import, try that first
            val matchingSingleStaticImport = staticImports
                .filterNot { it.isAsterisk }
                .singleOrNull { it.rightMostSymbolAsString == node.nameAsString }
            if (matchingSingleStaticImport != null) {
                // Solve the type of the imported declaration
                val scopeType = typeSolver.solveType(matchingSingleStaticImport.nameAsString.dropLastWhile { it != '.' }.dropLast(1))

                resolveMethodCallExprInScope(node, createResolvedRefType(scopeType))
                    ?.also { return it }
            }

            // Find types of all on-demand static import which may contain a method with the same name as the node
            val matchingOnDemandStaticImport = staticImports
                .filter { it.isAsterisk }
                .mapNotNull { importDecl ->
                    typeSolver.solveType(importDecl.nameAsString)
                        .takeIf { resolvedRefTypeDecl ->
                            symbolSolverCache.getDeclaredMethods(resolvedRefTypeDecl)
                                .filter { it.isStatic }
                                .any { it.name == node.nameAsString }
                        }
                }

            // If this method name is imported via an on-demand static import, try that as well
            matchingOnDemandStaticImport.forEach { resolvedRefTypeDecl ->
                resolveMethodCallExprInScope(node, createResolvedRefType(resolvedRefTypeDecl))
                    ?.also { return it }
            }
        }

        // First - Get the type of the scope
        val scopeType = if (node.hasScope()) {
            calculateTypeInternal(node.scope.get())
        } else {
            // No lock required - No type caching performed
            getTypeOfThisIn(node, true)
        }
        resolveMethodCallExprInScope(node, scopeType)
            ?.also { return it }

        val fallbackScopeType = node.scope
            .getOrNull()
            ?.let { calculateTypeFallback(it) }
        fallbackScopeType
            ?.takeIf { it != scopeType }
            ?.let { resolveMethodCallExprInScope(node, it) }
            ?.also { return it }

        // Find ancestors with a different `this` type and solve in that context
        node.ancestorsAsSequence()
            .mapNotNull { ancestorNode ->
                getTypeOfThisIn(ancestorNode, true).takeIf { it != scopeType }
            }
            .toSortedSet(RESOLVED_TYPE_COMPARATOR)
            .firstNotNullOfOrNull {
                resolveMethodCallExprInScope(node, it)
            }
            ?.also { return it }

        LOGGER.warn("Resolution of MethodCallExpr where no method matches argument type(s) not implemented")
        LOGGER.warn("Attempting to find a method matching `$node` (${node.fullRangeString}) in ${scopeType.describe()}")

        return null
    }

    /**
     * Solves the [type] within the context of the [scope].
     */
    private fun solveTypeInType(
        type: ClassOrInterfaceType,
        scope: ResolvedReferenceTypeDeclaration
    ): ResolvedReferenceTypeDeclaration? {
        // If the type is the same as the name of the scope, we are done
        if (type.nameAsString == scope.qualifiedName) {
            return scope
        }

        // Assume the type is nested - Append the entire type into the qname of the scope, and try again
        val nestedName = "${scope.qualifiedName}.${type.nameWithScope}"
        val resolvedTypeDecl = typeSolver.tryToSolveType(nestedName)
        if (resolvedTypeDecl.isSolved) {
            return resolvedTypeDecl.correspondingDeclaration
        }

        // Same as before, but assume the type is additionally qualified by scope
        val leftMostTypeComponent = extractLeftMostType(type)
        if (leftMostTypeComponent.nameAsString == scope.qualifiedName) {
            val resolvedDeclWithExplicitLhs =
                typeSolver.tryToSolveType("${scope.packageName}.${type.nameWithScope}")
            if (resolvedDeclWithExplicitLhs.isSolved) {
                return resolvedDeclWithExplicitLhs.correspondingDeclaration
            }
        }

        // Try supertypes for inherited member types
        symbolSolverCache.getAncestors(scope).forEach {
            val resolvedDeclInSupertype = solveTypeInType(type, it.typeDeclaration.get())
            if (resolvedDeclInSupertype != null) {
                return resolvedDeclInSupertype
            }
        }

        return null
    }

    /**
     * Solves the [type] within the context of the [scope].
     */
    private fun solveTypeInType(
        type: ClassOrInterfaceType,
        scope: TypeDeclaration<*>
    ): ResolvedReferenceTypeDeclaration? {
        val scopeType = resolveDeclaration<ResolvedReferenceTypeDeclaration>(scope)
        // If the type is the same as the name of the scope, we are done
        if (!type.scope.isPresent && type.nameAsString == scope.nameAsString) {
            return scopeType
        }

        // Assume the type is nested - Append the entire type into the qname of the scope, and try again
        val nestedName = "${scopeType.qualifiedName}.${type.nameWithScope}"
        val resolvedTypeDecl = typeSolver.tryToSolveType(nestedName)
        if (resolvedTypeDecl.isSolved) {
            return resolvedTypeDecl.correspondingDeclaration
        }

        // Same as before, but assume the type is additionally qualified by scope
        val leftMostTypeComponent = extractLeftMostType(type)
        if (leftMostTypeComponent.nameAsString == scope.nameAsString) {
            val resolvedDeclWithExplicitLhs =
                typeSolver.tryToSolveType("${scopeType.packageName}.${type.nameWithScope}")
            if (resolvedDeclWithExplicitLhs.isSolved) {
                return resolvedDeclWithExplicitLhs.correspondingDeclaration
            }
        }

        // Try the parent scope if we are also nested
        val parentScope = scope.findAncestor(TypeDeclaration::class.java).getOrNull()
        if (parentScope != null) {
            val resolvedDeclInParentScope = solveTypeInType(type, parentScope)
            if (resolvedDeclInParentScope != null) {
                return resolvedDeclInParentScope
            }
        }

        // Try supertypes for inherited member types
        val supertypes = scope
            .let { (it as? NodeWithExtends<*>)?.extendedTypes.orEmpty() + (it as? NodeWithImplements<*>)?.implementedTypes.orEmpty() }
            .filterNot { it == type }
            .mapNotNull { toResolvedType<ResolvedReferenceType>(it).typeDeclaration?.getOrNull() }

        supertypes
            .forEach { supertypeDecl ->
                val resolvedDeclInSupertype = supertypeDecl.toTypedAstOrNull<TypeDeclaration<*>>(null)
                    ?.let { solveTypeInType(type, it) }
                    ?: solveTypeInType(type, supertypeDecl)

                if (resolvedDeclInSupertype != null) {
                    return resolvedDeclInSupertype
                }
            }

        return null
    }

    /**
     * Solves a name in an arbitrary node as a type.
     *
     * @param pretendClassType The name enclosed in a [ClassOrInterfaceType] AST node. Use
     * [StaticJavaParser.parseClassOrInterfaceType] to convert from a string.
     * @param context [Node] which the [pretendClassType] is derived from.
     */
    private fun solveNameAsType(
        pretendClassType: ClassOrInterfaceType,
        context: Node
    ): ResolvedReferenceTypeDeclaration? {
        val leftMostTypeComponent = extractLeftMostType(pretendClassType)

        val solveInAnonClassBody = context.ancestorsAsSequence()
            .filterIsInstance<ObjectCreationExpr>()
            .filter { it.anonymousClassBody.isPresent }
            .map {
                // We solve using ourselves to prevent infinite recursion if the type contains generic parameters
                solveNameAsType(it.type, it)
            }
            .mapNotNull { it?.toTypedAstOrNull<TypeDeclaration<*>>(null) }
            .firstNotNullOfOrNull { solveTypeInType(pretendClassType, it) }
        if (solveInAnonClassBody != null) {
            return solveInAnonClassBody
        }

        val classInCUOrNull = solveTypeInType(
            pretendClassType,
            context.findAncestor(TypeDeclaration::class.java).get()
        )
        if (classInCUOrNull != null) {
            return classInCUOrNull
        }

        // If our class name has a scope, solve the type within the scope
        if (pretendClassType.scope.isPresent) {
            val scopeType = runCatching {
                toResolvedType<ResolvedReferenceType>(pretendClassType.scope.get()).toResolvedTypeDeclaration()
            }.getOrNull()
            val classInScopeOrNull = scopeType?.let {
                solveTypeInType(
                    StaticJavaParser.parseClassOrInterfaceType(pretendClassType.nameAsString),
                    it
                )
            }

            if (classInScopeOrNull != null) {
                return classInScopeOrNull
            }
        }

        // Single-type-import shadows everything (except a type in our CU)
        val importOrNull = context.findCompilationUnit()
            .get()
            .imports
            .find { importDecl ->
                if (importDecl.isAsterisk) {
                    typeSolver.tryToSolveType("${importDecl.nameAsString}.${leftMostTypeComponent.nameAsString}").isSolved
                } else {
                    leftMostTypeComponent.nameAsString == importDecl.nameAsString.takeLastWhile { it != '.' }
                }
            }

        if (importOrNull != null && !importOrNull.isAsterisk) {
            val typeName =
                "${importOrNull.nameAsString.removeSuffix(leftMostTypeComponent.nameAsString)}${pretendClassType.nameWithScope}"

            val resolvedTypeDecl = typeSolver.tryToSolveType(typeName)
            if (resolvedTypeDecl.isSolved) {
                return resolvedTypeDecl.correspondingDeclaration
            }
        }

        val classInSamePackageOrNull = context.findCompilationUnit()
            .flatMap { it.packageDeclaration }
            .map { it.nameAsString }
            .getOrNull()
            ?.let { typeSolver.tryToSolveType("$it.${pretendClassType.nameWithScope}") }

        if (classInSamePackageOrNull?.isSolved == true) {
            return classInSamePackageOrNull.correspondingDeclaration
        }

        // Type-import-on-demand shadows nothing
        // Note that the implicit java.lang import is on-demand, so the order does not matter here
        if (importOrNull != null && importOrNull.isAsterisk) {
            val typeName = "${importOrNull.nameAsString}.${pretendClassType.nameWithScope}"

            val resolvedTypeDecl = typeSolver.tryToSolveType(typeName)
            if (resolvedTypeDecl.isSolved) {
                return resolvedTypeDecl.correspondingDeclaration
            }
        }

        val classInJavaLang = typeSolver.tryToSolveType("java.lang.${pretendClassType.nameWithScope}")
        if (classInJavaLang.isSolved) {
            return classInJavaLang.correspondingDeclaration
        }

        return null
    }

    /**
     * Resolves a [NameExpr] as a type.
     *
     * This is usually needed when a class name is used as the scope of an expression.
     */
    private fun resolveNameExprAsType(node: NameExpr): ResolvedReferenceTypeDeclaration? =
        solveNameAsType(StaticJavaParser.parseClassOrInterfaceType(node.nameAsString), node)

    /**
     * Whether the [methodDecl] is potentially incorrect resolved due to it and another method sharing a type variable
     * with the same name at the same parameter position, and requires to be solved by the fallback algorithm.
     */
    private fun needsResolveMethodCallWithGenericOverloadWorkaround(methodDecl: ResolvedMethodDeclaration): Boolean {
        if (methodDecl.parameters.none { it.type.isTypeVariable }) {
            return false
        }

        val idxOfTp = methodDecl.parameters
            .withIndex()
            .filter { it.value.type.isTypeVariable }
            .map { it.index }
        val declType = methodDecl.declaringType()

        return symbolSolverCache.getAllMethods(declType)
            .asSequence()
            .filterNot {
                with(it.declaration) {
                    qualifiedName == methodDecl.qualifiedName &&
                            parameters.zip(methodDecl.parameters).all { (a, b) ->
                                val aType = a.type
                                val bType = b.type

                                if (aType.isTypeVariable && bType.isTypeVariable) {
                                    val aTp = aType.asTypeParameter()
                                    val bTp = bType.asTypeParameter()

                                    aTp == bTp && aTp.containerId == bTp.containerId
                                } else if (a.type.isArray && b.type.isArray) {
                                    val aComponent = a.type.asArrayType().baseComponentType()
                                    val bComponent = b.type.asArrayType().baseComponentType()

                                    if (a.type != b.type) {
                                        false
                                    } else if (aComponent.isTypeVariable && bComponent.isTypeVariable) {
                                        val aTp = aComponent.asTypeParameter()
                                        val bTp = bComponent.asTypeParameter()

                                        aTp == bTp && aTp.containerId == bTp.containerId
                                    } else {
                                        aComponent == bComponent
                                    }
                                } else {
                                    a.type == b.type
                                }
                            }
                }
            }
            .filter { it.name == methodDecl.name }
            .filter { it.declaration.numberOfParams == methodDecl.numberOfParams }
            .filter { it.paramTypes.any { it.isTypeVariable } }
            .any { methodUsage ->
                idxOfTp
                    .map { methodDecl.parameters[it].type to methodUsage.declaration.parameters[it].type }
                    .all { (a, b) ->
                        a.isTypeVariable && b.isTypeVariable && a.asTypeParameter().name == b.asTypeParameter().name
                    }
            }
    }

    /**
     * Fallback implementation for [JavaParserFacade.solveArguments].
     *
     * Concretely solves the argument type by using [FuzzySymbolSolver.calculateType].
     */
    private fun solveArgumentsFallback(node: Node, args: NodeList<Expression>, argumentTypes: MutableList<ResolvedType>, placeholders: MutableList<LambdaArgumentTypePlaceholder>) {
        args.forEachIndexed { i, parameterValue ->
            if (parameterValue.isLambdaExpr || parameterValue.isMethodReferenceExpr) {
                val placeholder = LambdaArgumentTypePlaceholder(i)
                argumentTypes.add(placeholder)
                placeholders.add(placeholder)
            } else {
                runCatching {
                    argumentTypes.add(calculateType(parameterValue))
                }.recoverCatching {  e ->
                    if (e is UnsolvedSymbolException) {
                        throw e
                    } else {
                        throw RuntimeException("Unable to calculate the type of a parameter of a method call. Method call: $node, Parameter: $parameterValue", e)
                    }
                }.getOrThrow()
            }
        }
    }

    /**
     * Fallback implementation for [JavaParserFacade.solveArguments] for constructors.
     *
     * Contains two fallbacks:
     *
     * - Implements simplified resolution algorithm based on JLS §15.12.2
     * - Implements fuzzy matching if there is only one candidate constructor
     */
    private fun solveConstructorByArgumentsFallback(
        constructors: List<ResolvedConstructorDeclaration>,
        argumentTypes: List<ResolvedType>,
        node: Node
    ): SymbolReference<ResolvedConstructorDeclaration> {
        // If there are no constructors, assume we are in an interface context
        if (constructors.isEmpty()) {
            return SymbolReference.unsolved()
        }

        // Implement simplified resolution based on JLS §15.12.2
        // §15.2.2.1 - Identify Potentially Applicable Methods
        val potentiallyApplicableMethods = filterMethodByPotentialApplicability(
            constructors,
            argumentTypes,
            getTypeOfThisIn(node).asReferenceType(),
            node
        )

        // §15.2.2.2 + §15.2.2.3 - Identify Matching Arity Methods Applicable by Strict/Loose Invocation
        val (matchingArityCtors, variableArityCtors) = potentiallyApplicableMethods.partition {
            !it.hasVariadicParameter()
        }

        // §15.2.2.4 - Identify Methods Applicable by Variable Arity Invocation
        val matchingArityCtorRes = runCatching {
            ConstructorResolutionLogic.findMostApplicable(matchingArityCtors, argumentTypes, typeSolver)
        }.getOrDefault(SymbolReference.unsolved())
        if (matchingArityCtorRes.isSolved) {
            return matchingArityCtorRes
        }

        val varArityCtorRes = runCatching {
            ConstructorResolutionLogic.findMostApplicable(variableArityCtors, argumentTypes, typeSolver)
        }.getOrDefault(SymbolReference.unsolved())
        if (varArityCtorRes.isSolved) {
            return varArityCtorRes
        }

        // Start resolving using fuzzy match
        if (potentiallyApplicableMethods.size == 1) {
            return SymbolReference.solved(potentiallyApplicableMethods.single())
        }

        val matchingArityCtorByAssignability = matchingArityCtors.singleOrNull {
            // Use raw parameter types to avoid JavaParser's weird type parameter assignability rules
            it.mapRawParamTypes().zip(argumentTypes)
                .all { (expected, actual) ->
                    expected.isAssignableBy(actual)
                }
        }
        if (matchingArityCtorByAssignability != null) {
            return SymbolReference.solved(matchingArityCtorByAssignability)
        }

        val varArityCtorByAssignability = filterMethodsByArgTypes(variableArityCtors, argumentTypes)
        varArityCtorByAssignability.singleOrNull()
            ?.also { return SymbolReference.solved(it) }

        LOGGER.warn(
            "Unimplemented resolution of constructor with multiple candidates\nCandidates:\n{}\nArgument Types:\n{}",
            potentiallyApplicableMethods.joinToString("\n") { "- ${it.getFixedQualifiedSignature(this)}" },
            argumentTypes.joinToString(", ", prefix = "(", postfix = ")") { it.describe() }
        )
        return SymbolReference.unsolved()
    }

    /**
     * @see JavaParserFacade.solve
     */
    private fun solveExplicitConstructorInvocationStmtFallback(explicitConstructorInvocationStmt: ExplicitConstructorInvocationStmt): SymbolReference<ResolvedConstructorDeclaration> {
        // Constructor invocation must exist within a class (not interface).
        val optAncestorClassOrInterfaceNode = explicitConstructorInvocationStmt.findAncestor(TypeDeclaration::class.java)
        if (!optAncestorClassOrInterfaceNode.isPresent) {
            return SymbolReference.unsolved()
        }

        val classOrInterfaceNode = optAncestorClassOrInterfaceNode.get()
        val resolvedClassNode = resolveDeclaration<ResolvedReferenceTypeDeclaration>(classOrInterfaceNode)
        check(!resolvedClassNode.isInterface) {
            "Expected to be a class or enum -- cannot call this() or super() within an interface."
        }

        val typeDecl = if (explicitConstructorInvocationStmt.isThis) {
            // this()
            resolvedClassNode.asReferenceType()
        } else {
            // super()
            resolvedClassNode.asClass().superClass.flatMap { it.typeDeclaration }.getOrNull()
        }
        if (typeDecl == null) {
            return SymbolReference.unsolved()
        }

        // Solve each of the arguments being passed into this constructor invocation.
        val argumentTypes = mutableListOf<ResolvedType>()
        val placeholders = mutableListOf<LambdaArgumentTypePlaceholder>()
        solveArgumentsFallback(explicitConstructorInvocationStmt, explicitConstructorInvocationStmt.arguments, argumentTypes, placeholders)

        // Determine which constructor is referred to, and return it.
        val res = solveConstructorByArgumentsFallback(
            symbolSolverCache.getConstructors(typeDecl),
            argumentTypes,
            explicitConstructorInvocationStmt
        )
        for (placeholder in placeholders) {
            placeholder.setMethod(res)
        }

        return res
    }

    /**
     * Whether [ctor] is a viable candidate in the constructor call of [objectCreationExpr].
     *
     * This will only check whether the constructor has the correct visibility for invocation by [objectCreationExpr].
     */
    private fun isViableCtorCandidate(
        ctor: ResolvedConstructorDeclaration,
        objectCreationExpr: ObjectCreationExpr
    ): Boolean {
        val typeDecl = ctor.declaringType()

        /**
         * Whether the class of [ctor] is a supertype of any type declaration enclosing the [objectCreationExpr].
         */
        fun isAncestor(): Boolean {
            return if (objectCreationExpr.anonymousClassBody.isPresent) {
                val resolvedAnonClassDecl = JavaParserAnonymousClassDeclaration(objectCreationExpr, typeSolver)

                typeDecl.qualifiedName in symbolSolverCache.getAllAncestors(resolvedAnonClassDecl)
                    .map { it.qualifiedName }
            } else {
                objectCreationExpr.ancestorsAsSequence()
                    .filterIsInstance<TypeDeclaration<*>>()
                    .any { ancestorTypeDecl ->
                        val ancestorResolvedDecl =
                            resolveDeclaration<ResolvedReferenceTypeDeclaration>(ancestorTypeDecl)
                        typeDecl.qualifiedName in symbolSolverCache.getAllAncestors(ancestorResolvedDecl)
                            .map { it.qualifiedName }
                    }
            }
        }

        /**
         * Whether the class of [ctor] resides in the same package as the type enclosing the [objectCreationExpr].
         */
        fun isInSamePackage(): Boolean {
            return objectCreationExpr.findCompilationUnit()
                .flatMap { it.packageDeclaration }
                .map { it.nameAsString == typeDecl.packageName }
                .getOrDefault(false)
        }

        /**
         * Whether the class of [ctor] is the same as any type enclosing the [objectCreationExpr].
         */
        fun isSameClass(): Boolean {
            return objectCreationExpr.ancestorsAsSequence()
                .filterIsInstance<TypeDeclaration<*>>()
                .any { ancestorTypeDecl ->
                    ancestorTypeDecl.fullyQualifiedName.map { it == typeDecl.qualifiedName }
                        .getOrDefault(false)
                }
        }

        return when (ctor.accessSpecifier()) {
            AccessSpecifier.PUBLIC -> true
            AccessSpecifier.PROTECTED -> isAncestor() || isInSamePackage()
            AccessSpecifier.NONE -> isInSamePackage()
            AccessSpecifier.PRIVATE -> isSameClass()
            null -> unreachable()
        }
    }

    /**
     * @see JavaParserFacade.solve
     */
    internal fun solveObjectCreationExprFallback(objectCreationExpr: ObjectCreationExpr): SymbolReference<ResolvedConstructorDeclaration> {
        val argumentTypes = mutableListOf<ResolvedType>()
        val placeholders = mutableListOf<LambdaArgumentTypePlaceholder>()

        solveArgumentsFallback(objectCreationExpr, objectCreationExpr.arguments, argumentTypes, placeholders)

        val typeDecl = runCatching {
            facade.convert(objectCreationExpr.type, objectCreationExpr)
                .takeIf { it.isReferenceType }
                ?.asReferenceType()
                ?.typeDeclaration
                ?.getOrNull()
        }.recoverCatching {
            solveNameAsType(objectCreationExpr.type, objectCreationExpr)
        }.getOrNull() ?: return SymbolReference.unsolved()

        val constructors = symbolSolverCache.getConstructors(typeDecl)
            .filter { isViableCtorCandidate(it, objectCreationExpr) }
        val res = solveConstructorByArgumentsFallback(
            constructors,
            argumentTypes,
            objectCreationExpr
        )
        for (placeholder in placeholders) {
            placeholder.setMethod(res)
        }
        return res
    }

    fun resolveConstructorDeclarationFromEnumConstant(node: EnumConstantDeclaration): ResolvedConstructorDeclaration {
        val argumentTypes = mutableListOf<ResolvedType>()
        val placeholders = mutableListOf<LambdaArgumentTypePlaceholder>()

        solveArgumentsFallback(node, node.arguments, argumentTypes, placeholders)

        val typeDecl = resolveDeclaration<ResolvedEnumConstantDeclaration>(node)
            .type
            .asReferenceType()
            .typeDeclaration
            .getOrNull()
            ?: throw UnsolvedSymbolException("We are unable to find the constructor declaration according to $node")

        val res = solveConstructorByArgumentsFallback(
            symbolSolverCache.getConstructors(typeDecl),
            argumentTypes,
            node
        )
        for (placeholder in placeholders) {
            placeholder.setMethod(res)
        }
        return if (res.isSolved) {
            res.correspondingDeclaration
        } else {
            throw UnsolvedSymbolException("We are unable to find the constructor declaration according to $node")
        }
    }

    override fun <T : Any> resolveDeclaration(node: Node, resultClass: Class<T>): T {
        injectSolverIfNotPresent(node)

        if (node.isDeclResolved) {
            if (resultClass.isInstance(node.resolvedDecl)) {
                return resultClass.cast(node.resolvedDecl)
            }
        }

        if (node is MethodDeclaration) {
            val resolved = JavaParserMethodDeclaration(node, typeSolver)
            node.resolvedDecl = resolved
            if (resultClass.isInstance(resolved)) {
                return resultClass.cast(resolved)
            }
        }
        if (node is ClassOrInterfaceDeclaration) {
            val resolved = toTypeDeclaration(node)
            node.resolvedDecl = resolved
            if (resultClass.isInstance(resolved)) {
                return resultClass.cast(resolved)
            }
        }
        if (node is EnumDeclaration) {
            val resolved = toTypeDeclaration(node)
            node.resolvedDecl = resolved
            if (resultClass.isInstance(resolved)) {
                return resultClass.cast(resolved)
            }
        }
        if (node is EnumConstantDeclaration) {
            val enumDeclaration = node.findAncestor(EnumDeclaration::class.java).get().resolve().asEnum()
            val resolved = enumDeclaration.enumConstants
                .first { (it as JavaParserEnumConstantDeclaration).wrappedNode == node }
            node.resolvedDecl = resolved
            if (resultClass.isInstance(resolved)) {
                return resultClass.cast(resolved)
            }
        }
        if (node is ConstructorDeclaration) {
            val typeDeclaration = node.parentNode.get() as TypeDeclaration<*>
            val resolvedTypeDeclaration = resolveDeclaration<ResolvedReferenceTypeDeclaration>(typeDeclaration)
            val resolved = symbolSolverCache.getConstructors(resolvedTypeDeclaration)
                .filterIsInstance<JavaParserConstructorDeclaration<*>>()
                .firstOrNull { it.wrappedNode == node }
                ?: throw RuntimeException("This constructor cannot be found in its parent. This seems wrong")
            node.resolvedDecl = resolved
            if (resultClass.isInstance(resolved)) {
                return resultClass.cast(resolved)
            }
        }
        if (node is AnnotationDeclaration) {
            val resolved = toTypeDeclaration(node)
            node.resolvedDecl = resolved
            if (resultClass.isInstance(resolved)) {
                return resultClass.cast(resolved)
            }
        }
        if (node is AnnotationMemberDeclaration) {
            val annotationDeclaration = node.findAncestor(AnnotationDeclaration::class.java).get().resolve()
            val resolved = annotationDeclaration.annotationMembers
                .first { (it as JavaParserAnnotationMemberDeclaration).wrappedNode == node }
            node.resolvedDecl = resolved
            if (resultClass.isInstance(resolved)) {
                return resultClass.cast(resolved)
            }
        }
        if (node is FieldDeclaration) {
            if (node.variables.size != 1) {
                throw RuntimeException("Cannot resolve a Field Declaration including multiple variable declarators. Resolve the single variable declarators")
            }
            val resolved = JavaParserFieldDeclaration(node.getVariable(0), typeSolver)
            node.resolvedDecl = resolved
            if (resultClass.isInstance(resolved)) {
                return resultClass.cast(resolved)
            }
        }
        if (node is VariableDeclarator) {
            val resolved: ResolvedValueDeclaration = when {
                node.parentNode.map { it is FieldDeclaration }.orElse(false) ->
                    JavaParserFieldDeclaration(node, typeSolver)
                node.parentNode.map { it is VariableDeclarationExpr }.orElse(false) ->
                    JavaParserVariableDeclaration(node, typeSolver)
                else -> throw UnsupportedOperationException("Parent of VariableDeclarator is: ${node.parentNode}")
            }
            node.resolvedDecl = resolved
            if (resultClass.isInstance(resolved)) {
                return resultClass.cast(resolved)
            }
        }
        if (node is MethodCallExpr) {
            // res is used to determine whether the node is solved but the resultClass is incorrect
            // tr is used to determine whether an exception was thrown during the solving process
            val (res, facadeEx) = runCatching {
                facade.solve(node)
            }.mapCatching { result ->
                if (result.isSolved) {
                    val decl = result.correspondingDeclaration

                    if (needsResolveMethodCallWithGenericOverloadWorkaround(decl)) {
                        throw RuntimeException("Possibly buggy resolution of method call with generic parameter overloads")
                    }

                    node.resolvedDecl = decl
                    if (resultClass.isInstance(decl)) {
                        return resultClass.cast(decl)
                    }
                }

                result.takeIf { it.isSolved }
            }.let { it.getOrNull() to it.exceptionOrNull() }

            val (fallbackRes, fallbackEx) = if (res == null) {
                runCatching {
                    resolveMethodCallExprFallback(node)
                }.let { it.getOrNull() to it.exceptionOrNull() }
            } else (null to null)

            if (fallbackRes != null) {
                node.resolvedDecl = fallbackRes
                if (resultClass.isInstance(fallbackRes)) {
                    return resultClass.cast(fallbackRes)
                }
            }

            when {
                // Encountered an exception when solving using fallback -> re-throw that exception
                fallbackEx != null ->
                    throw RuntimeException(
                        "Encountered error while solving `$node` using fallback algorithm",
                        fallbackEx
                    )
                // Encountered an exception when solving -> re-throw that exception
                facadeEx != null ->
                    throw RuntimeException(
                        "Encountered error while solving `$node` using default algorithm",
                        facadeEx
                    )
                // Symbol was not solved using both default and fallback algorithms - Throw UnsolvedSymbolException
                res == null && fallbackRes == null ->
                    throw UnsolvedSymbolException("We are unable to find the method declaration corresponding to $node")
                // Otherwise - Cast to target type unsuccessful -> Fallthrough to throw UnsupportedOperationException
                else -> {}
            }
        }
        if (node is ObjectCreationExpr) {
            runCatching {
                val result = facade.solve(node)
                if (result.isSolved) {
                    node.resolvedDecl = result.correspondingDeclaration
                    if (resultClass.isInstance(result.correspondingDeclaration)) {
                        return resultClass.cast(result.correspondingDeclaration)
                    }
                }
            }

            val fallback = solveObjectCreationExprFallback(node)
            if (fallback.isSolved) {
                node.resolvedDecl = fallback.correspondingDeclaration
                if (resultClass.isInstance(fallback.correspondingDeclaration)) {
                    return resultClass.cast(fallback.correspondingDeclaration)
                }
            }

            if (resultClass.isAssignableFrom(JavaParserAnonymousClassDeclaration::class.java)) {
                return resultClass.cast(JavaParserAnonymousClassDeclaration(node, typeSolver))
            }

            throw UnsolvedSymbolException("We are unable to find the constructor declaration corresponding to $node")
        }
        if (node is NameExpr) {
            val (resolvedDecl, facadeEx) = runCatching {
                val result = facade.solve(node)
                if (result.isSolved) {
                    node.resolvedDecl = result.correspondingDeclaration
                    if (resultClass.isInstance(result.correspondingDeclaration)) {
                        return resultClass.cast(result.correspondingDeclaration)
                    }
                }

                result.takeIf { it.isSolved }
            }.let { it.getOrNull() to it.exceptionOrNull() }

            if (resolvedDecl == null) {
                // Workaround: JavaParser does not resolve enum constants by simple name in SwitchEntry context
                if (node.parentNode.get() is SwitchEntry) {
                    val switchEntry = node.parentNode.get() as SwitchEntry
                    val switchParent = switchEntry.parentNode.get() as SwitchNode
                    val selectorType = calculateTypeInternal(switchParent.selector)

                    if (selectorType is ResolvedReferenceType) {
                        val selectorTypeDecl = selectorType.typeDeclaration.get()
                        if (selectorTypeDecl !is ResolvedEnumDeclaration) {
                            TODO()
                        }

                        val matchingEnumConst = selectorTypeDecl.enumConstants
                            .find { it.name == node.nameAsString }

                        if (matchingEnumConst != null) {
                            node.resolvedDecl = matchingEnumConst
                            if (resultClass.isInstance(matchingEnumConst)) {
                                return resultClass.cast(matchingEnumConst)
                            }
                        }
                    }
                }

                // JavaParser does not support solving fields in an EnumConstantDeclaration, try that
                val parentTypeContainer = node.parentContainer
                    ?.let { if (it is MethodDeclaration) it.parentContainer else it }

                if (parentTypeContainer is EnumConstantDeclaration) {
                    val foundEnumConstantField = parentTypeContainer
                        .classBody
                        .flatMap {
                            if (it.isFieldDeclaration) {
                                it.asFieldDeclaration().variables
                            } else emptyList()
                        }
                        .singleOrNull { it.nameAsString == node.nameAsString }
                        ?.let { JavaParserFieldDeclaration(it, typeSolver) }
                        .let { SymbolReference(it) }

                    if (foundEnumConstantField.isSolved) {
                        node.resolvedDecl = foundEnumConstantField.correspondingDeclaration
                        if (resultClass.isInstance(foundEnumConstantField.correspondingDeclaration)) {
                            return resultClass.cast(foundEnumConstantField.correspondingDeclaration)
                        }
                    }
                }
            }

                val solveAsTypeResult = resolveNameExprAsType(node)
                if (solveAsTypeResult != null) {
                    node.resolvedDecl = solveAsTypeResult
                    if (resultClass.isInstance(solveAsTypeResult)) {
                        return resultClass.cast(solveAsTypeResult)
                    }
                }

            throw UnsolvedSymbolException(
                "We are unable to find the value declaration corresponding to $node",
                facadeEx
            )
        }
        if (node is MethodReferenceExpr) {
            val result = facade.solve(node)
            if (result.isSolved) {
                node.resolvedDecl = result.correspondingDeclaration
                if (resultClass.isInstance(result.correspondingDeclaration)) {
                    return resultClass.cast(result.correspondingDeclaration)
                }
            } else {
                throw UnsolvedSymbolException("We are unable to find the method declaration corresponding to $node")
            }
        }
        if (node is FieldAccessExpr) {
            val (defaultSolveRes, defaultSolveEx) = runCatching {
                var result = facade.solve(node)

                if (result.isSolved) {
                    node.resolvedDecl = result.correspondingDeclaration
                    if (resultClass.isInstance(result.correspondingDeclaration)) {
                        return resultClass.cast(result.correspondingDeclaration)
                    }
                }

                if (node.name.id == "length") {
                    val scopeType = super.calculateType(node.scope)
                    if (scopeType.isArray) {
                        result = SymbolReference.solved(ArrayLengthValueDeclaration)
                        node.resolvedDecl = ArrayLengthValueDeclaration
                        if (resultClass.isInstance(ArrayLengthValueDeclaration)) {
                            return resultClass.cast(ArrayLengthValueDeclaration)
                        }
                    }
                }

                result.takeIf { it.isSolved }
            }.let { it.getOrNull() to it.exceptionOrNull() }

            if (defaultSolveRes == null) {
                val solvedDeclAsType = typeSolver.tryToSolveType(node.toString())
                if (solvedDeclAsType.isSolved) {
                    node.resolvedDecl = solvedDeclAsType.correspondingDeclaration
                    if (resultClass.isInstance(solvedDeclAsType.correspondingDeclaration)) {
                        return resultClass.cast(solvedDeclAsType.correspondingDeclaration)
                    }
                }

                val scopeType = calculateTypeInternal(node.scope)
                if (node.name.id == "length") {
                    if (scopeType.isArray) {
                        node.resolvedDecl = ArrayLengthValueDeclaration
                        if (resultClass.isInstance(ArrayLengthValueDeclaration)) {
                            return resultClass.cast(ArrayLengthValueDeclaration)
                        }
                    }
                }

                when (scopeType) {
                    is ResolvedReferenceType -> {
                        // Try again?
                        val matchingField = scopeType.declaredFields.find { it.name == node.nameAsString }
                        if (matchingField != null) {
                            node.resolvedDecl = matchingField
                            if (resultClass.isInstance(matchingField)) {
                                return resultClass.cast(matchingField)
                            }
                        }

                        // It's a nested type then? Try solving using the fully-qualified name
                        runCatching {
                            typeSolver.solveType("${scopeType.qualifiedName}.${node.nameAsString}")
                        }.onSuccess {
                            node.resolvedDecl = it
                            if (resultClass.isInstance(it)) {
                                return resultClass.cast(it)
                            }
                        }
                    }
                    is ResolvedTypeVariable -> {
                        // Resolve field accesses for type parameters with bounds
                        val decl = scopeType.asTypeParameter()
                            .bounds
                            .map { it.type }
                            .mapNotNull { boundedType ->
                                when (boundedType) {
                                    is ResolvedReferenceType ->
                                        boundedType.declaredFields.find { it.name == node.nameAsString }
                                    else -> {
                                        LOGGER.warn("Field-of-bounds declaration resolution not implemented")
                                        null
                                    }
                                }
                            }
                            .singleOrNull()

                        if (decl != null) {
                            node.resolvedDecl = decl
                            if (resultClass.isInstance(decl)) {
                                return resultClass.cast(decl)
                            }
                        }
                    }
                    is ResolvedWildcard -> {
                        // Resolve field accesses for type parameters with bounds
                        if (scopeType.isSuper) {
                            TODO()
                        }

                        val decl = (if (scopeType.isBounded) scopeType.boundedType else ResolvedObjectType(typeSolver))
                            .let { boundedType ->
                                when (boundedType) {
                                    is ResolvedReferenceType ->
                                        boundedType.declaredFields.find { it.name == node.nameAsString }
                                    else -> {
                                        LOGGER.warn("Field-of-bounds declaration resolution not implemented")
                                        null
                                    }
                                }
                            }

                        if (decl != null) {
                            node.resolvedDecl = decl
                            if (resultClass.isInstance(decl)) {
                                return resultClass.cast(decl)
                            }
                        }
                    }
                }
            }

            throw UnsolvedSymbolException(
                "We are unable to find the value declaration corresponding to $node",
                defaultSolveEx
            )
        }
        if (node is ThisExpr) {
            val result = facade.solve(node)
            if (result.isSolved) {
                node.resolvedDecl = result.correspondingDeclaration
                if (resultClass.isInstance(result.correspondingDeclaration)) {
                    return resultClass.cast(result.correspondingDeclaration)
                }
            } else {
                throw UnsolvedSymbolException("We are unable to find the type declaration corresponding to $node")
            }
        }
        if (node is ExplicitConstructorInvocationStmt) {
            runCatching {
                val result = facade.solve(node)
                if (result.isSolved && !result.correspondingDeclaration.lastParam.isVariadic) {
                    node.resolvedDecl = result.correspondingDeclaration
                    if (resultClass.isInstance(result.correspondingDeclaration)) {
                        return resultClass.cast(result.correspondingDeclaration)
                    }
                }
            }

            val fallback = solveExplicitConstructorInvocationStmtFallback(node)
            if (fallback.isSolved) {
                node.resolvedDecl = fallback.correspondingDeclaration
                if (resultClass.isInstance(fallback.correspondingDeclaration)) {
                    return resultClass.cast(fallback.correspondingDeclaration)
                }
            }

            throw UnsolvedSymbolException("We are unable to find the constructor declaration corresponding to $node")
        }
        if (node is Parameter) {
            if (resultClass == ResolvedParameterDeclaration::class.java) {
                val callableDeclaration = node.findAncestor(CallableDeclaration::class.java).get()
                val resolvedMethodLikeDeclaration =
                    if (callableDeclaration.isConstructorDeclaration) {
                        callableDeclaration.asConstructorDeclaration()
                    } else {
                        callableDeclaration.asMethodDeclaration()
                    }.let { resolveDeclaration<ResolvedMethodLikeDeclaration>(it) }
                for (i in 0 until resolvedMethodLikeDeclaration.numberOfParams) {
                    if (resolvedMethodLikeDeclaration.getParam(i).name == node.nameAsString) {
                        node.resolvedDecl = resolvedMethodLikeDeclaration.getParam(i)
                        @Suppress("UNCHECKED_CAST")
                        return resultClass.cast(resolvedMethodLikeDeclaration.getParam(i)) as T
                    }
                }
            }
        }
        if (node is AnnotationExpr) {
            val result = facade.solve(node)
            if (result.isSolved) {
                node.resolvedDecl = result.correspondingDeclaration
                if (resultClass.isInstance(result.correspondingDeclaration)) {
                    return resultClass.cast(result.correspondingDeclaration)
                }
            } else {
                throw UnsolvedSymbolException("We are unable to find the annotation declaration corresponding to $node")
            }
        }
        if (node is PatternExpr) {
            val result = facade.solve(node)
            if (result.isSolved) {
                node.resolvedDecl = result.correspondingDeclaration
                if (resultClass.isInstance(result.correspondingDeclaration)) {
                    return resultClass.cast(result.correspondingDeclaration)
                }
            } else {
                throw UnsolvedSymbolException("We are unable to find the method declaration corresponding to $node")
            }
        }
        throw UnsupportedOperationException("Unable to find the declaration of type ${resultClass.simpleName} from ${node::class.java.simpleName} (expr: `$node`)")
    }

    override fun <T : Any> toResolvedType(javaparserType: Type, resultClass: Class<T>): T {
        injectSolverIfNotPresent(javaparserType)

        if (javaparserType.isTypeResolved) {
            if (resultClass.isInstance(javaparserType.resolvedType)) {
                return resultClass.cast(javaparserType.resolvedType)
            }
        }

        runCatching {
            // Skip to fallback path if the type is possibly a nested type
            if (javaparserType.isClassOrInterfaceType && javaparserType.asClassOrInterfaceType().scope.isPresent) {
                val solvedTypeAsQName = typeSolver.tryToSolveType(javaparserType.asString())
                if (!solvedTypeAsQName.isSolved) {
                    throw RuntimeException("Possibly nested type requires fallback path")
                }
            }

            val resolvedType = facade.convertToUsage(javaparserType)
            javaparserType.resolvedType = resolvedType

            if (resultClass.isInstance(resolvedType)) {
                return resultClass.cast(resolvedType)
            }
        }.recoverCatching { tr ->
            when (javaparserType) {
                is ClassOrInterfaceType -> {
                    val solvedDeclInScope = runCatching {
                        solveNameAsType(javaparserType, javaparserType)
                    }.getOrNull()
                    if (solvedDeclInScope != null) {
                        val typeParams = javaparserType.typeArguments
                            .map { typeArgs ->
                                typeArgs.map { toResolvedType<ResolvedType>(it) }
                            }
                            .getOrDefault(emptyList())
                        val resolvedType =
                            ReferenceTypeImpl(solvedDeclInScope, typeParams) as ResolvedReferenceType
                        javaparserType.resolvedType = resolvedType

                        if (resultClass.isInstance(resolvedType)) {
                            return resultClass.cast(resolvedType)
                        }
                    }

                    val fallbackException = runCatching {
                        val resolvedType =
                            createResolvedRefType(typeSolver.solveType(javaparserType.nameWithScope))
                        javaparserType.resolvedType = resolvedType

                        if (resultClass.isInstance(resolvedType)) {
                            return resultClass.cast(resolvedType)
                        }
                    }.exceptionOrNull()

                    fallbackException?.let {
                        throw UnsolvedSymbolException(
                            "Unable to solve type for `$javaparserType` (${javaparserType::class.simpleName})",
                            fallbackException
                        )
                    }
                }

                is UnionType -> {
                    javaparserType.elements
                        .map { toResolvedType<ResolvedType>(it) }
                        .let { ResolvedUnionType(it) }
                }

                else -> throw UnsolvedSymbolException(
                    "Unable to solve type for `$javaparserType` (${javaparserType::class.simpleName})",
                    tr
                )
            }
        }.getOrThrow()

        throw UnsupportedOperationException("Unable to get the resolved type of class ${resultClass.simpleName} from $javaparserType")
    }

    /**
     * Fallback algorithm for calculating the type of a [BinaryExpr].
     *
     * @see com.github.javaparser.symbolsolver.javaparsermodel.TypeExtractor
     */
    private fun calculateBinaryExprTypeFallback(binaryExpr: BinaryExpr): ResolvedType {
        return when (val operator = checkNotNull(binaryExpr.operator)) {
            BinaryExpr.Operator.PLUS,
            BinaryExpr.Operator.MINUS,
            BinaryExpr.Operator.MULTIPLY,
            BinaryExpr.Operator.DIVIDE,
            BinaryExpr.Operator.REMAINDER,
            BinaryExpr.Operator.BINARY_OR,
            BinaryExpr.Operator.BINARY_AND,
            BinaryExpr.Operator.XOR -> {
                val leftType = calculateTypeInternal(binaryExpr.left)
                val rightType = calculateTypeInternal(binaryExpr.right)

                if (operator == BinaryExpr.Operator.PLUS) {
                    val isLeftString =
                        leftType.isReferenceType && leftType.asReferenceType().qualifiedName == java.lang.String::class.qualifiedName!!
                    val isRightString =
                        rightType.isReferenceType && rightType.asReferenceType().qualifiedName == java.lang.String::class.qualifiedName!!

                    if (isLeftString || isRightString) {
                        return if (isLeftString) leftType else rightType
                    }
                }

                val isLeftNumeric = leftType.isPrimitive && leftType.asPrimitive().isNumeric
                val isRightNumeric = rightType.isPrimitive && rightType.asPrimitive().isNumeric

                if (isLeftNumeric && isRightNumeric) {
                    return leftType.asPrimitive().bnp(rightType.asPrimitive())
                }

                if (rightType.isAssignableBy(leftType)) {
                    return rightType
                }
                leftType
            }
            BinaryExpr.Operator.LESS_EQUALS,
            BinaryExpr.Operator.LESS,
            BinaryExpr.Operator.GREATER,
            BinaryExpr.Operator.GREATER_EQUALS,
            BinaryExpr.Operator.EQUALS,
            BinaryExpr.Operator.NOT_EQUALS,
            BinaryExpr.Operator.OR,
            BinaryExpr.Operator.AND -> ResolvedPrimitiveType.BOOLEAN
            BinaryExpr.Operator.SIGNED_RIGHT_SHIFT,
            BinaryExpr.Operator.UNSIGNED_RIGHT_SHIFT,
            BinaryExpr.Operator.LEFT_SHIFT -> {
                val rt = calculateTypeInternal(binaryExpr.left)
                ResolvedPrimitiveType.unp(rt)
            }
        }
    }

    private val Expression.parentResolvedContainer: ResolvedTypeParametrizable
        get() = resolveDeclaration<ResolvedDeclaration>(checkNotNull(parentContainer)) as ResolvedTypeParametrizable

    /**
     * Calculates the type of the [expression] using a fallback algorithm.
     */
    fun calculateTypeFallback(expression: Expression): ResolvedType {
        val exprContainer = expression.parentResolvedContainer

        return when (expression) {
            is ClassExpr -> {
                val resolvedClassType = super.calculateType(expression).asReferenceType()
                val resolvedClassLiteralType = toResolvedType<ResolvedReferenceType>(expression.type)

                ReferenceTypeImpl(
                    resolvedClassType.toResolvedTypeDeclaration(),
                    listOf(resolvedClassLiteralType)
                )
            }
            is FieldAccessExpr -> {
                val resolvedDecl = resolveDeclaration<ResolvedDeclaration>(expression)
                when {
                    resolvedDecl is ResolvedValueDeclaration -> {
                        val resolvedType = resolvedDecl.type
                        val resolvedScopeType = calculateTypeInternal(expression.scope)
                        val resolvedScopeRefType = resolvedScopeType.asReferenceTypeOrNull()

                        solveTpInClassContext(resolvedType, resolvedScopeRefType, exprContainer)
                    }
                    resolvedDecl.isType -> {
                        when (val typeDecl = resolvedDecl.asType()) {
                            is ResolvedReferenceTypeDeclaration -> ReferenceTypeImpl.undeterminedParameters(typeDecl)
                            else -> {
                                TODO()
                            }
                        }
                    }
                    else -> TODO()
                }
            }
            is MethodCallExpr -> {
                val scopeType = if (expression.hasScope()) {
                    calculateTypeInternal(expression.scope.get())
                } else {
                    getTypeOfThisIn(expression, true)
                }
                val typeArgs = expression.typeArguments
                    .map { taNode -> taNode.map { toResolvedType<ResolvedType>(it) } }
                    .getOrNull()
                val argTypes = expression.arguments.map { calculateTypeInternal(it) }

                val resolvedDecl = runCatching {
                    resolveDeclaration<ResolvedDeclaration>(expression)
                }.getOrNull()

                if (resolvedDecl != null) {
                    return when (resolvedDecl) {
                        is ResolvedAnnotationMemberDeclaration -> {
                            solveTpInClassContext(resolvedDecl.type, scopeType.asReferenceTypeOrNull(), exprContainer)
                        }
                        is ResolvedMethodDeclaration -> {
                            when {
                                resolvedDecl.qualifiedName == "java.lang.Object.getClass" -> {
                                    // Handle the special case of Object.getClass, where its API return type is Class<?>
                                    // but the actual type is specified as follows:
                                    //
                                    // The actual result type is Class<? extends |X|> where |X| is the erasure of the
                                    // static type of the expression on which getClass is called.
                                    createResolvedRefType(
                                        resolvedDecl.returnType.asReferenceType().toResolvedTypeDeclaration(),
                                        listOf(ResolvedWildcard.extendsBound(rawifyType(scopeType, typeSolver)))
                                    )
                                }

                                else -> {
                                    solveTpInMethodContext(
                                        resolvedDecl,
                                        scopeType.asReferenceTypeOrNull(),
                                        argTypes,
                                        typeArgs,
                                        exprContainer
                                    )
                                }
                            }
                        }
                        else -> {
                            TODO()
                        }
                    }
                }

                LOGGER.warn(
                    "Unable to find declaration for MethodCallExpr `$expression` (scopeType=`${scopeType.describe()}` name=`${expression.nameAsString}` typeArgs=${
                        typeArgs.orEmpty().joinToString { it.describe() }
                    } argTypes=${argTypes.joinToString { it.describe() }}) "
                )
                solveTpInClassContext(super.calculateType(expression), scopeType.asReferenceTypeOrNull(), exprContainer)
            }
            is NameExpr -> {
                val resolvedDecl = runCatching {
                    resolveDeclaration<ResolvedDeclaration>(expression)
                }.getOrNull()

                when (resolvedDecl) {
                    is ResolvedValueDeclaration -> {
                        // Workaround JavaParser misidentifying the type of NameExpr if there are clashing type names?
                        val varDeclSite = when (resolvedDecl) {
                            is ResolvedFieldDeclaration -> {
                                resolvedDecl.toTypedAstOrNull<FieldDeclaration>(null)
                                    ?.let { fieldDecl ->
                                        fieldDecl.commonType to fieldDecl.variables.single { it.nameAsString == expression.nameAsString }
                                    }
                            }
                            is ResolvedParameterDeclaration -> {
                                resolvedDecl.toTypedAstOrNull<Parameter>(null)
                                    ?.let { it.type to it }
                            }
                            else -> {
                                resolvedDecl.toTypedAstOrNull<VariableDeclarationExpr>(null)
                                    ?.let { varDecl ->
                                        varDecl.commonType to varDecl.variables.single { it.nameAsString == expression.nameAsString }
                                    }
                            }
                        }

                        if (varDeclSite != null) {
                            val (astType, nodeContext) = varDeclSite
                            if (astType is ClassOrInterfaceType) {
                                val resolvedTypeDecl = solveNameAsType(astType, nodeContext)
                                if (resolvedTypeDecl != null) {
                                    val typeArgs = astType.typeArguments
                                        .map { nodeList -> nodeList.map { toResolvedType<ResolvedType>(it) } }
                                        .orElse(emptyList())
                                    val solvedRefType = ReferenceTypeImpl(resolvedTypeDecl, typeArgs)

                                    return if (nodeContext is Parameter && nodeContext.isVarArgs) {
                                        ResolvedArrayType(solvedRefType, 1)
                                    } else {
                                        solvedRefType
                                    }
                                }
                            }
                        }

                        val resolvedType = runCatching { resolvedDecl.type }.getOrNull()
                        if (resolvedType != null) {
                            return solveTpInClassContext(resolvedType, null, exprContainer)
                        }
                    }
                    is ResolvedTypeDeclaration -> {
                        return when (val typeDecl = resolvedDecl.asType()) {
                            is ResolvedReferenceTypeDeclaration -> {
                                ReferenceTypeImpl.undeterminedParameters(typeDecl)
                            }
                            else -> {
                                TODO()
                            }
                        }
                    }
                    null -> {
                        // Cannot resolve declaration - Fall back to super implementation and throw UnsolvedSymbolException
                    }
                    else -> TODO()
                }

                super.calculateType(expression)
            }
            is ArrayAccessExpr -> {
                // Solve the concrete type of the base, then append
                val nameType = calculateTypeInternal(expression.name)

                check(nameType.isArray)

                nameType.asArrayType().componentType
            }
            is ThisExpr, is SuperExpr -> {
                // Allow generic types to be present after solving
                super.calculateType(expression)
            }
            is ObjectCreationExpr -> {
                // Allow type params to be present for raw types
                runCatching {
                    super.calculateType(expression)
                }.recoverCatching {
                    val resolvedTypeDecl = resolveDeclaration<ResolvedConstructorDeclaration>(expression)
                        .declaringType()

                    if (resolvedTypeDecl.typeParameters.isEmpty()) {
                        createResolvedRefType(resolvedTypeDecl)
                    } else {
                        TODO("Unimplemented handling of type parameters in constructor")
                    }
                }.getOrThrow()
            }
            is CastExpr -> {
                // Take the target type of the cast as the calculated type
                toResolvedType(expression.type)
            }
            is ArrayCreationExpr -> {
                // No additional handling required
                super.calculateType(expression)
            }
            is ConditionalExpr -> {
                // Solve type of each branch individually, then unionize for resolution later
                listOf(
                    calculateTypeInternal(expression.thenExpr),
                    calculateTypeInternal(expression.elseExpr)
                ).distinct().unionTypes()
            }
            is ArrayInitializerExpr -> {
                // Solve the type of each array element individually, then unionize for resolution later
                expression.values.map { calculateTypeInternal(it) }.distinct().unionTypes()
            }
            is AssignExpr -> {
                // Solve the type of the target expression
                calculateTypeInternal(expression.target)
            }
            else -> {
                LOGGER.warn("No fallback calculateType implementation for expression `$expression` (${expression::class.simpleName})")
                super.calculateType(expression)
            }
        }
    }

    /**
     * Implementation for calculating the type of an expression.
     */
    internal fun calculateTypeInternal(expression: Expression): ResolvedType {
        injectSolverIfNotPresent(expression)

        if (expression.isTypeResolved) {
            return expression.resolvedType
        }

        val scopeType by lazy(LazyThreadSafetyMode.NONE) {
            (expression as? NodeWithTraversableScope)
                ?.traverseScope()
                ?.mapNotNull {
                    try {
                        calculateType(it).asReferenceTypeOrNull()
                    } catch (tr: Throwable) {
                        if (expression !is FieldAccessExpr) {
                            throw tr
                        }

                        null
                    }
                }
                ?.getOrNull()
        }
        val exprContainer = expression.parentResolvedContainer

        val defaultEx = runCatching {
            if (expression.isMethodCallExpr) {
                val resolvedMethod = runCatching {
                    resolveDeclaration<ResolvedMethodLikeDeclaration>(expression.asMethodCallExpr())
                }.getOrNull()

                if (resolvedMethod?.parameters?.any { it.isVariadic } == true) {
                    throw RuntimeException("Possibly buggy resolution of method call type with variadic parameters")
                }
            }

            val exprType = solveTpInClassContext(super.calculateType(expression), scopeType, exprContainer)

            if (GenericsSolver.getNestedGenerics(exprType).isEmpty()) {
                expression.resolvedType = exprType
                return exprType
            }
        }.exceptionOrNull()

        val fallbackType = when (expression) {
            is BinaryExpr -> calculateBinaryExprTypeFallback(expression)
            is FieldAccessExpr -> {
                try {
                    calculateTypeFallback(expression)
                } catch (ex: Throwable) {
                    UnsolvedSymbolException(
                        "Unable to solve type for `$expression` (${expression::class.simpleName})",
                        ex
                    ).apply {
                        defaultEx?.also { addSuppressed(it) }
                    }.let{
                        throw it
                    }
                }
            }
            is MarkerAnnotationExpr -> {
                createResolvedRefType(resolveDeclaration(expression))
            }
            is MethodCallExpr -> {
                calculateTypeFallback(expression)
            }
            is NameExpr -> {
                try {
                    calculateTypeFallback(expression)
                } catch (ex: Throwable) {
                    UnsolvedSymbolException(
                        "Unable to solve type for `$expression` (${expression::class.simpleName})",
                        ex
                    ).apply {
                        defaultEx?.also { addSuppressed(it) }
                    }.let {
                        throw it
                    }
                }
            }
            is EnclosedExpr -> {
                calculateTypeFallback(expression.inner)
            }
            else -> calculateTypeFallback(expression)
        }.let { exprType ->
            solveTpInClassContext(exprType, scopeType, exprContainer)
        }
        expression.resolvedType = fallbackType

        return fallbackType
    }

    override fun calculateType(expression: Expression): ResolvedType =
        genericsSolver.normalizeType(calculateTypeInternal(expression))

    /**
     * @see JavaParserFacade.getTypeOfThisIn
     */
    fun getTypeOfThisIn(node: Node, flattenAnonTypes: Boolean = false): ResolvedType {
        val facadeSolvedType = facade.getTypeOfThisIn(node)
        if (!flattenAnonTypes) {
            return facadeSolvedType
        }
        if (!facadeSolvedType.isReferenceType) {
            return facadeSolvedType
        }
        val facadeSolvedRefTypeDecl = facadeSolvedType.asReferenceType().toResolvedTypeDeclaration()
        if (!facadeSolvedRefTypeDecl.isAnonymousClass) {
            return facadeSolvedType
        }

        if (facadeSolvedRefTypeDecl !is JavaParserAnonymousClassDeclaration) {
            return facadeSolvedType
        }

        val objCreationExpr = facadeSolvedRefTypeDecl.toTypedAst<ObjectCreationExpr>(null)
        val objCreationExprType = toResolvedType<ResolvedReferenceType>(objCreationExpr.type)

        checkNotNull(
            facadeSolvedType.asReferenceType()
                .directAncestors
                .singleOrNull { it.qualifiedName == objCreationExprType.qualifiedName }
        )

        return objCreationExprType
    }

    companion object {

        private val LOGGER = Logger<FuzzySymbolSolver>()

        private val RESOLVED_TYPE = object : DataKey<ResolvedType>() {}
        private val RESOLVED_DECL = object : DataKey<ResolvedDeclaration>() {}
    }
}