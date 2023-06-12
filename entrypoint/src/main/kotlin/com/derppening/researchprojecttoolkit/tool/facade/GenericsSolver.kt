package com.derppening.researchprojecttoolkit.tool.facade

import com.derppening.researchprojecttoolkit.tool.facade.typesolver.PartitionedTypeSolver
import com.derppening.researchprojecttoolkit.util.*
import com.github.javaparser.ast.body.BodyDeclaration
import com.github.javaparser.ast.body.Parameter
import com.github.javaparser.ast.expr.Expression
import com.github.javaparser.resolution.TypeSolver
import com.github.javaparser.resolution.declarations.*
import com.github.javaparser.resolution.logic.InferenceVariableType
import com.github.javaparser.resolution.model.typesystem.ReferenceTypeImpl
import com.github.javaparser.resolution.types.*
import com.github.javaparser.symbolsolver.resolution.typeinference.TypeHelper
import hk.ust.cse.castle.toolkit.jvm.util.RuntimeAssertions.unreachable
import kotlin.jvm.optionals.getOrNull

/**
 * A type solver which replaces generics type variables with wildcard types.
 */
class GenericsSolver(private val symbolSolver: FuzzySymbolSolver, private val typeSolver: PartitionedTypeSolver) {

    private val resolvedObjectType: ResolvedReferenceType
        get() = ResolvedObjectType(typeSolver)

    /**
     * Converts a generic [ResolvedTypeVariable] or [ResolvedWildcard] into a reference type if possible.
     */
    fun normalizeType(type: ResolvedType): ResolvedType = normalizeType(type, typeSolver)

    /**
     * Canonicalizes a type.
     *
     * Specifically:
     *
     * - Raw [createResolvedRefType] will be canonicalized to include type parameters.
     * - Wildcards will be canonicalized as according to [canonicalizeWildcard].
     */
    private fun canonicalizeType(type: ResolvedType): ResolvedType {
        return when {
            type is ResolvedIntersectionType ->
                ResolvedIntersectionType(type.elements.map { canonicalizeType(it) })
            type.isUnionType ->
                ResolvedUnionType(type.asUnionType().elements.map { canonicalizeType(it) })
            type.isReferenceType -> {
                val refType = type.asReferenceType()
                if (refType.typeParametersMap.isEmpty()) {
                    ReferenceTypeImpl.undeterminedParameters(refType.toResolvedTypeDeclaration())
                } else {
                    refType.transformTypeParameters { canonicalizeType(it) }
                }
            }
            type.isWildcard -> canonicalizeWildcard(type.asWildcard())
            else -> type
        }
    }

    /**
     * Canonicalizes a [ResolvedWildcard].
     *
     * Specifically:
     *
     * - `? extends ? extends T` is canonicalized into `? extends T` (by stripping the outer bound)
     * - `? extends ? super T` is canonicalized into `? extends Object`
     * - `? super ? extends T` is canonicalized into `? super T` (pessimization)
     * - `? super ? super T` is canonicalized into `? super T` (by stripping the outer bound)
     *
     * Note that only the structure of the wildcard will be changed. To simplify the wildcard instead, use
     * [simplifyWildcard].
     */
    private fun canonicalizeWildcard(wildcard: ResolvedWildcard): ResolvedWildcard {
        // Recursively canonicalize the bounded type
        return if (wildcard.isBounded) {
            val boundedType = wildcard.boundedType

            // If the canonicalized bounded type is also a wildcard, perform simplification on the type
            val newBoundedType = if (boundedType.isWildcard) {
                val canonicalizedBoundedWildcard = canonicalizeWildcard(boundedType.asWildcard())
                when {
                    wildcard.isExtends && canonicalizedBoundedWildcard.isExtends -> {
                        // `? extends ? extends T` => `? extends T`
                        canonicalizedBoundedWildcard.boundedType
                    }
                    wildcard.isExtends && canonicalizedBoundedWildcard.isSuper -> {
                        // `? extends ? super T` => `? extends Object`
                        resolvedObjectType
                    }
                    wildcard.isSuper && canonicalizedBoundedWildcard.isExtends -> {
                        // `? super ? extends T` => `? super T`
                        canonicalizedBoundedWildcard.boundedType
                    }
                    wildcard.isSuper && canonicalizedBoundedWildcard.isSuper -> {
                        // `? super ? super T` => `? super T`
                        canonicalizedBoundedWildcard.boundedType
                    }
                    else -> unreachable("Canonicalization of ${wildcard.describe()} failed")
                }
            } else if (boundedType.isReferenceType && !boundedType.asReferenceType().typeParametersMap().isEmpty) {
                boundedType.asReferenceType()
                    .transformTypeParameters { if (it.isWildcard) canonicalizeWildcard(it.asWildcard()) else it }
            } else {
                boundedType
            }

            if (newBoundedType.isReferenceType && newBoundedType.asReferenceType().isJavaLangObject) {
                ResolvedWildcard.extendsBound(resolvedObjectType)
            } else if (wildcard.isExtends) {
                ResolvedWildcard.extendsBound(newBoundedType)
            } else {
                ResolvedWildcard.superBound(newBoundedType)
            }
        } else {
            ResolvedWildcard.extendsBound(resolvedObjectType)
        }
    }

    /**
     * Simplifies the bounds of a wildcard.
     *
     * - An effectively unbounded list of wildcards will be simplified into `? extends java.lang.Object`
     * - Otherwise, the bounds will be iteratively reduced based on the class hierarchy(s) of the extending class(es).
     */
    internal fun simplifyWildcard(wildcards: Collection<ResolvedWildcard>): ResolvedWildcard {
        val reducedWildcards = wildcards.map { canonicalizeWildcard(it) }
            .distinct()
            .filterNot { it.isEffectivelyUnbounded }
        when (reducedWildcards.size) {
            0 -> return canonicalizeWildcard(ResolvedWildcard.UNBOUNDED)
            1 -> return canonicalizeWildcard(reducedWildcards.single())
        }

        require(reducedWildcards.all { it.isExtends } || wildcards.all { it.isSuper })

        val isExtends = when {
            reducedWildcards.all { it.isExtends } -> true
            reducedWildcards.all { it.isSuper } -> false
            else -> TODO()
        }
        val flattenedBounds = reducedWildcards.flatMap {
            val boundedType = it.boundedType
            if (boundedType is ResolvedIntersectionType) {
                boundedType.elements
            } else if (boundedType.isUnionType) {
                TODO("Simplification of union types not implemented")
            } else {
                listOf(it.boundedType)
            }
        }.distinct()

        val simplifiedBounds = when {
            flattenedBounds.singleOrNull()?.isTypeVariable == true -> listOf(flattenedBounds.single())
            flattenedBounds.all { it.isReferenceType } -> {
                val flattenedRefBounds = flattenedBounds.map { it.asReferenceType() }
                if (flattenedRefBounds.size == 1) {
                    listOf(flattenedRefBounds.single())
                } else {
                    flattenedRefBounds.fold(emptyList<ResolvedReferenceType>()) { acc, it ->
                        if (acc.isEmpty()) {
                            listOf(it)
                        } else {
                            // If this type is a more-specific type of all previously collected bounds, use this instead
                            // For instance, given bounds <? extends Comparable<?> & String>, since String extends
                            // Comparable<?>, the bounds can be simplified to <? extends java.lang.String>
                            val isThisCommonTypeOfAllKnownBounds = if (isExtends) {
                                acc.all { knownBound -> knownBound.isAssignableBy(it) }
                            } else {
                                acc.all { knownBound -> it.isAssignableBy(knownBound) }
                            }

                            if (isThisCommonTypeOfAllKnownBounds) {
                                listOf(it)
                            } else {
                                // Otherwise, we iteratively go through each bound, and we simplify those which are
                                // more-specific
                                // For instance, given bounds <? extends Collection<?> & Set<?> & Comparable<?>>, since
                                // Set<?> extends Collection<?>, the Collection<?> bound is redundant, and the bounds
                                // can be simplified to <? extends Set<?> & Comparable<?>>
                                var appendType = true

                                val newAcc = acc.map { knownBound ->
                                    if (isExtends && isMoreSpecific(knownBound, it)) {
                                        appendType = false
                                        it
                                    } else if (!isExtends && isMoreSpecific(it, knownBound)) {
                                        appendType = false
                                        it
                                    } else {
                                        if (it.qualifiedName == knownBound.qualifiedName) {
                                            appendType = false
                                        }

                                        knownBound
                                    }
                                }.distinct()

                                newAcc + listOfNotNull(it.takeIf { appendType })
                            }
                        }
                    }
                }
            }
            flattenedBounds.any { it.isTypeVariable } -> {
                val (tv, nonTvBounds) = flattenedBounds.partition { it.isTypeVariable }
                val tvBounds = tv.flatMap { it.asTypeParameter().toWildcard(true).boundedTypes }

                nonTvBounds.fold(tv) { acc, nonTvType ->
                    if (nonTvType in tvBounds || tvBounds.any { isMoreSpecific(it, nonTvType) }) {
                        acc
                    } else if (nonTvType in acc || acc.any { isMoreSpecific(it, nonTvType) }) {
                        acc
                    } else {
                        acc + nonTvType
                    }
                }
            }
            else -> TODO("Simplifying wildcard for ${flattenedBounds.joinToString { it.describe() }} not implemented")
        }.let {
            when (it.size) {
                0 -> {
                    // Technically should not happen, but would be equivalent to <?>
                    resolvedObjectType
                }
                1 -> it.single()
                else -> ResolvedIntersectionType(it)
            }
        }

        return if (isExtends) {
            ResolvedWildcard.extendsBound(simplifiedBounds)
        } else {
            ResolvedWildcard.superBound(simplifiedBounds)
        }.let {
            canonicalizeWildcard(it)
        }
    }

    /**
     * Simplifies the bounds of a wildcard.
     *
     * @see simplifyWildcard
     */
    private fun simplifyWildcard(wildcard: ResolvedWildcard): ResolvedWildcard =
        simplifyWildcard(wildcard.boundedTypes.map {
            if (wildcard.isExtends)
                ResolvedWildcard.extendsBound(it)
            else
                ResolvedWildcard.superBound(it)
        })

    /**
     * Converts this into a [ResolvedWildcard].
     *
     * @param preserveNestedTp If `true`, replaces the nested type parameters of [this] with an unbounded wildcard.
     */
    private fun ResolvedTypeParameterDeclaration.toWildcard(preserveNestedTp: Boolean = false): ResolvedWildcard {
        check(bounds.all { it.isExtends } || bounds.all { it.isSuper })

        val boundedTypes = bounds.map { it.type }

        return when {
            isUnbounded -> ResolvedWildcard.UNBOUNDED
            bounds.all { it.isSuper } -> ResolvedIntersectionWildcard(boundedTypes, true)
            bounds.all { it.isExtends } -> ResolvedIntersectionWildcard(boundedTypes, false)
            else -> TODO()
        }.let {
            if (preserveNestedTp) {
                it
            } else {
                it.replaceTypeVariables(this, ResolvedWildcard.UNBOUNDED)
            }.asWildcard()
        }.let {
            canonicalizeWildcard(it)
        }
    }

    /**
     * Retrieves the [ResolvedWildcard] which represents a more constrained (and therefore specific) type.
     *
     * Note that an instance other than [a] or [b] may be returned, if the intersection of [a] and [b] returns a more
     * specific type which neither type can represent.
     */
    fun getMoreSpecific(a: ResolvedWildcard, b: ResolvedWildcard): ResolvedWildcard {
        return when {
            a.isEffectivelyUnbounded && b.isEffectivelyUnbounded -> canonicalizeWildcard(ResolvedWildcard.UNBOUNDED)
            a.isBounded && b.isEffectivelyUnbounded -> a
            a.isEffectivelyUnbounded && b.isBounded -> b
            else -> {
                val aBounds = a.boundedTypes
                val bBounds = b.boundedTypes

                check(a.isExtends == b.isExtends)

                val mergedBounds = aBounds + bBounds
                val boundedWildcards = mergedBounds.map {
                    if (a.isExtends) {
                        ResolvedWildcard.extendsBound(it)
                    } else {
                        ResolvedWildcard.superBound(it)
                    }
                }

                simplifyWildcard(boundedWildcards)
            }
        }
    }

    /**
     * Returns `true` if [lhs] is a more specific type than [rhs].
     */
    fun isMoreSpecific(lhs: ResolvedType, rhs: ResolvedType, swapped: Boolean = false): Boolean {
        return when {
            lhs.isNull -> false
            lhs.isPrimitive -> {
                if (!rhs.isPrimitive) {
                    false
                } else {
                    TODO()
                }
            }
            lhs.isReferenceType -> {
                val lhsType = lhs.asReferenceType()

                if (!rhs.isReferenceType) {
                    true
                } else {
                    val rhsType = rhs.asReferenceType()

                    if (lhsType.qualifiedName == rhsType.qualifiedName) {
                        when {
                            lhsType.isRawType && !rhsType.isRawType -> false
                            !lhsType.isRawType && rhsType.isRawType -> true
                            lhsType.typeParametersValues().zip(rhsType.typeParametersValues()).let {
                                it.any { (a, b) -> isMoreSpecific(a, b) } && it.none { (a, b) -> isMoreSpecific(b, a) }
                            } -> true
                            else -> false
                        }
                    } else {
                        when {
                            rhsType.qualifiedName in symbolSolver.symbolSolverCache.getAllAncestors(lhsType).map { it.qualifiedName } -> true
                            lhsType.qualifiedName in symbolSolver.symbolSolverCache.getAllAncestors(rhsType).map { it.qualifiedName } -> false
                            lhsType.typeParametersValues().zip(rhsType.typeParametersValues()).let {
                                it.any { (a, b) -> isMoreSpecific(a, b) } && it.none { (a, b) -> isMoreSpecific(b, a) }
                            } -> true
                            !lhsType.isAssignableBy(rhsType) && rhsType.isAssignableBy(lhsType) -> true
                            lhsType.isAssignableBy(rhsType) && !rhsType.isAssignableBy(lhsType) -> false
                            else -> {
                                // Neither is more specific than the other
                                false
                            }
                        }
                    }
                }
            }
            lhs.isArray -> {
                when {
                    rhs.isTypeVariable || rhs.isWildcard -> true
                    rhs.isArray || rhs.isReferenceType -> {
                        when {
                            !lhs.isAssignableBy(rhs) && rhs.isAssignableBy(lhs) -> true
                            lhs.isAssignableBy(rhs) && !rhs.isAssignableBy(lhs) -> false
                            else -> TODO()
                        }
                    }
                    else -> TODO()
                }
            }
            lhs.isTypeVariable -> {
                val lhsType = lhs.asTypeParameter()

                when {
                    rhs.isTypeVariable -> {
                        val rhsType = rhs.asTypeParameter()

                        when {
                            lhsType.isUnbounded && rhsType.isUnbounded -> false
                            lhsType.isBounded && rhsType.isUnbounded -> true
                            lhsType.isUnbounded && rhsType.isBounded -> false
                            else -> {
                                isMoreSpecific(lhsType.toWildcard(), rhsType.toWildcard())
                            }
                        }
                    }
                    rhs.isWildcard -> {
                        val lhsTypeAsWildcard = lhsType.toWildcard()
                        val rhsType = rhs.asWildcard()

                        // Type variables are more specific than wildcards if their bounds are the same
                        if (lhsTypeAsWildcard == rhsType) {
                            return true
                        }

                        isMoreSpecific(lhsTypeAsWildcard, rhsType)
                    }
                    else -> !isMoreSpecific(rhs, lhs, true)
                }
            }
            lhs.isWildcard -> {
                val lhsType = lhs.asWildcard()

                when {
                    rhs.isWildcard -> {
                        val rhsType = rhs.asWildcard()
                        when (getMoreSpecific(lhsType, rhsType)) {
                            lhsType -> true
                            rhsType -> false
                            else -> {
                                TODO("More Specific Resolution between ${lhsType.describe()} and ${rhsType.describe()} not implemented")
                            }
                        }
                    }
                    else -> !isMoreSpecific(rhs, lhs, true)
                }
            }
            else -> {
                if (!swapped) {
                    !isMoreSpecific(rhs, lhs, true)
                } else {
                    TODO()
                }
            }
        }
    }

    private fun getMoreSpecificSameRefType(a: ResolvedReferenceType, b: ResolvedReferenceType): ResolvedReferenceType {
        assert(a.qualifiedName == b.qualifiedName)

        return if (a.isRawType && !b.isRawType) {
            b
        } else if (!a.isRawType && b.isRawType) {
            a
        } else {
            a.transformTypeParametersIndexed { index, _ ->
                getMoreSpecific(a.typeParametersValues()[index], b.typeParametersValues()[index])
            }.asReferenceType()
        }
    }

    /**
     * Retrieves the [ResolvedType] which represents a more constrained (and therefore specific) type.
     *
     * Note that an instance other than [a] or [b] may be returned, if the intersection of [a] and [b] returns a more
     * specific type which neither type can represent.
     */
    internal fun getMoreSpecific(a: ResolvedType, b: ResolvedType): ResolvedType {
        return when {
            a == b -> a
            a.isReferenceType && b.isReferenceType && a.asReferenceType().qualifiedName == b.asReferenceType().qualifiedName -> {
                getMoreSpecificSameRefType(a.asReferenceType(), b.asReferenceType())
            }

            a.isWildcard && b.isWildcard -> getMoreSpecific(a.asWildcard(), b.asWildcard())
            isMoreSpecific(a, b) && !isMoreSpecific(b, a) -> a
            !isMoreSpecific(a, b) && isMoreSpecific(b, a) -> b
            !isMoreSpecific(a, b) && !isMoreSpecific(b, a) -> {
                when {
                    a.isTypeVariable && b.isWildcard -> a
                    a.isWildcard && b.isTypeVariable -> b
                    a.isReferenceType && b.isReferenceType -> {
                        val aRef = a.asReferenceType()
                        val bRef = b.asReferenceType()

                        if (aRef.qualifiedName != bRef.qualifiedName) {
                            TypeHelper.leastUpperBound(setOf(aRef, bRef))
                        } else {
                            getMoreSpecificSameRefType(aRef, bRef)
                        }
                    }
                    a.isTypeVariable && b.isTypeVariable -> {
                        val aTv = a.asTypeParameter()
                        val bTv = b.asTypeParameter()
                        val aTvScopes = aTv.container.getTpScopes().map { it.qualifiedId }.toSet()
                        val bTvScopes = bTv.container.getTpScopes().map { it.qualifiedId }.toSet()

                        if ((aTvScopes union bTvScopes).size > maxOf(aTvScopes.size, bTvScopes.size)) {
                            TODO()
                        }

                        when {
                            (bTvScopes - aTvScopes).isNotEmpty() -> b
                            (aTvScopes - bTvScopes).isNotEmpty() -> a
                            else -> TODO()
                        }
                    }
                    else -> {
                        TODO("Neither a or b are more specific than each other (a=${a.describe()} b=${b.describe()})")
                    }
                }
            }
            isMoreSpecific(a, b) && isMoreSpecific(b, a) -> {
                unreachable("Impossible Case: Both a and b are more specific than each other (a=${a.describe()} b=${b.describe()})")
            }
            else -> {
                unreachable()
            }
        }
    }

    /**
     * Propagates the bounds of all type parameters of a [createResolvedRefType] to the type itself. This method will
     * choose the bounds which are more specific between the bounds declared by the type parameter and the actual type
     * of the type variable.
     */
    private fun propagateTypeDeclaredBoundsToType(resolvedType: ResolvedReferenceType): ResolvedReferenceType {
        return if (resolvedType.isReferenceType) {
            val refType = resolvedType.asReferenceType()

            val newTp = refType.typeParametersMap
                .map { (tpDecl, tpType) ->
                    val newType = if (tpType.isWildcard) {
                        val moreSpecificWildcard = getMoreSpecific(tpDecl.toWildcard(), tpType.asWildcard())
                        moreSpecificWildcard.replaceTypeVariables(tpDecl, tpType)
                    } else tpType

                    if (newType.isWildcard) {
                        simplifyWildcard(newType.asWildcard())
                    } else newType
                }

            ReferenceTypeImpl(refType.toResolvedTypeDeclaration(), newTp)
        } else resolvedType
    }

    /**
     * Recursively expands the bounds declared in a type parameter.
     */
    private fun expandTypeParamDeclaredBounds(resolvedType: ResolvedType): ResolvedType {
        val typeVarsInType = getNestedTypeVariables(resolvedType)
        val typeVarReplacements = typeVarsInType.map {
            val tp = it.asTypeParameter()
            val wildcardTp = tp.toWildcard()
            val wildcardContainsTp = when (resolvedType) {
                is ResolvedIntersectionType -> {
                    resolvedType.elements.any { it.mention(listOf(tp)) }
                }
                else -> it.mention(listOf(tp))
            }

            it to (wildcardTp.takeUnless { wildcardContainsTp } ?: it)
        }

        return typeVarReplacements.fold(resolvedType) { acc, (tv, replaceType) ->
            acc.replaceTypeVariables(tv.asTypeParameter(), replaceType)
        }
    }

    /**
     * The qualified ID of a [ResolvedTypeParametrizable].
     *
     * This will be the qualified name for a type, or the qualified signature of a method.
     */
    private val ResolvedTypeParametrizable.qualifiedId: String
        get() = when (this) {
            is ResolvedReferenceTypeDeclaration -> this.qualifiedName
            is ResolvedMethodLikeDeclaration -> {
                runCatching {
                    this.qualifiedSignature
                }.recoverCatching {
                    val typeId = declaringType().id
                    val signature = buildString {
                        append(name)
                        append("(")
                        for (i in 0 until numberOfParams) {
                            if (i != 0) append(", ")

                            runCatching {
                                getParam(i).describeType()
                            }.recoverCatching {
                                getParam(i).toTypedAst<Parameter>(null)
                                    .type
                                    .let { symbolSolver.toResolvedType<ResolvedType>(it) }
                            }.getOrThrow()
                        }
                        append(")")
                    }

                    "$typeId.$signature"
                }.getOrThrow()
            }
            else -> TODO()
        }

    /**
     * Given a type parameter used in this scope, returns the locations (classes or methods) which this type parameter
     * may be declared in.
     */
    private fun ResolvedTypeParametrizable.getTpScopes(): Collection<ResolvedTypeParametrizable> {
        return when (this) {
            is ResolvedMethodLikeDeclaration -> {
                listOf(this) + declaringType().getTpScopes()
            }
            is ResolvedReferenceTypeDeclaration -> {
                val astDecl = toAst().getOrNull()
                val parentTpScopes = astDecl
                    ?.let {
                        when (it) {
                            is Expression -> it.parentContainer
                            is BodyDeclaration<*> -> it.parentContainer
                            else -> null
                        }
                    }
                    ?.let { symbolSolver.resolveDeclaration<ResolvedDeclaration>(it) }
                    ?.let { it as? ResolvedTypeParametrizable }
                    ?.getTpScopes()
                    .orEmpty()

                listOf(this) + parentTpScopes
            }
            else -> {
                TODO()
            }
        }
    }

    /**
     * Replaces all type parameters in [resolvedType] which is not declared in this or any parent container with
     * wildcards.
     */
    private fun replaceNonContainerGenerics(
        resolvedType: ResolvedType,
        container: ResolvedTypeParametrizable
    ): ResolvedType {
        val containers = container.getTpScopes()

        return replaceNonContainerGenerics(resolvedType, containers)
    }

    /**
     * Replaces all type parameters in [resolvedType] which is not declared in any container of [containers] with
     * wildcards.
     */
    private fun replaceNonContainerGenerics(
        resolvedType: ResolvedType,
        containers: Collection<ResolvedTypeParametrizable>
    ): ResolvedType {
        return when {
            resolvedType.isTypeVariable -> {
                val tv = resolvedType.asTypeVariable()
                if (containers.none { it.qualifiedId == tv.asTypeParameter().containerQualifiedName }) {
                    tv.asTypeParameter().toWildcard()
                } else tv
            }
            resolvedType.isWildcard -> {
                val wildcard = resolvedType.asWildcard()
                when {
                    wildcard.isExtends -> ResolvedWildcard.extendsBound(
                        replaceNonContainerGenerics(wildcard.boundedType, containers)
                    )
                    wildcard.isSuper -> ResolvedWildcard.superBound(
                        replaceNonContainerGenerics(wildcard.boundedType, containers)
                    )
                    else -> resolvedType
                }
            }
            resolvedType.isReferenceType -> {
                canonicalizeType(resolvedType)
                    .asReferenceType()
                    .transformTypeParameters { replaceNonContainerGenerics(it, containers) }
            }
            resolvedType is ResolvedIntersectionType -> {
                ResolvedIntersectionType(resolvedType.elements.map { replaceNonContainerGenerics(it, containers) })
            }
            resolvedType.isUnionType -> {
                val unionType = resolvedType.asUnionType()
                ResolvedIntersectionType(unionType.elements.map { replaceNonContainerGenerics(it, containers) })
            }
            else -> resolvedType
        }
    }

    /**
     * Solves the type parameter in class context.
     *
     * @param retType The calculated return type.
     * @param scopeType The scope of the return type, for instance the type of the scope expression of a field access
     * expression.
     * @param exprContainer The container which the type is used in.
     */
    fun solveTpInClassContext(
        retType: ResolvedType,
        scopeType: ResolvedReferenceType?,
        exprContainer: ResolvedTypeParametrizable
    ): ResolvedType {
        if (retType.isArray) {
            return ResolvedArrayType(
                solveTpInClassContext(
                    retType.asArrayType().baseComponentType(),
                    scopeType,
                    exprContainer
                ),
                retType.arrayLevel()
            )
        }

        val newTypeByScopeTpReplacement = if (scopeType != null) {
            propagateTypeDeclaredBoundsToType(scopeType)
                .useThisTypeParametersOnTheGivenType(retType)
        } else retType

        val newTypeByNestedTpReplacement = expandTypeParamDeclaredBounds(retType)

        val collectedTypes = listOf(
            retType,
            newTypeByScopeTpReplacement,
            newTypeByNestedTpReplacement
        ).map {
            replaceNonContainerGenerics(it, exprContainer)
        }.toSortedSet(RESOLVED_TYPE_COMPARATOR)
        val newType = collectedTypes.reduce { acc, it -> getMoreSpecific(acc, it) }

        return canonicalizeType(newType)
    }

    /**
     * find all types which a type variable may take on from a method parameter/argument pair.
     *
     * @param tv type variable to infer types from.
     * @param paramType the parameter type.
     * @param argType the argument type.
     * @param exprContainer the location which the type variable is declared in.
     */
    private fun findInferredTpTypes(
        tv: ResolvedTypeVariable,
        paramType: ResolvedType,
        argType: ResolvedType,
        exprContainer: ResolvedTypeParametrizable
    ): Collection<ResolvedType> {
        when {
            paramType == tv -> return listOf(argType)
            paramType.isArray -> {
                val paramArray = paramType.asArrayType()
                val paramComponentType = paramArray.componentType

                return if (argType.isArray) {
                    findInferredTpTypes(tv, paramComponentType, argType.asArrayType().componentType, exprContainer)
                } else if (paramComponentType.isTypeVariable && argType.isPrimitive) {
                    val argPrimitiveType = argType.asPrimitive()

                    findInferredTpTypes(tv, paramComponentType, TypeHelper.toBoxedType(argPrimitiveType, typeSolver), exprContainer)
                } else if (argType.isReference) {
                    findInferredTpTypes(tv, paramComponentType, argType, exprContainer)
                } else {
                    TODO()
                }
            }
            // Below isArray check, because ResolvedArrayType does not support mention operation
            !paramType.mention(listOf(tv.asTypeParameter())) -> return emptyList()
            paramType.isWildcard -> {
                val paramWildcard = paramType.asWildcard()
                return if (paramWildcard.isEffectivelyUnbounded) {
                    listOf(canonicalizeWildcard(ResolvedWildcard.UNBOUNDED))
                } else if (paramWildcard.isSuper) {
                    if (argType.isWildcard) {
                        val argWildcard = argType.asWildcard()

                        findInferredTpTypes(tv, paramWildcard.boundedType, argWildcard.boundedType, exprContainer)
                    } else {
                        listOf(argType)
                    }
                } else {
                    if (argType.isWildcard) {
                        val argWildcard = argType.asWildcard()

                        if (argWildcard.isExtends) {
                            findInferredTpTypes(tv, paramWildcard.boundedType, argWildcard.boundedType, exprContainer)
                        } else {
                            TODO()
                        }
                    } else {
                        findInferredTpTypes(tv, paramWildcard.boundedType, argType, exprContainer)
                    }
                }
            }
        }

        when {
            argType.isTypeVariable -> {
                val argTp = argType.asTypeParameter()
                return findInferredTpTypes(tv, paramType, argTp.toWildcard(), exprContainer)
            }
            argType.isWildcard -> {
                val argWildcard = argType.asWildcard()
                return if (argWildcard.isEffectivelyUnbounded) {
                    listOf(canonicalizeWildcard(ResolvedWildcard.UNBOUNDED))
                } else if (argWildcard.isExtends) {
                    findInferredTpTypes(tv, paramType, argWildcard.boundedType, exprContainer)
                } else {
                    TODO()
                }
            }
            argType.isArray -> {
                val argArray = argType.asArrayType()
                val argComponentType = argArray.componentType
                return if (argComponentType.isReferenceType && argComponentType.asReferenceType().qualifiedName == paramType.asReferenceType().qualifiedName) {
                    findInferredTpTypes(tv, paramType, argComponentType, exprContainer)
                } else {
                    TODO()
                }
            }
        }

        check(paramType.isReferenceType) {
            "Expected paramType to be reference type, got `${paramType.describe()}` (${paramType::class.simpleName})"
        }

        val paramRefType = paramType.asReferenceType()
        val argRefType = when {
            argType.isReferenceType -> argType.asReferenceType()
            argType.isNull -> return emptyList()
            else -> TODO()
        }

        if (!paramRefType.isReferenceType || !argRefType.isReferenceType) {
            TODO()
        }

        return if (paramRefType.typeParametersMap.any { it.b == tv }) {
            val paramTps = paramRefType.typeParametersMap
                .filter { (_, tpType) ->
                    tpType.mention(listOf(tv.asTypeParameter()))
                }
            paramTps.mapNotNull { (tpDecl, _) ->
                argRefType.typeParamValue(tpDecl).getOrNull()
            }
        } else {
            paramRefType.typeParametersValues().zip(argRefType.typeParametersValues()).flatMap { (p, a) ->
                findInferredTpTypes(tv, p, a, exprContainer)
            }
        }
    }

    /**
     * Replaces all type variables within [retType] by using [argTypes] to infer the concrete values of type parameters
     * of [methodDecl].
     */
    private fun replaceTpCommonTypeByArgType(
        retType: ResolvedType,
        methodDecl: ResolvedMethodDeclaration,
        argTypes: List<ResolvedType>,
        exprContainer: ResolvedTypeParametrizable
    ): ResolvedType = replaceTpCommonTypeByArgType(
        getNestedTypeVariables(retType).filter { it.asTypeParameter().declaredOnMethod() },
        retType,
        methodDecl,
        argTypes,
        exprContainer
    )

    /**
     * Replaces all type variables in [tpRefByRetType] by using [argTypes] to infer the concrete values of type
     * parameters of [methodDecl].
     */
    private fun replaceTpCommonTypeByArgType(
        tpRefByRetType: List<ResolvedTypeVariable>,
        retType: ResolvedType,
        methodDecl: ResolvedMethodDeclaration,
        argTypes: List<ResolvedType>,
        exprContainer: ResolvedTypeParametrizable
    ): ResolvedType {
        if (tpRefByRetType.isEmpty()) {
            return retType
        }

        // Type of parameter/argument with any type variable referenced by return type
        val relevantTypes = tpRefByRetType
            .associateWith {
                val paramsIdx = methodDecl.parameters.withIndex()
                    .filter { it.value.type.isReference }
                    .filter {
                        it.value.type.mention(tpRefByRetType.map { it.asTypeParameter() })
                    }
                    .map { it.index }
                val params = methodDecl.parameters
                    .filterIndexed { idx, _ -> idx in paramsIdx }
                    .map { it.type }
                val args = argTypes.filterIndexed { idx, _ -> idx in paramsIdx }

                params.zip(args)
            }
            .filterValues { it.isNotEmpty() }

        val replacementType = relevantTypes
            .entries
            .fold(retType) { newRetType, (tv, tvTypes) ->
                val inferredTvTypes = tvTypes
                    .flatMap { (paramType, argType) ->
                        findInferredTpTypes(tv, paramType, argType, exprContainer)
                    }
                    .filterNot { it.isWildcard && it.asWildcard().isEffectivelyUnbounded }
                    .distinct()

                val commonType = when (inferredTvTypes.size) {
                    0 -> null
                    1 -> inferredTvTypes.single()
                    else -> inferredTvTypes.reduce { acc, it -> getMoreSpecific(acc, it) }
                }

                if (commonType != null) {
                    newRetType.replaceTypeVariables(tv.asTypeParameter(), commonType)
                } else {
                    newRetType
                }
            }

        return if (replacementType.isTypeVariable) {
            // Is any bounded type inferable from argument?
            val replacementTp = replacementType.asTypeParameter()

            when (replacementTp.bounds.size) {
                0 -> replacementType
                1 -> {
                    if (replacementTp.hasUpperBound()) {
                        TODO()
                    }

                    val lowerBound = replacementTp.lowerBound
                    if (lowerBound.isTypeVariable) {
                        // S extends T extends U -> S extends U
                        replaceTpCommonTypeByArgType(lowerBound.asTypeVariable(), methodDecl, argTypes, exprContainer)
                    } else {
                        replacementType
                    }
                }
                else -> {
                    check(replacementTp.bounds.all { it.isExtends } || replacementTp.bounds.all { it.isSuper })

                    ResolvedIntersectionWildcard(replacementTp.bounds.map { it.type }, replacementTp.hasUpperBound())
                }
            }
        } else {
            replacementType
        }
    }

    /**
     * Solves the type parameter in method context.
     *
     * @param methodDecl The method declaration to solve.
     * @param scopeType The scope of the return type, for instance the type of the scope expression of a field access
     * expression.
     * @param argTypes The calculated types of arguments.
     * @param typeArgs The explicitly-provided list of type arguments.
     * @param exprContainer The container which the type is used in.
     */
    fun solveTpInMethodContext(
        methodDecl: ResolvedMethodDeclaration,
        scopeType: ResolvedReferenceType?,
        argTypes: List<ResolvedType>,
        typeArgs: List<ResolvedType>?,
        exprContainer: ResolvedTypeParametrizable
    ): ResolvedType =
        solveTpInMethodContext(methodDecl.returnType, scopeType, methodDecl, argTypes, typeArgs, exprContainer)

    /**
     * Solves the type parameter in method context.
     *
     * @param retType The type of the return value.
     * @param scopeType The scope of the return type, for instance the type of the scope expression of a field access
     * expression.
     * @param methodDecl The method declaration to solve.
     * @param argTypes The calculated types of arguments.
     * @param typeArgs The explicitly-provided list of type arguments.
     * @param exprContainer The container which the type is used in.
     */
    private fun solveTpInMethodContext(
        retType: ResolvedType,
        scopeType: ResolvedReferenceType?,
        methodDecl: ResolvedMethodDeclaration,
        argTypes: List<ResolvedType>,
        typeArgs: List<ResolvedType>?,
        exprContainer: ResolvedTypeParametrizable
    ): ResolvedType {
        if (retType.isArray) {
            return ResolvedArrayType(
                solveTpInMethodContext(
                    retType.asArrayType().baseComponentType(),
                    scopeType,
                    methodDecl,
                    argTypes,
                    typeArgs,
                    exprContainer
                ),
                retType.arrayLevel()
            )
        }

        val newTypeByMethodTpReplacement = if (typeArgs != null) {
            val tpToTypeArg = methodDecl.typeParameters
                .zip(typeArgs)

            tpToTypeArg.fold(retType) { acc, (tpDecl, tpType) ->
                acc.replaceTypeVariables(tpDecl, tpType)
            }
        } else retType

        val newTypeByScopeTpReplacement = if (scopeType != null) {
            propagateTypeDeclaredBoundsToType(scopeType)
                .useThisTypeParametersOnTheGivenType(retType)
        } else retType

        val newTypeByArgTypeInference = replaceTpCommonTypeByArgType(retType, methodDecl, argTypes, exprContainer)

        val newTypeByNestedTpReplacement = expandTypeParamDeclaredBounds(retType)

        val collectedTypes = listOf(
            retType,
            newTypeByMethodTpReplacement,
            newTypeByScopeTpReplacement,
            newTypeByArgTypeInference,
            newTypeByNestedTpReplacement
        ).map {
            replaceNonContainerGenerics(it, exprContainer)
        }.toSortedSet(RESOLVED_TYPE_COMPARATOR)
        val newType = collectedTypes.reduce { acc, it -> getMoreSpecific(acc, it) }

        return canonicalizeType(newType)
    }

    companion object {

        private val LOGGER = Logger<GenericsSolver>()

        /**
         * Converts a generic [ResolvedTypeVariable] or [ResolvedWildcard] into a reference type if possible.
         */
        fun normalizeType(type: ResolvedType, typeSolver: TypeSolver): ResolvedType {
            return when {
                type.isArray -> type.asArrayType().let {
                    ResolvedArrayType(normalizeType(it.baseComponentType(), typeSolver), it.arrayLevel())
                }
                type.isTypeVariable -> {
                    val tv = type.asTypeParameter()
                    when {
                        tv.hasLowerBound() && tv.bounds.size == 1 -> normalizeType(tv.lowerBound, typeSolver)
                        tv.isUnbounded -> ResolvedObjectType(typeSolver)
                        else -> type
                    }
                }
                type.isWildcard -> {
                    val wildcard = type.asWildcard()
                    when {
                        wildcard.isExtends -> normalizeType(wildcard.boundedType, typeSolver)
                        !wildcard.isBounded -> ResolvedObjectType(typeSolver)
                        else -> wildcard
                    }
                }
                else -> type
            }
        }

        /**
         * Returns all nested generic types within [type].
         */
        fun getNestedGenerics(type: ResolvedType): List<ResolvedType> {
            return when {
                type.isTypeVariable -> listOf(type.asTypeVariable())
                type.isPrimitive || type.isVoid || type.isNull -> emptyList()
                type.isArray -> getNestedGenerics(type.asArrayType().baseComponentType())
                type is ResolvedIntersectionType -> type.elements.flatMap { getNestedGenerics(it) }
                type.isUnionType -> type.asUnionType().elements.flatMap { getNestedGenerics(it) }
                type.isWildcard -> listOf(type.asWildcard())
                type.isReferenceType -> {
                    type.asReferenceType().let {
                        it.typeParametersValues().flatMap { getNestedGenerics(it) }
                    }
                }
                else -> error("Don't know how to get type variables in ${type::class.simpleName}")
            }
        }

        /**
         * Returns all nested type variables within [type].
         */
        fun getNestedTypeVariables(type: ResolvedType): List<ResolvedTypeVariable> {
            return when {
                type.isTypeVariable -> listOf(type.asTypeVariable())
                type.isPrimitive || type.isVoid || type.isNull -> emptyList()
                type.isArray -> getNestedTypeVariables(type.asArrayType().baseComponentType())
                type is ResolvedIntersectionType -> type.elements.flatMap { getNestedTypeVariables(it) }
                type.isUnionType -> type.asUnionType().elements.flatMap { getNestedTypeVariables(it) }
                type.isWildcard -> type.asWildcard()
                    .takeIf { it.isBounded }
                    ?.boundedType
                    ?.let {
                        getNestedTypeVariables(it)
                    }.orEmpty()
                type.isReferenceType -> {
                    type.asReferenceType().let {
                        it.typeParametersValues().flatMap { getNestedTypeVariables(it) }
                    }
                }
                type is InferenceVariableType -> emptyList()
                else -> error("Don't know how to get type variables in ${type::class.simpleName}")
            }
        }
    }
}