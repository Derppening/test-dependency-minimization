package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.model.ExecutableDeclaration
import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.tool.compilation.CompilerProxy
import com.derppening.researchprojecttoolkit.tool.reducer.AbstractBaselineReducer.Companion.findMethodFromJacocoCoverage
import com.derppening.researchprojecttoolkit.tool.transform.TagGroundTruthUnusedDecls.Companion.decideForFieldDecl
import com.derppening.researchprojecttoolkit.tool.transform.TagGroundTruthUnusedDecls.Companion.isTypeReachable
import com.derppening.researchprojecttoolkit.util.findAll
import com.derppening.researchprojecttoolkit.util.getFieldVariableDecl
import com.github.javaparser.ast.Node
import org.junit.jupiter.api.Nested
import java.nio.file.Path
import kotlin.test.*

class CoverageBasedReducerJacksonDatabind1fTest : CoverageBasedReducerIntegTest() {

    override val project = TestProjects.JacksonDatabind1f

    @Nested
    inner class TestPOJOAsArray {

        @Nested
        inner class TestNullColumn {

            private val entrypoint = "com.fasterxml.jackson.databind.struct.TestPOJOAsArray::testNullColumn"

            @Nested
            inner class WithAssertions : OnTestCase(entrypoint, true) {

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

                    val junitRunner = tryTestNoAssertion(outputDir!!.path, JAVA_17_HOME) {
                        add("-cp")
                        add("${project.testCpJars.joinToString(":")}:${sourceRoots.joinToString(":")}")

                        add("-m")
                        add(testCase.toJUnitMethod())
                    }
                    assertTrue(junitRunner.exitCode.isFailure)
                }

                @Nested
                inner class ReductionRegressionTests {

                    @Test
                    fun `Regression-00`() {
                        val srcCU = reducer.context
                            .getCUByPrimaryTypeName("com.fasterxml.jackson.databind.node.ObjectNode")
                        assumeTrue(srcCU != null)
                        val srcType = srcCU.primaryType.get()
                        assumeTrue(srcType.inclusionReasonsData.isNotEmpty())

                        val tgtCU = reducer.context
                            .getCUByPrimaryTypeName("com.fasterxml.jackson.databind.node.ContainerNode")
                        assumeTrue(tgtCU != null)

                        val tgtType = tgtCU.primaryType.get()
                        assertTrue(tgtType.inclusionReasonsData.isNotEmpty())
                        assertFalse(tgtType.isUnusedForRemovalData)
                    }

                    @Test
                    fun `Regression-01`() {
                        val cu = reducer.context
                            .getCUByPrimaryTypeName("com.fasterxml.jackson.databind.ser.TestEmptyClass")
                        assumeTrue(cu != null)

                        val type = cu.primaryType.get()
                        val uncachedTypeDecision = isTypeReachable(reducer.context, type, enableAssertions, noCache = true)
                        val cachedTypeDecision = isTypeReachable(reducer.context, type, enableAssertions, noCache = false)
                        assumeTrue(cachedTypeDecision)
                        assumeTrue(uncachedTypeDecision)

                        val fieldVarDecl = type.getFieldVariableDecl("mapper")
                        val uncachedFieldDecision = decideForFieldDecl(
                            reducer.context,
                            fieldVarDecl,
                            enableAssertions,
                            noCache = true
                        )
                        val cachedFieldDecision = decideForFieldDecl(
                            reducer.context,
                            fieldVarDecl,
                            enableAssertions,
                            noCache = false
                        )

                        assertEquals(uncachedFieldDecision, cachedFieldDecision)
                    }
                }

                @Nested
                inner class SymbolLookupTests {

                    @Test
                    fun `Solve for Method in Nested Type with Nested Type Argument`() {
                        val coverageData = project.getBaselineDir(entrypointSpec)
                            .readJacocoBaselineCoverage()
                        assumeTrue(coverageData != null)

                        val packageCov = coverageData.report
                            .packages.single { it.name == "com/fasterxml/jackson/databind/introspect" }
                        val classCov = packageCov
                            .classes.single { it.name == "com/fasterxml/jackson/databind/introspect/VisibilityChecker\$Std" }
                        val methodCov = classCov.methods
                            .single { it.name == "with" && it.desc == "(Lcom/fasterxml/jackson/annotation/JsonAutoDetect\$Visibility;)Lcom/fasterxml/jackson/databind/introspect/VisibilityChecker\$Std;" }

                        val cu =
                            reducer.context.getCUByPrimaryTypeName("com.fasterxml.jackson.annotation.JsonAutoDetect")
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

                    @Test
                    fun `Solve for Constructor Not Present in AST`() {
                        val coverageData = project.getBaselineDir(entrypointSpec)
                            .readJacocoBaselineCoverage()
                        assumeTrue(coverageData != null)

                        val packageCov = coverageData.report
                            .packages.single { it.name == "com/fasterxml/jackson/databind" }
                        val classCov = packageCov
                            .classes.single { it.name == "com/fasterxml/jackson/databind/ObjectMapper\$1" }
                        val methodCov = classCov.methods
                            .single { it.name == "<init>" && it.desc == "(Lcom/fasterxml/jackson/databind/ObjectMapper;Lcom/fasterxml/jackson/databind/ObjectMapper;)V" }

                        val cu = reducer.context.getCUByPrimaryTypeName("com.fasterxml.jackson.databind.ObjectMapper")
                        assumeTrue(cu != null)

                        val matchedMethod = findMethodFromJacocoCoverage(
                            cu.findAll<Node> { ExecutableDeclaration.createOrNull(it) != null }
                                .map { ExecutableDeclaration.create(it) },
                            classCov,
                            methodCov,
                            reducer.context
                        )
                        assertNull(matchedMethod)
                    }
                }
            }
        }
    }
}