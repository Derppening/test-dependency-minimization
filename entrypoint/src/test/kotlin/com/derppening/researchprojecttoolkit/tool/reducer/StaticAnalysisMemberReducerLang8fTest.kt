package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.tool.compilation.CompilerProxy
import com.derppening.researchprojecttoolkit.tool.reachability.ReachableReason
import com.derppening.researchprojecttoolkit.tool.transform.TagStaticAnalysisMemberUnusedDecls.Companion.isTypeReachable
import org.junit.jupiter.api.Nested
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class StaticAnalysisMemberReducerLang8fTest : StaticAnalysisMemberReducerIntegTest() {

    override val project = TestProjects.Lang8f

    @Nested
    inner class FastDateFormat_PrinterTest {

        @Nested
        inner class TestCalendarTimezoneRespected : OnTestCase("org.apache.commons.lang3.time.FastDateFormat_PrinterTest::testCalendarTimezoneRespected", true) {

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
             * java.lang.IllegalStateException: Cannot find class with name org.apache.commons.lang3.time.FastDateFormat_PrinterTest in source roots [/workspace/src/main/java, /workspace/src/test/java]
             * 	at com.derppening.researchprojecttoolkit.model.EntrypointSpec$Companion.fromArg(EntrypointSpec.kt:46)
             * 	at com.derppening.researchprojecttoolkit.defects4j.IndividualTestCaseComparisonRunner.runSingle-OsBMiQA(IndividualTestCaseComparisonRunner.kt:223)
             * 	at com.derppening.researchprojecttoolkit.defects4j.IndividualTestCaseComparisonRunner.runSingle(IndividualTestCaseComparisonRunner.kt:288)
             * 	at com.derppening.researchprojecttoolkit.defects4j.IndividualTestCaseComparisonRunner.run(IndividualTestCaseComparisonRunner.kt:355)
             * 	at com.derppening.researchprojecttoolkit.commands.Defects4JCompare.runCompare(Defects4JCmd.kt:292)
             * 	at com.derppening.researchprojecttoolkit.commands.Defects4JCompare.run(Defects4JCmd.kt:332)
             * 	at com.github.ajalt.clikt.parsers.Parser.parse(Parser.kt:198)
             * 	at com.github.ajalt.clikt.parsers.Parser.parse(Parser.kt:211)
             * 	at com.github.ajalt.clikt.parsers.Parser.parse(Parser.kt:211)
             * 	at com.github.ajalt.clikt.parsers.Parser.parse(Parser.kt:18)
             * 	at com.github.ajalt.clikt.core.CliktCommand.parse(CliktCommand.kt:400)
             * 	at com.github.ajalt.clikt.core.CliktCommand.parse$default(CliktCommand.kt:397)
             * 	at com.github.ajalt.clikt.core.CliktCommand.main(CliktCommand.kt:415)
             * 	at com.github.ajalt.clikt.core.CliktCommand.main(CliktCommand.kt:440)
             * 	at com.derppening.researchprojecttoolkit.MainKt.main(Main.kt:68)
             * ```
             */
            @Test
            fun `Regression-00`() {
                val cu = reducer.context.getCUByPrimaryTypeName("org.apache.commons.lang3.time.FastDateFormat_PrinterTest")
                assumeTrue(cu != null)

                val type = cu.primaryType.get().asClassOrInterfaceDeclaration()
                assertTrue(type.inclusionReasonsData.any { it is ReachableReason.ByEntrypoint })

                val typeReachable = isTypeReachable(reducer.context, type, enableAssertions, noCache = true)
                assertTrue(typeReachable)
            }
        }
    }
}