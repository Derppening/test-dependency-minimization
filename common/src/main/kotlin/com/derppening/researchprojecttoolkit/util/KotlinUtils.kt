package com.derppening.researchprojecttoolkit.util

/**
 * Applies the given [transform] function onto both elements of the [Pair].
 */
fun <T, R> Pair<T, T>.mapBoth(transform: (T) -> R): Pair<R, R> =
    transform(first) to transform(second)
