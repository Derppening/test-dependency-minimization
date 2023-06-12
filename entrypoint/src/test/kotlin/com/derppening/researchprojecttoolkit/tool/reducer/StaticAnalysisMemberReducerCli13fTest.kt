package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.tool.compilation.CompilerProxy
import com.derppening.researchprojecttoolkit.tool.reachability.ReachableReason
import com.derppening.researchprojecttoolkit.tool.transform.NodeTransformDecision
import com.derppening.researchprojecttoolkit.tool.transform.TagStaticAnalysisMemberUnusedDecls.Companion.decideForConstructor
import com.derppening.researchprojecttoolkit.tool.transform.TagStaticAnalysisMemberUnusedDecls.Companion.decideForFieldDecl
import com.derppening.researchprojecttoolkit.tool.transform.TagStaticAnalysisMemberUnusedDecls.Companion.decideForMethod
import com.derppening.researchprojecttoolkit.tool.transform.TagStaticAnalysisMemberUnusedDecls.Companion.isTypeReachable
import com.derppening.researchprojecttoolkit.util.getFieldVariableDecl
import com.derppening.researchprojecttoolkit.util.synchronizedWith
import org.junit.jupiter.api.Nested
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.test.*

class StaticAnalysisMemberReducerCli13fTest : StaticAnalysisMemberReducerIntegTest() {

    override val project = TestProjects.Cli13f

    @Nested
    inner class TriggeringTestsGranularity : StaticAnalysisMemberReducerIntegTest.OnTriggeringTests(true) {

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

            project.copyProjectFile(Path("src/java/org/apache/commons/cli2/resource/CLIMessageBundle_en_US.properties"), outputDir!!.path)

            assertTestSuccess(outputDir!!.path, JAVA_17_HOME) {
                add("-cp")
                add("${project.testCpJars.joinToString(":")}:${sourceRoots.joinToString(":")}")

                testCases.forEach {
                    add("-m")
                    add(it.toJUnitMethod())
                }
            }
        }

        /**
         * ```
         * java.lang.AssertionError: This method should not be reached! Signature: getMaximum()
         * at org.apache.commons.cli2.option.ArgumentImpl.getMaximum(ArgumentImpl.java:272)
         * at org.apache.commons.cli2.option.SourceDestArgument.<init>(SourceDestArgument.java:64)
         * at org.apache.commons.cli2.option.SourceDestArgument.<init>(SourceDestArgument.java:50)
         * at org.apache.commons.cli2.bug.BugLoopingOptionLookAlikeTest.testLoopingOptionLookAlike2(BugLoopingOptionLookAlikeTest.java:60)
         * ```
         */
        @Test
        fun `Regression-00`() {
            // org.apache.commons.cli2.bug.BugLoopingOptionLookAlikeTest.testLoopingOptionLookAlike2()
            run {
                val cu = reducer.context.loadedCompilationUnits
                    .single {
                        it.primaryType.flatMap { it.fullyQualifiedName }.orElse(null) == "org.apache.commons.cli2.bug.BugLoopingOptionLookAlikeTest"
                    }

                val method = cu.primaryType.get()
                    .getMethodsBySignature("testLoopingOptionLookAlike2").single()

                assertTrue(method.inclusionReasonsData.isNotEmpty())
                assertTrue {
                    method.inclusionReasonsData.synchronizedWith {
                        filterIsInstance<ReachableReason.Entrypoint>().isNotEmpty()
                    }
                }
                assertFalse(method.isUnusedForRemovalData)
                assertFalse(method.isUnusedForDummyData)
            }

            // org.apache.commons.cli2.option.SourceDestArgument.<init>(Argument, Argument)
            run {
                val cu = reducer.context.loadedCompilationUnits
                    .single {
                        it.primaryType.flatMap { it.fullyQualifiedName }
                            .orElse(null) == "org.apache.commons.cli2.option.SourceDestArgument"
                    }

                val method = cu.primaryType.get()
                    .getConstructorByParameterTypes("Argument", "Argument").get()

                assertTrue(method.inclusionReasonsData.isNotEmpty())
                assertTrue {
                    method.inclusionReasonsData.synchronizedWith {
                        filterIsInstance<ReachableReason.ReferencedBySymbolName>().isNotEmpty()
                    }
                }
                assertFalse(method.isUnusedForRemovalData)
                assertFalse(method.isUnusedForDummyData)
            }

            // org.apache.commons.cli2.option.SourceDestArgument.<init>(Argument, Argument, char, char, String, List)
            run {
                val cu = reducer.context.loadedCompilationUnits
                    .single {
                        it.primaryType.flatMap { it.fullyQualifiedName }.orElse(null) == "org.apache.commons.cli2.option.SourceDestArgument"
                    }

                val method = cu.primaryType.get()
                    .getConstructorByParameterTypes("Argument", "Argument", "char", "char", "String", "List").get()

                assertTrue(method.inclusionReasonsData.isNotEmpty())
                assertTrue {
                    method.inclusionReasonsData.synchronizedWith {
                        filterIsInstance<ReachableReason.ReferencedByCtorCallByExplicitStmt>().isNotEmpty()
                    }
                }
                assertFalse(method.isUnusedForRemovalData)
                assertFalse(method.isUnusedForDummyData)
            }

            // org.apache.commons.cli2.Argument.getMaximum()
            // Static call target of `org.apache.commons.cli2.option.ArgumentImpl.getMaximum()`
            run {
                val cu = reducer.context.loadedCompilationUnits
                    .single {
                        it.primaryType.flatMap { it.fullyQualifiedName }.orElse(null) == "org.apache.commons.cli2.Argument"
                    }

                val method = cu.primaryType.get()
                    .getMethodsBySignature("getMaximum").single()

                assertTrue(method.inclusionReasonsData.isNotEmpty())
                assertTrue {
                    method.inclusionReasonsData.synchronizedWith {
                        filterIsInstance<ReachableReason.ReferencedBySymbolName>().isNotEmpty()
                    }
                }
                assertFalse(method.isUnusedForRemovalData)
                assertFalse(method.isUnusedForDummyData)
            }

            // org.apache.commons.cli2.option.ArgumentImpl.getMaximum()
            run {
                val cu = reducer.context.loadedCompilationUnits
                    .single {
                        it.primaryType.flatMap { it.fullyQualifiedName }.orElse(null) == "org.apache.commons.cli2.option.ArgumentImpl"
                    }

                val method = cu.primaryType.get()
                    .getMethodsBySignature("getMaximum").single()

                assertTrue(method.inclusionReasonsData.isNotEmpty())
                assertTrue {
                    method.inclusionReasonsData.synchronizedWith {
                        filterIsInstance<ReachableReason.TransitiveMethodCallTarget>().isNotEmpty()
                    }
                }
                assertFalse(method.isUnusedForRemovalData)
                assertFalse(method.isUnusedForDummyData)
            }
        }

        /**
         * ```
         * src/java/org/apache/commons/cli2/commandline/Parser.java:39: error: constructor HelpFormatter in class HelpFormatter cannot be applied to given types;
         *     private HelpFormatter helpFormatter = new HelpFormatter();
         *                                           ^
         *   required: String,String,String,int
         *   found:    no arguments
         *   reason: actual and formal argument lists differ in length
         * ```
         */
        @Test
        fun `Regression-01`() {
            val srcCU = reducer.context
                .getCUByPrimaryTypeName("org.apache.commons.cli2.commandline.Parser")
            assumeTrue(srcCU != null)

            val srcType = srcCU.primaryType.get()
            val srcFieldVar = srcType.getFieldVariableDecl("helpFormatter")
            assumeTrue {
                decideForFieldDecl(
                    reducer.context,
                    srcFieldVar,
                    enableAssertions = enableAssertions,
                    noCache = true
                ) == NodeTransformDecision.NO_OP
            }

            val tgtCU = reducer.context
                .getCUByPrimaryTypeName("org.apache.commons.cli2.util.HelpFormatter")
            assumeTrue(tgtCU != null)

            val tgtType = tgtCU.primaryType.get()
            val tgtCtor = tgtType.defaultConstructor.get()
            val decision = decideForConstructor(
                reducer.context,
                tgtCtor,
                enableAssertions,
                noCache = true
            )
            assertNotEquals(NodeTransformDecision.REMOVE, decision)
        }
    }

    @Nested
    inner class BugLoopingOptionLookAlikeTest {

        @Nested
        inner class TestLoopingOptionLookAlike2 {

            private val entrypoint = "org.apache.commons.cli2.bug.BugLoopingOptionLookAlikeTest::testLoopingOptionLookAlike2"

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
                        listOf("-Xmaxerrs", "$JAVAC_MAXERRS"),
                        JAVA_17_HOME
                    )

                    assertCompileSuccess(compiler)

                    // Copy `src/java/org/apache/commons/cli2/resource/CLIMessageBundle_en_US.properties` into directory
                    project.copyProjectFile(
                        Path("src/java/org/apache/commons/cli2/resource/CLIMessageBundle_en_US.properties"),
                        outputDir!!.path
                    )

                    assertTestSuccess(outputDir!!.path, JAVA_17_HOME) {
                        add("-cp")
                        add("${project.testCpJars.joinToString(":")}:${sourceRoots.joinToString(":")}")

                        add("-m")
                        add(testCase.toJUnitMethod())
                    }
                }

                /**
                 * ```
                 * compile:
                 *   [javac] /tmp/5878617484854633299/src/java/org/apache/commons/cli2/option/GroupImpl.java:395: error: ReverseStringComparator is not abstract and does not override abstract method compare(Object,Object) in Comparator
                 *   [javac] class ReverseStringComparator implements Comparator {
                 *   [javac] ^
                 * ```
                 */
                @Test
                fun `Regression-00`() {
                    val cu = reducer.context.getCUByPrimaryTypeName("org.apache.commons.cli2.option.GroupImpl")
                    assumeTrue(cu != null)

                    val type = cu.types
                        .single { it.nameAsString == "ReverseStringComparator" }.asClassOrInterfaceDeclaration()
                    assumeTrue(type.isUnusedForRemovalData)

                    val method = type
                        .getMethodsBySignature("compare", "Object", "Object").single()

                    assertTrue(method.inclusionReasonsData.isNotEmpty())
                    assertTrue {
                        method.inclusionReasonsData.synchronizedWith {
                            filterIsInstance<ReachableReason.TransitiveLibraryCallTarget>().isNotEmpty()
                        }
                    }
                    assertFalse(method.isUnusedForRemovalData)
                    assertFalse(method.isUnusedForDummyData)
                }

                /**
                 * ```
                 *     => java.lang.AssertionError: This method should not be reached! Signature: hashCode()
                 *        org.apache.commons.cli2.DisplaySetting.hashCode(DisplaySetting.java:129)
                 *        java.base/java.util.HashMap.hash(HashMap.java:338)
                 *        java.base/java.util.HashMap.put(HashMap.java:610)
                 *        java.base/java.util.HashSet.add(HashSet.java:221)
                 *        org.apache.commons.cli2.DisplaySetting.<init>(DisplaySetting.java:125)
                 *        org.apache.commons.cli2.DisplaySetting.<clinit>(DisplaySetting.java:46)
                 *        org.apache.commons.cli2.util.HelpFormatter.<clinit>(HelpFormatter.java:83)
                 *        org.apache.commons.cli2.commandline.Parser.<init>(Parser.java:39)
                 *        org.apache.commons.cli2.bug.BugLoopingOptionLookAlikeTest.testLoopingOptionLookAlike2(BugLoopingOptionLookAlikeTest.java:50)
                 *        java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
                 *        [...]
                 * ```
                 */
                @Test
                fun `Regression-01`() {
                    val cu = reducer.context.getCUByPrimaryTypeName("org.apache.commons.cli2.DisplaySetting")
                    assumeTrue(cu != null)

                    val type = cu.primaryType.get()
                    assumeFalse(type.isUnusedForRemovalData)

                    val method = type
                        .getMethodsBySignature("hashCode").single()

                    assertTrue(method.inclusionReasonsData.isNotEmpty())
                    assertTrue {
                        method.inclusionReasonsData.synchronizedWith {
                            filterIsInstance<ReachableReason.TransitiveLibraryCallTarget>().isNotEmpty()
                        }
                    }
                    assertFalse(method.isUnusedForRemovalData)
                    assertFalse(method.isUnusedForDummyData)
                }

                /**
                 * ```
                 *     => java.lang.AssertionError: This method should not be reached! Signature: getResourceHelper()
                 *        org.apache.commons.cli2.resource.ResourceHelper.getResourceHelper(ResourceHelper.java:85)
                 *        org.apache.commons.cli2.builder.ArgumentBuilder.<clinit>(ArgumentBuilder.java:32)
                 *        org.apache.commons.cli2.bug.BugLoopingOptionLookAlikeTest.testLoopingOptionLookAlike2(BugLoopingOptionLookAlikeTest.java:40)
                 *        java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
                 *        java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
                 *        java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
                 *        java.base/java.lang.reflect.Method.invoke(Method.java:568)
                 *        junit.framework.TestCase.runTest(TestCase.java:177)
                 *        junit.framework.TestCase.runBare(TestCase.java:142)
                 *        junit.framework.TestResult$1.protect(TestResult.java:122)
                 *        [...]
                 * ```
                 */
                @Test
                fun `Regression-02`() {
                    val srcCU = reducer.context
                        .getCUByPrimaryTypeName("org.apache.commons.cli2.builder.ArgumentBuilder")
                    assumeTrue(srcCU != null)

                    val srcType = srcCU.primaryType.get()
                    assumeTrue(isTypeReachable(reducer.context, srcType, enableAssertions, noCache = true))

                    val srcFieldVar = srcType.getFieldVariableDecl("resources")
                    assumeTrue {
                        decideForFieldDecl(
                            reducer.context,
                            srcFieldVar,
                            enableAssertions = enableAssertions,
                            noCache = true
                        ) == NodeTransformDecision.NO_OP
                    }

                    val tgtCU = reducer.context
                        .getCUByPrimaryTypeName("org.apache.commons.cli2.resource.ResourceHelper")
                    assumeTrue(tgtCU != null)
                    val tgtType = tgtCU.primaryType.get()
                    val tgtMethod = tgtType.getMethodsBySignature("getResourceHelper").single()
                    val decision = decideForMethod(
                        reducer.context,
                        tgtMethod,
                        enableAssertions = enableAssertions,
                        noCache = true
                    )
                    assertEquals(NodeTransformDecision.NO_OP, decision)
                }

                /**
                 * ```
                 *     => java.lang.AssertionError: This method should not be reached! Signature: canProcess(WriteableCommandLine, ListIterator)
                 *        org.apache.commons.cli2.option.OptionImpl.canProcess(OptionImpl.java:49)
                 *        org.apache.commons.cli2.commandline.Parser.parse(Parser.java:75)
                 *        org.apache.commons.cli2.bug.BugLoopingOptionLookAlikeTest.testLoopingOptionLookAlike2(BugLoopingOptionLookAlikeTest.java:49)
                 *        java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
                 *        java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
                 *        java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
                 *        java.base/java.lang.reflect.Method.invoke(Method.java:568)
                 *        junit.framework.TestCase.runTest(TestCase.java:177)
                 *        junit.framework.TestCase.runBare(TestCase.java:142)
                 *        junit.framework.TestResult$1.protect(TestResult.java:122)
                 *        [...]
                 * ```
                 */
                @Test
                fun `Regression-03`() {
                    val methodCallSrcCU = reducer.context
                        .getCUByPrimaryTypeName("org.apache.commons.cli2.commandline.Parser")
                    assumeTrue(methodCallSrcCU != null)

                    val methodCallSrcType = methodCallSrcCU.primaryType.get()
                    assumeTrue(isTypeReachable(reducer.context, methodCallSrcType, enableAssertions, noCache = true))

                    val methodCallSrcExpr = methodCallSrcType.getMethodsBySignature("parse", "String[]").single()
                    assumeTrue {
                        decideForMethod(reducer.context, methodCallSrcExpr, enableAssertions = enableAssertions, noCache = true) == NodeTransformDecision.NO_OP
                    }

                    val tgtTypeCreationCU = reducer.context
                        .getCUByPrimaryTypeName("org.apache.commons.cli2.builder.GroupBuilder")
                    assumeTrue(tgtTypeCreationCU != null)

                    val tgtTypeCreationType = tgtTypeCreationCU.primaryType.get()
                    assumeTrue(isTypeReachable(reducer.context, tgtTypeCreationType, enableAssertions, noCache = true))

                    val tgtTypeCreationMethod = tgtTypeCreationType.getMethodsBySignature("create").single()
                    assumeTrue {
                        decideForMethod(
                            reducer.context,
                            tgtTypeCreationMethod,
                            enableAssertions = enableAssertions,
                            noCache = true
                        ) == NodeTransformDecision.NO_OP
                    }

                    val tgtCU = reducer.context
                        .getCUByPrimaryTypeName("org.apache.commons.cli2.option.OptionImpl")
                    assumeTrue(tgtCU != null)
                    val tgtType = tgtCU.primaryType.get()
//                    assertTrue(tgtType.instanceCreationData.isNotEmpty())

                    val tgtMethod = tgtType.getMethodsBySignature("canProcess", "WriteableCommandLine", "ListIterator").single()
                    val decision = decideForMethod(
                        reducer.context,
                        tgtMethod,
                        enableAssertions = enableAssertions,
                        noCache = true
                    )
                    assertEquals(NodeTransformDecision.NO_OP, decision)
                }

                @Test
                fun `Regression-04`() {
                    val srcCU = reducer.context
                        .getCUByPrimaryTypeName("org.apache.commons.cli2.commandline.Parser")
                    assumeTrue(srcCU != null)
                    val srcType = srcCU.primaryType.get()
                    val srcFieldVar = srcType.getFieldVariableDecl("helpFormatter")

                    assumeTrue {
                        val fieldDecisionCached = decideForFieldDecl(
                            reducer.context,
                            srcFieldVar,
                            enableAssertions,
                            noCache = true
                        )
                        val fieldDecisionNoCache = decideForFieldDecl(
                            reducer.context,
                            srcFieldVar,
                            enableAssertions,
                            noCache = false
                        )

                        fieldDecisionCached == fieldDecisionNoCache
                    }

                    val tgtCU = reducer.context
                        .getCUByPrimaryTypeName("org.apache.commons.cli2.util.HelpFormatter")
                    assumeTrue(tgtCU != null)
                    val tgtType = tgtCU.primaryType.get()
                    val typeReachableNoCache = isTypeReachable(
                        reducer.context,
                        tgtType,
                        enableAssertions,
                        noCache = true
                    )
                    assertTrue(typeReachableNoCache)
                    val typeReachableCached = isTypeReachable(
                        reducer.context,
                        tgtType,
                        enableAssertions,
                        noCache = false
                    )
                    assertTrue(typeReachableCached)
                }
            }
        }
    }
}