package com.derppening.researchprojecttoolkit.util

/**
 * Creates a padding with the given number of [level].
 */
fun createPadding(level: Int): String =
    "| ".repeat((level - 1).coerceAtLeast(0)) + "|-".repeat(level.coerceAtMost(1))

