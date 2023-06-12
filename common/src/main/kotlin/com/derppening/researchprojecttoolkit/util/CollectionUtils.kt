package com.derppening.researchprojecttoolkit.util

import org.apache.commons.math3.util.CombinatoricsUtils
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.experimental.ExperimentalTypeInference

/**
 * Builds a [Collection] by populating [initial] using the given [builderAction], and converting into the final type by
 * [finalize].
 */
@OptIn(ExperimentalContracts::class, ExperimentalTypeInference::class)
inline fun <E, C : MutableCollection<E>, R : Collection<E>> buildCollection(
    initial: C,
    finalize: (C) -> R,
    @BuilderInference builderAction: C.() -> Unit
): R {
    contract {
        callsInPlace(builderAction, InvocationKind.EXACTLY_ONCE)
    }

    return finalize(initial.apply(builderAction))
}

/**
 * Builds a [MutableCollection] by populating [initial] using the given [builderAction].
 */
@OptIn(ExperimentalContracts::class, ExperimentalTypeInference::class)
inline fun <E, C : MutableCollection<E>> buildCollection(
    initial: C,
    @BuilderInference builderAction: C.() -> Unit
): C {
    contract {
        callsInPlace(builderAction, InvocationKind.EXACTLY_ONCE)
    }

    return buildCollection(initial, { it }, builderAction)
}

/**
 * Same as [dropWhile], but continues to drop first elements while the predicate evaluates to `false`.
 */
fun <T> Iterable<T>.dropWhileAndIncluding(predicate: (T) -> Boolean): List<T> =
    this.dropWhile { predicate(it) }
        .dropWhile { !predicate(it) }

/**
 * Same as [dropLastWhile], but continues to drop last elements while the predicate evaluates to `false`.
 */
fun <T> List<T>.dropLastWhileAndIncluding(predicate: (T) -> Boolean): List<T> =
    this.dropLastWhile { predicate(it) }
        .dropLastWhile { !predicate(it) }

/**
 * Zips all combinations of elements present in this [List], returning a [List] of [Pair]s of elements.
 *
 * @param transform Transformation function for ordering each pair
 */
private fun <E> List<E>.zipCombinations(transform: (Pair<E, E>) -> Pair<E, E>): List<Pair<E, E>> {
    val list = ArrayList<Pair<E, E>>(CombinatoricsUtils.factorial(this.size).toInt())
    for (i in this.indices) {
        for (j in (i + 1) until this.size) {
            list.add(transform(this[i] to this[j]))
        }
    }

    return list.toList()
}

/**
 * Zips all combinations of elements present in this [List], returning a [List] of [Pair]s of elements.
 *
 * The pair will be ordered based on the order of appearance in the list, i.e. elements which appear earlier in the list
 * will be assigned to [Pair.first].
 */
fun <E> List<E>.zipCombinations(): List<Pair<E, E>> = zipCombinations { it }

/**
 * Zips all combinations of elements present in this [List], returning a [List] of [Pair]s of elements.
 *
 * The pair will be ordered using the given [comparator].
 */
fun <E> List<E>.zipCombinationsOrdered(comparator: Comparator<E>): List<Pair<E, E>> =
    zipCombinations { (lhs, rhs) ->
        if (comparator.compare(lhs, rhs) <= 0) {
            lhs to rhs
        } else {
            rhs to lhs
        }
    }

/**
 * Zips all combinations of elements present in this [List], returning a [List] of [Pair]s of elements.
 *
 * The pair will be ordered using a comparator created for the given type [R].
 */
fun <E, R : Comparable<R>> List<E>.zipCombinationsOrdered(selector: (E) -> R): List<Pair<E, E>> =
    zipCombinationsOrdered(Comparator.comparing { selector(it) })

/**
 * Zips all combinations of elements present in this [List], returning a [List] of [Pair]s of elements.
 *
 * The pair will be ordered using the natural order of [E].
 */
fun <E : Comparable<E>> List<E>.zipCombinationsOrdered(): List<Pair<E, E>> =
    zipCombinationsOrdered(Comparator.naturalOrder())

/**
 * Whether [this] is a subset of [other].
 */
infix fun <E> Set<E>.isSubsetOf(other: Set<E>): Boolean = (this union other) == other

/**
 * Whether [this] is a property subset of [other].
 */
infix fun <E> Set<E>.isProperSubsetOf(other: Set<E>): Boolean = isSubsetOf(other) && this != other

/**
 * Uses this [Collection] with a [synchronized] block.
 *
 * This is a convenience method for synchronized collections.
 */
fun <C : Collection<*>, R> C.synchronizedWith(block: C.() -> R) = synchronized(this) {
    with(this, block)
}
