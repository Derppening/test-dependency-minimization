package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.tool.compilation.CompilerProxy
import com.derppening.researchprojecttoolkit.tool.reachability.ReachableReason
import com.derppening.researchprojecttoolkit.util.synchronizedWith
import org.junit.jupiter.api.Nested
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StaticAnalysisMemberReducerCodec10fTest : StaticAnalysisMemberReducerIntegTest() {

    override val project = TestProjects.Codec10f

    @Nested
    inner class CaverphoneTest {

        @Nested
        inner class TestEndMb : OnTestCase("org.apache.commons.codec.language.CaverphoneTest::testEndMb", true) {

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
             * junit.framework.AssertionFailedError: Exception in constructor: testEndMb (java.lang.AssertionError: This method should not be reached! Signature: createStringEncoder()
             *   at org.apache.commons.codec.language.CaverphoneTest.createStringEncoder(CaverphoneTest.java:35)
             *   at org.apache.commons.codec.StringEncoderAbstractTest.<init>(StringEncoderAbstractTest.java:29)
             *   at org.apache.commons.codec.language.CaverphoneTest.<init>(CaverphoneTest.java:31)
             * ```
             */
            @Test
            fun `Regression-00`() {
                // org.apache.commons.codec.language.CaverphoneTest.<init>(String)
                run {
                    val cu = reducer.context.loadedCompilationUnits
                        .single {
                            it.primaryType.flatMap { it.fullyQualifiedName }.orElse(null) == "org.apache.commons.codec.language.CaverphoneTest"
                        }

                    val ctor = cu.primaryType.get()
                        .getConstructorByParameterTypes("String").get()

                    assertTrue(ctor.inclusionReasonsData.isNotEmpty())
                    assertTrue {
                        ctor.inclusionReasonsData.synchronizedWith {
                            filterIsInstance<ReachableReason.Entrypoint>().isNotEmpty()
                        }
                    }
                    assertFalse(ctor.isUnusedForRemovalData)
                    assertFalse(ctor.isUnusedForDummyData)
                }

                // org.apache.commons.codec.StringEncoderAbstractTest.<init>(String)
                run {
                    val cu = reducer.context.loadedCompilationUnits
                        .single {
                            it.primaryType.flatMap { it.fullyQualifiedName }.orElse(null) == "org.apache.commons.codec.StringEncoderAbstractTest"
                        }

                    val ctor = cu.primaryType.get()
                        .getConstructorByParameterTypes("String").get()

                    assertTrue(ctor.inclusionReasonsData.isNotEmpty())
                    assertTrue {
                        ctor.inclusionReasonsData.synchronizedWith {
                            filterIsInstance<ReachableReason.ReferencedByCtorCallByExplicitStmt>().isNotEmpty()
                        }
                    }
                    assertFalse(ctor.isUnusedForRemovalData)
                    assertFalse(ctor.isUnusedForDummyData)
                }

                // org.apache.commons.codec.StringEncoderAbstractTest.stringEncoder
                run {
                    val cu = reducer.context.loadedCompilationUnits
                        .single {
                            it.primaryType.flatMap { it.fullyQualifiedName }.orElse(null) == "org.apache.commons.codec.StringEncoderAbstractTest"
                        }

                    val field = cu.primaryType.get()
                        .getFieldByName("stringEncoder")
                        .map { fieldDecl -> fieldDecl.variables.single { it.nameAsString == "stringEncoder" } }
                        .get()

                    assertTrue(field.inclusionReasonsData.isNotEmpty())
                    assertTrue {
                        field.inclusionReasonsData.synchronizedWith {
                            filterIsInstance<ReachableReason.ReferencedBySymbolName>().isNotEmpty()
                        }
                    }
                    assertFalse(field.isUnusedForRemovalData)
                }

                // org.apache.commons.codec.StringEncoderAbstractTest.createStringEncoder()
                run {
                    val cu = reducer.context.loadedCompilationUnits
                        .single {
                            it.primaryType.flatMap { it.fullyQualifiedName }.orElse(null) == "org.apache.commons.codec.StringEncoderAbstractTest"
                        }

                    val method = cu.primaryType.get()
                        .getMethodsBySignature("createStringEncoder").single()

                    assertTrue(method.inclusionReasonsData.isNotEmpty())
                    assertTrue {
                        method.inclusionReasonsData.synchronizedWith {
                            filterIsInstance<ReachableReason.ReferencedBySymbolName>().isNotEmpty()
                        }
                    }
                    assertFalse(method.isUnusedForRemovalData)
                    assertFalse(method.isUnusedForDummyData)
                }

                // org.apache.commons.codec.CaverphoneTest.createStringEncoder()
                run {
                    val cu = reducer.context.loadedCompilationUnits
                        .single {
                            it.primaryType.flatMap { it.fullyQualifiedName }.orElse(null) == "org.apache.commons.codec.language.CaverphoneTest"
                        }

                    val method = cu.primaryType.get()
                        .getMethodsBySignature("createStringEncoder").single()

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

        @Nested
        inner class TestEncodeNull : OnTestCase("org.apache.commons.codec.language.CaverphoneTest::testEncodeNull", true) {

            /**
             * ```
             * junit.framework.AssertionFailedError: Exception in constructor: testEncodeNull (java.lang.AssertionError: This method should not be reached! Signature: createStringEncoder()
             *   at org.apache.commons.codec.language.CaverphoneTest.createStringEncoder(CaverphoneTest.java:35)
             *   at org.apache.commons.codec.StringEncoderAbstractTest.<init>(StringEncoderAbstractTest.java:29)
             *   at org.apache.commons.codec.language.CaverphoneTest.<init>(CaverphoneTest.java:31)
             * ```
             */
            @Test
            fun `Regression-00`() {
                // org.apache.commons.codec.language.CaverphoneTest.<init>(String)
                run {
                    val cu = reducer.context.loadedCompilationUnits
                        .single {
                            it.primaryType.flatMap { it.fullyQualifiedName }.orElse(null) == "org.apache.commons.codec.language.CaverphoneTest"
                        }

                    val ctor = cu.primaryType.get()
                        .getConstructorByParameterTypes("String").get()

                    assertTrue(ctor.inclusionReasonsData.isNotEmpty())
                    assertTrue {
                        ctor.inclusionReasonsData.synchronizedWith {
                            filterIsInstance<ReachableReason.Entrypoint>().isNotEmpty()
                        }
                    }
                    assertFalse(ctor.isUnusedForRemovalData)
                    assertFalse(ctor.isUnusedForDummyData)
                }

                // org.apache.commons.codec.StringEncoderAbstractTest.<init>(String)
                run {
                    val cu = reducer.context.loadedCompilationUnits
                        .single {
                            it.primaryType.flatMap { it.fullyQualifiedName }.orElse(null) == "org.apache.commons.codec.StringEncoderAbstractTest"
                        }

                    val ctor = cu.primaryType.get()
                        .getConstructorByParameterTypes("String").get()

                    assertTrue(ctor.inclusionReasonsData.isNotEmpty())
                    assertTrue {
                        ctor.inclusionReasonsData.synchronizedWith {
                            filterIsInstance<ReachableReason.ReferencedByCtorCallByExplicitStmt>().isNotEmpty()
                        }
                    }
                    assertFalse(ctor.isUnusedForRemovalData)
                    assertFalse(ctor.isUnusedForDummyData)
                }

                // org.apache.commons.codec.StringEncoderAbstractTest.stringEncoder
                run {
                    val cu = reducer.context.loadedCompilationUnits
                        .single {
                            it.primaryType.flatMap { it.fullyQualifiedName }.orElse(null) == "org.apache.commons.codec.StringEncoderAbstractTest"
                        }

                    val field = cu.primaryType.get()
                        .getFieldByName("stringEncoder")
                        .map { fieldDecl -> fieldDecl.variables.single { it.nameAsString == "stringEncoder" } }
                        .get()

                    assertTrue(field.inclusionReasonsData.isNotEmpty())
                    assertTrue {
                        field.inclusionReasonsData.synchronizedWith {
                            filterIsInstance<ReachableReason.ReferencedBySymbolName>().isNotEmpty()
                        }
                    }
                    assertFalse(field.isUnusedForRemovalData)
                }

                // org.apache.commons.codec.StringEncoderAbstractTest.createStringEncoder()
                run {
                    val cu = reducer.context.loadedCompilationUnits
                        .single {
                            it.primaryType.flatMap { it.fullyQualifiedName }.orElse(null) == "org.apache.commons.codec.StringEncoderAbstractTest"
                        }

                    val method = cu.primaryType.get()
                        .getMethodsBySignature("createStringEncoder").single()

                    assertTrue(method.inclusionReasonsData.isNotEmpty())
                    assertTrue {
                        method.inclusionReasonsData.synchronizedWith {
                            filterIsInstance<ReachableReason.ReferencedBySymbolName>().isNotEmpty()
                        }
                    }
                    assertFalse(method.isUnusedForRemovalData)
                    assertFalse(method.isUnusedForDummyData)
                }

                // org.apache.commons.codec.CaverphoneTest.createStringEncoder()
                run {
                    val cu = reducer.context.loadedCompilationUnits
                        .single {
                            it.primaryType.flatMap { it.fullyQualifiedName }.orElse(null) == "org.apache.commons.codec.language.CaverphoneTest"
                        }

                    val method = cu.primaryType.get()
                        .getMethodsBySignature("createStringEncoder").single()

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
}