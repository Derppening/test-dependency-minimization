package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.GlobalConfiguration
import com.derppening.researchprojecttoolkit.model.EntrypointSpec
import com.derppening.researchprojecttoolkit.tool.TestProjects
import com.derppening.researchprojecttoolkit.util.TemporaryPath
import com.derppening.researchprojecttoolkit.util.TestCase
import com.derppening.researchprojecttoolkit.util.TestUnit
import com.derppening.researchprojecttoolkit.util.clearMemory
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

abstract class ClassDepReducerIntegTest<ReducerT : AbstractReducer> {

    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    abstract inner class TestLevel {

        protected abstract val reducerCreator: () -> ReducerT

        private var _reducer: AbstractReducer? = null

        @Suppress("UNCHECKED_CAST")
        protected val reducer: ReducerT get() = _reducer as ReducerT

        @BeforeAll
        fun setUpAll() {
            _reducer = reducerCreator()
            reducer.run()

            reducer.taggedCUs
        }

        @AfterAll
        fun tearDownAll() {
            _reducer = null

            clearMemory()
        }
    }

    abstract inner class TriggeringTestsLevel : TestLevel() {

        protected val testCases: List<TestCase>
            get() = project.entrypoints.map { TestUnit.fromEntrypointString(it) as TestCase }
        protected val entrypointSpecs: List<EntrypointSpec>
            get() = testCases.map { EntrypointSpec.fromArg(it, project.sourceRoots) }
    }

    abstract inner class TestCaseLevel(private val entrypoint: String) : TestLevel() {

        protected val testCase: TestCase
            get() = TestUnit.fromEntrypointString(entrypoint) as TestCase
        protected val entrypointSpec: EntrypointSpec
            get() = EntrypointSpec.fromArg(testCase, project.sourceRoots)
    }

    protected abstract val project: TestProjects.Project

    protected var outputDir: TemporaryPath? = null

    @BeforeTest
    fun setUp() {
        outputDir = TemporaryPath.createDirectory()
    }

    @AfterTest
    fun tearDown() {
        outputDir?.close()
        outputDir = null
    }

    companion object {

        @JvmStatic
        @BeforeAll
        fun setUpAll() {
            GlobalConfiguration()
        }
    }
}
