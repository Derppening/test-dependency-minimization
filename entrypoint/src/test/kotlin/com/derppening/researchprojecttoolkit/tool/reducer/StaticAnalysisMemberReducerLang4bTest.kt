package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.tool.compilation.CompilerProxy
import com.derppening.researchprojecttoolkit.tool.transform.NodeTransformDecision
import com.derppening.researchprojecttoolkit.tool.transform.TagStaticAnalysisMemberUnusedDecls.Companion.decideForMethod
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import org.junit.jupiter.api.Nested
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class StaticAnalysisMemberReducerLang4bTest : StaticAnalysisMemberReducerIntegTest() {

    override val project = TestProjects.Lang4b

    @Nested
    inner class TypeUtilsTest {

        @Nested
        inner class TestIsAssignable : OnTestCase("org.apache.commons.lang3.text.translate.LookupTranslatorTest::testLang882", true) {

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
                    emptyList(),
                    JAVA_8_HOME
                )
                assertCompileSuccess(compiler)

                val junitRunner = tryTestNoAssertion(outputDir!!.path, JAVA_8_HOME) {
                    add("-cp")
                    add("${project.testCpJars.joinToString(":")}:${sourceRoots.joinToString(":")}")

                    add("-m")
                    add(testCase.toJUnitMethod())
                }
                // Expected Fail: Buggy version
                assertFalse(junitRunner.exitCode.isSuccess)
            }

            @Test
            fun `Regression-00`() {
                val srcCU = reducer.context
                    .getCUByPrimaryTypeName("org.apache.commons.lang3.text.translate.LookupTranslator")
                assumeTrue(srcCU != null)

                val tgtCU = reducer.context
                    .getCUByPrimaryTypeName("org.apache.commons.lang3.ObjectUtilsTest")
                assumeTrue(tgtCU != null)

                val tgtType = tgtCU.primaryType.get()
                    .members
                    .single { it is ClassOrInterfaceDeclaration && it.nameAsString == "NonComparableCharSequence" }
                    .asClassOrInterfaceDeclaration()
                val tgtMethod = tgtType.getMethodsBySignature("length").single()

                val tgtMethodDecisionCached = decideForMethod(
                    reducer.context,
                    tgtMethod,
                    enableAssertions,
                    noCache = false
                )
                assertEquals(NodeTransformDecision.REMOVE, tgtMethodDecisionCached)
                val tgtMethodDecision = decideForMethod(
                    reducer.context,
                    tgtMethod,
                    enableAssertions,
                    noCache = true
                )
                assertEquals(NodeTransformDecision.REMOVE, tgtMethodDecision)
            }
        }
    }
}