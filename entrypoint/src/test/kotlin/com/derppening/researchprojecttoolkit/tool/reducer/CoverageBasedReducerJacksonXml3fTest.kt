package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.tool.compilation.CompilerProxy
import com.derppening.researchprojecttoolkit.tool.reachability.ReachableReason
import com.derppening.researchprojecttoolkit.util.NodeAstComparator
import com.derppening.researchprojecttoolkit.util.synchronizedWith
import com.github.javaparser.ast.Node
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.expr.AnnotationExpr
import org.junit.jupiter.api.Nested
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoverageBasedReducerJacksonXml3fTest : CoverageBasedReducerIntegTest() {

    override val project = TestProjects.JacksonXml3f

    @Nested
    inner class XmlParserNextXxxTest {

        @Nested
        inner class TestXmlAttributesWithNextTextValue {

            private val entrypoint = "com.fasterxml.jackson.dataformat.xml.stream.XmlParserNextXxxTest::testXmlAttributesWithNextTextValue"

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

                    assertTestSuccess(outputDir!!.path, JAVA_17_HOME) {
                        add("-cp")
                        add("${project.testCpJars.joinToString(":")}:${sourceRoots.joinToString(":")}")

                        add("-m")
                        add(testCase.toJUnitMethod())
                    }
                }

                @Nested
                inner class ReductionRegressionTests {

                    /**
                     * ```
                     * [javac] src/test/java/com/fasterxml/jackson/dataformat/xml/XmlTestBase.java:6: error: package com.fasterxml.jackson.dataformat.xml.annotation does not exist
                     * [javac] import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
                     * [javac]                                                       ^
                     * ```
                     */
                    @Test
                    fun `Fix Removal of Annotation Imports`() {
                        val cu = reducer.context
                            .getCUByPrimaryTypeName("com.fasterxml.jackson.dataformat.xml.XmlTestBase")
                        assumeTrue(cu != null)

                        val importDecl = cu.imports
                            .single {
                                it.nameAsString == "com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty" &&
                                        !it.isStatic &&
                                        !it.isAsterisk
                            }
                        assertEquals(1, importDecl.inclusionReasonsData.size)

                        val mappedSources = importDecl.inclusionReasonsData.synchronizedWith {
                            map { (it as ReachableReason.DirectlyReferencedByNode).node }
                        }

                        val annoExpr = cu.primaryType.get()
                            .members
                            .single {
                                it is ClassOrInterfaceDeclaration && it.nameAsString == "NameBean"
                            }
                            .asClassOrInterfaceDeclaration()
                            .getFieldByName("age").get()
                            .annotations
                            .single { it.nameAsString == "JacksonXmlProperty" }

                        assertEquals(0, NodeAstComparator.compare(mappedSources[0] as AnnotationExpr, annoExpr))

                        assertTrue((annoExpr as Node).isUnusedForRemovalData)

                        assertTrue(importDecl.isUnusedForRemovalData)
                    }
                }

                @Nested
                inner class SymbolLookupTests {
                }
            }
        }
    }
}