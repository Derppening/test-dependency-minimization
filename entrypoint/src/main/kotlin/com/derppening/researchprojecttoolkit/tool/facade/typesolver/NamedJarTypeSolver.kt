package com.derppening.researchprojecttoolkit.tool.facade.typesolver

import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver
import java.io.File
import java.nio.file.Path

/**
 * A [JarTypeSolver] which stores the location of the JAR used to solve types.
 */
class NamedJarTypeSolver(val pathToJar: String) : JarTypeSolver(pathToJar) {

    constructor(pathToJar: Path) : this(pathToJar.toFile())
    constructor(pathToJar: File) : this(pathToJar.absolutePath)

    override fun toString(): String = "NamedJarTypeSolver{pathToJar=$pathToJar}"
}