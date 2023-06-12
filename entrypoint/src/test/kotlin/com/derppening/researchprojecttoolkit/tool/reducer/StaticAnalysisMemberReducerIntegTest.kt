package com.derppening.researchprojecttoolkit.tool.reducer

abstract class StaticAnalysisMemberReducerIntegTest : ClassDepReducerIntegTest<StaticAnalysisMemberReducer>() {

    abstract inner class OnTriggeringTests(protected val enableAssertions: Boolean) : TriggeringTestsLevel() {

        override val reducerCreator = { project.getStaticAnalysisMemberReducer(enableAssertions, null) }
    }

    abstract inner class OnTestCase(
        entrypoint: String,
        protected val enableAssertions: Boolean
    ) : TestCaseLevel(entrypoint) {

        override val reducerCreator = { project.getStaticAnalysisMemberReducer(entrypointSpec, enableAssertions, null) }
    }
}