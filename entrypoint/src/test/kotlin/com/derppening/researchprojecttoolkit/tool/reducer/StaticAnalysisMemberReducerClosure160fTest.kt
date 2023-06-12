package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.tool.compilation.CompilerProxy
import com.derppening.researchprojecttoolkit.tool.transform.NodeTransformDecision
import com.derppening.researchprojecttoolkit.tool.transform.TagStaticAnalysisMemberUnusedDecls.Companion.decideForFieldDecl
import com.derppening.researchprojecttoolkit.tool.transform.TagStaticAnalysisMemberUnusedDecls.Companion.isTypeReachable
import com.derppening.researchprojecttoolkit.util.getFieldVariableDecl
import com.derppening.researchprojecttoolkit.util.getTypeByName
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import org.junit.jupiter.api.Nested
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class StaticAnalysisMemberReducerClosure160fTest : StaticAnalysisMemberReducerIntegTest() {

    override val project = TestProjects.Closure160f

    @Nested
    inner class CommandLineRunnerTest {

        @Nested
        inner class TestCheckSymbolsOverrideForQuiet : OnTestCase("com.google.javascript.jscomp.CommandLineRunnerTest::testCheckSymbolsOverrideForQuiet", true) {

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

                val junitRunner = tryTestNoAssertion(outputDir!!.path, JAVA_17_HOME) {
                    add("-cp")
                    add("${project.testCpJars.joinToString(":")}:${sourceRoots.joinToString(":")}:build/classes")

                    add("-m")
                    add(testCase.toJUnitMethod())
                }
                assertTrue(junitRunner.exitCode.isFailure)
                assertTrue {
                    junitRunner.stdout
                        .joinToString("\n")
                        .contains("This method should not be reached! Signature: BooleanOptionHandler(CmdLineParser, OptionDef, Setter)")
                }
            }

            /**
             * ```
             * src/com/google/javascript/jscomp/CommandLineRunner.java:161: error: cannot find symbol
             *          @Option(name = "--jscomp_error", handler = WarningGuardErrorOptionHandler.class, usage = "Make the named class of warnings an error. Options:" + DiagnosticGroups.DIAGNOSTIC_GROUP_NAMES)
             *                                                     ^
             *    symbol:   class WarningGuardErrorOptionHandler
             *    location: class Flags
             * src/com/google/javascript/jscomp/CommandLineRunner.java:166: error: cannot find symbol
             *          @Option(name = "--jscomp_warning", handler = WarningGuardWarningOptionHandler.class, usage = "Make the named class of warnings a normal warning. " + "Options:" + DiagnosticGroups.DIAGNOSTIC_GROUP_NAMES)
             *                                                       ^
             *    symbol:   class WarningGuardWarningOptionHandler
             *    location: class Flags
             * src/com/google/javascript/jscomp/CommandLineRunner.java:171: error: cannot find symbol
             *          @Option(name = "--jscomp_off", handler = WarningGuardOffOptionHandler.class, usage = "Turn off the named class of warnings. Options:" + DiagnosticGroups.DIAGNOSTIC_GROUP_NAMES)
             *                                                   ^
             *    symbol:   class WarningGuardOffOptionHandler
             *    location: class Flags
             * ```
             */
            @Test
            fun `Include Values in Annotations included by TransitiveClassMember`() {
                val cu = reducer.context
                    .getCUByPrimaryTypeName("com.google.javascript.jscomp.CommandLineRunner")
                assumeTrue(cu != null)

                val type = cu.primaryType.get().asClassOrInterfaceDeclaration()
                    .getTypeByName<ClassOrInterfaceDeclaration>("Flags")
                val srcField = type.getFieldVariableDecl("jscomp_error")
                assumeTrue {
                    decideForFieldDecl(
                        reducer.context,
                        srcField,
                        enableAssertions,
                        noCache = true
                    ) != NodeTransformDecision.REMOVE
                }
                assumeTrue {
                    decideForFieldDecl(
                        reducer.context,
                        srcField,
                        enableAssertions,
                        noCache = false
                    ) != NodeTransformDecision.REMOVE
                }

                val tgtType = type
                    .getTypeByName<ClassOrInterfaceDeclaration>("WarningGuardErrorOptionHandler")
                assertTrue {
                    isTypeReachable(
                        reducer.context,
                        tgtType,
                        enableAssertions,
                        noCache = true
                    )
                }
                assertTrue {
                    isTypeReachable(
                        reducer.context,
                        tgtType,
                        enableAssertions,
                        noCache = false
                    )
                }
            }
        }
    }
}