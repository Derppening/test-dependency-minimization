package com.derppening.researchprojecttoolkit.dependency

import com.derppening.gradle.DependencyGroup
import com.derppening.gradle.DependencyModule
import com.derppening.gradle.Version
import org.gradle.api.artifacts.dsl.DependencyHandler

object JUnit5 : DependencyGroup("org.junit") {

    object Jupiter : DependencyModule(JUnit5.group + "jupiter", "junit-jupiter", Version("5.9.3"))
    object Platform : DependencyModule(JUnit5.group + "platform", "junit-platform", Version("1.9.3"))
}

fun DependencyHandler.`junit-jupiter`(module: String? = null, version: String? = null): Any =
    JUnit5.Jupiter.asDependencyNotation(module, version)

fun DependencyHandler.`junit-platform`(module: String? = null, version: String? = null): Any =
    JUnit5.Platform.asDependencyNotation(module, version)
