package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.model.ExecutableDeclaration
import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.tool.compilation.CompilerProxy
import com.derppening.researchprojecttoolkit.tool.reducer.AbstractBaselineReducer.Companion.findMethodFromJacocoCoverage
import com.derppening.researchprojecttoolkit.tool.transform.NodeTransformDecision
import com.derppening.researchprojecttoolkit.tool.transform.TagGroundTruthUnusedDecls.Companion.decideForMethod
import com.derppening.researchprojecttoolkit.tool.transform.TagGroundTruthUnusedDecls.Companion.isTransitiveDependentReachable
import com.derppening.researchprojecttoolkit.util.findAll
import com.github.javaparser.ast.Node
import org.junit.jupiter.api.Nested
import java.nio.file.Path
import kotlin.test.*

class CoverageBasedReducerLang18fTest : CoverageBasedReducerIntegTest() {

    override val project = TestProjects.Lang18f

    @Nested
    inner class FastDateFormatTest {

        @Nested
        inner class TestFormat : OnTestCase("org.apache.commons.lang3.time.FastDateFormatTest::testFormat", true) {

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
                assertFalse(junitRunner.exitCode.isSuccess)
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

            @Nested
            inner class SymbolLookupTests {

                @Test
                fun `Find Method in Anonymous Class`() {
                    val coverageData = project.getBaselineDir(entrypointSpec)
                        .readJacocoBaselineCoverage()
                    assumeTrue(coverageData != null)

                    val packageCov = coverageData.report
                        .packages.single { it.name == "org/apache/commons/lang3/time" }
                    val classCov = packageCov
                        .classes.single { it.name == "org/apache/commons/lang3/time/FastDateFormat\$1" }
                    val methodCov = classCov.methods
                        .single { it.name == "createInstance" && it.desc == "(Ljava/lang/String;Ljava/util/TimeZone;Ljava/util/Locale;)Lorg/apache/commons/lang3/time/FastDateFormat;" }

                    val cu = reducer.context.getCUByPrimaryTypeName("org.apache.commons.lang3.time.FastDateFormat")
                    assumeTrue(cu != null)

                    val matchedMethod = findMethodFromJacocoCoverage(
                        cu.findAll<Node> { ExecutableDeclaration.createOrNull(it) != null }
                            .map { ExecutableDeclaration.create(it) },
                        classCov,
                        methodCov,
                        reducer.context
                    )
                    assertNotNull(matchedMethod)
                }
            }

            @Nested
            inner class ReductionRegressionTests {

                @Test
                fun `Exclude Methods Overriding Non-Abstract Library Method`() {
                    val cu = reducer.context
                        .getCUByPrimaryTypeName("org.apache.commons.lang3.mutable.MutableByte")
                    assumeTrue(cu != null)

                    val type = cu.primaryType.get()

                    val hashCodeMethod = type.getMethodsBySignature("hashCode").single()

                    assertFalse {
                        isTransitiveDependentReachable(
                            reducer.context,
                            hashCodeMethod,
                            enableAssertions,
                            noCache = true
                        )
                    }

                    val equalsMethod = type.getMethodsBySignature("equals", "Object").single()

                    assertFalse {
                        isTransitiveDependentReachable(
                            reducer.context,
                            equalsMethod,
                            enableAssertions,
                            noCache = true
                        )
                    }

                    val hashCodeDecision = decideForMethod(
                        reducer.context,
                        hashCodeMethod,
                        enableAssertions,
                        noCache = true
                    )
                    assertEquals(NodeTransformDecision.REMOVE, hashCodeDecision)

                    assertTrue(hashCodeMethod.isUnusedForRemovalData)

                    val equalsDecision = decideForMethod(
                        reducer.context,
                        equalsMethod,
                        enableAssertions,
                        noCache = true
                    )
                    assertEquals(NodeTransformDecision.REMOVE, equalsDecision)

                    assertTrue(equalsMethod.isUnusedForRemovalData)
                }
            }
        }
    }
}