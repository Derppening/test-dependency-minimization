package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.GlobalConfiguration
import com.derppening.researchprojecttoolkit.model.EntrypointSpec
import com.derppening.researchprojecttoolkit.tool.TestProjects
import com.derppening.researchprojecttoolkit.util.clearMemory
import org.junit.jupiter.api.BeforeAll
import kotlin.test.AfterTest
import kotlin.test.Test

class StaticAnalysisMemberReducerTest {

    private fun testReductionCacheStability(
        project: TestProjects.Project,
        entrypoint: EntrypointSpec,
        nonFatal: Boolean = false
    ) {
        ClassDepReducerTestUtils.testReductionCacheStability(nonFatal) {
            project.getStaticAnalysisMemberReducer(entrypoint, enableAssertions = true, null)
        }
    }

    private fun testParallelReductionCorrectness(project: TestProjects.Project, entrypoint: EntrypointSpec) {
        ClassDepReducerTestUtils.testParallelReductionCorrectness {
            project.getStaticAnalysisMemberReducer(entrypoint, enableAssertions = true, it)
        }
    }

    @Test
    fun `Cli-13f - Parallel Reduction Correctness`() {
        val project = TestProjects.Cli13f
        val entrypoint = project.parseEntrypoint("org.apache.commons.cli2.bug.BugLoopingOptionLookAlikeTest::testLoopingOptionLookAlike2")

        testParallelReductionCorrectness(project, entrypoint)
    }

    @Test
    fun `Cli-13f - Reduction Cache Stability`() {
        val project = TestProjects.Cli13f
        val entrypoint = project.parseEntrypoint("org.apache.commons.cli2.bug.BugLoopingOptionLookAlikeTest::testLoopingOptionLookAlike2")

        testReductionCacheStability(project, entrypoint)
    }

    @Test
    fun `Mockito-17f - Parallel Reduction Correctness`() {
        val project = TestProjects.Mockito17f
        val entrypoint = project.parseEntrypoint("org.mockitousage.basicapi.MocksSerializationTest::shouldBeSerializeAndHaveExtraInterfaces")

        testParallelReductionCorrectness(project, entrypoint)
    }

    @Test
    fun `Mockito-17f - Reduction Cache Stability`() {
        val project = TestProjects.Mockito17f
        val entrypoint = project.parseEntrypoint("org.mockitousage.basicapi.MocksSerializationTest::shouldBeSerializeAndHaveExtraInterfaces")

        testReductionCacheStability(project, entrypoint, true)
    }

    @Test
    fun `Mockito-25f - Parallel Reduction Correctness`() {
        val project = TestProjects.Mockito25f
        val entrypoint = project.parseEntrypoint("org.mockito.internal.stubbing.defaultanswers.ReturnsGenericDeepStubsTest::will_return_default_value_on_non_mockable_nested_generic")

        testParallelReductionCorrectness(project, entrypoint)
    }

    @Test
    fun `Mockito-25f - Reduction Cache Stability`() {
        val project = TestProjects.Mockito25f
        val entrypoint = project.parseEntrypoint("org.mockito.internal.stubbing.defaultanswers.ReturnsGenericDeepStubsTest::will_return_default_value_on_non_mockable_nested_generic")

        testReductionCacheStability(project, entrypoint)
    }

    @AfterTest
    fun tearDown() {
        clearMemory()
    }

    companion object {

        @JvmStatic
        @BeforeAll
        fun setUpAll() {
            GlobalConfiguration()
        }
    }

}