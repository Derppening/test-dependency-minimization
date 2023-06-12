package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.model.ReferenceTypeLikeDeclaration
import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.tool.compilation.CompilerProxy
import com.derppening.researchprojecttoolkit.tool.reducer.AbstractBaselineReducer.Companion.isUnusedInCoverage
import com.derppening.researchprojecttoolkit.tool.transform.NodeTransformDecision
import com.derppening.researchprojecttoolkit.tool.transform.TagGroundTruthUnusedDecls
import com.derppening.researchprojecttoolkit.tool.transform.TagGroundTruthUnusedDecls.Companion.isTypeReachable
import com.derppening.researchprojecttoolkit.util.synchronizedWith
import org.junit.jupiter.api.Nested
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoverageBasedReducerCompress28fTest : CoverageBasedReducerIntegTest() {

    override val project = TestProjects.Compress28f

    @Nested
    inner class TarArchiveInputStreamTest {

        @Nested
        inner class ShouldThrowAnExceptionOnTruncatedEntries {

            private val entrypoint = "org.apache.commons.compress.archivers.tar.TarArchiveInputStreamTest::shouldThrowAnExceptionOnTruncatedEntries"

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
                    project.copyProjectFile(resDir.resolve("COMPRESS-279.tar"), outputDir!!.path)

                    assertTestSuccess(outputDir!!.path, JAVA_17_HOME) {
                        add("-cp")
                        add("${project.testCpJars.joinToString(":")}:${sourceRoots.joinToString(":")}:${outputDir!!.path.resolve(resDir)}")

                        add("-m")
                        add(testCase.toJUnitMethod())
                    }
                }

                @Nested
                inner class ReductionRegressionTests {

                    /**
                     * ```
                     * [javac] src/test/java/org/apache/commons/compress/archivers/tar/TarArchiveInputStreamTest.java:20: error: cannot find symbol
                     * [javac] import static org.apache.commons.compress.AbstractTestCase.mkdir;
                     * [javac]                                          ^
                     * [javac]   symbol:   class AbstractTestCase
                     * [javac]   location: package org.apache.commons.compress
                     * [javac] src/test/java/org/apache/commons/compress/archivers/tar/TarArchiveInputStreamTest.java:20: error: static import only from classes and interfaces
                     * [javac] import static org.apache.commons.compress.AbstractTestCase.mkdir;
                     * [javac] ^
                     * [javac] src/test/java/org/apache/commons/compress/archivers/tar/TarArchiveInputStreamTest.java:31: error: cannot find symbol
                     * [javac]         File dir = mkdir("COMPRESS-279");
                     * [javac]                    ^
                     * [javac]   symbol:   method mkdir(String)
                     * [javac]   location: class TarArchiveInputStreamTest
                     * ```
                     */
                    @Test
                    fun `Include Classes if Loaded`() {
                        val srcCU = reducer.context
                            .getCUByPrimaryTypeName("org.apache.commons.compress.archivers.tar.TarArchiveInputStreamTest")
                        assumeTrue(srcCU != null)

                        val srcType = srcCU.primaryType.get()
                        val srcMethod = srcType
                            .getMethodsBySignature("shouldThrowAnExceptionOnTruncatedEntries").single()
                        val srcMethodCall = srcMethod
                            .body.get()
                            .statements[0].asExpressionStmt()
                            .expression.asVariableDeclarationExpr()
                            .variables.single { it.nameAsString == "dir" }
                            .initializer.get().asMethodCallExpr()
                        assumeFalse {
                            srcMethodCall.isUnusedInCoverage(
                                ReferenceTypeLikeDeclaration.create(srcType).coberturaCoverageData?.lines,
                                srcCU.jacocoCoverageData?.first?.lines,
                                false
                            )
                        }
                        assumeFalse(srcMethodCall.isUnusedForRemovalData)
                        assumeFalse(srcMethodCall.isUnusedForDummyData)

                        val tgtCU = reducer.context
                            .getCUByPrimaryTypeName("org.apache.commons.compress.AbstractTestCase")
                        assumeTrue(tgtCU != null)

                        val tgtType = tgtCU.primaryType.get()

                        val typeDecision = isTypeReachable(
                            reducer.context,
                            tgtType,
                            enableAssertions,
                            noCache = true
                        )
                        assertTrue(typeDecision)

                        val tgtMethod = tgtType.getMethodsBySignature("mkdir", "String").single()
                        assertTrue {
                            tgtMethod.inclusionReasonsData.synchronizedWith { isNotEmpty() }
                        }

                        val methodDecision = TagGroundTruthUnusedDecls.Companion.decideForMethod(
                            reducer.context,
                            tgtMethod,
                            enableAssertions,
                            noCache = true
                        )
                        assertEquals(NodeTransformDecision.NO_OP, methodDecision)
                        assertFalse(tgtMethod.isUnusedForRemovalData)
                        assertFalse(tgtMethod.isUnusedForDummyData)
                    }
                }


                @Nested
                inner class SymbolLookupTests {
                }
            }
        }
    }
}