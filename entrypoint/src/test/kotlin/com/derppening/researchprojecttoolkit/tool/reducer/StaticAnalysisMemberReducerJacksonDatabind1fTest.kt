package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.tool.JAVA_17_HOME
import com.derppening.researchprojecttoolkit.tool.TestProjects
import com.derppening.researchprojecttoolkit.tool.assertCompileSuccess
import com.derppening.researchprojecttoolkit.tool.assertTestSuccess
import com.derppening.researchprojecttoolkit.tool.compilation.CompilerProxy
import org.junit.jupiter.api.Nested
import java.nio.file.Path
import kotlin.test.Test

class StaticAnalysisMemberReducerJacksonDatabind1fTest : StaticAnalysisMemberReducerIntegTest() {

    override val project = TestProjects.JacksonDatabind1f

    @Nested
    inner class TestPOJOAsArray {

        @Nested
        inner class TestNullColumn : OnTestCase("com.fasterxml.jackson.databind.struct.TestPOJOAsArray::testNullColumn", true) {

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
        }
    }
}