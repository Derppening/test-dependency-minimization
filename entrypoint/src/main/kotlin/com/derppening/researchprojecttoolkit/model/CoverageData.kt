package com.derppening.researchprojecttoolkit.model

import com.github.javaparser.ast.CompilationUnit

/**
 * A set of coverage data for a single bug revision.
 */
sealed class CoverageData {

    abstract val jacocoCov: CoverageSource.JacocoCoverage?

    data class Full(
        val coberturaCov: CoverageSource.CoberturaCoverage,
        override val jacocoCov: CoverageSource.JacocoCoverage?
    ) : CoverageData() {

        constructor(coverageXML: CoberturaXML, jacocoXML: JacocoXML?) : this(
            CoverageSource.CoberturaCoverage(coverageXML),
            jacocoXML?.let { CoverageSource.JacocoCoverage(it) }
        )
    }

    data class PartialMix(
        val classes: CoverageSource,
        val tests: CoverageSource,
        override val jacocoCov: CoverageSource.JacocoCoverage?
    ) : CoverageData() {

        constructor(loadedClasses: LoadedClasses, jacocoXML: JacocoXML?) : this(
            CoverageSource.ClassLoader(loadedClasses.sourceClasses),
            CoverageSource.ClassLoader(loadedClasses.testClasses),
            jacocoXML?.let { CoverageSource.JacocoCoverage(it) }
        )
    }

    /**
     * @param allSourceClasses All source classes which exists in the project.
     * @param allSourceClasses All test classes which exists in the project.
     * @return The [CoverageSource] which information for this [CompilationUnit] is available from.
     */
    fun getCUSource(cu: CompilationUnit, allSourceClasses: Set<String>, allTestClasses: Set<String>): CoverageSource =
        when (this) {
            is Full -> coberturaCov
            is PartialMix -> {
                jacocoCov ?: run {
                    when (val qualifiedName = cu.primaryType.flatMap { it.fullyQualifiedName }.get()) {
                        in allSourceClasses -> classes
                        in allTestClasses -> tests
                        else -> error("Cannot find class `$qualifiedName` in source/test classes set")
                    }
                }
            }
        }
}