package com.derppening.researchprojecttoolkit.dependency

import com.derppening.gradle.DependencyModule
import org.gradle.api.artifacts.dsl.DependencyHandler

private object ApacheCommons : DependencyModule("org.apache.commons", "commons")

fun DependencyHandler.`apache-commons`(module: String? = null, version: String? = null): Any =
    ApacheCommons.asDependencyNotation(module, version)

private object ApacheCommonsIO : DependencyModule("commons-io", "commons")

fun DependencyHandler.`apache-commons-io`(module: String? = null, version: String? = null): Any =
    ApacheCommonsIO.asDependencyNotation(module, version)
