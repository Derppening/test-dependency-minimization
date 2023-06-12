package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.tool.compilation.CompilerProxy
import com.derppening.researchprojecttoolkit.tool.transform.NodeTransformDecision
import com.derppening.researchprojecttoolkit.tool.transform.TagStaticAnalysisMemberUnusedDecls.Companion.decideForFieldDecl
import com.derppening.researchprojecttoolkit.tool.transform.TagStaticAnalysisMemberUnusedDecls.Companion.decideForMethod
import com.derppening.researchprojecttoolkit.tool.transform.TagStaticAnalysisMemberUnusedDecls.Companion.isTypeReachable
import com.derppening.researchprojecttoolkit.util.getFieldVariableDecl
import com.derppening.researchprojecttoolkit.util.getTypeByName
import com.derppening.researchprojecttoolkit.util.synchronizedWith
import com.github.javaparser.ast.body.AnnotationDeclaration
import org.junit.jupiter.api.Nested
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StaticAnalysisMemberReducerMockito12fTest : StaticAnalysisMemberReducerIntegTest() {

    override val project = TestProjects.Mockito12f

    @Nested
    inner class CaptorAnnotationBasicTest {

        @Nested
        inner class ShouldCaptureGenericList : OnTestCase("org.mockitousage.annotation.CaptorAnnotationBasicTest::shouldCaptureGenericList", true) {

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

                // org.mockito.configuration.MockitoConfiguration is loaded by reflection
                val junitRunner = tryTestNoAssertion(outputDir!!.path, JAVA_17_HOME) {
                    add("-cp")
                    add("${project.testCpJars.joinToString(":")}:${sourceRoots.joinToString(":")}")

                    add("-m")
                    add(testCase.toJUnitMethod())
                }
                assertTrue(junitRunner.exitCode.isFailure)
                assertTrue {
                    junitRunner.stdout.joinToString("\n").contains("This method should not be reached! Signature: getAnnotationEngine()")
                }
            }

            /**
            * ```
            * src/org/mockito/exceptions/Reporter.java:42: error: cannot find symbol
            *     [javac]         throw new MockitoException(join("Checked exception is invalid for this method!", "Invalid: " + t));
            *     [javac]                                    ^
            *     [javac]   symbol:   method join(String,String)
            *     [javac]   location: class Reporter
            * ```
            */
            @Test
            fun `Break Cyclic Reachability for Unqualified Static Imported Methods`() {
                val srcCU = reducer.context
                    .getCUByPrimaryTypeName("org.mockito.exceptions.Reporter")
                assumeTrue(srcCU != null)

                val srcType = srcCU.primaryType.get()
                val srcMethod = srcType
                    .getMethodsBySignature("checkedExceptionInvalid", "Throwable").single()
                assumeTrue {
                    decideForMethod(
                        reducer.context,
                        srcMethod,
                        enableAssertions,
                        noCache = false
                    ) == NodeTransformDecision.NO_OP
                }

                val tgtCU = reducer.context
                    .getCUByPrimaryTypeName("org.mockito.internal.util.StringJoiner")
                assumeTrue(tgtCU != null)

                val tgtType = tgtCU.primaryType.get()
                val tgtMethod = tgtType.getMethodsBySignature("join", "Object").single()

                val typeReachable = isTypeReachable(
                    reducer.context,
                    tgtType,
                    enableAssertions,
                    noCache = true
                )

                val methodDecision = decideForMethod(
                    reducer.context,
                    tgtMethod,
                    enableAssertions,
                    noCache = true
                )

                assertTrue(typeReachable)

                assertTrue {
                    tgtMethod.inclusionReasonsData.synchronizedWith { isNotEmpty() }
                }

                assertEquals(NodeTransformDecision.NO_OP, methodDecision)
                assertFalse(tgtMethod.isUnusedForRemovalData)
                assertFalse(tgtMethod.isUnusedForDummyData)
            }
        }
    }

    @Nested
    inner class CaptorAnnotationTest {

        @Nested
        inner class TestNormalUsage : OnTestCase("org.mockitousage.annotation.CaptorAnnotationTest::testNormalUsage", true) {

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

                // org.mockito.configuration.MockitoConfiguration is loaded by reflection
                val junitRunner = tryTestNoAssertion(outputDir!!.path, JAVA_17_HOME) {
                    add("-cp")
                    add("${project.testCpJars.joinToString(":")}:${sourceRoots.joinToString(":")}")

                    add("-m")
                    add(testCase.toJUnitMethod())
                }
                assertTrue(junitRunner.exitCode.isFailure)
                assertTrue {
                    junitRunner.stdout.joinToString("\n").contains("This method should not be reached! Signature: getAnnotationEngine()")
                }
            }

            /**
            * ```
            * test/org/mockitousage/annotation/CaptorAnnotationTest.java:33: error: cannot find symbol
            *     @NotAMock
            *      ^
            *   symbol:   class NotAMock
            *   location: class CaptorAnnotationTest
            * ```
            */
            @Test
            fun `Tag Annotation Class on Field Declaration`() {
                val cu = reducer.context
                    .getCUByPrimaryTypeName("org.mockitousage.annotation.CaptorAnnotationTest")
                assumeTrue(cu != null)

                val type = cu.primaryType.get().asClassOrInterfaceDeclaration()
                val srcFieldVar = type.getFieldVariableDecl("notAMock")
                assumeTrue {
                    decideForFieldDecl(
                        reducer.context,
                        srcFieldVar,
                        enableAssertions,
                        noCache = false
                    ) == NodeTransformDecision.NO_OP
                }

                val tgtType = type.getTypeByName<AnnotationDeclaration>("NotAMock")

                val typeReachable = isTypeReachable(
                    reducer.context,
                    tgtType,
                    enableAssertions,
                    noCache = true
                )

                assertTrue(typeReachable)
            }
        }
    }
}