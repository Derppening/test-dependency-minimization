package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.tool.compilation.CompilerProxy
import com.derppening.researchprojecttoolkit.tool.transform.NodeTransformDecision
import com.derppening.researchprojecttoolkit.tool.transform.TagGroundTruthUnusedDecls.Companion.decideForMethod
import org.junit.jupiter.api.Nested
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoverageBasedReducerLang57fTest : CoverageBasedReducerIntegTest() {

    override val project = TestProjects.Lang57f

    @Nested
    inner class LocaleUtilsTest {

        @Nested
        inner class TestCountriesByLanguage : OnTestCase("org.apache.commons.lang.LocaleUtilsTest::testCountriesByLanguage", true) {

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
                tryCompileNoAssertion(compiler)
                assertFalse(compiler.exitCode.isSuccess)
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

            @Nested
            inner class ReductionRegressionTests {

                @Test
                fun `Exclude Transitively Methods included by Static-Only Scoped Methods`() {
                    val cu = reducer.context
                        .getCUByPrimaryTypeName("org.apache.commons.lang.math.AbstractRangeTest")
                    assumeTrue(cu != null)

                    val type = cu.primaryType.get()
                    val method = type.getMethodsBySignature("setUp").single()

                    val decision = decideForMethod(reducer.context, method, enableAssertions, noCache = true)
                    assertEquals(NodeTransformDecision.REMOVE, decision)

                    assertTrue(method.isUnusedForRemovalData)
                }
            }
        }
    }
}