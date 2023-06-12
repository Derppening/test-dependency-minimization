package com.derppening.researchprojecttoolkit.commands

import com.derppening.researchprojecttoolkit.defects4j.AbstractComparisonRunner
import com.derppening.researchprojecttoolkit.defects4j.IndividualTestCaseComparisonRunner
import com.derppening.researchprojecttoolkit.model.Defects4JBugVersionRange
import com.derppening.researchprojecttoolkit.model.JacocoXML
import com.derppening.researchprojecttoolkit.model.StandardCsvFormats
import com.derppening.researchprojecttoolkit.model.TestMethodFilter
import com.derppening.researchprojecttoolkit.tool.reducer.AbstractReducer
import com.derppening.researchprojecttoolkit.util.*
import com.derppening.researchprojecttoolkit.util.posix.runProcess
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.arguments.transformAll
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import hk.ust.cse.castle.toolkit.jvm.util.RuntimeAssertions.unreachable
import org.dom4j.io.SAXReader
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.Path
import kotlin.io.path.bufferedReader
import kotlin.io.path.inputStream
import kotlin.io.path.isRegularFile
import kotlin.system.exitProcess

/**
 * Banned bugs which cannot be executed due to no fault of my own.
 */
private val BUG_BLACKLIST = listOf(
    // Cli-15f: MALFORMED when running JUnit JAR
    Defects4JWorkspace.ProjectRev(
        Defects4JWorkspace.ProjectID.CLI,
        Defects4JWorkspace.VersionID(15, Defects4JWorkspace.BugVersion.BUGGY)
    ),
    Defects4JWorkspace.ProjectRev(
        Defects4JWorkspace.ProjectID.CLI,
        Defects4JWorkspace.VersionID(15, Defects4JWorkspace.BugVersion.FIXED)
    ),
    // Cli-16f: MALFORMED when loading JUnit JAR
    Defects4JWorkspace.ProjectRev(
        Defects4JWorkspace.ProjectID.CLI,
        Defects4JWorkspace.VersionID(16, Defects4JWorkspace.BugVersion.BUGGY)
    ),
    Defects4JWorkspace.ProjectRev(
        Defects4JWorkspace.ProjectID.CLI,
        Defects4JWorkspace.VersionID(16, Defects4JWorkspace.BugVersion.FIXED)
    ),
)

class Defects4JCmd : CliktCommand(help = HELP_TEXT, name = "d4j") {

    init {
        subcommands(SUBCOMMANDS)
    }

    override fun run() = Unit

    companion object {

        private const val HELP_TEXT = "Defects4J-related commands."
        private val SUBCOMMANDS = listOf(
            Defects4JAggregateStats(),
            Defects4JCompare(),
            Defects4JCheckout(),
            Defects4JCoverageJacoco(),
            Defects4JInstrumentCobertura(),
            Defects4JInstrumentJacoco(),
            Defects4JExport(),
            Defects4JRunCoverageJacoco(),
        )
    }
}

private class Defects4JAggregateStats : CliktCommand(help = HELP_TEXT, name = "aggregate-stats") {

    private val all by option("--all")
        .flag()
    private val forClass by option("--class")
        .flag()
    private val forMember by option("--member")
        .flag()
    private val output by option()
        .path()
    private val dir by argument()
        .path(mustExist = true, canBeFile = false)

    override fun run() {
        if (!(forClass xor forMember)) {
            error("Only one of --class and --member should be specified")
        }

        val aggregator = if (forClass) {
            if (all) {
                StandardCsvFormats.AggregatedClassAllStats
            } else {
                StandardCsvFormats.AggregatedClassStats
            }
        } else {
            if (all) {
                StandardCsvFormats.AggregatedMemberAllStats
            } else {
                StandardCsvFormats.AggregatedMemberStats
            }
        }

        val outputFile = output ?: dir.resolve(aggregator.defaultFilename)
        aggregator.aggregateStats(dir, outputFile)
    }

    companion object {

        private const val HELP_TEXT = "Aggregates statistics from executed subjects."
    }
}

private class Defects4JCompare : CliktCommand(help = HELP_TEXT, name = "compare") {

    private enum class AggregateStatsLevel {
        NONE,
        BRIEF,
        ALL
    }

    private val aggregateStats by option("--aggregate-stats", help = "Aggregate statistics at the end.")
        .enum<AggregateStatsLevel> { it.asCmdlineOpt() }
        .default(AggregateStatsLevel.BRIEF)
    private val buggy by option("--buggy", help = "Only run the buggy version.")
        .flag()
    private val continuePassIf by option("--continue-pass-if", help = "Condition to continue running reduction passes.")
        .enum<AbstractComparisonRunner.IterationContinueCondition> { it.asCmdlineOpt() }
        .default(AbstractComparisonRunner.IterationContinueCondition.ALWAYS)
    private val d4jDir by option("-dD", help = "The directory to Defects4J.")
        .path(mustExist = true, canBeFile = false)
    private val enableAssertions by option("-ea", "--enableassertions", help = "")
        .flag("-da", "--disableassertions", default = true)
    private val failFast by option(
        "-ff", "--fail-fast",
        help = "Stop running immediately when a project failed to execute."
    )
        .flag()
    private val fork by option(
        "-f",
        help = "If specified, runs the comparison using the given command (usually `java`) instead of using this instance."
    )
    private val fixed by option("--fixed", help = "Only run the fixed version.")
        .flag()
    private val javacSourceLevel by option("-source", help = "Provide source compatibility with specified release.")
        .convert { JavaRelease.fromCmdlineOption(it) }
    private val javacReleaseLevel by option("--release", help = "Compile as if for a specific VM version; Equivalent to `-source <N> -target <N>`.")
        .convert { JavaRelease.fromCmdlineOption(it) }
    private val javacTargetLevel by option("-target", help = "Generate class files for specific VM version.")
        .convert { JavaRelease.fromCmdlineOption(it) }
    private val noOptimize by option("-O", "--no-optimize", help = "Do not run optimizations when performing reduction.")
        .enum<AbstractReducer.Optimization> { it.asCmdlineOpt() }
        .multiple()
        .unique()
    private val outputDetails by option(
        "-oD", "--output-details",
        help = "Outputs details of why each class is being retained."
    )
        .flag()
    private val outputDir by option("-o", "--output", help = "The root directory to output reduction information to.")
        .path()
        .default(Path("/out"))
    private val projectId by option("-p", help = "Defects4J project ID.")
        .convert { Defects4JWorkspace.ProjectID.fromString(it) }
    private val maxPasses by option("--max-passes", help = "Number of reduction passes to execute for a single bug.")
        .int()
        .convert { if (it < 0) UInt.MAX_VALUE else it.toUInt() }
        .default(UInt.MAX_VALUE)
    private val test by option("--test", help = "Level to testing to perform on the minimized sources.")
        .enum<AbstractComparisonRunner.TestLevel> { it.asCmdlineOpt() }
        .default(AbstractComparisonRunner.TestLevel.TEST)
    private val threads by option("-T", "--threads", help = "Number of threads to execute reduction task.")
        .int()
    private val timeout by option("-t", "--timeout", help = "Timeout for compilation and testing.")
        .int()
        .default(60)
    private val testMethodFilter by option(
        "-tm", "--test-method-filter",
        help = "Wildcard expression filtering the test cases to execute. By default executes all tests in relevant test classes.",
        metavar = "[relevant|failing|all|REGEX]"
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

    private fun parseJavacEffectiveVersions(): Defects4JWorkspace.JavacVersionOverride =
        Defects4JWorkspace.JavacVersionOverride.fromCmdline(javacReleaseLevel, javacSourceLevel, javacTargetLevel)

    private fun <T> Result<T>.handleError(): Result<T> {
        val ex = exceptionOrNull()
        return if (ex != null && failFast) {
            throw ex
        } else {
            this
        }
    }

    private fun runCompare(
        configuration: AbstractComparisonRunner.Configuration,
        projectRev: Defects4JWorkspace.ProjectRev,
        forkProcess: Boolean
    ): Boolean {
        if (projectRev in BUG_BLACKLIST) {
            LOGGER.info("Skipping $projectRev because it is in bug blacklist")
            return true
        }

        // Only fork if we are NOT running a single revision in a single mode at a single granularity
        val forkCmd = fork.takeIf { forkProcess }

        return if (forkCmd != null) {
            val (cmd, args) = buildList {
                addAll(forkCmd.split(' ').filter { it.isNotBlank() })

                add("d4j")
                add("compare")
                add("-p")
                add(projectRev.projectId.toString())
                add("-v")
                add(projectRev.versionId.toString())
                add("-o")
                add(outputDir.toString())
                add("-t")
                add(timeout.toString())
                add("-tm")
                add(testMethodFilter.cmdlineOpt.first())
                add("--max-passes")
                if (maxPasses > Int.MAX_VALUE.toUInt()) {
                    add((-1).toString())
                } else {
                    add(maxPasses.toString())
                }
                add("--test")
                add(test.asCmdlineOpt())

                if (d4jDir != null) {
                    add("-dD")
                    add(d4jDir.toString())
                }
                if (workspaceDir != null) {
                    add("-dW")
                    add(workspaceDir.toString())
                }
                if (javacReleaseLevel != null) {
                    add("--release")
                    add(javacReleaseLevel.toString())
                }
                if (javacSourceLevel != null) {
                    add("-source")
                    add(javacSourceLevel.toString())
                }
                if (javacTargetLevel != null) {
                    add("-target")
                    add(javacTargetLevel.toString())
                }
                if (threads != null) {
                    add("-T")
                    add(threads.toString())
                }
                if (noOptimize.isNotEmpty()) {
                    add("--no-optimize")
                    add(noOptimize.joinToString(",") { it.asCmdlineOpt() })
                }
                if (outputDetails) {
                    add("-oD")
                }

                add("--aggregate-stats")
                add("none")
            }.let { it[0] to it.drop(1).toTypedArray() }

            LOGGER.info("Forking new toolkit process to execute $projectRev")

            val processOutput = runProcess(cmd, *args, timeout = null) {
                inheritIO()
            }
            val exitCode = ExitCode(processOutput.process.exitValue())

            if (exitCode.isSuccess) {
                LOGGER.info("$projectRev executed successfully")
            } else {
                LOGGER.error("$projectRev failed to execute")
            }

            exitCode.isSuccess
        } else {
            runCatching {
                IndividualTestCaseComparisonRunner(projectRev, testMethodFilter, configuration).run()
            }.onSuccess {
                LOGGER.info("$projectRev executed successfully using Relevant Test-Methods Comparator")
            }.onFailure {
                LOGGER.error("$projectRev failed to execute using Relevant Test-Methods Comparator", it)
            }.handleError().isSuccess
        }
    }

    override fun run() {
        val configuration = AbstractComparisonRunner.Configuration(
            outputDir = outputDir,
            patchJavaVersion = true,
            outputDetails = outputDetails,
            testLevel = test,
            iterationContinueCondition = continuePassIf,
            maxNumPasses = maxPasses,
            noOptimize = noOptimize,
            timeout = Duration.ofSeconds(timeout.toLong()),
            threads = threads,
            javacVersionOverride = parseJavacEffectiveVersions(),
            enableAssertions = enableAssertions,
            d4jPath = d4jDir,
            workspacePath = workspaceDir
        )

        if (projectId != null && versionId != null) {
            if (versions.isNotEmpty()) {
                LOGGER.error("Cannot specify list of project versions in single-execution mode")
                exitProcess(1)
            }

            if (buggy) {
                LOGGER.warn("Running in single-execution mode - `--buggy` ignored")
            }
            if (fixed) {
                LOGGER.warn("Running in single-execution mode - `--fixed` ignored")
            }

            runCompare(
                configuration,
                Defects4JWorkspace.ProjectRev(projectId!!, versionId!!),
                false
            )
        } else if (projectId != null || versionId != null) {
            LOGGER.error("projectId and versionId must be specified together")
            exitProcess(1)
        } else {
            val successes = AtomicInteger()
            val failures = AtomicInteger()

            val projectRevsToRun = if (versions.isEmpty()) {
                Defects4JWorkspace.ProjectID.values().associateWith { it.projectValidBugs.toSet() }
            } else {
                versions.groupBy { it.projectId }
                    .mapValues { (_, v) ->
                        v.map { it.versionRange }.fold(listOf<Int>()) { acc, it -> acc + it.toList() }
                    }
                    .mapValues { (k, v) -> (v intersect k.projectValidBugs).toSet() }
            }
            val forkProcess = projectRevsToRun.values.sumOf { it.sum() } > 1

            projectRevsToRun.forEach { (projectId, versionIds) ->
                versionIds.forEach { version ->
                    if (!fixed) {
                        val res = runCompare(
                            configuration,
                            Defects4JWorkspace.ProjectRev(projectId, version, Defects4JWorkspace.BugVersion.BUGGY),
                            forkProcess
                        )
                        if (res) successes.incrementAndGet() else failures.incrementAndGet()
                    }
                    if (!buggy) {
                        val res = runCompare(
                            configuration,
                            Defects4JWorkspace.ProjectRev(projectId, version, Defects4JWorkspace.BugVersion.FIXED),
                            forkProcess
                        )
                        if (res) successes.incrementAndGet() else failures.incrementAndGet()
                    }
                }
            }

            LOGGER.info("Completed ${successes.get() + failures.get()} jobs with ${failures.get()} failures")
        }

        if (aggregateStats != AggregateStatsLevel.NONE) {
            LOGGER.info("Aggregating statistics")

            when (aggregateStats) {
                AggregateStatsLevel.BRIEF -> {
                    StandardCsvFormats.AggregatedClassStats.aggregateStats(outputDir)
                    StandardCsvFormats.AggregatedMemberStats.aggregateStats(outputDir)
                }

                AggregateStatsLevel.ALL -> {
                    StandardCsvFormats.AggregatedClassAllStats.aggregateStats(outputDir)
                    StandardCsvFormats.AggregatedMemberAllStats.aggregateStats(outputDir)
                }

                else -> unreachable()
            }
        }
    }

    companion object {

        private const val HELP_TEXT = "Runs batch comparison."
        private val LOGGER = Logger<Defects4JCompare>()
    }
}

private class Defects4JCheckout : CliktCommand(help = HELP_TEXT, name = "checkout") {

    private val javacSourceLevel by option("-source", help = "Provide source compatibility with specified release.")
        .convert { JavaRelease.fromCmdlineOption(it) }
    private val javacReleaseLevel by option("--release", help = "Compile as if for a specific VM version; Equivalent to `-source <N> -target <N>`.")
        .convert { JavaRelease.fromCmdlineOption(it) }
    private val javacTargetLevel by option("-target", help = "Generate class files for specific VM version.")
        .convert { JavaRelease.fromCmdlineOption(it) }
    private val projectId by option("-p", help = "Defects4J project ID.")
        .required()
    private val versionId by option("-v", help = "Defects4J bug ID.")
        .required()
    private val outputDir by option("-o", help = "The directory to output to.")
        .path()

    private fun parseJavacEffectiveVersions(): Defects4JWorkspace.JavacVersionOverride =
        Defects4JWorkspace.JavacVersionOverride.fromCmdline(javacReleaseLevel, javacSourceLevel, javacTargetLevel)

    override fun run() {
        val projectRev = Defects4JWorkspace.ProjectRev(
            Defects4JWorkspace.ProjectID.fromString(projectId),
            Defects4JWorkspace.VersionID.fromString(versionId)
        )
        val outputDir = outputDir ?: Path("/workspace")
        Defects4JWorkspace(outputDir, Path("/defects4j"), null, parseJavacEffectiveVersions())
            .initAndPrepare(projectRev, forAnalysis = false)
    }

    companion object {

        private const val HELP_TEXT = "Alias for `d4j checkout`, but also patching source/target Java version."
    }
}

private class Defects4JCoverageJacoco : CliktCommand(help = HELP_TEXT, name = "coverage.jacoco") {

    private val d4jDir by option("-dD", help = "The directory to Defects4J.")
        .path(mustExist = true, canBeFile = false)
    private val javacSourceLevel by option("-source", help = "Provide source compatibility with specified release.")
        .convert { JavaRelease.fromCmdlineOption(it) }
    private val javacReleaseLevel by option("--release", help = "Compile as if for a specific VM version; Equivalent to `-source <N> -target <N>`.")
        .convert { JavaRelease.fromCmdlineOption(it) }
    private val javacTargetLevel by option("-target", help = "Generate class files for specific VM version.")
        .convert { JavaRelease.fromCmdlineOption(it) }
    private val projectId by option("-p", help = "Defects4J project ID.")
        .required()
    private val verify by option("--verify", help = "Loads the coverage XML and verifies that the tool is able to load the XML.")
        .flag()
    private val versionId by option("-v", help = "Defects4J bug ID.")
        .required()
    private val testCases by argument()
        .convert { TestCase.fromD4JQualifiedName(it) }
        .multiple(required = true)
    private val workspaceDir by option("-dW", help = "The directory to the repository workspace.")
        .path()

    private fun parseJavacEffectiveVersions(): Defects4JWorkspace.JavacVersionOverride =
        Defects4JWorkspace.JavacVersionOverride.fromCmdline(javacReleaseLevel, javacSourceLevel, javacTargetLevel)

    override fun run() {
        val projectRev = Defects4JWorkspace.ProjectRev(
            Defects4JWorkspace.ProjectID.fromString(projectId),
            Defects4JWorkspace.VersionID.fromString(versionId)
        )
        val d4jWorkspace = Defects4JWorkspace(
            workspaceDir ?: Path("/workspace"),
            d4jDir ?: Path("/defects4j"),
            null,
            parseJavacEffectiveVersions()
        )

        LOGGER.info("Checking out and compiling project")
        d4jWorkspace.initAndPrepare(projectRev, forAnalysis = false)
        d4jWorkspace.compile()

        d4jWorkspace.coverageJacoco(testCases)

        if (verify) {
            d4jWorkspace.workspace.resolve("jacoco.xml")
                .also { check(it.isRegularFile()) }
                .inputStream()
                .use { SAXReader().read(it) }
                .let { JacocoXML.fromDocument(it) }
        }
    }

    companion object {

        private val LOGGER = Logger<Defects4JCoverageJacoco>()

        private const val HELP_TEXT = "Run coverage using Jacoco."
    }
}

private class Defects4JInstrumentCobertura : CliktCommand(help = HELP_TEXT, name = "instrument.cobertura") {

    private val d4jDir by option("-dD", help = "The directory to Defects4J.")
        .path(mustExist = true, canBeFile = false)
    private val workspaceDir by option("-dW", help = "The directory to the repository workspace.")
        .path()

    override fun run() {
        val d4jWorkspace = Defects4JWorkspace(
            workspaceDir ?: Path("/workspace"),
            d4jDir ?: Path("/defects4j"),
            null,
            Defects4JWorkspace.JavacVersionOverride.UNCHANGED
        )

        LOGGER.info("Compiling project for Cobertura coverage")
        d4jWorkspace.compile()

        d4jWorkspace.patchD4JCoverage()

        d4jWorkspace.dumpCoberturaInstrumented()
    }

    companion object {

        private val LOGGER = Logger<Defects4JInstrumentCobertura>()

        private const val HELP_TEXT = "Offline instruments classes using Cobertura."
    }
}

private class Defects4JInstrumentJacoco : CliktCommand(help = HELP_TEXT, name = "instrument.jacoco") {

    private val d4jDir by option("-dD", help = "The directory to Defects4J.")
        .path(mustExist = true, canBeFile = false)
    private val workspaceDir by option("-dW", help = "The directory to the repository workspace.")
        .path()
    private val output by option("-o", "--output", "--dest", help = "Path to write instrumented Java classes to")
        .path()
        .required()

    override fun run() {
        val d4jWorkspace = Defects4JWorkspace(
            workspaceDir ?: Path("/workspace"),
            d4jDir ?: Path("/defects4j"),
            null,
            Defects4JWorkspace.JavacVersionOverride.UNCHANGED
        )

        LOGGER.info("Compiling project for Jacoco coverage")
        d4jWorkspace.compile()

        d4jWorkspace.dumpJacocoInstrumented(output)
    }

    companion object {

        private val LOGGER = Logger<Defects4JInstrumentJacoco>()

        private const val HELP_TEXT = "Offline instruments classes using Jacoco."
    }
}

private class Defects4JRunCoverageJacoco : CliktCommand(help = HELP_TEXT, name = "run.coverage.jacoco") {

    private val d4jDir by option("-dD", help = "The directory to Defects4J.")
        .path(mustExist = true, canBeFile = false)
    private val verify by option("--verify", help = "Loads the coverage XML and verifies that the tool is able to load the XML.")
        .flag()
    private val testCases by argument()
        .convert { TestCase.fromD4JQualifiedName(it) }
        .multiple(required = true)
    private val workspaceDir by option("-dW", help = "The directory to the repository workspace.")
        .path()

    override fun run() {
        val d4jWorkspace = Defects4JWorkspace(
            workspaceDir ?: Path("/workspace"),
            d4jDir ?: Path("/defects4j"),
            null,
            Defects4JWorkspace.JavacVersionOverride.UNCHANGED
        )

        LOGGER.info("Compiling project for Jacoco coverage")
        d4jWorkspace.compile()

        d4jWorkspace.coverageJacoco(testCases)

        if (verify) {
            d4jWorkspace.workspace.resolve("jacoco.xml")
                .also { check(it.isRegularFile()) }
                .inputStream()
                .use { SAXReader().read(it) }
                .let { JacocoXML.fromDocument(it) }
        }
    }

    companion object {

        private val LOGGER = Logger<Defects4JRunCoverageJacoco>()

        private const val HELP_TEXT = "Run coverage using Jacoco."
    }
}

private class Defects4JExport : CliktCommand(help = HELP_TEXT, name = "export") {

    enum class RemapProfile(val d4jPath: Path, val lazyWorkspacePath: (Defects4JWorkspace.ProjectRev) -> Path) {
        HOST(Path("/home/david/tmp/defects4j-workspace"), { Path("/home/david/tmp/defects4j").resolve(it.toString()) }),
        CONTAINER(AbstractComparisonRunner.DEFECTS4J_PATH, AbstractComparisonRunner.WORKSPACE_PATH),
        UNIT_TEST(Path("/d4j"), { Path("/").resolve("${it.projectId.toString().lowercase()}/${it.versionId}") });

        constructor(d4jPath: Path, workspacePath: Path) : this(d4jPath, { workspacePath })
    }

    private val javacSourceLevel by option("-source", help = "Provide source compatibility with specified release.")
        .convert { JavaRelease.fromCmdlineOption(it) }
    private val javacReleaseLevel by option("--release", help = "Compile as if for a specific VM version; Equivalent to `-source <N> -target <N>`.")
        .convert { JavaRelease.fromCmdlineOption(it) }
    private val javacTargetLevel by option("-target", help = "Generate class files for specific VM version.")
        .convert { JavaRelease.fromCmdlineOption(it) }
    private val projectId by option("-p", help = "Defects4J project ID.")
        .required()
    private val versionId by option("-v", help = "Defects4J bug ID.")
        .required()
    private val remapPaths by option("-r", help = "Remaps exported paths based on a profile.")
        .enum<RemapProfile> { it.asCmdlineOpt() }
    private val remapD4j by option("-rD", help = "Remaps the Defects4J path in the output to an alternate path.")
        .flag()
    private val remapD4jPath by option(
        "-pD",
        help = "The path to remap Defects4J to. Must be used in conjunction with `-rD`."
    )
        .path()
    private val remapWorkspace by option(
        "-rW",
        help = "Remaps the repository workspace in the output to an alternate path."
    )
        .flag()
    private val remapWorkspacePath by option(
        "-pW",
        help = "The path to remap the workspace to. Must be used in conjunction with `-rW`."
    )
        .path()
    private val outputDir by option("-o", help = "The directory to output to.")
        .path()

    private fun parseJavacEffectiveVersions(): Defects4JWorkspace.JavacVersionOverride =
        Defects4JWorkspace.JavacVersionOverride.fromCmdline(javacReleaseLevel, javacSourceLevel, javacTargetLevel)

    override fun run() {
        val projectRev = Defects4JWorkspace.ProjectRev(
            Defects4JWorkspace.ProjectID.fromString(projectId),
            Defects4JWorkspace.VersionID.fromString(versionId)
        )
        val outputDir = outputDir ?: Path("/workspace")
        val configuration = AbstractComparisonRunner.Configuration(
            outputDir = outputDir,
            patchJavaVersion = false,
            outputDetails = false,
            testLevel = AbstractComparisonRunner.TestLevel.NONE,
            iterationContinueCondition = AbstractComparisonRunner.IterationContinueCondition.ALWAYS,
            maxNumPasses = 0u,
            noOptimize = emptySet(),
            timeout = Duration.ZERO,
            threads = null,
            javacVersionOverride = parseJavacEffectiveVersions()
        )
        val d4jWorkspace = IndividualTestCaseComparisonRunner(projectRev, TestMethodFilter.All, configuration)

        val d4jPath = when {
            remapD4j -> remapD4jPath
            remapPaths != null -> remapPaths!!.d4jPath
            else -> null
        }
        val workspacePath = when {
            remapWorkspace -> remapWorkspacePath
            remapPaths != null -> remapPaths!!.lazyWorkspacePath(projectRev)
            else -> null
        }

        d4jWorkspace.exportProperties(d4jPath, workspacePath, this.outputDir == null)

        val toolOutDir = ToolOutputDirectory(outputDir)
        val compileClasspath = toolOutDir.getCompileClasspathPath()
            .bufferedReader()
            .use { it.readText() }
            .trim('\n')
        val testClasspath = toolOutDir.getTestClasspathPath()
            .bufferedReader()
            .use { it.readText() }
            .trim('\n')
        val srcClasspath = toolOutDir.getSrcClasspathPath()
            .bufferedReader()
            .use { it.readText() }
            .trim('\n')
        val sourceRoots = toolOutDir.getSourceRootsPath()
            .bufferedReader()
            .use { it.readLines() }
            .filter { it.isNotBlank() }
        val entrypoints = toolOutDir.getTriggeringTestsPath()
            .bufferedReader()
            .use { it.readLines() }
            .filter { it.isNotBlank() }

        if (remapPaths == RemapProfile.UNIT_TEST) {
            val basePath = Path("/")
                .resolve(projectRev.projectId.toString().lowercase())
                .resolve(projectRev.versionId.toString())
            val srcTypeSolverPaths = sourceRoots
                .map { Path(it) }
                .map { if (it.startsWith(basePath)) basePath.relativize(it) else it }
            val srcCpTypeSolverPaths = srcClasspath.split(':')
                .filter { it.isNotBlank() }
                .map { Path(it) }
                .map { if (it.startsWith(basePath)) basePath.relativize(it) else it }
            val compileCpTypeSolverPaths = compileClasspath.split(':')
                .filter { it.isNotBlank() }
                .map { Path(it) }
                .map { if (it.startsWith(basePath)) basePath.relativize(it) else it }
            val testCpTypeSolverPaths = testClasspath.split(':')
                .filter { it.isNotBlank() }
                .map { Path(it) }
                .map { if (it.startsWith(basePath)) basePath.relativize(it) else it }

            val objInstance = buildString {
                appendLine("object ${projectRev.projectId}${projectRev.versionId} : Project() {")
                appendLine()

                appendLine("${' ' * 4}override val projectRev = ProjectRev(ProjectID.${projectRev.projectId.name}, ${projectRev.versionId.id}, BugVersion.${projectRev.versionId.version.name})")
                appendLine()

                appendLine("${' ' * 4}override val srcTypeSolverPaths = listOf(")
                srcTypeSolverPaths.forEach {
                    appendLine("${' ' * 8}Path(\"$it\"),")
                }
                appendLine("${' ' * 4})")
                if (srcCpTypeSolverPaths.isNotEmpty()) {
                    appendLine("${' ' * 4}override val srcCpTypeSolverPaths = listOf(")
                    srcCpTypeSolverPaths.forEach {
                        appendLine("${' ' * 8}Path(\"$it\"),")
                    }
                    appendLine("${' ' * 4})")
                }
                appendLine("${' ' * 4}override val compileCpTypeSolverPaths = listOf(")
                compileCpTypeSolverPaths.forEach {
                    appendLine("${' ' * 8}Path(\"$it\"),")
                }
                appendLine("${' ' * 4})")
                appendLine("${' ' * 4}override val testCpTypeSolverPaths = listOf(")
                testCpTypeSolverPaths.forEach {
                    appendLine("${' ' * 8}Path(\"$it\"),")
                }
                appendLine("${' ' * 4})")
                appendLine()

                appendLine("${' ' * 4}override val entrypoints = listOf(")
                entrypoints.forEach {
                    appendLine("${' ' * 8}\"$it\",")
                }
                appendLine("${' ' * 4})")
                append("}")
            }

            LOGGER.info("Unit Test TestProject instance:\n$objInstance")
        } else {
            val toolkitArgs = buildList {
                add("-cp")
                add(testClasspath)
                if (srcClasspath.isNotBlank()) {
                    add("-scp")
                    add(srcClasspath)
                }
                sourceRoots.forEach {
                    add("-sr")
                    add(it)
                }
                entrypoints.forEach {
                    add(it)
                }
            }

            LOGGER.info("Toolkit Arguments for `class-reduce`:")
            LOGGER.info(toolkitArgs.joinToString(" "))
        }
    }

    companion object {

        private const val HELP_TEXT = "Exports a project revision and related information."
        private val LOGGER = Logger<Defects4JExport>()
    }
}
