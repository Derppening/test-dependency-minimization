package com.derppening.researchprojecttoolkit.dependency

import com.derppening.gradle.DependencyModule
import org.gradle.api.artifacts.dsl.DependencyHandler

object Logback : DependencyModule("ch.qos.logback", "logback", "1.3.7")

fun DependencyHandler.logback(module: String? = null, version: String? = null): Any =
    Logback.asDependencyNotation(module, version)
