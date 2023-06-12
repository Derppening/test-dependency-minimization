package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.tool.compilation.CompilerProxy
import com.derppening.researchprojecttoolkit.tool.reachability.ReachableReason
import com.derppening.researchprojecttoolkit.tool.transform.NodeTransformDecision
import com.derppening.researchprojecttoolkit.tool.transform.TagStaticAnalysisMemberUnusedDecls
import com.derppening.researchprojecttoolkit.util.synchronizedWith
import org.junit.jupiter.api.Nested
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StaticAnalysisMemberReducerCli21fTest : StaticAnalysisMemberReducerIntegTest() {

    override val project = TestProjects.Cli21f

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
         * java.lang.AssertionError: This method should not be reached! Signature: setParent(Option)
         *   at org.apache.commons.cli2.option.OptionImpl.setParent(OptionImpl.java:123)
         *   at org.apache.commons.cli2.option.GroupImpl.<init>(GroupImpl.java:89)
         *   at org.apache.commons.cli2.builder.GroupBuilder.create(GroupBuilder.java:54)
         *   at org.apache.commons.cli2.bug.BugCLI150Test.testNegativeNumber(BugCLI150Test.java:45)
         * ```
         */
        @Test
        fun `Regression-00`() {
            // org.apache.commons.cli2.bug.BugCLI150Test.testNegativeNumber()
            run {
                val cu = reducer.context.loadedCompilationUnits
                    .single {
                        it.primaryType.flatMap { it.fullyQualifiedName }.orElse(null) == "org.apache.commons.cli2.bug.BugCLI150Test"
                    }

                val method = cu.primaryType.get()
                    .getMethodsBySignature("testNegativeNumber").single()

                assertTrue(method.inclusionReasonsData.isNotEmpty())
                assertTrue {
                    method.inclusionReasonsData.synchronizedWith {
                        filterIsInstance<ReachableReason.Entrypoint>().isNotEmpty()
                    }
                }
                assertFalse(method.isUnusedForRemovalData)
                assertFalse(method.isUnusedForDummyData)
            }

            // org.apache.commons.cli2.builder.GroupBuilder.create()
            run {
                val cu = reducer.context.loadedCompilationUnits
                    .single {
                        it.primaryType.flatMap { it.fullyQualifiedName }.orElse(null) == "org.apache.commons.cli2.builder.GroupBuilder"
                    }

                val method = cu.primaryType.get()
                    .getMethodsBySignature("create").single()

                assertTrue(method.inclusionReasonsData.isNotEmpty())
                assertTrue {
                    method.inclusionReasonsData.synchronizedWith {
                        filterIsInstance<ReachableReason.ReferencedBySymbolName>().isNotEmpty()
                    }
                }
                assertFalse(method.isUnusedForRemovalData)
                assertFalse(method.isUnusedForDummyData)
            }

            // org.apache.commons.cli2.option.GroupImpl.<init>(List, String, String, int, int, boolean)
            run {
                val cu = reducer.context.loadedCompilationUnits
                    .single {
                        it.primaryType.flatMap { it.fullyQualifiedName }.orElse(null) == "org.apache.commons.cli2.option.GroupImpl"
                    }

                val method = cu.primaryType.get()
                    .getConstructorByParameterTypes("List", "String", "String", "int", "int", "boolean").get()

                assertTrue(method.inclusionReasonsData.isNotEmpty())
                assertTrue {
                    method.inclusionReasonsData.synchronizedWith {
                        filterIsInstance<ReachableReason.ReferencedBySymbolName>().isNotEmpty()
                    }
                }
                assertFalse(method.isUnusedForRemovalData)
                assertFalse(method.isUnusedForDummyData)
            }

            // org.apache.commons.cli2.Option.setParent(Option)
            // Static call target of `org.apache.commons.cli2.Option.setParent(Option)`
            run {
                val cu = reducer.context.loadedCompilationUnits
                    .single {
                        it.primaryType.flatMap { it.fullyQualifiedName }.orElse(null) == "org.apache.commons.cli2.Option"
                    }

                val method = cu.primaryType.get()
                    .getMethodsBySignature("setParent", "Option").single()

                assertTrue(method.inclusionReasonsData.isNotEmpty())
                assertTrue {
                    method.inclusionReasonsData.synchronizedWith {
                        filterIsInstance<ReachableReason.ReferencedBySymbolName>().isNotEmpty()
                    }
                }
                assertFalse(method.isUnusedForRemovalData)
                assertFalse(method.isUnusedForDummyData)
            }

            // org.apache.commons.cli2.option.OptionImpl.setParent(Option)
            run {
                val cu = reducer.context.loadedCompilationUnits
                    .single {
                        it.primaryType.flatMap { it.fullyQualifiedName }.orElse(null) == "org.apache.commons.cli2.option.OptionImpl"
                    }

                val method = cu.primaryType.get()
                    .getMethodsBySignature("setParent", "Option").single()

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
         * java.lang.AssertionError: This method should not be reached! Signature: getInitialSeparator()
         *   at org.apache.commons.cli2.option.ArgumentImpl.getInitialSeparator(ArgumentImpl.java:186)
         *   at org.apache.commons.cli2.option.ParentImpl.process(ParentImpl.java:62)
         *   at org.apache.commons.cli2.option.GroupImpl.process(GroupImpl.java:160)
         *   at org.apache.commons.cli2.commandline.Parser.parse(Parser.java:86)
         *   at org.apache.commons.cli2.bug.BugCLI150Test.testNegativeNumber(BugCLI150Test.java:48)
         * ```
         */
        @Test
        fun `Regression-01`() {
            // org.apache.commons.cli2.bug.BugCLI150Test.testNegativeNumber()
            run {
                val cu = reducer.context.loadedCompilationUnits
                    .single {
                        it.primaryType.flatMap { it.fullyQualifiedName }.orElse(null) == "org.apache.commons.cli2.bug.BugCLI150Test"
                    }

                val method = cu.primaryType.get()
                    .getMethodsBySignature("testNegativeNumber").single()

                assertTrue(method.inclusionReasonsData.isNotEmpty())
                assertTrue {
                    method.inclusionReasonsData.synchronizedWith {
                        filterIsInstance<ReachableReason.Entrypoint>().isNotEmpty()
                    }
                }
                assertFalse(method.isUnusedForRemovalData)
                assertFalse(method.isUnusedForDummyData)
            }

            // org.apache.commons.cli2.commandline.Parser.parse(String[])
            run {
                val cu = reducer.context.loadedCompilationUnits
                    .single {
                        it.primaryType.flatMap { it.fullyQualifiedName }.orElse(null) == "org.apache.commons.cli2.commandline.Parser"
                    }

                val method = cu.primaryType.get()
                    .getMethodsBySignature("parse", "String[]").single()

                assertTrue(method.inclusionReasonsData.isNotEmpty())
                assertTrue {
                    method.inclusionReasonsData.synchronizedWith {
                        filterIsInstance<ReachableReason.ReferencedBySymbolName>().isNotEmpty()
                    }
                }
                assertFalse(method.isUnusedForRemovalData)
                assertFalse(method.isUnusedForDummyData)
            }

            // org.apache.commons.cli2.Option.process(WriteableCommandLine, ListIterator)
            // Static call target of `org.apache.commons.cli2.option.GroupImpl.process(WriteableCommandLine, ListIterator)`
            run {
                val cu = reducer.context.loadedCompilationUnits
                    .single {
                        it.primaryType.flatMap { it.fullyQualifiedName }.orElse(null) == "org.apache.commons.cli2.Option"
                    }

                val method = cu.primaryType.get()
                    .getMethodsBySignature("process", "WriteableCommandLine", "ListIterator").single()

                assertTrue(method.inclusionReasonsData.isNotEmpty())
                assertTrue {
                    method.inclusionReasonsData.synchronizedWith {
                        filterIsInstance<ReachableReason.ReferencedBySymbolName>().isNotEmpty()
                    }
                }
                assertFalse(method.isUnusedForRemovalData)
                assertFalse(method.isUnusedForDummyData)
            }

            // org.apache.commons.cli2.option.GroupImpl.process(WriteableCommandLine, ListIterator)
            run {
                val cu = reducer.context.loadedCompilationUnits
                    .single {
                        it.primaryType.flatMap { it.fullyQualifiedName }.orElse(null) == "org.apache.commons.cli2.option.GroupImpl"
                    }

                val method = cu.primaryType.get()
                    .getMethodsBySignature("process", "WriteableCommandLine", "ListIterator").single()

                assertTrue(method.inclusionReasonsData.isNotEmpty())
                assertTrue {
                    method.inclusionReasonsData.synchronizedWith {
                        filterIsInstance<ReachableReason.TransitiveMethodCallTarget>().isNotEmpty()
                    }
                }
                assertFalse(method.isUnusedForRemovalData)
                assertFalse(method.isUnusedForDummyData)
            }

            // org.apache.commons.cli2.Option.process(WriteableCommandLine, ListIterator)
            // Static call target of `org.apache.commons.cli2.option.ParentImpl.process(WriteableCommandLine, ListIterator)`
            // Already checked in a previous stack (`org.apache.commons.cli2.option.GroupImpl.process(WriteableCommandLine, ListIterator)`)

            // org.apache.commons.cli2.option.ParentImpl.process(WriteableCommandLine, ListIterator)
            run {
                val cu = reducer.context.loadedCompilationUnits
                    .single {
                        it.primaryType.flatMap { it.fullyQualifiedName }.orElse(null) == "org.apache.commons.cli2.option.ParentImpl"
                    }

                val method = cu.primaryType.get()
                    .getMethodsBySignature("process", "WriteableCommandLine", "ListIterator").single()

                assertTrue(method.inclusionReasonsData.isNotEmpty())
                assertTrue {
                    method.inclusionReasonsData.synchronizedWith {
                        filterIsInstance<ReachableReason.TransitiveMethodCallTarget>().isNotEmpty()
                    }
                }
                assertFalse(method.isUnusedForRemovalData)
                assertFalse(method.isUnusedForDummyData)
            }

            // org.apache.commons.cli2.Argument.getInitialSeparator()
            // Static call target of `org.apache.commons.cli2.option.ArgumentImpl.getInitialSeparator()`
            run {
                val cu = reducer.context.loadedCompilationUnits
                    .single {
                        it.primaryType.flatMap { it.fullyQualifiedName }.orElse(null) == "org.apache.commons.cli2.Argument"
                    }

                val method = cu.primaryType.get()
                    .getMethodsBySignature("getInitialSeparator").single()

                assertTrue(method.inclusionReasonsData.isNotEmpty())
                assertTrue {
                    method.inclusionReasonsData.synchronizedWith {
                        filterIsInstance<ReachableReason.ReferencedBySymbolName>().isNotEmpty()
                    }
                }
                assertFalse(method.isUnusedForRemovalData)
                assertFalse(method.isUnusedForDummyData)
            }

            // org.apache.commons.cli2.option.ArgumentImpl.getInitialSeparator()
            run {
                val cu = reducer.context.loadedCompilationUnits
                    .single {
                        it.primaryType.flatMap { it.fullyQualifiedName }.orElse(null) == "org.apache.commons.cli2.option.ArgumentImpl"
                    }

                val method = cu.primaryType.get()
                    .getMethodsBySignature("getInitialSeparator").single()

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
         *        org.apache.commons.cli2.commandline.CommandLineImpl.hasOption(CommandLineImpl.java:33)
         *        org.apache.commons.cli2.commandline.Parser.parse(Parser.java:94)
         *        org.apache.commons.cli2.bug.BugCLI150Test.testNegativeNumber(BugCLI150Test.java:48)
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
                .getCUByPrimaryTypeName("org.apache.commons.cli2.commandline.Parser")
            assumeTrue(srcCU != null)
            val srcType = srcCU.primaryType.get()
            val srcMethod = srcType.getMethodsBySignature("parse", "String[]").single()

            assumeTrue {
                val methodDecisionCached = TagStaticAnalysisMemberUnusedDecls.decideForMethod(
                    reducer.context,
                    srcMethod,
                    enableAssertions,
                    noCache = true
                )
                val methodDecisionNoCache = TagStaticAnalysisMemberUnusedDecls.decideForMethod(
                    reducer.context,
                    srcMethod,
                    enableAssertions,
                    noCache = false
                )

                methodDecisionCached == methodDecisionNoCache
            }

            val tgtCU = reducer.context
                .getCUByPrimaryTypeName("org.apache.commons.cli2.commandline.CommandLineImpl")
            assumeTrue(tgtCU != null)
            val tgtType = tgtCU.primaryType.get()
            val typeReachableNoCache = TagStaticAnalysisMemberUnusedDecls.isTypeReachable(
                reducer.context,
                tgtType,
                enableAssertions,
                noCache = true
            )
            assertTrue(typeReachableNoCache)
            val typeReachableCached = TagStaticAnalysisMemberUnusedDecls.isTypeReachable(
                reducer.context,
                tgtType,
                enableAssertions,
                noCache = false
            )
            assertTrue(typeReachableCached)

            val tgtMethod = tgtType
                .getMethodsBySignature("hasOption", "String").single()
            val methodReachableNoCache = TagStaticAnalysisMemberUnusedDecls.decideForMethod(
                reducer.context,
                tgtMethod,
                enableAssertions,
                noCache = true
            )
            assertEquals(NodeTransformDecision.NO_OP, methodReachableNoCache)
            val methodReachableCached = TagStaticAnalysisMemberUnusedDecls.decideForMethod(
                reducer.context,
                tgtMethod,
                enableAssertions,
                noCache = false
            )
            assertEquals(NodeTransformDecision.NO_OP, methodReachableCached)
        }
    }
}