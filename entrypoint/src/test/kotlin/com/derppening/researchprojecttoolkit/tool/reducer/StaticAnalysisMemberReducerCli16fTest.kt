package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.tool.compilation.CompilerProxy
import com.derppening.researchprojecttoolkit.tool.reachability.ReachableReason
import com.derppening.researchprojecttoolkit.tool.transform.NodeTransformDecision
import com.derppening.researchprojecttoolkit.tool.transform.TagStaticAnalysisMemberUnusedDecls.Companion.decideForMethod
import com.derppening.researchprojecttoolkit.util.synchronizedWith
import org.junit.jupiter.api.Nested
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StaticAnalysisMemberReducerCli16fTest : StaticAnalysisMemberReducerIntegTest() {

    override val project = TestProjects.Cli16f

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
         * java.lang.AssertionError: This method should not be reached! Signature: addProperty(String, String)
         *   at org.apache.commons.cli2.commandline.WriteableCommandLineImpl.addProperty(WriteableCommandLineImpl.java:179)
         *   at org.apache.commons.cli2.commandline.DefaultingCommandLineTest.setUp(DefaultingCommandLineTest.java:69)
         * ```
         */
        @Test
        fun `Regression-00`() {
            // org.apache.commons.cli2.commandline.DefaultingCommandLineTest.setUp()
            run {
                val cu = reducer.context.loadedCompilationUnits
                    .single {
                        it.primaryType.flatMap { it.fullyQualifiedName }.orElse(null) == "org.apache.commons.cli2.commandline.DefaultingCommandLineTest"
                    }

                val method = cu.primaryType.get()
                    .getMethodsBySignature("setUp").single()

                assertTrue(method.inclusionReasonsData.isNotEmpty())
                assertTrue {
                    method.inclusionReasonsData.synchronizedWith {
                        filterIsInstance<ReachableReason.Entrypoint>().isNotEmpty()
                    }
                }
                assertFalse(method.isUnusedForRemovalData)
                assertFalse(method.isUnusedForDummyData)
            }

            // org.apache.commons.cli2.WriteableCommandLine.addProperty(String, String)
            // Static call target of `org.apache.commons.cli2.commandline.WriteableCommandLineImpl.addProperty(String, String)`
            run {
                val cu = reducer.context.loadedCompilationUnits
                    .single {
                        it.primaryType.flatMap { it.fullyQualifiedName }.orElse(null) == "org.apache.commons.cli2.WriteableCommandLine"
                    }

                val method = cu.primaryType.get()
                    .getMethodsBySignature("addProperty", "String", "String").single()

                assertTrue(method.inclusionReasonsData.isNotEmpty())
                assertTrue {
                    method.inclusionReasonsData.synchronizedWith {
                        filterIsInstance<ReachableReason.ReferencedBySymbolName>().isNotEmpty()
                    }
                }
                assertFalse(method.isUnusedForRemovalData)
                assertFalse(method.isUnusedForDummyData)
            }

            // org.apache.commons.cli2.commandline.WriteableCommandLineImpl.addProperty(String, String)
            run {
                val cu = reducer.context.loadedCompilationUnits
                    .single {
                        it.primaryType.flatMap { it.fullyQualifiedName }.orElse(null) == "org.apache.commons.cli2.commandline.WriteableCommandLineImpl"
                    }

                val method = cu.primaryType.get()
                    .getMethodsBySignature("addProperty", "String", "String").single()

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
         *     => java.lang.AssertionError: This method should not be reached! Signature: hasOption(String)
         *        org.apache.commons.cli2.commandline.CommandLineImpl.hasOption(CommandLineImpl.java:30)
         *        org.apache.commons.cli2.commandline.Parser.parse(Parser.java:94)
         *        org.apache.commons.cli2.bug.BugCLI123Test.testMultipleChildOptions(BugCLI123Test.java:96)
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
        fun `Regression-01`() {
            val srcCU = reducer.context
                .getCUByPrimaryTypeName("org.apache.commons.cli2.commandline.Parser")
            assumeTrue(srcCU != null)

            val srcMethod = srcCU.primaryType.get()
                .getMethodsBySignature("parse", "String[]").single()
            assumeFalse(srcMethod.isUnusedForDummyData)
            assumeFalse(srcMethod.isUnusedForRemovalData)

            val tgtCU = reducer.context
                .getCUByPrimaryTypeName("org.apache.commons.cli2.commandline.CommandLineImpl")
            assumeTrue(tgtCU != null)

            val tgtType = tgtCU.primaryType.get()
            val tgtMethod = tgtType.getMethodsBySignature("hasOption", "String").single()
            val methodDecision = decideForMethod(
                reducer.context,
                tgtMethod,
                enableAssertions,
                noCache = true
            )
            assertEquals(NodeTransformDecision.NO_OP, methodDecision)
        }

        /**
         * ```
         *     => java.lang.AssertionError: This method should not be reached! Signature: createCommandLine()
         *        org.apache.commons.cli2.commandline.DefaultingCommandLineTest.createCommandLine(DefaultingCommandLineTest.java:45)
         *        org.apache.commons.cli2.CommandLineTestCase.setUp(CommandLineTestCase.java:51)
         *        org.apache.commons.cli2.commandline.DefaultingCommandLineTest.setUp(DefaultingCommandLineTest.java:49)
         *        junit.framework.TestCase.runBare(TestCase.java:140)
         *        junit.framework.TestResult$1.protect(TestResult.java:122)
         *        junit.framework.TestResult.runProtected(TestResult.java:142)
         *        junit.framework.TestResult.run(TestResult.java:125)
         *        junit.framework.TestCase.run(TestCase.java:130)
         *        junit.framework.TestSuite.runTest(TestSuite.java:241)
         *        junit.framework.TestSuite.run(TestSuite.java:236)
         *        [...]
         * ```
         */
        @Test
        fun `Regression-02`() {
            val srcCU = reducer.context
                .getCUByPrimaryTypeName("org.apache.commons.cli2.CommandLineTestCase")
            assumeTrue(srcCU != null)

            val srcMethod = srcCU.primaryType.get()
                .getMethodsBySignature("setUp").single()
            assumeFalse(srcMethod.isUnusedForDummyData)
            assumeFalse(srcMethod.isUnusedForRemovalData)

            val tgtCU = reducer.context
                .getCUByPrimaryTypeName("org.apache.commons.cli2.commandline.DefaultingCommandLineTest")
            assumeTrue(tgtCU != null)

            val tgtType = tgtCU.primaryType.get()
            val tgtMethod = tgtType.getMethodsBySignature("createCommandLine").single()
            val methodDecision = decideForMethod(
                reducer.context,
                tgtMethod,
                enableAssertions,
                noCache = true
            )
            assertEquals(NodeTransformDecision.NO_OP, methodDecision)
        }
    }
}