package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.model.ExecutableDeclaration
import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.tool.compilation.CompilerProxy
import com.derppening.researchprojecttoolkit.tool.reducer.AbstractBaselineReducer.Companion.findMethodFromJacocoCoverage
import com.derppening.researchprojecttoolkit.util.findAll
import com.github.javaparser.ast.Node
import com.github.javaparser.ast.type.PrimitiveType
import org.junit.jupiter.api.Nested
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class CoverageBasedReducerLang20fTest : CoverageBasedReducerIntegTest() {

    override val project = TestProjects.Lang20f

    @Nested
    inner class StringUtilsTest {

        @Nested
        inner class TestJoin_ArrayChar : OnTestCase("org.apache.commons.lang3.StringUtilsTest::testJoin_ArrayChar", true) {

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

            @Nested
            inner class SymbolLookupTests {

                @Test
                fun `Find Generic Override`() {
                    val coverageData = project.getBaselineDir(entrypointSpec)
                        .readJacocoBaselineCoverage()
                    assumeTrue(coverageData != null)

                    val packageCov = coverageData.report
                        .packages.single { it.name == "org/apache/commons/lang3/mutable" }
                    val classCov = packageCov
                        .classes.single { it.name == "org/apache/commons/lang3/mutable/MutableByte" }
                    val methodCov = classCov.methods
                        .single { it.name == "compareTo" && it.desc == "(Lorg/apache/commons/lang3/mutable/MutableByte;)I" }

                    val cu = reducer.context.getCUByPrimaryTypeName("org.apache.commons.lang3.mutable.MutableByte")
                    assumeTrue(cu != null)

                    val matchedMethod = findMethodFromJacocoCoverage(
                        cu.findAll<Node> { ExecutableDeclaration.createOrNull(it) != null }
                            .map { ExecutableDeclaration.create(it) },
                        classCov,
                        methodCov,
                        reducer.context
                    )
                    assertNotNull(matchedMethod)
                    assertIs<ExecutableDeclaration.MethodDecl>(matchedMethod)
                    assertEquals("compareTo", matchedMethod.name)
                    assertEquals(1, matchedMethod.node.parameters.size)
                    val param0Type = matchedMethod.node.parameters[0].type
                    assertEquals("MutableByte", param0Type.asString())
                    val returnType = assertIs<PrimitiveType>(matchedMethod.node.type)
                    assertEquals(PrimitiveType.intType(), returnType)
                }
            }
        }
    }
}