package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.model.ReferenceTypeLikeDeclaration
import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.tool.compilation.CompilerProxy
import com.derppening.researchprojecttoolkit.tool.reducer.AbstractBaselineReducer.Companion.findClassFromJacocoCoverage
import com.derppening.researchprojecttoolkit.util.findAll
import com.github.javaparser.ast.Node
import org.junit.jupiter.api.Nested
import java.nio.file.Path
import kotlin.test.*

class CoverageBasedReducerJacksonDatabind5fTest : CoverageBasedReducerIntegTest() {

    override val project = TestProjects.JacksonDatabind5f

    @Nested
    inner class TestMixinMerging {

        @Nested
        inner class TestDisappearingMixins515 : OnTestCase("com.fasterxml.jackson.databind.introspect.TestMixinMerging::testDisappearingMixins515", true) {

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
                assertTrue(junitRunner.exitCode.isFailure)
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
                tryCompileNoAssertion(compiler)
                assertFalse(compiler.exitCode.isSuccess)
            }

            @Nested
            inner class SymbolLookupTests {

                @Test
                fun `Solve for Anon Class with No AST Token in First Coverage Line`() {
                    val coverageData = project.getBaselineDir(entrypointSpec)
                        .readJacocoBaselineCoverage()
                    assumeTrue(coverageData != null)

                    val classCov = coverageData.report
                        .packages.single { it.name == "com/fasterxml/jackson/databind" }
                        .classes.single { it.name == "com/fasterxml/jackson/databind/ObjectMapper\$1" }

                    val cu = reducer.context.getCUByPrimaryTypeName("com.fasterxml.jackson.databind.ObjectMapper")
                    assumeTrue(cu != null)

                    val allRefLikeTypes = cu
                        .findAll<Node> { ReferenceTypeLikeDeclaration.createOrNull(it) != null }
                        .map { ReferenceTypeLikeDeclaration.create(it) }

                    val expectedAnonClass = cu.primaryType.get()
                        .getMethodsBySignature("registerModule", "Module").single()
                        .body.get()
                        .statements[5].asExpressionStmt()
                        .expression.asMethodCallExpr()
                        .arguments[0].asObjectCreationExpr()

                    val foundClass = findClassFromJacocoCoverage(allRefLikeTypes, classCov)
                    assertIs<ReferenceTypeLikeDeclaration.AnonClassDecl>(foundClass)
                    assertEquals(expectedAnonClass, foundClass.node)
                }
            }
        }
    }
}