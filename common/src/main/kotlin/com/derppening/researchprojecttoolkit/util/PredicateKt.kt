package com.derppening.researchprojecttoolkit.util

import java.util.function.Predicate

fun interface PredicateKt<T> : Predicate<T>, (T) -> Boolean {

    override fun test(t: T): Boolean = invoke(t)

    operator fun not(): PredicateKt<T> = PredicateKt { !this(it) }
    override fun and(other: Predicate<in T>): PredicateKt<T> = PredicateKt { this(it) && other.test(it) }
    override fun or(other: Predicate<in T>): PredicateKt<T> = PredicateKt { this(it) || other.test(it) }
}