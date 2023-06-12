package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.tool.compilation.CompilerProxy
import com.derppening.researchprojecttoolkit.tool.transform.NodeTransformDecision
import com.derppening.researchprojecttoolkit.tool.transform.TagStaticAnalysisMemberUnusedDecls.Companion.decideForConstructor
import com.derppening.researchprojecttoolkit.tool.transform.TagStaticAnalysisMemberUnusedDecls.Companion.decideForFieldDecl
import com.derppening.researchprojecttoolkit.tool.transform.TagStaticAnalysisMemberUnusedDecls.Companion.isTypeReachable
import com.derppening.researchprojecttoolkit.util.getFieldVariableDecl
import org.junit.jupiter.api.Nested
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertFalse

class StaticAnalysisMemberReducerClosure79fTest : StaticAnalysisMemberReducerIntegTest() {

    override val project = TestProjects.Closure79f

    @Nested
    inner class NormalizeTest {

        @Nested
        inner class TestIssue : OnTestCase("com.google.javascript.jscomp.NormalizeTest::testIssue", true) {

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
             * src/com/google/javascript/rhino/TokenStream.java:454: error: cannot find symbol
             *     private Parser parser;
             *             ^
             *   symbol:   class Parser
             *   location: class TokenStream
             * ```
             */
            @Test
            fun `Regression-00`() {
                val srcCU = reducer.context
                    .getCUByPrimaryTypeName("com.google.javascript.rhino.TokenStream")
                assumeTrue(srcCU != null)
                val srcType = srcCU.primaryType.get()
                val srcFieldVar = srcType.getFieldVariableDecl("parser")

                val fieldDecisionNoCache = decideForFieldDecl(
                    reducer.context,
                    srcFieldVar,
                    enableAssertions,
                    noCache = true
                )
                val fieldDecisionCached = decideForFieldDecl(
                    reducer.context,
                    srcFieldVar,
                    enableAssertions,
                    noCache = false
                )
                assumeTrue(fieldDecisionCached == fieldDecisionNoCache)
                assumeTrue(fieldDecisionCached == NodeTransformDecision.REMOVE)

                val tgtCU = reducer.context
                    .getCUByPrimaryTypeName("com.google.javascript.rhino.Parser")
                assumeTrue(tgtCU != null)
                val tgtType = tgtCU.primaryType.get()
                val typeReachableNoCache = isTypeReachable(
                    reducer.context,
                    tgtType,
                    enableAssertions,
                    noCache = true
                )
                assertFalse(typeReachableNoCache)
                val typeReachableCached = isTypeReachable(
                    reducer.context,
                    tgtType,
                    enableAssertions,
                    noCache = false
                )
                assertFalse(typeReachableCached)
            }


            /**
             * ```
             * src/com/google/javascript/rhino/TokenStream.java:66: error: cannot find symbol
             *     public TokenStream(Parser parser, Reader sourceReader, String sourceString, int lineno) {
             *                        ^
             *   symbol:   class Parser
             *   location: class TokenStream
             * ```
             */
            @Test
            fun `Regression-01`() {
                val srcCU = reducer.context
                    .getCUByPrimaryTypeName("com.google.javascript.rhino.TokenStream")
                assumeTrue(srcCU != null)
                val srcType = srcCU.primaryType.get()
                val srcCtor = srcType.getConstructorByParameterTypes("Parser", "Reader", "String", "int").get()

                val methodDecisionNoCache = decideForConstructor(
                    reducer.context,
                    srcCtor,
                    enableAssertions,
                    noCache = true
                )
                val methodDecisionCached = decideForConstructor(
                    reducer.context,
                    srcCtor,
                    enableAssertions,
                    noCache = false
                )
                assumeTrue(methodDecisionCached == methodDecisionNoCache)
                assumeTrue(methodDecisionCached == NodeTransformDecision.REMOVE)

                val tgtCU = reducer.context
                    .getCUByPrimaryTypeName("com.google.javascript.rhino.Parser")
                assumeTrue(tgtCU != null)
                val tgtType = tgtCU.primaryType.get()
                val typeReachableNoCache = isTypeReachable(
                    reducer.context,
                    tgtType,
                    enableAssertions,
                    noCache = true
                )
                assertFalse(typeReachableNoCache)
                val typeReachableCached = isTypeReachable(
                    reducer.context,
                    tgtType,
                    enableAssertions,
                    noCache = false
                )
                assertFalse(typeReachableCached)
            }
        }
    }
}