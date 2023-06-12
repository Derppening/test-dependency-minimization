package com.derppening.researchprojecttoolkit.util

import java.util.*

/**
 * Returns an empty [Optional].
 */
@Suppress("NOTHING_TO_INLINE")
inline fun <T : Any> optionalOf(): Optional<T> = Optional.empty<T>()

/**
 * Returns an [Optional] describing the [value], or an empty `Optional` if `value` is `null`.
 */
@Suppress("NOTHING_TO_INLINE")
inline fun <T : Any> optionalOf(value: T?): Optional<T> = Optional.ofNullable(value)

/**
 * If a value is present, performs the given [action] on it, and returns the original [Optional] instance.
 */
fun <T : Any> Optional<T>.onPresent(action: (T) -> Unit): Optional<T> {
    if (isPresent) action(get())
    return this
}

/**
 * Variant of [Optional.map] which accepts a target type which is nullable. If the [mapper] generates a `null` value,
 * this method will return [Optional.empty].
 */
fun <T : Any, U : Any> Optional<T>.mapNotNull(mapper: (T) -> U?): Optional<U> = flatMap { optionalOf(mapper(it)) }