package com.derppening.researchprojecttoolkit.tool.reducer

abstract class CoverageBasedReducerIntegTest : ClassDepReducerIntegTest<CoverageBasedReducer>() {

    abstract inner class OnTestCase(
        entrypoint: String,
        protected val enableAssertions: Boolean
    ) : TestCaseLevel(entrypoint) {

        override val reducerCreator = { project.getCoverageBasedReducer(entrypointSpec, enableAssertions, null) }
    }
}