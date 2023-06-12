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

class StaticAnalysisMemberReducerJxPath3fTest : StaticAnalysisMemberReducerIntegTest() {

    override val project = TestProjects.JxPath3f

    @Nested
    inner class BadlyImplementedFactoryTest {

        @Nested
        inner class TestBadFactoryImplementation : OnTestCase("org.apache.commons.jxpath.ri.model.beans.BadlyImplementedFactoryTest::testBadFactoryImplementation", true) {

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
             * compile.tests:
             *     [javac] /tmp/7556129661603294194/src/test/org/apache/commons/jxpath/ri/compiler/ExtensionFunctionTest.java:43: error: constructor JXPathTestCase in class JXPathTestCase cannot be applied to given types;
             *     [javac] public class ExtensionFunctionTest extends JXPathTestCase {
             *     [javac]        ^
             *     [javac]   required: String
             *     [javac]   found: no arguments
             *     [javac]   reason: actual and formal argument lists differ in length
             * ```
             */
            @Test
            @Ignore
            fun `Regression-00`() {
                val cu = reducer.context.getCUByPrimaryTypeName("org.apache.commons.jxpath.ri.compiler.ExtensionFunctionTest")
                assumeTrue(cu != null)

                val type = cu.primaryType.get()

                assertTrue(type.inclusionReasonsData.isNotEmpty())
                assertTrue {
                    type.inclusionReasonsData.synchronizedWith {
                        filterIsInstance<ReachableReason.NestParent>().isNotEmpty()
                    }
                }
                assertTrue(type.isUnusedForSupertypeRemovalData)
            }
        }
    }
}