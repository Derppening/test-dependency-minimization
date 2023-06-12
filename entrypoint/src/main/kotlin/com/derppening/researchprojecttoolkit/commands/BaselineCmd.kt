package com.derppening.researchprojecttoolkit.commands

import com.derppening.researchprojecttoolkit.GlobalConfiguration
import com.derppening.researchprojecttoolkit.defects4j.ProjectInfoExporter
import com.derppening.researchprojecttoolkit.model.*
import com.derppening.researchprojecttoolkit.util.*
import com.derppening.researchprojecttoolkit.util.posix.runProcess
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.arguments.transformAll
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import hk.ust.cse.castle.toolkit.jvm.util.RuntimeAssertions.unreachable
import javassist.bytecode.Descriptor
import java.nio.file.Path
import kotlin.io.path.Path

class BaselineCmd : CliktCommand(help = HELP_TEXT, name = "bl") {

    init {
        subcommands(SUBCOMMANDS)
    }

    override fun run() = Unit

    companion object {

        private const val HELP_TEXT = ""
        private val SUBCOMMANDS = listOf(
            BaselineCoverage(),
            BaselineExport(),
        )
    }
}

private class BaselineCoverage : CliktCommand(help = HELP_TEXT, name = "coverage") {

    private val globalConfig by requireObject<GlobalConfiguration>()

    private val project by option("-p")
        .convert { Defects4JWorkspace.ProjectID.fromString(it) }
        .required()
    private val version by option("-v")
        .convert { Defects4JWorkspace.VersionID.fromString(it) }
        .required()
    private val testCase by option("-t")
        .convert { TestCase.fromD4JQualifiedName(it) }
        .required()

    private val selectClass by option("-c", "--select-class")
    private val selectMethod by option("-m", "--select-method")
    private val selectLine by option("-l", "--select-line")

    private val toolOutDir by option("-d", help = "Tool output directory to read from.")
        .path(mustExist = true, canBeFile = false)
        .defaultLazy { globalConfig.cmdlineOpts.baselineDir }

    private fun findPackageFromCoverage(packageName: String, coverageData: CoverageData): MergedCoverageData.Package {
        val qualifiedPackageName = packageName.replace('/', '.')

        val jacocoCov = coverageData.jacocoCov
        val jacocoPackageCov = if (jacocoCov != null) {
            val jacocoPackageName = qualifiedPackageName.replace('.', '/')

            jacocoCov.value.report
                .packages.singleOrNull { it.name == jacocoPackageName }
        } else null

        val coberturaPackageCov = if (coverageData is CoverageData.Full) {
            coverageData.coberturaCov.value.coverage
                .packages.singleOrNull { it.name == qualifiedPackageName }
        } else null

        return MergedCoverageData.Package(coberturaPackageCov, jacocoPackageCov)
    }

    private fun findClassFromCoverage(clazzName: String, coverageData: CoverageData): MergedCoverageData.Class {
        val qualifiedClassName = clazzName.replace('/', '.')

        val nameComponents = qualifiedClassName.split('.')
        val inferredPackageName = nameComponents.dropLast(1).joinToString(".")

        val packageCov = findPackageFromCoverage(inferredPackageName, coverageData)

        val classByJacocoCoverage = if (packageCov.isJacocoDataPresent) {
            val jacocoCov = checkNotNull(packageCov.jacocoCov)

            val jacocoClassName = qualifiedClassName.replace('.', '/')

            jacocoCov.classes.singleOrNull { it.name == jacocoClassName }
        } else null

        val classByCoberturaCoverage = if (packageCov.isCoberturaDataPresent) {
            val coberturaCov = checkNotNull(packageCov.coberturaCov)

            coberturaCov.classes.singleOrNull { it.name == qualifiedClassName }
        } else null

        return MergedCoverageData.Class(classByCoberturaCoverage, classByJacocoCoverage)
    }

    private fun findMethodFromCoverage(
        qualifiedMethodName: String,
        coverageData: CoverageData
    ): List<MergedCoverageData.Method> {
        val (className, methodName) = qualifiedMethodName.split("::")

        val classCov = findClassFromCoverage(className, coverageData)

        val methodsByJacocoCoverage = if (classCov.isJacocoDataPresent) {
            val jacocoCov = classCov.jacocoCov!!

            jacocoCov.methods.filter { it.name == methodName }
        } else null
        val methodsByCoberturaCoverage = if (classCov.isCoberturaDataPresent) {
            val coberturaCov = classCov.coberturaCov!!

            coberturaCov.methods.filter { it.name == methodName }
        } else null

        return when {
            methodsByJacocoCoverage != null -> {
                methodsByJacocoCoverage.map { jacocoMethodCov ->
                    MergedCoverageData.Method(
                        methodsByCoberturaCoverage?.find { it.signature == jacocoMethodCov.desc },
                        jacocoMethodCov
                    )
                }
            }
            methodsByCoberturaCoverage != null -> {
                methodsByCoberturaCoverage
                    .map { MergedCoverageData.Method(it, null) }
            }
            else -> emptyList()
        }
    }

    private fun findLineFromCoverage(
        filename: Path,
        lineNo: Int,
        coverageData: CoverageData
    ): MergedCoverageData.Line {
        val fileDir = filename.parent

        val inferredPackageName = fileDir.toString().replace('/', '.')

        val packageCov = findPackageFromCoverage(inferredPackageName, coverageData)

        val classByJacocoCoverage = if (packageCov.isJacocoDataPresent) {
            val jacocoCov = checkNotNull(packageCov.jacocoCov)

            jacocoCov.sourceFiles
                .singleOrNull { it.name == filename.fileName }
                ?.lines
                ?.singleOrNull { it.nr == lineNo }
        } else null

        val classByCoberturaCoverage = if (packageCov.isCoberturaDataPresent) {
            val coberturaCov = checkNotNull(packageCov.coberturaCov)

            coberturaCov.classes
                .filter { it.filename == filename }
                .flatMap { it.lines }
                .singleOrNull { it.number == lineNo }
        } else null

        return MergedCoverageData.Line(classByCoberturaCoverage, classByJacocoCoverage)
    }

    private fun List<JacocoXML.Counter>.outputJacocoCounter(header: String, counterType: JacocoXML.CounterType) {
        val counter = getCounterForTypeOrNull(counterType)
        val total = counter?.let { it.covered + it.missed }
        val rate = counter?.let { it.covered.toDouble() / (it.covered + it.missed).toDouble() }

        if (counter != null) {
            LOGGER.info("$header: ${counter.covered}/${total!!} (${rate!!})")
        } else {
            LOGGER.info("$header: (no data)")
        }
    }

    private fun outputClassCoverage(coverageData: CoverageData) {
        val clazzName = checkNotNull(selectClass)
        val qualifiedClassName = clazzName.replace('/', '.')

        val merged = findClassFromCoverage(clazzName, coverageData)
        val coberturaClassCov = merged.coberturaCov
        val jacocoClassCov = merged.jacocoCov

        LOGGER.info("Coverage Data for Class `$qualifiedClassName`:")
        if (coberturaClassCov != null) {
            val lines = coberturaClassCov.lines
                .let { it.count { it.hits > 0 } to it.count() }

            LOGGER.info("    Cobertura:")
            LOGGER.info("        Line Rate  : ${lines.first}/${lines.second} (${coberturaClassCov.lineRate})")
            LOGGER.info("        Branch Rate: ${coberturaClassCov.branchRate}")

            LOGGER.info("        Reachable  : ${merged.coberturaIsHit}")
        } else {
            LOGGER.info("    Cobertura: (no data)")
        }

        if (jacocoClassCov != null) {
            LOGGER.info("    Jacoco:")
            jacocoClassCov.counters.outputJacocoCounter("        Instructions", JacocoXML.CounterType.INSTRUCTION)
            jacocoClassCov.counters.outputJacocoCounter("        Lines       ", JacocoXML.CounterType.LINE)
            jacocoClassCov.counters.outputJacocoCounter("        Branches    ", JacocoXML.CounterType.COMPLEXITY)
            jacocoClassCov.counters.outputJacocoCounter("        Methods     ", JacocoXML.CounterType.METHOD)
            jacocoClassCov.counters.outputJacocoCounter("        Classes     ", JacocoXML.CounterType.CLASS)

            LOGGER.info("        Reachable   : ${merged.jacocoIsHit}")
        } else {
            LOGGER.info("    Jacoco: (no data)")
        }

        LOGGER.info("    Data Sound: ${merged.isDataSound}")
        LOGGER.info("    Reachable : ${merged.isReachable}")
    }

    private fun outputMethodCoverage(coverageData: CoverageData) {
        val methodName = checkNotNull(selectMethod)
        val qualifiedMethodName = methodName.replace('/', '.')

        val merged = findMethodFromCoverage(qualifiedMethodName, coverageData)

        LOGGER.info("Coverage Data for Method `$qualifiedMethodName`: ${merged.size} matches")
        merged.forEach { matchedMethod ->
            val coberturaCov = matchedMethod.coberturaCov
            val jacocoCov = matchedMethod.jacocoCov

            val sig = matchedMethod.jacocoCov?.desc ?: matchedMethod.coberturaCov?.signature
            LOGGER.info("    $sig // ${Descriptor.toString(sig)}:")

            if (coberturaCov != null) {
                val lines = coberturaCov.lines
                    .let { it.count { it.hits > 0 } to it.count() }

                LOGGER.info("        Cobertura:")
                LOGGER.info("            Line Rate  : ${lines.first}/${lines.second} (${coberturaCov.lineRate})")
                LOGGER.info("            Branch Rate: ${coberturaCov.branchRate}")

                LOGGER.info("            Reachable  : ${matchedMethod.coberturaIsHit}")
            } else {
                LOGGER.info("        Cobertura: (no data)")
            }

            if (jacocoCov != null) {
                LOGGER.info("        Jacoco:")
                jacocoCov.counters.outputJacocoCounter("            Instructions", JacocoXML.CounterType.INSTRUCTION)
                jacocoCov.counters.outputJacocoCounter("            Lines       ", JacocoXML.CounterType.LINE)
                jacocoCov.counters.outputJacocoCounter("            Branches    ", JacocoXML.CounterType.COMPLEXITY)
                jacocoCov.counters.outputJacocoCounter("            Methods     ", JacocoXML.CounterType.METHOD)

                LOGGER.info("            Reachable   : ${matchedMethod.jacocoIsHit}")
            } else {
                LOGGER.info("        Jacoco: (no data)")
            }

            LOGGER.info("        Data Sound: ${matchedMethod.isDataSound}")
            LOGGER.info("        Reachable : ${matchedMethod.isReachable}")
        }
    }

    private fun outputLineCoverage(coverageData: CoverageData) {
        val line = checkNotNull(selectLine)
        val (filename, lineNo) = line.split(':')

        val merged = findLineFromCoverage(Path(filename), lineNo.toInt(), coverageData)
        val coberturaClassCov = merged.coberturaCov
        val jacocoClassCov = merged.jacocoCov

        LOGGER.info("Coverage Data for Line `$line`:")
        if (coberturaClassCov != null) {
            LOGGER.info("    Cobertura:")
            LOGGER.info("        Reachable  : ${merged.coberturaIsHit}")
        } else {
            LOGGER.info("    Cobertura: (no data)")
        }

        if (jacocoClassCov != null) {
            LOGGER.info("    Jacoco:")
            LOGGER.info("        Reachable   : ${merged.jacocoIsHit}")
        } else {
            LOGGER.info("    Jacoco: (no data)")
        }

        LOGGER.info("    Data Sound: ${merged.isDataSound}")
        LOGGER.info("    Reachable : ${merged.isReachable}")
    }

    override fun run() {
        val projectRev = Defects4JWorkspace.ProjectRev(project, version)

        requireNotNull(listOfNotNull(selectClass, selectMethod, selectLine).singleOrNull()) {
            "Only one of --select-class, --select-method, or --select-line may be specified"
        }

        val mergedCoverage = ToolOutputDirectory(toolOutDir, projectRev, testCase, true).readCoverageData()

        when {
            selectClass != null -> outputClassCoverage(mergedCoverage)
            selectMethod != null -> outputMethodCoverage(mergedCoverage)
            selectLine != null -> outputLineCoverage(mergedCoverage)
            else -> unreachable()
        }
    }

    companion object {

        private const val HELP_TEXT = ""

        private val LOGGER = Logger<BaselineCoverage>()
    }
}

private class BaselineExport : CliktCommand(help = HELP_TEXT, name = "export") {

    private val d4jDir by option("-dD", help = "The directory to Defects4J.")
        .path(mustExist = true, canBeFile = false)
    private val failAtEnd by option(
        "-fae",
        "--fail-at-end",
        help = "Continue running even if a project failed to execute."
    )
        .flag()
    private val fork by option(
        "-f",
        help = "If specified, runs the comparison using the given command (usually `java`) instead of using this instance."
    )
    private val output by option("-o", help = "Console or file to output to.")
        .path()
        .default(Path("/out"))
    private val noBaseline by option("-B", help = "Ignores the presence of exported files in the baseline.")
        .flag()
    private val projectId by option("-p", help = "Defects4J project ID.")
        .convert { Defects4JWorkspace.ProjectID.fromString(it) }
    private val retries by option("-r", "--retries", help = "Number of times to retry when forked process times out.")
        .int()
        .default(3)
    private val testMethodFilter by option(
        "-tm", "--test-method-filter",
        help = "Wildcard expression filtering the test cases to execute. By default executes all tests in relevant test classes.",
        metavar = "[relevant|failing-test|failing-class|all|REGEX]"
    )
        .convert { TestMethodFilter.parseString(it) }
        .default(TestMethodFilter.FailingTestClasses)
    private val versionId by option("-v", help = "Defects4J bug ID.")
        .convert { Defects4JWorkspace.VersionID.fromString(it) }
    private val versions by argument(help = "The set of versions to batch run. The syntax of each version may be \$projectId, \$projectId:\$versionId, or \$projectId:\$beginVersionId-\$endVersionId")
        .multiple()
        .transformAll { args -> args.map { Defects4JBugVersionRange.fromString(it) } }
    private val workspaceDir by option("-dW", help = "The directory to the repository workspace.")
        .path()

    private fun <T> Result<T>.handleError(): Result<T> {
        val ex = exceptionOrNull()
        return if (ex != null && !failAtEnd) {
            throw ex
        } else {
            this
        }
    }

    private fun runCompare(
        configuration: ProjectInfoExporter.Configuration,
        projectRev: Defects4JWorkspace.ProjectRev,
        forkProcess: Boolean
    ): Boolean {
        // Only fork if we are NOT running a single revision in a single mode at a single granularity
        val forkCmd = fork.takeIf { forkProcess }

        return if (forkCmd != null) {
            val (cmd, args) = buildList {
                addAll(forkCmd.split(' ').filter { it.isNotBlank() })

                add("bl")
                add("export")
                add("-o")
                add(output.toString())
                add("-tm")
                add(testMethodFilter.cmdlineOpt.first())
                if (d4jDir != null) {
                    add("-dD")
                    add(d4jDir.toString())
                }
                if (noBaseline) {
                    add("-B")
                }
                if (workspaceDir != null) {
                    add("-dW")
                    add(workspaceDir.toString())
                }

                add("-p")
                add(projectRev.projectId.toString())
                add("-v")
                add(projectRev.versionId.toString())
            }.let { it[0] to it.drop(1).toTypedArray() }

            LOGGER.info("Forking new toolkit process to execute $projectRev")

            var timedOut: Boolean
            var exitCode = ExitCode(137)
            for (i in 0 until retries) {
                val processOutput = runProcess(cmd, *args, timeout = null) {
                    inheritIO()
                }
                timedOut = processOutput.isTimedOut
                if (timedOut) {
                    LOGGER.warn("$projectRev timed out! Retry Count: $i")

                    exitCode = ExitCode(137)
                } else {
                    exitCode = ExitCode(processOutput.process.exitValue())
                    break
                }
            }

            if (!exitCode.isSuccess) {
                LOGGER.error("Forked Process of Baseline Export for $projectRev failed to execute")
            }

            exitCode.isSuccess
        } else {
            LOGGER.info("Running baseline for $projectRev")
            runCatching {
                ProjectInfoExporter(
                    configuration,
                    projectRev,
                    testMethodFilter,
                    output
                ).run()
            }.onSuccess {
                LOGGER.info("Baseline Export for $projectRev executed successfully")
            }.onFailure {
                LOGGER.error("Baseline Export for $projectRev failed", it)
            }.handleError().isSuccess
        }
    }

    override fun run() {
        val config = ProjectInfoExporter.Configuration(
            workspaceDir ?: Path("/workspace"),
            d4jDir ?: Path("/defects4j"),
            noBaseline
        )

        if (projectId != null && versionId != null) {
            val projectRev = Defects4JWorkspace.ProjectRev(checkNotNull(projectId), checkNotNull(versionId))
            runCompare(
                config,
                projectRev,
                false
            )

            return
        }

        if (projectId != null || versionId != null) {
            error("Project ID and Version ID must be specified in conjunction with each other")
        }

        val projectRevsToRun = if (versions.isEmpty()) {
            Defects4JWorkspace.ProjectID.values().associateWith { it.projectValidBugs.toSet() }
        } else {
            versions.groupBy { it.projectId }
                .mapValues { (_, v) ->
                    v.map { it.versionRange }.fold(listOf<Int>()) { acc, it -> acc + it.toList() }
                }
                .mapValues { (k, v) -> (v intersect k.projectValidBugs).toSet() }
        }
        projectRevsToRun.forEach { (projectId, bugIds) ->
            bugIds.forEach { bugId ->
                Defects4JWorkspace.BugVersion.values().forEach { bugVersion ->
                    val projectRev = Defects4JWorkspace.ProjectRev(projectId, bugId, bugVersion)
                    runCompare(
                        config,
                        projectRev,
                        projectRevsToRun.keys.size != 1 || bugIds.size != 1
                    )
                }
            }
        }
    }

    companion object {

        private const val HELP_TEXT = "Exports baseline information for a bug."

        private val LOGGER = Logger<BaselineExport>()
    }
}
