package com.derppening.researchprojecttoolkit.dependency

import com.derppening.gradle.DependencyModule
import org.gradle.api.artifacts.dsl.DependencyHandler

object Dom4J : DependencyModule("org.dom4j", "dom4j", "2.1.4")

fun DependencyHandler.dom4j(module: String? = null, version: String? = null): Any =
    Dom4J.asDependencyNotation(module, version)
