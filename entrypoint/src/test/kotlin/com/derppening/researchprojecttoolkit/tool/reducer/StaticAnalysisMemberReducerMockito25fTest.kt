package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.tool.compilation.CompilerProxy
import com.derppening.researchprojecttoolkit.tool.transform.NodeTransformDecision
import com.derppening.researchprojecttoolkit.tool.transform.TagStaticAnalysisMemberUnusedDecls.Companion.decideForFieldDecl
import com.derppening.researchprojecttoolkit.tool.transform.TagStaticAnalysisMemberUnusedDecls.Companion.decideForMethod
import com.derppening.researchprojecttoolkit.tool.transform.TagStaticAnalysisMemberUnusedDecls.Companion.isTypeReachable
import com.derppening.researchprojecttoolkit.util.getFieldVariableDecl
import com.derppening.researchprojecttoolkit.util.getTypeByName
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import org.junit.jupiter.api.Nested
import java.nio.file.Path
import kotlin.test.*

class StaticAnalysisMemberReducerMockito25fTest : StaticAnalysisMemberReducerIntegTest() {

    override val project = TestProjects.Mockito25f

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

            @Test
            fun `Regression-00`() {
                val cu = reducer.context
                    .getCUByPrimaryTypeName("org.mockito.internal.matchers.apachecommons.EqualsBuilderTest")
                assumeTrue(cu != null)

                val type = cu.primaryType.get()
                    .members.single { it is ClassOrInterfaceDeclaration && it.nameAsString == "TestTSubObject2" }
                    .asClassOrInterfaceDeclaration()
                val fieldVar = type.getFieldVariableDecl("t")

                val typeDecision = isTypeReachable(
                    reducer.context,
                    type,
                    enableAssertions,
                    noCache = true
                )
                assertFalse(typeDecision)

                val fieldDecision = decideForFieldDecl(
                    reducer.context,
                    fieldVar,
                    enableAssertions,
                    noCache = true
                )
                assertEquals(NodeTransformDecision.REMOVE, fieldDecision)
            }

            /**
             * ```
             *     => java.lang.AssertionError: This method should not be reached! Signature: getAnnotationEngine()
             *        org.mockito.configuration.MockitoConfiguration.getAnnotationEngine(MockitoConfiguration.java:34)
             *        org.mockito.internal.configuration.GlobalConfiguration.getAnnotationEngine(GlobalConfiguration.java:51)
             *        org.mockito.MockitoAnnotations.initMocks(MockitoAnnotations.java:90)
             *        org.mockitoutil.TestBase.init(TestBase.java:30)
             *        java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
             *        java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
             *        java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
             *        java.base/java.lang.reflect.Method.invoke(Method.java:568)
             *        org.junit.runners.model.FrameworkMethod$1.runReflectiveCall(FrameworkMethod.java:59)
             *        org.junit.internal.runners.model.ReflectiveCallable.run(ReflectiveCallable.java:12)
             *        [...]
             * ```
             */
            @Test
            fun `Regression-01`() {
                val srcCU = reducer.context
                    .getCUByPrimaryTypeName("org.mockito.internal.configuration.GlobalConfiguration")
                assumeTrue(srcCU != null)
                val srcType = srcCU.primaryType.get().asClassOrInterfaceDeclaration()
                val srcMethod = srcType.getMethodsBySignature("getAnnotationEngine").single()
                assumeTrue {
                    decideForMethod(
                        reducer.context,
                        srcMethod,
                        enableAssertions,
                        noCache = true
                    ) == NodeTransformDecision.NO_OP
                }
                assumeTrue {
                    decideForMethod(
                        reducer.context,
                        srcMethod,
                        enableAssertions,
                        noCache = false
                    ) == NodeTransformDecision.NO_OP
                }

                val tgtCU = reducer.context
                    .getCUByPrimaryTypeName("org.mockito.configuration.MockitoConfiguration")
                assumeTrue(tgtCU != null)
                val tgtType = tgtCU.primaryType.get()
                val tgtMethod = tgtType.getMethodsBySignature("getAnnotationEngine").single()
                assertTrue {
                    decideForMethod(
                        reducer.context,
                        tgtMethod,
                        enableAssertions,
                        noCache = true
                    ) == NodeTransformDecision.DUMMY
                }
                assertTrue {
                    decideForMethod(
                        reducer.context,
                        tgtMethod,
                        enableAssertions,
                        noCache = false
                    ) == NodeTransformDecision.DUMMY
                }
            }

            /**
             * ```
             * src/org/mockito/internal/configuration/injection/PropertyAndSetterInjection.java:61: error: <anonymous org.mockito.internal.configuration.injection.PropertyAndSetterInjection$1> is not abstract and does not override abstract method isOut(Field) in Filter
             *     private ListUtil.Filter<Field> notFinalOrStatic = new ListUtil.Filter<Field>() {
             *                                                                                    ^
             * ```
             */
            @Test
            fun `Regression-02`() {
                val srcCU = reducer.context
                    .getCUByPrimaryTypeName("org.mockito.internal.util.collections.ListUtil")
                assumeTrue(srcCU != null)
                val srcType = srcCU.primaryType.get().asClassOrInterfaceDeclaration()
                    .getTypeByName<ClassOrInterfaceDeclaration>("Filter")
                val srcMethod = srcType.getMethodsBySignature("isOut", "T").single()
                assumeTrue {
                    decideForMethod(
                        reducer.context,
                        srcMethod,
                        enableAssertions,
                        noCache = true
                    ) != NodeTransformDecision.REMOVE
                }

                val tgtCU = reducer.context
                    .getCUByPrimaryTypeName("org.mockito.internal.configuration.injection.PropertyAndSetterInjection")
                assumeTrue(tgtCU != null)
                val tgtType = tgtCU.primaryType.get()
                val tgtMethod = tgtType.getFieldVariableDecl("notFinalOrStatic")
                    .initializer.get().asObjectCreationExpr()
                    .anonymousClassBody.get()
                    .single {
                        it is MethodDeclaration &&
                                it.nameAsString == "isOut" &&
                                it.parameters.size == 1 &&
                                it.getParameter(0).typeAsString == "Field"
                    }.asMethodDeclaration()

                val methodDecision = decideForMethod(
                    reducer.context,
                    tgtMethod,
                    enableAssertions,
                    noCache = true
                )
                assertNotEquals(NodeTransformDecision.REMOVE, methodDecision)
            }
        }
    }
}