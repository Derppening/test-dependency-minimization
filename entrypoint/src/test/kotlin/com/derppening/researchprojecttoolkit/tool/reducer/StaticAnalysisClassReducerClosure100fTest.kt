package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.tool.compilation.CompilerProxy
import com.derppening.researchprojecttoolkit.tool.transform.NodeTransformDecision
import com.derppening.researchprojecttoolkit.tool.transform.TagStaticAnalysisClassUnusedDecls.Companion.decideForType
import com.derppening.researchprojecttoolkit.util.getTypeByName
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import org.junit.jupiter.api.Nested
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class StaticAnalysisClassReducerClosure100fTest : StaticAnalysisClassReducerIntegTest() {

    override val project = TestProjects.Closure100f

    @Nested
    inner class CheckGlobalThisTest {

        @Nested
        inner class TestStaticMethod3 : OnTestCase("com.google.javascript.jscomp.CheckGlobalThisTest::testStaticMethod3", true) {

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

            /**
             * ```
             * src/com/google/javascript/jscomp/RemoveConstantExpressions.java:20: error: package com.google.javascript.jscomp.ParallelCompilerPass does not exist
             * import com.google.javascript.jscomp.ParallelCompilerPass.Result;
             *                                                         ^
             * src/com/google/javascript/jscomp/RemoveConstantExpressions.java:50: error: cannot find symbol
             *         cb.getResult().notifyCompiler(compiler);
             *                       ^
             *   symbol:   method notifyCompiler(AbstractCompiler)
             *   location: class Result
             * src/com/google/javascript/jscomp/RemoveConstantExpressions.java:62: error: no suitable constructor found for Result(no arguments)
             *         private final Result result = new Result();
             *                                       ^
             *     constructor Result.Result(JSError[],JSError[],String,VariableMap,VariableMap,VariableMap,VariableMap,FunctionInformationMap,SourceMap,String,Map<String,Integer>) is not applicable
             *       (actual and formal argument lists differ in length)
             *     constructor Result.Result(JSError[],JSError[],String,VariableMap,VariableMap,VariableMap,FunctionInformationMap,SourceMap,String) is not applicable
             *       (actual and formal argument lists differ in length)
             * src/com/google/javascript/jscomp/RemoveConstantExpressions.java:123: error: cannot find symbol
             *             result.changed = true;
             *                   ^
             *   symbol:   variable changed
             *   location: variable result of type Result
             * ```
             */
            @Test
            fun `Include Container Type of Nested Class`() {
                val cu = reducer.context
                    .getCUByPrimaryTypeName("com.google.javascript.jscomp.ParallelCompilerPass")
                assumeTrue(cu != null)

                val primaryType = cu.primaryType.get().asClassOrInterfaceDeclaration()
                val nestedType = primaryType.getTypeByName<ClassOrInterfaceDeclaration>("Result")

                assumeTrue {
                    decideForType(nestedType, noCache = true) != NodeTransformDecision.REMOVE
                }

                assertTrue {
                    decideForType(primaryType, noCache = true) != NodeTransformDecision.REMOVE
                }
            }
        }
    }
}