package com.derppening.researchprojecttoolkit.util

/**
 * Retrieves the value of a field of [T] with the field [name].
 */
inline fun <reified T : Any, R> T.getFieldValue(name: String): R {
    return T::class.java
        .getDeclaredField(name)
        .apply {
            isAccessible = true
        }
        .get(this)
        .let {
            @Suppress("UNCHECKED_CAST")
            it as R
        }
}
