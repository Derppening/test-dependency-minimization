package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.tool.compilation.CompilerProxy
import com.derppening.researchprojecttoolkit.tool.transform.NodeTransformDecision
import com.derppening.researchprojecttoolkit.tool.transform.TagGroundTruthUnusedDecls.Companion.decideForMethod
import com.derppening.researchprojecttoolkit.tool.transform.TagGroundTruthUnusedDecls.Companion.isTypeReachable
import com.derppening.researchprojecttoolkit.util.getTypeByName
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import org.junit.jupiter.api.Nested
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class CoverageBasedReducerClosure101fTest : CoverageBasedReducerIntegTest() {

    override val project = TestProjects.Closure101f

    @Nested
    inner class CommandLineRunnerTest {

        @Nested
        inner class TestProcessClosurePrimitives : OnTestCase("com.google.javascript.jscomp.CommandLineRunnerTest::testProcessClosurePrimitives", true) {

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

                // Copy `build/classes/rhino_ast/java/com/google/javascript/rhino/Messages.properties` into directory
                project.copyProjectFile(
                    Path("build/classes/rhino_ast/java/com/google/javascript/rhino/Messages.properties"),
                    outputDir!!.path
                )
                project.copyProjectFile(
                    Path("src/com/google/javascript/jscomp/parsing/ParserConfig.properties"),
                    outputDir!!.path
                )

                assertTestSuccess(outputDir!!.path, JAVA_17_HOME) {
                    add("-cp")
                    add("${project.testCpJars.joinToString(":")}:${sourceRoots.joinToString(":")}:build/classes")

                    add("-m")
                    add(testCase.toJUnitMethod())
                }
            }

            @Nested
            inner class SymbolLookupTests

            @Nested
            inner class ReductionRegressionTests {

                /**
                 * ```
                 * src/com/google/javascript/jscomp/Compiler.java:241: error: cannot find symbol
                 *         } catch (JSModuleGraph.ModuleDependenceException e) {
                 *                               ^
                 *   symbol:   class ModuleDependenceException
                 *   location: class JSModuleGraph
                 * ```
                 */
                @Test
                fun `Include Caught Exception Type in Tagging Phase`() {
                    val srcCU = reducer.context
                        .getCUByPrimaryTypeName("com.google.javascript.jscomp.Compiler")
                    assumeTrue(srcCU != null)

                    val srcType = srcCU.primaryType.get().asClassOrInterfaceDeclaration()
                    assumeTrue {
                        isTypeReachable(
                            reducer.context,
                            srcType,
                            enableAssertions,
                            noCache = true
                        )
                    }

                    val srcMethod = srcType.getMethodsBySignature("init", "JSSourceFile[]", "JSModule[]", "CompilerOptions").single()
                    assumeTrue {
                        decideForMethod(
                            reducer.context,
                            srcMethod,
                            enableAssertions,
                            noCache = true
                        ) == NodeTransformDecision.NO_OP
                    }

                    val tgtCU = reducer.context
                        .getCUByPrimaryTypeName("com.google.javascript.jscomp.JSModuleGraph")
                    assumeTrue(tgtCU != null)

                    val tgtType = tgtCU.primaryType.get().asClassOrInterfaceDeclaration()
                        .getTypeByName<ClassOrInterfaceDeclaration>("ModuleDependenceException")

                    assertTrue {
                        isTypeReachable(
                            reducer.context,
                            tgtType,
                            enableAssertions,
                            noCache = true
                        )
                    }
                }
            }
        }
    }
}