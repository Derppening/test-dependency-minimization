package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.tool.compilation.CompilerProxy
import com.derppening.researchprojecttoolkit.tool.reachability.ReachableReason
import com.derppening.researchprojecttoolkit.tool.transform.NodeTransformDecision
import com.derppening.researchprojecttoolkit.tool.transform.TagStaticAnalysisMemberUnusedDecls.Companion.decideForFieldDecl
import com.derppening.researchprojecttoolkit.util.getFieldVariableDecl
import com.derppening.researchprojecttoolkit.util.synchronizedWith
import org.junit.jupiter.api.Nested
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.test.*

class StaticAnalysisMemberReducerJxPath1fTest : StaticAnalysisMemberReducerIntegTest() {

    override val project = TestProjects.JxPath1f

    @Nested
    inner class DOMModelTest {

        @Nested
        inner class TestGetNode : OnTestCase("org.apache.commons.jxpath.ri.model.dom.DOMModelTest::testGetNode", true) {

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

                project.copyProjectFile(Path("src/test/org/apache/commons/jxpath/Vendor.xml"), outputDir!!.path)

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
             *     [javac] /tmp/7724717742451498926/src/test/org/apache/commons/jxpath/ri/compiler/ExtensionFunctionTest.java:43: error: constructor JXPathTestCase in class JXPathTestCase cannot be applied to given types;
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

            @Ignore
            @Test
            fun `Regression-01`() {
                val cu = reducer.context.getCUByPrimaryTypeName("org.apache.commons.jxpath.TestBean")
                assumeTrue(cu != null)

                val type = cu.primaryType.get()
                val fieldDecl = type.getFieldVariableDecl("beans")

                val fieldDeclDecision = decideForFieldDecl(
                    reducer.context,
                    fieldDecl,
                    enableAssertions,
                    noCache = true
                )
                assertEquals(NodeTransformDecision.NO_OP, fieldDeclDecision)
            }
        }
    }
}