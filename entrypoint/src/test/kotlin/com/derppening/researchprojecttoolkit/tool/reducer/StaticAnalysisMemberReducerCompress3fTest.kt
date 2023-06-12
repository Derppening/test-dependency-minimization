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

class StaticAnalysisMemberReducerCompress3fTest : StaticAnalysisMemberReducerIntegTest() {

    override val project = TestProjects.Compress3f

    @Nested
    inner class ArchiveOutputStreamTest {

        @Nested
        inner class TestFinish : OnTestCase("org.apache.commons.compress.archivers.ArchiveOutputStreamTest::testFinish", true) {

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

                val junitRunner = tryTestNoAssertion(outputDir!!.path, JAVA_17_HOME) {
                    add("-cp")
                    add("${project.testCpJars.joinToString(":")}:${sourceRoots.joinToString(":")}")

                    add("-m")
                    add(testCase.toJUnitMethod())
                }
                assertFalse(junitRunner.exitCode.isSuccess)
            }

            /**
             * ```
             * junit.framework.AssertionFailedError: Exception in constructor: testFinish (java.lang.AssertionError: This method should not be reached! Signature: AbstractTestCase()
             *   at org.apache.commons.compress.AbstractTestCase.<init>(AbstractTestCase.java:55)
             *   at org.apache.commons.compress.archivers.ArchiveOutputStreamTest.<init>(ArchiveOutputStreamTest.java:13)
             * ```
             */
            @Test
            fun `Regression-00`() {
                // org.apache.commons.compress.archivers.ArchiveOutputStreamTest.<init>()
                run {
                    val cu = reducer.context.loadedCompilationUnits
                        .single {
                            it.primaryType.flatMap { it.fullyQualifiedName }.orElse(null) == "org.apache.commons.compress.archivers.ArchiveOutputStreamTest"
                        }

                    val type = cu.primaryType.get()

                    assertTrue(type.inclusionReasonsData.isNotEmpty())
                    assertTrue {
                        type.inclusionReasonsData.synchronizedWith {
                            filterIsInstance<ReachableReason.EntrypointClass>().isNotEmpty()
                        }
                    }
                }

                // org.apache.commons.compress.AbstractTestCase.<init>()
                run {
                    val cu = reducer.context.loadedCompilationUnits
                        .single {
                            it.primaryType.flatMap { it.fullyQualifiedName }.orElse(null) == "org.apache.commons.compress.AbstractTestCase"
                        }

                    val ctor = cu.primaryType.get()
                        .defaultConstructor.get()

                    assertTrue(ctor.inclusionReasonsData.isNotEmpty())
                    assertTrue {
                        ctor.inclusionReasonsData.synchronizedWith {
                            filterIsInstance<ReachableReason.TransitiveCtorForSubclass>().isNotEmpty()
                        }
                    }
                    assertFalse(ctor.isUnusedForRemovalData)
                    assertFalse(ctor.isUnusedForDummyData)
                }
            }
        }
    }
}