package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.model.ExecutableDeclaration
import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.tool.compilation.CompilerProxy
import com.derppening.researchprojecttoolkit.tool.reducer.AbstractBaselineReducer.Companion.findMethodFromJacocoCoverage
import com.derppening.researchprojecttoolkit.util.findAll
import com.github.javaparser.ast.Node
import com.github.javaparser.ast.type.PrimitiveType
import org.junit.jupiter.api.Nested
import java.nio.file.Path
import kotlin.test.*

class CoverageBasedReducerCodec3fTest : CoverageBasedReducerIntegTest() {

    override val project = TestProjects.Codec3f

    @Nested
    inner class DoubleMetaphone2Test {

        @Nested
        inner class TestDoubleMetaphoneAlternate : OnTestCase("org.apache.commons.codec.language.DoubleMetaphone2Test::testDoubleMetaphoneAlternate", true) {

            @Test
            fun testExecution() {
                val sourceRootMapping = mutableMapOf<Path, Path>()
                reducer.getTransformedCompilationUnits(outputDir!!.path, sourceRootMapping = sourceRootMapping)
                    .parallelStream()
                    .forEach { cu -> cu.storage.ifPresent { it.save() } }

                val sourceRoots = sourceRootMapping.values
                val compiler = CompilerProxy(
                    sourceRoots,
                    project.testCpJars.joinToString(":"),
                    emptyList()
                )
                assertCompileSuccess(compiler)

                assertTestSuccess {
                    add("-cp")
                    add("${project.testCpJars.joinToString(":")}:${sourceRoots.joinToString(":")}")

                    add("-m")
                    add(testCase.toJUnitMethod())
                }
            }

            @Nested
            inner class SymbolLookupTests {

                @Test
                fun `Normalize Anonymous Class Delimiter`() {
                    val coverageData = project.getBaselineDir(entrypointSpec)
                        .readJacocoBaselineCoverage()
                    assumeTrue(coverageData != null)

                    val packageCov = coverageData.report
                        .packages.single { it.name == "org/apache/commons/codec/language" }
                    val classCov = packageCov
                        .classes.single { it.name == "org/apache/commons/codec/language/DoubleMetaphone" }
                    val methodCov = classCov.methods
                        .single { it.name == "handleAEIOUY" && it.desc == "(Ljava/lang/String;Lorg/apache/commons/codec/language/DoubleMetaphone\$DoubleMetaphoneResult;I)I" }

                    val cu = reducer.context.getCUByPrimaryTypeName("org.apache.commons.codec.language.DoubleMetaphone")
                    assumeTrue(cu != null)

                    val matchedMethod = findMethodFromJacocoCoverage(
                        cu.findAll<Node> { ExecutableDeclaration.createOrNull(it) != null }
                            .map { ExecutableDeclaration.create(it) },
                        classCov,
                        methodCov,
                        reducer.context
                    )

                    assertNotNull(matchedMethod)
                    assertIs<ExecutableDeclaration.MethodDecl>(matchedMethod)
                    assertEquals("handleAEIOUY", matchedMethod.name)
                    assertEquals(3, matchedMethod.node.parameters.size)
                    val returnType = assertIs<PrimitiveType>(matchedMethod.node.type)
                    assertEquals(PrimitiveType.intType(), returnType)
                }
            }

            @Nested
            inner class ReductionRegressionTests {
                /**
                 * ```
                 * compile.tests:
                 *  [javac] /workspace/src/test/org/apache/commons/codec/binary/Base64Test.java:78: error: unmappable character (0xE9) for encoding UTF-8
                 *  [javac]         byte[] decode = b64.decode("SGVsbG{������}8gV29ybGQ=");
                 *  [javac]                                            ^
                 *  [javac] /workspace/src/test/org/apache/commons/codec/binary/Base64Test.java:78: error: unmappable character (0xE9) for encoding UTF-8
                 *  [javac]         byte[] decode = b64.decode("SGVsbG{������}8gV29ybGQ=");
                 *  [javac]                                             ^
                 *  [javac] /workspace/src/test/org/apache/commons/codec/binary/Base64Test.java:78: error: unmappable character (0xE9) for encoding UTF-8
                 *  [javac]         byte[] decode = b64.decode("SGVsbG{������}8gV29ybGQ=");
                 *  [javac]                                              ^
                 *  [javac] /workspace/src/test/org/apache/commons/codec/binary/Base64Test.java:78: error: unmappable character (0xE9) for encoding UTF-8
                 *  [javac]         byte[] decode = b64.decode("SGVsbG{������}8gV29ybGQ=");
                 *  [javac]                                               ^
                 *  [javac] /workspace/src/test/org/apache/commons/codec/binary/Base64Test.java:78: error: unmappable character (0xE9) for encoding UTF-8
                 *  [javac]         byte[] decode = b64.decode("SGVsbG{������}8gV29ybGQ=");
                 *  [javac]                                                ^
                 *  [javac] /workspace/src/test/org/apache/commons/codec/binary/Base64Test.java:78: error: unmappable character (0xE9) for encoding UTF-8
                 *  [javac]         byte[] decode = b64.decode("SGVsbG{������}8gV29ybGQ=");
                 *  [javac]                                                 ^
                 *  [javac] 6 errors
                 * ```
                 */
                @Test
                @Ignore("Obsolete")
                fun `Regression-00`() {
                    val cu = reducer.context
                        .getCUByPrimaryTypeName("org.apache.commons.codec.binary.Base64Test")
                    assumeTrue(cu != null)

                    val type = cu.primaryType.get()
                    assertTrue(type.inclusionReasonsData.isEmpty())

                    val method = type
                        .getMethodsBySignature("testBase64").single()
                    assertTrue(method.inclusionReasonsData.isEmpty())
                    assertTrue(method.isUnusedForDummyData)
                }

                /**
                 * ```
                 * compile:
                 *  [javac] /tmp/16733472430756346902/build.xml:56: warning: 'includeantruntime' was not set, defaulting to build.sysclasspath=last; set to false for repeatable builds
                 *  [javac] Compiling 29 source files to /tmp/16733472430756346902/target/classes
                 *  [javac] /tmp/16733472430756346902/src/java/org/apache/commons/codec/BinaryDecoder.java:39: error: cannot access DecoderException
                 *  [javac]     byte[] decode(byte[] pArray) throws DecoderException;
                 *  [javac]                                         ^
                 *  [javac]   bad source file: /tmp/16733472430756346902/src/java/org/apache/commons/codec/DecoderException.java
                 *  [javac]     file does not contain class org.apache.commons.codec.DecoderException
                 *  [javac]     Please remove or make sure it appears in the correct subdirectory of the sourcepath.
                 *  [javac] /tmp/16733472430756346902/src/java/org/apache/commons/codec/BinaryEncoder.java:39: error: cannot access EncoderException
                 *  [javac]     byte[] encode(byte[] pArray) throws EncoderException;
                 *  [javac]                                         ^
                 *  [javac]   bad source file: /tmp/16733472430756346902/src/java/org/apache/commons/codec/EncoderException.java
                 *  [javac]     file does not contain class org.apache.commons.codec.EncoderException
                 *  [javac]     Please remove or make sure it appears in the correct subdirectory of the sourcepath.
                 *  [javac] /tmp/16733472430756346902/src/java/org/apache/commons/codec/language/DoubleMetaphone.java:194: error: cannot find symbol
                 *  [javac]     public Object encode(Object obj) throws EncoderException {
                 *  [javac]                                             ^
                 *  [javac]   symbol:   class EncoderException
                 *  [javac]   location: class DoubleMetaphone
                 * ```
                 */
                @Test
                @Ignore("Obsolete")
                fun `Regression-01`() {
                    val cu = reducer.context
                        .getCUByPrimaryTypeName("org.apache.commons.codec.BinaryDecoder")
                    assumeTrue(cu != null)

                    val method = cu.primaryType.get()
                        .getMethodsBySignature("decode", "byte[]").single()

                    assertFalse(method.inclusionReasonsData.isNotEmpty())
                    assertTrue(method.isUnusedForDummyData)
                }

                @Test
                fun `Regression-02`() {
                    val cu = reducer.context
                        .getCUByPrimaryTypeName("org.apache.commons.codec.language.DoubleMetaphone2Test")
                    assumeTrue(cu != null)

                    val method = cu.primaryType.get()
                        .getConstructorByParameterTypes("String").get()

                    assertTrue(method.inclusionReasonsData.isNotEmpty())
                    assertFalse(method.isUnusedForDummyData)
                    assertFalse(method.isUnusedForRemovalData)
                }

                /**
                 * ```
                 * /tmp/3785019603641206196/srcRoot0/org/apache/commons/codec/net/URLCodec.java:23: error: cannot find symbol
                 * import org.apache.commons.codec.CharEncoding;
                 *                                ^
                 *   symbol:   class CharEncoding
                 *   location: package org.apache.commons.codec
                 * ```
                 */
                @Test
                fun `Regression-03`() {
                    val charEncodingCU = reducer.context
                        .getCUByPrimaryTypeName("org.apache.commons.codec.CharEncoding")
                    assumeTrue(charEncodingCU != null)

                    val charEncodingType = charEncodingCU.primaryType.get()

                    assertTrue(charEncodingType.inclusionReasonsData.isEmpty())

                    val urlCodecCU = reducer.context
                        .getCUByPrimaryTypeName("org.apache.commons.codec.net.URLCodec")
                    assumeTrue(urlCodecCU != null)

                    val import = urlCodecCU.imports
                        .single {
                            it.nameAsString == "org.apache.commons.codec.CharEncoding"
                        }
                    assertTrue(import.isUnusedForRemovalData)
                }
            }
        }
    }
}