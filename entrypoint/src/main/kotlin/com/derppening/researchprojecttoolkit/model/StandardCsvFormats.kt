package com.derppening.researchprojecttoolkit.model

import com.derppening.researchprojecttoolkit.defects4j.SourceRootOutput
import com.derppening.researchprojecttoolkit.util.Defects4JWorkspace
import com.derppening.researchprojecttoolkit.util.ToolOutputDirectory
import com.derppening.researchprojecttoolkit.util.isSubsetOf
import hk.ust.cse.castle.toolkit.jvm.jsl.PredicatedFileCollector
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import java.nio.file.Path
import kotlin.io.path.*

private val STAT_FIELDS = arrayOf(
    "Total Count",
    "Predicted Count",
    "Actual Count",
    "True-Positive",
    "False-Positive",
    "False-Negative",
    "True-Negative",
    "True-Positive Rate",
    "False-Positive Rate",
    "False-Negative Rate",
    "True-Negative Rate",
    "Accuracy",
    "Precision",
    "Recall",
    "F-score",
)

private fun hasOutput(toolOutDir: ToolOutputDirectory): Boolean {
    return arrayOf(
        ToolOutputDirectory::getDefaultCompilePath,
    ).any { fileGetter ->
        fileGetter(toolOutDir).exists()
    }
}

private fun dumpSourceRootStats(printer: CSVPrinter, path: Path) {
    if (path.isRegularFile()) {
        val contents = path.bufferedReader()
            .use { it.readLines() }
            .associate { line -> line.split(':').let { it[0].trim() to it[1].trim() } }

        STAT_FIELDS.map { checkNotNull(contents[it]) }
    } else {
        List(STAT_FIELDS.size) { "" }
    }.forEach {
        printer.print(it)
    }
}

abstract class StandardCsvFormat {

    protected abstract val formatBuilder: CSVFormat.Builder
    abstract val defaultFilename: Path

    val readerFormat: CSVFormat
        get() = formatBuilder.apply {
            setSkipHeaderRecord(true)
        }.build()
    val writerFormat: CSVFormat
        get() = formatBuilder.build()

    abstract fun aggregateStats(outputDir: Path, outputFile: Path = outputDir.resolve(defaultFilename)): Path
}

object StandardCsvFormats {

    abstract class AbstractAggregatedClassStats : StandardCsvFormat()
    abstract class AbstractAggregatedMemberStats : StandardCsvFormat() {

        /**
         * Checks whether [toolOutDir] has execution output for the given pass.
         */
        protected fun hasOutput(toolOutDir: ToolOutputDirectory, passNum: UInt): Boolean {
            val hasNonSourceRootDependentOutput = arrayOf(
                ToolOutputDirectory::getCompilePath,
                ToolOutputDirectory::getFailingTestsPath,
                ToolOutputDirectory::getSourcesPath,
                ToolOutputDirectory::getTestPath,
            ).any { fileGetter ->
                fileGetter(toolOutDir, passNum).exists()
            }
            val hasSourceRootDependentOutput = arrayOf(
                ToolOutputDirectory::getClassDiffPath,
                ToolOutputDirectory::getClassStatPath,
                ToolOutputDirectory::getMethodDiffPath,
                ToolOutputDirectory::getMethodStatPath,
            ).any { fileGetter ->
                fileGetter(toolOutDir, passNum, SourceRootOutput.ALL).exists()
            }
            val hasSourceRootDependentDebuggableOutput = arrayOf(
                ToolOutputDirectory::getActualClassesPath,
                ToolOutputDirectory::getActualMethodsPath,
            ).any { fileGetter ->
                fileGetter(toolOutDir, passNum, SourceRootOutput.ALL, false).exists()
            }

            return hasNonSourceRootDependentOutput || hasSourceRootDependentOutput || hasSourceRootDependentDebuggableOutput
        }

        protected fun getFailingTestResult(
            toolOutDir: ToolOutputDirectory,
            versionId: Defects4JWorkspace.VersionID,
            passNum: UInt
        ): Boolean? {
            val failingTestsFile = toolOutDir.getFailingTestsPath(passNum)
            return if (failingTestsFile.isRegularFile()) {
                val failingTests = failingTestsFile.bufferedReader()
                    .use { it.readLines() }
                    .filter { it.startsWith("--- ") }
                    .map { it.removePrefix("--- ") }
                    .toSet()

                val expectedFailures = if (versionId.version == Defects4JWorkspace.BugVersion.FIXED) {
                    emptySet()
                } else {
                    toolOutDir.getExpectedFailingTestsPath()
                        .bufferedReader()
                        .use { it.readLines() }
                        .filter { it.isNotBlank() }
                        .toSet()
                }

                failingTests isSubsetOf expectedFailures
            } else null
        }
    }

    object AggregatedClassStats : AbstractAggregatedClassStats() {

        override val defaultFilename: Path = Path("aggregated-class-stats.csv")

        override fun aggregateStats(outputDir: Path, outputFile: Path): Path {
            val testOutDirs = PredicatedFileCollector(outputDir)
                .collect {
                    val relPath = outputDir.relativize(it)
                    val fileName = relPath.fileName.toString()
                    relPath.nameCount == 2 && fileName.isNotBlank() && fileName.contains("::") && it.isDirectory()
                }
                .sorted()

            CSVPrinter(outputFile.bufferedWriter(), writerFormat).use { printer ->
                for (testOutDir in testOutDirs) {
                    val projectOutDir = testOutDir.parent.also { check(it.isDirectory()) }

                    val toolOutDir = ToolOutputDirectory(projectOutDir, testOutDir.last(), readOnly = true)

                    val (projectId, bugId) = projectOutDir.fileName
                        .toString()
                        .let { Defects4JWorkspace.ProjectRev.fromString(it) }

                    if (!hasOutput(toolOutDir)) {
                        break
                    }

                    printer.print(projectId)
                    printer.print(bugId)
                    printer.print(testOutDir.fileName)

                    dumpSourceRootStats(printer, toolOutDir.getBaselineClassStatPath(SourceRootOutput.ALL))
                    dumpSourceRootStats(printer, toolOutDir.getBaselineMethodStatPath(SourceRootOutput.ALL))

                    val timeTakenFile = toolOutDir.getBaselineRunningTimePath()
                    if (timeTakenFile.isRegularFile()) {
                        val timeTaken = timeTakenFile.bufferedReader()
                            .useLines { lines ->
                                lines.first().also { checkNotNull(it.toIntOrNull()) }
                            }
                            .toInt()

                        printer.print(timeTaken)
                    } else {
                        printer.print("")
                    }

                    val compile0File = toolOutDir.getDefaultCompilePath()
                    if (compile0File.isRegularFile()) {
                        val exitCode = compile0File.bufferedReader()
                            .useLines { lines ->
                                lines.first().also { check(it.startsWith("Pre-Reduce Compile ExitCode: ")) }
                            }
                            .removePrefix("Pre-Reduce Compile ExitCode: ")
                            .toInt()

                        printer.print(exitCode == 0)
                    } else {
                        printer.print("")
                    }

                    val compile1File = toolOutDir.getGroundTruthCompilePath()
                    if (compile1File.isRegularFile()) {
                        val exitCode = compile1File.bufferedReader()
                            .useLines { lines ->
                                lines.first().also { check(it.startsWith("Pre-Reduce Compile ExitCode: ")) }
                            }
                            .removePrefix("Pre-Reduce Compile ExitCode: ")
                            .toInt()

                        printer.print(exitCode == 0)
                    } else {
                        printer.print("")
                    }

                    val failingTests1File = toolOutDir.getGroundTruthFailingTestsPath()
                    if (failingTests1File.isRegularFile()) {
                        val failingTests = failingTests1File.bufferedReader()
                            .use { it.readLines() }
                            .filter { it.startsWith("--- ") }
                            .map { it.removePrefix("--- ") }
                            .toSet()

                        val expectedFailures = if (bugId.version == Defects4JWorkspace.BugVersion.FIXED) {
                            emptySet()
                        } else {
                            toolOutDir.getExpectedFailingTestsPath()
                                .bufferedReader()
                                .use { it.readLines() }
                                .filter { it.isNotBlank() }
                                .toSet()
                        }

                        printer.print(failingTests isSubsetOf expectedFailures)
                    } else {
                        printer.print("")
                    }

                    val compileFile = toolOutDir.getBaselineCompilePath()
                    if (compileFile.isRegularFile()) {
                        val exitCode = compileFile.bufferedReader()
                            .useLines { lines ->
                                lines.first().also { check(it.startsWith("Compile ExitCode: ")) }
                            }
                            .removePrefix("Compile ExitCode: ")
                            .toInt()

                        printer.print(exitCode == 0)
                    } else {
                        printer.print("")
                    }

                    val failingTestsFile = toolOutDir.getBaselineFailingTestsPath()
                    if (failingTestsFile.isRegularFile()) {
                        val failingTests = failingTestsFile.bufferedReader()
                            .use { it.readLines() }
                            .filter { it.startsWith("--- ") }
                            .map { it.removePrefix("--- ") }
                            .toSet()

                        val expectedFailures = if (bugId.version == Defects4JWorkspace.BugVersion.FIXED) {
                            emptySet()
                        } else {
                            toolOutDir.getExpectedFailingTestsPath()
                                .bufferedReader()
                                .use { it.readLines() }
                                .filter { it.isNotBlank() }
                                .toSet()
                        }

                        printer.print(failingTests isSubsetOf expectedFailures)
                    } else {
                        printer.print("")
                    }

                    printer.println()
                }
            }

            return outputFile
        }

        override val formatBuilder: CSVFormat.Builder = CSVFormat.RFC4180.builder().apply {
            setHeader(
                "project_id",
                "bug_id",
                "test_case",
                "class-total",
                "class-pp",
                "class-p",
                "class-tp",
                "class-fp",
                "class-fn",
                "class-tn",
                "class-tpr",
                "class-fpr",
                "class-fnr",
                "class-tnr",
                "class-accuracy",
                "class-precision",
                "class-recall",
                "class-f1",
                "method-total",
                "method-pp",
                "method-p",
                "method-tp",
                "method-fp",
                "method-fn",
                "method-tn",
                "method-tpr",
                "method-fpr",
                "method-fnr",
                "method-tnr",
                "method-accuracy",
                "method-precision",
                "method-recall",
                "method-f1",
                "time_taken",
                "compile0",
                "compile1",
                "test_match1",
                "compile",
                "test_match"
            )
        }
    }

    object AggregatedClassAllStats : StandardCsvFormat() {

        override val defaultFilename: Path = Path("aggregated-class-all-stats.csv")

        override fun aggregateStats(outputDir: Path, outputFile: Path): Path {
            val testOutDirs = PredicatedFileCollector(outputDir)
                .collect {
                    val relPath = outputDir.relativize(it)
                    val fileName = relPath.fileName.toString()
                    relPath.nameCount == 2 && fileName.isNotBlank() && fileName.contains("::") && it.isDirectory()
                }
                .sorted()

            CSVPrinter(outputFile.bufferedWriter(), writerFormat).use { printer ->
                for (testOutDir in testOutDirs) {
                    val projectOutDir = testOutDir.parent.also { check(it.isDirectory()) }

                    val toolOutDir = ToolOutputDirectory(projectOutDir, testOutDir.last(), readOnly = true)

                    val (projectId, bugId) = projectOutDir.fileName
                        .toString()
                        .let { Defects4JWorkspace.ProjectRev.fromString(it) }

                    if (!hasOutput(toolOutDir)) {
                        break
                    }

                    printer.print(projectId)
                    printer.print(bugId)
                    printer.print(testOutDir.fileName)

                    arrayOf(
                        SourceRootOutput.ALL,
                        SourceRootOutput.SOURCE,
                        SourceRootOutput.TEST
                    ).forEach { sourceRoot ->
                        dumpSourceRootStats(printer, toolOutDir.getBaselineClassStatPath(sourceRoot))
                        dumpSourceRootStats(printer, toolOutDir.getBaselineMethodStatPath(sourceRoot))
                    }

                    val timeTakenFile = toolOutDir.getBaselineRunningTimePath()
                    if (timeTakenFile.isRegularFile()) {
                        val timeTaken = timeTakenFile.bufferedReader()
                            .useLines { lines ->
                                lines.first().also { checkNotNull(it.toIntOrNull()) }
                            }
                            .toInt()

                        printer.print(timeTaken)
                    } else {
                        printer.print("")
                    }

                    val compile0File = toolOutDir.getDefaultCompilePath()
                    if (compile0File.isRegularFile()) {
                        val exitCode = compile0File.bufferedReader()
                            .useLines { lines ->
                                lines.first().also { check(it.startsWith("Pre-Reduce Compile ExitCode: ")) }
                            }
                            .removePrefix("Pre-Reduce Compile ExitCode: ")
                            .toInt()

                        printer.print(exitCode == 0)
                    } else {
                        printer.print("")
                    }

                    val compile1File = toolOutDir.getGroundTruthCompilePath()
                    if (compile1File.isRegularFile()) {
                        val exitCode = compile1File.bufferedReader()
                            .useLines { lines ->
                                lines.first().also { check(it.startsWith("Pre-Reduce Compile ExitCode: ")) }
                            }
                            .removePrefix("Pre-Reduce Compile ExitCode: ")
                            .toInt()

                        printer.print(exitCode == 0)
                    } else {
                        printer.print("")
                    }

                    val failingTests1File = toolOutDir.getGroundTruthFailingTestsPath()
                    if (failingTests1File.isRegularFile()) {
                        val failingTests = failingTests1File.bufferedReader()
                            .use { it.readLines() }
                            .filter { it.startsWith("--- ") }
                            .map { it.removePrefix("--- ") }
                            .toSet()

                        val expectedFailures = if (bugId.version == Defects4JWorkspace.BugVersion.FIXED) {
                            emptySet()
                        } else {
                            toolOutDir.getExpectedFailingTestsPath()
                                .bufferedReader()
                                .use { it.readLines() }
                                .filter { it.isNotBlank() }
                                .toSet()
                        }

                        printer.print(failingTests isSubsetOf expectedFailures)
                    } else {
                        printer.print("")
                    }

                    val compileFile = toolOutDir.getBaselineCompilePath()
                    if (compileFile.isRegularFile()) {
                        val exitCode = compileFile.bufferedReader()
                            .useLines { lines ->
                                lines.first().also { check(it.startsWith("Compile ExitCode: ")) }
                            }
                            .removePrefix("Compile ExitCode: ")
                            .toInt()

                        printer.print(exitCode == 0)
                    } else {
                        printer.print("")
                    }

                    val failingTestsFile = toolOutDir.getBaselineFailingTestsPath()
                    if (failingTestsFile.isRegularFile()) {
                        val failingTests = failingTestsFile.bufferedReader()
                            .use { it.readLines() }
                            .filter { it.startsWith("--- ") }
                            .map { it.removePrefix("--- ") }
                            .toSet()

                        val expectedFailures = if (bugId.version == Defects4JWorkspace.BugVersion.FIXED) {
                            emptySet()
                        } else {
                            toolOutDir.getExpectedFailingTestsPath()
                                .bufferedReader()
                                .use { it.readLines() }
                                .filter { it.isNotBlank() }
                                .toSet()
                        }

                        printer.print(failingTests isSubsetOf expectedFailures)
                    } else {
                        printer.print("")
                    }

                    printer.println()
                }
            }

            return outputFile
        }

        override val formatBuilder: CSVFormat.Builder = CSVFormat.RFC4180.builder().apply {
            setHeader(
                "project_id",
                "bug_id",
                "test_case",
                "class-total",
                "class-pp",
                "class-p",
                "class-tp",
                "class-fp",
                "class-fn",
                "class-tn",
                "class-tpr",
                "class-fpr",
                "class-fnr",
                "class-tnr",
                "class-accuracy",
                "class-precision",
                "class-recall",
                "class-f1",
                "method-total",
                "method-pp",
                "method-p",
                "method-tp",
                "method-fp",
                "method-fn",
                "method-tn",
                "method-tpr",
                "method-fpr",
                "method-fnr",
                "method-tnr",
                "method-accuracy",
                "method-precision",
                "method-recall",
                "method-f1",
                "src-class-total",
                "src-class-pp",
                "src-class-p",
                "src-class-tp",
                "src-class-fp",
                "src-class-fn",
                "src-class-tn",
                "src-class-tpr",
                "src-class-fpr",
                "src-class-fnr",
                "src-class-tnr",
                "src-class-accuracy",
                "src-class-precision",
                "src-class-recall",
                "src-class-f1",
                "src-method-total",
                "src-method-pp",
                "src-method-p",
                "src-method-tp",
                "src-method-fp",
                "src-method-fn",
                "src-method-tn",
                "src-method-tpr",
                "src-method-fpr",
                "src-method-fnr",
                "src-method-tnr",
                "src-method-accuracy",
                "src-method-precision",
                "src-method-recall",
                "src-method-f1",
                "test-class-total",
                "test-class-pp",
                "test-class-p",
                "test-class-tp",
                "test-class-fp",
                "test-class-fn",
                "test-class-tn",
                "test-class-tpr",
                "test-class-fpr",
                "test-class-fnr",
                "test-class-tnr",
                "test-class-accuracy",
                "test-class-precision",
                "test-class-recall",
                "test-class-f1",
                "test-method-total",
                "test-method-pp",
                "test-method-p",
                "test-method-tp",
                "test-method-fp",
                "test-method-fn",
                "test-method-tn",
                "test-method-tpr",
                "test-method-fpr",
                "test-method-fnr",
                "test-method-tnr",
                "test-method-accuracy",
                "test-method-precision",
                "test-method-recall",
                "test-method-f1",
                "time_taken",
                "compile0",
                "compile1",
                "test_match1",
                "compile",
                "test_match"
            )
        }
    }

    object AggregatedMemberStats : AbstractAggregatedMemberStats() {

        override val defaultFilename: Path = Path("aggregated-member-stats.csv")

        override fun aggregateStats(outputDir: Path, outputFile: Path): Path {
            val testOutDirs = PredicatedFileCollector(outputDir)
                .collect {
                    val relPath = outputDir.relativize(it)
                    val fileName = relPath.fileName.toString()
                    relPath.nameCount == 2 && fileName.isNotBlank() && fileName.contains("::") && it.isDirectory()
                }
                .sorted()

            CSVPrinter(outputFile.bufferedWriter(), writerFormat).use { printer ->
                for (testOutDir in testOutDirs) {
                    val projectOutDir = testOutDir.parent.also { check(it.isDirectory()) }

                    val toolOutDir = ToolOutputDirectory(projectOutDir, testOutDir.last(), readOnly = true)

                    val (projectId, versionId) = projectOutDir.fileName
                        .toString()
                        .let { Defects4JWorkspace.ProjectRev.fromString(it) }

                    var totalTimeTaken = 0
                    val lastPass = toolOutDir.getLastPass() ?: 0u
                    for (passNum in 0u..lastPass) {
                        if (!hasOutput(toolOutDir)) {
                            break
                        }

                        printer.print(projectId)
                        printer.print(versionId)
                        printer.print(testOutDir.fileName)

                        // Skip pass number if there is no reduction output
                        if (hasOutput(toolOutDir, passNum)) {
                            printer.print(passNum)

                            if (!hasOutput(toolOutDir, passNum + 1u)) {
                                printer.print(true)
                            } else {
                                printer.print("")
                            }
                        } else {
                            printer.print("")
                            printer.print("")
                        }

                        dumpSourceRootStats(printer, toolOutDir.getClassStatPath(passNum, SourceRootOutput.ALL))
                        dumpSourceRootStats(printer, toolOutDir.getMethodStatPath(passNum, SourceRootOutput.ALL))

                        val timeTakenFile = toolOutDir.getRunningTimePath(passNum)
                        if (timeTakenFile.isRegularFile()) {
                            val timeTaken = timeTakenFile.bufferedReader()
                                .useLines { lines ->
                                    lines.first().also { checkNotNull(it.toIntOrNull()) }
                                }
                                .toInt()
                            totalTimeTaken += timeTaken

                            printer.print(timeTaken)
                            printer.print(totalTimeTaken)
                        } else {
                            printer.print("")
                            printer.print("")
                        }

                        val compile0File = toolOutDir.getDefaultCompilePath()
                        if (compile0File.isRegularFile()) {
                            val exitCode = compile0File.bufferedReader()
                                .useLines { lines ->
                                    lines.first().also { check(it.startsWith("Pre-Reduce Compile ExitCode: ")) }
                                }
                                .removePrefix("Pre-Reduce Compile ExitCode: ")
                                .toInt()

                            printer.print(exitCode == 0)
                        } else {
                            printer.print("")
                        }

                        val compile1File = toolOutDir.getGroundTruthCompilePath()
                        if (compile1File.isRegularFile()) {
                            val exitCode = compile1File.bufferedReader()
                                .useLines { lines ->
                                    lines.first().also { check(it.startsWith("Pre-Reduce Compile ExitCode: ")) }
                                }
                                .removePrefix("Pre-Reduce Compile ExitCode: ")
                                .toInt()

                            printer.print(exitCode == 0)
                        } else {
                            printer.print("")
                        }

                        val failingTests1File = toolOutDir.getGroundTruthFailingTestsPath()
                        if (failingTests1File.isRegularFile()) {
                            val failingTests = failingTests1File.bufferedReader()
                                .use { it.readLines() }
                                .filter { it.startsWith("--- ") }
                                .map { it.removePrefix("--- ") }
                                .toSet()

                            val expectedFailures = if (versionId.version == Defects4JWorkspace.BugVersion.FIXED) {
                                emptySet()
                            } else {
                                toolOutDir.getExpectedFailingTestsPath()
                                    .bufferedReader()
                                    .use { it.readLines() }
                                    .filter { it.isNotBlank() }
                                    .toSet()
                            }

                            printer.print(failingTests isSubsetOf expectedFailures)
                        } else {
                            printer.print("")
                        }

                        val compileFile = toolOutDir.getCompilePath(passNum)
                        if (compileFile.isRegularFile()) {
                            val exitCode = compileFile.bufferedReader()
                                .useLines { lines ->
                                    lines.first().also { check(it.startsWith("Compile ExitCode: ")) }
                                }
                                .removePrefix("Compile ExitCode: ")
                                .toInt()

                            printer.print(exitCode == 0)
                        } else {
                            printer.print("")
                        }

                        // test_match
                        val failingTestsResult = getFailingTestResult(toolOutDir, versionId, passNum)
                        printer.print(failingTestsResult ?: "")

                        printer.println()
                    }
                }
            }

            return outputFile
        }

        override val formatBuilder: CSVFormat.Builder = CSVFormat.RFC4180.builder().apply {
            setHeader(
                "project_id",
                "bug_id",
                "test_case",
                "pass_num",
                "last_pass",
                "class-total",
                "class-pp",
                "class-p",
                "class-tp",
                "class-fp",
                "class-fn",
                "class-tn",
                "class-tpr",
                "class-fpr",
                "class-fnr",
                "class-tnr",
                "class-accuracy",
                "class-precision",
                "class-recall",
                "class-f1",
                "method-total",
                "method-pp",
                "method-p",
                "method-tp",
                "method-fp",
                "method-fn",
                "method-tn",
                "method-tpr",
                "method-fpr",
                "method-fnr",
                "method-tnr",
                "method-accuracy",
                "method-precision",
                "method-recall",
                "method-f1",
                "time_taken",
                "total_time_taken",
                "compile0",
                "compile1",
                "test_match1",
                "compile",
                "test_match"
            )
        }
    }

    object AggregatedMemberAllStats : AbstractAggregatedMemberStats() {

        override val defaultFilename: Path = Path("aggregated-member-all-stats.csv")

        override fun aggregateStats(outputDir: Path, outputFile: Path): Path {
            val testOutDirs = PredicatedFileCollector(outputDir)
                .collect {
                    val relPath = outputDir.relativize(it)
                    val fileName = relPath.fileName.toString()
                    relPath.nameCount == 2 && fileName.isNotBlank() && fileName.contains("::") && it.isDirectory()
                }
                .sorted()

            CSVPrinter(outputFile.bufferedWriter(), writerFormat).use { printer ->
                for (testOutDir in testOutDirs) {
                    val projectOutDir = testOutDir.parent.also { check(it.isDirectory()) }

                    val toolOutDir = ToolOutputDirectory(projectOutDir, testOutDir.last(), readOnly = true)

                    val (projectId, versionId) = projectOutDir.fileName
                        .toString()
                        .let { Defects4JWorkspace.ProjectRev.fromString(it) }

                    var totalTimeTaken = 0
                    val lastPass = toolOutDir.getLastPass() ?: 0u
                    for (passNum in 0u..lastPass) {
                        if (!hasOutput(toolOutDir)) {
                            break
                        }

                        printer.print(projectId)
                        printer.print(versionId)
                        printer.print(testOutDir.fileName)

                        // Skip pass number if there is no reduction output
                        if (hasOutput(toolOutDir, passNum)) {
                            printer.print(passNum)

                            if (!hasOutput(toolOutDir, passNum + 1u)) {
                                printer.print(true)
                            } else {
                                printer.print("")
                            }
                        } else {
                            printer.print("")
                            printer.print("")
                        }

                        arrayOf(
                            SourceRootOutput.ALL,
                            SourceRootOutput.SOURCE,
                            SourceRootOutput.TEST
                        ).forEach { sourceRoot ->
                            dumpSourceRootStats(printer, toolOutDir.getClassStatPath(passNum, sourceRoot))
                            dumpSourceRootStats(printer, toolOutDir.getMethodStatPath(passNum, sourceRoot))
                        }

                        val timeTakenFile = toolOutDir.getRunningTimePath(passNum)
                        if (timeTakenFile.isRegularFile()) {
                            val timeTaken = timeTakenFile.bufferedReader()
                                .useLines { lines ->
                                    lines.first().also { checkNotNull(it.toIntOrNull()) }
                                }
                                .toInt()
                            totalTimeTaken += timeTaken

                            printer.print(timeTaken)
                            printer.print(totalTimeTaken)
                        } else {
                            printer.print("")
                            printer.print("")
                        }

                        val compile0File = toolOutDir.getDefaultCompilePath()
                        if (compile0File.isRegularFile()) {
                            val exitCode = compile0File.bufferedReader()
                                .useLines { lines ->
                                    lines.first().also { check(it.startsWith("Pre-Reduce Compile ExitCode: ")) }
                                }
                                .removePrefix("Pre-Reduce Compile ExitCode: ")
                                .toInt()

                            printer.print(exitCode == 0)
                        } else {
                            printer.print("")
                        }

                        val compile1File = toolOutDir.getGroundTruthCompilePath()
                        if (compile1File.isRegularFile()) {
                            val exitCode = compile1File.bufferedReader()
                                .useLines { lines ->
                                    lines.first().also { check(it.startsWith("Pre-Reduce Compile ExitCode: ")) }
                                }
                                .removePrefix("Pre-Reduce Compile ExitCode: ")
                                .toInt()

                            printer.print(exitCode == 0)
                        } else {
                            printer.print("")
                        }

                        val failingTests1File = toolOutDir.getGroundTruthFailingTestsPath()
                        if (failingTests1File.isRegularFile()) {
                            val failingTests = failingTests1File.bufferedReader()
                                .use { it.readLines() }
                                .filter { it.startsWith("--- ") }
                                .map { it.removePrefix("--- ") }
                                .toSet()

                            val expectedFailures = if (versionId.version == Defects4JWorkspace.BugVersion.FIXED) {
                                emptySet()
                            } else {
                                toolOutDir.getExpectedFailingTestsPath()
                                    .bufferedReader()
                                    .use { it.readLines() }
                                    .filter { it.isNotBlank() }
                                    .toSet()
                            }

                            printer.print(failingTests isSubsetOf expectedFailures)
                        } else {
                            printer.print("")
                        }

                        val compileFile = toolOutDir.getCompilePath(passNum)
                        if (compileFile.isRegularFile()) {
                            val exitCode = compileFile.bufferedReader()
                                .useLines { lines ->
                                    lines.first().also { check(it.startsWith("Compile ExitCode: ")) }
                                }
                                .removePrefix("Compile ExitCode: ")
                                .toInt()

                            printer.print(exitCode == 0)
                        } else {
                            printer.print("")
                        }

                        // test_match
                        val failingTestsResult = getFailingTestResult(toolOutDir, versionId, passNum)
                        printer.print(failingTestsResult ?: "")

                        printer.println()
                    }
                }
            }

            return outputFile
        }

        override val formatBuilder: CSVFormat.Builder = CSVFormat.RFC4180.builder().apply {
            setHeader(
                "project_id",
                "bug_id",
                "test_case",
                "pass_num",
                "last_pass",
                "class-total",
                "class-pp",
                "class-p",
                "class-tp",
                "class-fp",
                "class-fn",
                "class-tn",
                "class-tpr",
                "class-fpr",
                "class-fnr",
                "class-tnr",
                "class-accuracy",
                "class-precision",
                "class-recall",
                "class-f1",
                "method-total",
                "method-pp",
                "method-p",
                "method-tp",
                "method-fp",
                "method-fn",
                "method-tn",
                "method-tpr",
                "method-fpr",
                "method-fnr",
                "method-tnr",
                "method-accuracy",
                "method-precision",
                "method-recall",
                "method-f1",
                "src-class-total",
                "src-class-pp",
                "src-class-p",
                "src-class-tp",
                "src-class-fp",
                "src-class-fn",
                "src-class-tn",
                "src-class-tpr",
                "src-class-fpr",
                "src-class-fnr",
                "src-class-tnr",
                "src-class-accuracy",
                "src-class-precision",
                "src-class-recall",
                "src-class-f1",
                "src-method-total",
                "src-method-pp",
                "src-method-p",
                "src-method-tp",
                "src-method-fp",
                "src-method-fn",
                "src-method-tn",
                "src-method-tpr",
                "src-method-fpr",
                "src-method-fnr",
                "src-method-tnr",
                "src-method-accuracy",
                "src-method-precision",
                "src-method-recall",
                "src-method-f1",
                "test-class-total",
                "test-class-pp",
                "test-class-p",
                "test-class-tp",
                "test-class-fp",
                "test-class-fn",
                "test-class-tn",
                "test-class-tpr",
                "test-class-fpr",
                "test-class-fnr",
                "test-class-tnr",
                "test-class-accuracy",
                "test-class-precision",
                "test-class-recall",
                "test-class-f1",
                "test-method-total",
                "test-method-pp",
                "test-method-p",
                "test-method-tp",
                "test-method-fp",
                "test-method-fn",
                "test-method-tn",
                "test-method-tpr",
                "test-method-fpr",
                "test-method-fnr",
                "test-method-tnr",
                "test-method-accuracy",
                "test-method-precision",
                "test-method-recall",
                "test-method-f1",
                "time_taken",
                "total_time_taken",
                "compile0",
                "compile1",
                "test_match1",
                "compile",
                "test_match"
            )
        }
    }
}