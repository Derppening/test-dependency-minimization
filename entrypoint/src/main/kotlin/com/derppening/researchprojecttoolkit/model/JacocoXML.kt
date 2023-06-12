package com.derppening.researchprojecttoolkit.model

import org.dom4j.Document
import org.dom4j.Element
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.Path

@JvmInline
value class JacocoXML(val report: Report) {

    enum class CounterType {
        INSTRUCTION, BRANCH, LINE, COMPLEXITY, METHOD, CLASS
    }

    data class Counter(
        val type: CounterType,
        val missed: Int,
        val covered: Int
    ) {

        companion object {

            fun fromElement(e: Element): Counter = with(e) {
                check(name == "counter")

                Counter(
                    CounterType.valueOf(attributeValue("type")),
                    attributeValue("missed").toInt(),
                    attributeValue("covered").toInt()
                )
            }
        }
    }

    data class Report(
        val name: String,
        val sessionInfo: SessionInfo,
        val packages: List<Package>,
        val counters: List<Counter>
    ) {

        companion object {

            fun fromElement(e: Element): Report = with(e) {
                check(name == "report")

                Report(
                    attributeValue("name"),
                    SessionInfo.fromElement(element("sessioninfo")),
                    elements("package").map { Package.fromElement(it) },
                    elements("counter").map { Counter.fromElement(it) }
                )
            }
        }
    }

    data class SessionInfo(
        val id: String,
        val start: Instant,
        val dump: Instant
    ) {

        companion object {

            fun fromElement(e: Element): SessionInfo = with(e) {
                check(name == "sessioninfo")

                SessionInfo(
                    attributeValue("id"),
                    Instant.ofEpochMilli(attributeValue("start").toLong()),
                    Instant.ofEpochMilli(attributeValue("dump").toLong()),
                )
            }
        }
    }

    data class Package(
        val name: String,
        val classes: List<Class>,
        val sourceFiles: List<SourceFile>,
        val counters: List<Counter>
    ) {

        companion object {

            fun fromElement(e: Element): Package = with(e) {
                check(name == "package")

                Package(
                    attributeValue("name"),
                    elements("class").map { Class.fromElement(it) },
                    elements("sourcefile").map { SourceFile.fromElement(it) },
                    elements("counter").map { Counter.fromElement(it) }
                )
            }
        }
    }

    data class Class(
        val name: String,
        val sourceFileName: Path,
        val methods: List<Method>,
        val counters: List<Counter>
    ) {

        companion object {

            fun fromElement(e: Element): Class = with(e) {
                check(name == "class")

                Class(
                    attributeValue("name"),
                    Path(attributeValue("sourcefilename")),
                    elements("method").map { Method.fromElement(it) },
                    elements("counter").map { Counter.fromElement(it) }
                )
            }
        }
    }

    data class Method(
        val name: String,
        val desc: String,
        val line: Int,
        val counters: List<Counter>
    ) {

        companion object {

            fun fromElement(e: Element): Method = with(e) {
                check(name == "method")

                Method(
                    attributeValue("name"),
                    attributeValue("desc"),
                    attributeValue("line").toInt(),
                    elements("counter").map { Counter.fromElement(it) }
                )
            }
        }
    }

    data class SourceFile(
        val name: Path,
        val lines: List<Line>,
        val counters: List<Counter>
    ) {

        companion object {

            fun fromElement(e: Element): SourceFile = with(e) {
                check(name == "sourcefile")

                SourceFile(
                    Path(attributeValue("name")),
                    elements("line").map { Line.fromElement(it) },
                    elements("counter").map { Counter.fromElement(it) }
                )
            }
        }
    }

    data class Line(
        val nr: Int,
        val mi: Int,
        val ci: Int,
        val mb: Int,
        val cb: Int
    ) {

        companion object {

            fun fromElement(e: Element): Line = with(e) {
                check(name == "line")

                Line(
                    attributeValue("nr").toInt(),
                    attributeValue("mi").toInt(),
                    attributeValue("ci").toInt(),
                    attributeValue("mb").toInt(),
                    attributeValue("cb").toInt(),
                )
            }
        }
    }

    companion object {

        fun fromDocument(document: Document): JacocoXML = with(document.rootElement) {
            JacocoXML(Report.fromElement(this))
        }
    }
}

fun List<JacocoXML.Counter>.getCounterForType(counterType: JacocoXML.CounterType): JacocoXML.Counter =
    single { it.type == counterType }
fun List<JacocoXML.Counter>.getCounterForTypeOrNull(counterType: JacocoXML.CounterType): JacocoXML.Counter? =
    singleOrNull { it.type == counterType }
