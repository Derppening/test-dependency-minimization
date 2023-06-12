package com.derppening.researchprojecttoolkit.dependency

import com.derppening.gradle.DependencyModule
import org.gradle.api.artifacts.dsl.DependencyHandler

object SLF4J : DependencyModule("org.slf4j", "slf4j", "2.0.7")

fun DependencyHandler.slf4j(module: String? = null, version: String? = null): Any =
    SLF4J.asDependencyNotation(module, version)
