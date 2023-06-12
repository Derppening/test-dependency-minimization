package com.derppening.researchprojecttoolkit.tool

import com.derppening.researchprojecttoolkit.GlobalConfiguration
import com.derppening.researchprojecttoolkit.model.CoverageData
import com.derppening.researchprojecttoolkit.model.EntrypointSpec
import com.derppening.researchprojecttoolkit.tool.facade.FuzzySymbolSolver
import com.derppening.researchprojecttoolkit.tool.facade.typesolver.NamedJarTypeSolver
import com.derppening.researchprojecttoolkit.tool.facade.typesolver.NamedJavaParserTypeSolver
import com.derppening.researchprojecttoolkit.tool.facade.typesolver.PartitionedTypeSolver
import com.derppening.researchprojecttoolkit.tool.reducer.CoverageBasedReducer
import com.derppening.researchprojecttoolkit.tool.reducer.StaticAnalysisClassReducer
import com.derppening.researchprojecttoolkit.tool.reducer.StaticAnalysisMemberReducer
import com.derppening.researchprojecttoolkit.util.Defects4JWorkspace.*
import com.derppening.researchprojecttoolkit.util.TestCase
import com.derppening.researchprojecttoolkit.util.ToolOutputDirectory
import com.derppening.researchprojecttoolkit.util.createParserConfiguration
import com.github.javaparser.JavaParser
import com.github.javaparser.ParserConfiguration
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Path
import kotlin.io.path.Path

object TestProjects {

    abstract class Project {

        abstract val projectRev: ProjectRev

        open val basePath: Path
            get() = Path("/")
                .resolve(projectRev.projectId.toString().filter { it.isLetter() }.lowercase())
                .resolve("${projectRev.versionId}")

        protected abstract val srcTypeSolverPaths: List<Path>
        protected open val srcCpTypeSolverPaths: List<Path> = emptyList()
        protected open val compileCpTypeSolverPaths: List<Path>? = null
        protected abstract val testCpTypeSolverPaths: List<Path>

        abstract val entrypoints: List<String>

        val sourceRoots: List<Path>
            get() = srcTypeSolverPaths.map { getProjectResourcePath(it) }
        val cpSourceRoots: List<Path>
            get() = srcCpTypeSolverPaths.map { getProjectResourcePath(it) }
        val compileCpJars: List<Path>?
            get() = compileCpTypeSolverPaths?.map { getProjectResourcePath(it) }
        val testCpJars: List<Path>
            get() = testCpTypeSolverPaths.map { getProjectResourcePath(it) }

        private val srcTypeSolvers: List<JavaParserTypeSolver>
            get() = sourceRoots.map { NamedJavaParserTypeSolver(it) }
        private val srcCpTypeSolvers: List<JavaParserTypeSolver>
            get() = cpSourceRoots.map { NamedJavaParserTypeSolver(it) }
        private val compileCpTypeSolvers: List<JarTypeSolver>?
            get() = compileCpJars?.map { NamedJarTypeSolver(it) }
        private val testCpTypeSolvers: List<JarTypeSolver>
            get() = testCpJars.map { NamedJarTypeSolver(it) }

        private val entrypointSpecs: List<EntrypointSpec>
            get() = entrypoints.map {
                EntrypointSpec.fromArg(
                    it,
                    srcTypeSolverPaths.map { getProjectResourcePath(it) }
                )
            }

        fun getProjectResourcePath(): Path = getProjectResourcePath("")
        fun getProjectResourcePath(relPath: String): Path = getProjectResourcePath(Path(relPath))
        fun getProjectResourcePath(relPath: Path): Path = getResourcePath(basePath.resolve(relPath))

        fun parseSourceFile(
            relPath: String,
            symbolSolver: FuzzySymbolSolver,
            parserConfig: ParserConfiguration.() -> Unit = {}
        ): CompilationUnit {
            val parser = createParserConfiguration(symbolSolver) {
                parserConfig()
            }.let { JavaParser(it) }
            val parseRes = parser.parse(getProjectResourcePath(relPath))
            assumeTrue(parseRes.isSuccessful) { "Encountered errors while parsing source file: ${parseRes.problems}" }

            return parseRes.result.get()
        }

        fun getTypeSolver(): PartitionedTypeSolver = PartitionedTypeSolver(
            srcTypeSolvers,
            testCpTypeSolvers + srcCpTypeSolvers + ReflectionTypeSolver(),
            true
        )

        fun getSymbolSolver(): FuzzySymbolSolver = FuzzySymbolSolver(getTypeSolver())

        fun getReducerContext(): ReducerContext = ReducerContext(
            srcTypeSolverPaths.map { getProjectResourcePath(it) },
            getTypeSolver()
        )

        fun getCoverageBasedReducer(
            entrypoint: EntrypointSpec,
            enableAssertions: Boolean,
            threads: Int?
        ): CoverageBasedReducer {
            return CoverageBasedReducer(
                testCpTypeSolverPaths.joinToString(":") { getProjectResourcePath(it).toString() },
                srcCpTypeSolverPaths.joinToString(":") { getProjectResourcePath(it).toString() },
                srcTypeSolverPaths.map { getProjectResourcePath(it) },
                listOf(entrypoint),
                enableAssertions,
                threads,
                projectRev,
                TestCase.fromEntrypointSpec(entrypoint)
            )
        }

        fun getStaticAnalysisClassReducer(
            enableAssertions: Boolean,
            threads: Int?
        ): StaticAnalysisClassReducer {
            return StaticAnalysisClassReducer(
                testCpTypeSolverPaths.joinToString(":") { getProjectResourcePath(it).toString() },
                srcCpTypeSolverPaths.joinToString(":") { getProjectResourcePath(it).toString() },
                srcTypeSolverPaths.map { getProjectResourcePath(it) },
                entrypointSpecs,
                enableAssertions,
                threads
            )
        }

        fun getStaticAnalysisClassReducer(
            entrypoint: EntrypointSpec,
            enableAssertions: Boolean,
            threads: Int?
        ): StaticAnalysisClassReducer {
            return StaticAnalysisClassReducer(
                testCpTypeSolverPaths.joinToString(":") { getProjectResourcePath(it).toString() },
                srcCpTypeSolverPaths.joinToString(":") { getProjectResourcePath(it).toString() },
                srcTypeSolverPaths.map { getProjectResourcePath(it) },
                listOf(entrypoint),
                enableAssertions,
                threads
            )
        }

        fun getStaticAnalysisMemberReducer(
            enableAssertions: Boolean,
            threads: Int?
        ): StaticAnalysisMemberReducer {
            return StaticAnalysisMemberReducer(
                testCpTypeSolverPaths.joinToString(":") { getProjectResourcePath(it).toString() },
                srcCpTypeSolverPaths.joinToString(":") { getProjectResourcePath(it).toString() },
                srcTypeSolverPaths.map { getProjectResourcePath(it) },
                entrypointSpecs,
                enableAssertions,
                threads
            )
        }

        fun getStaticAnalysisMemberReducer(
            entrypoint: EntrypointSpec,
            enableAssertions: Boolean,
            threads: Int?
        ): StaticAnalysisMemberReducer {
            return StaticAnalysisMemberReducer(
                testCpTypeSolverPaths.joinToString(":") { getProjectResourcePath(it).toString() },
                srcCpTypeSolverPaths.joinToString(":") { getProjectResourcePath(it).toString() },
                srcTypeSolverPaths.map { getProjectResourcePath(it) },
                listOf(entrypoint),
                enableAssertions,
                threads
            )
        }

        fun getBaselineDir(entrypoint: EntrypointSpec?): ToolOutputDirectory =
            ToolOutputDirectory(
                GlobalConfiguration.INSTANCE.cmdlineOpts.baselineDir,
                projectRev,
                entrypoint?.let { TestCase.fromEntrypointSpec(it) },
                readOnly = true
            )

        fun getCoverage(entrypoint: EntrypointSpec): CoverageData = getBaselineDir(entrypoint).readCoverageData()

        fun parseEntrypoint(entrypoint: String): EntrypointSpec = EntrypointSpec.fromArg(
            entrypoint,
            sourceRoots
        )
    }

    object Chart1f : Project() {

        override val projectRev = ProjectRev(ProjectID.CHART, 1, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("source"),
            Path("tests"),
        )
        override val testCpTypeSolverPaths = listOf(Path("lib/servlet.jar"))

        override val entrypoints = listOf(
            "org.jfree.chart.renderer.category.junit.AbstractCategoryItemRendererTests::test2947660",
        )
    }

    object Cli1f : Project() {

        override val projectRev = ProjectRev(ProjectID.CLI, 1, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/java"),
            Path("src/test")
        )

        override val testCpTypeSolverPaths = listOf(
            Path("target/lib/commons-lang/jars/commons-lang-2.1.jar"),
            Path("/d4j/framework/projects/Cli/lib/junit/junit/4.12/junit-4.12.jar"),
        )

        override val entrypoints = listOf(
            "org.apache.commons.cli.bug.BugCLI13Test::testCLI13",
        )
    }

    object Cli13f : Project() {

        override val projectRev = ProjectRev(ProjectID.CLI, 13, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/java"),
            Path("src/test")
        )

        override val testCpTypeSolverPaths = listOf(
            Path("target/lib/commons-lang/jars/commons-lang-2.1.jar"),
            Path("target/lib/junit/jars/junit-3.8.1.jar"),
            Path("target/lib/jdepend/jars/jdepend-2.5.jar"),
        )

        override val entrypoints = listOf(
            "org.apache.commons.cli2.bug.BugLoopingOptionLookAlikeTest::testLoopingOptionLookAlike2",
        )
    }

    object Cli16f : Project() {

        override val projectRev = ProjectRev(ProjectID.CLI, 16, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/java"),
            Path("src/test")
        )

        override val testCpTypeSolverPaths = listOf(
            Path("target/lib/commons-lang/jars/commons-lang-2.1.jar"),
            Path("target/lib/junit/jars/junit-3.8.1.jar"),
            Path("target/lib/jdepend/jars/jdepend-2.5.jar"),
        )

        override val entrypoints = listOf(
            "org.apache.commons.cli2.bug.BugCLI123Test::testMultipleChildOptions",
            "org.apache.commons.cli2.bug.BugCLI123Test::testParentOptionAndChildOption",
            "org.apache.commons.cli2.bug.BugCLI123Test::testSingleChildOption",
            "org.apache.commons.cli2.commandline.DefaultingCommandLineTest::testGetOptions_Order",
            "org.apache.commons.cli2.commandline.PreferencesCommandLineTest::testGetOptions_Order",
            "org.apache.commons.cli2.commandline.PropertiesCommandLineTest::testGetOptions_Order",
            "org.apache.commons.cli2.commandline.WriteableCommandLineImplTest::testGetOptions_Order",
        )
    }

    object Cli17f : Project() {

        override val projectRev = ProjectRev(ProjectID.CLI, 17, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/java"),
            Path("src/test"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("target/lib/junit-3.8.1.jar"),
        )

        override val entrypoints = listOf(
            "org.apache.commons.cli.PosixParserTest::testStopBursting",
        )
    }

    object Cli21f : Project() {

        override val projectRev = ProjectRev(ProjectID.CLI, 21, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/java"),
            Path("src/test")
        )

        override val testCpTypeSolverPaths = listOf(
            Path("target/lib/commons-lang/jars/commons-lang-2.1.jar"),
            Path("target/lib/junit/jars/junit-3.8.1.jar"),
            Path("target/lib/jdepend/jars/jdepend-2.5.jar"),
        )

        override val entrypoints = listOf(
            "org.apache.commons.cli2.bug.BugCLI150Test::testNegativeNumber",
        )
    }

    object Cli27f : Project() {

        override val projectRev = ProjectRev(ProjectID.CLI, 27, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/java"),
            Path("src/test")
        )

        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/Cli/lib/junit/junit/3.8.2/junit-3.8.2.jar"),
        )

        override val entrypoints = listOf(
            "org.apache.commons.cli.BasicParserTest::testOptionGroupLong",
            "org.apache.commons.cli.GnuParserTest::testOptionGroupLong",
            "org.apache.commons.cli.PosixParserTest::testOptionGroupLong",
        )
    }

    object Cli40f : Project() {

        override val projectRev = ProjectRev(ProjectID.CLI, 40, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/main/java"),
            Path("src/test/java")
        )

        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/Cli/lib/junit/junit/4.12/junit-4.12.jar"),
            Path("/d4j/framework/projects/Cli/lib/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar")
        )

        override val entrypoints = listOf(
            "org.apache.commons.cli.TypeHandlerTest::testCreateValueInteger_failure",
        )
    }

    object Codec1f : Project() {

        override val projectRev = ProjectRev(ProjectID.CODEC, 1, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/java"),
            Path("src/test"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/lib/junit-4.11.jar"),
            Path("/d4j/framework/projects/Codec/lib/junit/junit/4.12/junit-4.12.jar"),
            Path("/d4j/framework/projects/Codec/lib/org/apache/commons/commons-lang3/3.4/commons-lang3-3.4.jar"),
            Path("/d4j/framework/projects/Codec/lib/org/apache/commons/commons-lang3/3.8.1/commons-lang3-3.8.1.jar"),
            Path("/d4j/framework/projects/Codec/lib/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar"),
        )

        override val entrypoints = listOf(
            "org.apache.commons.codec.language.CaverphoneTest::testLocaleIndependence",
            "org.apache.commons.codec.language.DoubleMetaphoneTest::testLocaleIndependence",
            "org.apache.commons.codec.language.MetaphoneTest::testLocaleIndependence",
            "org.apache.commons.codec.language.RefinedSoundexTest::testLocaleIndependence",
            "org.apache.commons.codec.language.SoundexTest::testLocaleIndependence",
        )
    }

    object Codec3f : Project() {

        override val projectRev = ProjectRev(ProjectID.CODEC, 3, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/java"),
            Path("src/test"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/lib/junit-4.11.jar"),
            Path("/d4j/framework/projects/Codec/lib/junit/junit/4.12/junit-4.12.jar"),
            Path("/d4j/framework/projects/Codec/lib/lib/junit/junit/4.12/junit-4.12.jar"),
            Path("/d4j/framework/projects/Codec/lib/lib/org/apache/commons/commons-lang3/3.4/commons-lang3-3.4.jar"),
            Path("/d4j/framework/projects/Codec/lib/lib/org/apache/commons/commons-lang3/3.8.1/commons-lang3-3.8.1.jar"),
            Path("/d4j/framework/projects/Codec/lib/lib/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar"),
            Path("/d4j/framework/projects/Codec/lib/org/apache/commons/commons-lang3/3.4/commons-lang3-3.4.jar"),
            Path("/d4j/framework/projects/Codec/lib/org/apache/commons/commons-lang3/3.8.1/commons-lang3-3.8.1.jar"),
            Path("/d4j/framework/projects/Codec/lib/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar"),
        )

        override val entrypoints = listOf(
            "org.apache.commons.codec.language.DoubleMetaphone2Test::testDoubleMetaphoneAlternate",
        )
    }

    object Codec4f : Project() {

        override val projectRev = ProjectRev(ProjectID.CODEC, 4, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/java"),
            Path("src/test"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/lib/junit-4.11.jar"),
            Path("/d4j/framework/projects/Codec/lib/junit/junit/4.12/junit-4.12.jar"),
            Path("/d4j/framework/projects/Codec/lib/org/apache/commons/commons-lang3/3.4/commons-lang3-3.4.jar"),
            Path("/d4j/framework/projects/Codec/lib/org/apache/commons/commons-lang3/3.8.1/commons-lang3-3.8.1.jar"),
            Path("/d4j/framework/projects/Codec/lib/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar"),
        )

        override val entrypoints = listOf(
            "org.apache.commons.codec.binary.Base64Codec13Test::testEncoder",
            "org.apache.commons.codec.binary.Base64Codec13Test::testBinaryEncoder",
        )
    }

    object Codec10f : Project() {

        override val projectRev = ProjectRev(ProjectID.CODEC, 10, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/java"),
            Path("src/test")
        )

        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/lib/junit-4.11.jar"),
            Path("/d4j/framework/projects/Codec/lib/junit/junit/4.12/junit-4.12.jar"),
            Path("/d4j/framework/projects/Codec/lib/org/apache/commons/commons-lang3/3.4/commons-lang3-3.4.jar"),
            Path("/d4j/framework/projects/Codec/lib/org/apache/commons/commons-lang3/3.8.1/commons-lang3-3.8.1.jar"),
            Path("/d4j/framework/projects/Codec/lib/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar"),
        )

        override val entrypoints = listOf(
            "org.apache.commons.codec.language.CaverphoneTest::testEndMb",
        )
    }

    object Codec14f : Project() {

        override val projectRev = ProjectRev(ProjectID.CODEC, 14, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/main/java"),
            Path("src/test/java"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/lib/junit-4.11.jar"),
            Path("src/main/resources/commons-lang3-3.8.1.jar"),
            Path("/d4j/framework/projects/Codec/lib/junit/junit/4.12/junit-4.12.jar"),
            Path("/d4j/framework/projects/Codec/lib/org/apache/commons/commons-lang3/3.4/commons-lang3-3.4.jar"),
            Path("/d4j/framework/projects/Codec/lib/org/apache/commons/commons-lang3/3.8.1/commons-lang3-3.8.1.jar"),
            Path("/d4j/framework/projects/Codec/lib/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar"),
        )

        override val entrypoints = listOf(
            "org.apache.commons.codec.language.bm.PhoneticEngineRegressionTest::testCompatibilityWithOriginalVersion"
        )
    }

    object Closure1f : Project() {

        override val projectRev = ProjectRev(ProjectID.CLOSURE, 1, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src"),
            Path("test"),
        )
        override val srcCpTypeSolverPaths = listOf(
            Path("/closure/1f/gen"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("lib/args4j.jar"),
            Path("lib/guava.jar"),
            Path("lib/json.jar"),
            Path("lib/jsr305.jar"),
            Path("lib/protobuf-java.jar"),
            Path("build/lib/rhino.jar"),
            Path("lib/ant.jar"),
            Path("lib/ant-launcher.jar"),
            Path("lib/caja-r4314.jar"),
            Path("lib/jarjar.jar"),
            Path("lib/junit.jar"),
        )

        override val entrypoints = listOf(
            "com.google.javascript.jscomp.CommandLineRunnerTest::testSimpleModeLeavesUnusedParams",
            "com.google.javascript.jscomp.CommandLineRunnerTest::testForwardDeclareDroppedTypes",
            "com.google.javascript.jscomp.CommandLineRunnerTest::testDebugFlag1",
            "com.google.javascript.jscomp.IntegrationTest::testIssue787",
            "com.google.javascript.jscomp.RemoveUnusedVarsTest::testRemoveGlobal1",
            "com.google.javascript.jscomp.RemoveUnusedVarsTest::testRemoveGlobal2",
            "com.google.javascript.jscomp.RemoveUnusedVarsTest::testRemoveGlobal3",
            "com.google.javascript.jscomp.RemoveUnusedVarsTest::testIssue168b",
        )
    }

    object Closure79f : Project() {

        override val projectRev = ProjectRev(ProjectID.CLOSURE, 79, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src"),
            Path("test"),
        )
        override val srcCpTypeSolverPaths = listOf(
            Path("gen"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("lib/args4j.jar"),
            Path("lib/guava.jar"),
            Path("lib/jsr305.jar"),
            Path("lib/libtrunk_rhino_parser_jarjared.jar"),
            Path("lib/protobuf-java.jar"),
            Path("lib/ant.jar"),
            Path("lib/ant-launcher.jar"),
            Path("lib/caja-r4314.jar"),
            Path("lib/json.jar"),
            Path("lib/junit.jar"),
        )

        override val entrypoints = listOf(
            "com.google.javascript.jscomp.NormalizeTest::testIssue",
            "com.google.javascript.jscomp.VarCheckTest::testPropReferenceInExterns1",
            "com.google.javascript.jscomp.VarCheckTest::testPropReferenceInExterns3",
            "com.google.javascript.jscomp.VarCheckTest::testVarReferenceInExterns",
            "com.google.javascript.jscomp.VarCheckTest::testCallInExterns",
        )
    }

    object Closure100f : Project() {

        override val projectRev = ProjectRev(ProjectID.CLOSURE, 100, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src"),
            Path("test"),
        )
        override val srcCpTypeSolverPaths = listOf(
            Path("gen"),
        )
        override val compileCpTypeSolverPaths = listOf(
//            Path("build/classes"),
            Path("lib/ant_deploy.jar"),
            Path("lib/args4j_deploy.jar"),
            Path("lib/google_common_deploy.jar"),
            Path("lib/hamcrest-core-1.1.jar"),
            Path("lib/junit.jar"),
            Path("lib/libtrunk_rhino_parser_jarjared.jar"),
            Path("lib/protobuf_deploy.jar"),
        )
        override val testCpTypeSolverPaths = listOf(
//            Path("build/classes"),
            Path("lib/ant_deploy.jar"),
            Path("lib/args4j_deploy.jar"),
            Path("lib/google_common_deploy.jar"),
            Path("lib/hamcrest-core-1.1.jar"),
            Path("lib/junit.jar"),
            Path("lib/libtrunk_rhino_parser_jarjared.jar"),
            Path("lib/protobuf_deploy.jar"),
//            Path("build/test"),
        )

        override val entrypoints = listOf(
            "com.google.javascript.jscomp.CheckGlobalThisTest::testStaticFunction6",
            "com.google.javascript.jscomp.CheckGlobalThisTest::testStaticFunction7",
            "com.google.javascript.jscomp.CheckGlobalThisTest::testStaticFunction8",
            "com.google.javascript.jscomp.CheckGlobalThisTest::testGlobalThis7",
            "com.google.javascript.jscomp.CheckGlobalThisTest::testStaticMethod2",
            "com.google.javascript.jscomp.CheckGlobalThisTest::testStaticMethod3",
            "com.google.javascript.jscomp.CheckGlobalThisTest::testInnerFunction1",
            "com.google.javascript.jscomp.CheckGlobalThisTest::testInnerFunction2",
            "com.google.javascript.jscomp.CheckGlobalThisTest::testInnerFunction3",
        )
    }

    object Closure101f : Project() {

        override val projectRev = ProjectRev(ProjectID.CLOSURE, 101, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src"),
            Path("test"),
        )
        override val srcCpTypeSolverPaths = listOf(
            Path("gen"),
        )
        override val compileCpTypeSolverPaths = listOf(
//            Path("build/classes"),
            Path("lib/ant_deploy.jar"),
            Path("lib/args4j_deploy.jar"),
            Path("lib/google_common_deploy.jar"),
            Path("lib/hamcrest-core-1.1.jar"),
            Path("lib/junit.jar"),
            Path("lib/libtrunk_rhino_parser_jarjared.jar"),
            Path("lib/protobuf_deploy.jar"),
        )
        override val testCpTypeSolverPaths = listOf(
//            Path("build/classes"),
            Path("lib/ant_deploy.jar"),
            Path("lib/args4j_deploy.jar"),
            Path("lib/google_common_deploy.jar"),
            Path("lib/hamcrest-core-1.1.jar"),
            Path("lib/junit.jar"),
            Path("lib/libtrunk_rhino_parser_jarjared.jar"),
            Path("lib/protobuf_deploy.jar"),
//            Path("build/test"),
        )

        override val entrypoints = listOf(
            "com.google.javascript.jscomp.CommandLineRunnerTest::testProcessClosurePrimitives",
        )
    }

    object Closure104f_230525_Pass3 : Project() {

        override val projectRev = ProjectRev(ProjectID.CLOSURE, 104, BugVersion.FIXED)
        override val basePath: Path = super.basePath.parent.resolve("${projectRev.versionId}.230525.pass3")

        override val srcTypeSolverPaths = listOf(
            Path("src"),
            Path("test"),
        )
        override val srcCpTypeSolverPaths = listOf(
            Path("gen"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("lib/ant_deploy.jar"),
            Path("lib/google_common_deploy.jar"),
            Path("lib/hamcrest-core-1.1.jar"),
            Path("lib/junit4-core.jar"),
            Path("lib/junit4-legacy.jar"),
            Path("lib/libtrunk_rhino_parser_jarjared.jar"),
            Path("lib/protobuf_deploy.jar"),
        )

        override val entrypoints = listOf(
            "com.google.javascript.rhino.jstype.UnionTypeTest::testGreatestSubtypeUnionTypes5",
        )
    }

    object Closure160f : Project() {

        override val projectRev = ProjectRev(ProjectID.CLOSURE, 160, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src"),
            Path("test"),
        )
        override val srcCpTypeSolverPaths = listOf(
            Path("gen"),
        )
        override val compileCpTypeSolverPaths = listOf(
//            Path("build/classes"),
            Path("lib/args4j.jar"),
            Path("lib/guava.jar"),
            Path("lib/json.jar"),
            Path("lib/jsr305.jar"),
            Path("lib/libtrunk_rhino_parser_jarjared.jar"),
            Path("lib/protobuf-java.jar"),
            Path("lib/ant.jar"),
        )
        override val testCpTypeSolverPaths = listOf(
//            Path("build/classes"),
            Path("lib/args4j.jar"),
            Path("lib/guava.jar"),
            Path("lib/json.jar"),
            Path("lib/jsr305.jar"),
            Path("lib/libtrunk_rhino_parser_jarjared.jar"),
            Path("lib/protobuf-java.jar"),
            Path("lib/ant.jar"),
            Path("lib/ant-launcher.jar"),
            Path("lib/caja-r4314.jar"),
            Path("lib/junit.jar"),
//            Path("build/test"),
        )

        override val entrypoints = listOf(
            "com.google.javascript.jscomp.CommandLineRunnerTest::testCheckSymbolsOverrideForQuiet",
        )
    }

    object Closure168b_230530_Pass0 : Project() {

        override val projectRev = ProjectRev(ProjectID.CLOSURE, 168, BugVersion.BUGGY)
        override val basePath: Path = super.basePath.parent.resolve("${projectRev.versionId}.230530.pass0")

        override val srcTypeSolverPaths = listOf(
            Path("src"),
            Path("test"),
        )
        override val srcCpTypeSolverPaths = listOf(
            Path("gen"),
        )
        override val compileCpTypeSolverPaths = listOf(
//            Path("build/classes"),
            Path("lib/args4j.jar"),
            Path("lib/guava.jar"),
            Path("lib/json.jar"),
            Path("lib/jsr305.jar"),
            Path("lib/protobuf-java.jar"),
            Path("build/lib/rhino.jar"),
            Path("lib/ant.jar"),
        )
        override val testCpTypeSolverPaths = listOf(
//            Path("build/classes"),
            Path("lib/args4j.jar"),
            Path("lib/guava.jar"),
            Path("lib/json.jar"),
            Path("lib/jsr305.jar"),
            Path("lib/protobuf-java.jar"),
            Path("build/lib/rhino.jar"),
            Path("lib/ant.jar"),
            Path("lib/ant-launcher.jar"),
            Path("lib/caja-r4314.jar"),
            Path("lib/jarjar.jar"),
            Path("lib/junit.jar"),
//            Path("build/test"),
        )

        override val entrypoints = listOf(
            "com.google.javascript.jscomp.TypeCheckTest::testIssue726",
        )
    }

    object Collections25f : Project() {

        override val projectRev = ProjectRev(ProjectID.COLLECTIONS, 25, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/main/java"),
            Path("src/test/java")
        )

        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/lib/junit-4.11.jar"),
            Path("/d4j/framework/projects/Collections/lib/hamcrest-core-1.3.jar"),
            Path("/d4j/framework/projects/Collections/lib/easymock-2.0.jar")
        )

        override val entrypoints = listOf(
            "org.apache.commons.collections4.IteratorUtilsTest::testCollatedIterator",
        )
    }

    object Collections27f : Project() {

        override val projectRev = ProjectRev(ProjectID.COLLECTIONS, 27, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/main/java"),
            Path("src/test/java")
        )

        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/lib/junit-4.11.jar"),
            Path("/d4j/framework/projects/Collections/lib/hamcrest-core-1.3.jar"),
            Path("/d4j/framework/projects/Collections/lib/easymock-2.0.jar")
        )

        override val entrypoints = listOf(
            "org.apache.commons.collections4.map.MultiValueMapTest::testUnsafeDeSerialization",
        )
    }

    object Collections28f : Project() {

        override val projectRev = ProjectRev(ProjectID.COLLECTIONS, 28, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/main/java"),
            Path("src/test/java")
        )

        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/lib/junit-4.11.jar"),
            Path("/d4j/framework/projects/Collections/lib/hamcrest-core-1.3.jar"),
            Path("/d4j/framework/projects/Collections/lib/easymock-2.0.jar")
        )

        override val entrypoints = listOf(
            "org.apache.commons.collections4.trie.PatriciaTrieTest::testPrefixMapClear",
        )
    }

    object Compress3f : Project() {

        override val projectRev = ProjectRev(ProjectID.COMPRESS, 3, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/main/java"),
            Path("src/test/java"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/Compress/lib/junit/junit/3.8.2/junit-3.8.2.jar"),
        )

        override val entrypoints = listOf(
            "org.apache.commons.compress.archivers.ArchiveOutputStreamTest::testFinish",
        )
    }

    object Compress4f : Project() {

        override val projectRev = ProjectRev(ProjectID.COMPRESS, 4, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/main/java"),
            Path("src/test/java"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/Compress/lib/junit/junit/3.8.2/junit-3.8.2.jar"),
        )

        override val entrypoints = listOf(
            "org.apache.commons.compress.archivers.jar.JarArchiveOutputStreamTest::testJarMarker",
            "org.apache.commons.compress.archivers.zip.UTF8ZipFilesTest::testCP437FileRoundtripImplicitUnicodeExtra",
            "org.apache.commons.compress.archivers.zip.UTF8ZipFilesTest::testUtf8FileRoundtripImplicitUnicodeExtra",
            "org.apache.commons.compress.archivers.zip.UTF8ZipFilesTest::testCP437FileRoundtripExplicitUnicodeExtra",
            "org.apache.commons.compress.archivers.zip.UTF8ZipFilesTest::testUtf8FileRoundtripExplicitUnicodeExtra",
            "org.apache.commons.compress.archivers.zip.UTF8ZipFilesTest::testASCIIFileRoundtripImplicitUnicodeExtra",
            "org.apache.commons.compress.archivers.zip.UTF8ZipFilesTest::testUtf8FileRoundtripNoEFSImplicitUnicodeExtra",
            "org.apache.commons.compress.archivers.zip.UTF8ZipFilesTest::testZipArchiveInputStreamReadsUnicodeFields",
            "org.apache.commons.compress.archivers.zip.UTF8ZipFilesTest::testASCIIFileRoundtripExplicitUnicodeExtra",
            "org.apache.commons.compress.archivers.zip.UTF8ZipFilesTest::testUtf8FileRoundtripNoEFSExplicitUnicodeExtra",
        )
    }

    object Compress9f : Project() {

        override val projectRev = ProjectRev(ProjectID.COMPRESS, 9, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/main/java"),
            Path("src/test/java"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/Compress/lib/junit/junit/4.8.2/junit-4.8.2.jar"),
        )

        override val entrypoints = listOf(
            "org.apache.commons.compress.archivers.tar.TarArchiveOutputStreamTest::testCount",
        )
    }

    object Compress10f : Project() {

        override val projectRev = ProjectRev(ProjectID.COMPRESS, 10, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/main/java"),
            Path("src/test/java"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/Compress/lib/junit/junit/4.8.2/junit-4.8.2.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/hamcrest/hamcrest-core/1.1/hamcrest-core-1.1.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/tukaani/xz/1.0/xz-1.0.jar"),
        )

        override val entrypoints = listOf(
            "org.apache.commons.compress.archivers.zip.UTF8ZipFilesTest::testReadWinZipArchive",
        )
    }

    object Compress11f : Project() {

        override val projectRev = ProjectRev(ProjectID.COMPRESS, 11, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/main/java"),
            Path("src/test/java"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/Compress/lib/junit/junit/4.10/junit-4.10.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/hamcrest/hamcrest-core/1.1/hamcrest-core-1.1.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/tukaani/xz/1.0/xz-1.0.jar"),
        )

        override val entrypoints = listOf(
            "org.apache.commons.compress.archivers.ArchiveStreamFactoryTest::shortTextFilesAreNoTARs",
        )
    }

    object Compress12f : Project() {

        override val projectRev = ProjectRev(ProjectID.COMPRESS, 12, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/main/java"),
            Path("src/test/java"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/Compress/lib/junit/junit/4.10/junit-4.10.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/hamcrest/hamcrest-core/1.1/hamcrest-core-1.1.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/tukaani/xz/1.0/xz-1.0.jar"),
        )

        override val entrypoints = listOf(
            "org.apache.commons.compress.archivers.TarTestCase::testCOMPRESS178",
        )
    }

    object Compress21f : Project() {

        override val projectRev = ProjectRev(ProjectID.COMPRESS, 21, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/main/java"),
            Path("src/test/java"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/Compress/lib/junit/junit/4.11/junit-4.11.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/tukaani/xz/1.4/xz-1.4.jar"),
        )

        override val entrypoints = listOf(
            "org.apache.commons.compress.archivers.sevenz.SevenZOutputFileTest::testSevenEmptyFiles",
            "org.apache.commons.compress.archivers.sevenz.SevenZOutputFileTest::testEightFilesSomeNotEmpty",
            "org.apache.commons.compress.archivers.sevenz.SevenZOutputFileTest::testSixEmptyFiles",
            "org.apache.commons.compress.archivers.sevenz.SevenZOutputFileTest::testEightEmptyFiles",
            "org.apache.commons.compress.archivers.sevenz.SevenZOutputFileTest::testNineEmptyFiles",
            "org.apache.commons.compress.archivers.sevenz.SevenZOutputFileTest::testSixFilesSomeNotEmpty",
            "org.apache.commons.compress.archivers.sevenz.SevenZOutputFileTest::testNineFilesSomeNotEmpty",
            "org.apache.commons.compress.archivers.sevenz.SevenZOutputFileTest::testSevenFilesSomeNotEmpty",
        )
    }

    object Compress22f : Project() {

        override val projectRev = ProjectRev(ProjectID.COMPRESS, 22, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/main/java"),
            Path("src/test/java"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/Compress/lib/junit/junit/4.11/junit-4.11.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/tukaani/xz/1.4/xz-1.4.jar"),
        )

        override val entrypoints = listOf(
            "org.apache.commons.compress.compressors.bzip2.PythonTruncatedBzip2Test::testPartialReadTruncatedData",
        )
    }

    object Compress27f : Project() {

        override val projectRev = ProjectRev(ProjectID.COMPRESS, 27, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/main/java"),
            Path("src/test/java"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/Compress/lib/junit/junit/4.11/junit-4.11.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/tukaani/xz/1.5/xz-1.5.jar"),
        )

        override val entrypoints = listOf(
            "org.apache.commons.compress.archivers.tar.TarUtilsTest::testParseOctal",
        )
    }

    object Compress28f : Project() {

        override val projectRev = ProjectRev(ProjectID.COMPRESS, 28, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/main/java"),
            Path("src/test/java"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/Compress/lib/junit/junit/4.11/junit-4.11.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/tukaani/xz/1.5/xz-1.5.jar"),
        )

        override val entrypoints = listOf(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStreamTest::shouldThrowAnExceptionOnTruncatedEntries",
        )
    }

    object Compress28f_230411_Pass1 : Project() {

        override val projectRev = ProjectRev(ProjectID.COMPRESS, 28, BugVersion.FIXED)
        override val basePath: Path = super.basePath.parent.resolve("${projectRev.versionId}.230411.pass1")

        override val srcTypeSolverPaths = listOf(
            Path("src/main/java"),
            Path("src/test/java"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/Compress/lib/junit/junit/4.11/junit-4.11.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/tukaani/xz/1.5/xz-1.5.jar"),
        )

        override val entrypoints = listOf(
            "org.apache.commons.compress.archivers.tar.TarArchiveInputStreamTest::shouldThrowAnExceptionOnTruncatedEntries",
        )
    }

    object Compress29f : Project() {

        override val projectRev = ProjectRev(ProjectID.COMPRESS, 29, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/main/java"),
            Path("src/test/java"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/Compress/lib/junit/junit/4.12/junit-4.12.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/tukaani/xz/1.5/xz-1.5.jar"),
        )

        override val entrypoints = listOf(
            "org.apache.commons.compress.archivers.ArchiveStreamFactoryTest::testEncodingInputStream",
            "org.apache.commons.compress.archivers.ArchiveStreamFactoryTest::testEncodingInputStreamAutodetect",
            "org.apache.commons.compress.archivers.ArchiveStreamFactoryTest::testEncodingOutputStream",
        )
    }

    object Compress31f : Project() {

        override val projectRev = ProjectRev(ProjectID.COMPRESS, 31, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/main/java"),
            Path("src/test/java"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/Compress/lib/junit/junit/4.12/junit-4.12.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/tukaani/xz/1.5/xz-1.5.jar"),
        )

        override val entrypoints = listOf(
            "org.apache.commons.compress.archivers.TarTestCase::testCOMPRESS178",
            "org.apache.commons.compress.archivers.tar.TarUtilsTest::testParseOctalInvalid",
        )
    }

    object Compress33f : Project() {

        override val projectRev = ProjectRev(ProjectID.COMPRESS, 33, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/main/java"),
            Path("src/test/java"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/Compress/lib/junit/junit/4.12/junit-4.12.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/tukaani/xz/1.5/xz-1.5.jar"),
        )

        override val entrypoints = listOf(
            "org.apache.commons.compress.compressors.DetectCompressorTestCase::testDetection",
        )
    }

    object Compress36f : Project() {

        override val projectRev = ProjectRev(ProjectID.COMPRESS, 36, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/main/java"),
            Path("src/test/java"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/Compress/lib/junit/junit/4.12/junit-4.12.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/tukaani/xz/1.5/xz-1.5.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/powermock/powermock-module-junit4/1.6.4/powermock-module-junit4-1.6.4.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/powermock/powermock-module-junit4-common/1.6.4/powermock-module-junit4-common-1.6.4.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/powermock/powermock-core/1.6.4/powermock-core-1.6.4.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/javassist/javassist/3.20.0-GA/javassist-3.20.0-GA.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/powermock/powermock-reflect/1.6.4/powermock-reflect-1.6.4.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/powermock/powermock-api-mockito/1.6.4/powermock-api-mockito-1.6.4.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/mockito/mockito-core/1.10.19/mockito-core-1.10.19.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/objenesis/objenesis/2.1/objenesis-2.1.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/powermock/powermock-api-support/1.6.4/powermock-api-support-1.6.4.jar"),
        )

        override val entrypoints = listOf(
            "org.apache.commons.compress.archivers.sevenz.SevenZFileTest::readEntriesOfSize0",
        )
    }

    object Compress41f : Project() {

        override val projectRev = ProjectRev(ProjectID.COMPRESS, 41, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/main/java"),
            Path("src/test/java"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/Compress/lib/junit/junit/4.12/junit-4.12.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/tukaani/xz/1.6/xz-1.6.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/powermock/powermock-module-junit4/1.6.4/powermock-module-junit4-1.6.4.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/powermock/powermock-module-junit4-common/1.6.4/powermock-module-junit4-common-1.6.4.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/powermock/powermock-core/1.6.4/powermock-core-1.6.4.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/javassist/javassist/3.20.0-GA/javassist-3.20.0-GA.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/powermock/powermock-reflect/1.6.4/powermock-reflect-1.6.4.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/powermock/powermock-api-mockito/1.6.4/powermock-api-mockito-1.6.4.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/mockito/mockito-core/1.10.19/mockito-core-1.10.19.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/objenesis/objenesis/2.1/objenesis-2.1.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/powermock/powermock-api-support/1.6.4/powermock-api-support-1.6.4.jar"),
        )

        override val entrypoints = listOf(
            "org.apache.commons.compress.archivers.ZipTestCase::testListAllFilesWithNestedArchive",
            "org.apache.commons.compress.archivers.zip.ZipArchiveInputStreamTest::testThrowOnInvalidEntry",
        )
    }

    object Compress42f : Project() {

        override val projectRev = ProjectRev(ProjectID.COMPRESS, 42, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/main/java"),
            Path("src/test/java"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/Compress/lib/junit/junit/4.12/junit-4.12.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/tukaani/xz/1.6/xz-1.6.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/powermock/powermock-module-junit4/1.6.4/powermock-module-junit4-1.6.4.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/powermock/powermock-module-junit4-common/1.6.4/powermock-module-junit4-common-1.6.4.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/powermock/powermock-core/1.6.4/powermock-core-1.6.4.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/javassist/javassist/3.20.0-GA/javassist-3.20.0-GA.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/powermock/powermock-reflect/1.6.4/powermock-reflect-1.6.4.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/powermock/powermock-api-mockito/1.6.4/powermock-api-mockito-1.6.4.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/mockito/mockito-core/1.10.19/mockito-core-1.10.19.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/objenesis/objenesis/2.1/objenesis-2.1.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/powermock/powermock-api-support/1.6.4/powermock-api-support-1.6.4.jar"),
        )

        override val entrypoints = listOf(
            "org.apache.commons.compress.archivers.zip.ZipArchiveEntryTest::isUnixSymlinkIsFalseIfMoreThanOneFlagIsSet",
        )
    }

    object Compress47f : Project() {

        override val projectRev = ProjectRev(ProjectID.COMPRESS, 47, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/main/java"),
            Path("src/test/java")
        )

        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/Compress/lib/org/objenesis/objenesis/2.6/objenesis-2.6.jar"),
            Path("/d4j/framework/projects/Compress/lib/junit/junit/4.12/junit-4.12.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar"),
            Path("/d4j/framework/projects/Compress/lib/com/github/luben/zstd-jni/1.3.3-1/zstd-jni-1.3.3-1.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/brotli/dec/0.1.2/dec-0.1.2.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/tukaani/xz/1.8/xz-1.8.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/powermock/powermock-module-junit4/1.7.3/powermock-module-junit4-1.7.3.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/powermock/powermock-module-junit4-common/1.7.3/powermock-module-junit4-common-1.7.3.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/powermock/powermock-reflect/1.7.3/powermock-reflect-1.7.3.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/powermock/powermock-core/1.7.3/powermock-core-1.7.3.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/javassist/javassist/3.21.0-GA/javassist-3.21.0-GA.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/powermock/powermock-api-mockito/1.7.3/powermock-api-mockito-1.7.3.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/powermock/powermock-api-mockito-common/1.7.3/powermock-api-mockito-common-1.7.3.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/powermock/powermock-api-support/1.7.3/powermock-api-support-1.7.3.jar"),
            Path("/d4j/framework/projects/Compress/lib/org/mockito/mockito-core/1.10.19/mockito-core-1.10.19.jar")
        )

        override val entrypoints = listOf(
            "org.apache.commons.compress.archivers.zip.ZipArchiveInputStreamTest::properlyMarksEntriesAsUnreadableIfUncompressedSizeIsUnknown",
        )
    }

    object Csv10f : Project() {

        override val projectRev = ProjectRev(ProjectID.CSV, 10, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/main/java"),
            Path("src/test/java")
        )

        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/Csv/lib/junit/junit/4.11/junit-4.11.jar"),
            Path("/d4j/framework/projects/Csv/lib/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar"),
            Path("/d4j/framework/projects/Csv/lib/commons-io/commons-io/2.2/commons-io-2.2.jar"),
            Path("/d4j/framework/projects/Csv/lib/com/h2database/h2/1.3.168/h2-1.3.168.jar"),
            Path("/d4j/framework/projects/lib/junit-4.11.jar"),
            Path("/d4j/framework/projects/Csv/lib/com/csvreader/1.0/javacsv.jar"),
            Path("/d4j/framework/projects/Csv/lib/com/generationjava/io/1.0/gj-csv-1.0.jar"),
            Path("/d4j/framework/projects/Csv/lib/com/h2database/h2/1.3.168/h2-1.3.168.jar"),
            Path("/d4j/framework/projects/Csv/lib/com/h2database/h2/1.4.180/h2-1.4.180.jar"),
            Path("/d4j/framework/projects/Csv/lib/com/h2database/h2/1.4.181/h2-1.4.181.jar"),
            Path("/d4j/framework/projects/Csv/lib/com/h2database/h2/1.4.182/h2-1.4.182.jar"),
            Path("/d4j/framework/projects/Csv/lib/com/h2database/h2/1.4.196/h2-1.4.196.jar"),
            Path("/d4j/framework/projects/Csv/lib/com/h2database/h2/1.4.198/h2-1.4.198.jar"),
            Path("/d4j/framework/projects/Csv/lib/com/opencsv/4.0/opencsv-4.0.jar"),
            Path("/d4j/framework/projects/Csv/lib/commons-io/commons-io/2.2/commons-io-2.2.jar"),
            Path("/d4j/framework/projects/Csv/lib/commons-io/commons-io/2.4/commons-io-2.4.jar"),
            Path("/d4j/framework/projects/Csv/lib/commons-io/commons-io/2.5/commons-io-2.5.jar"),
            Path("/d4j/framework/projects/Csv/lib/commons-io/commons-io/2.6/commons-io-2.6.jar"),
            Path("/d4j/framework/projects/Csv/lib/junit/junit/3.8.1/junit-3.8.1.jar"),
            Path("/d4j/framework/projects/Csv/lib/junit/junit/4.10/junit-4.10.jar"),
            Path("/d4j/framework/projects/Csv/lib/junit/junit/4.11/junit-4.11.jar"),
            Path("/d4j/framework/projects/Csv/lib/junit/junit/4.12/junit-4.12.jar"),
            Path("/d4j/framework/projects/Csv/lib/org/apache/commons/commons-lang3/3.4/commons-lang3-3.4.jar"),
            Path("/d4j/framework/projects/Csv/lib/org/apache/commons/commons-lang3/3.6/commons-lang3-3.6.jar"),
            Path("/d4j/framework/projects/Csv/lib/org/apache/commons/commons-lang3/3.7/commons-lang3-3.7.jar"),
            Path("/d4j/framework/projects/Csv/lib/org/apache/commons/commons-lang3/3.8.1/commons-lang3-3.8.1.jar"),
            Path("/d4j/framework/projects/Csv/lib/org/hamcrest/hamcrest-core/1.1/hamcrest-core-1.1.jar"),
            Path("/d4j/framework/projects/Csv/lib/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar"),
            Path("/d4j/framework/projects/Csv/lib/org/mockito/mockito-all/1.10.19/mockito-all-1.10.19.jar"),
            Path("/d4j/framework/projects/Csv/lib/org/mockito/mockito-all/1.9.5/mockito-all-1.9.5.jar"),
            Path("/d4j/framework/projects/Csv/lib/org/openjdk/jmh/1.21/jmh-core-1.21.jar"),
            Path("/d4j/framework/projects/Csv/lib/org/skife/csv/1.0/csv-1.0.jar"),
            Path("/d4j/framework/projects/Csv/lib/org/supercsv/2.4.0/super-csv-2.4.0.jar"),
        )

        override val entrypoints = listOf(
            "org.apache.commons.csv.CSVPrinterTest::testHeader",
        )
    }

    object Gson1f : Project() {

        override val projectRev = ProjectRev(ProjectID.GSON, 1, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("gson/src/main/java"),
            Path("gson/src/test/java"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/Gson/lib/junit/junit/3.8.2/junit-3.8.2.jar"),
            Path("/d4j/framework/projects/lib/junit-4.11.jar"),
            Path("/d4j/framework/projects/Gson/lib/com/google/code/findbugs/jsr305/3.0.0/jsr305-3.0.0.jar"),
            Path("/d4j/framework/projects/Gson/lib/junit/junit/3.8.2/junit-3.8.2.jar"),
            Path("/d4j/framework/projects/Gson/lib/junit/junit/4.12/junit-4.12.jar"),
            Path("/d4j/framework/projects/Gson/lib/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar"),
        )

        override val entrypoints = listOf(
            "com.google.gson.functional.TypeVariableTest::testSingle",
        )
    }

    object Gson3f : Project() {

        override val projectRev = ProjectRev(ProjectID.GSON, 3, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("gson/src/main/java"),
            Path("gson/src/test/java")
        )
        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/Gson/lib/junit/junit/3.8.2/junit-3.8.2.jar"),
            Path("/d4j/framework/projects/lib/junit-4.11.jar"),
            Path("/d4j/framework/projects/Gson/lib/com/google/code/findbugs/jsr305/3.0.0/jsr305-3.0.0.jar"),
            Path("/d4j/framework/projects/Gson/lib/junit/junit/3.8.2/junit-3.8.2.jar"),
            Path("/d4j/framework/projects/Gson/lib/junit/junit/4.12/junit-4.12.jar"),
            Path("/d4j/framework/projects/Gson/lib/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar")
        )

        override val entrypoints = listOf(
            "com.google.gson.functional.MapTest::testConcurrentMap",
            "com.google.gson.functional.MapTest::testConcurrentNavigableMap",
        )
    }

    object JacksonDatabind1f : Project() {

        override val projectRev = ProjectRev(ProjectID.JACKSON_DATABIND, 1, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/main/java"),
            Path("src/test/java"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.2.1/jackson-annotations-2.2.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.2.1/jackson-core-2.2.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/junit/junit/4.10/junit-4.10.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/hamcrest/hamcrest-core/1.1/hamcrest-core-1.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/cglib/cglib/2.2.2/cglib-2.2.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/asm/asm/3.3.1/asm-3.3.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/codehaus/groovy/groovy/1.7.9/groovy-1.7.9.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/antlr/antlr/2.7.7/antlr-2.7.7.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/asm/asm-commons/3.2/asm-commons-3.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/asm/asm-util/3.2/asm-util-3.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/asm/asm-analysis/3.2/asm-analysis-3.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/asm/asm-tree/3.2/asm-tree-3.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/hibernate/hibernate-cglib-repack/2.1_3/hibernate-cglib-repack-2.1_3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/antlr/antlr/2.7.7/antlr-2.7.7.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/asm/asm-analysis/3.2/asm-analysis-3.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/asm/asm-commons/3.2/asm-commons-3.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/asm/asm-tree/3.2/asm-tree-3.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/asm/asm-util/3.2/asm-util-3.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/asm/asm/3.3.1/asm-3.3.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/cglib/cglib/2.2.2/cglib-2.2.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/cglib/cglib/3.1/cglib-3.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.10.0-SNAPSHOT/jackson-annotations-2.10.0-20190519.085045-3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.2.0/jackson-annotations-2.2.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.2.1/jackson-annotations-2.2.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.3.0/jackson-annotations-2.3.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.4.0-rc3/jackson-annotations-2.4.0-rc3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.4.0/jackson-annotations-2.4.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.5.0/jackson-annotations-2.5.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.6.0-rc1/jackson-annotations-2.6.0-rc1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.6.0-rc2/jackson-annotations-2.6.0-rc2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.6.0-rc3/jackson-annotations-2.6.0-rc3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.6.0/jackson-annotations-2.6.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.7.0-rc2/jackson-annotations-2.7.0-rc2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.7.0/jackson-annotations-2.7.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.8.0.rc1/jackson-annotations-2.8.0.rc1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.8.0/jackson-annotations-2.8.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.9.0.pr3/jackson-annotations-2.9.0.pr3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.9.0/jackson-annotations-2.9.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/3.0-SNAPSHOT/jackson-annotations-3.0-20190323.173155-41.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/3.0-SNAPSHOT/jackson-annotations-3.0-20190519.085339-43.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.10.0-SNAPSHOT/jackson-core-2.10.0-20190518.180547-60.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.2.0/jackson-core-2.2.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.2.1/jackson-core-2.2.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.3.0/jackson-core-2.3.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.4.0-rc3/jackson-core-2.4.0-rc3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.4.0/jackson-core-2.4.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.4.1.1/jackson-core-2.4.1.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.4.2/jackson-core-2.4.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.4.3/jackson-core-2.4.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.4.5/jackson-core-2.4.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.4.6/jackson-core-2.4.6.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.5.0/jackson-core-2.5.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.5.1/jackson-core-2.5.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.5.3/jackson-core-2.5.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.5.4/jackson-core-2.5.4.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.5.5/jackson-core-2.5.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.6.0-rc1/jackson-core-2.6.0-rc1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.6.0-rc2/jackson-core-2.6.0-rc2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.6.0-rc3/jackson-core-2.6.0-rc3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.6.0/jackson-core-2.6.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.6.1/jackson-core-2.6.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.6.2/jackson-core-2.6.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.6.3/jackson-core-2.6.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.6.4/jackson-core-2.6.4.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.6.5/jackson-core-2.6.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.6.6/jackson-core-2.6.6.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.6.7/jackson-core-2.6.7.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.7.0-rc2/jackson-core-2.7.0-rc2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.7.0/jackson-core-2.7.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.7.1/jackson-core-2.7.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.7.2/jackson-core-2.7.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.7.4/jackson-core-2.7.4.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.7.5/jackson-core-2.7.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.7.6/jackson-core-2.7.6.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.7.7/jackson-core-2.7.7.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.7.8/jackson-core-2.7.8.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.7.9/jackson-core-2.7.9.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.0.rc1/jackson-core-2.8.0.rc1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.0.rc2/jackson-core-2.8.0.rc2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.0/jackson-core-2.8.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.10/jackson-core-2.8.10.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.3/jackson-core-2.8.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.4/jackson-core-2.8.4.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.5/jackson-core-2.8.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.6/jackson-core-2.8.6.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.7/jackson-core-2.8.7.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.8-SNAPSHOT/jackson-core-2.8.8-20170301.000803-1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.8/jackson-core-2.8.8.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.9/jackson-core-2.8.9.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.9.0.pr3/jackson-core-2.9.0.pr3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.9.6/jackson-core-2.9.6.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.9.7/jackson-core-2.9.7.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.9.8/jackson-core-2.9.8.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.9.9/jackson-core-2.9.9.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/3.0.0-SNAPSHOT/jackson-core-3.0.0-20190516.032312-448.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/google/guava/guava/18.0/guava-18.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/google/jimfs/jimfs/1.1/jimfs-1.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/javax/measure/jsr-275/0.9.1/jsr-275-0.9.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/javax/measure/jsr-275/0.9.2/jsr-275-0.9.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/junit/junit/4.10/junit-4.10.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/junit/junit/4.11/junit-4.11.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/junit/junit/4.12/junit-4.12.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/junit/junit/4.8.2/junit-4.8.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/net/bytebuddy/byte-buddy-agent/1.7.5/byte-buddy-agent-1.7.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/net/bytebuddy/byte-buddy-agent/1.9.3/byte-buddy-agent-1.9.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/net/bytebuddy/byte-buddy/1.7.5/byte-buddy-1.7.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/net/bytebuddy/byte-buddy/1.9.3/byte-buddy-1.9.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/assertj/assertj-core/3.8.0/assertj-core-3.8.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/assertj/assertj-core/3.9.1/assertj-core-3.9.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/codehaus/groovy/groovy/1.7.9/groovy-1.7.9.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/codehaus/groovy/groovy/2.4.0/groovy-2.4.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/hamcrest/hamcrest-core/1.1/hamcrest-core-1.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/hibernate/hibernate-cglib-repack/2.1_3/hibernate-cglib-repack-2.1_3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/javassist/javassist/3.19.0-GA/javassist-3.19.0-GA.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/javassist/javassist/3.20.0-GA/javassist-3.20.0-GA.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/javassist/javassist/3.21.0-GA/javassist-3.21.0-GA.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/javassist/javassist/3.22.0-CR2/javassist-3.22.0-CR2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/javassist/javassist/3.24.0-GA/javassist-3.24.0-GA.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/mockito/mockito-all/1.10.19/mockito-all-1.10.19.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/mockito/mockito-core/1.10.19/mockito-core-1.10.19.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/mockito/mockito-core/2.10.0/mockito-core-2.10.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/mockito/mockito-core/2.23.0/mockito-core-2.23.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/objenesis/objenesis/2.1/objenesis-2.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/objenesis/objenesis/2.6/objenesis-2.6.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/objenesis/objenesis/3.0.1/objenesis-3.0.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/ow2/asm/asm/4.2/asm-4.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-mockito-common/1.6.5/powermock-api-mockito-common-1.6.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-mockito-common/1.7.3/powermock-api-mockito-common-1.7.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-mockito-common/1.7.4/powermock-api-mockito-common-1.7.4.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-mockito/1.6.2/powermock-api-mockito-1.6.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-mockito/1.6.3/powermock-api-mockito-1.6.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-mockito/1.6.5/powermock-api-mockito-1.6.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-mockito/1.7.3/powermock-api-mockito-1.7.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-mockito/1.7.4/powermock-api-mockito-1.7.4.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-mockito2/2.0.0-beta.5/powermock-api-mockito2-2.0.0-beta.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-mockito2/2.0.0/powermock-api-mockito2-2.0.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-support/1.6.2/powermock-api-support-1.6.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-support/1.6.3/powermock-api-support-1.6.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-support/1.6.5/powermock-api-support-1.6.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-support/1.7.3/powermock-api-support-1.7.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-support/1.7.4/powermock-api-support-1.7.4.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-support/2.0.0-beta.5/powermock-api-support-2.0.0-beta.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-support/2.0.0/powermock-api-support-2.0.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-core/1.6.2/powermock-core-1.6.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-core/1.6.3/powermock-core-1.6.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-core/1.6.5/powermock-core-1.6.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-core/1.7.3/powermock-core-1.7.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-core/1.7.4/powermock-core-1.7.4.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-core/2.0.0-beta.5/powermock-core-2.0.0-beta.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-core/2.0.0/powermock-core-2.0.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4-common/1.6.2/powermock-module-junit4-common-1.6.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4-common/1.6.3/powermock-module-junit4-common-1.6.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4-common/1.6.5/powermock-module-junit4-common-1.6.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4-common/1.7.3/powermock-module-junit4-common-1.7.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4-common/1.7.4/powermock-module-junit4-common-1.7.4.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4-common/2.0.0-beta.5/powermock-module-junit4-common-2.0.0-beta.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4-common/2.0.0/powermock-module-junit4-common-2.0.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4/1.6.2/powermock-module-junit4-1.6.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4/1.6.3/powermock-module-junit4-1.6.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4/1.6.5/powermock-module-junit4-1.6.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4/1.7.3/powermock-module-junit4-1.7.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4/1.7.4/powermock-module-junit4-1.7.4.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4/2.0.0-beta.5/powermock-module-junit4-2.0.0-beta.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4/2.0.0/powermock-module-junit4-2.0.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-reflect/1.6.2/powermock-reflect-1.6.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-reflect/1.6.3/powermock-reflect-1.6.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-reflect/1.6.5/powermock-reflect-1.6.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-reflect/1.7.3/powermock-reflect-1.7.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-reflect/1.7.4/powermock-reflect-1.7.4.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-reflect/2.0.0-beta.5/powermock-reflect-2.0.0-beta.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-reflect/2.0.0/powermock-reflect-2.0.0.jar"),
        )

        override val entrypoints = listOf(
            "com.fasterxml.jackson.databind.struct.TestPOJOAsArray::testNullColumn",
        )
    }

    object JacksonDatabind5f : Project() {

        override val projectRev = ProjectRev(ProjectID.JACKSON_DATABIND, 5, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/main/java"),
            Path("src/test/java"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.4.0/jackson-annotations-2.4.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.4.1.1/jackson-core-2.4.1.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/cglib/cglib/2.2.2/cglib-2.2.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/asm/asm/3.3.1/asm-3.3.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/codehaus/groovy/groovy/1.7.9/groovy-1.7.9.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/antlr/antlr/2.7.7/antlr-2.7.7.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/asm/asm-commons/3.2/asm-commons-3.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/asm/asm-util/3.2/asm-util-3.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/asm/asm-analysis/3.2/asm-analysis-3.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/asm/asm-tree/3.2/asm-tree-3.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/hibernate/hibernate-cglib-repack/2.1_3/hibernate-cglib-repack-2.1_3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/junit/junit/4.8.2/junit-4.8.2.jar"),
            Path("/d4j/framework/projects/lib/junit-4.11.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/cglib/cglib/3.1/cglib-3.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.10.0-SNAPSHOT/jackson-annotations-2.10.0-20190519.085045-3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.2.0/jackson-annotations-2.2.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.2.1/jackson-annotations-2.2.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.3.0/jackson-annotations-2.3.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.4.0-rc3/jackson-annotations-2.4.0-rc3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.5.0/jackson-annotations-2.5.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.6.0-rc1/jackson-annotations-2.6.0-rc1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.6.0-rc2/jackson-annotations-2.6.0-rc2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.6.0-rc3/jackson-annotations-2.6.0-rc3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.6.0/jackson-annotations-2.6.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.7.0-rc2/jackson-annotations-2.7.0-rc2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.7.0/jackson-annotations-2.7.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.8.0.rc1/jackson-annotations-2.8.0.rc1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.8.0/jackson-annotations-2.8.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.9.0.pr3/jackson-annotations-2.9.0.pr3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.9.0/jackson-annotations-2.9.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/3.0-SNAPSHOT/jackson-annotations-3.0-20190323.173155-41.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/3.0-SNAPSHOT/jackson-annotations-3.0-20190519.085339-43.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.10.0-SNAPSHOT/jackson-core-2.10.0-20190518.180547-60.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.2.0/jackson-core-2.2.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.2.1/jackson-core-2.2.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.3.0/jackson-core-2.3.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.4.0-rc3/jackson-core-2.4.0-rc3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.4.0/jackson-core-2.4.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.4.2/jackson-core-2.4.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.4.3/jackson-core-2.4.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.4.5/jackson-core-2.4.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.4.6/jackson-core-2.4.6.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.5.0/jackson-core-2.5.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.5.1/jackson-core-2.5.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.5.3/jackson-core-2.5.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.5.4/jackson-core-2.5.4.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.5.5/jackson-core-2.5.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.6.0-rc1/jackson-core-2.6.0-rc1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.6.0-rc2/jackson-core-2.6.0-rc2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.6.0-rc3/jackson-core-2.6.0-rc3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.6.0/jackson-core-2.6.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.6.1/jackson-core-2.6.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.6.2/jackson-core-2.6.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.6.3/jackson-core-2.6.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.6.4/jackson-core-2.6.4.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.6.5/jackson-core-2.6.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.6.6/jackson-core-2.6.6.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.6.7/jackson-core-2.6.7.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.7.0-rc2/jackson-core-2.7.0-rc2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.7.0/jackson-core-2.7.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.7.1/jackson-core-2.7.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.7.2/jackson-core-2.7.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.7.4/jackson-core-2.7.4.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.7.5/jackson-core-2.7.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.7.6/jackson-core-2.7.6.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.7.7/jackson-core-2.7.7.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.7.8/jackson-core-2.7.8.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.7.9/jackson-core-2.7.9.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.0.rc1/jackson-core-2.8.0.rc1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.0.rc2/jackson-core-2.8.0.rc2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.0/jackson-core-2.8.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.10/jackson-core-2.8.10.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.3/jackson-core-2.8.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.4/jackson-core-2.8.4.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.5/jackson-core-2.8.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.6/jackson-core-2.8.6.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.7/jackson-core-2.8.7.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.8-SNAPSHOT/jackson-core-2.8.8-20170301.000803-1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.8/jackson-core-2.8.8.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.9/jackson-core-2.8.9.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.9.0.pr3/jackson-core-2.9.0.pr3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.9.6/jackson-core-2.9.6.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.9.7/jackson-core-2.9.7.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.9.8/jackson-core-2.9.8.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.9.9/jackson-core-2.9.9.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/3.0.0-SNAPSHOT/jackson-core-3.0.0-20190516.032312-448.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/google/guava/guava/18.0/guava-18.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/google/jimfs/jimfs/1.1/jimfs-1.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/javax/measure/jsr-275/0.9.1/jsr-275-0.9.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/javax/measure/jsr-275/0.9.2/jsr-275-0.9.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/junit/junit/4.10/junit-4.10.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/junit/junit/4.11/junit-4.11.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/junit/junit/4.12/junit-4.12.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/net/bytebuddy/byte-buddy-agent/1.7.5/byte-buddy-agent-1.7.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/net/bytebuddy/byte-buddy-agent/1.9.3/byte-buddy-agent-1.9.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/net/bytebuddy/byte-buddy/1.7.5/byte-buddy-1.7.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/net/bytebuddy/byte-buddy/1.9.3/byte-buddy-1.9.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/assertj/assertj-core/3.8.0/assertj-core-3.8.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/assertj/assertj-core/3.9.1/assertj-core-3.9.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/codehaus/groovy/groovy/2.4.0/groovy-2.4.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/hamcrest/hamcrest-core/1.1/hamcrest-core-1.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/javassist/javassist/3.19.0-GA/javassist-3.19.0-GA.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/javassist/javassist/3.20.0-GA/javassist-3.20.0-GA.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/javassist/javassist/3.21.0-GA/javassist-3.21.0-GA.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/javassist/javassist/3.22.0-CR2/javassist-3.22.0-CR2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/javassist/javassist/3.24.0-GA/javassist-3.24.0-GA.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/mockito/mockito-all/1.10.19/mockito-all-1.10.19.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/mockito/mockito-core/1.10.19/mockito-core-1.10.19.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/mockito/mockito-core/2.10.0/mockito-core-2.10.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/mockito/mockito-core/2.23.0/mockito-core-2.23.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/objenesis/objenesis/2.1/objenesis-2.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/objenesis/objenesis/2.6/objenesis-2.6.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/objenesis/objenesis/3.0.1/objenesis-3.0.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/ow2/asm/asm/4.2/asm-4.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-mockito-common/1.6.5/powermock-api-mockito-common-1.6.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-mockito-common/1.7.3/powermock-api-mockito-common-1.7.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-mockito-common/1.7.4/powermock-api-mockito-common-1.7.4.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-mockito/1.6.2/powermock-api-mockito-1.6.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-mockito/1.6.3/powermock-api-mockito-1.6.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-mockito/1.6.5/powermock-api-mockito-1.6.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-mockito/1.7.3/powermock-api-mockito-1.7.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-mockito/1.7.4/powermock-api-mockito-1.7.4.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-mockito2/2.0.0-beta.5/powermock-api-mockito2-2.0.0-beta.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-mockito2/2.0.0/powermock-api-mockito2-2.0.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-support/1.6.2/powermock-api-support-1.6.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-support/1.6.3/powermock-api-support-1.6.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-support/1.6.5/powermock-api-support-1.6.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-support/1.7.3/powermock-api-support-1.7.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-support/1.7.4/powermock-api-support-1.7.4.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-support/2.0.0-beta.5/powermock-api-support-2.0.0-beta.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-support/2.0.0/powermock-api-support-2.0.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-core/1.6.2/powermock-core-1.6.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-core/1.6.3/powermock-core-1.6.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-core/1.6.5/powermock-core-1.6.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-core/1.7.3/powermock-core-1.7.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-core/1.7.4/powermock-core-1.7.4.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-core/2.0.0-beta.5/powermock-core-2.0.0-beta.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-core/2.0.0/powermock-core-2.0.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4-common/1.6.2/powermock-module-junit4-common-1.6.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4-common/1.6.3/powermock-module-junit4-common-1.6.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4-common/1.6.5/powermock-module-junit4-common-1.6.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4-common/1.7.3/powermock-module-junit4-common-1.7.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4-common/1.7.4/powermock-module-junit4-common-1.7.4.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4-common/2.0.0-beta.5/powermock-module-junit4-common-2.0.0-beta.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4-common/2.0.0/powermock-module-junit4-common-2.0.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4/1.6.2/powermock-module-junit4-1.6.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4/1.6.3/powermock-module-junit4-1.6.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4/1.6.5/powermock-module-junit4-1.6.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4/1.7.3/powermock-module-junit4-1.7.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4/1.7.4/powermock-module-junit4-1.7.4.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4/2.0.0-beta.5/powermock-module-junit4-2.0.0-beta.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4/2.0.0/powermock-module-junit4-2.0.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-reflect/1.6.2/powermock-reflect-1.6.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-reflect/1.6.3/powermock-reflect-1.6.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-reflect/1.6.5/powermock-reflect-1.6.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-reflect/1.7.3/powermock-reflect-1.7.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-reflect/1.7.4/powermock-reflect-1.7.4.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-reflect/2.0.0-beta.5/powermock-reflect-2.0.0-beta.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-reflect/2.0.0/powermock-reflect-2.0.0.jar"),
        )

        override val entrypoints = listOf(
            "com.fasterxml.jackson.databind.introspect.TestMixinMerging::testDisappearingMixins515",
        )
    }

    object JacksonDatabind8f : Project() {

        override val projectRev = ProjectRev(ProjectID.JACKSON_DATABIND, 8, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/main/java"),
            Path("src/test/java"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.5.0/jackson-annotations-2.5.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.5.0/jackson-core-2.5.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/cglib/cglib/2.2.2/cglib-2.2.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/asm/asm/3.3.1/asm-3.3.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/codehaus/groovy/groovy/1.7.9/groovy-1.7.9.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/antlr/antlr/2.7.7/antlr-2.7.7.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/asm/asm-commons/3.2/asm-commons-3.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/asm/asm-util/3.2/asm-util-3.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/asm/asm-analysis/3.2/asm-analysis-3.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/asm/asm-tree/3.2/asm-tree-3.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/javax/measure/jsr-275/0.9.2/jsr-275-0.9.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/hibernate/hibernate-cglib-repack/2.1_3/hibernate-cglib-repack-2.1_3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/junit/junit/4.11/junit-4.11.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar"),
            Path("/d4j/framework/projects/lib/junit-4.11.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/antlr/antlr/2.7.7/antlr-2.7.7.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/asm/asm-analysis/3.2/asm-analysis-3.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/asm/asm-commons/3.2/asm-commons-3.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/asm/asm-tree/3.2/asm-tree-3.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/asm/asm-util/3.2/asm-util-3.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/asm/asm/3.3.1/asm-3.3.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/cglib/cglib/2.2.2/cglib-2.2.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/cglib/cglib/3.1/cglib-3.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.10.0-SNAPSHOT/jackson-annotations-2.10.0-20190519.085045-3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.2.0/jackson-annotations-2.2.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.2.1/jackson-annotations-2.2.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.3.0/jackson-annotations-2.3.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.4.0-rc3/jackson-annotations-2.4.0-rc3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.4.0/jackson-annotations-2.4.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.5.0/jackson-annotations-2.5.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.6.0-rc1/jackson-annotations-2.6.0-rc1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.6.0-rc2/jackson-annotations-2.6.0-rc2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.6.0-rc3/jackson-annotations-2.6.0-rc3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.6.0/jackson-annotations-2.6.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.7.0-rc2/jackson-annotations-2.7.0-rc2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.7.0/jackson-annotations-2.7.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.8.0.rc1/jackson-annotations-2.8.0.rc1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.8.0/jackson-annotations-2.8.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.9.0.pr3/jackson-annotations-2.9.0.pr3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.9.0/jackson-annotations-2.9.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/3.0-SNAPSHOT/jackson-annotations-3.0-20190323.173155-41.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/3.0-SNAPSHOT/jackson-annotations-3.0-20190519.085339-43.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.10.0-SNAPSHOT/jackson-core-2.10.0-20190518.180547-60.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.2.0/jackson-core-2.2.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.2.1/jackson-core-2.2.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.3.0/jackson-core-2.3.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.4.0-rc3/jackson-core-2.4.0-rc3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.4.0/jackson-core-2.4.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.4.1.1/jackson-core-2.4.1.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.4.2/jackson-core-2.4.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.4.3/jackson-core-2.4.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.4.5/jackson-core-2.4.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.4.6/jackson-core-2.4.6.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.5.0/jackson-core-2.5.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.5.1/jackson-core-2.5.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.5.3/jackson-core-2.5.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.5.4/jackson-core-2.5.4.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.5.5/jackson-core-2.5.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.6.0-rc1/jackson-core-2.6.0-rc1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.6.0-rc2/jackson-core-2.6.0-rc2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.6.0-rc3/jackson-core-2.6.0-rc3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.6.0/jackson-core-2.6.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.6.1/jackson-core-2.6.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.6.2/jackson-core-2.6.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.6.3/jackson-core-2.6.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.6.4/jackson-core-2.6.4.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.6.5/jackson-core-2.6.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.6.6/jackson-core-2.6.6.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.6.7/jackson-core-2.6.7.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.7.0-rc2/jackson-core-2.7.0-rc2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.7.0/jackson-core-2.7.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.7.1/jackson-core-2.7.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.7.2/jackson-core-2.7.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.7.4/jackson-core-2.7.4.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.7.5/jackson-core-2.7.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.7.6/jackson-core-2.7.6.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.7.7/jackson-core-2.7.7.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.7.8/jackson-core-2.7.8.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.7.9/jackson-core-2.7.9.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.0.rc1/jackson-core-2.8.0.rc1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.0.rc2/jackson-core-2.8.0.rc2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.0/jackson-core-2.8.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.10/jackson-core-2.8.10.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.3/jackson-core-2.8.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.4/jackson-core-2.8.4.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.5/jackson-core-2.8.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.6/jackson-core-2.8.6.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.7/jackson-core-2.8.7.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.8-SNAPSHOT/jackson-core-2.8.8-20170301.000803-1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.8/jackson-core-2.8.8.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.9/jackson-core-2.8.9.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.9.0.pr3/jackson-core-2.9.0.pr3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.9.6/jackson-core-2.9.6.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.9.7/jackson-core-2.9.7.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.9.8/jackson-core-2.9.8.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.9.9/jackson-core-2.9.9.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/3.0.0-SNAPSHOT/jackson-core-3.0.0-20190516.032312-448.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/google/guava/guava/18.0/guava-18.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/google/jimfs/jimfs/1.1/jimfs-1.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/javax/measure/jsr-275/0.9.1/jsr-275-0.9.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/javax/measure/jsr-275/0.9.2/jsr-275-0.9.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/junit/junit/4.10/junit-4.10.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/junit/junit/4.11/junit-4.11.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/junit/junit/4.12/junit-4.12.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/junit/junit/4.8.2/junit-4.8.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/net/bytebuddy/byte-buddy-agent/1.7.5/byte-buddy-agent-1.7.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/net/bytebuddy/byte-buddy-agent/1.9.3/byte-buddy-agent-1.9.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/net/bytebuddy/byte-buddy/1.7.5/byte-buddy-1.7.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/net/bytebuddy/byte-buddy/1.9.3/byte-buddy-1.9.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/assertj/assertj-core/3.8.0/assertj-core-3.8.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/assertj/assertj-core/3.9.1/assertj-core-3.9.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/codehaus/groovy/groovy/1.7.9/groovy-1.7.9.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/codehaus/groovy/groovy/2.4.0/groovy-2.4.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/hamcrest/hamcrest-core/1.1/hamcrest-core-1.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/hibernate/hibernate-cglib-repack/2.1_3/hibernate-cglib-repack-2.1_3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/javassist/javassist/3.19.0-GA/javassist-3.19.0-GA.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/javassist/javassist/3.20.0-GA/javassist-3.20.0-GA.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/javassist/javassist/3.21.0-GA/javassist-3.21.0-GA.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/javassist/javassist/3.22.0-CR2/javassist-3.22.0-CR2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/javassist/javassist/3.24.0-GA/javassist-3.24.0-GA.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/mockito/mockito-all/1.10.19/mockito-all-1.10.19.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/mockito/mockito-core/1.10.19/mockito-core-1.10.19.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/mockito/mockito-core/2.10.0/mockito-core-2.10.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/mockito/mockito-core/2.23.0/mockito-core-2.23.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/objenesis/objenesis/2.1/objenesis-2.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/objenesis/objenesis/2.6/objenesis-2.6.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/objenesis/objenesis/3.0.1/objenesis-3.0.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/ow2/asm/asm/4.2/asm-4.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-mockito-common/1.6.5/powermock-api-mockito-common-1.6.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-mockito-common/1.7.3/powermock-api-mockito-common-1.7.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-mockito-common/1.7.4/powermock-api-mockito-common-1.7.4.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-mockito/1.6.2/powermock-api-mockito-1.6.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-mockito/1.6.3/powermock-api-mockito-1.6.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-mockito/1.6.5/powermock-api-mockito-1.6.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-mockito/1.7.3/powermock-api-mockito-1.7.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-mockito/1.7.4/powermock-api-mockito-1.7.4.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-mockito2/2.0.0-beta.5/powermock-api-mockito2-2.0.0-beta.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-mockito2/2.0.0/powermock-api-mockito2-2.0.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-support/1.6.2/powermock-api-support-1.6.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-support/1.6.3/powermock-api-support-1.6.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-support/1.6.5/powermock-api-support-1.6.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-support/1.7.3/powermock-api-support-1.7.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-support/1.7.4/powermock-api-support-1.7.4.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-support/2.0.0-beta.5/powermock-api-support-2.0.0-beta.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-support/2.0.0/powermock-api-support-2.0.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-core/1.6.2/powermock-core-1.6.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-core/1.6.3/powermock-core-1.6.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-core/1.6.5/powermock-core-1.6.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-core/1.7.3/powermock-core-1.7.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-core/1.7.4/powermock-core-1.7.4.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-core/2.0.0-beta.5/powermock-core-2.0.0-beta.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-core/2.0.0/powermock-core-2.0.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4-common/1.6.2/powermock-module-junit4-common-1.6.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4-common/1.6.3/powermock-module-junit4-common-1.6.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4-common/1.6.5/powermock-module-junit4-common-1.6.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4-common/1.7.3/powermock-module-junit4-common-1.7.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4-common/1.7.4/powermock-module-junit4-common-1.7.4.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4-common/2.0.0-beta.5/powermock-module-junit4-common-2.0.0-beta.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4-common/2.0.0/powermock-module-junit4-common-2.0.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4/1.6.2/powermock-module-junit4-1.6.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4/1.6.3/powermock-module-junit4-1.6.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4/1.6.5/powermock-module-junit4-1.6.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4/1.7.3/powermock-module-junit4-1.7.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4/1.7.4/powermock-module-junit4-1.7.4.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4/2.0.0-beta.5/powermock-module-junit4-2.0.0-beta.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4/2.0.0/powermock-module-junit4-2.0.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-reflect/1.6.2/powermock-reflect-1.6.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-reflect/1.6.3/powermock-reflect-1.6.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-reflect/1.6.5/powermock-reflect-1.6.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-reflect/1.7.3/powermock-reflect-1.7.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-reflect/1.7.4/powermock-reflect-1.7.4.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-reflect/2.0.0-beta.5/powermock-reflect-2.0.0-beta.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-reflect/2.0.0/powermock-reflect-2.0.0.jar")
        )

        override val entrypoints = listOf(
            "com.fasterxml.jackson.databind.deser.TestJdkTypes::testStringBuilder",
        )
    }

    object JacksonDatabind20f : Project() {

        override val projectRev = ProjectRev(ProjectID.JACKSON_DATABIND, 20, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/main/java"),
            Path("src/test/java"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.5.0/jackson-annotations-2.5.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.5.3/jackson-core-2.5.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/cglib/cglib/3.1/cglib-3.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/ow2/asm/asm/4.2/asm-4.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/codehaus/groovy/groovy/2.4.0/groovy-2.4.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/javax/measure/jsr-275/0.9.2/jsr-275-0.9.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/hibernate/hibernate-cglib-repack/2.1_3/hibernate-cglib-repack-2.1_3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/junit/junit/4.11/junit-4.11.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar"),
            Path("/d4j/framework/projects/lib/junit-4.11.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/antlr/antlr/2.7.7/antlr-2.7.7.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/asm/asm-analysis/3.2/asm-analysis-3.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/asm/asm-commons/3.2/asm-commons-3.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/asm/asm-tree/3.2/asm-tree-3.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/asm/asm-util/3.2/asm-util-3.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/asm/asm/3.3.1/asm-3.3.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/cglib/cglib/2.2.2/cglib-2.2.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.10.0-SNAPSHOT/jackson-annotations-2.10.0-20190519.085045-3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.2.0/jackson-annotations-2.2.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.2.1/jackson-annotations-2.2.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.3.0/jackson-annotations-2.3.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.4.0-rc3/jackson-annotations-2.4.0-rc3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.4.0/jackson-annotations-2.4.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.6.0-rc1/jackson-annotations-2.6.0-rc1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.6.0-rc2/jackson-annotations-2.6.0-rc2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.6.0-rc3/jackson-annotations-2.6.0-rc3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.6.0/jackson-annotations-2.6.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.7.0-rc2/jackson-annotations-2.7.0-rc2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.7.0/jackson-annotations-2.7.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.8.0.rc1/jackson-annotations-2.8.0.rc1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.8.0/jackson-annotations-2.8.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.9.0.pr3/jackson-annotations-2.9.0.pr3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.9.0/jackson-annotations-2.9.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/3.0-SNAPSHOT/jackson-annotations-3.0-20190323.173155-41.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/3.0-SNAPSHOT/jackson-annotations-3.0-20190519.085339-43.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.10.0-SNAPSHOT/jackson-core-2.10.0-20190518.180547-60.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.2.0/jackson-core-2.2.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.2.1/jackson-core-2.2.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.3.0/jackson-core-2.3.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.4.0-rc3/jackson-core-2.4.0-rc3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.4.0/jackson-core-2.4.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.4.1.1/jackson-core-2.4.1.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.4.2/jackson-core-2.4.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.4.3/jackson-core-2.4.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.4.5/jackson-core-2.4.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.4.6/jackson-core-2.4.6.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.5.0/jackson-core-2.5.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.5.1/jackson-core-2.5.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.5.4/jackson-core-2.5.4.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.5.5/jackson-core-2.5.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.6.0-rc1/jackson-core-2.6.0-rc1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.6.0-rc2/jackson-core-2.6.0-rc2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.6.0-rc3/jackson-core-2.6.0-rc3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.6.0/jackson-core-2.6.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.6.1/jackson-core-2.6.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.6.2/jackson-core-2.6.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.6.3/jackson-core-2.6.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.6.4/jackson-core-2.6.4.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.6.5/jackson-core-2.6.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.6.6/jackson-core-2.6.6.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.6.7/jackson-core-2.6.7.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.7.0-rc2/jackson-core-2.7.0-rc2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.7.0/jackson-core-2.7.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.7.1/jackson-core-2.7.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.7.2/jackson-core-2.7.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.7.4/jackson-core-2.7.4.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.7.5/jackson-core-2.7.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.7.6/jackson-core-2.7.6.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.7.7/jackson-core-2.7.7.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.7.8/jackson-core-2.7.8.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.7.9/jackson-core-2.7.9.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.0.rc1/jackson-core-2.8.0.rc1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.0.rc2/jackson-core-2.8.0.rc2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.0/jackson-core-2.8.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.10/jackson-core-2.8.10.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.3/jackson-core-2.8.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.4/jackson-core-2.8.4.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.5/jackson-core-2.8.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.6/jackson-core-2.8.6.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.7/jackson-core-2.8.7.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.8-SNAPSHOT/jackson-core-2.8.8-20170301.000803-1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.8/jackson-core-2.8.8.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.9/jackson-core-2.8.9.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.9.0.pr3/jackson-core-2.9.0.pr3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.9.6/jackson-core-2.9.6.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.9.7/jackson-core-2.9.7.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.9.8/jackson-core-2.9.8.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.9.9/jackson-core-2.9.9.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/3.0.0-SNAPSHOT/jackson-core-3.0.0-20190516.032312-448.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/google/guava/guava/18.0/guava-18.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/google/jimfs/jimfs/1.1/jimfs-1.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/javax/measure/jsr-275/0.9.1/jsr-275-0.9.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/junit/junit/4.10/junit-4.10.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/junit/junit/4.12/junit-4.12.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/junit/junit/4.8.2/junit-4.8.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/net/bytebuddy/byte-buddy-agent/1.7.5/byte-buddy-agent-1.7.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/net/bytebuddy/byte-buddy-agent/1.9.3/byte-buddy-agent-1.9.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/net/bytebuddy/byte-buddy/1.7.5/byte-buddy-1.7.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/net/bytebuddy/byte-buddy/1.9.3/byte-buddy-1.9.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/assertj/assertj-core/3.8.0/assertj-core-3.8.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/assertj/assertj-core/3.9.1/assertj-core-3.9.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/codehaus/groovy/groovy/1.7.9/groovy-1.7.9.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/hamcrest/hamcrest-core/1.1/hamcrest-core-1.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/javassist/javassist/3.19.0-GA/javassist-3.19.0-GA.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/javassist/javassist/3.20.0-GA/javassist-3.20.0-GA.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/javassist/javassist/3.21.0-GA/javassist-3.21.0-GA.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/javassist/javassist/3.22.0-CR2/javassist-3.22.0-CR2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/javassist/javassist/3.24.0-GA/javassist-3.24.0-GA.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/mockito/mockito-all/1.10.19/mockito-all-1.10.19.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/mockito/mockito-core/1.10.19/mockito-core-1.10.19.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/mockito/mockito-core/2.10.0/mockito-core-2.10.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/mockito/mockito-core/2.23.0/mockito-core-2.23.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/objenesis/objenesis/2.1/objenesis-2.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/objenesis/objenesis/2.6/objenesis-2.6.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/objenesis/objenesis/3.0.1/objenesis-3.0.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-mockito-common/1.6.5/powermock-api-mockito-common-1.6.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-mockito-common/1.7.3/powermock-api-mockito-common-1.7.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-mockito-common/1.7.4/powermock-api-mockito-common-1.7.4.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-mockito/1.6.2/powermock-api-mockito-1.6.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-mockito/1.6.3/powermock-api-mockito-1.6.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-mockito/1.6.5/powermock-api-mockito-1.6.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-mockito/1.7.3/powermock-api-mockito-1.7.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-mockito/1.7.4/powermock-api-mockito-1.7.4.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-mockito2/2.0.0-beta.5/powermock-api-mockito2-2.0.0-beta.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-mockito2/2.0.0/powermock-api-mockito2-2.0.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-support/1.6.2/powermock-api-support-1.6.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-support/1.6.3/powermock-api-support-1.6.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-support/1.6.5/powermock-api-support-1.6.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-support/1.7.3/powermock-api-support-1.7.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-support/1.7.4/powermock-api-support-1.7.4.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-support/2.0.0-beta.5/powermock-api-support-2.0.0-beta.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-support/2.0.0/powermock-api-support-2.0.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-core/1.6.2/powermock-core-1.6.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-core/1.6.3/powermock-core-1.6.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-core/1.6.5/powermock-core-1.6.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-core/1.7.3/powermock-core-1.7.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-core/1.7.4/powermock-core-1.7.4.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-core/2.0.0-beta.5/powermock-core-2.0.0-beta.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-core/2.0.0/powermock-core-2.0.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4-common/1.6.2/powermock-module-junit4-common-1.6.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4-common/1.6.3/powermock-module-junit4-common-1.6.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4-common/1.6.5/powermock-module-junit4-common-1.6.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4-common/1.7.3/powermock-module-junit4-common-1.7.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4-common/1.7.4/powermock-module-junit4-common-1.7.4.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4-common/2.0.0-beta.5/powermock-module-junit4-common-2.0.0-beta.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4-common/2.0.0/powermock-module-junit4-common-2.0.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4/1.6.2/powermock-module-junit4-1.6.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4/1.6.3/powermock-module-junit4-1.6.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4/1.6.5/powermock-module-junit4-1.6.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4/1.7.3/powermock-module-junit4-1.7.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4/1.7.4/powermock-module-junit4-1.7.4.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4/2.0.0-beta.5/powermock-module-junit4-2.0.0-beta.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4/2.0.0/powermock-module-junit4-2.0.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-reflect/1.6.2/powermock-reflect-1.6.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-reflect/1.6.3/powermock-reflect-1.6.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-reflect/1.6.5/powermock-reflect-1.6.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-reflect/1.7.3/powermock-reflect-1.7.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-reflect/1.7.4/powermock-reflect-1.7.4.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-reflect/2.0.0-beta.5/powermock-reflect-2.0.0-beta.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-reflect/2.0.0/powermock-reflect-2.0.0.jar"),
        )

        override val entrypoints = listOf(
            "com.fasterxml.jackson.databind.introspect.TestNamingStrategyStd::testNamingWithObjectNode",
        )
    }

    object JacksonDatabind80f : Project() {

        override val projectRev = ProjectRev(ProjectID.JACKSON_DATABIND, 80, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/main/java"),
            Path("src/test/java"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.9.0.pr3/jackson-annotations-2.9.0.pr3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.9.0.pr3/jackson-core-2.9.0.pr3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/junit/junit/4.12/junit-4.12.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4/1.6.5/powermock-module-junit4-1.6.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4-common/1.6.5/powermock-module-junit4-common-1.6.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-core/1.6.5/powermock-core-1.6.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/javassist/javassist/3.20.0-GA/javassist-3.20.0-GA.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-reflect/1.6.5/powermock-reflect-1.6.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-mockito/1.6.5/powermock-api-mockito-1.6.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/mockito/mockito-core/1.10.19/mockito-core-1.10.19.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/objenesis/objenesis/2.1/objenesis-2.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-mockito-common/1.6.5/powermock-api-mockito-common-1.6.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-support/1.6.5/powermock-api-support-1.6.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/javax/measure/jsr-275/0.9.1/jsr-275-0.9.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/google/jimfs/jimfs/1.1/jimfs-1.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/google/guava/guava/18.0/guava-18.0.jar"),
            Path("/d4j/framework/projects/lib/junit-4.11.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/antlr/antlr/2.7.7/antlr-2.7.7.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/asm/asm-analysis/3.2/asm-analysis-3.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/asm/asm-commons/3.2/asm-commons-3.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/asm/asm-tree/3.2/asm-tree-3.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/asm/asm-util/3.2/asm-util-3.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/asm/asm/3.3.1/asm-3.3.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/cglib/cglib/2.2.2/cglib-2.2.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/cglib/cglib/3.1/cglib-3.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.10.0-SNAPSHOT/jackson-annotations-2.10.0-20190519.085045-3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.2.0/jackson-annotations-2.2.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.2.1/jackson-annotations-2.2.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.3.0/jackson-annotations-2.3.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.4.0-rc3/jackson-annotations-2.4.0-rc3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.4.0/jackson-annotations-2.4.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.5.0/jackson-annotations-2.5.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.6.0-rc1/jackson-annotations-2.6.0-rc1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.6.0-rc2/jackson-annotations-2.6.0-rc2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.6.0-rc3/jackson-annotations-2.6.0-rc3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.6.0/jackson-annotations-2.6.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.7.0-rc2/jackson-annotations-2.7.0-rc2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.7.0/jackson-annotations-2.7.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.8.0.rc1/jackson-annotations-2.8.0.rc1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.8.0/jackson-annotations-2.8.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.9.0.pr3/jackson-annotations-2.9.0.pr3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/2.9.0/jackson-annotations-2.9.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/3.0-SNAPSHOT/jackson-annotations-3.0-20190323.173155-41.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-annotations/3.0-SNAPSHOT/jackson-annotations-3.0-20190519.085339-43.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.10.0-SNAPSHOT/jackson-core-2.10.0-20190518.180547-60.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.2.0/jackson-core-2.2.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.2.1/jackson-core-2.2.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.3.0/jackson-core-2.3.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.4.0-rc3/jackson-core-2.4.0-rc3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.4.0/jackson-core-2.4.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.4.1.1/jackson-core-2.4.1.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.4.2/jackson-core-2.4.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.4.3/jackson-core-2.4.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.4.5/jackson-core-2.4.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.4.6/jackson-core-2.4.6.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.5.0/jackson-core-2.5.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.5.1/jackson-core-2.5.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.5.3/jackson-core-2.5.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.5.4/jackson-core-2.5.4.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.5.5/jackson-core-2.5.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.6.0-rc1/jackson-core-2.6.0-rc1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.6.0-rc2/jackson-core-2.6.0-rc2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.6.0-rc3/jackson-core-2.6.0-rc3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.6.0/jackson-core-2.6.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.6.1/jackson-core-2.6.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.6.2/jackson-core-2.6.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.6.3/jackson-core-2.6.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.6.4/jackson-core-2.6.4.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.6.5/jackson-core-2.6.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.6.6/jackson-core-2.6.6.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.6.7/jackson-core-2.6.7.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.7.0-rc2/jackson-core-2.7.0-rc2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.7.0/jackson-core-2.7.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.7.1/jackson-core-2.7.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.7.2/jackson-core-2.7.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.7.4/jackson-core-2.7.4.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.7.5/jackson-core-2.7.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.7.6/jackson-core-2.7.6.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.7.7/jackson-core-2.7.7.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.7.8/jackson-core-2.7.8.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.7.9/jackson-core-2.7.9.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.0.rc1/jackson-core-2.8.0.rc1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.0.rc2/jackson-core-2.8.0.rc2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.0/jackson-core-2.8.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.10/jackson-core-2.8.10.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.3/jackson-core-2.8.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.4/jackson-core-2.8.4.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.5/jackson-core-2.8.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.6/jackson-core-2.8.6.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.7/jackson-core-2.8.7.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.8-SNAPSHOT/jackson-core-2.8.8-20170301.000803-1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.8/jackson-core-2.8.8.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.8.9/jackson-core-2.8.9.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.9.0.pr3/jackson-core-2.9.0.pr3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.9.6/jackson-core-2.9.6.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.9.7/jackson-core-2.9.7.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.9.8/jackson-core-2.9.8.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/2.9.9/jackson-core-2.9.9.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/fasterxml/jackson/core/jackson-core/3.0.0-SNAPSHOT/jackson-core-3.0.0-20190516.032312-448.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/google/guava/guava/18.0/guava-18.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/com/google/jimfs/jimfs/1.1/jimfs-1.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/javax/measure/jsr-275/0.9.1/jsr-275-0.9.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/javax/measure/jsr-275/0.9.2/jsr-275-0.9.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/junit/junit/4.10/junit-4.10.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/junit/junit/4.11/junit-4.11.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/junit/junit/4.12/junit-4.12.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/junit/junit/4.8.2/junit-4.8.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/net/bytebuddy/byte-buddy-agent/1.7.5/byte-buddy-agent-1.7.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/net/bytebuddy/byte-buddy-agent/1.9.3/byte-buddy-agent-1.9.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/net/bytebuddy/byte-buddy/1.7.5/byte-buddy-1.7.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/net/bytebuddy/byte-buddy/1.9.3/byte-buddy-1.9.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/assertj/assertj-core/3.8.0/assertj-core-3.8.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/assertj/assertj-core/3.9.1/assertj-core-3.9.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/codehaus/groovy/groovy/1.7.9/groovy-1.7.9.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/codehaus/groovy/groovy/2.4.0/groovy-2.4.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/hamcrest/hamcrest-core/1.1/hamcrest-core-1.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/hibernate/hibernate-cglib-repack/2.1_3/hibernate-cglib-repack-2.1_3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/javassist/javassist/3.19.0-GA/javassist-3.19.0-GA.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/javassist/javassist/3.20.0-GA/javassist-3.20.0-GA.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/javassist/javassist/3.21.0-GA/javassist-3.21.0-GA.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/javassist/javassist/3.22.0-CR2/javassist-3.22.0-CR2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/javassist/javassist/3.24.0-GA/javassist-3.24.0-GA.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/mockito/mockito-all/1.10.19/mockito-all-1.10.19.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/mockito/mockito-core/1.10.19/mockito-core-1.10.19.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/mockito/mockito-core/2.10.0/mockito-core-2.10.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/mockito/mockito-core/2.23.0/mockito-core-2.23.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/objenesis/objenesis/2.1/objenesis-2.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/objenesis/objenesis/2.6/objenesis-2.6.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/objenesis/objenesis/3.0.1/objenesis-3.0.1.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/ow2/asm/asm/4.2/asm-4.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-mockito-common/1.6.5/powermock-api-mockito-common-1.6.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-mockito-common/1.7.3/powermock-api-mockito-common-1.7.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-mockito-common/1.7.4/powermock-api-mockito-common-1.7.4.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-mockito/1.6.2/powermock-api-mockito-1.6.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-mockito/1.6.3/powermock-api-mockito-1.6.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-mockito/1.6.5/powermock-api-mockito-1.6.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-mockito/1.7.3/powermock-api-mockito-1.7.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-mockito/1.7.4/powermock-api-mockito-1.7.4.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-mockito2/2.0.0-beta.5/powermock-api-mockito2-2.0.0-beta.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-mockito2/2.0.0/powermock-api-mockito2-2.0.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-support/1.6.2/powermock-api-support-1.6.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-support/1.6.3/powermock-api-support-1.6.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-support/1.6.5/powermock-api-support-1.6.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-support/1.7.3/powermock-api-support-1.7.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-support/1.7.4/powermock-api-support-1.7.4.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-support/2.0.0-beta.5/powermock-api-support-2.0.0-beta.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-api-support/2.0.0/powermock-api-support-2.0.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-core/1.6.2/powermock-core-1.6.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-core/1.6.3/powermock-core-1.6.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-core/1.6.5/powermock-core-1.6.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-core/1.7.3/powermock-core-1.7.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-core/1.7.4/powermock-core-1.7.4.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-core/2.0.0-beta.5/powermock-core-2.0.0-beta.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-core/2.0.0/powermock-core-2.0.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4-common/1.6.2/powermock-module-junit4-common-1.6.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4-common/1.6.3/powermock-module-junit4-common-1.6.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4-common/1.6.5/powermock-module-junit4-common-1.6.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4-common/1.7.3/powermock-module-junit4-common-1.7.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4-common/1.7.4/powermock-module-junit4-common-1.7.4.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4-common/2.0.0-beta.5/powermock-module-junit4-common-2.0.0-beta.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4-common/2.0.0/powermock-module-junit4-common-2.0.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4/1.6.2/powermock-module-junit4-1.6.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4/1.6.3/powermock-module-junit4-1.6.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4/1.6.5/powermock-module-junit4-1.6.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4/1.7.3/powermock-module-junit4-1.7.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4/1.7.4/powermock-module-junit4-1.7.4.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4/2.0.0-beta.5/powermock-module-junit4-2.0.0-beta.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-module-junit4/2.0.0/powermock-module-junit4-2.0.0.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-reflect/1.6.2/powermock-reflect-1.6.2.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-reflect/1.6.3/powermock-reflect-1.6.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-reflect/1.6.5/powermock-reflect-1.6.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-reflect/1.7.3/powermock-reflect-1.7.3.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-reflect/1.7.4/powermock-reflect-1.7.4.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-reflect/2.0.0-beta.5/powermock-reflect-2.0.0-beta.5.jar"),
            Path("/d4j/framework/projects/JacksonDatabind/lib/org/powermock/powermock-reflect/2.0.0/powermock-reflect-2.0.0.jar")
        )

        override val entrypoints = listOf(
            "com.fasterxml.jackson.databind.jsontype.TestTypeNames::testBaseTypeId1616",
        )
    }

    object JacksonXml1f : Project() {

        override val projectRev = ProjectRev(ProjectID.JACKSON_XML, 1, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/main/java"),
            Path("src/test/java"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-core/2.7.0-rc4-SNAPSHOT/jackson-core-2.7.0-rc4-20160106.062135-1.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-annotations/2.7.0-rc3/jackson-annotations-2.7.0-rc3.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-databind/2.7.0-rc4-SNAPSHOT/jackson-databind-2.7.0-rc4-20160109.222726-11.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/module/jackson-module-jaxb-annotations/2.7.0-rc3/jackson-module-jaxb-annotations-2.7.0-rc3.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/org/codehaus/woodstox/stax2-api/3.1.4/stax2-api-3.1.4.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/woodstox/woodstox-core/5.0.1/woodstox-core-5.0.1.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/junit/junit/4.12/junit-4.12.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar"),
            Path("/d4j/framework/projects/lib/junit-4.11.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-annotations/2.10.0-SNAPSHOT/jackson-annotations-2.10.0-20180816.040648-1.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-annotations/2.3.0/jackson-annotations-2.3.0.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-annotations/2.5.0/jackson-annotations-2.5.0.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-annotations/2.6.0-rc3/jackson-annotations-2.6.0-rc3.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-annotations/2.6.0/jackson-annotations-2.6.0.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-annotations/2.7.0-rc2/jackson-annotations-2.7.0-rc2.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-annotations/2.7.0/jackson-annotations-2.7.0.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-annotations/2.8.0.rc1/jackson-annotations-2.8.0.rc1.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-annotations/2.8.0/jackson-annotations-2.8.0.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-annotations/2.9.0/jackson-annotations-2.9.0.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-annotations/3.0-SNAPSHOT/jackson-annotations-3.0-20190323.173155-41.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-core/2.10.0-SNAPSHOT/jackson-core-2.10.0-20190119.070823-59.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-core/2.3.1/jackson-core-2.3.1.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-core/2.5.0/jackson-core-2.5.0.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-core/2.6.0-rc3/jackson-core-2.6.0-rc3.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-core/2.6.3/jackson-core-2.6.3.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-core/2.6.4/jackson-core-2.6.4.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-core/2.7.0-rc2/jackson-core-2.7.0-rc2.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-core/2.7.3/jackson-core-2.7.3.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-core/2.7.6/jackson-core-2.7.6.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-core/2.7.7/jackson-core-2.7.7.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-core/2.8.0.rc1/jackson-core-2.8.0.rc1.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-core/2.8.5/jackson-core-2.8.5.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-core/2.9.6/jackson-core-2.9.6.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-core/2.9.8/jackson-core-2.9.8.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-core/3.0.0-SNAPSHOT/jackson-core-3.0.0-20190424.231835-446.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-databind/2.10.0-SNAPSHOT/jackson-databind-2.10.0-20190508.041220-162.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-databind/2.3.1/jackson-databind-2.3.1.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-databind/2.5.0/jackson-databind-2.5.0.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-databind/2.6.0-rc3/jackson-databind-2.6.0-rc3.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-databind/2.6.3/jackson-databind-2.6.3.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-databind/2.6.4/jackson-databind-2.6.4.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-databind/2.7.0-rc2/jackson-databind-2.7.0-rc2.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-databind/2.7.3/jackson-databind-2.7.3.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-databind/2.7.6/jackson-databind-2.7.6.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-databind/2.7.7/jackson-databind-2.7.7.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-databind/2.8.0.rc1/jackson-databind-2.8.0.rc1.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-databind/2.8.5/jackson-databind-2.8.5.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-databind/2.9.6/jackson-databind-2.9.6.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-databind/2.9.8/jackson-databind-2.9.8.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-databind/3.0.0-SNAPSHOT/jackson-databind-3.0.0-20190508.041656-759.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/module/jackson-module-jaxb-annotations/2.10.0-SNAPSHOT/jackson-module-jaxb-annotations-2.10.0-20190507.041821-2.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/module/jackson-module-jaxb-annotations/2.3.1/jackson-module-jaxb-annotations-2.3.1.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/module/jackson-module-jaxb-annotations/2.5.0/jackson-module-jaxb-annotations-2.5.0.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/module/jackson-module-jaxb-annotations/2.6.0-rc3/jackson-module-jaxb-annotations-2.6.0-rc3.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/module/jackson-module-jaxb-annotations/2.6.3/jackson-module-jaxb-annotations-2.6.3.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/module/jackson-module-jaxb-annotations/2.6.4/jackson-module-jaxb-annotations-2.6.4.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/module/jackson-module-jaxb-annotations/2.7.0-rc2/jackson-module-jaxb-annotations-2.7.0-rc2.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/module/jackson-module-jaxb-annotations/2.7.3/jackson-module-jaxb-annotations-2.7.3.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/module/jackson-module-jaxb-annotations/2.7.6/jackson-module-jaxb-annotations-2.7.6.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/module/jackson-module-jaxb-annotations/2.7.7/jackson-module-jaxb-annotations-2.7.7.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/module/jackson-module-jaxb-annotations/2.8.0.rc1/jackson-module-jaxb-annotations-2.8.0.rc1.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/module/jackson-module-jaxb-annotations/2.8.5/jackson-module-jaxb-annotations-2.8.5.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/module/jackson-module-jaxb-annotations/2.9.6/jackson-module-jaxb-annotations-2.9.6.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/module/jackson-module-jaxb-annotations/2.9.8/jackson-module-jaxb-annotations-2.9.8.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/module/jackson-module-jaxb-annotations/3.0.0-SNAPSHOT/jackson-module-jaxb-annotations-3.0.0-20180220.170517-3.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/woodstox/woodstox-core/5.0.2/woodstox-core-5.0.2.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/woodstox/woodstox-core/5.0.3/woodstox-core-5.0.3.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/woodstox/woodstox-core/5.2.0/woodstox-core-5.2.0.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/javax/xml/bind/jaxb-api/2.3.0/jaxb-api-2.3.0.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/junit/junit/4.10/junit-4.10.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/junit/junit/4.11/junit-4.11.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/org/codehaus/woodstox/stax2-api/3.1.1/stax2-api-3.1.1.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/org/codehaus/woodstox/stax2-api/4.1/stax2-api-4.1.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/org/codehaus/woodstox/woodstox-core-asl/4.1.5/woodstox-core-asl-4.1.5.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/org/codehaus/woodstox/woodstox-core-asl/4.3.0/woodstox-core-asl-4.3.0.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/org/hamcrest/hamcrest-core/1.1/hamcrest-core-1.1.jar"),
        )

        override val entrypoints = listOf(
            "com.fasterxml.jackson.dataformat.xml.lists.NestedUnwrappedLists180Test::testNestedUnwrappedLists180",
            "com.fasterxml.jackson.dataformat.xml.lists.NestedUnwrappedListsTest::testNestedWithEmpty2",
            "com.fasterxml.jackson.dataformat.xml.lists.NestedUnwrappedListsTest::testNestedWithEmpty",
        )
    }

    object JacksonXml3f : Project() {

        override val projectRev = ProjectRev(ProjectID.JACKSON_XML, 3, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/main/java"),
            Path("src/test/java"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-core/2.7.6/jackson-core-2.7.6.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-annotations/2.7.0/jackson-annotations-2.7.0.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-databind/2.7.6/jackson-databind-2.7.6.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/module/jackson-module-jaxb-annotations/2.7.6/jackson-module-jaxb-annotations-2.7.6.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/org/codehaus/woodstox/stax2-api/3.1.4/stax2-api-3.1.4.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/woodstox/woodstox-core/5.0.2/woodstox-core-5.0.2.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/junit/junit/4.12/junit-4.12.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar"),
            Path("/d4j/framework/projects/lib/junit-4.11.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-annotations/2.10.0-SNAPSHOT/jackson-annotations-2.10.0-20180816.040648-1.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-annotations/2.3.0/jackson-annotations-2.3.0.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-annotations/2.5.0/jackson-annotations-2.5.0.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-annotations/2.6.0-rc3/jackson-annotations-2.6.0-rc3.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-annotations/2.6.0/jackson-annotations-2.6.0.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-annotations/2.7.0-rc2/jackson-annotations-2.7.0-rc2.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-annotations/2.7.0-rc3/jackson-annotations-2.7.0-rc3.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-annotations/2.8.0.rc1/jackson-annotations-2.8.0.rc1.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-annotations/2.8.0/jackson-annotations-2.8.0.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-annotations/2.9.0/jackson-annotations-2.9.0.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-annotations/3.0-SNAPSHOT/jackson-annotations-3.0-20190323.173155-41.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-core/2.10.0-SNAPSHOT/jackson-core-2.10.0-20190119.070823-59.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-core/2.3.1/jackson-core-2.3.1.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-core/2.5.0/jackson-core-2.5.0.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-core/2.6.0-rc3/jackson-core-2.6.0-rc3.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-core/2.6.3/jackson-core-2.6.3.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-core/2.6.4/jackson-core-2.6.4.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-core/2.7.0-rc2/jackson-core-2.7.0-rc2.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-core/2.7.0-rc4-SNAPSHOT/jackson-core-2.7.0-rc4-20160106.062135-1.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-core/2.7.3/jackson-core-2.7.3.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-core/2.7.7/jackson-core-2.7.7.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-core/2.8.0.rc1/jackson-core-2.8.0.rc1.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-core/2.8.5/jackson-core-2.8.5.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-core/2.9.6/jackson-core-2.9.6.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-core/2.9.8/jackson-core-2.9.8.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-core/3.0.0-SNAPSHOT/jackson-core-3.0.0-20190424.231835-446.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-databind/2.10.0-SNAPSHOT/jackson-databind-2.10.0-20190508.041220-162.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-databind/2.3.1/jackson-databind-2.3.1.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-databind/2.5.0/jackson-databind-2.5.0.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-databind/2.6.0-rc3/jackson-databind-2.6.0-rc3.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-databind/2.6.3/jackson-databind-2.6.3.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-databind/2.6.4/jackson-databind-2.6.4.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-databind/2.7.0-rc2/jackson-databind-2.7.0-rc2.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-databind/2.7.0-rc4-SNAPSHOT/jackson-databind-2.7.0-rc4-20160109.222726-11.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-databind/2.7.3/jackson-databind-2.7.3.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-databind/2.7.7/jackson-databind-2.7.7.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-databind/2.8.0.rc1/jackson-databind-2.8.0.rc1.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-databind/2.8.5/jackson-databind-2.8.5.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-databind/2.9.6/jackson-databind-2.9.6.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-databind/2.9.8/jackson-databind-2.9.8.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-databind/3.0.0-SNAPSHOT/jackson-databind-3.0.0-20190508.041656-759.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/module/jackson-module-jaxb-annotations/2.10.0-SNAPSHOT/jackson-module-jaxb-annotations-2.10.0-20190507.041821-2.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/module/jackson-module-jaxb-annotations/2.3.1/jackson-module-jaxb-annotations-2.3.1.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/module/jackson-module-jaxb-annotations/2.5.0/jackson-module-jaxb-annotations-2.5.0.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/module/jackson-module-jaxb-annotations/2.6.0-rc3/jackson-module-jaxb-annotations-2.6.0-rc3.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/module/jackson-module-jaxb-annotations/2.6.3/jackson-module-jaxb-annotations-2.6.3.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/module/jackson-module-jaxb-annotations/2.6.4/jackson-module-jaxb-annotations-2.6.4.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/module/jackson-module-jaxb-annotations/2.7.0-rc2/jackson-module-jaxb-annotations-2.7.0-rc2.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/module/jackson-module-jaxb-annotations/2.7.0-rc3/jackson-module-jaxb-annotations-2.7.0-rc3.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/module/jackson-module-jaxb-annotations/2.7.3/jackson-module-jaxb-annotations-2.7.3.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/module/jackson-module-jaxb-annotations/2.7.7/jackson-module-jaxb-annotations-2.7.7.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/module/jackson-module-jaxb-annotations/2.8.0.rc1/jackson-module-jaxb-annotations-2.8.0.rc1.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/module/jackson-module-jaxb-annotations/2.8.5/jackson-module-jaxb-annotations-2.8.5.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/module/jackson-module-jaxb-annotations/2.9.6/jackson-module-jaxb-annotations-2.9.6.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/module/jackson-module-jaxb-annotations/2.9.8/jackson-module-jaxb-annotations-2.9.8.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/module/jackson-module-jaxb-annotations/3.0.0-SNAPSHOT/jackson-module-jaxb-annotations-3.0.0-20180220.170517-3.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/woodstox/woodstox-core/5.0.1/woodstox-core-5.0.1.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/woodstox/woodstox-core/5.0.3/woodstox-core-5.0.3.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/woodstox/woodstox-core/5.2.0/woodstox-core-5.2.0.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/javax/xml/bind/jaxb-api/2.3.0/jaxb-api-2.3.0.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/junit/junit/4.10/junit-4.10.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/junit/junit/4.11/junit-4.11.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/org/codehaus/woodstox/stax2-api/3.1.1/stax2-api-3.1.1.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/org/codehaus/woodstox/stax2-api/4.1/stax2-api-4.1.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/org/codehaus/woodstox/woodstox-core-asl/4.1.5/woodstox-core-asl-4.1.5.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/org/codehaus/woodstox/woodstox-core-asl/4.3.0/woodstox-core-asl-4.3.0.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/org/hamcrest/hamcrest-core/1.1/hamcrest-core-1.1.jar"),
        )

        override val entrypoints = listOf(
            "com.fasterxml.jackson.dataformat.xml.stream.XmlParserNextXxxTest::testXmlAttributesWithNextTextValue",
        )
    }

    object JacksonXml4f : Project() {

        override val projectRev = ProjectRev(ProjectID.JACKSON_XML, 4, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/main/java"),
            Path("src/test/java"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-core/2.8.5/jackson-core-2.8.5.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-annotations/2.8.0/jackson-annotations-2.8.0.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-databind/2.8.5/jackson-databind-2.8.5.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/module/jackson-module-jaxb-annotations/2.8.5/jackson-module-jaxb-annotations-2.8.5.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/org/codehaus/woodstox/stax2-api/3.1.4/stax2-api-3.1.4.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/woodstox/woodstox-core/5.0.3/woodstox-core-5.0.3.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/junit/junit/4.12/junit-4.12.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar"),
            Path("/d4j/framework/projects/lib/junit-4.11.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-annotations/2.10.0-SNAPSHOT/jackson-annotations-2.10.0-20180816.040648-1.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-annotations/2.3.0/jackson-annotations-2.3.0.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-annotations/2.5.0/jackson-annotations-2.5.0.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-annotations/2.6.0-rc3/jackson-annotations-2.6.0-rc3.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-annotations/2.6.0/jackson-annotations-2.6.0.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-annotations/2.7.0-rc2/jackson-annotations-2.7.0-rc2.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-annotations/2.7.0-rc3/jackson-annotations-2.7.0-rc3.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-annotations/2.7.0/jackson-annotations-2.7.0.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-annotations/2.8.0.rc1/jackson-annotations-2.8.0.rc1.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-annotations/2.9.0/jackson-annotations-2.9.0.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-annotations/3.0-SNAPSHOT/jackson-annotations-3.0-20190323.173155-41.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-core/2.10.0-SNAPSHOT/jackson-core-2.10.0-20190119.070823-59.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-core/2.3.1/jackson-core-2.3.1.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-core/2.5.0/jackson-core-2.5.0.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-core/2.6.0-rc3/jackson-core-2.6.0-rc3.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-core/2.6.3/jackson-core-2.6.3.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-core/2.6.4/jackson-core-2.6.4.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-core/2.7.0-rc2/jackson-core-2.7.0-rc2.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-core/2.7.0-rc4-SNAPSHOT/jackson-core-2.7.0-rc4-20160106.062135-1.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-core/2.7.3/jackson-core-2.7.3.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-core/2.7.6/jackson-core-2.7.6.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-core/2.7.7/jackson-core-2.7.7.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-core/2.8.0.rc1/jackson-core-2.8.0.rc1.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-core/2.9.6/jackson-core-2.9.6.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-core/2.9.8/jackson-core-2.9.8.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-core/3.0.0-SNAPSHOT/jackson-core-3.0.0-20190424.231835-446.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-databind/2.10.0-SNAPSHOT/jackson-databind-2.10.0-20190508.041220-162.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-databind/2.3.1/jackson-databind-2.3.1.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-databind/2.5.0/jackson-databind-2.5.0.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-databind/2.6.0-rc3/jackson-databind-2.6.0-rc3.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-databind/2.6.3/jackson-databind-2.6.3.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-databind/2.6.4/jackson-databind-2.6.4.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-databind/2.7.0-rc2/jackson-databind-2.7.0-rc2.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-databind/2.7.0-rc4-SNAPSHOT/jackson-databind-2.7.0-rc4-20160109.222726-11.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-databind/2.7.3/jackson-databind-2.7.3.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-databind/2.7.6/jackson-databind-2.7.6.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-databind/2.7.7/jackson-databind-2.7.7.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-databind/2.8.0.rc1/jackson-databind-2.8.0.rc1.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-databind/2.9.6/jackson-databind-2.9.6.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-databind/2.9.8/jackson-databind-2.9.8.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/core/jackson-databind/3.0.0-SNAPSHOT/jackson-databind-3.0.0-20190508.041656-759.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/module/jackson-module-jaxb-annotations/2.10.0-SNAPSHOT/jackson-module-jaxb-annotations-2.10.0-20190507.041821-2.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/module/jackson-module-jaxb-annotations/2.3.1/jackson-module-jaxb-annotations-2.3.1.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/module/jackson-module-jaxb-annotations/2.5.0/jackson-module-jaxb-annotations-2.5.0.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/module/jackson-module-jaxb-annotations/2.6.0-rc3/jackson-module-jaxb-annotations-2.6.0-rc3.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/module/jackson-module-jaxb-annotations/2.6.3/jackson-module-jaxb-annotations-2.6.3.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/module/jackson-module-jaxb-annotations/2.6.4/jackson-module-jaxb-annotations-2.6.4.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/module/jackson-module-jaxb-annotations/2.7.0-rc2/jackson-module-jaxb-annotations-2.7.0-rc2.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/module/jackson-module-jaxb-annotations/2.7.0-rc3/jackson-module-jaxb-annotations-2.7.0-rc3.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/module/jackson-module-jaxb-annotations/2.7.3/jackson-module-jaxb-annotations-2.7.3.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/module/jackson-module-jaxb-annotations/2.7.6/jackson-module-jaxb-annotations-2.7.6.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/module/jackson-module-jaxb-annotations/2.7.7/jackson-module-jaxb-annotations-2.7.7.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/module/jackson-module-jaxb-annotations/2.8.0.rc1/jackson-module-jaxb-annotations-2.8.0.rc1.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/module/jackson-module-jaxb-annotations/2.9.6/jackson-module-jaxb-annotations-2.9.6.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/module/jackson-module-jaxb-annotations/2.9.8/jackson-module-jaxb-annotations-2.9.8.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/jackson/module/jackson-module-jaxb-annotations/3.0.0-SNAPSHOT/jackson-module-jaxb-annotations-3.0.0-20180220.170517-3.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/woodstox/woodstox-core/5.0.1/woodstox-core-5.0.1.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/woodstox/woodstox-core/5.0.2/woodstox-core-5.0.2.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/com/fasterxml/woodstox/woodstox-core/5.2.0/woodstox-core-5.2.0.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/javax/xml/bind/jaxb-api/2.3.0/jaxb-api-2.3.0.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/junit/junit/4.10/junit-4.10.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/junit/junit/4.11/junit-4.11.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/org/codehaus/woodstox/stax2-api/3.1.1/stax2-api-3.1.1.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/org/codehaus/woodstox/stax2-api/4.1/stax2-api-4.1.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/org/codehaus/woodstox/woodstox-core-asl/4.1.5/woodstox-core-asl-4.1.5.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/org/codehaus/woodstox/woodstox-core-asl/4.3.0/woodstox-core-asl-4.3.0.jar"),
            Path("/d4j/framework/projects/JacksonXml/lib/org/hamcrest/hamcrest-core/1.1/hamcrest-core-1.1.jar"),
        )

        override val entrypoints = listOf(
            "com.fasterxml.jackson.dataformat.xml.misc.RootNameTest::testDynamicRootName",
        )
    }

    object Jsoup86f : Project() {

        override val projectRev = ProjectRev(ProjectID.JSOUP, 86, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/main/java"),
            Path("src/test/java"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/Jsoup/lib/junit/junit/4.12/junit-4.12.jar"),
            Path("/d4j/framework/projects/Jsoup/lib/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar"),
            Path("/d4j/framework/projects/Jsoup/lib/com/google/code/gson/gson/2.7/gson-2.7.jar"),
            Path("/d4j/framework/projects/Jsoup/lib/org/eclipse/jetty/jetty-server/9.2.26.v20180806/jetty-server-9.2.26.v20180806.jar"),
            Path("/d4j/framework/projects/Jsoup/lib/javax/servlet/javax.servlet-api/3.1.0/javax.servlet-api-3.1.0.jar"),
            Path("/d4j/framework/projects/Jsoup/lib/org/eclipse/jetty/jetty-http/9.2.26.v20180806/jetty-http-9.2.26.v20180806.jar"),
            Path("/d4j/framework/projects/Jsoup/lib/org/eclipse/jetty/jetty-util/9.2.26.v20180806/jetty-util-9.2.26.v20180806.jar"),
            Path("/d4j/framework/projects/Jsoup/lib/org/eclipse/jetty/jetty-io/9.2.26.v20180806/jetty-io-9.2.26.v20180806.jar"),
            Path("/d4j/framework/projects/Jsoup/lib/org/eclipse/jetty/jetty-servlet/9.2.26.v20180806/jetty-servlet-9.2.26.v20180806.jar"),
            Path("/d4j/framework/projects/Jsoup/lib/org/eclipse/jetty/jetty-security/9.2.26.v20180806/jetty-security-9.2.26.v20180806.jar"),
            Path("/d4j/framework/projects/lib/junit-4.11.jar"),
            Path("/d4j/framework/projects/Jsoup/lib/com/google/code/gson/gson/2.7/gson-2.7.jar"),
            Path("/d4j/framework/projects/Jsoup/lib/commons-lang/commons-lang/2.4/commons-lang-2.4.jar"),
            Path("/d4j/framework/projects/Jsoup/lib/javax/servlet/javax.servlet-api/3.1.0/javax.servlet-api-3.1.0.jar"),
            Path("/d4j/framework/projects/Jsoup/lib/junit/junit/4.12/junit-4.12.jar"),
            Path("/d4j/framework/projects/Jsoup/lib/junit/junit/4.5/junit-4.5.jar"),
            Path("/d4j/framework/projects/Jsoup/lib/org/eclipse/jetty/jetty-http/9.2.22.v20170606/jetty-http-9.2.22.v20170606.jar"),
            Path("/d4j/framework/projects/Jsoup/lib/org/eclipse/jetty/jetty-http/9.2.26.v20180806/jetty-http-9.2.26.v20180806.jar"),
            Path("/d4j/framework/projects/Jsoup/lib/org/eclipse/jetty/jetty-http/9.2.28.v20190418/jetty-http-9.2.28.v20190418.jar"),
            Path("/d4j/framework/projects/Jsoup/lib/org/eclipse/jetty/jetty-io/9.2.22.v20170606/jetty-io-9.2.22.v20170606.jar"),
            Path("/d4j/framework/projects/Jsoup/lib/org/eclipse/jetty/jetty-io/9.2.26.v20180806/jetty-io-9.2.26.v20180806.jar"),
            Path("/d4j/framework/projects/Jsoup/lib/org/eclipse/jetty/jetty-io/9.2.28.v20190418/jetty-io-9.2.28.v20190418.jar"),
            Path("/d4j/framework/projects/Jsoup/lib/org/eclipse/jetty/jetty-security/9.2.22.v20170606/jetty-security-9.2.22.v20170606.jar"),
            Path("/d4j/framework/projects/Jsoup/lib/org/eclipse/jetty/jetty-security/9.2.26.v20180806/jetty-security-9.2.26.v20180806.jar"),
            Path("/d4j/framework/projects/Jsoup/lib/org/eclipse/jetty/jetty-security/9.2.28.v20190418/jetty-security-9.2.28.v20190418.jar"),
            Path("/d4j/framework/projects/Jsoup/lib/org/eclipse/jetty/jetty-server/9.2.22.v20170606/jetty-server-9.2.22.v20170606.jar"),
            Path("/d4j/framework/projects/Jsoup/lib/org/eclipse/jetty/jetty-server/9.2.26.v20180806/jetty-server-9.2.26.v20180806.jar"),
            Path("/d4j/framework/projects/Jsoup/lib/org/eclipse/jetty/jetty-server/9.2.28.v20190418/jetty-server-9.2.28.v20190418.jar"),
            Path("/d4j/framework/projects/Jsoup/lib/org/eclipse/jetty/jetty-servlet/9.2.22.v20170606/jetty-servlet-9.2.22.v20170606.jar"),
            Path("/d4j/framework/projects/Jsoup/lib/org/eclipse/jetty/jetty-servlet/9.2.26.v20180806/jetty-servlet-9.2.26.v20180806.jar"),
            Path("/d4j/framework/projects/Jsoup/lib/org/eclipse/jetty/jetty-servlet/9.2.28.v20190418/jetty-servlet-9.2.28.v20190418.jar"),
            Path("/d4j/framework/projects/Jsoup/lib/org/eclipse/jetty/jetty-util/9.2.22.v20170606/jetty-util-9.2.22.v20170606.jar"),
            Path("/d4j/framework/projects/Jsoup/lib/org/eclipse/jetty/jetty-util/9.2.26.v20180806/jetty-util-9.2.26.v20180806.jar"),
            Path("/d4j/framework/projects/Jsoup/lib/org/eclipse/jetty/jetty-util/9.2.28.v20190418/jetty-util-9.2.28.v20190418.jar"),
            Path("/d4j/framework/projects/Jsoup/lib/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar"),
        )

        override val entrypoints = listOf(
            "org.jsoup.parser.XmlTreeBuilderTest::handlesLTinScript",
        )
    }

    object JxPath1f : Project() {

        override val projectRev = ProjectRev(ProjectID.JX_PATH, 1, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/java"),
            Path("src/test"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("target/lib/ant-1.5.jar"),
            Path("target/lib/ant-optional-1.5.1.jar"),
            Path("target/lib/ant-optional-1.5.jar"),
            Path("target/lib/commons-beanutils-1.4.jar"),
            Path("target/lib/commons-collections-2.0.jar"),
            Path("target/lib/commons-logging-1.0.4.jar"),
            Path("target/lib/jdom-1.0.jar"),
            Path("target/lib/junit-3.8.1.jar"),
            Path("target/lib/junit-3.8.jar"),
            Path("target/lib/mockrunner-0.4.1.jar"),
            Path("target/lib/servletapi-2.2.jar"),
            Path("target/lib/xerces-1.2.3.jar"),
            Path("target/lib/xml-apis-2.0.2.jar"),
            Path("/d4j/framework/projects/lib/junit-4.11.jar"),
        )

        override val entrypoints = listOf(
            "org.apache.commons.jxpath.ri.model.dom.DOMModelTest::testGetNode",
            "org.apache.commons.jxpath.ri.model.jdom.JDOMModelTest::testGetNode",
        )
    }

    object JxPath2f : Project() {

        override val projectRev = ProjectRev(ProjectID.JX_PATH, 2, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/java"),
            Path("src/test"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("target/lib/ant-1.5.jar"),
            Path("target/lib/ant-optional-1.5.1.jar"),
            Path("target/lib/ant-optional-1.5.jar"),
            Path("target/lib/commons-beanutils-1.4.jar"),
            Path("target/lib/commons-collections-2.0.jar"),
            Path("target/lib/commons-logging-1.0.4.jar"),
            Path("target/lib/jdom-1.0.jar"),
            Path("target/lib/junit-3.8.1.jar"),
            Path("target/lib/junit-3.8.jar"),
            Path("target/lib/mockrunner-0.4.1.jar"),
            Path("target/lib/servletapi-2.2.jar"),
            Path("target/lib/xerces-1.2.3.jar"),
            Path("target/lib/xml-apis-2.0.2.jar"),
            Path("/d4j/framework/projects/lib/junit-4.11.jar"),
        )

        override val entrypoints = listOf(
            "org.apache.commons.jxpath.ri.compiler.ExtensionFunctionTest::testNodeSetReturn",
        )
    }

    object JxPath3f : Project() {

        override val projectRev = ProjectRev(ProjectID.JX_PATH, 3, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/java"),
            Path("src/test"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("target/lib/ant-1.5.jar"),
            Path("target/lib/ant-optional-1.5.1.jar"),
            Path("target/lib/ant-optional-1.5.jar"),
            Path("target/lib/commons-beanutils-1.4.jar"),
            Path("target/lib/commons-collections-2.0.jar"),
            Path("target/lib/commons-logging-1.0.4.jar"),
            Path("target/lib/jdom-1.0.jar"),
            Path("target/lib/junit-3.8.1.jar"),
            Path("target/lib/junit-3.8.jar"),
            Path("target/lib/mockrunner-0.4.1.jar"),
            Path("target/lib/servletapi-2.2.jar"),
            Path("target/lib/xerces-1.2.3.jar"),
            Path("target/lib/xml-apis-2.0.2.jar"),
            Path("/d4j/framework/projects/lib/junit-4.11.jar"),
        )

        override val entrypoints = listOf(
            "org.apache.commons.jxpath.ri.model.beans.BadlyImplementedFactoryTest::testBadFactoryImplementation",
        )
    }

    object JxPath19f : Project() {

        override val projectRev = ProjectRev(ProjectID.JX_PATH, 19, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/java"),
            Path("src/test"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("target/lib/commons-beanutils-1.7.0.jar"),
            Path("target/lib/commons-logging-1.1.jar"),
            Path("target/lib/jdom-1.0.jar"),
            Path("target/lib/jsp-api-2.0.jar"),
            Path("target/lib/junit-3.8.1.jar"),
            Path("target/lib/mockrunner-0.4.1.jar"),
            Path("target/lib/mockrunner-jdk1.3-j2ee1.3-0.4.jar"),
            Path("target/lib/servletapi-2.4.jar"),
            Path("target/lib/xerces-2.4.0.jar"),
            Path("target/lib/xml-apis-2.0.2.jar"),
            Path("/d4j/framework/projects/lib/junit-4.11.jar"),
        )

        override val entrypoints = listOf(
            "org.apache.commons.jxpath.ri.model.AliasedNamespaceIterationTest::testIterateJDOM",
            "org.apache.commons.jxpath.ri.model.AliasedNamespaceIterationTest::testIterateDOM",
        )
    }

    object JxPath20f : Project() {

        override val projectRev = ProjectRev(ProjectID.JX_PATH, 20, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/java"),
            Path("src/test"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("target/lib/commons-beanutils-1.7.0.jar"),
            Path("target/lib/commons-logging-1.1.jar"),
            Path("target/lib/jdom-1.0.jar"),
            Path("target/lib/jsp-api-2.0.jar"),
            Path("target/lib/junit-3.8.1.jar"),
            Path("target/lib/mockrunner-0.4.1.jar"),
            Path("target/lib/mockrunner-jdk1.3-j2ee1.3-0.4.jar"),
            Path("target/lib/servletapi-2.4.jar"),
            Path("target/lib/xerces-2.4.0.jar"),
            Path("target/lib/xml-apis-2.0.2.jar"),
            Path("/d4j/framework/projects/lib/junit-4.11.jar"),
        )

        override val entrypoints = listOf(
            "org.apache.commons.jxpath.ri.compiler.JXPath149Test::testComplexOperationWithVariables",
        )
    }

    object JxPath21f : Project() {

        override val projectRev = ProjectRev(ProjectID.JX_PATH, 21, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/java"),
            Path("src/test"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("target/lib/commons-beanutils-1.7.0.jar"),
            Path("target/lib/commons-logging-1.1.jar"),
            Path("target/lib/jdom-1.0.jar"),
            Path("target/lib/jsp-api-2.0.jar"),
            Path("target/lib/junit-3.8.1.jar"),
            Path("target/lib/mockrunner-0.4.1.jar"),
            Path("target/lib/mockrunner-jdk1.3-j2ee1.3-0.4.jar"),
            Path("target/lib/servletapi-2.4.jar"),
            Path("target/lib/xerces-2.4.0.jar"),
            Path("target/lib/xml-apis-2.0.2.jar"),
            Path("/d4j/framework/projects/lib/junit-4.11.jar"),
        )

        override val entrypoints = listOf(
            "org.apache.commons.jxpath.ri.model.JXPath151Test::testMapValueEquality",
            "org.apache.commons.jxpath.ri.model.MixedModelTest::testNull",
        )
    }

    object JxPath22f : Project() {

        override val projectRev = ProjectRev(ProjectID.JX_PATH, 22, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/java"),
            Path("src/test"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("target/lib/commons-beanutils-1.7.0.jar"),
            Path("target/lib/commons-logging-1.1.jar"),
            Path("target/lib/jdom-1.0.jar"),
            Path("target/lib/jsp-api-2.0.jar"),
            Path("target/lib/junit-3.8.1.jar"),
            Path("target/lib/mockrunner-0.4.1.jar"),
            Path("target/lib/mockrunner-jdk1.3-j2ee1.3-0.4.jar"),
            Path("target/lib/servletapi-2.4.jar"),
            Path("target/lib/xerces-2.4.0.jar"),
            Path("target/lib/xml-apis-2.0.2.jar"),
            Path("/d4j/framework/projects/lib/junit-4.11.jar"),
        )

        override val entrypoints = listOf(
            "org.apache.commons.jxpath.ri.model.JXPath154Test::testInnerEmptyNamespaceDOM",
        )
    }

    object Lang1f : Project() {

        override val projectRev = ProjectRev(ProjectID.LANG, 1, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/main/java"),
            Path("src/test/java"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/lib/junit-4.11.jar"),
            Path("/d4j/framework/projects/Lang/lib/easymock.jar"),
            Path("/d4j/framework/projects/Lang/lib/commons-io.jar"),
            Path("/d4j/framework/projects/Lang/lib/cglib.jar"),
            Path("/d4j/framework/projects/Lang/lib/asm.jar"),
        )

        override val entrypoints = listOf(
            "org.apache.commons.lang3.math.NumberUtilsTest::TestLang747",
        )
    }

    object Lang4b : Project() {

        override val projectRev = ProjectRev(ProjectID.LANG, 4, BugVersion.BUGGY)

        override val srcTypeSolverPaths = listOf(
            Path("src/main/java"),
            Path("src/test/java"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/lib/junit-4.11.jar"),
            Path("/d4j/framework/projects/Lang/lib/easymock.jar"),
            Path("/d4j/framework/projects/Lang/lib/commons-io.jar"),
            Path("/d4j/framework/projects/Lang/lib/cglib.jar"),
            Path("/d4j/framework/projects/Lang/lib/asm.jar"),
        )

        override val entrypoints = listOf(
            "org.apache.commons.lang3.text.translate.LookupTranslatorTest::testLang882",
        )
    }

    object Lang8f : Project() {

        override val projectRev = ProjectRev(ProjectID.LANG, 8, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/main/java"),
            Path("src/test/java"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/lib/junit-4.11.jar"),
            Path("/d4j/framework/projects/Lang/lib/easymock.jar"),
            Path("/d4j/framework/projects/Lang/lib/commons-io.jar"),
            Path("/d4j/framework/projects/Lang/lib/cglib.jar"),
            Path("/d4j/framework/projects/Lang/lib/asm.jar"),
        )

        override val entrypoints = listOf(
            "org.apache.commons.lang3.time.FastDateFormat_PrinterTest::testCalendarTimezoneRespected",
            "org.apache.commons.lang3.time.FastDatePrinterTest::testCalendarTimezoneRespected",
        )
    }

    object Lang15b : Project() {

        override val projectRev = ProjectRev(ProjectID.LANG, 15, BugVersion.BUGGY)

        override val srcTypeSolverPaths = listOf(
            Path("src/main/java"),
            Path("src/test/java"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/lib/junit-4.11.jar"),
            Path("/d4j/framework/projects/Lang/lib/easymock.jar"),
            Path("/d4j/framework/projects/Lang/lib/commons-io.jar"),
            Path("/d4j/framework/projects/Lang/lib/cglib.jar"),
            Path("/d4j/framework/projects/Lang/lib/asm.jar"),
        )

        override val entrypoints = listOf(
            "org.apache.commons.lang3.reflect.TypeUtilsTest::testGetTypeArguments",
            "org.apache.commons.lang3.reflect.TypeUtilsTest::testIsAssignable",
        )
    }

    object Lang17f : Project() {

        override val projectRev = ProjectRev(ProjectID.LANG, 17, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/main/java"),
            Path("src/test/java"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/lib/junit-4.11.jar"),
            Path("/d4j/framework/projects/Lang/lib/easymock.jar"),
            Path("/d4j/framework/projects/Lang/lib/cglib.jar"),
            Path("/d4j/framework/projects/Lang/lib/asm.jar"),
            Path("/d4j/framework/projects/Lang/lib/commons-io.jar"),
        )

        override val entrypoints = listOf(
            "org.apache.commons.lang3.StringEscapeUtilsTest::testLang720",
        )
    }

    object Lang18f : Project() {

        override val projectRev = ProjectRev(ProjectID.LANG, 18, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/main/java"),
            Path("src/test/java"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/lib/junit-4.11.jar"),
            Path("/d4j/framework/projects/Lang/lib/easymock.jar"),
            Path("/d4j/framework/projects/Lang/lib/cglib.jar"),
            Path("/d4j/framework/projects/Lang/lib/asm.jar"),
            Path("/d4j/framework/projects/Lang/lib/commons-io.jar"),
        )

        override val entrypoints = listOf(
            "org.apache.commons.lang3.time.FastDateFormatTest::testFormat",
        )
    }

    object Lang19f : Project() {

        override val projectRev = ProjectRev(ProjectID.LANG, 19, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/main/java"),
            Path("src/test/java"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/lib/junit-4.11.jar"),
            Path("/d4j/framework/projects/Lang/lib/easymock.jar"),
            Path("/d4j/framework/projects/Lang/lib/cglib.jar"),
            Path("/d4j/framework/projects/Lang/lib/asm.jar"),
            Path("/d4j/framework/projects/Lang/lib/commons-io.jar"),
        )

        override val entrypoints = listOf(
            "org.apache.commons.lang3.text.translate.NumericEntityUnescaperTest::testUnfinishedEntity",
            "org.apache.commons.lang3.text.translate.NumericEntityUnescaperTest::testOutOfBounds",
        )
    }

    object Lang20f : Project() {

        override val projectRev = ProjectRev(ProjectID.LANG, 20, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/main/java"),
            Path("src/test/java"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/lib/junit-4.11.jar"),
            Path("/d4j/framework/projects/Lang/lib/easymock.jar"),
            Path("/d4j/framework/projects/Lang/lib/cglib.jar"),
            Path("/d4j/framework/projects/Lang/lib/asm.jar"),
            Path("/d4j/framework/projects/Lang/lib/commons-io.jar"),
        )

        override val entrypoints = listOf(
            "org.apache.commons.lang3.StringUtilsTest::testJoin_ArrayChar",
            "org.apache.commons.lang3.StringUtilsTest::testJoin_Objectarray",
        )
    }

    object Lang27f : Project() {

        override val projectRev = ProjectRev(ProjectID.LANG, 27, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/main/java"),
            Path("src/test/java"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/Lang/lib/junit/junit/4.7/junit-4.7.jar"),
            Path("/d4j/framework/projects/Lang/lib/org/easymock/easymock/2.5.2/easymock-2.5.2.jar"),
            Path("/d4j/framework/projects/Lang/lib/cglib.jar"),
            Path("/d4j/framework/projects/Lang/lib/asm.jar"),
            Path("/d4j/framework/projects/Lang/lib/easymock.jar"),
            Path("/d4j/framework/projects/Lang/lib/commons-io.jar"),
            Path("/d4j/framework/projects/lib/junit-4.11.jar"),
        )

        override val entrypoints = listOf(
            "org.apache.commons.lang3.math.NumberUtilsTest::testCreateNumber",
        )
    }

    object Lang42f : Project() {

        override val projectRev = ProjectRev(ProjectID.LANG, 42, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/java"),
            Path("src/test"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/lib/junit-4.11.jar"),
            Path("/d4j/framework/projects/Lang/lib/cglib.jar"),
            Path("/d4j/framework/projects/Lang/lib/asm.jar"),
            Path("/d4j/framework/projects/Lang/lib/easymock.jar"),
            Path("/d4j/framework/projects/Lang/lib/commons-io.jar"),
        )

        override val entrypoints = listOf(
            "org.apache.commons.lang.StringEscapeUtilsTest::testEscapeHtmlHighUnicode",
        )
    }

    object Lang57f : Project() {

        override val projectRev = ProjectRev(ProjectID.LANG, 57, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/java"),
            Path("src/test"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/lib/junit-4.11.jar"),
            Path("/d4j/framework/projects/Lang/lib/cglib.jar"),
            Path("/d4j/framework/projects/Lang/lib/asm.jar"),
            Path("/d4j/framework/projects/Lang/lib/easymock.jar"),
            Path("/d4j/framework/projects/Lang/lib/commons-io.jar"),
        )

        override val entrypoints = listOf(
            "org.apache.commons.lang.LocaleUtilsTest::testAvailableLocaleSet",
            "org.apache.commons.lang.LocaleUtilsTest::testIsAvailableLocale",
            "org.apache.commons.lang.LocaleUtilsTest::testAvailableLocaleList",
            "org.apache.commons.lang.LocaleUtilsTest::testCountriesByLanguage",
            "org.apache.commons.lang.LocaleUtilsTest::testLocaleLookupList_LocaleLocale",
            "org.apache.commons.lang.LocaleUtilsTest::testLanguagesByCountry",
            "org.apache.commons.lang.LocaleUtilsTest::testToLocale_1Part",
            "org.apache.commons.lang.LocaleUtilsTest::testToLocale_2Part",
            "org.apache.commons.lang.LocaleUtilsTest::testToLocale_3Part",
            "org.apache.commons.lang.LocaleUtilsTest::testLocaleLookupList_Locale",
            "org.apache.commons.lang.LocaleUtilsTest::testConstructor",
        )
    }

    object Lang64f : Project() {

        override val projectRev = ProjectRev(ProjectID.LANG, 64, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/java"),
            Path("src/test"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/lib/junit-4.11.jar"),
            Path("/d4j/framework/projects/Lang/lib/cglib.jar"),
            Path("/d4j/framework/projects/Lang/lib/asm.jar"),
            Path("/d4j/framework/projects/Lang/lib/easymock.jar"),
            Path("/d4j/framework/projects/Lang/lib/commons-io.jar"),
        )

        override val entrypoints = listOf(
            "org.apache.commons.lang.enums.ValuedEnumTest::testCompareTo_otherEnumType",
        )
    }

    object Math1f : Project() {

        override val projectRev = ProjectRev(ProjectID.MATH, 1, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/main/java"),
            Path("src/test/java"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/lib/junit-4.11.jar"),
            Path("/d4j/framework/projects/Math/lib/commons-discovery-0.5.jar")
        )

        override val entrypoints = listOf(
            "org.apache.commons.math3.fraction.BigFractionTest::testDigitLimitConstructor",
            "org.apache.commons.math3.fraction.FractionTest::testDigitLimitConstructor",
        )
    }

    object Math2f : Project() {

        override val projectRev = ProjectRev(ProjectID.MATH, 2, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/main/java"),
            Path("src/test/java"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/lib/junit-4.11.jar"),
            Path("/d4j/framework/projects/Math/lib/commons-discovery-0.5.jar")
        )

        override val entrypoints = listOf(
            "org.apache.commons.math3.distribution.HypergeometricDistributionTest::testMath1021",
        )
    }

    object Math4f : Project() {

        override val projectRev = ProjectRev(ProjectID.MATH, 4, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/main/java"),
            Path("src/test/java"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/lib/junit-4.11.jar"),
            Path("/d4j/framework/projects/Math/lib/commons-discovery-0.5.jar")
        )

        override val entrypoints = listOf(
            "org.apache.commons.math3.fraction.BigFractionTest::testDigitLimitConstructor",
            "org.apache.commons.math3.fraction.FractionTest::testDigitLimitConstructor",
        )
    }

    object Math17f : Project() {

        override val projectRev = ProjectRev(ProjectID.MATH, 17, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/main/java"),
            Path("src/test/java"),
        )
        override val compileCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/Math/lib/commons-discovery-0.5.jar"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/lib/junit-4.11.jar"),
            Path("/d4j/framework/projects/Math/lib/commons-discovery-0.5.jar"),
        )

        override val entrypoints = listOf(
            "org.apache.commons.math3.dfp.DfpTest::testMultiply",
        )
    }

    object Math29f : Project() {

        override val projectRev = ProjectRev(ProjectID.MATH, 29, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/main/java"),
            Path("src/test/java"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/lib/junit-4.11.jar"),
            Path("/d4j/framework/projects/Math/lib/commons-discovery-0.5.jar")
        )

        override val entrypoints = listOf(
            "org.apache.commons.math3.linear.SparseRealVectorTest::testEbeDivideMixedTypes",
            "org.apache.commons.math3.linear.SparseRealVectorTest::testEbeMultiplyMixedTypes",
            "org.apache.commons.math3.linear.SparseRealVectorTest::testEbeMultiplySameType",
        )
    }

    object Math32f : Project() {

        override val projectRev = ProjectRev(ProjectID.MATH, 32, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/main/java"),
            Path("src/test/java"),
        )
        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/lib/junit-4.11.jar"),
            Path("/d4j/framework/projects/Math/lib/commons-discovery-0.5.jar"),
        )

        override val entrypoints = listOf(
            "org.apache.commons.math3.geometry.euclidean.threed.PolyhedronsSetTest::testIssue780",
        )
    }

    object Mockito1f : Project() {

        override val projectRev = ProjectRev(ProjectID.MOCKITO, 1, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src"),
            Path("test"),
        )

        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/Mockito/lib/asm-all-5.0.4.jar"),
            Path("/d4j/framework/projects/Mockito/lib/assertj-core-2.1.0.jar"),
            Path("/d4j/framework/projects/Mockito/lib/cglib-and-asm-1.0.jar"),
            Path("/d4j/framework/projects/Mockito/lib/cobertura-2.0.3.jar"),
            Path("/d4j/framework/projects/Mockito/lib/fest-assert-1.3.jar"),
            Path("/d4j/framework/projects/Mockito/lib/fest-util-1.1.4.jar"),
            Path("/d4j/framework/projects/Mockito/lib/hamcrest-all-1.3.jar"),
            Path("/d4j/framework/projects/Mockito/lib/hamcrest-core-1.1.jar"),
            Path("/d4j/framework/projects/Mockito/lib/objenesis-2.1.jar"),
            Path("/d4j/framework/projects/Mockito/lib/objenesis-2.2.jar"),
            Path("/d4j/framework/projects/Mockito/lib/powermock-reflect-1.2.5.jar"),
            Path("/d4j/framework/lib/build_systems/gradle/deps/net/bytebuddy/byte-buddy/0.2.1/byte-buddy-0.2.1.jar"),
        )

        override val entrypoints = listOf(
            "org.mockito.internal.invocation.InvocationMatcherTest::should_capture_arguments_when_args_count_does_NOT_match",
            "org.mockito.internal.util.reflection.FieldInitializerTest::can_instantiate_class_with_parameterized_constructor",
            "org.mockito.internal.util.reflection.ParameterizedConstructorInstantiatorTest::should_report_failure_if_constructor_throws_exception",
            "org.mockito.internal.util.reflection.ParameterizedConstructorInstantiatorTest::should_fail_if_an_argument_instance_type_do_not_match_wanted_type",
            "org.mockito.internal.util.reflection.ParameterizedConstructorInstantiatorTest::should_instantiate_type_with_vararg_constructor",
            "org.mockito.internal.util.reflection.ParameterizedConstructorInstantiatorTest::should_instantiate_type_if_resolver_provide_matching_types",
            "org.mockitousage.basicapi.ResetTest::shouldRemoveAllStubbing",
            "org.mockitousage.basicapi.UsingVarargsTest::shouldVerifyWithNullVarArgArray",
            "org.mockitousage.basicapi.UsingVarargsTest::shouldVerifyWithAnyObject",
            "org.mockitousage.basicapi.UsingVarargsTest::shouldStubBooleanVarargs",
            "org.mockitousage.basicapi.UsingVarargsTest::shouldMatchEasilyEmptyVararg",
            "org.mockitousage.basicapi.UsingVarargsTest::shouldVerifyBooleanVarargs",
            "org.mockitousage.basicapi.UsingVarargsTest::shouldStubCorrectlyWhenMixedVarargsUsed",
            "org.mockitousage.basicapi.UsingVarargsTest::shouldStubStringVarargs",
            "org.mockitousage.basicapi.UsingVarargsTest::shouldStubCorrectlyWhenDoubleStringAndMixedVarargsUsed",
            "org.mockitousage.basicapi.UsingVarargsTest::shouldVerifyStringVarargs",
            "org.mockitousage.basicapi.UsingVarargsTest::shouldVerifyObjectVarargs",
            "org.mockitousage.bugs.VarargsErrorWhenCallingRealMethodTest::shouldNotThrowAnyException",
            "org.mockitousage.bugs.varargs.VarargsAndAnyObjectPicksUpExtraInvocationsTest::shouldVerifyCorrectlyWithAnyVarargs",
            "org.mockitousage.bugs.varargs.VarargsAndAnyObjectPicksUpExtraInvocationsTest::shouldVerifyCorrectlyNumberOfInvocationsUsingAnyVarargAndEqualArgument",
            "org.mockitousage.bugs.varargs.VarargsNotPlayingWithAnyObjectTest::shouldStubUsingAnyVarargs",
            "org.mockitousage.matchers.VerificationAndStubbingUsingMatchersTest::shouldVerifyUsingMatchers",
            "org.mockitousage.stubbing.BasicStubbingTest::test_stub_only_not_verifiable",
            "org.mockitousage.stubbing.BasicStubbingTest::should_evaluate_latest_stubbing_first",
            "org.mockitousage.stubbing.DeprecatedStubbingTest::shouldEvaluateLatestStubbingFirst",
            "org.mockitousage.verification.VerificationInOrderMixedWithOrdiraryVerificationTest::shouldUseEqualsToVerifyMethodVarargs",
        )
    }

    object Mockito12f : Project() {

        override val projectRev = ProjectRev(ProjectID.MOCKITO, 12, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src"),
            Path("test"),
        )
        override val compileCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/Mockito/lib/asm-all-5.0.4.jar"),
            Path("/d4j/framework/projects/Mockito/lib/assertj-core-2.1.0.jar"),
            Path("/d4j/framework/projects/Mockito/lib/cglib-and-asm-1.0.jar"),
            Path("/d4j/framework/projects/Mockito/lib/cobertura-2.0.3.jar"),
            Path("/d4j/framework/projects/Mockito/lib/fest-assert-1.3.jar"),
            Path("/d4j/framework/projects/Mockito/lib/fest-util-1.1.4.jar"),
//            Path("/d4j/framework/projects/Mockito/lib/hamcrest-all-1.3.jar"),
            Path("/d4j/framework/projects/Mockito/lib/hamcrest-core-1.1.jar"),
            Path("/d4j/framework/projects/Mockito/lib/objenesis-2.1.jar"),
            Path("/d4j/framework/projects/Mockito/lib/objenesis-2.2.jar"),
            Path("/d4j/framework/projects/Mockito/lib/powermock-reflect-1.2.5.jar"),
        )
        override val testCpTypeSolverPaths = listOf(
//            Path("/d4j/framework/projects/lib/junit-4.11.jar"),
            Path("/d4j/framework/projects/Mockito/lib/asm-all-5.0.4.jar"),
            Path("/d4j/framework/projects/Mockito/lib/assertj-core-2.1.0.jar"),
            Path("/d4j/framework/projects/Mockito/lib/cglib-and-asm-1.0.jar"),
            Path("/d4j/framework/projects/Mockito/lib/cobertura-2.0.3.jar"),
            Path("/d4j/framework/projects/Mockito/lib/fest-assert-1.3.jar"),
            Path("/d4j/framework/projects/Mockito/lib/fest-util-1.1.4.jar"),
//            Path("/d4j/framework/projects/Mockito/lib/hamcrest-all-1.3.jar"),
            Path("/d4j/framework/projects/Mockito/lib/hamcrest-core-1.1.jar"),
            Path("/d4j/framework/projects/Mockito/lib/objenesis-2.1.jar"),
            Path("/d4j/framework/projects/Mockito/lib/objenesis-2.2.jar"),
            Path("/d4j/framework/projects/Mockito/lib/powermock-reflect-1.2.5.jar"),
            // Reordered from testCpTypeSolverPaths[0] - Use hamcrest-core first due to abstract method issue
            Path("/d4j/framework/projects/lib/junit-4.11.jar"),
        )

        override val entrypoints = listOf(
            "org.mockito.internal.util.reflection.GenericMasterTest::shouldDealWithNestedGenerics",
            "org.mockitousage.annotation.CaptorAnnotationBasicTest::shouldUseAnnotatedCaptor",
            "org.mockitousage.annotation.CaptorAnnotationBasicTest::shouldUseCaptorInOrdinaryWay",
            "org.mockitousage.annotation.CaptorAnnotationBasicTest::shouldCaptureGenericList",
            "org.mockitousage.annotation.CaptorAnnotationBasicTest::shouldUseGenericlessAnnotatedCaptor",
            "org.mockitousage.annotation.CaptorAnnotationTest::shouldScreamWhenWrongTypeForCaptor",
            "org.mockitousage.annotation.CaptorAnnotationTest::testNormalUsage",
            "org.mockitousage.annotation.CaptorAnnotationTest::shouldScreamWhenMoreThanOneMockitoAnnotaton",
            "org.mockitousage.annotation.CaptorAnnotationTest::shouldScreamWhenInitializingCaptorsForNullClass",
            "org.mockitousage.annotation.CaptorAnnotationTest::shouldLookForAnnotatedCaptorsInSuperClasses",
        )
    }

    object Mockito17f : Project() {

        override val projectRev = ProjectRev(ProjectID.MOCKITO, 17, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src"),
            Path("test"),
        )
        override val compileCpTypeSolverPaths = listOf(
//            Path("target/classes"),
            Path("/d4j/framework/projects/Mockito/lib/asm-all-5.0.4.jar"),
            Path("/d4j/framework/projects/Mockito/lib/assertj-core-2.1.0.jar"),
            Path("/d4j/framework/projects/Mockito/lib/cglib-and-asm-1.0.jar"),
            Path("/d4j/framework/projects/Mockito/lib/cobertura-2.0.3.jar"),
            Path("/d4j/framework/projects/Mockito/lib/fest-assert-1.3.jar"),
            Path("/d4j/framework/projects/Mockito/lib/fest-util-1.1.4.jar"),
//            Path("/d4j/framework/projects/Mockito/lib/hamcrest-all-1.3.jar"),
            Path("/d4j/framework/projects/Mockito/lib/hamcrest-core-1.1.jar"),
            Path("/d4j/framework/projects/Mockito/lib/objenesis-2.1.jar"),
            Path("/d4j/framework/projects/Mockito/lib/objenesis-2.2.jar"),
            Path("/d4j/framework/projects/Mockito/lib/powermock-reflect-1.2.5.jar"),
        )
        override val testCpTypeSolverPaths = listOf(
//            Path("/d4j/framework/projects/lib/junit-4.11.jar"),
//            Path("target/classes"),
//            Path("target/test-classes"),
            Path("/d4j/framework/projects/Mockito/lib/asm-all-5.0.4.jar"),
            Path("/d4j/framework/projects/Mockito/lib/assertj-core-2.1.0.jar"),
            Path("/d4j/framework/projects/Mockito/lib/cglib-and-asm-1.0.jar"),
            Path("/d4j/framework/projects/Mockito/lib/cobertura-2.0.3.jar"),
            Path("/d4j/framework/projects/Mockito/lib/fest-assert-1.3.jar"),
            Path("/d4j/framework/projects/Mockito/lib/fest-util-1.1.4.jar"),
//            Path("/d4j/framework/projects/Mockito/lib/hamcrest-all-1.3.jar"),
            Path("/d4j/framework/projects/Mockito/lib/hamcrest-core-1.1.jar"),
            Path("/d4j/framework/projects/Mockito/lib/objenesis-2.1.jar"),
            Path("/d4j/framework/projects/Mockito/lib/objenesis-2.2.jar"),
            Path("/d4j/framework/projects/Mockito/lib/powermock-reflect-1.2.5.jar"),
            // Reordered from testCpTypeSolverPaths[0] - Use hamcrest-core first due to abstract method issue
            Path("/d4j/framework/projects/lib/junit-4.11.jar"),
        )

        override val entrypoints = listOf(
            "org.mockitousage.basicapi.MocksSerializationTest::shouldBeSerializeAndHaveExtraInterfaces",
        )
    }

    object Mockito20f : Project() {

        override val projectRev = ProjectRev(ProjectID.MOCKITO, 20, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src"),
            Path("test"),
        )

        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/lib/junit-4.11.jar"),
            Path("/d4j/framework/projects/Mockito/lib/asm-all-5.0.4.jar"),
            Path("/d4j/framework/projects/Mockito/lib/assertj-core-2.1.0.jar"),
            Path("/d4j/framework/projects/Mockito/lib/cglib-and-asm-1.0.jar"),
            Path("/d4j/framework/projects/Mockito/lib/cobertura-2.0.3.jar"),
            Path("/d4j/framework/projects/Mockito/lib/fest-assert-1.3.jar"),
            Path("/d4j/framework/projects/Mockito/lib/fest-util-1.1.4.jar"),
            Path("/d4j/framework/projects/Mockito/lib/hamcrest-all-1.3.jar"),
            Path("/d4j/framework/projects/Mockito/lib/hamcrest-core-1.1.jar"),
            Path("/d4j/framework/projects/Mockito/lib/objenesis-2.1.jar"),
            Path("/d4j/framework/projects/Mockito/lib/objenesis-2.2.jar"),
            Path("/d4j/framework/projects/Mockito/lib/powermock-reflect-1.2.5.jar"),
            Path("/d4j/framework/lib/build_systems/gradle/deps/net/bytebuddy/byte-buddy/0.2.1/byte-buddy-0.2.1.jar"),
        )

        override val entrypoints = listOf(
            "org.mockitousage.annotation.SpyAnnotationTest::should_spy_inner_class",
            "org.mockitousage.annotation.SpyAnnotationTest::should_report_when_constructor_is_explosive",
            "org.mockitousage.constructor.CreatingMocksWithConstructorTest::can_spy_abstract_classes",
            "org.mockitousage.constructor.CreatingMocksWithConstructorTest::exception_message_when_constructor_not_found",
            "org.mockitousage.constructor.CreatingMocksWithConstructorTest::can_create_mock_with_constructor",
            "org.mockitousage.constructor.CreatingMocksWithConstructorTest::can_mock_inner_classes",
            "org.mockitousage.constructor.CreatingMocksWithConstructorTest::mocking_inner_classes_with_wrong_outer_instance",
            "org.mockitousage.constructor.CreatingMocksWithConstructorTest::can_mock_abstract_classes",
        )
    }

    object Mockito25f : Project() {

        override val projectRev = ProjectRev(ProjectID.MOCKITO, 25, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src"),
            Path("test"),
        )
        override val compileCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/Mockito/lib/asm-all-5.0.4.jar"),
            Path("/d4j/framework/projects/Mockito/lib/assertj-core-2.1.0.jar"),
            Path("/d4j/framework/projects/Mockito/lib/cglib-and-asm-1.0.jar"),
            Path("/d4j/framework/projects/Mockito/lib/cobertura-2.0.3.jar"),
            Path("/d4j/framework/projects/Mockito/lib/fest-assert-1.3.jar"),
            Path("/d4j/framework/projects/Mockito/lib/fest-util-1.1.4.jar"),
//            Path("/d4j/framework/projects/Mockito/lib/hamcrest-all-1.3.jar"),
            Path("/d4j/framework/projects/Mockito/lib/hamcrest-core-1.1.jar"),
            Path("/d4j/framework/projects/Mockito/lib/objenesis-2.1.jar"),
            Path("/d4j/framework/projects/Mockito/lib/objenesis-2.2.jar"),
            Path("/d4j/framework/projects/Mockito/lib/powermock-reflect-1.2.5.jar"),
        )
        override val testCpTypeSolverPaths = listOf(
//            Path("/d4j/framework/projects/lib/junit-4.11.jar"),
            Path("/d4j/framework/projects/Mockito/lib/asm-all-5.0.4.jar"),
            Path("/d4j/framework/projects/Mockito/lib/assertj-core-2.1.0.jar"),
            Path("/d4j/framework/projects/Mockito/lib/cglib-and-asm-1.0.jar"),
            Path("/d4j/framework/projects/Mockito/lib/cobertura-2.0.3.jar"),
            Path("/d4j/framework/projects/Mockito/lib/fest-assert-1.3.jar"),
            Path("/d4j/framework/projects/Mockito/lib/fest-util-1.1.4.jar"),
//            Path("/d4j/framework/projects/Mockito/lib/hamcrest-all-1.3.jar"),
            Path("/d4j/framework/projects/Mockito/lib/hamcrest-core-1.1.jar"),
            Path("/d4j/framework/projects/Mockito/lib/objenesis-2.1.jar"),
            Path("/d4j/framework/projects/Mockito/lib/objenesis-2.2.jar"),
            Path("/d4j/framework/projects/Mockito/lib/powermock-reflect-1.2.5.jar"),

            // Reordered from testCpTypeSolverPaths[0] - Use hamcrest-core first due to abstract method issue
            Path("/d4j/framework/projects/lib/junit-4.11.jar"),
        )

        override val entrypoints = listOf(
            "org.mockito.internal.stubbing.defaultanswers.ReturnsGenericDeepStubsTest::will_return_default_value_on_non_mockable_nested_generic",
            "org.mockito.internal.stubbing.defaultanswers.ReturnsGenericDeepStubsTest::can_create_mock_from_multiple_type_variable_bounds_when_return_type_of_parameterized_method_is_a_typevar_that_is_referencing_a_typevar_on_class",
            "org.mockito.internal.stubbing.defaultanswers.ReturnsGenericDeepStubsTest::can_create_mock_from_return_types_declared_with_a_bounded_wildcard",
            "org.mockito.internal.stubbing.defaultanswers.ReturnsGenericDeepStubsTest::can_create_mock_from_multiple_type_variable_bounds_when_return_type_of_parameterized_method_is_a_parameterizedtype_that_is_referencing_a_typevar_on_class",
            "org.mockito.internal.stubbing.defaultanswers.ReturnsGenericDeepStubsTest::generic_deep_mock_frenzy__look_at_these_chained_calls",
            "org.mockito.internal.stubbing.defaultanswers.ReturnsGenericDeepStubsTest::can_create_mock_from_multiple_type_variable_bounds_when_method_return_type_is_referencing_a_typevar_on_class",
        )
    }

    object Time27f : Project() {

        override val projectRev = ProjectRev(ProjectID.TIME, 27, BugVersion.FIXED)

        override val srcTypeSolverPaths = listOf(
            Path("src/main/java"),
            Path("src/test/java"),
        )

        override val testCpTypeSolverPaths = listOf(
            Path("/d4j/framework/projects/lib/junit-4.11.jar"),
        )

        override val entrypoints = listOf(
            "org.joda.time.format.TestPeriodFormatterBuilder::testBug2495455",
        )
    }
}