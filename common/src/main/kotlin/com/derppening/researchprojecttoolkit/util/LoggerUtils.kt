package com.derppening.researchprojecttoolkit.util

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

/**
 * Kotlin wrapper for [LoggerFactory.getLogger].
 */
fun Logger(clazz: KClass<*>): Logger = LoggerFactory.getLogger(clazz.java)

/**
 * Kotlin wrapper for [LoggerFactory.getLogger] using reified type parameters.
 */
inline fun <reified T> Logger(): Logger = Logger(T::class)