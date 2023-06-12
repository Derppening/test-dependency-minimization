package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.tool.JAVA_17_HOME
import com.derppening.researchprojecttoolkit.tool.TestProjects
import com.derppening.researchprojecttoolkit.tool.assertCompileSuccess
import com.derppening.researchprojecttoolkit.tool.compilation.CompilerProxy
import com.derppening.researchprojecttoolkit.tool.tryTestNoAssertion
import org.junit.jupiter.api.Nested
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse

class CoverageBasedReducerCompress22fTest : CoverageBasedReducerIntegTest() {

    override val project = TestProjects.Compress22f

    @Nested
    inner class PythonTruncatedBzip2Test {

        @Nested
        inner class TestPartialReadTruncatedData {

            private val entrypoint = "org.apache.commons.compress.compressors.bzip2.PythonTruncatedBzip2Test::testPartialReadTruncatedData"

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

                    val junitRunner = tryTestNoAssertion(outputDir!!.path, JAVA_17_HOME) {
                        add("-cp")
                        add("${project.testCpJars.joinToString(":")}:${sourceRoots.joinToString(":")}")

                        add("-m")
                        add(testCase.toJUnitMethod())
                    }
                    assertFalse(junitRunner.exitCode.isSuccess)
                }

                @Nested
                inner class ReductionRegressionTests {
                }
            }
        }
    }
}