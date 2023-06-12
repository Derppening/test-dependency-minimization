package com.derppening.researchprojecttoolkit.util

import java.time.Duration
import kotlin.time.toKotlinDuration

/**
 * Converts this [Duration] object into a pretty string, using "d/h/m/s" as duration suffixes.
 */
fun Duration.toPrettyString(): String {
    val ktDuration = toKotlinDuration()

    return buildString {
        ktDuration.toComponents { days, hours, minutes, seconds, _ ->
            days
                .takeIf { it != 0L }
                ?.let { append("${it}d") }
            hours
                .takeIf { isNotBlank() || it != 0 }
                ?.let { append("${it}h") }
            minutes
                .takeIf { isNotBlank() || it != 0 }
                ?.let { append("${it}m ") }
            seconds
                .takeIf { isNotBlank() || it != 0 }
                ?.let { append("${it}s") }
        }
    }
}
