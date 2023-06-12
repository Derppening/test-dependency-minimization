package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.tool.compilation.CompilerProxy
import com.derppening.researchprojecttoolkit.tool.transform.NodeTransformDecision
import com.derppening.researchprojecttoolkit.tool.transform.TagStaticAnalysisMemberUnusedDecls.Companion.decideForConstructor
import com.derppening.researchprojecttoolkit.tool.transform.TagStaticAnalysisMemberUnusedDecls.Companion.decideForFieldDecl
import com.derppening.researchprojecttoolkit.tool.transform.TagStaticAnalysisMemberUnusedDecls.Companion.decideForMethod
import com.derppening.researchprojecttoolkit.tool.transform.TagStaticAnalysisMemberUnusedDecls.Companion.isTypeReachable
import com.derppening.researchprojecttoolkit.util.getFieldVariableDecl
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import org.junit.jupiter.api.Nested
import java.nio.file.Path
import kotlin.test.*

class StaticAnalysisMemberReducerLang17fTest : StaticAnalysisMemberReducerIntegTest() {

    override val project = TestProjects.Lang17f

    @Nested
    inner class StringEscapeUtilsTest {

        @Nested
        inner class TestLang720 : OnTestCase("org.apache.commons.lang3.StringEscapeUtilsTest::testLang720", true) {

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

            /**
             * ```
             * [javac] /tmp/18145940037473240375/src/main/java/org/apache/commons/lang3/tuple/ImmutablePair.java:35: error: ImmutablePair is not abstract and does not override abstract method setValue(R) in Entry
             * [javac] public final class ImmutablePair<L, R> extends Pair<L, R> {
             * [javac]              ^
             * [javac]   where R is a type-variable:
             * [javac]     R extends Object declared in class ImmutablePair
             * ```
             */
            @Test
            @Ignore
            fun `Regression-00`() {
                val cu = reducer.context.getCUByPrimaryTypeName("org.apache.commons.lang3.tuple.ImmutablePair")
                assumeTrue(cu != null)

                val methodDecl = cu.primaryType.get()
                    .getMethodsBySignature("setValue", "R").single()
                assertTrue(methodDecl.inclusionReasonsData.isNotEmpty())
                assertFalse(methodDecl.isUnusedForDummyData)
                assertFalse(methodDecl.isUnusedForRemovalData)
            }

            /**
             * ```
             * /src/main/java/org/apache/commons/lang3/SystemUtils.java:440: error: cannot find symbol
             *     private static final JavaVersion JAVA_SPECIFICATION_VERSION_AS_ENUM = JavaVersion.get(JAVA_SPECIFICATION_VERSION);
             *                                                                                      ^
             *   symbol:   method get(String)
             *   location: class JavaVersion
             * ```
             */
            @Test
            fun `Regression-01`() {
                val srcCU = reducer.context.getCUByPrimaryTypeName("org.apache.commons.lang3.SystemUtils")
                assumeTrue(srcCU != null)

                val srcType = srcCU.primaryType.get()
                val srcFieldDecl = srcType.getFieldVariableDecl("JAVA_SPECIFICATION_VERSION_AS_ENUM")
                assumeTrue {
                    decideForFieldDecl(
                        reducer.context,
                        srcFieldDecl,
                        enableAssertions = enableAssertions,
                        noCache = true
                    ) == NodeTransformDecision.NO_OP
                }
                assumeFalse(srcFieldDecl.isUnusedForRemovalData)

                val tgtCU = reducer.context.getCUByPrimaryTypeName("org.apache.commons.lang3.JavaVersion")
                assumeTrue(tgtCU != null)

                val tgtType = tgtCU.primaryType.get()
                val tgtMethod = tgtType.getMethodsBySignature("get", "String").single()

                val tgtDecision = decideForMethod(
                    reducer.context,
                    tgtMethod,
                    enableAssertions,
                    noCache = true
                )
                assertNotEquals(NodeTransformDecision.REMOVE, tgtDecision)
                assertFalse(tgtType.isUnusedForDummyData)
                assertFalse(tgtType.isUnusedForRemovalData)
            }

            /**
             * ```
             * src/main/java/org/apache/commons/lang3/builder/ToStringStyle.java:116: error: cannot find symbol
             *     public static final ToStringStyle SHORT_PREFIX_STYLE = new ShortPrefixToStringStyle();
             *                                                                ^
             *   symbol:   class ShortPrefixToStringStyle
             *   location: class ToStringStyle
             * ```
             */
            @Test
            fun `Regression-02`() {
                val cu = reducer.context.getCUByPrimaryTypeName("org.apache.commons.lang3.builder.ToStringStyle")
                assumeTrue(cu != null)

                val srcType = cu.primaryType.get()
                val srcFieldDecl = srcType.getFieldVariableDecl("SHORT_PREFIX_STYLE")
                assumeTrue {
                    decideForFieldDecl(
                        reducer.context,
                        srcFieldDecl,
                        enableAssertions = enableAssertions,
                        noCache = true
                    ) == NodeTransformDecision.NO_OP
                }
                assumeFalse(srcFieldDecl.isUnusedForRemovalData)

                val tgtType = cu.primaryType.get()
                    .members
                    .single {
                        it is ClassOrInterfaceDeclaration && it.nameAsString == "ShortPrefixToStringStyle"
                    }
                    .asClassOrInterfaceDeclaration()
                val tgtTypeReachable = isTypeReachable(
                    reducer.context,
                    tgtType,
                    enableAssertions,
                    noCache = true
                )
                assertTrue(tgtTypeReachable)

                val tgtMethod = tgtType.defaultConstructor.get()

                val tgtMethodDecision = decideForConstructor(
                    reducer.context,
                    tgtMethod,
                    enableAssertions,
                    noCache = true
                )
                assertNotEquals(NodeTransformDecision.REMOVE, tgtMethodDecision)
                assertFalse(tgtType.isUnusedForDummyData)
                assertFalse(tgtType.isUnusedForRemovalData)
            }

            /**
             * ```
             * src/main/java/org/apache/commons/lang3/text/StrTokenizer.java:203: error: cannot find symbol
             *         setQuoteChar(quote);
             *         ^
             *   symbol:   method setQuoteChar(char)
             *   location: class StrTokenizer
             * src/main/java/org/apache/commons/lang3/text/StrTokenizer.java:249: error: cannot find symbol
             *         setQuoteChar(quote);
             *         ^
             *   symbol:   method setQuoteChar(char)
             *   location: class StrTokenizer
             * ```
             */
            @Test
            fun `Regression-03`() {
                val cu = reducer.context.getCUByPrimaryTypeName("org.apache.commons.lang3.text.StrTokenizer")
                assumeTrue(cu != null)

                val type = cu.primaryType.get()

                val srcCtor1 = type.getConstructorByParameterTypes("String", "char", "char").get()
                val ctor1Decision = decideForConstructor(
                    reducer.context,
                    srcCtor1,
                    enableAssertions,
                    noCache = true
                )
                assertEquals(NodeTransformDecision.REMOVE, ctor1Decision)

                val srcCtor2 = type.getConstructorByParameterTypes("char[]", "char", "char").get()
                val ctor2Decision = decideForConstructor(
                    reducer.context,
                    srcCtor2,
                    enableAssertions,
                    noCache = true
                )
                assertEquals(NodeTransformDecision.REMOVE, ctor2Decision)
            }
        }
    }
}