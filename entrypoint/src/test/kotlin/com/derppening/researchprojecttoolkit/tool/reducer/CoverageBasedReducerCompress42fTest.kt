package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.tool.compilation.CompilerProxy
import com.derppening.researchprojecttoolkit.tool.reachability.ReachableReason
import com.derppening.researchprojecttoolkit.util.synchronizedWith
import com.github.javaparser.ast.body.InitializerDeclaration
import com.github.javaparser.ast.body.TypeDeclaration
import org.junit.jupiter.api.Nested
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.test.*

class CoverageBasedReducerCompress42fTest : CoverageBasedReducerIntegTest() {

    override val project = TestProjects.Compress42f

    @Nested
    inner class ZipArchiveEntryTest {

        @Nested
        inner class IsUnixSymlinkIsFalseIfMoreThanOneFlagIsSet : OnTestCase("org.apache.commons.compress.archivers.zip.ZipArchiveEntryTest::isUnixSymlinkIsFalseIfMoreThanOneFlagIsSet", true) {

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
                project.copyProjectFile(resDir.resolve("COMPRESS-379.jar"), outputDir!!.path)

                assertTestSuccess(outputDir!!.path, JAVA_17_HOME) {
                    add("-cp")
                    add("${project.testCpJars.joinToString(":")}:${sourceRoots.joinToString(":")}:${outputDir!!.path.resolve(resDir)}")

                    add("-m")
                    add(testCase.toJUnitMethod())
                }
            }

            @Nested
            inner class ReductionRegressionTests {

                /**
                 * ```
                 * /tmp/8203942365146478030/src/main/java/org/apache/commons/compress/compressors/snappy/SnappyCompressorOutputStream.java:58: error: package ByteUtils does not exist
                 *     private final ByteUtils.ByteConsumer consumer;
                 *                            ^
                 * /tmp/8203942365146478030/src/main/java/org/apache/commons/compress/compressors/snappy/SnappyCompressorInputStream.java:91: error: package ByteUtils does not exist
                 *     private final ByteUtils.ByteSupplier supplier = new ByteUtils.ByteSupplier() {
                 *                            ^
                 * /tmp/8203942365146478030/src/main/java/org/apache/commons/compress/compressors/snappy/SnappyCompressorInputStream.java:91: error: package ByteUtils does not exist
                 *     private final ByteUtils.ByteSupplier supplier = new ByteUtils.ByteSupplier() {
                 *                            ^
                 * ```
                 */
                @Test
                @Ignore("Obsolete")
                fun `Regression-00`() {
                    val cu = reducer.context
                        .getCUByPrimaryTypeName("org.apache.commons.compress.compressors.snappy.SnappyCompressorOutputStream")
                    assumeTrue(cu != null)

                    val byteUtilsImportDecl = cu.imports
                        .single { it.nameAsString == "org.apache.commons.compress.utils.ByteUtils" && !it.isStatic && !it.isAsterisk }
                    assertTrue(byteUtilsImportDecl.inclusionReasonsData.isNotEmpty())
                    assertFalse(byteUtilsImportDecl.isUnusedForRemovalData)
                }

                /**
                 * ```
                 * /tmp/7662280439641301448/src/main/java/org/apache/commons/compress/archivers/zip/PKWareExtraHeader.java:169: error: cannot find symbol
                 *                 cte.put(Integer.valueOf(method.getCode()), method);
                 *                                               ^
                 *   symbol:   method getCode()
                 *   location: variable method of type EncryptionAlgorithm
                 * ```
                 */
                @Test
                fun `Regression-02`() {
                    run {
                        val cu = reducer.context
                            .getCUByPrimaryTypeName("org.apache.commons.compress.archivers.zip.PKWareExtraHeader")
                        assumeTrue(cu != null)

                        val initDecl = cu.primaryType.get()
                            .members
                            .single { it is TypeDeclaration<*> && it.nameAsString == "EncryptionAlgorithm" }
                            .asTypeDeclaration()
                            .members
                            .filterIsInstance<InitializerDeclaration>()
                            .single()

                        assertTrue(initDecl.isUnusedForDummyData)
                    }

                    val cu =
                        reducer.context.getCUByPrimaryTypeName("org.apache.commons.compress.archivers.zip.ZipMethod")
                    assumeTrue(cu != null)

                    val method = cu.primaryType.get()
                        .getMethodsBySignature("getCode").single()

                    assertTrue(method.inclusionReasonsData.isEmpty())
                    assertTrue(method.isUnusedForRemovalData)
                }

                /**
                 * ```
                 * /tmp/5235987051461083261/src/main/java/org/apache/commons/compress/compressors/lzma/LZMAUtils.java:23: error: cannot find symbol
                 * import org.apache.commons.compress.compressors.FileNameUtil;
                 *                                               ^
                 *   symbol:   class FileNameUtil
                 *   location: package org.apache.commons.compress.compressors
                 * ```
                 */
                @Test
                fun `Regression-03`() {
                    val cu = reducer.context
                        .getCUByPrimaryTypeName("org.apache.commons.compress.compressors.lzma.LZMAUtils")
                    assumeTrue(cu != null)

                    val importDecl = cu.imports
                        .single { it.nameAsString == "org.apache.commons.compress.compressors.FileNameUtil" && !it.isStatic && !it.isAsterisk }
                    assertTrue(importDecl.inclusionReasonsData.isNotEmpty())

                    val directInclusionReasons = importDecl.inclusionReasonsData.synchronizedWith {
                        filterIsInstance<ReachableReason.DirectlyReferencedByNode>()
                    }
                    assertTrue { directInclusionReasons.all { it.node.isUnusedForDummyData || it.node.isUnusedForRemovalData } }

                    assertTrue(importDecl.isUnusedForRemovalData)
                }

                /**
                 * ```
                 * /tmp/6983254916651865503/src/main/java/org/apache/commons/compress/archivers/zip/ZipArchiveInputStream.java:22: error: cannot find symbol
                 * import org.apache.commons.compress.archivers.ArchiveInputStream;
                 *                                             ^
                 *   symbol:   class ArchiveInputStream
                 *   location: package org.apache.commons.compress.archivers
                 * ```
                 */
                @Test
                fun `Regression-04`() {
                    val cu = reducer.context
                        .getCUByPrimaryTypeName("org.apache.commons.compress.archivers.zip.ZipArchiveInputStream")
                    assumeTrue(cu != null)

                    val importDecl = cu.imports
                        .single { it.nameAsString == "org.apache.commons.compress.archivers.ArchiveInputStream" && !it.isStatic && !it.isAsterisk }
                    assertTrue(importDecl.inclusionReasonsData.isNotEmpty())

                    val directInclusionReasons = importDecl.inclusionReasonsData.synchronizedWith {
                        filterIsInstance<ReachableReason.DirectlyReferencedByNode>()
                    }
                    assertTrue { directInclusionReasons.all { it.node.isUnusedForDummyData || it.node.isUnusedForRemovalData } }

                    assertTrue(importDecl.isUnusedForRemovalData)
                }
            }
        }
    }
}