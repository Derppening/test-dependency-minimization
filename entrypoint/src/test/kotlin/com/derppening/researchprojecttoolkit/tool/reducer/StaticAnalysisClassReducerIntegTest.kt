package com.derppening.researchprojecttoolkit.tool.reducer

abstract class StaticAnalysisClassReducerIntegTest : ClassDepReducerIntegTest<StaticAnalysisClassReducer>() {

    abstract inner class OnTriggeringTests(protected val enableAssertions: Boolean) : TriggeringTestsLevel() {

        override val reducerCreator = { project.getStaticAnalysisClassReducer(enableAssertions, null) }
    }

    abstract inner class OnTestCase(
        entrypoint: String,
        protected val enableAssertions: Boolean
    ) : TestCaseLevel(entrypoint) {

        override val reducerCreator = { project.getStaticAnalysisClassReducer(entrypointSpec, enableAssertions, null) }
    }
}