package com.derppening.researchprojecttoolkit.util

/**
 * Repeats [this] character by [n] number of times.
 */
operator fun Char.times(n: Int): String = "$this" * n

/**
 * Repeats [this] string by [n] number of times, as if by calling [String.repeat].
 */
operator fun String.times(n: Int): String = this.repeat(n)

/**
 * Trims [this] by removing all characters before and including the first occurrence of [token]. If the token is not
 * found in the string, returns the original string.
 */
fun String.trimAllBefore(token: String): String = this.replaceBefore(token, "").removePrefix(token)

/**
 * Trims [this] by removing all characters after and including the last occurrence of [token]. If the token is not
 * found in the string, returns the original string.
 */
fun String.trimAllAfterLast(token: String): String = this.replaceAfterLast(token, "").removeSuffix(token)