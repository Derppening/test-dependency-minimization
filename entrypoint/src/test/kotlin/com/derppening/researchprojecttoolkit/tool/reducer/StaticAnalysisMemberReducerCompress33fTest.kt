package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.tool.compilation.CompilerProxy
import com.derppening.researchprojecttoolkit.tool.reachability.ReachableReason
import com.derppening.researchprojecttoolkit.tool.transform.NodeTransformDecision
import com.derppening.researchprojecttoolkit.tool.transform.TagStaticAnalysisMemberUnusedDecls.Companion.decideForMethod
import com.derppening.researchprojecttoolkit.util.synchronizedWith
import org.junit.jupiter.api.Nested
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StaticAnalysisMemberReducerCompress33fTest : StaticAnalysisMemberReducerIntegTest() {

    override val project = TestProjects.Compress33f

    @Nested
    inner class DetectCompressorTestCase {

        @Nested
        inner class TestDetection : OnTestCase("org.apache.commons.compress.compressors.DetectCompressorTestCase::testDetection", true) {

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
                arrayOf(
                    "bla.tar.deflatez",
                    "bla.pack",
                    "bla.tar.xz",
                    "bla.tgz",
                    "bla.txt.bz2"
                ).forEach {
                    project.copyProjectFile(resDir.resolve(it), outputDir!!.path)
                }

                assertTestSuccess(outputDir!!.path, JAVA_8_HOME) {
                    add("-cp")
                    add("${project.testCpJars.joinToString(":")}:${sourceRoots.joinToString(":")}:${outputDir!!.path.resolve(resDir)}")

                    add("-m")
                    add(testCase.toJUnitMethod())
                }
            }

            /**
             * ```
             * junit.framework.AssertionFailedError: This method should not be reached! Signature: newStreamBridge()
             *   at org.apache.commons.compress.compressors.pack200.Pack200Strategy$1.newStreamBridge(Pack200Strategy.java:37)
             *   at org.apache.commons.compress.compressors.pack200.Pack200CompressorInputStream.<init>(Pack200CompressorInputStream.java:98)
             *   at org.apache.commons.compress.compressors.pack200.Pack200CompressorInputStream.<init>(Pack200CompressorInputStream.java:66)
             *   at org.apache.commons.compress.compressors.pack200.Pack200CompressorInputStream.<init>(Pack200CompressorInputStream.java:55)
             *   at org.apache.commons.compress.compressors.CompressorStreamFactory.createCompressorInputStream(CompressorStreamFactory.java:222)
             *   at org.apache.commons.compress.compressors.DetectCompressorTestCase.getStreamFor(DetectCompressorTestCase.java:119)
             *   at org.apache.commons.compress.compressors.DetectCompressorTestCase.testDetection(DetectCompressorTestCase.java:91)
             * ```
             */
            @Test
            fun `Regression-00`() {
                run {
                    val cu = reducer.context.getCUByPrimaryTypeName("org.apache.commons.compress.compressors.DetectCompressorTestCase")
                    assumeTrue(cu != null)

                    val method = cu.primaryType.get()
                        .getMethodsBySignature("testDetection").single()

                    assertTrue(method.inclusionReasonsData.isNotEmpty())
                    assertTrue {
                        method.inclusionReasonsData.synchronizedWith {
                            filterIsInstance<ReachableReason.Entrypoint>().isNotEmpty()
                        }
                    }
                    assertFalse(method.isUnusedForRemovalData)
                    assertFalse(method.isUnusedForDummyData)
                }

                run {
                    val cu = reducer.context.getCUByPrimaryTypeName("org.apache.commons.compress.compressors.DetectCompressorTestCase")
                    assumeTrue(cu != null)

                    val method = cu.primaryType.get()
                        .getMethodsBySignature("getStreamFor", "String").single()

                    assertTrue(method.inclusionReasonsData.isNotEmpty())
                    assertTrue {
                        method.inclusionReasonsData.synchronizedWith {
                            filterIsInstance<ReachableReason.ReferencedBySymbolName>().isNotEmpty()
                        }
                    }
                    assertFalse(method.isUnusedForRemovalData)
                    assertFalse(method.isUnusedForDummyData)
                }

                run {
                    val cu = reducer.context.getCUByPrimaryTypeName("org.apache.commons.compress.compressors.CompressorStreamFactory")
                    assumeTrue(cu != null)

                    val method = cu.primaryType.get()
                        .getMethodsBySignature("createCompressorInputStream", "InputStream").single()

                    assertTrue(method.inclusionReasonsData.isNotEmpty())
                    assertTrue {
                        method.inclusionReasonsData.synchronizedWith {
                            filterIsInstance<ReachableReason.ReferencedBySymbolName>().isNotEmpty()
                        }
                    }
                    assertFalse(method.isUnusedForRemovalData)
                    assertFalse(method.isUnusedForDummyData)
                }

                run {
                    val cu = reducer.context.getCUByPrimaryTypeName("org.apache.commons.compress.compressors.pack200.Pack200CompressorInputStream")
                    assumeTrue(cu != null)

                    val method = cu.primaryType.get()
                        .getConstructorByParameterTypes("InputStream").get()

                    assertTrue(method.inclusionReasonsData.isNotEmpty())
                    assertTrue {
                        method.inclusionReasonsData.synchronizedWith {
                            filterIsInstance<ReachableReason.ReferencedBySymbolName>().isNotEmpty()
                        }
                    }
                    assertFalse(method.isUnusedForRemovalData)
                    assertFalse(method.isUnusedForDummyData)
                }

                run {
                    val cu = reducer.context.getCUByPrimaryTypeName("org.apache.commons.compress.compressors.pack200.Pack200CompressorInputStream")
                    assumeTrue(cu != null)

                    val method = cu.primaryType.get()
                        .getConstructorByParameterTypes("InputStream", "Pack200Strategy").get()

                    assertTrue(method.inclusionReasonsData.isNotEmpty())
                    assertTrue {
                        method.inclusionReasonsData.synchronizedWith {
                            filterIsInstance<ReachableReason.ReferencedByCtorCallByExplicitStmt>().isNotEmpty()
                        }
                    }
                    assertFalse(method.isUnusedForRemovalData)
                    assertFalse(method.isUnusedForDummyData)
                }

                run {
                    val cu = reducer.context.getCUByPrimaryTypeName("org.apache.commons.compress.compressors.pack200.Pack200CompressorInputStream")
                    assumeTrue(cu != null)

                    val method = cu.primaryType.get()
                        .getConstructorByParameterTypes("InputStream", "File", "Pack200Strategy", "Map<String,String>").get()

                    assertTrue(method.inclusionReasonsData.isNotEmpty())
                    assertTrue {
                        method.inclusionReasonsData.synchronizedWith {
                            filterIsInstance<ReachableReason.ReferencedByCtorCallByExplicitStmt>().isNotEmpty()
                        }
                    }
                    assertFalse(method.isUnusedForRemovalData)
                    assertFalse(method.isUnusedForDummyData)
                }

                run {
                    val cu = reducer.context.getCUByPrimaryTypeName("org.apache.commons.compress.compressors.pack200.Pack200Strategy")
                    assumeTrue(cu != null)

                    val method = cu.primaryType.get()
                        .getMethodsBySignature("newStreamBridge").single()

                    assertTrue(method.inclusionReasonsData.isNotEmpty())
                    assertTrue {
                        method.inclusionReasonsData.synchronizedWith {
                            filterIsInstance<ReachableReason.ReferencedBySymbolName>().isNotEmpty()
                        }
                    }
                    assertFalse(method.isUnusedForRemovalData)
                    assertFalse(method.isUnusedForDummyData)
                }

                run {
                    val cu = reducer.context.getCUByPrimaryTypeName("org.apache.commons.compress.compressors.pack200.Pack200Strategy")
                    assumeTrue(cu != null)

                    val method = cu.primaryType.get()
                        .asEnumDeclaration()
                        .entries
                        .single { it.nameAsString == "IN_MEMORY" }
                        .classBody
                        .single {
                            it.isMethodDeclaration &&
                                    it.asMethodDeclaration().nameAsString == "newStreamBridge" &&
                                    it.asMethodDeclaration().parameters.size == 0
                        }
                        .asMethodDeclaration()

                    val decision = decideForMethod(
                        reducer.context,
                        method,
                        enableAssertions,
                        noCache = true
                    )

                    assertEquals(NodeTransformDecision.NO_OP, decision)
                    assertFalse(method.isUnusedForRemovalData)
                    assertFalse(method.isUnusedForDummyData)
                }
            }
        }
    }
}