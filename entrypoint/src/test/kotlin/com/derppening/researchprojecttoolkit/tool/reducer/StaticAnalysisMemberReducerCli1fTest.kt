package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.tool.compilation.CompilerProxy
import com.derppening.researchprojecttoolkit.tool.reachability.ReachableReason
import com.derppening.researchprojecttoolkit.util.synchronizedWith
import org.junit.jupiter.api.Nested
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StaticAnalysisMemberReducerCli1fTest : StaticAnalysisMemberReducerIntegTest() {

    override val project = TestProjects.Cli1f

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
         * java.lang.AssertionError: This method should not be reached! Signature: String[] flatten(Options, String[], boolean)
         *   at org.apache.commons.cli.PosixParser.flatten(PosixParser.java:102)
         *   at org.apache.commons.cli.Parser.parse(Parser.java:135)
         *   at org.apache.commons.cli.Parser.parse(Parser.java:73)
         *   at org.apache.commons.cli.bug.BugCLI13Test.testCLI13(BugCLI13Test.java:37)
         * ```
         */
        @Test
        fun `Regression-00`() {
            // org.apache.commons.cli.bug.BugCLI13Test.testCLI13()
            run {
                val cu = reducer.context.loadedCompilationUnits
                    .single {
                        it.primaryType.flatMap { it.fullyQualifiedName }.orElse(null) == "org.apache.commons.cli.bug.BugCLI13Test"
                    }

                val method = cu.primaryType.get()
                    .getMethodsBySignature("testCLI13").single()

                assertTrue(method.inclusionReasonsData.isNotEmpty())
                assertTrue {
                    method.inclusionReasonsData.synchronizedWith { filterIsInstance<ReachableReason.Entrypoint>().isNotEmpty() }
                }
                assertFalse(method.isUnusedForRemovalData)
                assertFalse(method.isUnusedForDummyData)
            }

            // org.apache.commons.cli.Parser.parse(Options, String[])
            run {
                val cu = reducer.context.loadedCompilationUnits
                    .single {
                        it.primaryType.flatMap { it.fullyQualifiedName }.orElse(null) == "org.apache.commons.cli.Parser"
                    }

                val method = cu.primaryType.get()
                    .getMethodsBySignature("parse", "Options", "String[]").single()

                assertTrue(method.inclusionReasonsData.isNotEmpty())
                assertTrue {
                    method.inclusionReasonsData.synchronizedWith {
                        filterIsInstance<ReachableReason.DirectlyReferencedByNode>().isNotEmpty()
                    }
                }
                assertFalse(method.isUnusedForRemovalData)
                assertFalse(method.isUnusedForDummyData)
            }

            // org.apache.commons.cli.Parser.parse(Options, String[], Properties, boolean)
            run {
                val cu = reducer.context.loadedCompilationUnits
                    .single {
                        it.primaryType.flatMap { it.fullyQualifiedName }.orElse(null) == "org.apache.commons.cli.Parser"
                    }

                val method = cu.primaryType.get()
                    .getMethodsBySignature("parse", "Options", "String[]", "Properties", "boolean").single()

                assertTrue(method.inclusionReasonsData.isNotEmpty())
                assertTrue {
                    method.inclusionReasonsData.synchronizedWith {
                        filterIsInstance<ReachableReason.DirectlyReferencedByNode>().isNotEmpty()
                    }
                }
                assertFalse(method.isUnusedForRemovalData)
                assertFalse(method.isUnusedForDummyData)
            }

            // org.apache.commons.cli.Parser.flatten(Options, String[], boolean)
            // Not in stack trace, but is the static call target of
            // List tokenList = Arrays.asList(flatten(this.options, arguments, stopAtNonOption));
            //                                ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
            run {
                val cu = reducer.context.loadedCompilationUnits
                    .single {
                        it.primaryType.flatMap { it.fullyQualifiedName }.orElse(null) == "org.apache.commons.cli.Parser"
                    }

                val method = cu.primaryType.get()
                    .getMethodsBySignature("flatten", "Options", "String[]", "boolean").single()

                assertTrue(method.inclusionReasonsData.isNotEmpty())
                assertTrue {
                    method.inclusionReasonsData.synchronizedWith {
                        filterIsInstance<ReachableReason.DirectlyReferencedByNode>().isNotEmpty()
                    }
                }
                assertFalse(method.isUnusedForRemovalData)
                assertFalse(method.isUnusedForDummyData)
            }

            // org.apache.commons.cli.PosixParser.flatten(Options, String[], boolean)
            run {
                val cu = reducer.context.loadedCompilationUnits
                    .single {
                        it.primaryType.flatMap { it.fullyQualifiedName }.orElse(null) == "org.apache.commons.cli.PosixParser"
                    }

                val method = cu.primaryType.get()
                    .getMethodsBySignature("flatten", "Options", "String[]", "boolean").single()

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
    }
}