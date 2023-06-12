package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.model.ReferenceTypeLikeDeclaration
import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.tool.compilation.CompilerProxy
import com.derppening.researchprojecttoolkit.tool.reducer.AbstractBaselineReducer.Companion.isUnusedInCoverage
import com.derppening.researchprojecttoolkit.tool.transform.NodeTransformDecision
import com.derppening.researchprojecttoolkit.tool.transform.TagGroundTruthUnusedDecls.Companion.decideForFieldDecl
import com.derppening.researchprojecttoolkit.tool.transform.TagGroundTruthUnusedDecls.Companion.decideForMethod
import com.derppening.researchprojecttoolkit.tool.transform.TagGroundTruthUnusedDecls.Companion.isTypeReachable
import com.derppening.researchprojecttoolkit.util.getFieldVariableDecl
import com.derppening.researchprojecttoolkit.util.synchronizedWith
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.EnumDeclaration
import com.github.javaparser.ast.body.InitializerDeclaration
import org.junit.jupiter.api.Nested
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CoverageBasedReducerJacksonDatabind8fTest : CoverageBasedReducerIntegTest() {

    override val project = TestProjects.JacksonDatabind8f

    @Nested
    inner class TestJdkTypes {

        @Nested
        inner class TestStringBuilder {

            private val entrypoint = "com.fasterxml.jackson.databind.deser.TestJdkTypes::testStringBuilder"

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

                    val srcRootOutputDirs = project.cpSourceRoots.map {
                        val srcRootOutputDir = outputDir!!.path.resolve(it.fileName)

                        it.toFile().copyRecursively(srcRootOutputDir.toFile())

                        srcRootOutputDir
                    }

                    val sourceRoots = sourceRootMapping.values.toList() + srcRootOutputDirs
                    val compiler = CompilerProxy(
                        sourceRoots,
                        project.testCpJars.joinToString(":"),
                        listOf("-Xmaxerrs", "$JAVAC_MAXERRS"),
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
                inner class ReductionRegressionTests {

                    @Test
                    fun `Include Annotation Members`() {
                        val cu = reducer.context
                            .getCUByPrimaryTypeName("com.fasterxml.jackson.databind.annotation.JsonSerialize")
                        assumeTrue(cu != null)

                        val srcType = cu.primaryType.get()
                        assumeTrue(srcType.inclusionReasonsData.isNotEmpty())
                        assumeTrue {
                            isTypeReachable(
                                reducer.context,
                                srcType,
                                enableAssertions,
                                noCache = true
                            )
                        }

                        val tgtType = srcType.members
                            .single { it is EnumDeclaration && it.nameAsString == "Typing" }
                            .asEnumDeclaration()
                        assertTrue {
                            isTypeReachable(
                                reducer.context,
                                tgtType,
                                enableAssertions,
                                noCache = true
                            )
                        }
                    }

                    @Test
                    fun `Include Annotation Expressions in Classes`() {
                        val srcCU = reducer.context
                            .getCUByPrimaryTypeName("com.fasterxml.jackson.databind.ser.std.ToStringSerializer")
                        assumeTrue(srcCU != null)

                        val srcType = srcCU.primaryType.get()
                        assumeTrue(srcType.inclusionReasonsData.isNotEmpty())
                        assumeTrue {
                            isTypeReachable(
                                reducer.context,
                                srcType,
                                enableAssertions,
                                noCache = true
                            )
                        }

                        val tgtCU = reducer.context
                            .getCUByPrimaryTypeName("com.fasterxml.jackson.databind.annotation.JacksonStdImpl")
                        assumeTrue(tgtCU != null)

                        val tgtType = tgtCU.primaryType.get()
                        assertTrue(tgtType.inclusionReasonsData.isNotEmpty())
                        assertTrue {
                            isTypeReachable(
                                reducer.context,
                                tgtType,
                                enableAssertions,
                                noCache = true
                            )
                        }
                    }

                    @Test
                    fun `Include Annotation Expressions in Fields`() {
                        val cu = reducer.context
                            .getCUByPrimaryTypeName("com.fasterxml.jackson.databind.struct.JSOGDeserialize622Test")
                        assumeTrue(cu != null)

                        val srcType = cu.primaryType.get()
                            .members
                            .single { it is ClassOrInterfaceDeclaration && it.nameAsString == "JSOGRef" }
                            .asClassOrInterfaceDeclaration()
                        assumeTrue(srcType.inclusionReasonsData.isNotEmpty())
                        assumeTrue {
                            isTypeReachable(
                                reducer.context,
                                srcType,
                                enableAssertions,
                                noCache = true
                            )
                        }

                        val tgtField = cu.primaryType.get()
                            .getFieldByName("REF_KEY").get()
                            .getFieldVariableDecl("REF_KEY")
                        assertTrue(tgtField.inclusionReasonsData.isNotEmpty())
                        assertNotEquals(
                            NodeTransformDecision.REMOVE,
                            decideForFieldDecl(
                                reducer.context,
                                tgtField,
                                enableAssertions,
                                noCache = true
                            )
                        )
                    }

                    @Test
                    fun `Find Type in Scope Expression of Class Expression`() {
                        val srcCU = reducer.context
                            .getCUByPrimaryTypeName("com.fasterxml.jackson.databind.ser.BasicSerializerFactory")
                        assumeTrue(srcCU != null)

                        val srcType = srcCU.primaryType.get()
                        val methodCall = srcType
                            .members
                            .filterIsInstance<InitializerDeclaration>()
                            .first { it.isStatic }
                            .body
                            .statements[15].asExpressionStmt()
                            .expression.asMethodCallExpr()

                        assumeFalse {
                            methodCall.isUnusedInCoverage(
                                ReferenceTypeLikeDeclaration.create(srcType).coberturaCoverageData?.lines,
                                srcCU.jacocoCoverageData?.first?.lines,
                                false
                            )
                        }
                        assumeFalse(methodCall.isUnusedForRemovalData)
                        assumeFalse(methodCall.isUnusedForDummyData)

                        val tgtCU = reducer.context
                            .getCUByPrimaryTypeName("com.fasterxml.jackson.databind.ser.std.SqlDateSerializer")
                        assumeTrue(tgtCU != null)

                        val tgtType = tgtCU.primaryType.get()
                        assertTrue(tgtType.inclusionReasonsData.isNotEmpty())
                        assertTrue {
                            isTypeReachable(
                                reducer.context,
                                tgtType,
                                enableAssertions,
                                noCache = true
                            )
                        }
                    }

                    @Test
                    fun `Transitively Make Method Decision`() {
                        val superclassCU = reducer.context
                            .getCUByPrimaryTypeName("com.fasterxml.jackson.databind.node.ContainerNode")
                        assumeTrue(superclassCU != null)

                        val superclassType = superclassCU.primaryType.get()
                        val superclassMethod = superclassType
                            .getMethodsBySignature("get", "int").single()

                        val superclassDecision = decideForMethod(
                            reducer.context,
                            superclassMethod,
                            enableAssertions,
                            noCache = true
                        )

                        assumeTrue {
                            superclassMethod.inclusionReasonsData.synchronizedWith { isNotEmpty() }
                        }
                        assumeTrue(superclassDecision != NodeTransformDecision.REMOVE)
                        assumeFalse(superclassMethod.isUnusedForRemovalData)

                        val thisCU = reducer.context
                            .getCUByPrimaryTypeName("com.fasterxml.jackson.databind.node.ObjectNode")
                        assumeTrue(thisCU != null)

                        val thisType = thisCU.primaryType.get()
                        val thisMethod = thisType
                            .getMethodsBySignature("get", "int").single()

                        val decision = decideForMethod(
                            reducer.context,
                            thisMethod,
                            enableAssertions,
                            noCache = true
                        )
                        assertNotEquals(NodeTransformDecision.REMOVE, decision)
                    }
                }

                @Nested
                inner class SymbolLookupTests {
                }
            }
        }
    }
}