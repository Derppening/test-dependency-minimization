package com.derppening.researchprojecttoolkit.commands

import com.derppening.researchprojecttoolkit.model.StandardCsvFormats
import com.derppening.researchprojecttoolkit.tool.javac.JavacError
import com.derppening.researchprojecttoolkit.tool.javac.JavacErrorCategory
import com.derppening.researchprojecttoolkit.util.Defects4JWorkspace
import com.derppening.researchprojecttoolkit.util.reflowLatexTable
import com.derppening.researchprojecttoolkit.util.reflowLatexTableStdin
import com.derppening.researchprojecttoolkit.util.toStringRounded
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import org.apache.commons.csv.CSVRecord
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.bufferedReader
import kotlin.io.path.useLines
import kotlin.io.path.walk
import kotlin.math.roundToInt

class ThesisCmd : CliktCommand(name = "thesis", help = HELP_TEXT, printHelpOnEmptyArgs = true) {

    init {
        subcommands(SUBCOMMANDS)
    }

    override fun run() = Unit

    companion object {

        private const val HELP_TEXT = "Thesis-related utilities."

        private val SUBCOMMANDS = listOf(
            ThesisGroupFailureReasons(),
            ThesisReflowTable(),
            ThesisStatByProject()
        )
    }
}

private class ThesisGroupFailureReasons : CliktCommand(name = "group-failure-reasons", help = HELP_TEXT) {

    private val rootDir by option("-d", help = "Root directory to aggregate failure reasons from.")
        .path(mustExist = true, canBeFile = false, mustBeReadable = true)
        .required()
    private val file by option("-f", help = "File to aggregate failure reasons from.")
        .path()
        .required()
    private val project by option("-p", help = "Project to aggregate failure reasons from.")
        .convert { Defects4JWorkspace.ProjectID.fromString(it) }

    @OptIn(ExperimentalPathApi::class)
    private fun aggregateFailureReasons(
        srcDir: Path,
        relPath: Path,
        pathPredicate: (Path) -> Boolean = { true }
    ): Map<JavacErrorCategory, Int> {
        return buildMap {
            srcDir.walk()
                .filter { it.endsWith(relPath) && pathPredicate(it) }
                .forEach { file ->
                    file
                        .useLines { line ->
                            line
                                .filter { it.trim().startsWith("[javac]") && it.contains("error:") }
                                .map { it.split("error:")[1].trim() }
                                .toList()
                        }
                        .map { JavacError.parse(it).category }
                        .distinct()
                        .forEach {
                            compute(it) { _, count -> (count ?: 0) + 1 }
                        }
                }
        }
    }

    override fun run() {
        val failureReasons = aggregateFailureReasons(
            rootDir,
            file
        ) {
            project?.let { it.toString().contains("$it-") } ?: true
        }

        failureReasons.entries
            .sortedByDescending { it.value }
            .forEach { (k, v) -> println("$k: $v") }
    }

    companion object {

        private const val HELP_TEXT = ""
    }
}

private class ThesisReflowTable : CliktCommand(name = "reflow-table", help = HELP_TEXT) {

    override fun run() {
        println("Enter the content of the table (Empty line to terminate):")
        reflowLatexTableStdin()
    }

    companion object {

        private const val HELP_TEXT = "Reflows a LaTeX table to align all columns."
    }
}

private class ThesisStatByProject : CliktCommand(name = "stat-by-project", help = HELP_TEXT) {

    private val all by option()
        .flag()
    private val csvDir by argument()
        .path(mustExist = true, canBeFile = false, mustBeReadable = true)

    private fun outputRQ2(rq2Class: Map<String, List<CSVRecord>>, rq2Member: Map<String, List<CSVRecord>>) {
        check(rq2Class.keys == rq2Member.keys)

        val merged = buildMap {
            rq2Class.entries.forEach { (projectId, classEntries) ->
                val memberEntries = checkNotNull(rq2Member[projectId])
                check(classEntries.size == memberEntries.size)

                put(projectId, classEntries to memberEntries)
            }
        }

        merged.entries
            .sortedBy { it.key }
            .map { (projectId, records) ->
                buildString {
                    append(projectId)
                    append(" & ")
                    append(records.first.size)
                    append(" & ")
                    appendRatio(records.first) { it["compile"].toBoolean() }
                    append(" & ")
                    appendRatio(records.first) { it["test_match"].toBoolean() }
                    append(" & ")
                    appendRatio(records.second) { it["compile"].toBoolean() }
                    append(" & ")
                    appendRatio(records.second) { it["test_match"].toBoolean() }
                    append(" \\\\")
                }
            }.let {
                it + "\\hline\\hline" + buildString {
                    val records = with(merged.values) {
                        flatMap { it.first } to flatMap { it.second }
                    }
                    check(records.first.size == records.second.size)

                    append("Total")
                    append(" & ")
                    append(records.first.size)
                    append(" & ")
                    appendRatio(records.first) { it["compile"].toBoolean() }
                    append(" & ")
                    appendRatio(records.first) { it["test_match"].toBoolean() }
                    append(" & ")
                    appendRatio(records.second) { it["compile"].toBoolean() }
                    append(" & ")
                    appendRatio(records.second) { it["test_match"].toBoolean() }
                    append(" \\\\")
                }
            }.let {
                reflowLatexTable(it)
            }.also {
                println("RQ2:")
                println(it.joinToString("\n"))
                println()
            }
    }

    private fun outputRQ3Time(rq2Class: Map<String, List<CSVRecord>>, rq2Member: Map<String, List<CSVRecord>>) {
        check(rq2Class.keys == rq2Member.keys)

        val merged = buildMap {
            rq2Class.entries.forEach { (projectId, classEntries) ->
                val memberEntries = checkNotNull(rq2Member[projectId])
                check(classEntries.size == memberEntries.size)

                put(projectId, classEntries to memberEntries)
            }
        }

        merged.entries
            .sortedBy { it.key }
            .map { (projectId, records) ->
                buildString {
                    append(projectId)
                    append(" & ")
                    append(records.first.size)
                    append(" &  & ")
                    appendAverage(records.first) { it["time_taken"].toDouble() / 1000 }
                    append(" & ")
                    appendAverage(records.second) { it["total_time_taken"].toDouble() / 1000 }
                    append(" \\\\")
                }
            }.let {
                it + "\\hline\\hline" + buildString {
                    val records = with(merged.values) {
                        flatMap { it.first } to flatMap { it.second }
                    }
                    check(records.first.size == records.second.size)

                    append("Total")
                    append(" & ")
                    append(records.first.size)
                    append(" &  & ")
                    appendAverage(records.first) { it["time_taken"].toDouble() / 1000 }
                    append(" & ")
                    appendAverage(records.second) { it["total_time_taken"].toDouble() / 1000 }
                    append(" \\\\")
                }
            }.let {
                reflowLatexTable(it)
            }.also {
                println("RQ3 Time:")
                println(it.joinToString("\n"))
                println()
            }
    }

    private fun aggregateStats(inputDir: Path) {
        val csvClassFormat = if (all) {
            StandardCsvFormats.AggregatedClassAllStats
        } else {
            StandardCsvFormats.AggregatedClassStats
        }
        val csvMemberFormat = if (all) {
            StandardCsvFormats.AggregatedMemberAllStats
        } else {
            StandardCsvFormats.AggregatedMemberStats
        }

        val csvClassRecords = inputDir.resolve(csvClassFormat.defaultFilename).bufferedReader().use {
            csvClassFormat.readerFormat
                .parse(it)
                .records
        }
        val csvMemberRecords = inputDir.resolve(csvMemberFormat.defaultFilename).bufferedReader().use {
            csvMemberFormat.readerFormat
                .parse(it)
                .records
        }

        val filtClassRecords = csvClassRecords.filter {
            !it["compile0"].toBooleanStrict() && it["compile1"].toBoolean()
        }
        val filtMemberRecords = csvMemberRecords.filter {
            !it["compile0"].toBooleanStrict() && it["compile1"].toBoolean()
        }

        val rq2ClassRecordsByProject = filtClassRecords.groupBy { it["project_id"] }
        val memberRecordsByProject = filtMemberRecords.groupBy { it["project_id"] }
        val rq2MemberRecordsByProject = memberRecordsByProject
            .mapValues { (_, v) -> v.filter { it["last_pass"].toBoolean() } }

        outputRQ2(rq2ClassRecordsByProject, rq2MemberRecordsByProject)

        val rq3RecordsByProject = memberRecordsByProject
            .mapValues { (_, v) ->
                v.filter { it["bug_id"].endsWith('f') && it["last_pass"].toBoolean() && it["test_match1"].toBooleanStrict() }
            }
            .filterValues { it.isNotEmpty() }

        val rq3SuccessRecordsByProject = rq3RecordsByProject
            .mapValues { (_, v) -> v.filter { it["test_match"].toBoolean() } }
            .filterValues { it.isNotEmpty() }

        rq3SuccessRecordsByProject.entries
            .sortedBy { it.key }
            .map { (projectId, records) ->
                buildString {
                    append(projectId)
                    append(" & ")
                    append(records.size)
                    append(" & ")
                    appendAverage(records) { it["class-fpr"].toDouble() }
                    append(" & ")
                    appendAverage(records) { it["class-fnr"].toDouble() }
                    append(" & ")
                    appendAverage(records) { it["class-accuracy"].toDouble() }
                    append(" & ")
                    appendAverage(records) { it["class-precision"].toDouble() }
                    append(" & ")
                    appendAverage(records) { it["class-recall"].toDouble() }
                    append(" & ")
                    appendAverage(records) { it["class-f1"].toDouble() }
                    append(" \\\\")
                }
            }.let {
                it + "\\hline\\hline" + buildString {
                    val records = rq3SuccessRecordsByProject.values.flatten()

                    append("Total")
                    append(" & ")
                    append(records.size)
                    append(" & ")
                    appendAverage(records) { it["class-fpr"].toDouble() }
                    append(" & ")
                    appendAverage(records) { it["class-fnr"].toDouble() }
                    append(" & ")
                    appendAverage(records) { it["class-accuracy"].toDouble() }
                    append(" & ")
                    appendAverage(records) { it["class-precision"].toDouble() }
                    append(" & ")
                    appendAverage(records) { it["class-recall"].toDouble() }
                    append(" & ")
                    appendAverage(records) { it["class-f1"].toDouble() }
                    append(" \\\\")
                }
            }.let {
                reflowLatexTable(it)
            }.also {
                println("RQ3 Success - Class:")
                println(it.joinToString("\n"))
                println()
            }

        rq3SuccessRecordsByProject.entries
            .sortedBy { it.key }
            .map { (projectId, records) ->
                buildString {
                    append(projectId)
                    append(" & ")
                    append(records.size)
                    append(" & ")
                    appendAverage(records) { it["method-fpr"].toDouble() }
                    append(" & ")
                    appendAverage(records) { it["method-fnr"].toDouble() }
                    append(" & ")
                    appendAverage(records) { it["method-accuracy"].toDouble() }
                    append(" & ")
                    appendAverage(records) { it["method-precision"].toDouble() }
                    append(" & ")
                    appendAverage(records) { it["method-recall"].toDouble() }
                    append(" & ")
                    appendAverage(records) { it["method-f1"].toDouble() }
                    append(" \\\\")
                }
            }.let {
                it + "\\hline\\hline" + buildString {
                    val records = rq3SuccessRecordsByProject.values.flatten()

                    append("Total")
                    append(" & ")
                    append(records.size)
                    append(" & ")
                    appendAverage(records) { it["method-fpr"].toDouble() }
                    append(" & ")
                    appendAverage(records) { it["method-fnr"].toDouble() }
                    append(" & ")
                    appendAverage(records) { it["method-accuracy"].toDouble() }
                    append(" & ")
                    appendAverage(records) { it["method-precision"].toDouble() }
                    append(" & ")
                    appendAverage(records) { it["method-recall"].toDouble() }
                    append(" & ")
                    appendAverage(records) { it["method-f1"].toDouble() }
                    append(" \\\\")
                }
            }.let {
                reflowLatexTable(it)
            }.also {
                println("RQ3 Success - Method:")
                println(it.joinToString("\n"))
                println()
            }

//    val rq3FailureRecordsByProject = rq3RecordsByProject
//        .mapValues { (_, v) -> v.filterNot { it["test_match"].toBoolean() } }
//        .filterValues { it.isNotEmpty() }
//
//    rq3FailureRecordsByProject.entries
//        .sortedBy { it.key }
//        .map { (projectId, records) ->
//            buildString {
//                append(projectId)
//                append(" & ")
//                append(records.size)
//                append(" & ")
//                appendAverage(records) { it["class-fpr"].toDouble() }
//                append(" & ")
//                append(records.map { it["class-fn"].toInt() }.average().toStringRounded(1))
//                append(" (")
//                appendAverage(records) { it["class-fnr"].toDouble() }
//                append(") & ")
//                appendAverage(records) { it["class-accuracy"].toDouble() }
//                append(" & ")
//                appendAverage(records) { it["class-precision"].toDouble() }
//                append(" & ")
//                appendAverage(records) { it["class-recall"].toDouble() }
//                append(" & ")
//                appendAverage(records) { it["class-f1"].toDouble() }
//                append(" \\\\")
//            }
//        }.let {
//            it + "\\hline\\hline" + buildString {
//                val records = rq3FailureRecordsByProject.values.flatten()
//
//                append("Total")
//                append(" & ")
//                append(records.size)
//                append(" & ")
//                appendAverage(records) { it["class-fpr"].toDouble() }
//                append(" & ")
//                append(records.map { it["class-fn"].toInt() }.average().toStringRounded(1))
//                append(" (")
//                appendAverage(records) { it["class-fnr"].toDouble() }
//                append(") & ")
//                appendAverage(records) { it["class-accuracy"].toDouble() }
//                append(" & ")
//                appendAverage(records) { it["class-precision"].toDouble() }
//                append(" & ")
//                appendAverage(records) { it["class-recall"].toDouble() }
//                append(" & ")
//                appendAverage(records) { it["class-f1"].toDouble() }
//                append(" \\\\")
//            }
//        }.let {
//            reflowLatexTable(it)
//        }.also {
//            println("RQ3 Failure - Class:")
//            println(it.joinToString("\n"))
//            println()
//        }
//
//    rq3FailureRecordsByProject.entries
//        .sortedBy { it.key }
//        .map { (projectId, records) ->
//            buildString {
//                append(projectId)
//                append(" & ")
//                append(records.size)
//                append(" & ")
//                appendAverage(records) { it["method-fpr"].toDouble() }
//                append(" & ")
//                append(records.map { it["method-fn"].toInt() }.average().toStringRounded(1))
//                append(" (")
//                appendAverage(records) { it["method-fnr"].toDouble() }
//                append(") & ")
//                appendAverage(records) { it["method-accuracy"].toDouble() }
//                append(" & ")
//                appendAverage(records) { it["method-precision"].toDouble() }
//                append(" & ")
//                appendAverage(records) { it["method-recall"].toDouble() }
//                append(" & ")
//                appendAverage(records) { it["method-f1"].toDouble() }
//                append(" \\\\")
//            }
//        }.let {
//            it + "\\hline\\hline" + buildString {
//                val records = rq3FailureRecordsByProject.values.flatten()
//
//                append("Total")
//                append(" & ")
//                append(records.size)
//                append(" & ")
//                appendAverage(records) { it["method-fpr"].toDouble() }
//                append(" & ")
//                append(records.map { it["method-fn"].toInt() }.average().toStringRounded(1))
//                append(" (")
//                appendAverage(records) { it["method-fnr"].toDouble() }
//                append(") & ")
//                appendAverage(records) { it["method-accuracy"].toDouble() }
//                append(" & ")
//                appendAverage(records) { it["method-precision"].toDouble() }
//                append(" & ")
//                appendAverage(records) { it["method-recall"].toDouble() }
//                append(" & ")
//                appendAverage(records) { it["method-f1"].toDouble() }
//                append(" \\\\")
//            }
//        }.let {
//            reflowLatexTable(it)
//        }.also {
//            println("RQ3 Failure - Method:")
//            println(it.joinToString("\n"))
//            println()
//        }

        val multipassRecordsByProject = memberRecordsByProject
            .mapValues { (_, v) ->
                v.filter { it["pass_num"].toInt() > 0 && it["last_pass"].toBoolean() }
            }
            .filterValues { it.isNotEmpty() }

        fun findInitialPass(record: CSVRecord): CSVRecord {
            return filtMemberRecords.asSequence()
                .filter { it["project_id"] == record["project_id"] }
                .filter { it["bug_id"] == record["bug_id"] }
                .filter { it["test_case"] == record["test_case"] }
                .filter { it["pass_num"].toInt() == 0 }
                .single()
        }

        fun StringBuilder.appendDiffAverage(records: Collection<CSVRecord>, mapper: (CSVRecord) -> Double ): StringBuilder {
            return appendAverage(records, withSign = true) {
                mapper(it) - mapper(findInitialPass(it))
            }
        }

        multipassRecordsByProject.entries
            .sortedBy { it.key }
            .map { (projectId, records) ->
                buildString {
                    append(projectId)
                    append(" & ")
                    append(records.size)
                    append(" & ")
                    appendDiffAverage(records) { it["class-fpr"].toDouble() }
                    append(" & ")
                    appendDiffAverage(records) { it["class-fnr"].toDouble() }
                    append(" & ")
                    appendDiffAverage(records) { it["class-accuracy"].toDouble() }
                    append(" & ")
                    appendDiffAverage(records) { it["class-precision"].toDouble() }
                    append(" & ")
                    appendDiffAverage(records) { it["class-recall"].toDouble() }
                    append(" & ")
                    appendDiffAverage(records) { it["class-f1"].toDouble() }
                    append(" \\\\")
                }
            }.let {
                it + "\\hline\\hline" + buildString {
                    val records = multipassRecordsByProject.values.flatten()

                    append("Total")
                    append(" & ")
                    append(records.size)
                    append(" & ")
                    appendDiffAverage(records) { it["class-fpr"].toDouble() }
                    append(" & ")
                    appendDiffAverage(records) { it["class-fnr"].toDouble() }
                    append(" & ")
                    appendDiffAverage(records) { it["class-accuracy"].toDouble() }
                    append(" & ")
                    appendDiffAverage(records) { it["class-precision"].toDouble() }
                    append(" & ")
                    appendDiffAverage(records) { it["class-recall"].toDouble() }
                    append(" & ")
                    appendDiffAverage(records) { it["class-f1"].toDouble() }
                    append(" \\\\")
                }
            }.let {
                reflowLatexTable(it)
            }.also {
                println("Multiple Passes - Class:")
                println(it.joinToString("\n"))
                println()
            }

        multipassRecordsByProject.entries
            .sortedBy { it.key }
            .map { (projectId, records) ->
                buildString {
                    append(projectId)
                    append(" & ")
                    append(records.size)
                    append(" & ")
                    appendDiffAverage(records) { it["method-fpr"].toDouble() }
                    append(" & ")
                    appendDiffAverage(records) { it["method-fnr"].toDouble() }
                    append(" & ")
                    appendDiffAverage(records) { it["method-accuracy"].toDouble() }
                    append(" & ")
                    appendDiffAverage(records) { it["method-precision"].toDouble() }
                    append(" & ")
                    appendDiffAverage(records) { it["method-recall"].toDouble() }
                    append(" & ")
                    appendDiffAverage(records) { it["method-f1"].toDouble() }
                    append(" \\\\")
                }
            }.let {
                it + "\\hline\\hline" + buildString {
                    val records = multipassRecordsByProject.values.flatten()

                    append("Total")
                    append(" & ")
                    append(records.size)
                    append(" & ")
                    appendDiffAverage(records) { it["method-fpr"].toDouble() }
                    append(" & ")
                    appendDiffAverage(records) { it["method-fnr"].toDouble() }
                    append(" & ")
                    appendDiffAverage(records) { it["method-accuracy"].toDouble() }
                    append(" & ")
                    appendDiffAverage(records) { it["method-precision"].toDouble() }
                    append(" & ")
                    appendDiffAverage(records) { it["method-recall"].toDouble() }
                    append(" & ")
                    appendDiffAverage(records) { it["method-f1"].toDouble() }
                    append(" \\\\")
                }
            }.let {
                reflowLatexTable(it)
            }.also {
                println("Multiple Passes - Method:")
                println(it.joinToString("\n"))
                println()
            }

        outputRQ3Time(rq2ClassRecordsByProject, rq2MemberRecordsByProject)
    }

    override fun run() {
        aggregateStats(csvDir)
    }

    companion object {

        private const val HELP_TEXT = "Aggregates subject statistics by project."

        private fun StringBuilder.appendRatio(
            coll: Collection<CSVRecord>,
            mapper: (CSVRecord) -> Boolean
        ): StringBuilder {
            val numMatching = coll.count { mapper(it) }
            append(numMatching)
            append(" (")
            append((numMatching.toDouble() / coll.size.toDouble() * 100.0).roundToInt())
            append("\\%)")
            return this
        }

        private fun StringBuilder.appendAverage(
            coll: Collection<CSVRecord>,
            withSign: Boolean = false,
            mapper: (CSVRecord) -> Double
        ): StringBuilder {
            val value = coll.map(mapper).average()
            if (withSign) {
                if (value > 0.0) {
                    append('+')
                }
            }
            append(value.toStringRounded(3))
            return this
        }
    }
}