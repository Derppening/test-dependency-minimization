package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.tool.compilation.CompilerProxy
import com.derppening.researchprojecttoolkit.tool.reachability.ReachableReason
import com.derppening.researchprojecttoolkit.util.synchronizedWith
import com.github.javaparser.ast.body.MethodDeclaration
import org.junit.jupiter.api.Nested
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StaticAnalysisMemberReducerCompress9fTest : StaticAnalysisMemberReducerIntegTest() {

    override val project = TestProjects.Compress9f

    @Nested
    inner class TarArchiveOutputStreamTest {

        @Nested
        inner class TestCount : OnTestCase("org.apache.commons.compress.archivers.tar.TarArchiveOutputStreamTest::testCount", true) {

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

                // Copy `src/test/resources/test1.xml` into directory
                project.copyProjectFile(Path("src/test/resources/test1.xml"), outputDir!!.path)

                assertTestSuccess(outputDir!!.path, JAVA_17_HOME) {
                    add("-cp")
                    add("${project.testCpJars.joinToString(":")}:${sourceRoots.joinToString(":")}")

                    add("-m")
                    add(testCase.toJUnitMethod())
                }
            }

            /**
             * ```
             * /tmp/4898786360402218847/src/main/java/org/apache/commons/compress/compressors/pack200/Pack200CompressorOutputStream.java:25: error: cannot find symbol
             * import java.util.jar.Pack200;
             *                     ^
             *   symbol:   class Pack200
             *   location: package java.util.jar
             * ```
             */
            @Test
            fun `Regression-00`() {
                val cu = reducer.context.getCUByPrimaryTypeName("org.apache.commons.compress.compressors.pack200.Pack200CompressorOutputStream")
                assumeTrue(cu != null)

                val type = cu.primaryType.get()
                assertTrue(type.inclusionReasonsData.isNotEmpty())
                assertTrue{
                    type.inclusionReasonsData.synchronizedWith {
                        filterIsInstance<ReachableReason.DirectlyReferenced>().isEmpty()
                    }
                }
                assertTrue(type.instanceCreationData.isEmpty())
                assertTrue(type.isUnusedForRemovalData)

                val import = cu.imports
                    .single { it.nameAsString == "java.util.jar.Pack200" && !it.isStatic && !it.isAsterisk }
                assertTrue(import.isUnusedForRemovalData)
            }

            /**
             * ```
             * /tmp/7714677112525922572/src/main/java/org/apache/commons/compress/archivers/dump/DumpArchiveInputStream.java:109: error: <anonymous org.apache.commons.compress.archivers.dump.DumpArchiveInputStream$1> is not abstract and does not override abstract method compare(DumpArchiveEntry,DumpArchiveEntry) in Comparator
             *         queue = new PriorityQueue<DumpArchiveEntry>(10, new Comparator<DumpArchiveEntry>() {
             *                                                                                            ^
             * ```
             *
             * See [`SymbolSolverUtilsTest.getOverriddenMethodInType Test - Method in Generic Anonymous Class`][com.derppening.researchprojecttoolkit.util.SymbolSolverUtilsTest].
             */
            @Test
            fun `Regression-01`() {
                val cu = reducer.context.getCUByPrimaryTypeName("org.apache.commons.compress.archivers.dump.DumpArchiveInputStream")
                assumeTrue(cu != null)

                val method = cu.primaryType.get()
                    .getConstructorByParameterTypes("InputStream").get()
                    .body
                    .statements[5].asExpressionStmt()
                    .expression.asAssignExpr()
                    .value.asObjectCreationExpr()
                    .arguments[1].asObjectCreationExpr()
                    .anonymousClassBody.get()
                    .single {
                        it is MethodDeclaration &&
                                it.nameAsString == "compare" &&
                                it.parameters.size == 2 &&
                                it.parameters[0].typeAsString == "DumpArchiveEntry" &&
                                it.parameters[1].typeAsString == "DumpArchiveEntry"
                    }
                assertFalse(method.isUnusedForRemovalData)
            }
        }
    }
}