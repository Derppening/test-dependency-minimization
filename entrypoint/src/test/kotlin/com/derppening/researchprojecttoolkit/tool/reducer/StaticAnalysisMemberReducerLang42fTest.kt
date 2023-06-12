package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.tool.compilation.CompilerProxy
import org.junit.jupiter.api.Nested
import java.nio.file.Path
import kotlin.jvm.optionals.getOrDefault
import kotlin.test.Test
import kotlin.test.assertTrue

class StaticAnalysisMemberReducerLang42fTest : StaticAnalysisMemberReducerIntegTest() {

    override val project = TestProjects.Lang42f

    @Nested
    inner class StringEscapeUtilsTest {

        @Nested
        inner class TestEscapeHtmlHighUnicode : OnTestCase("org.apache.commons.lang.StringEscapeUtilsTest::testEscapeHtmlHighUnicode", true) {

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
                    emptyList(),
                    JAVA_8_HOME
                )
                assertCompileSuccess(compiler)

                assertTestSuccess(outputDir!!.path, JAVA_8_HOME) {
                    add("-cp")
                    add("${project.testCpJars.joinToString(":")}:${sourceRoots.joinToString(":")}")

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

            @Test
            fun `Regression-00`() {
                val classes = reducer.getAndDumpAllTopLevelClasses()

                assertTrue {
                    classes.singleOrNull { clazz ->
                        clazz.fullyQualifiedName
                            .map { it == "org.apache.commons.lang.enum.Broken1OperationEnum" }
                            .getOrDefault(false)
                    } != null
                }

                assertTrue {
                    classes.singleOrNull { clazz ->
                        clazz.fullyQualifiedName
                            .map { it == "org.apache.commons.lang.enums.Broken1OperationEnum" }
                            .getOrDefault(false)
                    } != null
                }
            }
        }
    }
}