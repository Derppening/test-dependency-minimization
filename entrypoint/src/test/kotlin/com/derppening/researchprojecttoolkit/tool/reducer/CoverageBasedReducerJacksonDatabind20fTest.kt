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

class CoverageBasedReducerJacksonDatabind20fTest : CoverageBasedReducerIntegTest() {

    override val project = TestProjects.JacksonDatabind20f

    @Nested
    inner class TestNamingStrategyStd {

        @Nested
        inner class TestNamingWithObjectNode {

            private val entrypoint = "com.fasterxml.jackson.databind.introspect.TestNamingStrategyStd::testNamingWithObjectNode"

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

                    val srcRootOutputDirs = project.cpSourceRoots.map {
                        val srcRootOutputDir = outputDir!!.path.resolve(it.fileName)

                        it.toFile().copyRecursively(srcRootOutputDir.toFile())

                        srcRootOutputDir
                    }

                    val sourceRoots = sourceRootMapping.values.toList() + srcRootOutputDirs
                    val compiler = CompilerProxy(
                        sourceRoots,
                        project.testCpJars.joinToString(":"),
                        listOf("-Xmaxerrs", "$JAVAC_MAXERRS"),
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

                    @Test
                    fun `Do not remove entrypoints`() {
                        val cu = reducer.context
                            .getCUByPrimaryTypeName("com.fasterxml.jackson.databind.introspect.TestNamingStrategyStd")
                        assumeTrue(cu != null)

                        val method = cu.primaryType.get()
                            .getMethodsBySignature("testNamingWithObjectNode").single()

                        val methodDecision = decideForMethod(
                            reducer.context,
                            method,
                            enableAssertions,
                            noCache = true
                        )

                        assertEquals(NodeTransformDecision.NO_OP, methodDecision)
                        assertFalse(method.isUnusedForRemovalData)
                    }
                }

                @Nested
                inner class SymbolLookupTests {
                }
            }
        }
    }
}