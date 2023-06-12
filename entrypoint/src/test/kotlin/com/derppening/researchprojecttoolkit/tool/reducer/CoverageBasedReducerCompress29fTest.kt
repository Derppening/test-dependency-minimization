package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.tool.compilation.CompilerProxy
import org.junit.jupiter.api.Nested
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.test.Test

class CoverageBasedReducerCompress29fTest : CoverageBasedReducerIntegTest() {

    override val project = TestProjects.Compress29f

    @Nested
    inner class ArchiveStreamFactoryTest {

        @Nested
        inner class TestEncodingInputStreamAutodetect {

            private val entrypoint = "org.apache.commons.compress.archivers.ArchiveStreamFactoryTest::testEncodingInputStreamAutodetect"

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

                    val resDir = Path("src/test/resources")
                    arrayOf(
                        "bla.arj",
                        "bla.cpio",
                        "bla.dump",
                        "bla.jar",
                        "bla.tar",
                        "bla.zip",
                    ).forEach {
                        project.copyProjectFile(resDir.resolve(it), outputDir!!.path)
                    }

                    assertTestSuccess(outputDir!!.path, JAVA_17_HOME) {
                        add("-cp")
                        add("${project.testCpJars.joinToString(":")}:${sourceRoots.joinToString(":")}:${outputDir!!.path.resolve(resDir)}")

                        add("-m")
                        add(testCase.toJUnitMethod())
                    }
                }

                @Nested
                inner class ReductionRegressionTests {
                }
            }
        }
    }
}