package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.tool.compilation.CompilerProxy
import org.junit.jupiter.api.Nested
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.test.Test

class StaticAnalysisMemberReducerCompress41fTest : StaticAnalysisMemberReducerIntegTest() {

    override val project = TestProjects.Compress41f

    @Nested
    inner class ZipArchiveInputStreamTest {

        @Nested
        inner class TestThrowOnInvalidEntry : OnTestCase("org.apache.commons.compress.archivers.zip.ZipArchiveInputStreamTest::testThrowOnInvalidEntry", true) {

            @Test
            fun testExecutionJava8() {
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
                    listOf("-Xmaxerrs", "$JAVAC_MAXERRS"),
                    JAVA_8_HOME
                )

                assertCompileSuccess(compiler)

                val resDir = Path("src/test/resources")
                project.copyProjectFile(resDir.resolve("invalid-zip.zip"), outputDir!!.path)

                assertTestSuccess(outputDir!!.path, JAVA_8_HOME) {
                    add("-cp")
                    add("${project.testCpJars.joinToString(":")}:${sourceRoots.joinToString(":")}:${outputDir!!.path.resolve(resDir)}")

                    add("-m")
                    add(testCase.toJUnitMethod())
                }
            }

            @Test
            fun testExecutionJava17() {
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
                    listOf("-Xmaxerrs", "$JAVAC_MAXERRS"),
                    JAVA_17_HOME
                )

                assertCompileSuccess(compiler)

                val resDir = Path("src/test/resources")
                project.copyProjectFile(resDir.resolve("invalid-zip.zip"), outputDir!!.path)

                assertTestSuccess(outputDir!!.path, JAVA_17_HOME) {
                    add("-cp")
                    add("${project.testCpJars.joinToString(":")}:${sourceRoots.joinToString(":")}:${outputDir!!.path.resolve(resDir)}")

                    add("-m")
                    add(testCase.toJUnitMethod())
                }
            }
        }
    }
}