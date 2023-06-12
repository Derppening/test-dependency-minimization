package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.tool.compilation.CompilerProxy
import com.derppening.researchprojecttoolkit.tool.reachability.ReachableReason
import com.derppening.researchprojecttoolkit.util.synchronizedWith
import org.junit.jupiter.api.Nested
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StaticAnalysisMemberReducerJxPath19fTest : StaticAnalysisMemberReducerIntegTest() {

    override val project = TestProjects.JxPath19f

    @Nested
    inner class AliasedNamespaceIterationTest {

        @Nested
        inner class TestIterateDOM : OnTestCase("org.apache.commons.jxpath.ri.model.AliasedNamespaceIterationTest::testIterateDOM", true) {

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

                project.copyProjectFile(Path("src/test/org/apache/commons/jxpath/IterateAliasedNS.xml"), outputDir!!.path)

                val junitRunner = tryTestNoAssertion(outputDir!!.path, JAVA_17_HOME) {
                    add("-cp")
                    add("${project.testCpJars.joinToString(":")}:${sourceRoots.joinToString(":")}")

                    add("-m")
                    add(testCase.toJUnitMethod())
                }
                assertFalse(junitRunner.exitCode.isSuccess)
            }

            /**
             * ```
             * java.lang.AssertionError: This method should not be reached! Signature: newContext(JXPathContext, Object)
             *   at org.apache.commons.jxpath.ri.JXPathContextFactoryReferenceImpl.newContext(JXPathContextFactoryReferenceImpl.java:31)
             *   at org.apache.commons.jxpath.JXPathContext.newContext(JXPathContext.java:440)
             *   at org.apache.commons.jxpath.ri.model.AliasedNamespaceIterationTest.createContext(AliasedNamespaceIterationTest.java:39)
             *   at org.apache.commons.jxpath.ri.model.AliasedNamespaceIterationTest.doTestIterate(AliasedNamespaceIterationTest.java:45)
             *   at org.apache.commons.jxpath.ri.model.AliasedNamespaceIterationTest.testIterateDOM(AliasedNamespaceIterationTest.java:49)
             * ```
             */
            @Test
            @Ignore("Expected to fail because method uses reflection (and bypasses static analysis)")
            fun `Regression-00`() {
                run {
                    val cu = reducer.context.getCUByPrimaryTypeName("org.apache.commons.jxpath.ri.model.AliasedNamespaceIterationTest")
                    assumeTrue(cu != null)

                    val method = cu.primaryType.get()
                        .getMethodsBySignature("testIterateDOM").single()

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
                    val cu = reducer.context.getCUByPrimaryTypeName("org.apache.commons.jxpath.ri.model.AliasedNamespaceIterationTest")
                    assumeTrue(cu != null)

                    val method = cu.primaryType.get()
                        .getMethodsBySignature("doTestIterate", "String").single()

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
                    val cu = reducer.context.getCUByPrimaryTypeName("org.apache.commons.jxpath.ri.model.AliasedNamespaceIterationTest")
                    assumeTrue(cu != null)

                    val method = cu.primaryType.get()
                        .getMethodsBySignature("createContext", "String").single()

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
                    val cu = reducer.context.getCUByPrimaryTypeName("org.apache.commons.jxpath.JXPathContext")
                    assumeTrue(cu != null)

                    val method = cu.primaryType.get()
                        .getMethodsBySignature("newContext", "Object").single()

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
                    val cu = reducer.context.getCUByPrimaryTypeName("org.apache.commons.jxpath.JXPathContextFactory")
                    assumeTrue(cu != null)

                    val method = cu.primaryType.get()
                        .getMethodsBySignature("newContext", "JXPathContext", "Object").single()

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
                    val cu = reducer.context.getCUByPrimaryTypeName("org.apache.commons.jxpath.ri.JXPathContextFactoryReferenceImpl")
                    assumeTrue(cu != null)

                    val method = cu.primaryType.get()
                        .getMethodsBySignature("newContext", "JXPathContext", "Object").single()

                    assertTrue(method.inclusionReasonsData.isNotEmpty())
                    assertTrue {
                        method.inclusionReasonsData.synchronizedWith {
                            filterIsInstance<ReachableReason.TransitiveMethodCallTarget>().isNotEmpty()
                        }
                    }
                    assertFalse(method.isUnusedForRemovalData)
                    assertFalse(method.isUnusedForDummyData)
                }
            }
        }
    }
}