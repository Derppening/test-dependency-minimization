package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.tool.compilation.CompilerProxy
import com.derppening.researchprojecttoolkit.tool.transform.TagStaticAnalysisMemberUnusedDecls.Companion.decideForMethod
import org.junit.jupiter.api.Nested
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StaticAnalysisMemberReducerMockito17fTest : StaticAnalysisMemberReducerIntegTest() {

    override val project = TestProjects.Mockito17f

    @Nested
    inner class MocksSerializationTest {

        @Nested
        inner class ShouldBeSerializeAndHaveExtraInterfaces : OnTestCase("org.mockitousage.basicapi.MocksSerializationTest::shouldBeSerializeAndHaveExtraInterfaces", true) {

            @Test
            fun testExecution() {
                val sourceRootMapping = mutableMapOf<Path, Path>()
                reducer.getTransformedCompilationUnits(
                    outputDir!!.path,
                    project.getProjectResourcePath(),
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
            fun `Consistency of Determining Supertype Method`() {
                val supertypeCU = reducer.context
                    .getCUByPrimaryTypeName("org.mockito.configuration.AnnotationEngine")
                assumeTrue(supertypeCU != null)
                val supertype = supertypeCU.primaryType.get().asClassOrInterfaceDeclaration()

                val supertypeMethod = supertype.getMethodsBySignature("createMockFor", "Annotation", "Field").single()

                val noCacheSupertypeMethodDecision = decideForMethod(
                    reducer.context,
                    supertypeMethod,
                    enableAssertions,
                    noCache = true
                )
                val cacheSupertypeMethodDecision = decideForMethod(
                    reducer.context,
                    supertypeMethod,
                    enableAssertions,
                    noCache = false
                )

                assertEquals(noCacheSupertypeMethodDecision, cacheSupertypeMethodDecision)

                val cu = reducer.context
                    .getCUByPrimaryTypeName("org.mockito.internal.configuration.DefaultAnnotationEngine")
                assumeTrue(cu != null)
                val type = cu.primaryType.get().asClassOrInterfaceDeclaration()

                val method = type.getMethodsBySignature("createMockFor", "Annotation", "Field").single()

                val noCacheMethodDecision = decideForMethod(
                    reducer.context,
                    method,
                    enableAssertions,
                    noCache = true
                )
                val cacheMethodDecision = decideForMethod(
                    reducer.context,
                    method,
                    enableAssertions,
                    noCache = false
                )

                assertEquals(noCacheMethodDecision, cacheMethodDecision)
            }
        }
    }
}