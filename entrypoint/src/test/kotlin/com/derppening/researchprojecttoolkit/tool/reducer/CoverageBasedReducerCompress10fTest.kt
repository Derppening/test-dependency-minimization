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
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull

class CoverageBasedReducerCompress10fTest : CoverageBasedReducerIntegTest() {

    override val project = TestProjects.Compress10f

    @Nested
    inner class UTF8ZipFilesTest {

        @Nested
        inner class TestReadWinZipArchive : OnTestCase("org.apache.commons.compress.archivers.zip.UTF8ZipFilesTest::testReadWinZipArchive", true) {

            @Test
            @Ignore("Execution fails due to lack of method-level coverage data and not-implemented ClassLevel baseline reduction")
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
                project.copyProjectFile(resDir.resolve("utf8-winzip-test.zip"), outputDir!!.path)

                val junitRunner = tryTestNoAssertion(outputDir!!.path, JAVA_17_HOME) {
                    add("-cp")
                    add("${project.testCpJars.joinToString(":")}:${sourceRoots.joinToString(":")}:${outputDir!!.path.resolve(resDir)}")

                    add("-m")
                    add(testCase.toJUnitMethod())
                }
                assertFalse(junitRunner.exitCode.isSuccess)
            }
        }
    }

    @Nested
    inner class ZipArchiveEntryTest {

        @Nested
        inner class IsUnixSymlinkIsFalseIfMoreThanOneFlagIsSet : OnTestCase("org.apache.commons.compress.archivers.zip.UTF8ZipFilesTest::testReadWinZipArchive", true) {

            @Nested
            inner class SymbolLookupTests {

                @Test
                fun `Find Anonymous Class When Resolving Constructor`() {
                    val coverageData = project.getBaselineDir(entrypointSpec)
                        .readJacocoBaselineCoverage()
                    assumeTrue(coverageData != null)

                    val packageCov = coverageData.report
                        .packages.single { it.name == "org/apache/commons/compress/archivers/zip" }
                    val classCov = packageCov
                        .classes.single { it.name == "org/apache/commons/compress/archivers/zip/ZipFile\$2" }
                    val methodCov = classCov.methods
                        .single { it.name == "<init>" && it.desc == "(Lorg/apache/commons/compress/archivers/zip/ZipFile;)V" }

                    val cu = reducer.context.getCUByPrimaryTypeName("org.apache.commons.compress.archivers.zip.ZipFile")
                    assumeTrue(cu != null)

                    val matchedMethod = findMethodFromJacocoCoverage(
                        cu.findAll<Node> { ExecutableDeclaration.createOrNull(it) != null }
                            .map { ExecutableDeclaration.create(it) },
                        classCov,
                        methodCov,
                        reducer.context
                    )
                    assertNull(matchedMethod)
                }
            }
        }
    }
}