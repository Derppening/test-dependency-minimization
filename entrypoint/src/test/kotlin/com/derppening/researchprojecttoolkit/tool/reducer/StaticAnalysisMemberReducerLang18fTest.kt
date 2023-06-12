package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.tool.compilation.CompilerProxy
import com.derppening.researchprojecttoolkit.tool.transform.NodeTransformDecision
import com.derppening.researchprojecttoolkit.tool.transform.TagStaticAnalysisMemberUnusedDecls.Companion.decideForMethod
import com.derppening.researchprojecttoolkit.tool.transform.TagStaticAnalysisMemberUnusedDecls.Companion.isTypeReachable
import com.derppening.researchprojecttoolkit.util.resolveDeclaration
import com.derppening.researchprojecttoolkit.util.synchronizedWith
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration
import org.junit.jupiter.api.Nested
import java.nio.file.Path
import kotlin.jvm.optionals.getOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StaticAnalysisMemberReducerLang18fTest : StaticAnalysisMemberReducerIntegTest() {

    override val project = TestProjects.Lang18f

    @Nested
    inner class FastDateFormatTest {

        @Nested
        inner class TestFormat : OnTestCase("org.apache.commons.lang3.time.FastDateFormatTest::testFormat", true) {

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

                assertTestSuccess(outputDir!!.path, JAVA_17_HOME) {
                    add("-cp")
                    add("${project.testCpJars.joinToString(":")}:${sourceRoots.joinToString(":")}")

                    add("-m")
                    add(testCase.toJUnitMethod())
                }
            }

            /**
            * ```
            * java.lang.AssertionError: This method should not be reached! Signature: appendTo(StringBuffer, Calendar)
            * 	at org.apache.commons.lang3.time.FastDateFormat$PaddedNumberField.appendTo(FastDateFormat.java:860)
            * 	at org.apache.commons.lang3.time.FastDateFormat.applyRules(FastDateFormat.java:533)
            * 	at org.apache.commons.lang3.time.FastDateFormat.format(FastDateFormat.java:470)
            * 	at org.apache.commons.lang3.time.FastDateFormatTest.testFormat(FastDateFormatTest.java:52)
            * ```
            */
            @Test
            fun `Regression-00`() {
                val cu = reducer.context
                    .getCUByPrimaryTypeName("org.apache.commons.lang3.time.FastDateFormat")
                assumeTrue(cu != null)

                val srcType = cu.primaryType.get()
                val srcMethod = srcType
                    .getMethodsBySignature("applyRules", "Calendar", "StringBuffer").single()
                assumeTrue {
                    decideForMethod(
                        reducer.context,
                        srcMethod,
                        enableAssertions,
                        noCache = false
                    ) == NodeTransformDecision.NO_OP
                }

                val resolvedMethodDecl = cu.primaryType.get()
                    .members
                    .single { it is ClassOrInterfaceDeclaration && it.nameAsString == "Rule" }
                    .asClassOrInterfaceDeclaration()
                    .getMethodsBySignature("appendTo", "StringBuffer", "Calendar").single()
                    .let { reducer.context.symbolSolver.resolveDeclaration<ResolvedMethodDeclaration>(it) }
                val callExpr = srcMethod
                    .body.get()
                    .statements[0].asForEachStmt()
                    .body.asBlockStmt()
                    .statements[0].asExpressionStmt()
                    .expression.asMethodCallExpr()
                val inferredTypes = reducer.inferDynamicMethodCallTypes(
                    resolvedMethodDecl,
                    callExpr,
                    callExpr.scope.getOrNull()
                )
                assertTrue { inferredTypes.isNotEmpty() }

                val tgtType = cu.primaryType.get()
                    .members
                    .single { it is ClassOrInterfaceDeclaration && it.nameAsString == "PaddedNumberField" }
                    .asClassOrInterfaceDeclaration()

                val typeReachable = isTypeReachable(
                    reducer.context,
                    tgtType,
                    enableAssertions,
                    noCache = true
                )
                assertTrue(typeReachable)

                val tgtMethod = tgtType.getMethodsBySignature("appendTo", "StringBuffer", "Calendar").single()
                assertTrue {
                    tgtMethod.inclusionReasonsData.synchronizedWith { isNotEmpty() }
                }

                val methodDecision = decideForMethod(
                    reducer.context,
                    tgtMethod,
                    enableAssertions,
                    noCache = true
                )
                assertEquals(NodeTransformDecision.NO_OP, methodDecision)
                assertFalse(tgtMethod.isUnusedForRemovalData)
                assertFalse(tgtMethod.isUnusedForDummyData)
            }
        }
    }
}