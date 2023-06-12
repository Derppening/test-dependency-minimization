package com.derppening.researchprojecttoolkit.util

import java.util.function.Consumer

fun interface ConsumerKt<T> : Consumer<T>, (T) -> Unit {

    override fun accept(t: T) = invoke(t)

    override infix fun andThen(after: Consumer<in T>): ConsumerKt<T> = ConsumerKt {
        this(it)
        after.accept(it)
    }
}
