package com.derppening.researchprojecttoolkit.defects4j

import com.derppening.researchprojecttoolkit.GlobalConfiguration
import com.derppening.researchprojecttoolkit.model.TestMethodFilter
import com.derppening.researchprojecttoolkit.tool.facade.FuzzySymbolSolver
import com.derppening.researchprojecttoolkit.util.*
import com.github.javaparser.ast.body.CallableDeclaration
import com.github.javaparser.ast.body.TypeDeclaration
import com.github.javaparser.resolution.declarations.ResolvedMethodLikeDeclaration
import com.github.javaparser.utils.SourceRoot
import java.nio.file.Path
import java.util.*
import java.util.stream.Collectors
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.io.path.*

class ProjectInfoExporter(
    private val configuration: Configuration,
    private val projectRev: Defects4JWorkspace.ProjectRev,
    private val testMethodFilter: TestMethodFilter,
    private val outBaseDir: Path,
) {

    data class Configuration(
        val workspaceRoot: Path,
        val d4jRoot: Path,
        val ignoreBaseline: Boolean
    )

    private val d4jWorkspace = Defects4JWorkspace(
        configuration.workspaceRoot,
        configuration.d4jRoot,
        null,
        Defects4JWorkspace.JavacVersionOverride.UNCHANGED
    )

    private var isWorkspaceDirty = true

    /**
     * Returns the path to the [toolOutPath] in either the baseline directory or the output directory, or `null` if the
     * file is not present.
     */
    private fun getExistingOrNull(testCase: TestCase?, toolOutPath: (ToolOutputDirectory) -> Path): Path? {
        val dirsToSearch = listOfNotNull(
            ToolOutputDirectory(GlobalConfiguration.INSTANCE.cmdlineOpts.baselineDir, projectRev, testCase, true)
                .takeUnless { configuration.ignoreBaseline },
            ToolOutputDirectory(outBaseDir, projectRev, testCase, true)
        )

        return dirsToSearch
            .flatMap { listOf(it.getCompressedFile(toolOutPath), toolOutPath(it)) }
            .firstOrNull { it.exists() }
    }

    /**
     * Checks whether all paths in [toolOutPaths] is present in either the output directory or the baseline directory.
     */
    private fun isOutputsAllPresent(
        toolOutPaths: List<(ToolOutputDirectory) -> Path>
    ): Boolean = toolOutPaths.all { getExistingOrNull(null, it) != null }

    /**
     * Checks whether the paths in [toolOutPath] is present in either the output directory or the baseline directory.
     */
    private fun isOutputPresent(toolOutPath: (ToolOutputDirectory) -> Path): Boolean =
        isOutputsAllPresent(listOf(toolOutPath))

    /**
     * Returns the path to the [toolOutPath] in either the output directory or the baseline directory.
     */
    private fun getExisting(
        toolOutPath: (ToolOutputDirectory) -> Path
    ): Path = checkNotNull(getExistingOrNull(null, toolOutPath)) {
        "Cannot find path `${toolOutPath(ToolOutputDirectory(outBaseDir, projectRev, null, true))}`"
    }

    /**
     * Checks whether all paths in [toolOutPaths] is present in either the output directory or the baseline directory.
     */
    private fun isOutputsAllPresent(
        toolOutPaths: List<(ToolOutputDirectory) -> Path>,
        testCase: TestCase
    ): Boolean = toolOutPaths.all { getExistingOrNull(testCase, it) != null }

    /**
     * Checks whether the paths in [toolOutPath] is present in either the output directory or the baseline directory.
     */
    private fun isOutputPresent(toolOutPath: (ToolOutputDirectory) -> Path, testCase: TestCase): Boolean =
        isOutputsAllPresent(listOf(toolOutPath), testCase)

    /**
     * Returns the path to the [toolOutPath] in either the output directory or the baseline directory.
     */
    private fun getExisting(
        toolOutPath: (ToolOutputDirectory) -> Path,
        testCase: TestCase
    ): Path = checkNotNull(getExistingOrNull(testCase, toolOutPath)) {
        "Cannot find path `${toolOutPath(ToolOutputDirectory(outBaseDir, projectRev, testCase, true))}`"
    }

    /**
     * Compresses the file specified by [pathGetter] in [toolOutDir].
     */
    private fun compressFile(
        toolOutDir: ToolOutputDirectory,
        pathGetter: ToolOutputDirectory.() -> Path
    ) {
        if (toolOutDir.pathGetter().exists()) {
            if (toolOutDir.getCompressedFile(pathGetter).notExists()) {
                FileCompressionUtils.compressFile(
                    toolOutDir.pathGetter(),
                    toolOutDir.getCompressedFile(pathGetter)
                )
            }

            toolOutDir.pathGetter().deleteIfExists()
        }
    }

    /**
     * Compresses the [coverageXml] and save into [the compressed version][ToolOutputDirectory.getCompressedFile] of
     * [pathGetter] in [toolOutDir].
     */
    private fun compressCoverage(
        coverageXml: Path,
        toolOutDir: ToolOutputDirectory,
        pathGetter: ToolOutputDirectory.() -> Path
    ) {
        if (coverageXml.exists()) {
            coverageXml.copyTo(toolOutDir.pathGetter())
            compressFile(toolOutDir, pathGetter)
        }
    }

    private fun reset() {
        d4jWorkspace.initAndPrepare(projectRev, forAnalysis = false)
        d4jWorkspace.patchD4JCoverage()
    }

    /**
     * Runs the given [block], resetting the workspace before running the block if [isWorkspaceDirty] is set to `true`.
     *
     * @param dirtyWorkspace Whether the workspace should be set to dirty after running [block]. This is necessary if
     * [block] adds/modifies/deletes existing files in the workspace directory.
     */
    @OptIn(ExperimentalContracts::class)
    private fun <R> runResetting(dirtyWorkspace: Boolean = false, block: () -> R): R {
        contract {
            callsInPlace(block, InvocationKind.EXACTLY_ONCE)
        }

        if (isWorkspaceDirty) {
            reset()
            isWorkspaceDirty = false
        }

        return try {
            block()
        } finally {
            if (dirtyWorkspace) {
                isWorkspaceDirty = true
            }
        }
    }

    private fun dumpSourceRootClasses(
        sourceRootDir: Path,
        symbolSolver: FuzzySymbolSolver,
        sourceRoot: SourceRootOutput,
        toolOutDir: ToolOutputDirectory
    ): Pair<Set<String>, Set<String>> {
        val isOutputPresent = isOutputsAllPresent(listOf(
            { it.getAllClassesInSourceRootPath(sourceRoot) },
            { it.getAllMethodsInSourceRootPath(sourceRoot) },
        ))

        val allNamedSrcTypes = if (!isOutputPresent) {
            SourceRoot(sourceRootDir, createParserConfiguration(symbolSolver))
                .tryToParseParallelized()
                .parallelStream()
                .filter { it.isSuccessful }
                .map { it.result.get() }
                .flatMap { it.findAll<TypeDeclaration<*>> { it.canBeReferencedByName }.stream() }
                .collect(Collectors.toCollection { TreeSet(TYPE_DECL_COMPARATOR) })
        } else null

        val srcClasses = if (!isOutputPresent { it.getAllClassesInSourceRootPath(sourceRoot) }) {
            val allClasses = allNamedSrcTypes!!.parallelStream()
                .map { it.fullyQualifiedName.get() }
                .collect(Collectors.toSet())

            toolOutDir.getAllClassesInSourceRootPath(sourceRoot)
                .bufferedWriter()
                .use { writer ->
                    allClasses.sorted()
                        .forEach { writer.appendLine(it) }
                }

            allClasses
        } else {
            getExisting { it.getAllClassesInSourceRootPath(sourceRoot) }
                .bufferedReader()
                .useLines { lines -> lines.filter { it.isNotEmpty() }.toSet() }
        }

        val srcMethods = if (!isOutputPresent { it.getAllMethodsInSourceRootPath(sourceRoot) }) {
            val allMethods = allNamedSrcTypes!!.parallelStream()
                .flatMap { it.findAll<CallableDeclaration<*>>().stream() }
                .filter { it.canBeReferencedByName }
                .map { symbolSolver.resolveDeclaration<ResolvedMethodLikeDeclaration>(it).bytecodeQualifiedSignature }
                .collect(Collectors.toSet())

            toolOutDir.getAllMethodsInSourceRootPath(sourceRoot)
                .bufferedWriter()
                .use { writer ->
                    allMethods.sorted()
                        .forEach { writer.appendLine(it) }
                }

            allMethods
        } else {
            getExisting { it.getAllMethodsInSourceRootPath(sourceRoot) }
                .bufferedReader()
                .useLines { lines -> lines.filter { it.isNotEmpty() }.toSet() }
        }

        return srcClasses to srcMethods
    }

    private fun runCoberturaCoverage(
        testCase: TestCase,
        classFilter: ClassFilter,
        toolOutDir: ToolOutputDirectory
    ) {
        if (isOutputPresent({ it.getBaselineCoberturaCoveragePath(classFilter) }, testCase)) {
            return
        }

        val coberturaXml = d4jWorkspace.workspace.resolve("coverage.xml")
        try {
            LOGGER.info("Running coverage over test case(s)")
            d4jWorkspace.coverage(
                false,
                listOf(testCase),
                instrumentClasses = getExisting({
                    it.getAllClassesInSourceRootPath(classFilter.toSourceRootOutput())
                }, testCase)
            )

            compressCoverage(coberturaXml, toolOutDir) {
                getBaselineCoberturaCoveragePath(classFilter)
            }
        } finally {
            coberturaXml.deleteIfExists()
        }
    }

    private fun runJacocoCoverage(
        testCase: TestCase,
        classFilter: ClassFilter,
        toolOutDir: ToolOutputDirectory
    ) {
        if (isOutputPresent({ it.getBaselineJacocoCoveragePath(classFilter) }, testCase)) {
            return
        }

        val instrumentClassesSet = getExisting({
            it.getAllClassesInSourceRootPath(classFilter.toSourceRootOutput())
        }, testCase)
            .bufferedReader()
            .useLines { lines -> lines.filter { it.isNotEmpty() }.toSet() }

        val jacocoXml = d4jWorkspace.workspace.resolve("jacoco.xml")
        try {
            d4jWorkspace.coverageJacoco(listOf(testCase), instrumentClassesSet.toList())

            compressCoverage(jacocoXml, toolOutDir) {
                getBaselineJacocoCoveragePath(classFilter)
            }
        } finally {
            jacocoXml.deleteIfExists()
        }
    }

    private fun runCommon() {
        val toolOutDir = ToolOutputDirectory(outBaseDir, projectRev)

        val dirSrcClasses = if (!isOutputPresent(ToolOutputDirectory::getClassesSourceRootPath)) {
            LOGGER.info("Exporting classes source root")

            val dirSrcClasses = d4jWorkspace.export(Defects4JWorkspace.Property.DIR_SRC_CLASSES).trim()
                .let { d4jWorkspace.workspace.resolve(it) }

            toolOutDir.getClassesSourceRootPath()
                .bufferedWriter()
                .use { writer ->
                    writer.appendLine(dirSrcClasses.toString())
                }

            lazy(LazyThreadSafetyMode.NONE) {
                dirSrcClasses
            }
        } else {
            lazy(LazyThreadSafetyMode.NONE) {
                getExisting(ToolOutputDirectory::getClassesSourceRootPath)
                    .bufferedReader()
                    .use { it.readText() }
                    .trim()
                    .let { Path(it) }
            }
        }

        val dirSrcTests = if (!isOutputPresent(ToolOutputDirectory::getTestSourceRootPath)) {
            LOGGER.info("Exporting tests source root")

            val dirSrcTests = d4jWorkspace.export(Defects4JWorkspace.Property.DIR_SRC_TESTS).trim()
                .let { d4jWorkspace.workspace.resolve(it) }

            toolOutDir.getTestSourceRootPath()
                .bufferedWriter()
                .use { writer ->
                    writer.appendLine(dirSrcTests.toString())
                }

            lazy(LazyThreadSafetyMode.NONE) {
                dirSrcTests
            }
        } else {
            lazy(LazyThreadSafetyMode.NONE) {
                getExisting(ToolOutputDirectory::getTestSourceRootPath)
                    .bufferedReader()
                    .use { it.readText() }
                    .trim()
                    .let { Path(it) }
            }
        }

        if (!isOutputPresent(ToolOutputDirectory::getSrcClasspathPath)) {
            LOGGER.info("Exporting sources classpath")

            val scp = projectRev.getAdditionalSourceClasspaths(d4jWorkspace)

            toolOutDir.getSrcClasspathPath()
                .bufferedWriter()
                .use { writer ->
                    writer.appendLine(scp)
                }
        }

        if (!isOutputPresent(ToolOutputDirectory::getSourceRootsPath)) {
            LOGGER.info("Exporting source roots")

            val sr = listOf(dirSrcClasses.value, dirSrcTests.value)

            toolOutDir.getSourceRootsPath()
                .bufferedWriter()
                .use { writer ->
                    sr.forEach {
                        writer.appendLine(it.toString())
                    }
                }
        }

        if (!isOutputPresent(ToolOutputDirectory::getRelevantTestsPath)) {
            LOGGER.info("Exporting relevant test classes")

            val tt = d4jWorkspace.export(Defects4JWorkspace.Property.TESTS_RELEVANT)
                .split('\n')

            toolOutDir.getRelevantTestsPath()
                .bufferedWriter()
                .use { writer ->
                    tt.forEach {
                        writer.appendLine(it)
                    }
                }
        }

        if (!isOutputPresent(ToolOutputDirectory::getTriggeringTestsPath)) {
            LOGGER.info("Exporting triggering test cases")

            val tt = d4jWorkspace.triggeringTests

            toolOutDir.getTriggeringTestsPath()
                .bufferedWriter()
                .use { writer ->
                    tt.forEach {
                        writer.appendLine(it.toString())
                    }
                }
        }

        if (!isOutputPresent(ToolOutputDirectory::getCompileClasspathPath)) {
            LOGGER.info("Exporting compile classpath")

            val cp = d4jWorkspace.getCompileClasspath(projectRev, ignoreBaseline = true)
            isWorkspaceDirty = true

            toolOutDir.getCompileClasspathPath()
                .bufferedWriter()
                .use { writer ->
                    writer.appendLine(cp)
                }
        }

        if (!isOutputPresent(ToolOutputDirectory::getTestClasspathPath)) {
            LOGGER.info("Exporting test classpath")

            val cp = d4jWorkspace.getTestClasspath(projectRev, ignoreBaseline = true)
            isWorkspaceDirty = true

            toolOutDir.getTestClasspathPath()
                .bufferedWriter()
                .use { writer ->
                    writer.appendLine(cp)
                }
        }

        if (!isOutputPresent(ToolOutputDirectory::getAllTestsPath)) {
            LOGGER.info("Exporting all test classes")

            val tt = d4jWorkspace.export(Defects4JWorkspace.Property.TESTS_ALL)
                .split('\n')
            isWorkspaceDirty = true

            toolOutDir.getAllTestsPath()
                .bufferedWriter()
                .use { writer ->
                    tt.forEach {
                        writer.appendLine(it)
                    }
                }
        }

        if (!isOutputsAllPresent(ALL_CLASSES_PATHS)) {
            LOGGER.info("Exporting all classes/methods list")

            val (allClasses, allMethods) = run {
                d4jWorkspace.doBeforeAnalyze(projectRev)

                val symbolSolver = projectRev.getSymbolSolver(d4jWorkspace)

                val (srcClasses, srcMethods) = dumpSourceRootClasses(
                    dirSrcClasses.value,
                    symbolSolver,
                    SourceRootOutput.SOURCE,
                    toolOutDir
                )
                val (testClasses, testMethods) = dumpSourceRootClasses(
                    dirSrcTests.value,
                    symbolSolver,
                    SourceRootOutput.TEST,
                    toolOutDir
                )

                (srcClasses + testClasses) to (srcMethods + testMethods)
            }

            toolOutDir.getAllClassesInSourceRootPath(SourceRootOutput.ALL)
                .bufferedWriter()
                .use { writer ->
                    allClasses.sorted()
                        .forEach { writer.appendLine(it) }
                }

            toolOutDir.getAllMethodsInSourceRootPath(SourceRootOutput.ALL)
                .bufferedWriter()
                .use { writer ->
                    allMethods.sorted()
                        .forEach { writer.appendLine(it) }
                }
        }
    }

    private fun runSingle(testCase: TestCase) {
        val toolOutDir = ToolOutputDirectory(outBaseDir, projectRev, testCase)

        val isFallbackBaselinePresent = listOf(BASELINE_METHOD_SRC_FALLBACK_PATHS, BASELINE_METHOD_TEST_FALLBACK_PATHS)
            .any { isOutputsAllPresent(it, testCase) }
        if (isOutputsAllPresent(SINGLE_EXPORT_PATHS, testCase) || (isOutputsAllPresent(SINGLE_EXPORT_FALLBACK_PATHS, testCase) && isFallbackBaselinePresent)) {
            LOGGER.info("Baseline for $testCase already executed - Skipping")
            return
        }

        LOGGER.info("Running baseline for $testCase")

        val isFullBaselinePresent = isOutputsAllPresent(BASELINE_COBERTURA_COVERAGE_PATHS, testCase)
        if (!isFullBaselinePresent && !isFallbackBaselinePresent) {
            LOGGER.info("Exporting Cobertura bytecode methods list")

            try {
                runCoberturaCoverage(testCase, ClassFilter.ALL, toolOutDir)
            } catch (ex: Throwable) {
                LOGGER.warn("Unable to export Cobertura bytecode methods list - Falling back to exporting method list by source root", ex)

                arrayOf(
                    ClassFilter.CLASSES,
                    ClassFilter.TESTS
                ).forEach {
                    runCatching {
                        runCoberturaCoverage(testCase, it, toolOutDir)
                    }
                }
            }
        }

        val isJacocoBaselinePresent = isOutputsAllPresent(BASELINE_JACOCO_COVERAGE_PATHS, testCase)
        if (!isJacocoBaselinePresent && !isFallbackBaselinePresent) {
            LOGGER.info("Exporting Jacoco bytecode methods list")

            try {
                runJacocoCoverage(testCase, ClassFilter.ALL, toolOutDir)
            } catch (ex: Throwable) {
                LOGGER.warn("Unable to export Jacoco bytecode methods list - Falling back to exporting method list by source root", ex)

                arrayOf(
                    ClassFilter.CLASSES,
                    ClassFilter.TESTS
                ).forEach {
                    runCatching {
                        runJacocoCoverage(testCase, it, toolOutDir)
                    }
                }
            }
        }

        if (!isOutputsAllPresent(EXPECTED_CLASSES_PATHS, testCase)) {
            LOGGER.info("Exporting expected classes list")

            val loadedClasses = checkNotNull(d4jWorkspace.monitorTest(listOf(testCase), outputAll = true).first)

            if (!isOutputPresent(ToolOutputDirectory::getExpectedSourceClassesPath, testCase)) {
                toolOutDir.getExpectedSourceClassesPath()
                    .bufferedWriter()
                    .use { writer ->
                        loadedClasses.sourceClasses
                            .sorted()
                            .forEach { writer.appendLine(it) }
                    }
            }
            if (!isOutputPresent(ToolOutputDirectory::getExpectedTestClassesPath, testCase)) {
                toolOutDir.getExpectedTestClassesPath()
                    .bufferedWriter()
                    .use { writer ->
                        loadedClasses.testClasses
                            .sorted()
                            .forEach { writer.appendLine(it) }
                    }
            }
        }
    }

    fun run() {
        if (!isOutputsAllPresent(REV_COMMON_PATHS)) {
            runResetting {
                runCommon()
            }
        }

        LOGGER.info("Collecting test cases using ${testMethodFilter.cmdlineOpt.first()} filter")

        // getFilteredTestCases will output classpath, which requires compilation, and will dirty the workspace.
        val testCases = runResetting(true) {
            d4jWorkspace.doBeforeAnalyze(projectRev)

            testMethodFilter.getFilteredTestCases(d4jWorkspace, projectRev, ignoreBaseline = true)
                .sortedBy { it.toString() }
        }
        LOGGER.info("Collected ${testCases.size} test cases using ${testMethodFilter.cmdlineOpt.first()} filter")

        testCases.forEach { testCase ->
            runCatching {
                runSingle(testCase)
            }.onFailure { tr ->
                LOGGER.error("Unable to export baseline for $projectRev $testCase", tr)
            }
        }

        clearMemory()
    }

    companion object {

        private val LOGGER = Logger<ProjectInfoExporter>()

        private val ALL_CLASSES_PATHS = listOf<(ToolOutputDirectory) -> Path>(
            { it.getAllClassesInSourceRootPath(SourceRootOutput.SOURCE) },
            { it.getAllMethodsInSourceRootPath(SourceRootOutput.SOURCE) },
            { it.getAllClassesInSourceRootPath(SourceRootOutput.TEST) },
            { it.getAllMethodsInSourceRootPath(SourceRootOutput.TEST) },
            { it.getAllClassesInSourceRootPath(SourceRootOutput.ALL) },
            { it.getAllMethodsInSourceRootPath(SourceRootOutput.ALL) },
        )

        private val REV_COMMON_PATHS = listOf(
            ToolOutputDirectory::getClassesSourceRootPath,
            ToolOutputDirectory::getCompileClasspathPath,
            ToolOutputDirectory::getTestSourceRootPath,
            ToolOutputDirectory::getSrcClasspathPath,
            ToolOutputDirectory::getSourceRootsPath,
            ToolOutputDirectory::getRelevantTestsPath,
            ToolOutputDirectory::getTriggeringTestsPath,
            ToolOutputDirectory::getTestClasspathPath,
            ToolOutputDirectory::getAllTestsPath,
        ) + ALL_CLASSES_PATHS

        private val BASELINE_COBERTURA_COVERAGE_PATHS = listOf<(ToolOutputDirectory) -> Path>(
            ToolOutputDirectory::getBaselineCoberturaCoveragePath,
        )

        private val BASELINE_JACOCO_COVERAGE_PATHS = listOf<(ToolOutputDirectory) -> Path>(
            ToolOutputDirectory::getBaselineJacocoCoveragePath,
        )

        private val BASELINE_METHOD_SRC_FALLBACK_PATHS = listOf<(ToolOutputDirectory) -> Path>(
            ToolOutputDirectory::getBaselineCoberturaClassCoveragePath,
            ToolOutputDirectory::getBaselineJacocoClassCoveragePath
        )

        private val BASELINE_METHOD_TEST_FALLBACK_PATHS = listOf<(ToolOutputDirectory) -> Path>(
            ToolOutputDirectory::getBaselineCoberturaTestCoveragePath,
            ToolOutputDirectory::getBaselineJacocoTestCoveragePath
        )

        private val EXPECTED_CLASSES_PATHS = listOf<(ToolOutputDirectory) -> Path>(
            ToolOutputDirectory::getExpectedSourceClassesPath,
            ToolOutputDirectory::getExpectedTestClassesPath,
        )

        private val SINGLE_EXPORT_FALLBACK_PATHS = EXPECTED_CLASSES_PATHS
        private val SINGLE_EXPORT_PATHS = SINGLE_EXPORT_FALLBACK_PATHS +
                BASELINE_COBERTURA_COVERAGE_PATHS +
                BASELINE_JACOCO_COVERAGE_PATHS

        private fun ClassFilter.toSourceRootOutput(): SourceRootOutput = when (this) {
            ClassFilter.CLASSES -> SourceRootOutput.SOURCE
            ClassFilter.TESTS -> SourceRootOutput.TEST
            ClassFilter.ALL -> SourceRootOutput.ALL
        }
    }
}