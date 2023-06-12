package com.derppening.researchprojecttoolkit.dependency

import com.derppening.gradle.DependencyModule
import org.gradle.api.artifacts.dsl.DependencyHandler

private object JavaParser : DependencyModule("com.github.javaparser", "javaparser", "3.25.3")

fun DependencyHandler.`java-parser`(module: String? = null, version: String? = null): Any =
    JavaParser.asDependencyNotation(module, version)
