package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.tool.compilation.CompilerProxy
import com.derppening.researchprojecttoolkit.tool.transform.NodeTransformDecision
import com.derppening.researchprojecttoolkit.tool.transform.TagStaticAnalysisMemberUnusedDecls.Companion.decideForFieldDecl
import com.derppening.researchprojecttoolkit.tool.transform.TagStaticAnalysisMemberUnusedDecls.Companion.isTypeReachable
import com.derppening.researchprojecttoolkit.util.getFieldVariableDecl
import com.derppening.researchprojecttoolkit.util.getTypeByName
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import org.junit.jupiter.api.Nested
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class StaticAnalysisMemberReducerClosure168b_230530_Pass0Test : StaticAnalysisMemberReducerIntegTest() {

    override val project = TestProjects.Closure168b_230530_Pass0

    @Nested
    inner class TypeCheckTest {

        @Nested
        inner class TestIssue726 : OnTestCase("com.google.javascript.jscomp.TypeCheckTest::testIssue726", true) {

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
                    JAVA_8_HOME
                )

                assertCompileSuccess(compiler)

                project.copyProjectFile(
                    Path("build/classes/rhino_ast/java/com/google/javascript/rhino/Messages.properties"),
                    outputDir!!.path
                )
                project.copyProjectFile(
                    Path("src/com/google/javascript/jscomp/parsing/ParserConfig.properties"),
                    outputDir!!.path
                )

                val junitRunner = tryTestNoAssertion(outputDir!!.path, JAVA_8_HOME) {
                    add("-cp")
                    add("${project.testCpJars.joinToString(":")}:${sourceRoots.joinToString(":")}:build/classes")

                    add("-m")
                    add(testCase.toJUnitMethod())
                }
                assertTrue(junitRunner.exitCode.isFailure)
                assertTrue(junitRunner.stdout.joinToString("\n").contains("junit.framework.AssertionFailedError"))
            }

            @Test
            fun `Include Type Arguments of Supertypes`() {
                val cu = reducer.context
                    .getCUByPrimaryTypeName("com.google.javascript.jscomp.ReferenceCollectingCallback")
                assumeTrue(cu != null)

                val srcType = cu.primaryType.get().asClassOrInterfaceDeclaration()
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

                val tgtType = srcType.getTypeByName<ClassOrInterfaceDeclaration>("BasicBlock")
                assertTrue {
                    isTypeReachable(
                        reducer.context,
                        tgtType,
                        enableAssertions,
                        noCache = false
                    )
                }
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
            fun `Break Dependency Cycle for Nested Type in Variable Type`() {
                val cu = reducer.context
                    .getCUByPrimaryTypeName("com.google.debugging.sourcemap.SourceMapGeneratorV3")
                assumeTrue(cu != null)

                val srcType = cu.primaryType.get().asClassOrInterfaceDeclaration()
                val srcField = srcType.getFieldVariableDecl("mappings")
                assumeTrue {
                    decideForFieldDecl(
                        reducer.context,
                        srcField,
                        enableAssertions,
                        noCache = true
                    ) != NodeTransformDecision.REMOVE
                }
                assumeTrue {
                    decideForFieldDecl(
                        reducer.context,
                        srcField,
                        enableAssertions,
                        noCache = false
                    ) != NodeTransformDecision.REMOVE
                }

                val tgtType = srcType.getTypeByName<ClassOrInterfaceDeclaration>("Mapping")
                assertTrue {
                    isTypeReachable(
                        reducer.context,
                        tgtType,
                        enableAssertions,
                        noCache = true
                    )
                }
                assertTrue {
                    isTypeReachable(
                        reducer.context,
                        tgtType,
                        enableAssertions,
                        noCache = false
                    )
                }
            }
        }
    }
}