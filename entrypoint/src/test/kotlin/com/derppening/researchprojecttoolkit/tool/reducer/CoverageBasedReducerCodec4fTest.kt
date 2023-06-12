package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.tool.compilation.CompilerProxy
import com.derppening.researchprojecttoolkit.tool.reachability.ReachableReason
import com.derppening.researchprojecttoolkit.tool.transform.NodeTransformDecision
import com.derppening.researchprojecttoolkit.tool.transform.TagGroundTruthUnusedDecls
import com.derppening.researchprojecttoolkit.tool.transform.TagGroundTruthUnusedDecls.Companion.decideForConstructor
import com.derppening.researchprojecttoolkit.util.synchronizedWith
import org.junit.jupiter.api.Nested
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class CoverageBasedReducerCodec4fTest : CoverageBasedReducerIntegTest() {

    override val project = TestProjects.Codec4f

    @Nested
    inner class Base64Codec13Test {

        @Nested
        inner class TestBinaryEncoder {

            private val entrypoint = "org.apache.commons.codec.binary.Base64Codec13Test::testBinaryEncoder"

            @Nested
            inner class WithAssertions : OnTestCase(entrypoint, true) {

                @Test
                fun testExecution() {
                    val sourceRootMapping = mutableMapOf<Path, Path>()
                    reducer.getTransformedCompilationUnits(outputDir!!.path, sourceRootMapping = sourceRootMapping)
                        .parallelStream()
                        .forEach { cu -> cu.storage.ifPresent { it.save() } }

                    val sourceRoots = sourceRootMapping.values
                    val compiler = CompilerProxy(
                        sourceRoots,
                        project.testCpJars.joinToString(":"),
                        emptyList()
                    )
                    assertCompileSuccess(compiler)

                    assertTestSuccess {
                        add("-cp")
                        add("${project.testCpJars.joinToString(":")}:${sourceRoots.joinToString(":")}")

                        add("-m")
                        add(testCase.toJUnitMethod())
                    }
                }

                @Nested
                inner class SymbolLookupTests {
                }

                @Nested
                inner class ReductionRegressionTests {

                    @Test
                    fun `Do not remove entrypoints`() {
                        val cu = reducer.context
                            .getCUByPrimaryTypeName("org.apache.commons.codec.binary.Base64Codec13Test")
                        assumeTrue(cu != null)

                        val method = cu.primaryType.get()
                            .getMethodsBySignature("testBinaryEncoder").single()

                        val methodDecision = TagGroundTruthUnusedDecls.decideForMethod(
                            reducer.context,
                            method,
                            enableAssertions,
                            noCache = true
                        )

                        assertEquals(NodeTransformDecision.NO_OP, methodDecision)
                        assertFalse(method.isUnusedForRemovalData)
                    }

                    @Test
                    fun `Check dependent constructors before determining reachability`() {
                        val cu = reducer.context
                            .getCUByPrimaryTypeName("org.apache.commons.codec.binary.Base64")
                        assumeTrue(cu != null)

                        val type = cu.primaryType.get()
                        val defaultCtor = type.defaultConstructor.get()

                        assumeTrue(defaultCtor.inclusionReasonsData.isNotEmpty())
                        assumeTrue {
                            defaultCtor.inclusionReasonsData.synchronizedWith {
                                any { it is ReachableReason.DirectlyReferencedByNode }
                            }
                        }

                        val defaultCtorDecision = decideForConstructor(
                            reducer.context,
                            defaultCtor,
                            enableAssertions,
                            noCache = true
                        )
                        assumeTrue(defaultCtorDecision == NodeTransformDecision.NO_OP)

                        val directlyDependentCtor = type
                            .getConstructorByParameterTypes("int").get()

                        val directlyDependentCtorResult = decideForConstructor(
                            reducer.context,
                            directlyDependentCtor,
                            enableAssertions,
                            noCache = true
                        )

                        assertNotEquals(NodeTransformDecision.REMOVE, directlyDependentCtorResult)
                    }
                }
            }
        }
    }
}