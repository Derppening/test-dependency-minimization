package com.derppening.researchprojecttoolkit.model

import com.derppening.researchprojecttoolkit.tool.coberturaCoverageData
import com.derppening.researchprojecttoolkit.tool.jacocoCoverageData

/**
 *
 */
sealed class MergedCoverageData {

    abstract val coberturaLineCount: Int?
    abstract val coberturaIsHit: Boolean?
    abstract val jacocoIsHit: Boolean?

    val isCoberturaDataPresent: Boolean
        get() = coberturaLineCount != null && coberturaIsHit != null

    val isJacocoDataPresent: Boolean
        get() = jacocoIsHit != null

    val isPartiallyPresent: Boolean
        get() = isCoberturaDataPresent || isJacocoDataPresent

    val isFullyPresent: Boolean
        get() = isCoberturaDataPresent && isJacocoDataPresent

    val isDataSound: Boolean get() = isJacocoDataPresent || (isCoberturaDataPresent && coberturaLineCount!! > 0)

    val isReachable: Boolean
        get() {
            if (isCoberturaDataPresent) {
                if (isDataSound && coberturaIsHit!!) {
                    return true
                }
            }

            if (isJacocoDataPresent) {
                if (jacocoIsHit!!) {
                    return true
                }
            }

            return false
        }

    sealed class Declaration : MergedCoverageData() {

        abstract val coberturaLines: List<CoberturaXML.Line>?
        abstract val coberturaLineRate: Double?
        abstract val coberturaBranchRate: Double?
        abstract val jacocoCounters: List<JacocoXML.Counter>?

        override val coberturaLineCount get() = coberturaLines?.size
        override val coberturaIsHit get() = coberturaLineRate?.let { it > 0.0 }
        override val jacocoIsHit get() = jacocoCounters?.getCounterForTypeOrNull(JacocoXML.CounterType.INSTRUCTION)?.let { it.covered > 0 }
    }

    data class Class(
        val coberturaCov: CoberturaXML.Class?,
        val jacocoCov: JacocoXML.Class?
    ) : Declaration() {

        init {
            if (coberturaCov != null && jacocoCov != null) {
                check(coberturaCov.name == jacocoCov.name.replace('/', '.')) {
                    buildString {
                        append("Expected Cobertura and Jacoco to have the same class name! ")
                        append("Cobertura: ${coberturaCov.name}")
                        append(" / ")
                        append("Jacoco: ${jacocoCov.name.replace('/', '.')}")
                    }
                }
            }
        }

        override val coberturaLines get() = coberturaCov?.lines
        override val coberturaLineRate get() = coberturaCov?.lineRate
        override val coberturaBranchRate get() = coberturaCov?.branchRate
        override val jacocoCounters get() = jacocoCov?.counters

        fun findMethodsWithSignature(name: String?, jvmDesc: String?): List<Method> {
            val cobMethodCov = coberturaCov?.methods
            val jacMethodCov = jacocoCov?.methods

            val pairs = (cobMethodCov.orEmpty().map { it.name to it.signature } + jacMethodCov.orEmpty().map { it.name to it.desc })
                .toSet()

            return pairs
                .filter { (bcName, bcSig) ->
                    (name?.let { bcName == it } ?: true) && (jvmDesc?.let { bcSig == it } ?: true)
                }
                .map { (bcName, bcSig) ->
                    Method(
                        cobMethodCov?.find { it.name == bcName && it.signature == bcSig },
                        jacMethodCov?.find { it.name == bcName && it.desc == bcSig }
                    )
                }
        }
    }

    data class Method(
        val coberturaCov: CoberturaXML.Method?,
        val jacocoCov: JacocoXML.Method?
    ) : Declaration() {

        init {
            if (coberturaCov != null && jacocoCov != null) {
                check(coberturaCov.name == jacocoCov.name && coberturaCov.signature == jacocoCov.desc) {
                    buildString {
                        append("Expected Cobertura and Jacoco to have the same method name and signature! ")
                        append("Cobertura: ${coberturaCov.name} ${coberturaCov.signature}")
                        append(" / ")
                        append("Jacoco: ${jacocoCov.name} ${jacocoCov.desc}")
                    }
                }
            }
        }

        override val coberturaLines get() = coberturaCov?.lines
        override val coberturaLineRate get() = coberturaCov?.lineRate
        override val coberturaBranchRate get() = coberturaCov?.branchRate
        override val jacocoCounters get() = jacocoCov?.counters
    }

    data class Package(
        val coberturaCov: CoberturaXML.Package?,
        val jacocoCov: JacocoXML.Package?
    ) : MergedCoverageData() {

        override val coberturaLineCount get() = coberturaCov?.classes?.sumOf { it.lines.size }
        override val coberturaIsHit get() = coberturaCov?.classes?.any { it.lines.any { it.hits > 0L } }
        override val jacocoIsHit get() = jacocoCov?.counters?.getCounterForTypeOrNull(JacocoXML.CounterType.INSTRUCTION)?.let { it.covered > 0 }
    }

    data class Line(
        val coberturaCov: CoberturaXML.Line?,
        val jacocoCov: JacocoXML.Line?
    ) : MergedCoverageData() {

        override val coberturaLineCount get() = coberturaCov?.let { 1 }
        override val coberturaIsHit get() = coberturaCov?.let { it.hits > 0 }
        override val jacocoIsHit get() = jacocoCov?.let { it.ci > 0 }
    }

    companion object {

        fun from(refTypeDecl: ReferenceTypeLikeDeclaration<*>): Class =
            Class(refTypeDecl.coberturaCoverageData, refTypeDecl.jacocoCoverageData)

        fun from(execDecl: ExecutableDeclaration<*>): Method =
            Method(execDecl.coberturaCoverageData, execDecl.jacocoCoverageData)
    }
}