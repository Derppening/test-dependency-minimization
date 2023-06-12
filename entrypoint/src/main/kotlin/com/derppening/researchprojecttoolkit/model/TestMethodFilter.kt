package com.derppening.researchprojecttoolkit.model

import com.derppening.researchprojecttoolkit.defects4j.TestCaseCollector
import com.derppening.researchprojecttoolkit.util.Defects4JWorkspace
import com.derppening.researchprojecttoolkit.util.Logger
import com.derppening.researchprojecttoolkit.util.TestCase
import com.derppening.researchprojecttoolkit.util.TestClass
import hk.ust.cse.castle.toolkit.jvm.jsl.PredicatedFileCollector
import java.io.File
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile

/**
 * Types of test method filter.
 */
sealed class TestMethodFilter {

    /**
     * Command-line options which are used to enable this filter.
     */
    abstract val cmdlineOpt: List<String>

    protected fun getAllTestCases(
        d4jWorkspace: Defects4JWorkspace,
        sourceRoots: List<Path>,
        projectTestRoot: Path,
        classpath: String
    ): Set<TestCase> {
        val symbolSolver = checkNotNull(d4jWorkspace.cachedCheckedOutRevision)
            .getSymbolSolver(d4jWorkspace)

        val allTestClasses = d4jWorkspace.export(Defects4JWorkspace.Property.TESTS_ALL, true)
            .takeIf { it.isNotEmpty() }
            ?.split('\n')
            ?.map { it.trim() }
            ?.map { TestClass(it) }
            ?: run {
                LOGGER.warn("Unable to obtain all test classes using Defects4J - Fallback to searching in test sources root")

                val projectTestRootAbs = d4jWorkspace.workspace.resolve(projectTestRoot)

                PredicatedFileCollector(projectTestRootAbs)
                    .collect { it.extension == "java" }
                    .filter {
                        TestCaseCollector(it, sourceRoots, classpath, symbolSolver).collect().isNotEmpty()
                    }
                    .map {
                        "${projectTestRootAbs.relativize(it)}".removeSuffix(".java").replace(File.separatorChar, '.')
                    }
                    .map { TestClass(it) }
            }

        return allTestClasses
            .mapNotNull { testClassName ->
                val testClassRelPath = testClassName.pathRelativeToSourceRoot

                sourceRoots
                    .map { it.resolve(testClassRelPath) }
                    .singleOrNull { it.isRegularFile() }
            }
            .flatMap { TestCaseCollector(it, sourceRoots, classpath, symbolSolver).collect() }
            .toSet()
    }

    abstract fun getFilteredTestCases(
        d4jWorkspace: Defects4JWorkspace,
        projectTestRoot: Path,
        sourceRoots: List<Path>,
        classpath: String,
        triggeringTests: Set<TestCase>
    ): Set<TestCase>

    fun getFilteredTestCases(
        d4jWorkspace: Defects4JWorkspace,
        projectRev: Defects4JWorkspace.ProjectRev,
        ignoreBaseline: Boolean = false
    ): Set<TestCase> {
        val projectSrcRoot: Path = d4jWorkspace.workspace.resolve(
            d4jWorkspace.export(Defects4JWorkspace.Property.DIR_SRC_CLASSES)
        )
        val projectTestRoot: Path = d4jWorkspace.workspace.resolve(
            d4jWorkspace.export(Defects4JWorkspace.Property.DIR_SRC_TESTS)
        )
        val sourceRoots = listOf(projectSrcRoot, projectTestRoot)
        val classpath = d4jWorkspace.getTestClasspath(projectRev, ignoreBaseline)
        val triggeringTests = d4jWorkspace.triggeringTests

        return getFilteredTestCases(d4jWorkspace, projectTestRoot, sourceRoots, classpath, triggeringTests)
    }

    /**
     * Marker interface indicating that a [TestMethodFilter] can be nested within another filter.
     */
    interface Nestable

    /**
     * Filter which only enables test classes containing relevant tests (i.e. tests which loads any bugfix-modified
     * class) to this bug.
     */
    object RelevantTestClasses : TestMethodFilter(), Nestable {

        override val cmdlineOpt = listOf("relevant", "relevant-class", "relevant-classes")

        override fun getFilteredTestCases(
            d4jWorkspace: Defects4JWorkspace,
            projectTestRoot: Path,
            sourceRoots: List<Path>,
            classpath: String,
            triggeringTests: Set<TestCase>
        ): Set<TestCase> {
            val relevantTestClasses = d4jWorkspace.export(Defects4JWorkspace.Property.TESTS_RELEVANT)
                .split('\n')
                .filter { it.isNotBlank() }
                .map { it.trim() }
                .map { TestClass(it) }
                .toSet()

            return getAllTestCases(d4jWorkspace, sourceRoots, projectTestRoot, classpath)
                .filter { it.testClass in relevantTestClasses }
                .toSet()
        }
    }

    /**
     * Filter which only enables test cases which triggers this bug.
     */
    object FailingTestCases : TestMethodFilter(), Nestable {

        override val cmdlineOpt = listOf("failing-test", "failing-tests")

        override fun getFilteredTestCases(
            d4jWorkspace: Defects4JWorkspace,
            projectTestRoot: Path,
            sourceRoots: List<Path>,
            classpath: String,
            triggeringTests: Set<TestCase>
        ): Set<TestCase> = triggeringTests
    }

    /**
     * Filter which only enables test classes which includes tests triggering this bug.
     */
    object FailingTestClasses : TestMethodFilter(), Nestable {

        override val cmdlineOpt = listOf("failing-class", "failing-classes")

        override fun getFilteredTestCases(
            d4jWorkspace: Defects4JWorkspace,
            projectTestRoot: Path,
            sourceRoots: List<Path>,
            classpath: String,
            triggeringTests: Set<TestCase>
        ): Set<TestCase> {
            val failingTestClasses = triggeringTests.map { it.testClass }.toSet()

            return getAllTestCases(d4jWorkspace, sourceRoots, projectTestRoot, classpath)
                .filter { it.testClass in failingTestClasses }
                .toSet()
        }
    }

    /**
     * Filter which enables all test cases.
     */
    object All : TestMethodFilter() {

        override val cmdlineOpt = listOf("all")

        override fun getFilteredTestCases(
            d4jWorkspace: Defects4JWorkspace,
            projectTestRoot: Path,
            sourceRoots: List<Path>,
            classpath: String,
            triggeringTests: Set<TestCase>
        ): Set<TestCase> {
            return getAllTestCases(d4jWorkspace, sourceRoots, projectTestRoot, classpath)
        }
    }

    /**
     * Filter which enables test cases which matches the given [regex].
     */
    data class ByRegex(val regex: Regex) : TestMethodFilter(), Nestable {

        override val cmdlineOpt = listOf(regex.toString())

        override fun getFilteredTestCases(
            d4jWorkspace: Defects4JWorkspace,
            projectTestRoot: Path,
            sourceRoots: List<Path>,
            classpath: String,
            triggeringTests: Set<TestCase>
        ): Set<TestCase> {
            return getAllTestCases(d4jWorkspace, sourceRoots, projectTestRoot, classpath)
                .filter { "$it".matches(regex) }
                .toSet()
        }
    }

    /**
     * Filter which enables test cases matching the [inner] filter and can be compiled using the ground truth.
     */
    data class Golden(val inner: TestMethodFilter) : TestMethodFilter() {

        init {
            check(inner is Nestable)
        }

        override val cmdlineOpt = listOf("golden:${inner.cmdlineOpt.first()}")

        override fun getFilteredTestCases(
            d4jWorkspace: Defects4JWorkspace,
            projectTestRoot: Path,
            sourceRoots: List<Path>,
            classpath: String,
            triggeringTests: Set<TestCase>
        ): Set<TestCase> = inner.getFilteredTestCases(d4jWorkspace, projectTestRoot, sourceRoots, classpath, triggeringTests)
    }

    companion object {

        private val LOGGER = Logger<TestMethodFilter>()

        /**
         * Parses the string as an instance of [TestMethodFilter].
         */
        fun parseString(str: String): TestMethodFilter {
            return when {
                str in RelevantTestClasses.cmdlineOpt -> RelevantTestClasses
                str in FailingTestCases.cmdlineOpt -> FailingTestCases
                str in FailingTestClasses.cmdlineOpt -> FailingTestClasses
                str == "all" -> All
                str.startsWith("golden:") -> Golden(parseString(str.removePrefix("golden:")))
                else -> ByRegex(str.replace("*", ".*").toRegex())
            }
        }
    }
}