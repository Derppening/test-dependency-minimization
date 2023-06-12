package com.derppening.researchprojecttoolkit.util

import kotlin.io.path.Path
import kotlin.io.path.exists

/**
 * Configures this [ProcessBuilder] to redirect the standard file descriptors to the current terminal.
 *
 * @return `this`
 */
fun ProcessBuilder.redirectToTTY(): ProcessBuilder {
    val tty = Path("/dev/tty")
        .also { check(it.exists()) }
        .toFile()

    return this
        .redirectError(tty)
        .redirectOutput(tty)
        .redirectInput(tty)
}