package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.tool.compilation.CompilerProxy
import com.derppening.researchprojecttoolkit.tool.reachability.ReachableReason
import com.derppening.researchprojecttoolkit.tool.transform.NodeTransformDecision
import com.derppening.researchprojecttoolkit.tool.transform.TagStaticAnalysisMemberUnusedDecls.Companion.decideForConstructor
import com.derppening.researchprojecttoolkit.tool.transform.TagStaticAnalysisMemberUnusedDecls.Companion.decideForMethod
import com.derppening.researchprojecttoolkit.tool.transform.TagStaticAnalysisMemberUnusedDecls.Companion.isTypeReachable
import com.derppening.researchprojecttoolkit.util.synchronizedWith
import org.junit.jupiter.api.Nested
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StaticAnalysisMemberReducerCompress21fTest : StaticAnalysisMemberReducerIntegTest() {

    override val project = TestProjects.Compress21f

    @Nested
    inner class SevenZOutputFileTest {

        @Nested
        inner class TestEightFilesSomeNotEmpty : OnTestCase("org.apache.commons.compress.archivers.sevenz.SevenZOutputFileTest::testEightFilesSomeNotEmpty", true) {

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

                assertTestSuccess(outputDir!!.path, JAVA_17_HOME) {
                    add("-cp")
                    add("${project.testCpJars.joinToString(":")}:${sourceRoots.joinToString(":")}")

                    add("-m")
                    add(testCase.toJUnitMethod())
                }
            }

            /**
             * ```
             *     => java.lang.Exception: No tests found matching Method testEightFilesSomeNotEmpty(org.apache.commons.compress.archivers.sevenz.SevenZOutputFileTest) from org.junit.vintage.engine.descriptor.RunnerRequest@76908cc0
             *        org.junit.internal.requests.FilterRequest.getRunner(FilterRequest.java:40)
             *        org.junit.vintage.engine.descriptor.RunnerTestDescriptor.applyFilters(RunnerTestDescriptor.java:142)
             *        org.junit.vintage.engine.discovery.RunnerTestDescriptorPostProcessor.applyFiltersAndCreateDescendants(RunnerTestDescriptorPostProcessor.java:42)
             *        org.junit.vintage.engine.discovery.VintageDiscoverer.discover(VintageDiscoverer.java:46)
             *        org.junit.vintage.engine.VintageTestEngine.discover(VintageTestEngine.java:64)
             *        org.junit.platform.launcher.core.EngineDiscoveryOrchestrator.discoverEngineRoot(EngineDiscoveryOrchestrator.java:152)
             *        org.junit.platform.launcher.core.EngineDiscoveryOrchestrator.discoverSafely(EngineDiscoveryOrchestrator.java:132)
             *        org.junit.platform.launcher.core.EngineDiscoveryOrchestrator.discover(EngineDiscoveryOrchestrator.java:107)
             *        org.junit.platform.launcher.core.EngineDiscoveryOrchestrator.discover(EngineDiscoveryOrchestrator.java:78)
             *        org.junit.platform.launcher.core.DefaultLauncher.discover(DefaultLauncher.java:110)
             *        [...]
             * ```
             */
            @Test
            fun `Include Inherited Entrypoint Method`() {
                val baseClassCU = reducer.context
                    .getCUByPrimaryTypeName("org.apache.commons.compress.AbstractTestCase")
                assumeTrue(baseClassCU != null)

                val baseClassType = baseClassCU.primaryType.get()
                val baseClassTypeReachable = isTypeReachable(
                    reducer.context,
                    baseClassType,
                    enableAssertions,
                    noCache = true
                )
                assertTrue(baseClassTypeReachable)

                val noArgCtor = baseClassType.defaultConstructor.get()
                val noArgCtorDecision = decideForConstructor(
                    reducer.context,
                    noArgCtor,
                    enableAssertions,
                    noCache = true
                )
                assertEquals(NodeTransformDecision.NO_OP, noArgCtorDecision)
            }

            /**
             * ```
             * java.lang.AssertionError: This method should not be reached! Signature: getProperties()
             *   at org.apache.commons.compress.archivers.sevenz.SevenZMethod$1.getProperties(SevenZMethod.java:42)
             *   at org.apache.commons.compress.archivers.sevenz.SevenZOutputFile.writeFolder(SevenZOutputFile.java:315)
             *   at org.apache.commons.compress.archivers.sevenz.SevenZOutputFile.writeUnpackInfo(SevenZOutputFile.java:293)
             *   at org.apache.commons.compress.archivers.sevenz.SevenZOutputFile.writeStreamsInfo(SevenZOutputFile.java:261)
             *   at org.apache.commons.compress.archivers.sevenz.SevenZOutputFile.writeHeader(SevenZOutputFile.java:253)
             *   at org.apache.commons.compress.archivers.sevenz.SevenZOutputFile.finish(SevenZOutputFile.java:187)
             *   at org.apache.commons.compress.archivers.sevenz.SevenZOutputFile.close(SevenZOutputFile.java:77)
             *   at org.apache.commons.compress.archivers.sevenz.SevenZOutputFileTest.testCompress252(SevenZOutputFileTest.java:90)
             *   at org.apache.commons.compress.archivers.sevenz.SevenZOutputFileTest.testEightFilesSomeNotEmpty(SevenZOutputFileTest.java:69)
             * ```
             */
            @Test
            fun `Regression-00`() {
                // org.apache.commons.compress.archivers.sevenz.SevenZOutputFileTest.testEightFilesSomeNotEmpty()
                run {
                    val cu = reducer.context.getCUByPrimaryTypeName("org.apache.commons.compress.archivers.sevenz.SevenZOutputFileTest")
                    assumeTrue(cu != null)

                    val method = cu.primaryType.get()
                        .getMethodsBySignature("testEightFilesSomeNotEmpty").single()

                    assertTrue(method.inclusionReasonsData.isNotEmpty())
                    assertTrue {
                        method.inclusionReasonsData.synchronizedWith {
                            filterIsInstance<ReachableReason.Entrypoint>().isNotEmpty()
                        }
                    }
                    assertFalse(method.isUnusedForRemovalData)
                    assertFalse(method.isUnusedForDummyData)
                }

                // org.apache.commons.compress.archivers.sevenz.SevenZOutputFileTest.testCompress252(int, int)
                run {
                    val cu = reducer.context.getCUByPrimaryTypeName("org.apache.commons.compress.archivers.sevenz.SevenZOutputFileTest")
                    assumeTrue(cu != null)

                    val method = cu.primaryType.get()
                        .getMethodsBySignature("testCompress252", "int", "int").single()

                    assertTrue(method.inclusionReasonsData.isNotEmpty())
                    assertTrue {
                        method.inclusionReasonsData.synchronizedWith {
                            filterIsInstance<ReachableReason.ReferencedBySymbolName>().isNotEmpty()
                        }
                    }
                    assertFalse(method.isUnusedForRemovalData)
                    assertFalse(method.isUnusedForDummyData)
                }

                // org.apache.commons.compress.archivers.sevenz.SevenZOutputFile.close()
                run {
                    val cu = reducer.context.getCUByPrimaryTypeName("org.apache.commons.compress.archivers.sevenz.SevenZOutputFile")
                    assumeTrue(cu != null)

                    val method = cu.primaryType.get()
                        .getMethodsBySignature("close").single()

                    assertTrue(method.inclusionReasonsData.isNotEmpty())
                    assertTrue {
                        method.inclusionReasonsData.synchronizedWith {
                            filterIsInstance<ReachableReason.ReferencedBySymbolName>().isNotEmpty()
                        }
                    }
                    assertFalse(method.isUnusedForRemovalData)
                    assertFalse(method.isUnusedForDummyData)
                }

                // org.apache.commons.compress.archivers.sevenz.SevenZOutputFile.finish()
                run {
                    val cu = reducer.context.getCUByPrimaryTypeName("org.apache.commons.compress.archivers.sevenz.SevenZOutputFile")
                    assumeTrue(cu != null)

                    val method = cu.primaryType.get()
                        .getMethodsBySignature("finish").single()

                    assertTrue(method.inclusionReasonsData.isNotEmpty())
                    assertTrue {
                        method.inclusionReasonsData.synchronizedWith {
                            filterIsInstance<ReachableReason.ReferencedBySymbolName>().isNotEmpty()
                        }
                    }
                    assertFalse(method.isUnusedForRemovalData)
                    assertFalse(method.isUnusedForDummyData)
                }

                // org.apache.commons.compress.archivers.sevenz.SevenZOutputFile.writeHeader(DataOutput)
                run {
                    val cu = reducer.context.getCUByPrimaryTypeName("org.apache.commons.compress.archivers.sevenz.SevenZOutputFile")
                    assumeTrue(cu != null)

                    val method = cu.primaryType.get()
                        .getMethodsBySignature("writeHeader", "DataOutput").single()

                    assertTrue(method.inclusionReasonsData.isNotEmpty())
                    assertTrue {
                        method.inclusionReasonsData.synchronizedWith {
                            filterIsInstance<ReachableReason.ReferencedBySymbolName>().isNotEmpty()
                        }
                    }
                    assertFalse(method.isUnusedForRemovalData)
                    assertFalse(method.isUnusedForDummyData)
                }

                // org.apache.commons.compress.archivers.sevenz.SevenZOutputFile.writeStreamsInfo(DataOutput)
                run {
                    val cu = reducer.context.getCUByPrimaryTypeName("org.apache.commons.compress.archivers.sevenz.SevenZOutputFile")
                    assumeTrue(cu != null)

                    val method = cu.primaryType.get()
                        .getMethodsBySignature("writeStreamsInfo", "DataOutput").single()

                    assertTrue(method.inclusionReasonsData.isNotEmpty())
                    assertTrue {
                        method.inclusionReasonsData.synchronizedWith {
                            filterIsInstance<ReachableReason.ReferencedBySymbolName>().isNotEmpty()
                        }
                    }
                    assertFalse(method.isUnusedForRemovalData)
                    assertFalse(method.isUnusedForDummyData)
                }

                // org.apache.commons.compress.archivers.sevenz.SevenZOutputFile.writeUnpackInfo(DataOutput)
                run {
                    val cu = reducer.context.getCUByPrimaryTypeName("org.apache.commons.compress.archivers.sevenz.SevenZOutputFile")
                    assumeTrue(cu != null)

                    val method = cu.primaryType.get()
                        .getMethodsBySignature("writeUnpackInfo", "DataOutput").single()

                    assertTrue(method.inclusionReasonsData.isNotEmpty())
                    assertTrue {
                        method.inclusionReasonsData.synchronizedWith {
                            filterIsInstance<ReachableReason.ReferencedBySymbolName>().isNotEmpty()
                        }
                    }
                    assertFalse(method.isUnusedForRemovalData)
                    assertFalse(method.isUnusedForDummyData)
                }

                // org.apache.commons.compress.archivers.sevenz.SevenZOutputFile.writeFolder(DataOutput)
                run {
                    val cu = reducer.context.getCUByPrimaryTypeName("org.apache.commons.compress.archivers.sevenz.SevenZOutputFile")
                    assumeTrue(cu != null)

                    val method = cu.primaryType.get()
                        .getMethodsBySignature("writeFolder", "DataOutput").single()

                    assertTrue(method.inclusionReasonsData.isNotEmpty())
                    assertTrue {
                        method.inclusionReasonsData.synchronizedWith {
                            filterIsInstance<ReachableReason.ReferencedBySymbolName>().isNotEmpty()
                        }
                    }
                    assertFalse(method.isUnusedForRemovalData)
                    assertFalse(method.isUnusedForDummyData)
                }

                // org.apache.commons.compress.archivers.sevenz.SevenZMethod.getProperties()
                run {
                    val cu = reducer.context.getCUByPrimaryTypeName("org.apache.commons.compress.archivers.sevenz.SevenZMethod")
                    assumeTrue(cu != null)

                    val method = cu.primaryType.get()
                        .getMethodsBySignature("getProperties").single()

                    assertTrue(method.inclusionReasonsData.isNotEmpty())
                    assertTrue {
                        method.inclusionReasonsData.synchronizedWith {
                            filterIsInstance<ReachableReason.ReferencedBySymbolName>().isNotEmpty()
                        }
                    }
                    assertFalse(method.isUnusedForRemovalData)
                    assertFalse(method.isUnusedForDummyData)
                }

                // org.apache.commons.compress.archivers.sevenz.SevenZMethod$1.getProperties()
                run {
                    val cu = reducer.context.getCUByPrimaryTypeName("org.apache.commons.compress.archivers.sevenz.SevenZMethod")
                    assumeTrue(cu != null)

                    val method = cu.primaryType.get()
                        .asEnumDeclaration()
                        .entries
                        .single { it.nameAsString == "LZMA2" }
                        .classBody
                        .single {
                            it.isMethodDeclaration &&
                                    it.asMethodDeclaration().nameAsString == "getProperties" &&
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
                }
            }

            @Test
            fun `Pack200 Unreachable Regression`() {
                val cu = reducer.context.getCUByPrimaryTypeName("org.apache.commons.compress.compressors.pack200.Pack200CompressorInputStream")
                assumeTrue(cu != null)

                val type = cu.primaryType.get()
                val typeReachable = isTypeReachable(
                    reducer.context,
                    type,
                    enableAssertions,
                    noCache = true
                )
                assertFalse(typeReachable)
            }
        }
    }
}