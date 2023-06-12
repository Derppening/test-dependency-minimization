package com.derppening.researchprojecttoolkit.util

import java.text.DecimalFormat

/**
 * Converts [this] number as a percentage of [all].
 */
fun Number.asPercentage(all: Number): String {
    return DecimalFormat.getPercentInstance()
        .format(this.toDouble() / all.toDouble())
}

/**
 * Converts [this] to a [String], rounding it to the number of decimal places.
 *
 * @param dp Number of decimal places to round to.
 */
fun Number.toStringRounded(dp: Int): String {
    return "%.${dp}f".format(this.toDouble())
}