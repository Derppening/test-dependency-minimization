package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.tool.compilation.CompilerProxy
import com.derppening.researchprojecttoolkit.tool.transform.NodeTransformDecision
import com.derppening.researchprojecttoolkit.tool.transform.TagGroundTruthUnusedDecls
import org.junit.jupiter.api.Nested
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertNotEquals

class CoverageBasedReducerCompress11fTest : CoverageBasedReducerIntegTest() {

    override val project = TestProjects.Compress11f

    @Nested
    inner class ArchiveStreamFactoryTest {

        @Nested
        inner class ShortTextFilesAreNoTARs {

            private val entrypoint = "org.apache.commons.compress.archivers.ArchiveStreamFactoryTest::shortTextFilesAreNoTARs"

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

                @Nested
                inner class ReductionRegressionTests {

                    /**
                     * ```
                     * [javac] src/main/java/org/apache/commons/compress/archivers/jar/JarArchiveInputStream.java:32: error: constructor ZipArchiveInputStream in class ZipArchiveInputStream cannot be applied to given types;
                     * [javac]         super(inputStream);
                     * [javac]         ^
                     * [javac]   required: InputStream,String,boolean,boolean
                     * [javac]   found:    InputStream
                     * [javac]   reason: actual and formal argument lists differ in length
                     * ```
                     */
                    @Test
                    fun `Regression-00`() {
                        val cu = reducer.context
                            .getCUByPrimaryTypeName("org.apache.commons.compress.archivers.jar.JarArchiveInputStream")
                        assumeTrue(cu != null)

                        val ctor = cu.primaryType.get()
                            .getConstructorByParameterTypes("InputStream").get()

                        val ctorDecision = TagGroundTruthUnusedDecls.decideForConstructor(
                            reducer.context,
                            ctor,
                            enableAssertions = enableAssertions,
                            noCache = true
                        )

                        val superclassCU = reducer.context
                            .getCUByPrimaryTypeName("org.apache.commons.compress.archivers.zip.ZipArchiveInputStream")
                        assumeTrue(superclassCU != null)

                        val superclassCtor = superclassCU.primaryType.get()
                            .getConstructorByParameterTypes("InputStream").get()

                        val superclassCtorDecision = TagGroundTruthUnusedDecls.decideForConstructor(
                            reducer.context,
                            superclassCtor,
                            enableAssertions = enableAssertions,
                            noCache = true
                        )

                        if (ctorDecision != NodeTransformDecision.REMOVE) {
                            assertNotEquals(NodeTransformDecision.REMOVE, superclassCtorDecision)
                        }
                    }
                }
            }
        }
    }
}