package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.tool.compilation.CompilerProxy
import com.derppening.researchprojecttoolkit.tool.transform.NodeTransformDecision
import com.derppening.researchprojecttoolkit.tool.transform.TagStaticAnalysisMemberUnusedDecls.Companion.decideForConstructor
import org.junit.jupiter.api.Nested
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StaticAnalysisMemberReducerJacksonDatabind5fTest : StaticAnalysisMemberReducerIntegTest() {

    override val project = TestProjects.JacksonDatabind5f

    @Nested
    inner class TestMixinMerging {

        @Nested
        inner class TestDisappearingMixins515 : OnTestCase("com.fasterxml.jackson.databind.introspect.TestMixinMerging::testDisappearingMixins515", true) {

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

                val junitRunner = tryTestNoAssertion(outputDir!!.path, JAVA_8_HOME) {
                    add("-cp")
                    add("${project.testCpJars.joinToString(":")}:${sourceRoots.joinToString(":")}")

                    add("-m")
                    add(testCase.toJUnitMethod())
                }
                assertTrue(junitRunner.exitCode.isFailure)
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
                tryCompileNoAssertion(compiler)
                assertFalse(compiler.exitCode.isSuccess)
            }

            /**
             * ```
             *     => java.lang.AssertionError: This method should not be reached! Signature: ClassIntrospector()
             *        com.fasterxml.jackson.databind.introspect.ClassIntrospector.<init>(ClassIntrospector.java:38)
             *        com.fasterxml.jackson.databind.introspect.BasicClassIntrospector.<init>(BasicClassIntrospector.java:56)
             *        com.fasterxml.jackson.databind.introspect.BasicClassIntrospector.<clinit>(BasicClassIntrospector.java:54)
             *        com.fasterxml.jackson.databind.ObjectMapper.<clinit>(ObjectMapper.java:78)
             *        com.fasterxml.jackson.databind.BaseMapTest.<clinit>(BaseMapTest.java:24)
             * ```
             */
            @Test
            fun `Regression-00`() {
                val srcCU = reducer.context
                    .getCUByPrimaryTypeName("com.fasterxml.jackson.databind.introspect.BasicClassIntrospector")
                assumeTrue(srcCU != null)
                val srcType = srcCU.primaryType.get()
                val srcCtor = srcType.defaultConstructor.get()
                assumeTrue {
                    decideForConstructor(
                        reducer.context,
                        srcCtor,
                        enableAssertions,
                        noCache = true
                    ) == NodeTransformDecision.NO_OP
                }

                val tgtCU = reducer.context
                    .getCUByPrimaryTypeName("com.fasterxml.jackson.databind.introspect.ClassIntrospector")
                assumeTrue(tgtCU != null)
                val tgtType = tgtCU.primaryType.get()
                val tgtCtor = tgtType.defaultConstructor.get()

                val ctorDecision = decideForConstructor(
                    reducer.context,
                    tgtCtor,
                    enableAssertions,
                    noCache = true
                )
                assertEquals(NodeTransformDecision.NO_OP, ctorDecision)
            }
        }
    }
}