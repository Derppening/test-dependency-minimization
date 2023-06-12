package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.tool.compilation.CompilerProxy
import com.derppening.researchprojecttoolkit.tool.reachability.ReachableReason
import com.derppening.researchprojecttoolkit.util.synchronizedWith
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Nested
import java.nio.file.Path
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StaticAnalysisMemberReducerCli27fTest : StaticAnalysisMemberReducerIntegTest() {

    override val project = TestProjects.Cli27f

    @Ignore
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

        @Test
        fun `Unconditionally Keep Super-Invoking Constructors`() {
            val cu = reducer.context.loadedCompilationUnits
                .single {
                    it.primaryType.flatMap { it.fullyQualifiedName }.orElse(null) == "org.apache.commons.cli.OptionTest"
                }

            val constructor = cu.primaryType.get()
                .members
                .single { it.isClassOrInterfaceDeclaration && it.asClassOrInterfaceDeclaration().nameAsString == "DefaultOption" }
                .asClassOrInterfaceDeclaration()
                .getConstructorByParameterTypes("String", "String", "String")
                .also { assumeTrue(it.isPresent) }
                .get()

            assertTrue(constructor.inclusionReasonsData.isNotEmpty())
            assertFalse(constructor.isUnusedForRemovalData)
        }

        @Test
        fun `Regression-00`() {
            // org.apache.commons.cli.ParserTestCase.testSimpleLong()
            run {
                val cu = reducer.context.loadedCompilationUnits
                    .single {
                        it.primaryType.flatMap { it.fullyQualifiedName }.orElse(null) == "org.apache.commons.cli.ParserTestCase"
                    }

                val method = cu.primaryType.get()
                    .getMethodsBySignature("testSimpleLong").single()

                assertTrue(method.inclusionReasonsData.isNotEmpty())
                assertTrue {
                    method.inclusionReasonsData.synchronizedWith {
                        filterIsInstance<ReachableReason.Entrypoint>().isNotEmpty()
                    }
                }
                assertFalse(method.isUnusedForRemovalData)
                assertFalse(method.isUnusedForDummyData)
            }

            // org.apache.commons.cli.CommandLineParser.parse(Options, String[])
            // Not in stack trace, but is the static call target of
            // CommandLine cl = parser.parse(options, args);
            //                  ^^^^^^^^^^^^^^^^^^^^^^^^^^^
            run {
                val cu = reducer.context.loadedCompilationUnits
                    .single {
                        it.primaryType.flatMap { it.fullyQualifiedName }.orElse(null) == "org.apache.commons.cli.CommandLineParser"
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
                        filterIsInstance<ReachableReason.TransitiveMethodCallTarget>().isNotEmpty()
                    }
                }
                assertFalse(method.isUnusedForRemovalData)
                assertFalse(method.isUnusedForDummyData)
            }
        }
    }
}