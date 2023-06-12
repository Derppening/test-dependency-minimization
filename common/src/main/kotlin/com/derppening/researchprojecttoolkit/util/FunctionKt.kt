package com.derppening.researchprojecttoolkit.util

import java.util.function.Function

fun interface FunctionKt<T, R> : Function<T, R>, (T) -> R {

    override fun apply(t: T): R = invoke(t)

    override infix fun <V> andThen(after: Function<in R, out V>): FunctionKt<T, V> = FunctionKt {
        after.apply(this(it))
    }
}