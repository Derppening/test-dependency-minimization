package com.derppening.researchprojecttoolkit.defects4j

import com.derppening.researchprojecttoolkit.GlobalConfiguration
import com.derppening.researchprojecttoolkit.model.EntrypointSpec
import com.derppening.researchprojecttoolkit.tool.facade.FuzzySymbolSolver
import com.derppening.researchprojecttoolkit.tool.facade.typesolver.PartitionedTypeSolver
import com.derppening.researchprojecttoolkit.tool.inclusionReasonsData
import com.derppening.researchprojecttoolkit.tool.isUnusedForRemovalData
import com.derppening.researchprojecttoolkit.tool.reducer.AbstractReducer
import com.derppening.researchprojecttoolkit.tool.reducer.CoverageBasedReducer
import com.derppening.researchprojecttoolkit.util.*
import com.derppening.researchprojecttoolkit.util.Defects4JWorkspace.Property.DIR_SRC_CLASSES
import com.derppening.researchprojecttoolkit.util.Defects4JWorkspace.Property.DIR_SRC_TESTS
import com.derppening.researchprojecttoolkit.util.posix.ProcessOutput
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.Node
import com.github.javaparser.ast.body.CallableDeclaration
import com.github.javaparser.ast.body.TypeDeclaration
import com.github.javaparser.resolution.declarations.ResolvedMethodLikeDeclaration
import hk.ust.cse.castle.toolkit.jvm.jsl.PredicatedFileCollector
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.time.Duration
import java.util.*
import java.util.concurrent.ConcurrentSkipListMap
import java.util.function.Function
import java.util.stream.Collectors
import kotlin.io.path.*
import kotlin.jvm.optionals.getOrDefault
import java.util.function.Function as JFunction

abstract class AbstractComparisonRunner(
    protected val projectRev: Defects4JWorkspace.ProjectRev,
    protected val configuration: Configuration
) {

    /**
     * Configuration of the runner.
     *
     * @property outputDir The directory to output results to.
     * @property testLevel Level of testing to perform on the minimized sources.
     * @property enableAssertions Enable assertions when outputting reduced sources.
     */
    class Configuration(
        val outputDir: Path,
        val patchJavaVersion: Boolean,
        val outputDetails: Boolean,
        val testLevel: TestLevel,
        val iterationContinueCondition: IterationContinueCondition,
        val maxNumPasses: UInt,
        val noOptimize: Set<AbstractReducer.Optimization>,
        val timeout: Duration,
        val threads: Int?,
        val javacVersionOverride: Defects4JWorkspace.JavacVersionOverride,
        val enableAssertions: Boolean? = null,
        val d4jPath: Path? = null,
        val workspacePath: Path? = null,
    )

    enum class TestLevel {
        /**
         * Do not perform any testing on the minimized sources.
         */
        NONE,

        /**
         * Compile the minimized sources.
         */
        COMPILE,

        /**
         * Compile and run test cases on the minimized sources.
         */
        TEST
    }

    enum class IterationContinueCondition {
        /**
         * Continues iterative reduction if the test case successfully executes and passes all assertions.
         */
        TEST_SUCCESS,

        /**
         * Continues iterative reduction if the test case successfully executes and matches the expected result.
         */
        SUCCESS,

        /**
         * Continues iterative reduction if the test case successfully compiles.
         */
        COMPILE_SUCCESS,

        /**
         * Always continue iterative reduction.
         */
        ALWAYS;

        fun isSatisfiedBy(result: RunResult): Boolean = when (this) {
            TEST_SUCCESS -> result == RunResult.SUCCESS
            SUCCESS -> result == RunResult.SUCCESS || result == RunResult.TEST_EXPECTED_FAIL
            COMPILE_SUCCESS -> result != RunResult.COMPILE_FAILED
            ALWAYS -> true
        }
    }

    enum class RunResult {
        /**
         * The test case successfully compiled and passed all assertions.
         */
        SUCCESS,

        /**
         * The test case failed to compile.
         */
        COMPILE_FAILED,

        /**
         * The test case failed some assertions.
         */
        TEST_FAILED,

        /**
         * The test case failed some expected assertions.
         */
        TEST_EXPECTED_FAIL
    }

    /**
     * [Defects4JWorkspace] of the bug.
     */
    protected val d4jWorkspace = Defects4JWorkspace(
        configuration.workspacePath ?: WORKSPACE_PATH,
        configuration.d4jPath ?: DEFECTS4J_PATH,
        null,
        configuration.javacVersionOverride
    )

    /**
     * Compile classpath of the bug.
     */
    protected val compileClasspath by lazy {
        LOGGER.info("Obtaining project compile classpath")

        d4jWorkspace.getCompileClasspath(projectRev)
    }

    /**
     * Test classpath of the bug.
     */
    protected val testClasspath by lazy {
        LOGGER.info("Obtaining project test classpath")

        d4jWorkspace.getTestClasspath(projectRev)
    }

    /**
     * Source-based classpath of the bug.
     */
    protected val srcClasspath by lazy {
        LOGGER.info("Obtaining project source-based classpath")

        getAdditionalSourceClasspaths()
    }

    /**
     * Cached value for [Defects4JWorkspace.Property.DIR_SRC_CLASSES].
     */
    private val dirSrcClasses by lazy {
        LOGGER.info("Obtaining classes source root")

        runCatching {
            ToolOutputDirectory(GlobalConfiguration.INSTANCE.cmdlineOpts.baselineDir, projectRev, readOnly = true)
                .getClassesSourceRootPath()
                .bufferedReader()
                .use { it.readText() }
                .trim()
        }.recoverCatching {
            d4jWorkspace.export(DIR_SRC_CLASSES)
        }.getOrThrow()
    }

    /**
     * Project source root of the bug.
     */
    protected val projectSrcRoot by lazy {
        LOGGER.info("Obtaining project classes source root")

        d4jWorkspace.workspace.resolve(dirSrcClasses) as Path
    }

    /**
     * Cached value for [Defects4JWorkspace.Property.DIR_SRC_TESTS].
     */
    private val dirSrcTests by lazy {
        LOGGER.info("Obtaining test classes source root")

        runCatching {
            ToolOutputDirectory(GlobalConfiguration.INSTANCE.cmdlineOpts.baselineDir, projectRev, readOnly = true)
                .getTestSourceRootPath()
                .bufferedReader()
                .use { it.readText() }
                .trim()
        }.recoverCatching {
            d4jWorkspace.export(DIR_SRC_TESTS)
        }.getOrThrow()

    }

    /**
     * Project test source root of the bug.
     */
    protected val projectTestRoot by lazy {
        LOGGER.info("Obtaining project test classes source root")

        d4jWorkspace.workspace.resolve(dirSrcTests) as Path
    }

    private fun nodeInSrcRoot(node: Node, sourceRoot: Path): Boolean {
        return node.findCompilationUnit()
            .flatMap { it.storage }
            .map { it.path.startsWith(sourceRoot) }
            .getOrDefault(false)
    }

    /**
     * Checks whether [node] is in a compilation unit which exists under [TMP_PATH].
     *
     * @param sourceRoot Selects which source root to check. If [SourceRootOutput.ALL], checks whether the node is
     * inside [TMP_PATH].
     */
    protected fun nodeInTmpSrcRoot(node: Node, sourceRoot: SourceRootOutput): Boolean {
        return when (sourceRoot) {
            SourceRootOutput.SOURCE -> nodeInSrcRoot(node, TMP_PATH.resolve(dirSrcClasses))
            SourceRootOutput.TEST -> nodeInSrcRoot(node, TMP_PATH.resolve(dirSrcTests))
            SourceRootOutput.ALL -> true
        }
    }

    /**
     * Source roots of the bug.
     */
    protected val sourceRoots by lazy {
        LOGGER.info("Obtaining project source roots")

        listOf(projectSrcRoot, projectTestRoot) + getAdditionalSourceRoots()
    }

    /**
     * Expected set of test cases which
     */
    protected val triggeringTests by lazy {
        LOGGER.info("Obtaining project triggering tests")

        d4jWorkspace.triggeringTests
    }

    /**
     * Entrypoints to the bug.
     */
    protected val entrypoints by lazy {
        LOGGER.info("Obtaining project entrypoints")

        triggeringTests.map { EntrypointSpec.fromArg(it, sourceRoots) }
    }

    protected fun getExpectedClassList(
        toolOutDir: ToolOutputDirectory,
        sourceRoot: SourceRootOutput
    ): Set<String> {
        return toolOutDir.getGroundTruthClassesPath(sourceRoot, false)
            .bufferedReader()
            .use { it.readLines() }
            .toSet()
    }

    /**
     * [PartitionedTypeSolver] for solving symbols within this class.
     */
    protected val typeSolver by lazy {
        projectRev.getTypeSolver(d4jWorkspace)
    }

    /**
     * [FuzzySymbolSolver] for solving symbols within this class.
     */
    protected val symbolSolver by lazy {
        projectRev.getSymbolSolver(d4jWorkspace)
    }

    /**
     * @see Defects4JWorkspace.ProjectRev.getAdditionalSourceRoots
     */
    private fun getAdditionalSourceRoots(): List<Path> = projectRev.getAdditionalSourceRoots(d4jWorkspace)

    /**
     * @see Defects4JWorkspace.ProjectRev.getAdditionalSourceClasspaths
     */
    private fun getAdditionalSourceClasspaths(): String = projectRev.getAdditionalSourceClasspaths(d4jWorkspace)

    /**
     * Exports the result of compilation when no reduction has been performed.
     */
    protected fun exportDefaultCompilationResult(): Boolean {
        val defaultCompileOutputPath = ToolOutputDirectory(configuration.outputDir, projectRev)
            .getDefaultCompilePath()

        val compileExitCode = if (defaultCompileOutputPath.notExists()) {
            d4jWorkspace.initAndPrepare(projectRev, forAnalysis = false)

            LOGGER.info("Compiling full sources for baseline")

            val (compileExitCode, compilerLogs) = tryCompileCatching(d4jWorkspace)
                .map {
                    it.exitCode to it.stdout
                }.onFailure { tr ->
                    LOGGER.warn("Encountered exception while executing `defects4j compile`", tr)
                }.getOrElse { ExitCode(137) to emptyList() }

            val compilableStr = if (compileExitCode.isSuccess) "Successful" else "Unsuccessful"
            LOGGER.info("$projectRev Baseline: Compilation $compilableStr")

            defaultCompileOutputPath
                .bufferedWriter()
                .use { writer ->
                    writer.appendLine("Pre-Reduce Compile ExitCode: $compileExitCode")
                    writer.appendLine()
                    compilerLogs.forEach { writer.appendLine(it) }
                }

            compileExitCode
        } else {
            defaultCompileOutputPath.bufferedReader().use { reader ->
                reader.lines()
                    .findFirst()
                    .get()
                    .dropWhile { it != ':' }
                    .drop(1)
                    .trim()
                    .let { ExitCode(it.toInt()) }
            }
        }

        return compileExitCode.isSuccess
    }

    /**
     * Runs the ground truth.
     *
     * The golden baseline consists of reducing the project using the ground truth, then compiling using the boot JVM
     * version.
     */
    protected fun runGroundTruth(testCase: TestCase): Boolean {
        val toolOutDir = ToolOutputDirectory(configuration.outputDir, projectRev, testCase)

        val compileResult = if (toolOutDir.getGroundTruthCompilePath().notExists()) {
            LOGGER.info("Running ground truth for $projectRev $testCase")

            prepareWorkspaceForReduction()

            val reducer = CoverageBasedReducer(
                testClasspath,
                srcClasspath,
                sourceRoots,
                listOf(EntrypointSpec.fromArg(testCase, sourceRoots)),
                checkNotNull(configuration.enableAssertions) { "enableAssertions cannot be null" },
                configuration.threads,
                projectRev,
                testCase
            )

            if (configuration.threads != null) {
                reducer.run(checkNotNull(configuration.threads))
            } else {
                reducer.run()
            }

            val retainedCU = reducer.getTransformedCompilationUnits(TMP_PATH, d4jWorkspace.workspace)

            // Dump set of ground truth-retained classes and methods
            SourceRootOutput.values().forEach { sourceRoot ->
                getAndDumpClasses(retainedCU, toolOutDir, sourceRoot) {
                    getGroundTruthClassesPath(sourceRoot, it)
                }

                getAndDumpMethods(retainedCU, toolOutDir, sourceRoot) {
                    getGroundTruthMethodsPath(sourceRoot, it)
                }
            }

            val sourcesOutputDir = toolOutDir.getGroundTruthSourcesPath()
            dumpReducedSources(retainedCU, sourcesOutputDir)

            TemporaryPath.createDirectory().use { tempDir ->
                val tempWorkspace = createBlendedWorkspace(tempDir.path, sourcesOutputDir)

                LOGGER.info("Trying to compile ground truth sources")
                val (compileExitCode, compilerLogs) = tryCompileCatching(tempWorkspace)
                    .map {
                        it.exitCode to it.stdout
                    }.onFailure { tr ->
                        LOGGER.warn("Encountered exception while executing `defects4j compile`", tr)
                    }.getOrElse { ExitCode(137) to emptyList() }

                toolOutDir.getGroundTruthCompilePath()
                    .bufferedWriter()
                    .use { writer ->
                        writer.appendLine("Pre-Reduce Compile ExitCode: $compileExitCode")
                        writer.appendLine()
                        compilerLogs.forEach { writer.appendLine(it) }
                    }

                if (compileExitCode.isSuccess) {
                    LOGGER.info("Trying to run tests on ground truth sources")
                    val testExitCode = tryTestCatching(tempWorkspace, testCase)
                        .map { it.exitCode }
                        .onFailure { tr ->
                            LOGGER.warn("Encountered exception while executing `defects4j test`", tr)
                        }
                        .getOrElse { ExitCode(137) }

                    val failingTestsFile = tempWorkspace.workspace.resolve("failing_tests")

                    ToolOutputDirectory(configuration.outputDir, projectRev, testCase)
                        .getGroundTruthTestPath()
                        .bufferedWriter()
                        .use { writer ->
                            writer.appendLine("Test ExitCode: $testExitCode")
                        }

                    ToolOutputDirectory(configuration.outputDir, projectRev, testCase)
                        .getGroundTruthFailingTestsPath()
                        .bufferedWriter()
                        .use { writer ->
                            failingTestsFile
                                .bufferedReader()
                                .use { reader ->
                                    reader.copyTo(writer)
                                }
                        }
                }

                compileExitCode
            }
        } else {
            toolOutDir.getGroundTruthCompilePath().bufferedReader().use { reader ->
                reader.lines()
                    .findFirst()
                    .get()
                    .dropWhile { it != ':' }
                    .drop(1)
                    .trim()
                    .let { ExitCode(it.toInt()) }
            }
        }

        clearMemory()

        return compileResult.isSuccess
    }

    /**
     * @see Defects4JWorkspace.patchProjectSpecificFlags
     */
    protected fun patchProjectSpecificFlags(d4jWorkspace: Defects4JWorkspace = this.d4jWorkspace) =
        d4jWorkspace.patchProjectSpecificFlags(projectRev, configuration.patchJavaVersion)

    /**
     * @see Defects4JWorkspace.patchProjectWideFlags
     */
    protected fun patchProjectWideFlags(d4jWorkspace: Defects4JWorkspace = this.d4jWorkspace) =
        d4jWorkspace.patchProjectWideFlags(configuration.patchJavaVersion)

    /**
     * Resets the workspace to avoid existence of classes and sources during type solving.
     */
    protected fun prepareWorkspaceForReduction() {
        d4jWorkspace.initAndPrepare(projectRev)
    }

    /**
     * Retrieves the set of all top-level classes from this [AbstractReducer], dumps into
     * [ToolOutputDirectory.getAllClassesInSourceRootPath] in [projectOutputDir], and returns it.
     */
    protected fun AbstractReducer.getAndDumpAllTopLevelClasses(
        projectOutputDir: ToolOutputDirectory?,
        sourceRoot: SourceRootOutput
    ): Set<TypeDeclaration<*>> {
        val allClassesSet = getAndDumpAllTopLevelClasses {
            when (sourceRoot) {
                SourceRootOutput.SOURCE -> nodeInSrcRoot(it, projectSrcRoot)
                SourceRootOutput.TEST -> nodeInSrcRoot(it, projectTestRoot)
                SourceRootOutput.ALL -> true
            }
        }

        val allClassesFile = (projectOutputDir ?: ToolOutputDirectory(configuration.outputDir, projectRev))
            .getAllClassesInSourceRootPath(sourceRoot)
        if (allClassesFile.notExists()) {
            allClassesFile.bufferedWriter().use { writer ->
                allClassesSet.forEach {
                    writer.appendLine(it.fullyQualifiedName.get())
                }
            }
        }

        return allClassesSet
    }

    /**
     * Extracts all name-referencable methods from [typeDecls].
     */
    private fun mapMethodsFromTypes(
        typeDecls: Set<TypeDeclaration<*>>
    ): Map<ResolvedMethodLikeDeclaration, CallableDeclaration<*>> {
        return typeDecls
            .parallelStream()
            .flatMap { typeDecl ->
                typeDecl
                    .findAll<CallableDeclaration<*>> { it.canBeReferencedByName }
                    .stream()
            }
            .collect(
                Collectors.toConcurrentMap(
                    { symbolSolver.resolveDeclaration<ResolvedMethodLikeDeclaration>(it) },
                    JFunction.identity(),
                    ThrowingMergerForCallable,
                ) { ConcurrentSkipListMap(ResolvedCallableDeclComparator(symbolSolver)) }
            )
            .let { it as Map<ResolvedMethodLikeDeclaration, CallableDeclaration<*>> }
    }

    /**
     * Dumps all signatures in [classesSet] into [projectOutputDir] and returns it.
     */
    private fun getAndDumpAllMethods(
        classesSet: Set<TypeDeclaration<*>>,
        projectOutputDir: ToolOutputDirectory?,
        outFileMapper: ToolOutputDirectory.() -> Path
    ): Map<ResolvedMethodLikeDeclaration, CallableDeclaration<*>> {
        val allMethods = mapMethodsFromTypes(classesSet)

        val allMethodsFile = (projectOutputDir ?: ToolOutputDirectory(configuration.outputDir, projectRev))
            .let { it.outFileMapper() }
        if (allMethodsFile.notExists()) {
            val allMethodsWithImplicit = allMethods.keys.map { it.bytecodeQualifiedSignature }.toSet()

            allMethodsFile.bufferedWriter().use { writer ->
                allMethodsWithImplicit.forEach { writer.appendLine(it) }
            }
        }

        return allMethods
    }

    /**
     * Dumps all signatures in [classesSet] into [projectOutputDir] and returns it.
     */
    protected fun getAndDumpAllMethods(
        classesSet: Set<TypeDeclaration<*>>,
        projectOutputDir: ToolOutputDirectory?,
        sourceRoot: SourceRootOutput
    ): Map<ResolvedMethodLikeDeclaration, CallableDeclaration<*>> {
        return getAndDumpAllMethods(classesSet, projectOutputDir) {
            getAllMethodsInSourceRootPath(sourceRoot)
        }
    }

    /**
     * Dumps the names of [retained classes][retainedTypeDecls] (and its inclusion reason if specified by
     * [outputDetails]) to [outFile].
     */
    private fun dumpActualClassSet(
        retainedTypeDecls: Set<TypeDeclaration<*>>,
        outFile: Path,
        outputDetails: Boolean = configuration.outputDetails
    ) {
        outFile.bufferedWriter().use { writer ->
            retainedTypeDecls
                .map { it.fullyQualifiedName.get() to it }
                .sortedBy { it.first }
                .forEach { (qname, typeDecl) ->
                    writer.appendLine(qname)

                    if (outputDetails) {
                        typeDecl.inclusionReasonsData.synchronizedWith {
                            map { it.toReasonString() }
                                .distinct()
                                .sorted()
                                .forEach {
                                    writer.appendLine("    $it")
                                }
                        }
                    }
                }
        }
    }

    /**
     * Dumps the names of [retained classes][retainedTypeDecls] to an output file in [toolOutDir] as specified by
     * [toolOutPath].
     *
     * @param outputDebug Whether to output the inclusion reasons of the retained classes.
     */
    private fun dumpActualClassSet(
        retainedTypeDecls: Set<TypeDeclaration<*>>,
        toolOutDir: ToolOutputDirectory,
        outputDebug: Boolean,
        toolOutPath: ToolOutputDirectory.(Boolean) -> Path
    ) = dumpActualClassSet(retainedTypeDecls, toolOutDir.toolOutPath(outputDebug), outputDetails = outputDebug)

    protected fun getAndDumpClasses(
        retainedCU: List<CompilationUnit>,
        toolOutputDir: ToolOutputDirectory,
        sourceRoot: SourceRootOutput,
        toolOutPath: ToolOutputDirectory.(Boolean) -> Path
    ): SortedSet<TypeDeclaration<*>> {
        val retainedTypeDecls = retainedCU.parallelStream()
            .filter { nodeInTmpSrcRoot(it, sourceRoot) }
            .flatMap { it.findAll<TypeDeclaration<*>>().stream() }
            .filter { it.fullyQualifiedName.isPresent }
            .collect(Collectors.toCollection { TreeSet(TYPE_DECL_COMPARATOR) })

        dumpActualClassSet(retainedTypeDecls, toolOutputDir, false, toolOutPath)
        if (configuration.outputDetails && sourceRoot == SourceRootOutput.ALL) {
            dumpActualClassSet(retainedTypeDecls, toolOutputDir, true, toolOutPath)
        }

        return retainedTypeDecls
    }

    /**
     * Dumps the differences between the [predicted set of classes][ppSet] and the [actual set][pSet] of classes to
     * [outFile].
     */
    protected fun dumpDiff(stats: NumericStatistic, ppSet: Set<String>, pSet: Set<String>, outFile: Path) {
        outFile.bufferedWriter().use { writer ->
            writer.appendLine("Total Count: ${stats.population}")
            writer.appendLine("Predicted Count: ${stats.pp}")
            writer.appendLine("Actual Count: ${stats.p}")
            writer.appendLine()

            writer.appendLine("False-Positives: ${stats.fp}")
            (ppSet - pSet)
                .sorted()
                .forEach { writer.appendLine(it) }
            writer.appendLine()

            writer.appendLine("False-Negatives: ${stats.fn}")
            (pSet - ppSet)
                .sorted()
                .forEach { writer.appendLine(it) }
            writer.appendLine()
        }
    }

    /**
     * Dumps the signatures of [retained classes][retainedMethodDecls] (and its inclusion reason if specified by
     * [outputDetails]) to [outFile].
     */
    private fun dumpActualMethodSet(
        retainedMethodDecls: Map<ResolvedMethodLikeDeclaration, CallableDeclaration<*>>,
        outFile: Path,
        outputDetails: Boolean = configuration.outputDetails
    ) {
        outFile.bufferedWriter().use { writer ->
            retainedMethodDecls.entries
                .map { (resolvedDecl, astDecl) ->
                    resolvedDecl.bytecodeQualifiedSignature to astDecl
                }
                .sortedBy { it.first }
                .forEach { (qsig, astDecl) ->
                    writer.appendLine(qsig)

                    if (outputDetails) {
                        astDecl.inclusionReasonsData.synchronizedWith {
                            map { it.toReasonString() }
                                .distinct()
                                .sorted()
                                .forEach {
                                    writer.appendLine("    $it")
                                }
                        }
                    }
                }
        }
    }

    /**
     * Dumps the names of [retained methods][retainedMethodDecls] to an output file in [toolOutDir] as specified by
     * [toolOutPath].
     *
     * @param outputDebug Whether to output the inclusion reasons of the retained classes.
     */
    private fun dumpActualMethodSet(
        retainedMethodDecls: Map<ResolvedMethodLikeDeclaration, CallableDeclaration<*>>,
        toolOutDir: ToolOutputDirectory,
        outputDebug: Boolean,
        toolOutPath: ToolOutputDirectory.(Boolean) -> Path
    ) = dumpActualMethodSet(retainedMethodDecls, toolOutDir.toolOutPath(outputDebug), outputDetails = outputDebug)

    protected fun getAndDumpMethods(
        retainedCU: List<CompilationUnit>,
        toolOutputDir: ToolOutputDirectory,
        sourceRoot: SourceRootOutput,
        toolOutPath: ToolOutputDirectory.(Boolean) -> Path
    ): SortedMap<ResolvedMethodLikeDeclaration, CallableDeclaration<*>> {
        val retainedMethods = retainedCU.parallelStream()
            .filter { nodeInTmpSrcRoot(it, sourceRoot) }
            .flatMap { it.findAll<CallableDeclaration<*>> { it.canBeReferencedByName }.stream() }
            .filter { !it.isUnusedForRemovalData }
            .collect(
                Collectors.toConcurrentMap(
                    { symbolSolver.resolveDeclaration<ResolvedMethodLikeDeclaration>(it) },
                    Function.identity(),
                    ThrowingMergerForCallable,
                    { ConcurrentSkipListMap(ResolvedCallableDeclComparator(symbolSolver)) }
                )
            )

        dumpActualMethodSet(retainedMethods, toolOutputDir, false, toolOutPath)
        if (configuration.outputDetails && sourceRoot == SourceRootOutput.ALL) {
            dumpActualMethodSet(retainedMethods, toolOutputDir, true, toolOutPath)
        }

        return retainedMethods
    }

    protected fun getMethodPositiveSet(
        toolOutDir: ToolOutputDirectory,
        sourceRoot: SourceRootOutput
    ): Set<String> {
        return toolOutDir.getGroundTruthMethodsPath(sourceRoot, false)
            .bufferedReader()
            .use { it.readLines() }
            .toSet()
    }

    /**
     * Blends sources in [sourcesPath] into the [workspaceDir].
     */
    private fun blendWorkspace(workspaceDir: Path, sourcesPath: Path) {
        sourceRoots.map { d4jWorkspace.workspace.relativize(it) }
            .map { workspaceDir.resolve(it) }
            .forEach { dir ->
                PredicatedFileCollector(dir)
                    .collect { it.extension == "java" }
                    .forEach { it.deleteExisting() }
            }

        if (sourcesPath.isDirectory()) {
            LOGGER.info("Blending sources from $sourcesPath into $workspaceDir")
            PredicatedFileCollector(sourcesPath)
                .collect { it.isRegularFile() }
                .forEach { srcPath ->
                    val dstPath = workspaceDir
                        .resolve(sourcesPath.relativize(srcPath))
                        .apply { parent?.createDirectories() }

                    srcPath.copyTo(dstPath, overwrite = true)
                }
        } else {
            LOGGER.info("Decompressing and blending sources from $sourcesPath into $workspaceDir")
            FileCompressionUtils.decompressDir(sourcesPath, workspaceDir)
        }

        // Fixup: chmod +x ./gradlew
        workspaceDir.resolve("gradlew")
            .apply {
                if (isRegularFile()) {
                    val execMask = setOf(
                        PosixFilePermission.OWNER_EXECUTE,
                        PosixFilePermission.GROUP_EXECUTE,
                        PosixFilePermission.OTHERS_EXECUTE
                    )
                    setPosixFilePermissions(getPosixFilePermissions() + execMask)
                }
            }
    }

    /**
     * Blends sources in [sourcesTar] into the [workspace].
     */
    protected fun blendWorkspace(workspace: Defects4JWorkspace, sourcesTar: Path) {
        blendWorkspace(workspace.workspace, sourcesTar)
    }

    /**
     * Creates a workspace blending out-of-tree sources with files from the original bug.
     *
     * @param tempDir A temporary directory to blend sources into.
     * @param sourcesOutputPath The directory or compressed archive housing all reduced sources.
     * @return [Defects4JWorkspace] to the temporary workspace.
     */
    private fun createBlendedWorkspace(tempDir: Path, sourcesOutputPath: Path): Defects4JWorkspace {
        LOGGER.info("Test Compilation or Run Enabled - Creating Blended Project")

        val tempWorkspace = Defects4JWorkspace(tempDir, d4jWorkspace.d4jRoot, null, configuration.javacVersionOverride)

        LOGGER.info("Checking out temporary workspace")
        tempWorkspace.initAndPrepare(projectRev, forAnalysis = false)

        blendWorkspace(tempWorkspace, sourcesOutputPath)

        return tempWorkspace
    }

    private fun tryCompileCatching(tempWorkspace: Defects4JWorkspace): Result<ProcessOutput> {
        return runCatching {
            tempWorkspace.compile(timeout = configuration.timeout)
        }
    }

    /**
     * Try to compile the project in [tempWorkspace], outputting the result to [Configuration.outputDir].
     */
    private fun tryCompile(tempWorkspace: Defects4JWorkspace, testCase: TestCase?, passNum: UInt?): Boolean {
        LOGGER.info("Test Compilation Enabled - Trying to compile reduced sources")
        val (compileExitCode, compilerLogs) = tryCompileCatching(tempWorkspace)
            .map { it.exitCode to it.stdout }
            .onFailure { tr ->
                LOGGER.warn("Encountered exception while executing `defects4j compile`", tr)
            }
            .getOrElse { ExitCode(137) to emptyList() }

        val toolOutDir = ToolOutputDirectory(configuration.outputDir, projectRev, testCase)

        val compilePath = if (passNum != null) {
            toolOutDir.getCompilePath(passNum)
        } else {
            toolOutDir.getBaselineCompilePath()
        }
        compilePath.bufferedWriter()
            .use { writer ->
                writer.appendLine("Compile ExitCode: $compileExitCode")
                writer.appendLine()
                compilerLogs.forEach { writer.appendLine(it) }
            }

        if (compileExitCode.isSuccess) {
            LOGGER.info("Test compilation successful")
        } else {
            LOGGER.warn("Compilation failed:")
            compilerLogs.forEach { LOGGER.warn(it) }
        }

        return compileExitCode.isSuccess
    }

    private fun tryTestCatching(tempWorkspace: Defects4JWorkspace, testCase: TestCase?): Result<ProcessOutput> {
        return runCatching {
            if (testCase != null) {
                tempWorkspace.test(false, listOf(testCase), configuration.timeout)
            } else {
                tempWorkspace.test(timeout = configuration.timeout)
            }
        }
    }

    /**
     * Try to run test case(s) in the project in [tempWorkspace], outputting the result to [Configuration.outputDir].
     *
     * @param testCase The name of the test case, or `null` to run all relevant tests.
     */
    private fun tryTest(tempWorkspace: Defects4JWorkspace, testCase: TestCase?, passNum: UInt?): Boolean {
        LOGGER.info("Test Run Enabled - Trying to run tests on reduced sources")

        val testExitCode = tryTestCatching(tempWorkspace, testCase)
            .map { it.exitCode }
            .onFailure { tr ->
                LOGGER.warn("Encountered exception while executing `defects4j test`", tr)
            }
            .getOrElse { ExitCode(137) }

        val failingTestsFile = tempWorkspace.workspace.resolve("failing_tests")
        val isTestSuccess = failingTestsFile.inputStream().use { it.available() == 0 }

        val toolOutDir = ToolOutputDirectory(configuration.outputDir, projectRev, testCase)

        val testPath = if (passNum != null) {
            toolOutDir.getTestPath(passNum)
        } else {
            toolOutDir.getBaselineTestPath()
        }
        testPath.bufferedWriter()
            .use { writer ->
                writer.appendLine("Test ExitCode: $testExitCode")
            }

        val failingTestPath = if (passNum != null) {
            toolOutDir.getFailingTestsPath(passNum)
        } else {
            toolOutDir.getBaselineFailingTestsPath()
        }
        failingTestPath.bufferedWriter()
            .use { writer ->
                failingTestsFile
                    .bufferedReader()
                    .use { reader ->
                        reader.copyTo(writer)
                    }
            }

        return if (testExitCode.isSuccess && isTestSuccess) {
            LOGGER.info("Test Run Successful - All tests passed")
            true
        } else if (testExitCode.isSuccess) {
            LOGGER.info("Test Run Successful - Some tests failed")
            false
        } else {
            LOGGER.error("Test Run Failed - Unexpected failure")
            false
        }
    }

    /**
     * Tries to compile and run the test cases by blending the sources from [sourcesOutputPath].
     *
     * @param testCase The name of the test case, or `null` to run all relevant tests.
     */
    protected fun tryCompileAndTest(sourcesOutputPath: Path, testCase: TestCase?, passNum: UInt?): RunResult {
        if (configuration.testLevel == TestLevel.NONE) {
            return RunResult.SUCCESS
        }

        TemporaryPath.createDirectory().use { tempDir ->
            val tempWorkspace = createBlendedWorkspace(tempDir.path, sourcesOutputPath)

            val compileSuccessful = tryCompile(tempWorkspace, testCase, passNum)
            if (!compileSuccessful) {
                return RunResult.COMPILE_FAILED
            }

            if (configuration.testLevel == TestLevel.TEST) {
                val testSuccessful = tryTest(tempWorkspace, testCase, passNum)
                if (!testSuccessful) {
                    if (projectRev.versionId.version == Defects4JWorkspace.BugVersion.FIXED) {
                        return RunResult.TEST_FAILED
                    }

                    val toolOutDir = ToolOutputDirectory(configuration.outputDir, projectRev, testCase)

                    val failingTestsPath = if (passNum != null) {
                        toolOutDir.getFailingTestsPath(passNum)
                    } else {
                        toolOutDir.getBaselineFailingTestsPath()
                    }
                    val failingTests = failingTestsPath
                        .bufferedReader()
                        .use { it.readLines() }
                        .filter { it.startsWith("--- ") }
                        .map { it.removePrefix("--- ") }
                        .toSet()

                    val expectedFailures = toolOutDir.getExpectedFailingTestsPath()
                        .bufferedReader()
                        .use { it.readLines() }
                        .filter { it.isNotBlank() }
                        .toSet()

                    return if (failingTests isSubsetOf expectedFailures) {
                        RunResult.TEST_EXPECTED_FAIL
                    } else {
                        RunResult.TEST_FAILED
                    }
                }
            }
        }

        return RunResult.SUCCESS
    }

    abstract fun run()

    /**
     * Checks out the bug revision in a state where it is ready for reduction.
     */
    fun export() = d4jWorkspace.initAndPrepare(projectRev)

    /**
     * Checks out the bug revision and exports all properties to the project workspace.
     *
     * @param d4jNewRoot Remaps the Defects4J path to this path if provided.
     * @param workspaceNewRoot Remaps the workspace path to this path if provided.
     * @param exportToWorkspace If `true`, exports the properties to the workspace path instead of the output path
     */
    fun exportProperties(d4jNewRoot: Path? = null, workspaceNewRoot: Path? = null, exportToWorkspace: Boolean = false) {
        d4jWorkspace.initAndCheckout(projectRev)

        val newD4jWorkspace = Defects4JWorkspace(
            workspaceNewRoot ?: d4jWorkspace.workspace,
            d4jNewRoot ?: d4jWorkspace.d4jRoot,
            null,
            configuration.javacVersionOverride
        )
        TemporaryPath.createDirectory().use { tempDir ->
            val tempToolOutputDir = ToolOutputDirectory(tempDir.path)

            tempToolOutputDir.getCompileClasspathPath().bufferedWriter().use { writer ->
                compileClasspath
                    .split(':')
                    .map { Path(it) }
                    .joinToString(":") {
                        remapPropertyPath(it, d4jWorkspace, newD4jWorkspace).toString()
                    }.let { writer.appendLine(it) }
            }
            tempToolOutputDir.getTestClasspathPath().bufferedWriter().use { writer ->
                testClasspath
                    .split(':')
                    .map { Path(it) }
                    .joinToString(":") {
                        remapPropertyPath(it, d4jWorkspace, newD4jWorkspace).toString()
                    }.let { writer.appendLine(it) }
            }
            tempToolOutputDir.getSrcClasspathPath().bufferedWriter().use { writer ->
                srcClasspath
                    .split(':')
                    .map { Path(it) }
                    .joinToString(":") {
                        remapPropertyPath(it, d4jWorkspace, newD4jWorkspace).toString()
                    }.let { writer.appendLine(it) }
            }
            tempToolOutputDir.getSourceRootsPath().bufferedWriter().use { writer ->
                sourceRoots.forEach {
                    writer.appendLine(remapPropertyPath(it, d4jWorkspace, newD4jWorkspace).toString())
                }
            }
            tempToolOutputDir.getTriggeringTestsPath().bufferedWriter().use { writer ->
                entrypoints
                    .forEach { entrypoint ->
                        when (entrypoint) {
                            is EntrypointSpec.ClassInput -> "${entrypoint.testClass}"
                            is EntrypointSpec.MethodInput -> "${entrypoint.testClass}::${entrypoint.methodName}"
                        }.let { writer.appendLine(it) }
                    }
            }
            tempToolOutputDir.getExpectedFailingTestsPath().bufferedWriter().use { writer ->
                triggeringTests.forEach { writer.appendLine(it.toString()) }
            }

            // Reset project tree and setup project as-if it is being run by a tool
            prepareWorkspaceForReduction()

            val outputDir = if (exportToWorkspace) {
                ToolOutputDirectory(d4jWorkspace.workspace)
            } else {
                ToolOutputDirectory(configuration.outputDir, projectRev)
            }

            for (fileGetter in EXPORTED_FILES) {
                fileGetter(tempToolOutputDir).copyTo(fileGetter(outputDir))
            }
        }
    }

    companion object {

        private val LOGGER = Logger<AbstractComparisonRunner>()

        val DEFECTS4J_PATH = Path("/defects4j")
        val WORKSPACE_PATH = Path("/workspace")
        val TMP_PATH = Path("/tmp")

        /**
         * Remaps the [CompilationUnit.storage] of a node from [TMP_PATH] to [newRoot].
         */
        private fun remapStoragePath(compilationUnit: CompilationUnit, newRoot: Path): CompilationUnit {
            compilationUnit.storage
                .ifPresent {
                    compilationUnit.setStorage(newRoot.resolve(TMP_PATH.relativize(it.path)), it.encoding)
                }
            return compilationUnit
        }

        /**
         * Calculates the statistics for this run using the [set of all classes][populationSet],
         * [set of predicted-to-load classes][ppSet], and [set of actually-loaded classes][pSet].
         */
        @JvmStatic
        protected fun calcStatistics(populationSet: Set<String>, ppSet: Set<String>, pSet: Set<String>): NumericStatistic {
            // True-Positives: Predicted classes which are actually used
            val tpSet = ppSet intersect pSet
            // False-Positives: Predicted classes which are not used
            val fpSet = ppSet - pSet
            // False-Negatives: Used classes which are not predicted
            val fnSet = pSet - ppSet
            // True-Negatives: Not predicted classes which are also not used
            val tnSet = populationSet.filter { it !in ppSet && it !in pSet }.toSet()

            // Numerics
            val population = populationSet.size
            val p = pSet.size
            val n = population - p
            val pp = ppSet.size
            val pn = population - pp
            val tp = tpSet.size
            val fp = fpSet.size
            val fn = fnSet.size
            val tn = tnSet.size

            return NumericStatistic(p, n, pp, pn, tp, fp, tn, fn)
        }

        @JvmStatic
        protected fun NumericStatistic.dumpToFile(statPath: Path) {
            statPath
                .bufferedWriter()
                .use { writer ->
                    writer.appendLine("Total Count: $population")
                    writer.appendLine("Predicted Count: $pp")
                    writer.appendLine("Actual Count: $p")

                    writer.appendLine("True-Positive: $tp")
                    writer.appendLine("False-Positive: $fp")
                    writer.appendLine("False-Negative: $fn")
                    writer.appendLine("True-Negative: $tn")
                    writer.appendLine("True-Positive Rate: ${tpr.toStringRounded(3)}")
                    writer.appendLine("False-Positive Rate: ${fpr.toStringRounded(3)}")
                    writer.appendLine("False-Negative Rate: ${fnr.toStringRounded(3)}")
                    writer.appendLine("True-Negative Rate: ${tnr.toStringRounded(3)}")
                    writer.appendLine("Accuracy: ${accuracy.toStringRounded(3)}")
                    writer.appendLine("Precision: ${ppv.toStringRounded(3)}")
                    writer.appendLine("Recall: ${tpr.toStringRounded(3)}")
                    writer.appendLine("F-score: ${f1.toStringRounded(3)}")
                    writer.appendLine("Informedness: ${informedness.toStringRounded(3)}")
                    writer.appendLine("Prevalence Threshold: ${pt.toStringRounded(3)}")
                    writer.appendLine("Prevalence: ${prevalence.toStringRounded(3)}")
                    writer.appendLine("False Omission Rate: ${falseOmissionRate.toStringRounded(3)}")
                    writer.appendLine("Positive Likelihood Ratio: ${lrPlus.toStringRounded(3)}")
                    writer.appendLine("Negative Likelihood Ratio: ${lrMinus.toStringRounded(3)}")
                    writer.appendLine("False Discovery Rate: ${fdr.toStringRounded(3)}")
                    writer.appendLine("Negative Predictive Value: ${npv.toStringRounded(3)}")
                    writer.appendLine("Markedness: ${mk.toStringRounded(3)}")
                    writer.appendLine("Diagnostic Odds Ratio: ${dor.toStringRounded(3)}")
                    writer.appendLine("Balanced Accuracy: ${balancedAccuracy.toStringRounded(3)}")
                    writer.appendLine("Fowlkes-Mallows Index: ${fm.toStringRounded(3)}")
                    writer.appendLine("Matthews Correlation Coefficient: ${mcc.toStringRounded(3)}")
                    writer.appendLine("Threat Score: ${ts.toStringRounded(3)}")
                }
        }

        /**
         * Dumps all [reduced sources][retainedCU] into a compressed [outTar].
         */
        @JvmStatic
        protected fun dumpReducedSources(retainedCU: Collection<CompilationUnit>, outTar: Path) {
            TemporaryPath.createDirectory().use { tempDir ->
                LOGGER.info("Saving sources to ${tempDir.path}")

                retainedCU.forEach {
                    remapStoragePath(it, tempDir.path).storage.get().save()
                }

                LOGGER.info("Compressing sources to $outTar")
                FileCompressionUtils.compressDir(tempDir.path, outTar)
            }
        }

        /**
         * Remaps a [path] in a [Defects4JWorkspace] to another [Defects4JWorkspace], if the path is in the Defects4J Git
         * tree or the bug workspace.
         */
        private fun remapPropertyPath(
            path: Path,
            d4jWorkspace: Defects4JWorkspace,
            newD4jWorkspace: Defects4JWorkspace
        ): Path {
            return when {
                path.startsWith(d4jWorkspace.workspace) ->
                    newD4jWorkspace.workspace.resolve(d4jWorkspace.workspace.relativize(path))
                path.startsWith(d4jWorkspace.d4jRoot) ->
                    newD4jWorkspace.d4jRoot.resolve(d4jWorkspace.d4jRoot.relativize(path))
                else -> path
            }
        }

        private val EXPORTED_FILES = listOf(
            ToolOutputDirectory::getCompileClasspathPath,
            ToolOutputDirectory::getTestClasspathPath,
            ToolOutputDirectory::getSrcClasspathPath,
            ToolOutputDirectory::getSourceRootsPath,
            ToolOutputDirectory::getTriggeringTestsPath,
        )
    }
}