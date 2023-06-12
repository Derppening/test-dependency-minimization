package com.derppening.researchprojecttoolkit.dependency

import com.derppening.gradle.DependencyModule
import org.gradle.api.artifacts.dsl.DependencyHandler

object Clikt : DependencyModule("com.github.ajalt.clikt", "clikt", "3.5.2")

fun DependencyHandler.clikt(module: String? = null, version: String? = null): Any =
    Clikt.asDependencyNotation(module, version)
