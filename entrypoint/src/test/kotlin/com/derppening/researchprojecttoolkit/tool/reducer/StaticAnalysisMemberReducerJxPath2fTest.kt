package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.tool.compilation.CompilerProxy
import com.derppening.researchprojecttoolkit.tool.reachability.ReachableReason
import com.derppening.researchprojecttoolkit.util.synchronizedWith
import org.junit.jupiter.api.Nested
import java.nio.file.Path
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StaticAnalysisMemberReducerJxPath2fTest : StaticAnalysisMemberReducerIntegTest() {

    override val project = TestProjects.JxPath2f

    @Nested
    inner class ExtensionFunctionTest {

        @Nested
        inner class TestNodeSetReturn : OnTestCase("org.apache.commons.jxpath.ri.compiler.ExtensionFunctionTest::testNodeSetReturn", true) {

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
             *   at org.apache.commons.jxpath.ri.JXPathContextFactoryReferenceImpl.newContext(JXPathContextFactoryReferenceImpl.java:32)
             *   at org.apache.commons.jxpath.JXPathContext.newContext(JXPathContext.java:413)
             *   at org.apache.commons.jxpath.ri.compiler.ExtensionFunctionTest.setUp(ExtensionFunctionTest.java:63)
             * ```
             */
            @Test
            @Ignore("Expected to fail because method uses reflection (and bypasses static analysis)")
            fun `Regression-00`() {
                run {
                    val cu = reducer.context.getCUByPrimaryTypeName("org.apache.commons.jxpath.ri.compiler.ExtensionFunctionTest")
                    assumeTrue(cu != null)

                    val method = cu.primaryType.get()
                        .getMethodsBySignature("setUp").single()

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