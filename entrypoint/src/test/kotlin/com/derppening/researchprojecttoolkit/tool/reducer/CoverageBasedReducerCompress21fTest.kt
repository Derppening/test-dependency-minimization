package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.tool.compilation.CompilerProxy
import com.derppening.researchprojecttoolkit.tool.transform.TagGroundTruthUnusedDecls.Companion.isTypeReachable
import org.junit.jupiter.api.Nested
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class CoverageBasedReducerCompress21fTest : CoverageBasedReducerIntegTest() {

    override val project = TestProjects.Compress21f

    @Nested
    inner class SevenZOutputFileTest {

        @Nested
        inner class TestNineFilesSomeNotEmpty {

            private val entrypoint = "org.apache.commons.compress.archivers.sevenz.SevenZOutputFileTest::testNineFilesSomeNotEmpty"

            @Nested
            inner class WithAssertions : OnTestCase(entrypoint, true) {

                @Test
                fun testExecution() {
                    val sourceRootMapping = mutableMapOf<Path, Path>()
                    reducer.getTransformedCompilationUnits(
                        outputDir!!.path,
                        project.getProjectResourcePath(""),
                        sourceRootMapping = sourceRootMapping
                    )
                        .parallelStream()
                        .forEach { cu -> cu.storage.ifPresent { it.save() } }

                    val sourceRoots = sourceRootMapping.values
                    val compiler = CompilerProxy(
                        sourceRoots,
                        project.testCpJars.joinToString(":"),
                        emptyList(),
                        JAVA_17_HOME
                    )
                    assertCompileSuccess(compiler)

                    assertTestSuccess(outputDir!!.path, JAVA_17_HOME) {
                        add("-cp")
                        add("${project.testCpJars.joinToString(":")}:${sourceRoots.joinToString(":")}")

                        add("-m")
                        add(testCase.toJUnitMethod())
                    }
                }

                @Nested
                inner class ReductionRegressionTests {

                    @Test
                    fun `Regression-00`() {
                        val seqReducer = project.getCoverageBasedReducer(project.parseEntrypoint(entrypoint), enableAssertions, 1)
                            .also { reducer ->
                                reducer.run(1)
                                reducer.taggedCUs
                            }

                        val seqCU = seqReducer.context
                            .getCUByPrimaryTypeName("org.apache.commons.compress.archivers.ArchiveException")
                        assumeTrue(seqCU != null)
                        val parCU = reducer.context
                            .getCUByPrimaryTypeName("org.apache.commons.compress.archivers.ArchiveException")
                        assumeTrue(parCU != null)

                        val seqType = seqCU.primaryType.get()
                        val parType = parCU.primaryType.get()

                        val seqDecision = isTypeReachable(
                            seqReducer.context,
                            seqType,
                            enableAssertions,
                            noCache = true
                        )
                        val parDecision = isTypeReachable(
                            reducer.context,
                            parType,
                            enableAssertions,
                            noCache = true
                        )

                        assertEquals(seqDecision, parDecision)
                    }
                }
            }
        }
    }
}