package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.tool.compilation.CompilerProxy
import com.derppening.researchprojecttoolkit.tool.reachability.ReachableReason
import com.derppening.researchprojecttoolkit.util.synchronizedWith
import com.github.javaparser.ast.body.InitializerDeclaration
import org.junit.jupiter.api.Nested
import java.nio.file.Path
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StaticAnalysisMemberReducerCompress4fTest : StaticAnalysisMemberReducerIntegTest() {

    override val project = TestProjects.Compress4f

    @Nested
    inner class JarArchiveOutputStreamTest {

        @Nested
        inner class TestJarMarker : OnTestCase("org.apache.commons.compress.archivers.jar.JarArchiveOutputStreamTest::testJarMarker", true) {

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
             * java.lang.AssertionError: This method should not be reached! Signature: getHeaderId()
             *   at org.apache.commons.compress.archivers.zip.AsiExtraField.getHeaderId(AsiExtraField.java:95)
             *   at org.apache.commons.compress.archivers.zip.ExtraFieldUtils.register(ExtraFieldUtils.java:58)
             *   at org.apache.commons.compress.archivers.zip.ExtraFieldUtils.<clinit>(ExtraFieldUtils.java:42)
             *   at org.apache.commons.compress.archivers.zip.ZipArchiveEntry.setExtra(ZipArchiveEntry.java:249)
             *   at org.apache.commons.compress.archivers.zip.ZipArchiveEntry.addAsFirstExtraField(ZipArchiveEntry.java:212)
             *   at org.apache.commons.compress.archivers.jar.JarArchiveOutputStream.putArchiveEntry(JarArchiveOutputStream.java:46)
             *   at org.apache.commons.compress.archivers.jar.JarArchiveOutputStreamTest.testJarMarker(JarArchiveOutputStreamTest.java:38)
             * ```
             */
            @Test
            @Ignore("Expected to fail because method uses reflection (and bypasses static analysis)")
            fun `Regression-00`() {
                // org.apache.commons.compress.archivers.jar.JarArchiveOutputStreamTest.testJarMarker()
                run {
                    val cu = reducer.context.loadedCompilationUnits
                        .single {
                            it.primaryType.flatMap { it.fullyQualifiedName }.orElse(null) == "org.apache.commons.compress.archivers.jar.JarArchiveOutputStreamTest"
                        }

                    val method = cu.primaryType.get()
                        .getMethodsBySignature("testJarMarker").single()

                    assertTrue(method.inclusionReasonsData.isNotEmpty())
                    assertTrue {
                        method.inclusionReasonsData.synchronizedWith {
                            filterIsInstance<ReachableReason.Entrypoint>().isNotEmpty()
                        }
                    }
                    assertFalse(method.isUnusedForRemovalData)
                    assertFalse(method.isUnusedForDummyData)
                }

                // org.apache.commons.compress.archivers.jar.JarArchiveOutputStream.putArchiveEntry(ArchiveEntry)
                run {
                    val cu = reducer.context.loadedCompilationUnits
                        .single {
                            it.primaryType.flatMap { it.fullyQualifiedName }.orElse(null) == "org.apache.commons.compress.archivers.jar.JarArchiveOutputStream"
                        }

                    val method = cu.primaryType.get()
                        .getMethodsBySignature("putArchiveEntry", "ArchiveEntry").single()

                    assertTrue(method.inclusionReasonsData.isNotEmpty())
                    assertTrue {
                        method.inclusionReasonsData.synchronizedWith {
                            filterIsInstance<ReachableReason.ReferencedBySymbolName>().isNotEmpty()
                        }
                    }
                    assertFalse(method.isUnusedForRemovalData)
                    assertFalse(method.isUnusedForDummyData)
                }

                // org.apache.commons.compress.archivers.zip.ZipArchiveEntry.addAsFirstExtraField(ZipExtraField)
                run {
                    val cu = reducer.context.loadedCompilationUnits
                        .single {
                            it.primaryType.flatMap { it.fullyQualifiedName }.orElse(null) == "org.apache.commons.compress.archivers.zip.ZipArchiveEntry"
                        }

                    val method = cu.primaryType.get()
                        .getMethodsBySignature("addAsFirstExtraField", "ZipExtraField").single()

                    assertTrue(method.inclusionReasonsData.isNotEmpty())
                    assertTrue {
                        method.inclusionReasonsData.synchronizedWith {
                            filterIsInstance<ReachableReason.ReferencedBySymbolName>().isNotEmpty()
                        }
                    }
                    assertFalse(method.isUnusedForRemovalData)
                    assertFalse(method.isUnusedForDummyData)
                }

                // org.apache.commons.compress.archivers.zip.ZipArchiveEntry.setExtra()
                run {
                    val cu = reducer.context.loadedCompilationUnits
                        .single {
                            it.primaryType.flatMap { it.fullyQualifiedName }.orElse(null) == "org.apache.commons.compress.archivers.zip.ZipArchiveEntry"
                        }

                    val method = cu.primaryType.get()
                        .getMethodsBySignature("setExtra").single()

                    assertTrue(method.inclusionReasonsData.isNotEmpty())
                    assertTrue {
                        method.inclusionReasonsData.synchronizedWith {
                            filterIsInstance<ReachableReason.ReferencedBySymbolName>().isNotEmpty()
                        }
                    }
                    assertFalse(method.isUnusedForRemovalData)
                    assertFalse(method.isUnusedForDummyData)
                }

                // org.apache.commons.compress.archivers.zip.ExtraFieldUtils.<clinit>()
                run {
                    val cu = reducer.context.loadedCompilationUnits
                        .single {
                            it.primaryType.flatMap { it.fullyQualifiedName }.orElse(null) == "org.apache.commons.compress.archivers.zip.ExtraFieldUtils"
                        }

                    val initBlock = cu.primaryType.get()
                        .members
                        .filterIsInstance<InitializerDeclaration>()
                        .first()

                    assertFalse(initBlock.isUnusedForRemovalData)
                    assertFalse(initBlock.isUnusedForDummyData)
                }

                // org.apache.commons.compress.archivers.zip.ZipArchiveEntry.register(Class)
                run {
                    val cu = reducer.context.loadedCompilationUnits
                        .single {
                            it.primaryType.flatMap { it.fullyQualifiedName }.orElse(null) == "org.apache.commons.compress.archivers.zip.ExtraFieldUtils"
                        }

                    val method = cu.primaryType.get()
                        .getMethodsBySignature("register", "Class").single()

                    assertTrue(method.inclusionReasonsData.isNotEmpty())
                    assertTrue {
                        method.inclusionReasonsData.synchronizedWith {
                            filterIsInstance<ReachableReason.ReferencedBySymbolName>().isNotEmpty()
                        }
                    }
                    assertFalse(method.isUnusedForRemovalData)
                    assertFalse(method.isUnusedForDummyData)
                }

                // org.apache.commons.compress.archivers.zip.ZipArchiveEntry.register(Class)
                run {
                    val cu = reducer.context.loadedCompilationUnits
                        .single {
                            it.primaryType.flatMap { it.fullyQualifiedName }.orElse(null) == "org.apache.commons.compress.archivers.zip.AsiExtraField"
                        }

                    val method = cu.primaryType.get()
                        .getMethodsBySignature("getHeaderId").single()

                    assertTrue(method.inclusionReasonsData.isNotEmpty())
                    assertTrue {
                        method.inclusionReasonsData.synchronizedWith {
                            filterIsInstance<ReachableReason.TransitiveMethodCallTarget>().isNotEmpty()
                        }
                    }
                }
            }
        }
    }
}