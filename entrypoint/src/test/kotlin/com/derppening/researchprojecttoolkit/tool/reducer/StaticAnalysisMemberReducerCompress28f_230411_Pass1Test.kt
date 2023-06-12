package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.tool.compilation.CompilerProxy
import com.derppening.researchprojecttoolkit.tool.transform.NodeTransformDecision
import com.derppening.researchprojecttoolkit.tool.transform.TagStaticAnalysisMemberUnusedDecls.Companion.decideForMethod
import com.derppening.researchprojecttoolkit.tool.transform.TagStaticAnalysisMemberUnusedDecls.Companion.isTypeReachable
import com.derppening.researchprojecttoolkit.util.synchronizedWith
import org.junit.jupiter.api.Nested
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StaticAnalysisMemberReducerCompress28f_230411_Pass1Test : StaticAnalysisMemberReducerIntegTest() {

    override val project = TestProjects.Compress28f_230411_Pass1

    @Nested
    inner class TarArchiveInputStreamTest {

        @Nested
        inner class ShouldThrowAnExceptionOnTruncatedEntries : OnTestCase("org.apache.commons.compress.archivers.tar.TarArchiveInputStreamTest::shouldThrowAnExceptionOnTruncatedEntries", true) {

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
                    listOf("-Xmaxerrs", "$JAVAC_MAXERRS"),
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
            fun `Include Classes of Statically Imported Methods`() {
                val srcCU = reducer.context
                    .getCUByPrimaryTypeName("org.apache.commons.compress.archivers.tar.TarArchiveInputStreamTest")
                assumeTrue(srcCU != null)

                val srcType = srcCU.primaryType.get()
                val srcMethod = srcType
                    .getMethodsBySignature("shouldThrowAnExceptionOnTruncatedEntries").single()
                assumeTrue {
                    decideForMethod(
                        reducer.context,
                        srcMethod,
                        enableAssertions,
                        noCache = false
                    ) == NodeTransformDecision.NO_OP
                }

                val tgtCU = reducer.context
                    .getCUByPrimaryTypeName("org.apache.commons.compress.AbstractTestCase")
                assumeTrue(tgtCU != null)

                val tgtType = tgtCU.primaryType.get()

                val typeReachable = isTypeReachable(
                    reducer.context,
                    tgtType,
                    enableAssertions,
                    noCache = true
                )
                assertTrue(typeReachable)

                val tgtMethod = tgtType.getMethodsBySignature("mkdir", "String").single()
                assertTrue {
                    tgtMethod.inclusionReasonsData.synchronizedWith { isNotEmpty() }
                }

                val methodDecision = decideForMethod(
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
    }
}