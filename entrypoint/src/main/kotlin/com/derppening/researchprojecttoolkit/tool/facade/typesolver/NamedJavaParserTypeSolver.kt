package com.derppening.researchprojecttoolkit.tool.facade.typesolver

import com.github.javaparser.ParserConfiguration
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver
import java.io.File
import java.nio.file.Path

/**
 * A [JavaParserTypeSolver] which stores the [source root][srcDir] used to solve types.
 *
 * @property srcDir The source root of the solver.
 * @param parserConfiguration The configuration for [com.github.javaparser.JavaParser].
 * @param cacheSizeLimit The cache size limit.
 */
class NamedJavaParserTypeSolver(
    val srcDir: Path,
    parserConfiguration: ParserConfiguration = defaultParserConfiguration,
    cacheSizeLimit: Long = -1
) : JavaParserTypeSolver(srcDir, parserConfiguration, cacheSizeLimit) {

    constructor(srcDir: File, parserConfiguration: ParserConfiguration = defaultParserConfiguration) :
            this(srcDir.toPath(), parserConfiguration)
    constructor(srcDir: String, parserConfiguration: ParserConfiguration = defaultParserConfiguration) :
            this(File(srcDir), parserConfiguration)

    override fun toString(): String = "NamedJavaParserTypeSolver{srcDir=$srcDir}"

    companion object {

        private val defaultParserConfiguration
            get() = ParserConfiguration().apply {
                languageLevel = ParserConfiguration.LanguageLevel.BLEEDING_EDGE
            }
    }
}