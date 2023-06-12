package com.derppening.researchprojecttoolkit.util

import com.derppening.researchprojecttoolkit.defects4j.ClassFilter
import com.derppening.researchprojecttoolkit.defects4j.SourceRootOutput
import com.derppening.researchprojecttoolkit.model.*
import org.dom4j.io.SAXReader
import java.nio.file.Path
import kotlin.io.path.*

/**
 * Abstraction for a directory which holds all tool results.
 *
 * @property baseDir The directory to output files which is constant within a single bug revision.
 */
class ToolOutputDirectory(private val baseDir: Path, private val relPath: Path?, readOnly: Boolean = false) {

    /**
     * Creates an instance of [ToolOutputDirectory] which outputs all tool files to [basePath].
     */
    constructor(basePath: Path, readOnly: Boolean = false) : this(basePath, null, readOnly)

    /**
     * Creates an instance of [ToolOutputDirectory] which outputs all tool files a path constructed from [basePath],
     * [projectRev] and [testCase].
     */
    constructor(
        basePath: Path,
        projectRev: Defects4JWorkspace.ProjectRev,
        testCase: TestCase? = null,
        readOnly: Boolean = false
    ) : this(basePath.resolve(projectRev.toString()), testCase?.let { Path(it.toString()) }, readOnly)

    /**
     * The directory to output files which differ between test cases.
     */
    private val outputDir: Path get() = relPath?.let { baseDir.resolve(it) } ?: baseDir

    init {
        if (outputDir.exists()) {
            check(outputDir.isDirectory())
        } else if (!readOnly) {
            outputDir.createDirectories()
        }
    }

    /**
     * Path to the compressed version of the [given output file][pathGetter].
     */
    fun getCompressedFile(pathGetter: ToolOutputDirectory.() -> Path): Path =
        this.pathGetter().let {
            val compFileName = Path("${it.fileName}.zst")
            it.parent?.resolve(compFileName) ?: compFileName
        }

    /**
     * Path to the compressed version of the [given output directory][pathGetter].
     */
    fun getCompressedDir(pathGetter: ToolOutputDirectory.() -> Path): Path =
        this.pathGetter().let {
            val compFileName = Path("${it.fileName}.tar.zst")
            it.parent?.resolve(compFileName) ?: compFileName
        }

    /**
     * All paths for the given [output][pathGetter] after any reduction pass.
     */
    fun getAllPassesOf(pathGetter: ToolOutputDirectory.(UInt) -> Path): List<Path> {
        return generateSequence(0u) { it + 1u }
            .map { this.pathGetter(it) }
            .takeWhile { it.exists() }
            .toList()
    }

    fun getLastPass(): UInt? {
        return generateSequence(0u) { it + 1u }
            .takeWhile { outputDir.resolve(getReductionPassDir(it)).exists() }
            .lastOrNull()
    }

    /**
     * Path to the given [output][pathGetter] after performing all passes of reduction, or `null` if no reduction pass
     * for the output is found.
     */
    fun getLastPassOf(pathGetter: ToolOutputDirectory.(UInt) -> Path): Path? {
        return getAllPassesOf(pathGetter).lastOrNull()
    }

    /**
     * Path to file containing all retained classes after reduction.
     */
    fun getActualClassesPath(passNum: UInt, sourceRoot: SourceRootOutput, isDebug: Boolean): Path =
        outputDir.resolve(getReducedClassesRelPath(passNum, sourceRoot, isDebug))

    /**
     * Path to file containing all retained methods after reduction.
     */
    fun getActualMethodsPath(passNum: UInt, sourceRoot: SourceRootOutput, isDebug: Boolean): Path =
        outputDir.resolve(getReducedMethodsRelPath(passNum, sourceRoot, isDebug))

    /**
     * Path to Zstd-compressed archive containing all retained sources after performing the reduction [passNum] times.
     *
     * @param passNum 0-based index of the current pass.
     */
    fun getSourcesPath(passNum: UInt): Path = outputDir.resolve(getCompressedDir { getReducedSourcesRelPath(passNum) })

    /**
     * Path to file containing the diff between predicted and actual set of classes after reduction.
     */
    fun getClassDiffPath(passNum: UInt, sourceRoot: SourceRootOutput): Path =
        outputDir.resolve(getClassDiffRelPath(passNum, sourceRoot))

    /**
     * Path to file containing the diff between predicted and actual set of methods after reduction.
     */
    fun getMethodDiffPath(passNum: UInt, sourceRoot: SourceRootOutput): Path =
        outputDir.resolve(getMethodDiffRelPath(passNum, sourceRoot))

    /**
     * Path to file containing statistics between predicted and actual set of classes after reduction.
     */
    fun getClassStatPath(passNum: UInt, sourceRoot: SourceRootOutput): Path =
        outputDir.resolve(getClassStatRelPath(passNum, sourceRoot))

    /**
     * Path to file containing statistics between predicted and actual set of methods after reduction.
     */
    fun getMethodStatPath(passNum: UInt, sourceRoot: SourceRootOutput): Path =
        outputDir.resolve(getMethodStatRelPath(passNum, sourceRoot))

    fun getRunningTimePath(passNum: UInt): Path = outputDir.resolve(getRunningTimeRelPath(passNum))

    /**
     * Path to file containing the compilation result after reduction.
     */
    fun getCompilePath(passNum: UInt): Path = outputDir.resolve(getReducedCompileRelPath(passNum))

    /**
     * Path to file containing the test result after reduction.
     */
    fun getTestPath(passNum: UInt): Path = outputDir.resolve(getReducedTestRelPath(passNum))

    /**
     * Path to file containing the list of failing tests and reasons after reduction.
     */
    fun getFailingTestsPath(passNum: UInt): Path = outputDir.resolve(getReducedFailingTestsRelPath(passNum))

    fun getBaselineClassesPath(sourceRoot: SourceRootOutput, isDebug: Boolean): Path =
        outputDir.resolve(getBaselineClassesRelPath(sourceRoot, isDebug))

    fun getBaselineMethodsPath(sourceRoot: SourceRootOutput, isDebug: Boolean): Path =
        outputDir.resolve(getBaselineMethodsRelPath(sourceRoot, isDebug))

    fun getBaselineSourcesPath(): Path = outputDir.resolve(getCompressedDir { getBaselineSourcesRelPath() })

    fun getBaselineClassDiffPath(sourceRoot: SourceRootOutput): Path =
        outputDir.resolve(getBaselineClassDiffRelPath(sourceRoot))

    fun getBaselineMethodDiffPath(sourceRoot: SourceRootOutput): Path =
        outputDir.resolve(getBaselineMethodDiffRelPath(sourceRoot))

    fun getBaselineClassStatPath(sourceRoot: SourceRootOutput): Path =
        outputDir.resolve(getBaselineClassStatRelPath(sourceRoot))

    fun getBaselineMethodStatPath(sourceRoot: SourceRootOutput): Path =
        outputDir.resolve(getBaselineMethodStatRelPath(sourceRoot))

    fun getBaselineRunningTimePath(): Path =
        outputDir.resolve(getBaselineRunningTimeRelPath())

    fun getBaselineCompilePath(): Path = outputDir.resolve(getBaselineCompileRelPath())

    fun getBaselineTestPath(): Path = outputDir.resolve(getBaselineTestRelPath())

    fun getBaselineFailingTestsPath(): Path = outputDir.resolve(getBaselineFailingTestsRelPath())

    /**
     * Path to file containing the list of expected source classes obtained by running [Defects4JWorkspace.monitorTest]
     * on the test case(s).
     */
    fun getExpectedSourceClassesPath(): Path = outputDir.resolve(EXPECTED_SOURCE_CLASSES_REL_PATH)

    /**
     * Path to file containing the list of expected test classes obtained by running [Defects4JWorkspace.monitorTest]
     * on the test case(s).
     */
    fun getExpectedTestClassesPath(): Path = outputDir.resolve(EXPECTED_TEST_CLASSES_REL_PATH)

    /**
     * Path to the file containing the XML of Cobertura coverage executed on the baseline.
     */
    fun getBaselineCoberturaCoveragePath(): Path = outputDir.resolve(BASELINE_COBERTURA_COVERAGE_FILE)

    /**
     * Path to file containing the XML of Cobertura class coverage executed on the baseline.
     */
    fun getBaselineCoberturaClassCoveragePath(): Path = outputDir.resolve(BASELINE_COBERTURA_CLASS_COVERAGE_FILE)

    /**
     * Path to file containing the XML of Cobertura test coverage executed on the baseline.
     */
    fun getBaselineCoberturaTestCoveragePath(): Path = outputDir.resolve(BASELINE_COBERTURA_TEST_COVERAGE_FILE)

    fun getBaselineCoberturaCoveragePath(classFilter: ClassFilter): Path = when (classFilter) {
        ClassFilter.CLASSES -> getBaselineCoberturaClassCoveragePath()
        ClassFilter.TESTS -> getBaselineCoberturaTestCoveragePath()
        ClassFilter.ALL -> getBaselineCoberturaCoveragePath()
    }

    /**
     * Path to the file containing the XML of Jacoco coverage executed on the baseline.
     */
    fun getBaselineJacocoCoveragePath(): Path = outputDir.resolve(BASELINE_JACOCO_COVERAGE_FILE)

    /**
     * Path to the file containing the XML of Jacoco class coverage executed on the baseline.
     */
    fun getBaselineJacocoClassCoveragePath(): Path = outputDir.resolve(BASELINE_JACOCO_CLASS_COVERAGE_FILE)

    /**
     * Path to the file containing the XML of Jacoco test coverage executed on the baseline.
     */
    fun getBaselineJacocoTestCoveragePath(): Path = outputDir.resolve(BASELINE_JACOCO_TEST_COVERAGE_FILE)

    fun getBaselineJacocoCoveragePath(classFilter: ClassFilter): Path = when (classFilter) {
        ClassFilter.CLASSES -> getBaselineJacocoClassCoveragePath()
        ClassFilter.TESTS -> getBaselineJacocoTestCoveragePath()
        ClassFilter.ALL -> getBaselineJacocoCoveragePath()
    }

    /**
     * Path to file containing all retained classes after reduction by the baseline algorithm.
     */
    fun getGroundTruthClassesPath(sourceRoot: SourceRootOutput, isDebug: Boolean): Path =
        outputDir.resolve(getGroundTruthClassesRelPath(sourceRoot, isDebug))

    /**
     * Path to file containing all retained methods after reduction by the baseline algorithm.
     */
    fun getGroundTruthMethodsPath(sourceRoot: SourceRootOutput, isDebug: Boolean): Path =
        outputDir.resolve(getGroundTruthMethodsRelPath(sourceRoot, isDebug))

    /**
     * Path to the file containing the result of compiling the reduced baseline.
     */
    fun getGroundTruthCompilePath(): Path = outputDir.resolve(GROUND_TRUTH_COMPILE_PATH)

    /**
     * Path to the file containing the result of running tests on the reduced baseline.
     */
    fun getGroundTruthTestPath(): Path = outputDir.resolve(GROUND_TRUTH_TEST_PATH)

    /**
     * Path to the file containing the failing tests after running the reduced baseline.
     */
    fun getGroundTruthFailingTestsPath(): Path = outputDir.resolve(GROUND_TRUTH_FAILING_TESTS_PATH)

    /**
     * Path to directory containing the minimal set of sources required to compile this test case, derived from the
     * baseline.
     */
    fun getGroundTruthSourcesPath(): Path = outputDir.resolve(getCompressedDir { GROUND_TRUTH_SOURCES_PATH })

    /**
     * Path to file containing all classes in the [source root][sourceRoot] of this bug revision.
     */
    fun getAllClassesInSourceRootPath(sourceRoot: SourceRootOutput): Path =
        baseDir.resolve(getSourceRootClassesRelPath(sourceRoot))

    /**
     * Path to file containing all methods in the [source root][sourceRoot] of this bug revision.
     */
    fun getAllMethodsInSourceRootPath(sourceRoot: SourceRootOutput): Path =
        baseDir.resolve(getSourceRootMethodsRelPath(sourceRoot))

    /**
     * Path to file containing the compile classpath used to compile this bug revision.
     */
    fun getCompileClasspathPath(): Path = baseDir.resolve(COMPILE_CLASSPATH_REL_PATH)

    /**
     * Path to file containing the test classpath used to compile this bug revision.
     */
    fun getTestClasspathPath(): Path = baseDir.resolve(TEST_CLASSPATH_REL_PATH)

    /**
     * Path to file containing the source-based classpath used to compile this bug revision.
     */
    fun getSrcClasspathPath(): Path = baseDir.resolve(SRC_CLASSPATH_REL_PATH)

    /**
     * Path to file containing all triggering tests for this bug revision.
     */
    fun getTriggeringTestsPath(): Path = baseDir.resolve(ENTRYPOINTS_REL_PATH)

    /**
     * Path to file containing all expected failing tests for this bug revision.
     */
    fun getExpectedFailingTestsPath(): Path = baseDir.resolve(EXPECTED_FAILING_TESTS_REL_PATH)

    /**
     * Path to file containing all relevant test classes for this bug revision.
     */
    fun getRelevantTestsPath(): Path = baseDir.resolve(RELEVANT_TEST_CLASSES_REL_PATH)

    /**
     * Path to file containing all test classes for this bug revision.
     */
    fun getAllTestsPath(): Path = baseDir.resolve(ALL_TEST_CLASSES_REL_PATH)

    /**
     * Path to file containing the source root to library classes.
     */
    fun getClassesSourceRootPath(): Path = baseDir.resolve(CLASSES_SOURCE_ROOT_REL_PATH)

    /**
     * Path to file containing the source root to library tests.
     */
    fun getTestSourceRootPath(): Path = baseDir.resolve(TEST_SOURCE_ROOT_REL_PATH)

    /**
     * Path to file containing all source roots for this bug revision.
     */
    fun getSourceRootsPath(): Path = baseDir.resolve(SOURCE_ROOTS_REL_PATH)

    /**
     * Returns the path to the file storing the default compilation result.
     */
    fun getDefaultCompilePath(): Path = baseDir.resolve(DEFAULT_COMPILE_REL_PATH)

    /**
     * Reads the Cobertura coverage data for the path represented by [pathGetter].
     */
    fun readCoberturaCoverageData(pathGetter: ToolOutputDirectory.() -> Path): CoberturaXML? {
        val inStream = when {
            getCompressedFile(pathGetter).isRegularFile() -> {
                FileCompressionUtils.decompressAsStream(getCompressedFile(pathGetter))
            }
            this.pathGetter().isRegularFile() -> {
                this.pathGetter().inputStream()
            }
            else -> null
        }

        return inStream
            ?.use { SAXReader.createDefault().read(it) }
            ?.let { CoberturaXML.fromDocument(it) }
    }

    /**
     * Reads the full Cobertura baseline coverage data.
     *
     * Shortcut of [readCoberturaCoverageData] on [ToolOutputDirectory.getBaselineCoveragePath].
     */
    fun readCoberturaBaselineCoverage(): CoberturaXML? =
        readCoberturaCoverageData(ToolOutputDirectory::getBaselineCoberturaCoveragePath)

    /**
     * Reads the Jacoco coverage data for the path represented by [pathGetter].
     */
    fun readJacocoCoverageData(pathGetter: ToolOutputDirectory.() -> Path): JacocoXML? {
        val inStream = when {
            getCompressedFile(pathGetter).isRegularFile() -> {
                FileCompressionUtils.decompressAsStream(getCompressedFile(pathGetter))
            }
            this.pathGetter().isRegularFile() -> {
                this.pathGetter().inputStream()
            }
            else -> null
        }

        return inStream
            ?.use { SAXReader.createDefault().read(it) }
            ?.let { JacocoXML.fromDocument(it) }
    }

    /**
     * Reads the full Jacoco baseline coverage data.
     *
     * Shortcut of [readJacocoCoverageData] on [ToolOutputDirectory.getBaselineJacocoCoveragePath].
     */
    fun readJacocoBaselineCoverage(): JacocoXML? =
        readJacocoCoverageData(ToolOutputDirectory::getBaselineJacocoCoveragePath)

    /**
     * Reads all coverage data as a [CoverageData] instance.
     */
    fun readCoverageData(): CoverageData {
        val jacocoCov = readJacocoBaselineCoverage()

        return readCoberturaBaselineCoverage()
            ?.let { CoverageData.Full(it, jacocoCov) }
            ?: run {
                val classCoverage = readCoberturaCoverageData(ToolOutputDirectory::getBaselineCoberturaClassCoveragePath)
                val testCoverage = readCoberturaCoverageData(ToolOutputDirectory::getBaselineCoberturaTestCoveragePath)

                val loadedClasses = LoadedClasses.fromBaseline(this)

                val classCoverageSource = classCoverage?.let { CoverageSource.CoberturaCoverage(it) }
                    ?: CoverageSource.ClassLoader(loadedClasses.sourceClasses)
                val testCoverageSource = testCoverage?.let { CoverageSource.CoberturaCoverage(it) }
                    ?: CoverageSource.ClassLoader(loadedClasses.testClasses)
                val jacocoCoverageSource = jacocoCov?.let { CoverageSource.JacocoCoverage(it) }

                CoverageData.PartialMix(classCoverageSource, testCoverageSource, jacocoCoverageSource)
            }
    }

    fun createBaselineDir() {
        outputDir.resolve(CLASS_REDUCER_REL_PATH).createDirectories()
    }

    fun createReductionPassDir(passNum: UInt) {
        outputDir.resolve(getReductionPassDir(passNum)).createDirectories()
    }

    @OptIn(ExperimentalPathApi::class)
    fun deleteLastPassDir() {
        val lastPass = getLastPass() ?: return

        outputDir.resolve(getReductionPassDir(lastPass)).deleteRecursively()
    }

    companion object {

        private fun getReductionPassDir(passNum: UInt): Path = Path("pass${passNum}")

        private fun getSourceRootClassesRelPath(sourceRoot: SourceRootOutput): Path =
            Path("classes.${sourceRoot.fileComponent}.txt")
        private fun getSourceRootMethodsRelPath(sourceRoot: SourceRootOutput): Path =
            Path("methods.${sourceRoot.fileComponent}.txt")

        private fun getGroundTruthClassesRelPath(sourceRoot: SourceRootOutput, isDebug: Boolean): Path =
            buildString {
                append("classes.compile1.")
                append(sourceRoot.fileComponent)
                if (isDebug) append(".debug")
                append(".txt")
            }.let { Path(it) }
        private fun getGroundTruthMethodsRelPath(sourceRoot: SourceRootOutput, isDebug: Boolean): Path =
            buildString {
                append("methods.compile1.")
                append(sourceRoot.fileComponent)
                if (isDebug) append(".debug")
                append(".txt")
            }.let { Path(it) }

        private fun getReducedClassesRelPath(passNum: UInt, sourceRoot: SourceRootOutput, isDebug: Boolean): Path =
            buildString {
                append("classes.reduced.")
                append(sourceRoot.fileComponent)
                if (isDebug) append(".debug")
                append(".txt")
            }.let { getReductionPassDir(passNum).resolve(it) }
        private fun getReducedMethodsRelPath(passNum: UInt, sourceRoot: SourceRootOutput, isDebug: Boolean): Path =
            buildString {
                append("methods.reduced.")
                append(sourceRoot.fileComponent)
                if (isDebug) append(".debug")
                append(".txt")
            }.let { getReductionPassDir(passNum).resolve(it) }

        private fun getReducedSourcesRelPath(passNum: UInt): Path =
            getReductionPassDir(passNum).resolve("reduced-sources")
        private fun getClassDiffRelPath(passNum: UInt, sourceRoot: SourceRootOutput): Path =
            getReductionPassDir(passNum).resolve("diff.class.${sourceRoot.fileComponent}.txt")
        private fun getMethodDiffRelPath(passNum: UInt, sourceRoot: SourceRootOutput): Path =
            getReductionPassDir(passNum).resolve("diff.method.${sourceRoot.fileComponent}.txt")
        private fun getClassStatRelPath(passNum: UInt, sourceRoot: SourceRootOutput): Path =
            getReductionPassDir(passNum).resolve("stat.class.${sourceRoot.fileComponent}.txt")
        private fun getMethodStatRelPath(passNum: UInt, sourceRoot: SourceRootOutput): Path =
            getReductionPassDir(passNum).resolve("stat.method.${sourceRoot.fileComponent}.txt")
        private fun getRunningTimeRelPath(passNum: UInt): Path =
            getReductionPassDir(passNum).resolve(RUNNING_TIME_REL_PATH)
        private fun getReducedCompileRelPath(passNum: UInt): Path =
            getReductionPassDir(passNum).resolve("compile.reduced.txt")
        private fun getReducedTestRelPath(passNum: UInt): Path =
            getReductionPassDir(passNum).resolve("test.reduced.txt")
        private fun getReducedFailingTestsRelPath(passNum: UInt): Path =
            getReductionPassDir(passNum).resolve("failing_tests.reduced.txt")

        private val CLASS_REDUCER_REL_PATH = Path("class-reducer")

        private fun getBaselineClassesRelPath(sourceRoot: SourceRootOutput, isDebug: Boolean): Path =
            buildString {
                append("classes.reduced.")
                append(sourceRoot.fileComponent)
                if (isDebug) append(".debug")
                append(".txt")
            }.let { CLASS_REDUCER_REL_PATH.resolve(it) }
        private fun getBaselineMethodsRelPath(sourceRoot: SourceRootOutput, isDebug: Boolean): Path =
            buildString {
                append("methods.reduced.")
                append(sourceRoot.fileComponent)
                if (isDebug) append(".debug")
                append(".txt")
            }.let { CLASS_REDUCER_REL_PATH.resolve(it) }

        private fun getBaselineSourcesRelPath(): Path =
            CLASS_REDUCER_REL_PATH.resolve("reduced-sources")
        private fun getBaselineClassDiffRelPath(sourceRoot: SourceRootOutput): Path =
            CLASS_REDUCER_REL_PATH.resolve("diff.class.${sourceRoot.fileComponent}.txt")
        private fun getBaselineMethodDiffRelPath(sourceRoot: SourceRootOutput): Path =
            CLASS_REDUCER_REL_PATH.resolve("diff.method.${sourceRoot.fileComponent}.txt")
        private fun getBaselineClassStatRelPath(sourceRoot: SourceRootOutput): Path =
            CLASS_REDUCER_REL_PATH.resolve("stat.class.${sourceRoot.fileComponent}.txt")
        private fun getBaselineMethodStatRelPath(sourceRoot: SourceRootOutput): Path =
            CLASS_REDUCER_REL_PATH.resolve("stat.method.${sourceRoot.fileComponent}.txt")
        private fun getBaselineRunningTimeRelPath(): Path =
            CLASS_REDUCER_REL_PATH.resolve(RUNNING_TIME_REL_PATH)
        private fun getBaselineCompileRelPath(): Path =
            CLASS_REDUCER_REL_PATH.resolve("compile.reduced.txt")
        private fun getBaselineTestRelPath(): Path =
            CLASS_REDUCER_REL_PATH.resolve("test.reduced.txt")
        private fun getBaselineFailingTestsRelPath(): Path =
            CLASS_REDUCER_REL_PATH.resolve("failing_tests.reduced.txt")

        private val COMPILE_CLASSPATH_REL_PATH = Path("cp.compile.txt")
        private val TEST_CLASSPATH_REL_PATH = Path("cp.txt")
        private val SRC_CLASSPATH_REL_PATH = Path("scp.txt")
        private val ENTRYPOINTS_REL_PATH = Path("entrypoints.txt")
        private val EXPECTED_FAILING_TESTS_REL_PATH = Path("failing-tests.expected.txt")
        private val CLASSES_SOURCE_ROOT_REL_PATH = Path("source-root.classes.txt")
        private val TEST_SOURCE_ROOT_REL_PATH = Path("source-root.test.txt")
        private val SOURCE_ROOTS_REL_PATH = Path("source-roots.txt")
        private val DEFAULT_COMPILE_REL_PATH = Path("compile0.txt")
        private val RUNNING_TIME_REL_PATH = Path("running-time.txt")
        private val EXPECTED_SOURCE_CLASSES_REL_PATH = Path("src-classes.expected.txt")
        private val EXPECTED_TEST_CLASSES_REL_PATH = Path("test-classes.expected.txt")
        private val ALL_TEST_CLASSES_REL_PATH = Path("test-classes.all.txt")
        private val RELEVANT_TEST_CLASSES_REL_PATH = Path("test-classes.relevant.txt")
        private val BASELINE_COBERTURA_COVERAGE_FILE = Path("coverage.compile0.xml")
        private val BASELINE_COBERTURA_CLASS_COVERAGE_FILE = Path("coverage.compile0.class.xml")
        private val BASELINE_COBERTURA_TEST_COVERAGE_FILE = Path("coverage.compile0.test.xml")
        private val BASELINE_JACOCO_COVERAGE_FILE = Path("coverage.compile0.jacoco.xml")
        private val BASELINE_JACOCO_CLASS_COVERAGE_FILE = Path("coverage.compile0.jacoco.class.xml")
        private val BASELINE_JACOCO_TEST_COVERAGE_FILE = Path("coverage.compile0.jacoco.test.xml")
        private val GROUND_TRUTH_SOURCES_PATH = Path("compile1-sources")
        private val GROUND_TRUTH_COMPILE_PATH = Path("compile.compile1.txt")
        private val GROUND_TRUTH_TEST_PATH = Path("test.compile1.txt")
        private val GROUND_TRUTH_FAILING_TESTS_PATH = Path("failing_tests.compile1.txt")
    }
}