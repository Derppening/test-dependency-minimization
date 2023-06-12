package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.tool.compilation.CompilerProxy
import org.junit.jupiter.api.Nested
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class StaticAnalysisMemberReducerCodec1fTest : StaticAnalysisMemberReducerIntegTest() {

    override val project = TestProjects.Codec1f

    @Nested
    inner class CaverphoneTest {

        @Nested
        inner class TestLocaleIndependence : OnTestCase("org.apache.commons.codec.language.CaverphoneTest::testLocaleIndependence", true) {

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
             * compile.tests:
             *   [javac] /tmp/1899636792516502244/src/test/org/apache/commons/codec/net/RFC1522CodecTest.java:31: error: constructor Object in class Object cannot be applied to given types;
             *   [javac]         super(name);
             *   [javac]         ^
             *   [javac]   required: no arguments
             *   [javac]   found:    String
             *   [javac]   reason: actual and formal argument lists differ in length
             * ```
             */
            @Test
            fun `Regression-00`() {
                run {
                    val cu = reducer.context.loadedCompilationUnits
                        .single {
                            it.primaryType.flatMap { it.fullyQualifiedName }.orElse(null) == "org.apache.commons.codec.net.RFC1522CodecTest"
                        }

                    val type = cu.primaryType.get()
                    assumeTrue(type.isUnusedForSupertypeRemovalData, "Type needs to be marked as unused-for-supertype-removal for test to proceed")

                    val ctor = type.getConstructorByParameterTypes("String").get()
                    assertTrue(ctor.isUnusedForRemovalData)
                }
            }
        }
    }
}