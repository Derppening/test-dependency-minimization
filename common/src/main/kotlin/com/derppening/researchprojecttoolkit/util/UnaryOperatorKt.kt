package com.derppening.researchprojecttoolkit.util

import java.util.function.UnaryOperator

fun interface UnaryOperatorKt<T> : UnaryOperator<T>, (T) -> T {

    override fun apply(t: T): T = invoke(t)
}