package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.tool.compilation.CompilerProxy
import com.derppening.researchprojecttoolkit.tool.reachability.ReachableReason
import com.derppening.researchprojecttoolkit.tool.transform.NodeTransformDecision
import com.derppening.researchprojecttoolkit.tool.transform.TagGroundTruthUnusedDecls.Companion.decideForConstructor
import com.derppening.researchprojecttoolkit.tool.transform.TagGroundTruthUnusedDecls.Companion.decideForMethod
import com.derppening.researchprojecttoolkit.tool.transform.TagGroundTruthUnusedDecls.Companion.isTypeReachable
import com.derppening.researchprojecttoolkit.util.synchronizedWith
import org.junit.jupiter.api.Nested
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoverageBasedReducerCompress9fTest : CoverageBasedReducerIntegTest() {

    override val project = TestProjects.Compress9f

    @Nested
    inner class TarArchiveOutputStreamTest {

        @Nested
        inner class TestCount : OnTestCase("org.apache.commons.compress.archivers.tar.TarArchiveOutputStreamTest::testCount", true) {

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

                // Copy `src/test/resources/test1.xml` into directory
                project.copyProjectFile(Path("src/test/resources/test1.xml"), outputDir!!.path)

                assertTestSuccess(outputDir!!.path, JAVA_17_HOME) {
                    add("-cp")
                    add("${project.testCpJars.joinToString(":")}:${sourceRoots.joinToString(":")}")

                    add("-m")
                    add(testCase.toJUnitMethod())
                }
            }

            @Nested
            inner class ReductionRegressionTests {

                @Test
                fun `Do Not Mark Entrypoint as Unreachable`() {
                    val cu = reducer.context
                        .getCUByPrimaryTypeName("org.apache.commons.compress.archivers.tar.TarArchiveOutputStreamTest")
                    assumeTrue(cu != null)

                    val methodDecl = cu.primaryType.get()
                        .getMethodsBySignature("testCount").single()
                    assertTrue(methodDecl.inclusionReasonsData.isNotEmpty())

                    val decision = decideForMethod(reducer.context, methodDecl, enableAssertions, noCache = true)
                    assertEquals(NodeTransformDecision.NO_OP, decision)

                    assertFalse(methodDecl.isUnusedForRemovalData)
                    assertFalse(methodDecl.isUnusedForDummyData)
                }

                /**
                 * ```
                 * /tmp/7668598235599480414/srcRoot1/org/apache/commons/compress/compressors/pack200/Pack200CompressorOutputStream.java:25: error: cannot find symbol
                 * import java.util.jar.Pack200;
                 *                     ^
                 *   symbol:   class Pack200
                 *   location: package java.util.jar
                 * ```
                 */
                @Test
                fun `Regression-00`() {
                    val cu = reducer.context
                        .getCUByPrimaryTypeName("org.apache.commons.compress.compressors.pack200.Pack200Utils")
                    assumeTrue(cu != null)

                    val import = cu.imports.single {
                        !it.isStatic && !it.isAsterisk && it.nameAsString == "java.util.jar.Pack200"
                    }
                    assertTrue {
                        import.inclusionReasonsData.synchronizedWith {
                            all {
                                (it as ReachableReason.DirectlyReferencedByNode)
                                    .node
                                    .let { it.isUnusedForDummyData || it.isUnusedForRemovalData }
                            }
                        }
                    }
                    assertTrue(import.isUnusedForRemovalData)
                }

                /**
                 * ```
                 * /tmp/2119379984293622507/srcRoot0/org/apache/commons/compress/archivers/zip/Zip64SupportIT.java:34: error: cannot find symbol
                 *     @Test
                 *      ^
                 *   symbol:   class Test
                 *   location: class Zip64SupportIT
                 *   ```
                 */
                @Test
                fun `Regression-01`() {
                    val cu = reducer.context
                        .getCUByPrimaryTypeName("org.apache.commons.compress.archivers.zip.Zip64SupportIT")
                    assumeTrue(cu != null)

                    val method = cu.primaryType.get()
                        .getMethodsBySignature("read5GBOfZerosUsingInputStream").single()
                    assumeTrue(method.isUnusedForDummyData)

                    val import = cu.imports.single {
                        !it.isStatic && !it.isAsterisk && it.nameAsString == "org.junit.Test"
                    }
                    assertTrue {
                        import.inclusionReasonsData.synchronizedWith {
                            all {
                                (it as ReachableReason.DirectlyReferencedByNode)
                                    .node
                                    .let { it.isUnusedForDummyData || it.isUnusedForRemovalData }
                            }
                        }
                    }
                    assertFalse(import.isUnusedForRemovalData)
                }

                @Test
                fun `Do not include super-delegating constructors when superclass has default constructor`() {
                    val cu = reducer.context
                        .getCUByPrimaryTypeName("org.apache.commons.compress.archivers.ArchiveException")
                    assumeTrue(cu != null)

                    // Supertype of `ArchiveException` is `java.lang.Exception`, which has a default constructor
                    val type = cu.primaryType.get()

                    val ctor1 = type.getConstructorByParameterTypes("String").get()
                    assertEquals(NodeTransformDecision.REMOVE, decideForConstructor(reducer.context, ctor1, enableAssertions, noCache = true))

                    val ctor2 = type.getConstructorByParameterTypes("String", "Exception").get()
                    assertEquals(NodeTransformDecision.REMOVE, decideForConstructor(reducer.context, ctor2, enableAssertions, noCache = true))
                }

                @Test
                fun `Include Type Reachability when determining Member Reachability and Vice-Versa`() {
                    val baseCU = reducer.context
                        .getCUByPrimaryTypeName("org.apache.commons.compress.compressors.pack200.Pack200CompressorOutputStream")
                    assumeTrue(baseCU != null)

                    val baseType = baseCU.primaryType.get()
                    assertTrue(baseType.isUnusedForRemovalData)

                    val pack200StrategyCU = reducer.context
                        .getCUByPrimaryTypeName("org.apache.commons.compress.compressors.pack200.Pack200Strategy")
                    assumeTrue(pack200StrategyCU != null)

                    val pack200StrategyType = pack200StrategyCU.primaryType.get()
                    val pack200StrategyReachable =
                        isTypeReachable(reducer.context, pack200StrategyType, enableAssertions, noCache = true)
                    assertFalse(pack200StrategyReachable)

                    val streamBridgeCU = reducer.context
                        .getCUByPrimaryTypeName("org.apache.commons.compress.compressors.pack200.StreamBridge")
                    assumeTrue(streamBridgeCU != null)

                    val streamBridgeType = streamBridgeCU.primaryType.get()
                    val streamBridgeReachable = isTypeReachable(reducer.context, streamBridgeType, enableAssertions, noCache = true)
                    assertFalse(streamBridgeReachable)
                }

                @Test
                fun `Regression-02`() {
                    val cu = reducer.context
                        .getCUByPrimaryTypeName("org.apache.commons.compress.archivers.zip.ZipArchiveEntry")
                    assumeTrue(cu != null)

                    // Supertype of `ArchiveException` is `java.lang.Exception`, which has a default constructor
                    val type = cu.primaryType.get()

                    val altCtor = type.getConstructorByParameterTypes("String").get()
                    val altCtorDecision = decideForConstructor(reducer.context, altCtor, enableAssertions, noCache = true)

                    if (altCtorDecision == NodeTransformDecision.REMOVE) {
                        val defaultCtor = type.defaultConstructor.get()
                        val defaultCtorDecision = decideForConstructor(reducer.context, defaultCtor, enableAssertions, noCache = true)

                        assertEquals(NodeTransformDecision.REMOVE, defaultCtorDecision)
                    }
                }
            }
        }
    }
}