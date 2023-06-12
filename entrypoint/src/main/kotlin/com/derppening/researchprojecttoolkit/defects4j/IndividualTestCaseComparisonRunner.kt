package com.derppening.researchprojecttoolkit.defects4j

import com.derppening.researchprojecttoolkit.model.EntrypointSpec
import com.derppening.researchprojecttoolkit.model.TestMethodFilter
import com.derppening.researchprojecttoolkit.tool.reducer.AbstractReducer
import com.derppening.researchprojecttoolkit.tool.reducer.StaticAnalysisClassReducer
import com.derppening.researchprojecttoolkit.tool.reducer.StaticAnalysisMemberReducer
import com.derppening.researchprojecttoolkit.util.*
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.CallableDeclaration
import com.github.javaparser.ast.body.TypeDeclaration
import com.github.javaparser.resolution.declarations.ResolvedMethodLikeDeclaration
import kotlin.io.path.bufferedWriter
import kotlin.io.path.notExists
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

/**
 * A comparison runner which executes individual test cases.
 *
 * @param projectRev The project revision to execute.
 * @param testMethodFilter Filter for test methods to execute.
 */
class IndividualTestCaseComparisonRunner(
    projectRev: Defects4JWorkspace.ProjectRev,
    private val testMethodFilter: TestMethodFilter,
    configuration: Configuration
) : AbstractComparisonRunner(projectRev, configuration) {

    private data class Context(
        var allClassesSet: Set<TypeDeclaration<*>>? = null,
        var allMethodsSet: Map<ResolvedMethodLikeDeclaration, CallableDeclaration<*>>? = null,
        var allSrcClassesSet: Set<TypeDeclaration<*>>? = null,
        var allSrcMethodsSet: Map<ResolvedMethodLikeDeclaration, CallableDeclaration<*>>? = null,
        var allTestClassesSet: Set<TypeDeclaration<*>>? = null,
        var allTestMethodsSet: Map<ResolvedMethodLikeDeclaration, CallableDeclaration<*>>? = null,
        val retainedMethods: MutableSet<String> = mutableSetOf()
    )

    /**
     * Runs comparison for the given [sourceRoot] and writes the output to their respective files in
     * [ToolOutputDirectory].
     *
     * @param passNum The current pass number, or `null` if running using [StaticAnalysisClassReducer].
     */
    private fun compareSourceRoot(
        retainedCU: List<CompilationUnit>,
        toolOutputDir: ToolOutputDirectory,
        context: Context,
        passNum: UInt?,
        sourceRoot: SourceRootOutput
    ) {
        val allClassesSet = when (sourceRoot) {
            SourceRootOutput.SOURCE -> checkNotNull(context.allSrcClassesSet)
            SourceRootOutput.TEST -> checkNotNull(context.allTestClassesSet)
            SourceRootOutput.ALL -> checkNotNull(context.allClassesSet)
        }
        val allMethodsSet = when (sourceRoot) {
            SourceRootOutput.SOURCE -> checkNotNull(context.allSrcMethodsSet)
            SourceRootOutput.TEST -> checkNotNull(context.allTestMethodsSet)
            SourceRootOutput.ALL -> checkNotNull(context.allMethodsSet)
        }

        val retainedTypeDecls = getAndDumpClasses(retainedCU, toolOutputDir, sourceRoot) {
            if (passNum != null) {
                getActualClassesPath(passNum, sourceRoot, it)
            } else {
                getBaselineClassesPath(sourceRoot, it)
            }
        }
        val retainedTopLevelTypeDecls = retainedTypeDecls
            .filter { it.canBeReferencedByName }
            .toSortedSet(TYPE_DECL_COMPARATOR) as Set<TypeDeclaration<*>>

        val classPopulationSet = allClassesSet.map { it.fullyQualifiedName.get() }.toSet()
        // Predicted Set of Classes which will be used for compilation
        val classPredictedSet = retainedTopLevelTypeDecls
            .map { it.fullyQualifiedName.get() }
            .filter { it in classPopulationSet }
            .toSet()
        // Actual Set of Classes which is retained using the baseline reducer
        val classPositiveSet = getExpectedClassList(toolOutputDir, sourceRoot)
            .filter { it in classPopulationSet }
            .toSet()

        val classStats = calcStatistics(classPopulationSet, classPredictedSet, classPositiveSet)

        if (passNum != null) {
            toolOutputDir.getClassDiffPath(passNum, sourceRoot)
        } else {
            toolOutputDir.getBaselineClassDiffPath(sourceRoot)
        }.let { dumpDiff(classStats, classPredictedSet, classPositiveSet, it) }

        if (passNum != null) {
            toolOutputDir.getClassStatPath(passNum, sourceRoot)
        } else {
            toolOutputDir.getBaselineClassStatPath(sourceRoot)
        }.let { classStats.dumpToFile(it) }

        val retainedMethods = getAndDumpMethods(retainedCU, toolOutputDir, sourceRoot) {
            if (passNum != null) {
                getActualMethodsPath(passNum, sourceRoot, it)
            } else {
                getBaselineMethodsPath(sourceRoot, it)
            }
        }

        val methodPopulationSet = allMethodsSet.keys.map { it.bytecodeQualifiedSignature }.toSet()
        // Predicted Set of Methods which will be used for compilation
        val methodPredictedSet = retainedMethods.keys.map { it.bytecodeQualifiedSignature }
            .onEach { check(it in methodPopulationSet) { "Callable with signature $it not in population set $sourceRoot" } }
            .toSet()
        // Actual Set of Methods which is retained using the baseline reducer
        val methodPositiveSet = getMethodPositiveSet(toolOutputDir, sourceRoot)
            .onEach { check(it in methodPopulationSet) { "Callable with signature $it not in population set $sourceRoot" } }

        if (passNum != null && sourceRoot == SourceRootOutput.ALL) {
            context.retainedMethods.clear()
            context.retainedMethods.addAll(methodPredictedSet)
        }

        val methodStats = calcStatistics(methodPopulationSet, methodPredictedSet, methodPositiveSet)

        if (passNum != null) {
            toolOutputDir.getMethodDiffPath(passNum, sourceRoot)
        } else {
            toolOutputDir.getBaselineMethodDiffPath(sourceRoot)
        }.let { dumpDiff(methodStats, methodPredictedSet, methodPositiveSet, it) }

        if (passNum != null) {
            toolOutputDir.getMethodStatPath(passNum, sourceRoot)
        } else {
            toolOutputDir.getBaselineMethodStatPath(sourceRoot)
        }.let { methodStats.dumpToFile(it) }
    }

    @OptIn(ExperimentalTime::class)
    private fun runBaseline(testCase: TestCase, context: Context): RunResult {
        LOGGER.info("Running $projectRev $testCase Baseline")

        val toolOutputDir = ToolOutputDirectory(configuration.outputDir, projectRev, testCase)

        prepareWorkspaceForReduction()

        val entrypoints = listOf(testCase)
            .map { EntrypointSpec.fromArg(it, sourceRoots) }

        val reducer = StaticAnalysisClassReducer(
            testClasspath,
            srcClasspath,
            sourceRoots,
            entrypoints,
            checkNotNull(configuration.enableAssertions) { "enableAssertions cannot be null" },
            configuration.threads,
            AbstractReducer.Optimization.ALL - configuration.noOptimize
        )

        context.allClassesSet = reducer.getAndDumpAllTopLevelClasses(toolOutputDir, SourceRootOutput.ALL)
        context.allMethodsSet = getAndDumpAllMethods(context.allClassesSet!!, toolOutputDir, SourceRootOutput.ALL)
        context.allSrcClassesSet = reducer.getAndDumpAllTopLevelClasses(toolOutputDir, SourceRootOutput.SOURCE)
        context.allSrcMethodsSet = getAndDumpAllMethods(context.allSrcClassesSet!!, toolOutputDir, SourceRootOutput.SOURCE)
        context.allTestClassesSet = reducer.getAndDumpAllTopLevelClasses(toolOutputDir, SourceRootOutput.TEST)
        context.allTestMethodsSet = getAndDumpAllMethods(context.allTestClassesSet!!, toolOutputDir, SourceRootOutput.TEST)

        val markPhaseDuration = measureTime {
            if (configuration.threads != null) {
                reducer.run(checkNotNull(configuration.threads))
            } else {
                reducer.run()
            }
        }
        LOGGER.debug("Mark Phase of {} {} Baseline took {} s", projectRev, testCase, markPhaseDuration.toDouble(DurationUnit.SECONDS).toStringRounded(3))

        val (retainedCU, sweepPhaseDuration) = measureTimedValue {
            reducer.getTransformedCompilationUnits(TMP_PATH, d4jWorkspace.workspace)
        }
        LOGGER.debug("Sweep Phase of {} {} Baseline took {} s", projectRev, testCase, sweepPhaseDuration.toDouble(DurationUnit.SECONDS).toStringRounded(3))

        val totalDuration = markPhaseDuration + sweepPhaseDuration
        LOGGER.info("Reduction of {} {} Baseline took {} s", projectRev, testCase, totalDuration.toDouble(DurationUnit.SECONDS).toStringRounded(3))

        toolOutputDir.createBaselineDir()

        toolOutputDir.getBaselineRunningTimePath().bufferedWriter().use { writer ->
            writer.write(totalDuration.toInt(DurationUnit.MILLISECONDS).toString())
        }

        SourceRootOutput.values().forEach {
            compareSourceRoot(retainedCU, toolOutputDir, context, null, it)
        }

        val sourcesOutputTar = toolOutputDir.getBaselineSourcesPath()
        dumpReducedSources(retainedCU, sourcesOutputTar)

        val execResult = tryCompileAndTest(sourcesOutputTar, testCase, null)

        clearMemory()

        return execResult
    }

    /**
     * Runs a single test case with the given [testCase].
     */
    @OptIn(ExperimentalTime::class)
    private fun runPass(testCase: TestCase, passNum: UInt, context: Context): RunResult {
        LOGGER.info("Running $projectRev $testCase Pass $passNum")

        val toolOutputDir = ToolOutputDirectory(configuration.outputDir, projectRev, testCase)

        prepareWorkspaceForReduction()

        // Blend sources from a previous pass if we are repeating
        if (passNum > 0u) {
            val lastPassSources = checkNotNull(toolOutputDir.getLastPassOf(ToolOutputDirectory::getSourcesPath)) {
                "Cannot find sources from previous pass"
            }
            check(lastPassSources.fileName == toolOutputDir.getSourcesPath(passNum - 1u).fileName)

            blendWorkspace(d4jWorkspace, lastPassSources)
        }

        val entrypoints = listOf(testCase)
            .map { EntrypointSpec.fromArg(it, sourceRoots) }

        val reducer = StaticAnalysisMemberReducer(
            testClasspath,
            srcClasspath,
            sourceRoots,
            entrypoints,
            checkNotNull(configuration.enableAssertions) { "enableAssertions cannot be null" },
            configuration.threads,
            AbstractReducer.Optimization.ALL - configuration.noOptimize
        )

        val markPhaseDuration = measureTime {
            if (configuration.threads != null) {
                reducer.run(checkNotNull(configuration.threads))
            } else {
                reducer.run()
            }
        }
        LOGGER.debug("Mark Phase of {} {} Pass {} took {} s", projectRev, testCase, passNum, markPhaseDuration.toDouble(DurationUnit.SECONDS).toStringRounded(3))

        val (retainedCU, sweepPhaseDuration) = measureTimedValue {
            reducer.getTransformedCompilationUnits(TMP_PATH, d4jWorkspace.workspace)
        }
        LOGGER.debug("Sweep Phase of {} {} Pass {} took {} s", projectRev, testCase, passNum, sweepPhaseDuration.toDouble(DurationUnit.SECONDS).toStringRounded(3))

        val totalDuration = markPhaseDuration + sweepPhaseDuration
        LOGGER.info("Reduction of {} {} Pass {} took {} s", projectRev, testCase, passNum, totalDuration.toDouble(DurationUnit.SECONDS).toStringRounded(3))

        toolOutputDir.createReductionPassDir(passNum)

        toolOutputDir.getRunningTimePath(passNum).bufferedWriter().use { writer ->
            writer.write(totalDuration.toInt(DurationUnit.MILLISECONDS).toString())
        }

        SourceRootOutput.values().forEach {
            compareSourceRoot(retainedCU, toolOutputDir, context, passNum, it)
        }

        val sourcesOutputTar = toolOutputDir.getSourcesPath(passNum)
        dumpReducedSources(retainedCU, sourcesOutputTar)

        val execResult = tryCompileAndTest(sourcesOutputTar, testCase, passNum)

        clearMemory()

        return execResult
    }

    private fun runTestCase(testCase: TestCase) {
        if (!runGroundTruth(testCase) && testMethodFilter is TestMethodFilter.Golden) {
            LOGGER.info("Ground truth for $testCase is not compilable - Skipping")
            return
        }

        val maxNumPasses = configuration.maxNumPasses
        if (maxNumPasses == 0u) {
            LOGGER.info("Max Passes set to 0 - SKipping reduction passes")
            return
        }

        val context = Context()

        runBaseline(testCase, context)

        for (passNum in 0u until maxNumPasses) {
            val prevMethodPredictedSet = context.retainedMethods.toSet()

            val execResult = runPass(testCase, passNum, context)

            if (!configuration.iterationContinueCondition.isSatisfiedBy(execResult)) {
                LOGGER.warn("Execution $execResult failed to satisfy ${configuration.iterationContinueCondition} - Skipping rest of iterations")
                break
            }

            // Bail early if there are no more methods reduced in a pass
            if (context.retainedMethods == prevMethodPredictedSet) {
                LOGGER.info("No more methods reduced - Skipping rest of iterations")

                // Delete the output from this pass as it is redundant
                ToolOutputDirectory(configuration.outputDir, projectRev, testCase).deleteLastPassDir()

                break
            }
        }
    }

    override fun run() {
        d4jWorkspace.initAndCheckout(projectRev)

        val outputDir = ToolOutputDirectory(configuration.outputDir, projectRev)

        // Pre-cache the value of these variables before we patch anything
        testClasspath
        sourceRoots

        outputDir.getExpectedFailingTestsPath()
            .let { path ->
                if (path.notExists()) {
                    path.bufferedWriter().use { writer ->
                        triggeringTests.forEach { writer.appendLine(it.toString()) }
                    }
                }
            }

        patchProjectSpecificFlags()
        patchProjectWideFlags()

        val testCases = testMethodFilter.getFilteredTestCases(
            d4jWorkspace,
            projectTestRoot,
            sourceRoots,
            testClasspath,
            triggeringTests
        ).sortedBy { it.toString() }

        LOGGER.info("Collected ${testCases.size} test cases using ${testMethodFilter.cmdlineOpt.first()} filter")

        if (testCases.isEmpty()) {
            return
        }

        // Pre-create test case directories
        // Required for test stat aggregator to emit compile0 results
        testCases.forEach {
            ToolOutputDirectory(configuration.outputDir, projectRev, it)
        }

        val canDefaultBeCompiled = exportDefaultCompilationResult()
        if (canDefaultBeCompiled && testMethodFilter is TestMethodFilter.Golden) {
            LOGGER.info("Default revision is compilable - Skipping")
            return
        }

        testCases.forEach { tcName ->
            runCatching {
                runTestCase(tcName)
            }.onFailure {
                LOGGER.error("Failed to execute $projectRev $tcName", it)
            }

            clearMemory()
        }
    }

    companion object {

        private val LOGGER = Logger<IndividualTestCaseComparisonRunner>()
    }
}