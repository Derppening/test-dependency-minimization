package com.derppening.researchprojecttoolkit.util

/**
 * Casts [this] as an instance of [T] and calls the specified function [block] if the cast was successful.
 *
 * @return [this] as an instance of [T], or `null` if `this` is not an instance of [T].
 */
inline fun <reified T> Any.runAsOrNull(block: T.() -> Unit): T? {
    return (this as? T)?.also { it.block() }
}
