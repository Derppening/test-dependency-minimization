package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.tool.compilation.CompilerProxy
import com.derppening.researchprojecttoolkit.tool.reachability.ReachableReason
import com.derppening.researchprojecttoolkit.util.synchronizedWith
import org.junit.jupiter.api.Nested
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.jvm.optionals.getOrNull
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StaticAnalysisMemberReducerCompress47fTest : StaticAnalysisMemberReducerIntegTest() {

    override val project = TestProjects.Compress47f

    @Nested
    inner class ZipArchiveInputStreamTest {

        @Nested
        inner class NameSourceDefaultsToName : OnTestCase("org.apache.commons.compress.archivers.zip.ZipArchiveInputStreamTest::nameSourceDefaultsToName", true) {

            @Test
            fun testExecutionJava8() {
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
                    listOf("-Xmaxerrs", "$JAVAC_MAXERRS"),
                    JAVA_8_HOME
                )

                assertCompileSuccess(compiler)

                val resDir = Path("src/test/resources")
                project.copyProjectFile(resDir.resolve("bla.zip"), outputDir!!.path)

                // Compilation error due to use of reflection
                //
                // => java.lang.AssertionError: This method should not be reached! Signature: AsiExtraField()
                //   org.apache.commons.compress.archivers.zip.AsiExtraField.<init>(AsiExtraField.java:94)
                //   sun.reflect.NativeConstructorAccessorImpl.newInstance0(Native Method)
                //   sun.reflect.NativeConstructorAccessorImpl.newInstance(NativeConstructorAccessorImpl.java:62)
                //   sun.reflect.DelegatingConstructorAccessorImpl.newInstance(DelegatingConstructorAccessorImpl.java:45)
                //   java.lang.reflect.Constructor.newInstance(Constructor.java:423)
                //   java.lang.Class.newInstance(Class.java:442)
                //   org.apache.commons.compress.archivers.zip.ExtraFieldUtils.register(ExtraFieldUtils.java:67)
                //   org.apache.commons.compress.archivers.zip.ExtraFieldUtils.<clinit>(ExtraFieldUtils.java:42)
                //   org.apache.commons.compress.archivers.zip.ZipArchiveEntry.setExtra(ZipArchiveEntry.java:547)
                //   org.apache.commons.compress.archivers.zip.ZipArchiveInputStream.getNextZipEntry(ZipArchiveInputStream.java:306)
                //   [...]
                val junitRunner = tryTestNoAssertion(outputDir!!.path, JAVA_8_HOME) {
                    add("-cp")
                    add("${project.testCpJars.joinToString(":")}:${sourceRoots.joinToString(":")}:${outputDir!!.path.resolve(resDir)}")

                    add("-m")
                    add(testCase.toJUnitMethod())
                }
                assertFalse(junitRunner.exitCode.isSuccess)
            }

            /**
             * ```
             * /tmp/3329244613600614276/src/main/java/org/apache/commons/compress/compressors/deflate64/HuffmanDecoder.java:169: error: cannot find symbol
             *                             state = new HuffmanCodes(FIXED_CODES, FIXED_LITERALS, FIXED_DISTANCE);
             *                                                      ^
             *   symbol:   variable FIXED_CODES
             *   location: class HuffmanDecoder
             * /tmp/3329244613600614276/src/main/java/org/apache/commons/compress/compressors/deflate64/HuffmanDecoder.java:177: error: cannot find symbol
             *                             state = new HuffmanCodes(DYNAMIC_CODES, literalTable, distanceTable);
             *                                                      ^
             *   symbol:   variable DYNAMIC_CODES
             *   location: class HuffmanDecoder
             * /tmp/3329244613600614276/src/main/java/org/apache/commons/compress/compressors/deflate64/HuffmanDecoder.java:217: error: cannot find symbol
             *             return read < blockLength ? STORED : INITIAL;
             *                                         ^
             *   symbol:   variable STORED
             *   location: class HuffmanDecoder.UncompressedState
             * /tmp/3329244613600614276/src/main/java/org/apache/commons/compress/compressors/deflate64/HuffmanDecoder.java:217: error: cannot find symbol
             *             return read < blockLength ? STORED : INITIAL;
             *                                                  ^
             *   symbol:   variable INITIAL
             *   location: class HuffmanDecoder.UncompressedState
             * /tmp/3329244613600614276/src/main/java/org/apache/commons/compress/compressors/deflate64/HuffmanDecoder.java:257: error: cannot find symbol
             *             return INITIAL;
             *                    ^
             *   symbol:   variable INITIAL
             *   location: class HuffmanDecoder.InitialState
             * /tmp/3329244613600614276/src/main/java/org/apache/commons/compress/compressors/deflate64/HuffmanDecoder.java:300: error: cannot find symbol
             *             return endOfBlock ? INITIAL : state;
             *                                 ^
             *   symbol:   variable INITIAL
             *   location: class HuffmanDecoder.HuffmanCodes
             * ```
             */
            @Test
            fun `Regression-00`() {
                val huffmanStateCU = reducer.context.getCUByPrimaryTypeName("org.apache.commons.compress.compressors.deflate64.HuffmanState")
                assumeTrue(huffmanStateCU != null)

                val huffmanState = huffmanStateCU.primaryType.get()
                assertTrue(huffmanState.inclusionReasonsData.isNotEmpty())

                val huffmanStateDirectInclusionReasons = huffmanState.inclusionReasonsData.synchronizedWith {
                    filterIsInstance<ReachableReason.DirectlyReferencedByNode>()
                }
                assertTrue(huffmanStateDirectInclusionReasons.isNotEmpty())
                assertTrue {
                    huffmanStateDirectInclusionReasons.any {
                        it.node
                            .findCompilationUnit()
                            .flatMap { it.primaryType }
                            .flatMap { it.fullyQualifiedName }
                            .getOrNull() == "org.apache.commons.compress.compressors.deflate64.HuffmanDecoder"
                    }
                }

                val huffmanDecoderCU = reducer.context.getCUByPrimaryTypeName("org.apache.commons.compress.compressors.deflate64.HuffmanDecoder")
                assumeTrue(huffmanDecoderCU != null)

                val importDecl = huffmanDecoderCU.imports
                    .single { it.nameAsString == "org.apache.commons.compress.compressors.deflate64.HuffmanState" && it.isStatic && it.isAsterisk }
                assertTrue(importDecl.inclusionReasonsData.isNotEmpty())
                assertFalse(importDecl.isUnusedForRemovalData)
            }
        }
    }
}