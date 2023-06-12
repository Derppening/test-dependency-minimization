package com.derppening.researchprojecttoolkit.model

sealed class CoverageSource {

    data class ClassLoader(val value: List<String>) : CoverageSource()
    data class CoberturaCoverage(val value: CoberturaXML) : CoverageSource()
    data class JacocoCoverage(val value: JacocoXML) : CoverageSource()
}