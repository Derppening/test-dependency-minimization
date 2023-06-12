package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.model.ExecutableDeclaration
import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.tool.compilation.CompilerProxy
import com.derppening.researchprojecttoolkit.tool.reducer.AbstractBaselineReducer.Companion.findMethodFromJacocoCoverage
import com.derppening.researchprojecttoolkit.tool.transform.NodeTransformDecision
import com.derppening.researchprojecttoolkit.tool.transform.TagGroundTruthUnusedDecls.Companion.decideForMethod
import com.derppening.researchprojecttoolkit.util.findAll
import com.derppening.researchprojecttoolkit.util.getFieldVariableDecl
import com.github.javaparser.ast.Node
import com.github.javaparser.ast.body.ConstructorDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import org.junit.jupiter.api.Nested
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.test.*

class CoverageBasedReducerCompress31fTest : CoverageBasedReducerIntegTest() {

    override val project = TestProjects.Compress31f

    @Nested
    inner class TarUtilsTest {

        @Nested
        inner class TestParseOctalInvalid : OnTestCase("org.apache.commons.compress.archivers.tar.TarUtilsTest::testParseOctalInvalid", true) {

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
                project.copyProjectFile(resDir.resolve("utf8-winzip-test.zip"), outputDir!!.path)

                assertTestSuccess(outputDir!!.path, JAVA_17_HOME) {
                    add("-cp")
                    add("${project.testCpJars.joinToString(":")}:${sourceRoots.joinToString(":")}:${outputDir!!.path.resolve(resDir)}")

                    add("-m")
                    add(testCase.toJUnitMethod())
                }
            }

            @Nested
            inner class SymbolLookupTests {

                @Test
                fun `No Constructor in Enum Constant`() {
                    val coverageData = project.getBaselineDir(entrypointSpec).readJacocoBaselineCoverage()
                    assumeTrue(coverageData != null)

                    val cu = reducer.context
                        .getCUByPrimaryTypeName("org.apache.commons.compress.archivers.sevenz.CLI")
                    assumeTrue(cu != null)

                    run {
                        val packageCov = coverageData.report
                            .packages.single { it.name == "org/apache/commons/compress/archivers/sevenz" }
                        val classCov = packageCov
                            .classes.single { it.name == "org/apache/commons/compress/archivers/sevenz/CLI\$Mode" }
                        val methodCov = classCov.methods
                            .single { it.name == "<init>" && it.desc == "(Ljava/lang/String;ILjava/lang/String;)V" }

                        val matchedMethod = findMethodFromJacocoCoverage(
                            cu.findAll<Node> { ExecutableDeclaration.createOrNull(it) != null }
                                .map { ExecutableDeclaration.create(it) },
                            classCov,
                            methodCov,
                            reducer.context
                        )
                        assertNotNull(matchedMethod)
                        val methodDecl = assertIs<ConstructorDeclaration>(matchedMethod.node)
                        assertEquals(1, methodDecl.parameters.size)
                    }

                    run {
                        val packageCov = coverageData.report
                            .packages.single { it.name == "org/apache/commons/compress/archivers/sevenz" }
                        val classCov = packageCov
                            .classes.single { it.name == "org/apache/commons/compress/archivers/sevenz/CLI\$Mode\$1" }
                        val methodCov = classCov.methods
                            .single { it.name == "<init>" && it.desc == "(Ljava/lang/String;ILjava/lang/String;)V" }

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

    @Nested
    inner class TarTestCase {

        @Nested
        inner class TestCOMPRESS178 : OnTestCase("org.apache.commons.compress.archivers.TarTestCase::testCOMPRESS178", true) {

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
                project.copyProjectFile(resDir.resolve("COMPRESS-178.tar"), outputDir!!.path)

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
                 * /tmp/1898258570756207369/src/test/java/org/apache/commons/compress/archivers/TarTestCase.java:77: error: cannot find symbol
                 *             fail("Expected IOException");
                 *             ^
                 *   symbol:   method fail(String)
                 *   location: class TarTestCase
                 * /tmp/1898258570756207369/src/test/java/org/apache/commons/compress/archivers/TarTestCase.java:80: error: cannot find symbol
                 *             assertTrue("Expected cause = IllegalArgumentException", t instanceof IllegalArgumentException);
                 *             ^
                 *   symbol:   method assertTrue(String,boolean)
                 *   location: class TarTestCase
                 * ```
                 */
                @Test
                @Ignore("Obsolete")
                fun `Regression-00`() {
                    val cu = reducer.context
                        .getCUByPrimaryTypeName("org.apache.commons.compress.archivers.TarTestCase")
                    assumeTrue(cu != null)

                    val junitAssertImportDecl = cu.imports
                        .single { it.nameAsString == "org.junit.Assert" && it.isStatic && it.isAsterisk }
                    assertTrue(junitAssertImportDecl.inclusionReasonsData.isNotEmpty())
                    assertFalse(junitAssertImportDecl.isUnusedForRemovalData)
                }

                /**
                 * ```
                 * Failures (1):
                 *   JUnit Vintage:TarTestCase:testCOMPRESS178
                 *     MethodSource [className = 'org.apache.commons.compress.archivers.TarTestCase', methodName = 'testCOMPRESS178', methodParameterTypes = '']
                 *     => org.opentest4j.MultipleFailuresError: Multiple Failures (2 failures)
                 *     java.lang.AssertionError: This method should not be reached! Signature: setUp()
                 *     java.lang.AssertionError: This method should not be reached! Signature: tearDown()
                 *       org.junit.vintage.engine.execution.TestRun.getStoredResultOrSuccessful(TestRun.java:200)
                 *       org.junit.vintage.engine.execution.RunListenerAdapter.fireExecutionFinished(RunListenerAdapter.java:248)
                 *       org.junit.vintage.engine.execution.RunListenerAdapter.testFinished(RunListenerAdapter.java:214)
                 *       org.junit.vintage.engine.execution.RunListenerAdapter.testFinished(RunListenerAdapter.java:88)
                 *       org.junit.runner.notification.SynchronizedRunListener.testFinished(SynchronizedRunListener.java:87)
                 *       [...]
                 *       Suppressed: java.lang.AssertionError: This method should not be reached! Signature: setUp()
                 *         org.apache.commons.compress.AbstractTestCase.setUp(AbstractTestCase.java:51)
                 *         java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
                 *         java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
                 *         java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
                 *         java.base/java.lang.reflect.Method.invoke(Method.java:568)
                 *         [...]
                 *       Suppressed: java.lang.AssertionError: This method should not be reached! Signature: tearDown()
                 *         org.apache.commons.compress.AbstractTestCase.tearDown(AbstractTestCase.java:64)
                 *         java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
                 *         java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:77)
                 *         java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
                 *         java.base/java.lang.reflect.Method.invoke(Method.java:568)
                 *         [...]
                 * ```
                 */
                @Test
                @Ignore("Obsolete")
                fun `Regression-01`() {
                    val cu = reducer.context
                        .getCUByPrimaryTypeName("org.apache.commons.compress.AbstractTestCase")
                    assumeTrue(cu != null)

                    val type = cu.primaryType.get()
                    val setUpMethod = type.getMethodsByName("setUp").single()
                    assertFalse(setUpMethod.isUnusedForRemovalData)
                    assertFalse(setUpMethod.isUnusedForDummyData)

                    val tearDownMethod = type.getMethodsByName("tearDown").single()
                    assertFalse(tearDownMethod.isUnusedForRemovalData)
                    assertFalse(tearDownMethod.isUnusedForDummyData)
                }

                /**
                 * ```
                 * /tmp/1415909150634363297/src/main/java/org/apache/commons/compress/archivers/arj/ArjArchiveInputStream.java:79: error: cannot find symbol
                 *     public ArjArchiveEntry getNextEntry() throws IOException {
                 *            ^
                 *   symbol:   class ArjArchiveEntry
                 *   location: class ArjArchiveInputStream
                 * ```
                 */
                @Test
                @Ignore("Obsolete")
                fun `Regression-02`() {
                    run {
                        val cu = reducer.context
                            .getCUByPrimaryTypeName("org.apache.commons.compress.archivers.arj.ArjArchiveInputStream")
                        assumeTrue(cu != null)

                        val getNextEntryMethod = cu.primaryType.get()
                            .getMethodsBySignature("getNextEntry").single()

                        assertTrue(getNextEntryMethod.isUnusedForDummyData)
                    }

                    run {
                        val cu = reducer.context
                            .getCUByPrimaryTypeName("org.apache.commons.compress.archivers.arj.ArjArchiveEntry")
                        assumeTrue(cu != null)

                        val type = cu.primaryType.get()

                        assertFalse(type.isUnusedForRemovalData)
                    }
                }

                /**
                 * ```
                 * /tmp/381083138327616430/src/main/java/org/apache/commons/compress/archivers/dump/TapeInputStream.java:30: error: constructor FilterInputStream in class FilterInputStream cannot be applied to given types;
                 * class TapeInputStream extends FilterInputStream {
                 * ^
                 *   required: InputStream
                 *   found:    no arguments
                 *   reason: actual and formal argument lists differ in length
                 * ```
                 */
                @Test
                @Ignore("Obsolete")
                fun `Regression-04`() {
                    val cu = reducer.context
                        .getCUByPrimaryTypeName("org.apache.commons.compress.archivers.dump.TapeInputStream")
                    assumeTrue(cu != null)

                    val type = cu.primaryType.get()

                    val ctor = type.getConstructorByParameterTypes("InputStream").get()
                    assertTrue(ctor.isUnusedForDummyData)
                    assertFalse(ctor.isUnusedForRemovalData)
                }

                /**
                 * ```
                 * src/main/java/org/apache/commons/compress/archivers/tar/TarUtils.java:41: error: <anonymous org.apache.commons.compress.archivers.tar.TarUtils$1> is not abstract and does not override abstract method decode(byte[]) in ZipEncoding
                 *     static final ZipEncoding FALLBACK_ENCODING = new ZipEncoding() {
                 *                                                                    ^
                 * ```
                 */
                @Test
                fun `Regression-05`() {
                    val cu = reducer.context
                        .getCUByPrimaryTypeName("org.apache.commons.compress.archivers.tar.TarUtils")
                    assumeTrue(cu != null)

                    val type = cu.primaryType.get()
                    val method = type.getFieldByName("FALLBACK_ENCODING").get()
                        .getFieldVariableDecl("FALLBACK_ENCODING")
                        .initializer.get()
                        .asObjectCreationExpr()
                        .anonymousClassBody.get()
                        .single {
                            it is MethodDeclaration &&
                                    it.nameAsString == "decode" &&
                                    it.parameters.size == 1 &&
                                    it.parameters[0].typeAsString == "byte[]"
                        }
                        .asMethodDeclaration()

                    assertTrue(method.inclusionReasonsData.isEmpty())

                    val decision = decideForMethod(reducer.context, method, enableAssertions, noCache = true)
                    assertNotEquals(NodeTransformDecision.REMOVE, decision)

                    assertFalse(method.isUnusedForRemovalData)
                }

                /**
                 * ```
                 * src/main/java/org/apache/commons/compress/archivers/arj/ArjArchiveInputStream.java:68: error: cannot find symbol
                 *     public ArjArchiveEntry getNextEntry() throws IOException {
                 *            ^
                 *   symbol:   class ArjArchiveEntry
                 *   location: class ArjArchiveInputStream
                 * ```
                 */
                @Test
                @Ignore("Obsolete")
                fun `Regression-06`() {
                    val cu = reducer.context
                        .getCUByPrimaryTypeName("org.apache.commons.compress.archivers.arj.ArjArchiveInputStream")
                    assumeTrue(cu != null)

                    val type = cu.primaryType.get()
                    val method = type
                        .getMethodsBySignature("getNextEntry").single()

                    val decision = decideForMethod(reducer.context, method, enableAssertions, noCache = true)
                    assertNotEquals(NodeTransformDecision.REMOVE, decision)

                    assertFalse(method.isUnusedForRemovalData)
                }
            }
        }
    }
}