package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.tool.compilation.CompilerProxy
import com.derppening.researchprojecttoolkit.tool.transform.NodeTransformDecision
import com.derppening.researchprojecttoolkit.tool.transform.TagGroundTruthUnusedDecls.Companion.decideForEnumConstant
import com.derppening.researchprojecttoolkit.tool.transform.TagGroundTruthUnusedDecls.Companion.decideForFieldDecl
import com.derppening.researchprojecttoolkit.tool.transform.TagGroundTruthUnusedDecls.Companion.isTypeReachable
import com.derppening.researchprojecttoolkit.util.getFieldVariableDecl
import org.junit.jupiter.api.Nested
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoverageBasedReducerMockito25fTest : CoverageBasedReducerIntegTest() {

    override val project = TestProjects.Mockito25f

    @Nested
    inner class ReturnsGenericDeepStubsTest {

        @Nested
        inner class Will_return_default_value_on_non_mockable_nested_generic : OnTestCase("org.mockito.internal.stubbing.defaultanswers.ReturnsGenericDeepStubsTest::will_return_default_value_on_non_mockable_nested_generic", true) {

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

                val (compileSourceRoot, testSourceRoot) = (0..1).map { idx ->
                    sourceRootMapping.entries
                        .single { it.key.endsWith(project.sourceRoots[idx]) }
                        .value
                }

                val compileCompiler = CompilerProxy(
                    listOf(compileSourceRoot),
                    checkNotNull(project.compileCpJars).joinToString(":"),
                    emptyList(),
                    JAVA_8_HOME
                )
                assertCompileSuccess(compileCompiler)

                val testCompiler = CompilerProxy(
                    listOf(testSourceRoot),
                    buildList {
                        addAll(project.testCpJars)
                        add(compileSourceRoot)
                    }.joinToString(":"),
                    emptyList(),
                    JAVA_8_HOME
                )
                assertCompileSuccess(testCompiler)

                val sourceRoots = sourceRootMapping.values
                assertTestSuccess(outputDir!!.path, JAVA_8_HOME) {
                    add("-cp")
                    add("${project.testCpJars.joinToString(":")}:${sourceRoots.joinToString(":")}")

                    add("-m")
                    add(testCase.toJUnitMethod())
                }
            }

            @Test
            fun testExecutionJava17() {
                val sourceRootMapping = mutableMapOf<Path, Path>()
                reducer.getTransformedCompilationUnits(
                    outputDir!!.path,
                    project.getProjectResourcePath(""),
                    sourceRootMapping = sourceRootMapping
                )
                    .parallelStream()
                    .forEach { cu -> cu.storage.ifPresent { it.save() } }

                val (compileSourceRoot, testSourceRoot) = (0..1).map { idx ->
                    sourceRootMapping.entries
                        .single { it.key.endsWith(project.sourceRoots[idx]) }
                        .value
                }

                val compileCompiler = CompilerProxy(
                    listOf(compileSourceRoot),
                    checkNotNull(project.compileCpJars).joinToString(":"),
                    emptyList(),
                    JAVA_8_HOME
                )
                assertCompileSuccess(compileCompiler)

                val testCompiler = CompilerProxy(
                    listOf(testSourceRoot),
                    buildList {
                        addAll(project.testCpJars)
                        add(compileSourceRoot)
                    }.joinToString(":"),
                    emptyList(),
                    JAVA_8_HOME
                )
                assertCompileSuccess(testCompiler)

                val sourceRoots = sourceRootMapping.values
                val junitRunner = tryTestNoAssertion(outputDir!!.path, JAVA_17_HOME) {
                    add("-cp")
                    add("${project.testCpJars.joinToString(":")}:${sourceRoots.joinToString(":")}")

                    add("-m")
                    add(testCase.toJUnitMethod())
                }
                assertFalse(junitRunner.exitCode.isSuccess)
            }

            @Nested
            inner class ReductionRegressionTests {

                @Test
                fun `Include Annotation Member Default Value`() {
                    val srcCU = reducer.context
                        .getCUByPrimaryTypeName("org.mockito.Mock")
                    assumeTrue(srcCU != null)

                    val srcType = srcCU.primaryType.get()
                        .asAnnotationDeclaration()
                    assumeTrue {
                        isTypeReachable(
                            reducer.context,
                            srcType,
                            enableAssertions,
                            noCache = false
                        )
                    }
                    assumeTrue {
                        isTypeReachable(
                            reducer.context,
                            srcType,
                            enableAssertions,
                            noCache = true
                        )
                    }

                    val tgtCU = reducer.context
                        .getCUByPrimaryTypeName("org.mockito.Answers")
                    assumeTrue(tgtCU != null)

                    val tgtType = tgtCU.primaryType.get()
                        .asEnumDeclaration()
                    val tgtTypeReachableCached = isTypeReachable(
                        reducer.context,
                        tgtType,
                        enableAssertions,
                        noCache = false
                    )
                    val tgtTypeReachable = isTypeReachable(
                        reducer.context,
                        tgtType,
                        enableAssertions,
                        noCache = true
                    )

                    assertTrue(tgtTypeReachableCached)
                    assertTrue(tgtTypeReachable)
                    assertFalse(tgtType.isUnusedForRemovalData)
                    assertFalse(tgtType.isUnusedForDummyData)

                    val tgtEnumConst = tgtType.entries
                        .single { it.nameAsString == "RETURNS_DEFAULTS" }
                    val tgtEnumConstDecision = decideForEnumConstant(
                        reducer.context,
                        tgtEnumConst,
                        enableAssertions,
                        noCache = true
                    )
                    val tgtEnumConstDecisionCached = decideForEnumConstant(
                        reducer.context,
                        tgtEnumConst,
                        enableAssertions,
                        noCache = false
                    )

                    assertEquals(NodeTransformDecision.NO_OP, tgtEnumConstDecision)
                    assertEquals(NodeTransformDecision.NO_OP, tgtEnumConstDecisionCached)
                }

                @Test
                fun `Inconsistent Result for RETURNS_FIRST_ARGUMENT`() {
                    val tgtCU = reducer.context
                        .getCUByPrimaryTypeName("org.mockito.AdditionalAnswers")
                    assumeTrue(tgtCU != null)

                    val tgtType = tgtCU.primaryType.get()
                        .asClassOrInterfaceDeclaration()
                    val tgtTypeReachableCached = isTypeReachable(
                        reducer.context,
                        tgtType,
                        enableAssertions,
                        noCache = false
                    )
                    val tgtTypeReachable = isTypeReachable(
                        reducer.context,
                        tgtType,
                        enableAssertions,
                        noCache = true
                    )

                    assertFalse(tgtTypeReachableCached)
                    assertFalse(tgtTypeReachable)
                    assertTrue(tgtType.isUnusedForRemovalData)

                    val tgtFieldVar = tgtType.getFieldVariableDecl("RETURNS_FIRST_ARGUMENT")
                    val tgtFieldVarDecision = decideForFieldDecl(
                        reducer.context,
                        tgtFieldVar,
                        enableAssertions,
                        noCache = true
                    )
                    val tgtFieldVarCachedDecision = decideForFieldDecl(
                        reducer.context,
                        tgtFieldVar,
                        enableAssertions,
                        noCache = false
                    )

                    assertEquals(NodeTransformDecision.REMOVE, tgtFieldVarDecision)
                    assertEquals(NodeTransformDecision.REMOVE, tgtFieldVarCachedDecision)
                }

                /**
                 * ```
                 * src/org/mockito/Mockito.java:882: error: cannot find symbol
                 *     public static final Answer<Object> RETURNS_SMART_NULLS = Answers.RETURNS_SMART_NULLS.get();
                 *                                                                     ^
                 *   symbol:   variable RETURNS_SMART_NULLS
                 *   location: class Answers
                 * ```
                 */
                @Test
                fun `Include Enum Used as Scope`() {
                    val srcCU = reducer.context
                        .getCUByPrimaryTypeName("org.mockito.Mockito")
                    assumeTrue(srcCU != null)

                    val srcType = srcCU.primaryType.get()
                        .asClassOrInterfaceDeclaration()
                    assumeTrue {
                        isTypeReachable(
                            reducer.context,
                            srcType,
                            enableAssertions,
                            noCache = true
                        )
                    }

                    val srcFieldVar = srcType.getFieldVariableDecl("RETURNS_SMART_NULLS")
                    assumeTrue {
                        decideForFieldDecl(
                            reducer.context,
                            srcFieldVar,
                            enableAssertions,
                            noCache = true
                        ) != NodeTransformDecision.REMOVE
                    }

                    val tgtCU = reducer.context
                        .getCUByPrimaryTypeName("org.mockito.Answers")
                    assumeTrue(tgtCU != null)

                    val tgtType = tgtCU.primaryType.get()
                        .asEnumDeclaration()
                    val tgtTypeReachable = isTypeReachable(
                        reducer.context,
                        tgtType,
                        enableAssertions,
                        noCache = true
                    )

                    assertTrue(tgtTypeReachable)
                    assertFalse(tgtType.isUnusedForRemovalData)
                    assertFalse(tgtType.isUnusedForDummyData)

                    val tgtEnumConst = tgtType.entries
                        .single { it.nameAsString == "RETURNS_SMART_NULLS" }
                    val tgtEnumConstDecision = decideForEnumConstant(
                        reducer.context,
                        tgtEnumConst,
                        enableAssertions,
                        noCache = true
                    )

                    assertEquals(NodeTransformDecision.NO_OP, tgtEnumConstDecision)
                }
            }

            @Nested
            inner class SymbolLookupTests {
            }
        }
    }
}