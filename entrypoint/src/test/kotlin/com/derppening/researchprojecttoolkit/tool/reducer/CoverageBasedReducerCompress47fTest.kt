package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.model.ExecutableDeclaration
import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.tool.compilation.CompilerProxy
import com.derppening.researchprojecttoolkit.tool.reducer.AbstractBaselineReducer.Companion.findMethodFromJacocoCoverage
import com.derppening.researchprojecttoolkit.util.findAll
import com.github.javaparser.ast.Node
import org.junit.jupiter.api.Nested
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertNotNull

class CoverageBasedReducerCompress47fTest : CoverageBasedReducerIntegTest() {

    override val project = TestProjects.Compress47f

    @Nested
    inner class ZipArchiveInputStreamTest {

        @Nested
        inner class NameSourceDefaultsToName : OnTestCase("org.apache.commons.compress.archivers.zip.ZipArchiveInputStreamTest::nameSourceDefaultsToName", true) {

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

                val resDir = Path("src/test/resources")
                project.copyProjectFile(resDir.resolve("bla.zip"), outputDir!!.path)

                assertTestSuccess(outputDir!!.path, JAVA_17_HOME) {
                    add("-cp")
                    add("${project.testCpJars.joinToString(":")}:${sourceRoots.joinToString(":")}:${outputDir!!.path.resolve(resDir)}")

                    add("-m")
                    add(testCase.toJUnitMethod())
                }
            }

            @Nested
            inner class SymbolLookupTests {

                @Test
                fun `Solve for User-Defined Enum Constructor 1`() {
                    val coverageData = project.getBaselineDir(entrypointSpec)
                        .readJacocoBaselineCoverage()
                    assumeTrue(coverageData != null)

                    val packageCov = coverageData.report
                        .packages.single { it.name == "org/apache/commons/compress/archivers/zip" }
                    val classCov = packageCov
                        .classes.single { it.name == "org/apache/commons/compress/archivers/zip/ZipMethod" }
                    val methodCov = classCov.methods
                        .single { it.name == "<init>" && it.desc == "(Ljava/lang/String;II)V" }

                    val cu = reducer.context.getCUByPrimaryTypeName("org.apache.commons.compress.archivers.zip.ZipMethod")
                    assumeTrue(cu != null)

                    val matchedMethod = findMethodFromJacocoCoverage(
                        cu.findAll<Node> { ExecutableDeclaration.createOrNull(it) != null }
                            .map { ExecutableDeclaration.create(it) },
                        classCov,
                        methodCov,
                        reducer.context
                    )
                    assertNotNull(matchedMethod)
                }

                @Test
                fun `Solve for User-Defined Enum Constructor 2`() {
                    val coverageData = project.getBaselineDir(entrypointSpec)
                        .readJacocoBaselineCoverage()
                    assumeTrue(coverageData != null)

                    val packageCov = coverageData.report
                        .packages.single { it.name == "org/apache/commons/compress/archivers/zip" }
                    val classCov = packageCov
                        .classes.single { it.name == "org/apache/commons/compress/archivers/zip/ZipMethod" }
                    val methodCov = classCov.methods
                        .single { it.name == "<init>" && it.desc == "(Ljava/lang/String;II)V" }

                    val cu = reducer.context.getCUByPrimaryTypeName("org.apache.commons.compress.archivers.zip.ZipMethod")
                    assumeTrue(cu != null)

                    val matchedMethod = findMethodFromJacocoCoverage(
                        cu.findAll<Node> { ExecutableDeclaration.createOrNull(it) != null }
                            .map { ExecutableDeclaration.create(it) },
                        classCov,
                        methodCov,
                        reducer.context
                    )
                    assertNotNull(matchedMethod)
                }
            }
        }
    }
}