package com.derppening.gradle

/**
 * A dependency version.
 *
 * @property version The string representation of the version.
 */
data class Version(val version: String) {
    override fun toString(): String = version
}