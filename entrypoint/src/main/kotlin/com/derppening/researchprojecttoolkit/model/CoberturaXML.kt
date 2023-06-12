package com.derppening.researchprojecttoolkit.model

import org.dom4j.Document
import org.dom4j.Element
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.Path

@JvmInline
value class CoberturaXML(val coverage: Coverage) {

    data class Coverage(
        val lineRate: Double,
        val branchRate: Double,
        val linesCovered: Int,
        val linesValid: Int,
        val branchesCovered: Int,
        val branchesValid: Int,
        val complexity: Double,
        val version: String,
        val timestamp: Instant,
        val sources: List<Source>,
        val packages: List<Package>
    ) {

        companion object {

            fun fromElement(e: Element): Coverage = with(e) {
                check(name == "coverage")

                Coverage(
                    attributeValue("line-rate").toDouble(),
                    attributeValue("branch-rate").toDouble(),
                    attributeValue("lines-covered").toInt(),
                    attributeValue("lines-valid").toInt(),
                    attributeValue("branches-covered").toInt(),
                    attributeValue("branches-valid").toInt(),
                    attributeValue("complexity").toDouble(),
                    attributeValue("version"),
                    attributeValue("timestamp").let { Instant.ofEpochMilli(it.toLong()) },
                    element("sources").elements("source").map { Source.fromElement(it) },
                    element("packages").elements("package").map { Package.fromElement(it) }
                )
            }
        }
    }

    @JvmInline
    value class Source(val value: Path) {

        companion object {

            fun fromElement(e: Element): Source = with(e) {
                check(name == "source")

                Source(Path(e.text))
            }
        }
    }

    data class Package(
        val name: String,
        val lineRate: Double,
        val branchRate: Double,
        val complexity: Double,
        val classes: List<Class>
    ) {

        companion object {

            fun fromElement(e: Element): Package = with(e) {
                check(name == "package")

                Package(
                    attributeValue("name"),
                    attributeValue("line-rate").toDouble(),
                    attributeValue("branch-rate").toDouble(),
                    attributeValue("complexity").toDouble(),
                    element("classes").elements("class").map { Class.fromElement(it) }
                )
            }
        }
    }

    data class Class(
        val name: String,
        val filename: Path,
        val lineRate: Double,
        val branchRate: Double,
        val complexity: Double,
        val methods: List<Method>,
        val lines: List<Line>
    ) {

        companion object {

            fun fromElement(e: Element): Class = with(e) {
                check(name == "class")

                Class(
                    attributeValue("name"),
                    Path(attributeValue("filename")),
                    attributeValue("line-rate").toDouble(),
                    attributeValue("branch-rate").toDouble(),
                    attributeValue("complexity").toDouble(),
                    element("methods").elements("method").map { Method.fromElement(it) },
                    element("lines").elements("line").map { Line.fromElement(it) }
                )
            }
        }
    }

    data class Method(
        val name: String,
        val signature: String,
        val lineRate: Double,
        val branchRate: Double,
        val complexity: Double?,
        val lines: List<Line>
    ) {

        companion object {

            fun fromElement(e: Element): Method = with(e) {
                check(name == "method")

                Method(
                    attributeValue("name"),
                    attributeValue("signature"),
                    attributeValue("line-rate").toDouble(),
                    attributeValue("branch-rate").toDouble(),
                    attributeValue("complexity")?.toDouble(),
                    element("lines").elements("line").map { Line.fromElement(it) }
                )
            }
        }
    }

    data class Line(
        val number: Int,
        val hits: Long,
        val branch: Boolean,
        val conditionCoverage: String,
        val conditions: List<Conditions>
    ) {

        companion object {

            fun fromElement(e: Element): Line = with(e) {
                check(name == "line")

                Line(
                    attributeValue("number").toInt(),
                    attributeValue("hits").toLong(),
                    attributeValue("branch")?.toBooleanStrict() ?: false,
                    attributeValue("conditionCoverage") ?: "100%",
                    elements("conditions").map { Conditions.fromElement(it) }
                )
            }
        }
    }

    @JvmInline
    value class Conditions(val condition: List<Condition>) {

        companion object {

            fun fromElement(e: Element): Conditions = with(e) {
                check(name == "conditions")

                Conditions(elements("condition").map { Condition.fromElement(it) })
            }
        }
    }

    data class Condition(
        val number: Int,
        val type: String,
        val coverage: String
    ) {

        companion object {

            fun fromElement(e: Element): Condition = with(e) {
                check(name == "condition")

                Condition(
                    attributeValue("number").toInt(),
                    attributeValue("type"),
                    attributeValue("coverage")
                )
            }
        }
    }

    companion object {

        fun fromDocument(document: Document): CoberturaXML = with(document.rootElement) {
            CoberturaXML(Coverage.fromElement(this))
        }
    }
}
