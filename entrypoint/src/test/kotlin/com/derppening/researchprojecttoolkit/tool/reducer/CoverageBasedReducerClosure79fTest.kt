package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.model.ExecutableDeclaration
import com.derppening.researchprojecttoolkit.model.ReferenceTypeLikeDeclaration
import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.tool.compilation.CompilerProxy
import com.derppening.researchprojecttoolkit.tool.reachability.ReachableReason
import com.derppening.researchprojecttoolkit.tool.reducer.AbstractBaselineReducer.Companion.findClassFromJacocoCoverage
import com.derppening.researchprojecttoolkit.tool.reducer.AbstractBaselineReducer.Companion.findMethodFromJacocoCoverage
import com.derppening.researchprojecttoolkit.tool.transform.NodeTransformDecision
import com.derppening.researchprojecttoolkit.tool.transform.TagGroundTruthUnusedDecls.Companion.decideForConstructor
import com.derppening.researchprojecttoolkit.tool.transform.TagGroundTruthUnusedDecls.Companion.decideForMethod
import com.derppening.researchprojecttoolkit.tool.transform.TagGroundTruthUnusedDecls.Companion.isTransitiveDependentReachable
import com.derppening.researchprojecttoolkit.tool.transform.TagGroundTruthUnusedDecls.Companion.isTypeReachable
import com.derppening.researchprojecttoolkit.util.findAll
import com.derppening.researchprojecttoolkit.util.getFieldVariableDecl
import com.derppening.researchprojecttoolkit.util.synchronizedWith
import com.github.javaparser.ast.Node
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.ConstructorDeclaration
import com.github.javaparser.ast.body.EnumDeclaration
import org.junit.jupiter.api.Nested
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.test.*

class CoverageBasedReducerClosure79fTest : CoverageBasedReducerIntegTest() {

    override val project = TestProjects.Closure79f

    @Nested
    inner class NormalizeTest {

        @Nested
        inner class TestIssue {

            private val entrypoint = "com.google.javascript.jscomp.NormalizeTest::testIssue"

            @Ignore
            @Nested
            inner class NoAssertions : OnTestCase(entrypoint, false) {

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

                    val srcRootOutputDirs = project.cpSourceRoots.map {
                        val srcRootOutputDir = outputDir!!.path.resolve(it.fileName)

                        it.toFile().copyRecursively(srcRootOutputDir.toFile())

                        srcRootOutputDir
                    }

                    val sourceRoots = sourceRootMapping.values.toList() + srcRootOutputDirs
                    val compiler = CompilerProxy(
                        sourceRoots,
                        project.testCpJars.joinToString(":"),
                        listOf("-Xmaxerrs", "$JAVAC_MAXERRS"),
                        JAVA_17_HOME
                    )
                    assertCompileSuccess(compiler)

                    // Copy `build/classes/rhino_ast/java/com/google/javascript/rhino/Messages.properties` into directory
                    project.copyProjectFile(
                        Path("build/classes/rhino_ast/java/com/google/javascript/rhino/Messages.properties"),
                        outputDir!!.path
                    )
                    project.copyProjectFile(
                        Path("src/com/google/javascript/jscomp/parsing/ParserConfig.properties"),
                        outputDir!!.path
                    )

                    val junitRunner = tryTestNoAssertion(outputDir!!.path, JAVA_17_HOME) {
                        add("-cp")
                        add("${project.testCpJars.joinToString(":")}:${sourceRoots.joinToString(":")}:build/classes")

                        add("-m")
                        add(testCase.toJUnitMethod())
                    }
                    assertFalse(junitRunner.exitCode.isSuccess)
                }

                @Nested
                inner class ReductionRegressionTests {
                }
            }

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

                    val srcRootOutputDirs = project.cpSourceRoots.map {
                        val srcRootOutputDir = outputDir!!.path.resolve(it.fileName)

                        it.toFile().copyRecursively(srcRootOutputDir.toFile())

                        srcRootOutputDir
                    }

                    val sourceRoots = sourceRootMapping.values.toList() + srcRootOutputDirs
                    val compiler = CompilerProxy(
                        sourceRoots,
                        project.testCpJars.joinToString(":"),
                        listOf("-Xmaxerrs", "$JAVAC_MAXERRS"),
                        JAVA_17_HOME
                    )
                    assertCompileSuccess(compiler)

                    // Copy `build/classes/rhino_ast/java/com/google/javascript/rhino/Messages.properties` into directory
                    project.copyProjectFile(
                        Path("build/classes/rhino_ast/java/com/google/javascript/rhino/Messages.properties"),
                        outputDir!!.path
                    )
                    project.copyProjectFile(
                        Path("src/com/google/javascript/jscomp/parsing/ParserConfig.properties"),
                        outputDir!!.path
                    )

                    val junitRunner = tryTestNoAssertion(outputDir!!.path, JAVA_17_HOME) {
                        add("-cp")
                        add("${project.testCpJars.joinToString(":")}:${sourceRoots.joinToString(":")}:build/classes")

                        add("-m")
                        add(testCase.toJUnitMethod())
                    }
                    assertFalse(junitRunner.exitCode.isSuccess)
                }

                @Nested
                inner class SymbolLookupTests {

                    @Test
                    fun `Find Correct Nested Type`() {
                        val coverageData = project.getBaselineDir(entrypointSpec).readJacocoBaselineCoverage()
                        assumeTrue(coverageData != null)

                        val classCov = coverageData.report
                            .packages.single { it.name == "com/google/javascript/jscomp" }
                            .classes.single { it.name == "com/google/javascript/jscomp/Normalize\$DuplicateDeclarationHandler" }

                        val cu = reducer.context.getCUByPrimaryTypeName("com.google.javascript.jscomp.Normalize")
                        assumeTrue(cu != null)

                        val allRefLikeTypes = cu
                            .findAll<Node> { ReferenceTypeLikeDeclaration.createOrNull(it) != null }
                            .map { ReferenceTypeLikeDeclaration.create(it) }

                        val expectedClass = cu.primaryType.get()
                            .members
                            .single { it is ClassOrInterfaceDeclaration && it.nameAsString == "DuplicateDeclarationHandler" }
                            .asClassOrInterfaceDeclaration()

                        val foundClass = findClassFromJacocoCoverage(allRefLikeTypes, classCov)
                        assertIs<ReferenceTypeLikeDeclaration.TypeDecl>(foundClass)
                        assertEquals(expectedClass, foundClass.node)
                    }

                    @Test
                    fun `Find Correct Nested Type 2`() {
                        val coverageData = project.getBaselineDir(entrypointSpec).readJacocoBaselineCoverage()
                        assumeTrue(coverageData != null)

                        val cu =
                            reducer.context.getCUByPrimaryTypeName("com.google.javascript.jscomp.graph.GraphReachability")
                        assumeTrue(cu != null)

                        val allRefLikeTypes = cu
                            .findAll<Node> { ReferenceTypeLikeDeclaration.createOrNull(it) != null }
                            .map { ReferenceTypeLikeDeclaration.create(it) }

                        val classCov1 = coverageData.report
                            .packages.single { it.name == "com/google/javascript/jscomp/graph" }
                            .classes.single { it.name == "com/google/javascript/jscomp/graph/GraphReachability" }

                        val class1 = cu.primaryType.get()

                        val foundClass1 = findClassFromJacocoCoverage(allRefLikeTypes, classCov1)
                        assertIs<ReferenceTypeLikeDeclaration.TypeDecl>(foundClass1)
                        assertEquals(class1, foundClass1.node)

                        val classCov2 = coverageData.report
                            .packages.single { it.name == "com/google/javascript/jscomp/graph" }
                            .classes.single { it.name == "com/google/javascript/jscomp/graph/GraphReachability\$1" }

                        val foundObjExpr = cu.primaryType.get()
                            .getFieldByName("REACHABLE").get()
                            .variables.single()
                            .initializer.get().asObjectCreationExpr()

                        val foundClass2 = findClassFromJacocoCoverage(allRefLikeTypes, classCov2)
                        assertIs<ReferenceTypeLikeDeclaration.AnonClassDecl>(foundClass2)
                        assertEquals(foundObjExpr, foundClass2.node)
                    }

                    @Test
                    fun `Unable to Find Missing Local Type Constructor`() {
                        val coverageData = project.getBaselineDir(entrypointSpec).readJacocoBaselineCoverage()
                        assumeTrue(coverageData != null)

                        val packageCov = coverageData.report
                            .packages.single { it.name == "com/google/javascript/jscomp" }
                        val classCov = packageCov
                            .classes.single { it.name == "com/google/javascript/jscomp/ExpressionDecomposerTest\$1Find" }
                        val methodCov = classCov.methods
                            .single { it.name == "<init>" && it.desc == "(Ljava/lang/String;I)V" }

                        val cu =
                            reducer.context.getCUByPrimaryTypeName("com.google.javascript.jscomp.ExpressionDecomposerTest")
                        assumeTrue(cu != null)

                        val outputStream = ByteArrayOutputStream()
                        PrintStream(outputStream, true, StandardCharsets.UTF_8.name()).use { printStream ->
                            System.setOut(printStream)

                            val matchedMethod = findMethodFromJacocoCoverage(
                                cu.findAll<Node> { ExecutableDeclaration.createOrNull(it) != null }
                                    .map { ExecutableDeclaration.create(it) },
                                classCov,
                                methodCov,
                                reducer.context
                            )
                            assertNull(matchedMethod)

                            assertFalse {
                                outputStream.toString(StandardCharsets.UTF_8.name()).contains("Cannot find constructor")
                            }
                        }
                    }

                    @Test
                    fun `Find Missing Anon Class Constructor`() {
                        val coverageData = project.getBaselineDir(entrypointSpec).readJacocoBaselineCoverage()
                        assumeTrue(coverageData != null)

                        run {
                            val packageCov = coverageData.report
                                .packages.single { it.name == "com/google/javascript/jscomp" }
                            val classCov = packageCov
                                .classes.single { it.name == "com/google/javascript/jscomp/PassFactory" }
                            val methodCov = classCov.methods
                                .single { it.name == "<init>" && it.desc == "(Ljava/lang/String;Z)V" }

                            val cu = reducer.context.getCUByPrimaryTypeName("com.google.javascript.jscomp.PassFactory")
                            assumeTrue(cu != null)

                            val matchedMethod = findMethodFromJacocoCoverage(
                                cu.findAll<Node> { ExecutableDeclaration.createOrNull(it) != null }
                                    .map { ExecutableDeclaration.create(it) },
                                classCov,
                                methodCov,
                                reducer.context
                            )
                            assertNotNull(matchedMethod)
                        }

                        run {
                            val packageCov = coverageData.report
                                .packages.single { it.name == "com/google/javascript/jscomp" }
                            val classCov = packageCov
                                .classes.single { it.name == "com/google/javascript/jscomp/PassFactory\$1" }
                            val methodCov = classCov.methods
                                .single { it.name == "<init>" && it.desc == "(Lcom/google/javascript/jscomp/PassFactory;Ljava/lang/String;ZLcom/google/javascript/jscomp/PassFactory;)V" }

                            val cu = reducer.context.getCUByPrimaryTypeName("com.google.javascript.jscomp.PassFactory")
                            assumeTrue(cu != null)

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

                    @Test
                    fun `Vararg Parameter`() {
                        val coverageData = project.getBaselineDir(entrypointSpec).readJacocoBaselineCoverage()
                        assumeTrue(coverageData != null)

                        val packageCov = coverageData.report
                            .packages.single { it.name == "com/google/javascript/jscomp/jsonml" }
                        val classCov = packageCov
                            .classes.single { it.name == "com/google/javascript/jscomp/jsonml/JsonMLError" }
                        val methodCov = classCov.methods
                            .single { it.name == "<init>" && it.desc == "(Lcom/google/javascript/jscomp/DiagnosticType;Ljava/lang/String;Lcom/google/javascript/jscomp/jsonml/JsonML;ILcom/google/javascript/jscomp/jsonml/ErrorLevel;[Ljava/lang/String;)V" }

                        val cu =
                            reducer.context.getCUByPrimaryTypeName("com.google.javascript.jscomp.jsonml.JsonMLError")
                        assumeTrue(cu != null)

                        val matchedMethod = findMethodFromJacocoCoverage(
                            cu.findAll<Node> { ExecutableDeclaration.createOrNull(it) != null }
                                .map { ExecutableDeclaration.create(it) },
                            classCov,
                            methodCov,
                            reducer.context
                        )
                        assertNotNull(matchedMethod)
                        val methodDecl = assertIs<ConstructorDeclaration>(matchedMethod.node)
                        assertEquals(6, methodDecl.parameters.size)
                    }

                    @Test
                    fun `Replace Jacoco Class Name for Method Coverage`() {
                        val baselineDir = project.getBaselineDir(entrypointSpec)

                        val coberturaCov = baselineDir.readJacocoBaselineCoverage()
                        assumeTrue(coberturaCov != null)
                        val jacocoCov = baselineDir.readJacocoBaselineCoverage()
                        assumeTrue(jacocoCov != null)

                        val cu = reducer.context.getCUByPrimaryTypeName("com.google.javascript.jscomp.Compiler")
                        assumeTrue(cu != null)

                        val method = cu.primaryType.get()
                            .getMethodsBySignature("addChangeHandler", "CodeChangeHandler").single()

                        val jacocoClassCov = jacocoCov.report
                            .packages.single { it.name == "com/google/javascript/jscomp" }
                            .classes.single { it.name == "com/google/javascript/jscomp/Compiler" }
                        val jacocoMethodCov = jacocoClassCov
                            .methods.single { it.name == "addChangeHandler" }

                        val methodByJacocoCov = findMethodFromJacocoCoverage(
                            cu.findAll<Node> { ExecutableDeclaration.createOrNull(it) != null }
                                .map { ExecutableDeclaration.create(it) },
                            jacocoClassCov,
                            jacocoMethodCov,
                            reducer.context
                        )
                        assertNotNull(methodByJacocoCov)
                        assertEquals("addChangeHandler", methodByJacocoCov.name)
                    }

                    @Test
                    fun `Find Constructor in Nested Class of Interface`() {
                        val coverageData = project.getBaselineDir(entrypointSpec)
                            .readJacocoBaselineCoverage()
                        assumeTrue(coverageData != null)

                        val packageCov = coverageData.report
                            .packages.single { it.name == "com/google/javascript/jscomp" }
                        val classCov = packageCov
                            .classes.single { it.name == "com/google/javascript/jscomp/CodingConvention\$AssertionFunctionSpec" }
                        val methodCov = classCov.methods
                            .single { it.name == "<init>" && it.desc == "(Ljava/lang/String;)V" }

                        val cu = reducer.context.getCUByPrimaryTypeName("com.google.javascript.jscomp.CodingConvention")
                        assumeTrue(cu != null)

                        val type = cu.primaryType.get()
                            .members
                            .single { it is ClassOrInterfaceDeclaration && it.nameAsString == "AssertionFunctionSpec" }
                            .asClassOrInterfaceDeclaration()

                        run {
                            val matchedMethod = findMethodFromJacocoCoverage(
                                type.findAll<Node> { ExecutableDeclaration.createOrNull(it) != null }
                                    .map { ExecutableDeclaration.create(it) },
                                classCov,
                                methodCov,
                                reducer.context
                            )

                            assertNotNull(matchedMethod)
                            assertIs<ExecutableDeclaration.CtorDecl>(matchedMethod)
                            assertEquals(1, matchedMethod.node.parameters.size)
                        }

                        run {
                            val matchedMethod = findMethodFromJacocoCoverage(
                                cu.findAll<Node> { ExecutableDeclaration.createOrNull(it) != null }
                                    .map { ExecutableDeclaration.create(it) },
                                classCov,
                                methodCov,
                                reducer.context
                            )

                            assertNotNull(matchedMethod)
                            assertIs<ExecutableDeclaration.CtorDecl>(matchedMethod)
                            assertEquals(1, matchedMethod.node.parameters.size)
                        }
                    }
                }

                @Nested
                inner class ReductionRegressionTests {

                    /**
                     * ```
                     * src/com/google/javascript/rhino/JSDocInfo.java:67: error: cannot find symbol
                     *     private LazilyInitializedInfo info = null;
                     *             ^
                     *   symbol:   class LazilyInitializedInfo
                     *   location: class JSDocInfo
                     * ```
                     */
                    @Ignore
                    @Test
                    fun `Regression-00`() {
                        val cu = reducer.context
                            .getCUByPrimaryTypeName("com.google.javascript.rhino.JSDocInfo")
                        assumeTrue(cu != null)

                        val type = cu.primaryType.get()
                        val fieldDecl = type.getFieldByName("info").get()
                        val fieldVar = fieldDecl.getFieldVariableDecl("info")
                        assertTrue(fieldVar.inclusionReasonsData.isNotEmpty())

                        val targetType = type.members
                            .single { it is ClassOrInterfaceDeclaration && it.nameAsString == "LazilyInitializedInfo" }
                            .asClassOrInterfaceDeclaration()
                        assertTrue(targetType.inclusionReasonsData.isNotEmpty())
                        val filteredReasons = targetType.inclusionReasonsData.synchronizedWith {
                            filterIsInstance<ReachableReason.TransitiveNodeByExecDecl>()
                        }
                        assertTrue(filteredReasons.any { it.dependentNode === fieldDecl })
                    }

                    /**
                     * ```
                     * src/com/google/javascript/rhino/TokenStream.java:121: error: cannot find symbol
                     *     private Parser parser;
                     *             ^
                     *   symbol:   class Parser
                     *   location: class TokenStream
                     * ```
                     */
                    @Test
                    fun `Regression-01`() {
                        val cu = reducer.context
                            .getCUByPrimaryTypeName("com.google.javascript.rhino.TokenStream")
                        assumeTrue(cu != null)

                        val type = cu.primaryType.get()
                        val fieldDecl = type.getFieldByName("parser").get()
                        val fieldVar = fieldDecl.getFieldVariableDecl("parser")
                        assertTrue(fieldVar.inclusionReasonsData.isNotEmpty())

                        val targetCU = reducer.context.getCUByPrimaryTypeName("com.google.javascript.rhino.Parser")
                        assumeTrue(targetCU != null)
                        val targetType = targetCU.primaryType.get()
                        assertTrue(targetType.inclusionReasonsData.isNotEmpty())

                        assertTrue(isTransitiveDependentReachable(reducer.context, targetType, enableAssertions, noCache = true))
                    }

                    /**
                     * ```
                     * src/com/google/javascript/rhino/jstype/RecordType.java:75: error: cannot find symbol
                     *     RecordType(JSTypeRegistry registry, Map<String, RecordProperty> properties) {
                     *                                                     ^
                     *   symbol:   class RecordProperty
                     *   location: class RecordType
                     * ```
                     */
                    @Test
                    fun `Regression-02`() {
                        val cu = reducer.context
                            .getCUByPrimaryTypeName("com.google.javascript.rhino.jstype.RecordType")
                        assumeTrue(cu != null)

                        val type = cu.primaryType.get()
                        val ctorDecl =
                            type.getConstructorByParameterTypes("JSTypeRegistry", "Map<String,RecordProperty>").get()
                        assumeFalse(ctorDecl.isUnusedForRemovalData)
                        val nestedType = ctorDecl.parameters[1].type
                        assertTrue(nestedType.inclusionReasonsData.isNotEmpty())

                        val targetCU = reducer.context
                            .getCUByPrimaryTypeName("com.google.javascript.rhino.jstype.RecordTypeBuilder")
                        assumeTrue(targetCU != null)
                        val targetType = targetCU.primaryType.get()
                            .members.single { it is ClassOrInterfaceDeclaration && it.nameAsString == "RecordProperty" }
                            .asClassOrInterfaceDeclaration()
                        assertTrue(targetType.inclusionReasonsData.isNotEmpty())
                        assertFalse(targetType.isUnusedForRemovalData)
                    }

                    /**
                     * ```
                     * src/com/google/javascript/jscomp/graph/Graph.java:55: error: cannot find symbol
                     *     private static class GraphAnnotationState extends ArrayList<AnnotationState> {
                     *                                                                 ^
                     *   symbol:   class AnnotationState
                     *   location: class Graph<N,E>
                     *   where N,E are type-variables:
                     *     N extends Object declared in class Graph
                     *     E extends Object declared in class Graph
                     * ```
                     */
                    @Test
                    fun `Regression-03`() {
                        val cu = reducer.context
                            .getCUByPrimaryTypeName("com.google.javascript.jscomp.graph.Graph")
                        assumeTrue(cu != null)

                        val primaryType = cu.primaryType.get()
                        val type = primaryType
                            .members.single { it is ClassOrInterfaceDeclaration && it.nameAsString == "GraphAnnotationState" }
                            .asClassOrInterfaceDeclaration()
                        assertFalse(type.isUnusedForSupertypeRemovalData)

                        val targetType = primaryType
                            .members.single { it is ClassOrInterfaceDeclaration && it.nameAsString == "AnnotationState" }
                            .asClassOrInterfaceDeclaration()

                        assertTrue(isTransitiveDependentReachable(reducer.context, targetType, enableAssertions, noCache = true))
                        assertTrue(isTypeReachable(reducer.context, targetType, enableAssertions, noCache = true))

                        assertTrue(targetType.inclusionReasonsData.isNotEmpty())
                        assertFalse(targetType.isUnusedForRemovalData)
                    }

                    /**
                     * ```
                     * src/com/google/javascript/rhino/jstype/JSTypeRegistry.java:140: error: cannot find symbol
                     *     private ResolveMode resolveMode = ResolveMode.LAZY_NAMES;
                     *                                                  ^
                     *   symbol:   variable LAZY_NAMES
                     *   location: class ResolveMode
                     * ```
                     */
                    @Test
                    fun `Regression-04`() {
                        val cu = reducer.context
                            .getCUByPrimaryTypeName("com.google.javascript.rhino.jstype.JSTypeRegistry")
                        assumeTrue(cu != null)

                        val primaryType = cu.primaryType.get()
                        val targetType = primaryType
                            .members.single { it is EnumDeclaration && it.nameAsString == "ResolveMode" }
                            .asEnumDeclaration()
                        val enumConst = targetType.entries
                            .single { it.nameAsString == "LAZY_NAMES" }

                        assertTrue(enumConst.inclusionReasonsData.isNotEmpty())
                        assertFalse(enumConst.isUnusedForRemovalData)
                    }

                    /**
                     * ```
                     * src/com/google/javascript/jscomp/NameAnalyzer.java:74: error: cannot find symbol
                     *     private DiGraph<JsName, RefType> referenceGraph = LinkedDirectedGraph.createWithoutAnnotations();
                     *                                                                          ^
                     *   symbol:   method createWithoutAnnotations()
                     *   location: class LinkedDirectedGraph
                     * ```
                     */
                    @Test
                    @Ignore
                    fun `Regression-05`() {
                        val cu = reducer.context
                            .getCUByPrimaryTypeName("com.google.javascript.jscomp.NameAnalyzer")
                        assumeTrue(cu != null)

                        val primaryType = cu.primaryType.get()
                        val fieldDecl = primaryType
                            .getFieldByName("referenceGraph").get()
                            .getFieldVariableDecl("referenceGraph")
                        assertTrue(fieldDecl.inclusionReasonsData.isNotEmpty())
                        assertFalse(fieldDecl.isUnusedForRemovalData)

                        val targetCU =
                            reducer.context.getCUByPrimaryTypeName("com.google.javascript.jscomp.graph.LinkedDirectedGraph")
                        assumeTrue(targetCU != null)

                        val targetMethod = targetCU.primaryType.get()
                            .getMethodsBySignature("createWithoutAnnotations").single()

                        assertTrue(isTransitiveDependentReachable(reducer.context, targetMethod, enableAssertions, noCache = true))

                        assertTrue(targetMethod.inclusionReasonsData.isNotEmpty())
                        assertFalse(targetMethod.isUnusedForRemovalData)
                    }

                    /**
                     * ```
                     * src/com/google/javascript/jscomp/GlobalNamespace.java:128: error: cannot find symbol
                     *             return fullName() + " (" + type + "): globalSets=" + globalSets + ", localSets=" + localSets + ", totalGets=" + totalGets + ", aliasingGets=" + aliasingGets + ", callGets=" + callGets;
                     *                    ^
                     *   symbol:   method fullName()
                     *   location: class Name
                     * ```
                     */
                    @Test
                    fun `Regression-06`() {
                        val cu = reducer.context
                            .getCUByPrimaryTypeName("com.google.javascript.jscomp.GlobalNamespace")
                        assumeTrue(cu != null)

                        val primaryType = cu.primaryType.get()
                        val type = primaryType
                            .members
                            .single { it is ClassOrInterfaceDeclaration && it.nameAsString == "Name" }
                            .asClassOrInterfaceDeclaration()

                        run {
                            val method = type
                                .getMethodsBySignature("toString").single()

                            val decision = decideForMethod(reducer.context, method, enableAssertions, noCache = true)
                            assertNotEquals(NodeTransformDecision.NO_OP, decision)

                            assertTrue(method.isUnusedForRemovalData)
                        }

                        run {
                            val method = type
                                .getMethodsBySignature("fullName").single()

                            val decision = decideForMethod(reducer.context, method, enableAssertions, noCache = true)
                            assertEquals(NodeTransformDecision.REMOVE, decision)

                            assertTrue(method.isUnusedForRemovalData)
                        }
                    }

                    /**
                     * ```
                     * test/com/google/javascript/jscomp/ReplaceMessagesTest.java:25: error: ReplaceMessagesTest.SimpleMessageBundle is not abstract and does not override abstract method idGenerator() in MessageBundle
                     *     private class SimpleMessageBundle implements MessageBundle {
                     *             ^
                     * ```
                     */
                    @Test
                    fun `Regression-07`() {
                        val srcCU = reducer.context
                            .getCUByPrimaryTypeName("com.google.javascript.jscomp.MessageBundle")
                        assumeTrue(srcCU != null)

                        val srcType = srcCU.primaryType.get()
                        val srcMethodDecl = srcType
                            .getMethodsBySignature("idGenerator").single()

                        val srcDecision = decideForMethod(reducer.context, srcMethodDecl, enableAssertions, noCache = true)

                        val targetCU =
                            reducer.context.getCUByPrimaryTypeName("com.google.javascript.jscomp.ReplaceMessagesTest")
                        assumeTrue(targetCU != null)

                        val targetType = targetCU.primaryType.get()
                        val targetMethodDecl = targetType
                            .members
                            .single { it is ClassOrInterfaceDeclaration && it.nameAsString == "SimpleMessageBundle" }
                            .asClassOrInterfaceDeclaration()
                            .getMethodsBySignature("idGenerator").single()

                        val targetDecision = decideForMethod(reducer.context, targetMethodDecl, enableAssertions, noCache = true)
                        if (srcDecision == NodeTransformDecision.REMOVE) {
                            assertEquals(srcDecision, targetDecision)
                        } else {
                            assertEquals(NodeTransformDecision.DUMMY, targetDecision)
                        }
                    }

                    /**
                     * ```
                     * src/com/google/javascript/jscomp/Compiler.java:43: error: Compiler is not abstract and does not override abstract method getSourceLine(String,int) in SourceExcerptProvider
                     * public class Compiler extends AbstractCompiler {
                     *        ^
                     * ```
                     */
                    @Test
                    fun `Regression-08`() {
                        val srcCU = reducer.context
                            .getCUByPrimaryTypeName("com.google.javascript.jscomp.SourceExcerptProvider")
                        assumeTrue(srcCU != null)

                        val srcType = srcCU.primaryType.get()
                        val srcMethodDecl = srcType
                            .getMethodsBySignature("getSourceLine", "String", "int").single()

                        val srcDecision = decideForMethod(reducer.context, srcMethodDecl, enableAssertions, noCache = true)

                        val targetCU = reducer.context.getCUByPrimaryTypeName("com.google.javascript.jscomp.Compiler")
                        assumeTrue(targetCU != null)

                        val targetType = targetCU.primaryType.get()
                        val targetMethodDecl = targetType
                            .getMethodsBySignature("getSourceLine", "String", "int").single()

                        val targetDecision = decideForMethod(reducer.context, targetMethodDecl, enableAssertions, noCache = true)
                        if (srcDecision == NodeTransformDecision.REMOVE) {
                            assertEquals(srcDecision, targetDecision)
                        } else {
                            assertEquals(NodeTransformDecision.DUMMY, targetDecision)
                        }
                    }

                    /**
                     * ```
                     * src/com/google/javascript/jscomp/LightweightMessageFormatter.java:58: error: LineNumberingFormatter is not abstract and does not override abstract method formatLine(String,int) in ExcerptFormatter
                     *     static class LineNumberingFormatter implements ExcerptFormatter {
                     *            ^
                     * ```
                     */
                    @Test
                    fun `Regression-09`() {
                        val srcCU = reducer.context
                            .getCUByPrimaryTypeName("com.google.javascript.jscomp.SourceExcerptProvider")
                        assumeTrue(srcCU != null)

                        val srcType = srcCU.primaryType.get()
                        val srcMethodDecl = srcType
                            .members
                            .single { it is ClassOrInterfaceDeclaration && it.nameAsString == "ExcerptFormatter" }
                            .asClassOrInterfaceDeclaration()
                            .getMethodsBySignature("formatLine", "String", "int").single()

                        val srcDecision = decideForMethod(reducer.context, srcMethodDecl, enableAssertions, noCache = true)

                        val targetCU =
                            reducer.context.getCUByPrimaryTypeName("com.google.javascript.jscomp.LightweightMessageFormatter")
                        assumeTrue(targetCU != null)

                        val targetType = targetCU.primaryType.get()
                        val targetMethodDecl = targetType
                            .members
                            .single { it is ClassOrInterfaceDeclaration && it.nameAsString == "LineNumberingFormatter" }
                            .asClassOrInterfaceDeclaration()
                            .getMethodsBySignature("formatLine", "String", "int").single()

                        val targetDecision = decideForMethod(reducer.context, targetMethodDecl, enableAssertions, noCache = true)
                        if (srcDecision == NodeTransformDecision.REMOVE) {
                            assertEquals(srcDecision, targetDecision)
                        } else {
                            assertEquals(NodeTransformDecision.DUMMY, targetDecision)
                        }
                    }

                    /**
                     * ```
                     * /tmp/6337615793844660620/src/com/google/javascript/jscomp/sourcemap/SourceMapGeneratorV1.java:38: error: cannot find symbol
                     *     private Mapping lastMapping;
                     *             ^
                     *   symbol:   class Mapping
                     *   location: class SourceMapGeneratorV1
                     * ```
                     */
                    @Test
                    @Ignore("Obsolete")
                    fun `Regression-10`() {
                        val cu = reducer.context
                            .getCUByPrimaryTypeName("com.google.javascript.jscomp.sourcemap.SourceMapGeneratorV1")
                        assumeTrue(cu != null)

                        val type = cu.primaryType.get()
                        val targetType = type
                            .members
                            .single { it is ClassOrInterfaceDeclaration && it.nameAsString == "Mapping" }
                            .asClassOrInterfaceDeclaration()

                        assertTrue(targetType.inclusionReasonsData.isNotEmpty())
                        assertTrue(isTransitiveDependentReachable(reducer.context, targetType, enableAssertions, noCache = true))
                        assertTrue(isTypeReachable(reducer.context, targetType, enableAssertions, noCache = true))
                        assertFalse(targetType.isUnusedForRemovalData)
                    }

                    /**
                     * ```
                     * src/com/google/javascript/jscomp/MaybeReachingVariableUse.java:39: error: cannot find symbol
                     *         super(cfg, new ReachingUsesJoinOp());
                     *                        ^
                     *   symbol:   class ReachingUsesJoinOp
                     *   location: class MaybeReachingVariableUse
                     * ```
                     */
                    @Test
                    fun `Include DefaultConstructorDecl as TypeDeclaration`() {
                        val cu = reducer.context
                            .getCUByPrimaryTypeName("com.google.javascript.jscomp.MaybeReachingVariableUse")
                        assumeTrue(cu != null)

                        val type = cu.primaryType.get()

                        val srcMethod =
                            type.getConstructorByParameterTypes("ControlFlowGraph<Node>", "Scope", "AbstractCompiler")
                                .get()
                        assumeFalse(srcMethod.isUnusedForRemovalData)

                        val targetType = type
                            .members
                            .single { it is ClassOrInterfaceDeclaration && it.nameAsString == "ReachingUsesJoinOp" }
                            .asClassOrInterfaceDeclaration()

                        assertTrue(targetType.inclusionReasonsData.isNotEmpty())
                        assertTrue(isTransitiveDependentReachable(reducer.context, targetType, enableAssertions, noCache = true))
                        assertTrue(isTypeReachable(reducer.context, targetType, enableAssertions, noCache = true))
                        assertFalse(targetType.isUnusedForRemovalData)
                    }

                    /**
                     * ```
                     * src/com/google/javascript/jscomp/SourceMap.java:64: error: variable generator not initialized in the default constructor
                     *     final SourceMapGenerator generator;
                     *                              ^
                     * ```
                     */
                    @Test
                    fun `Regression-11`() {
                        val cu = reducer.context
                            .getCUByPrimaryTypeName("com.google.javascript.jscomp.SourceMap")
                        assumeTrue(cu != null)

                        val type = cu.primaryType.get()
                        assumeTrue(isTypeReachable(reducer.context, type, enableAssertions, noCache = true))

                        val targetCtor = type
                            .getConstructorByParameterTypes("SourceMapGenerator").get()

                        assertTrue(targetCtor.inclusionReasonsData.isNotEmpty())
                        assertNotEquals(
                            NodeTransformDecision.REMOVE,
                            decideForConstructor(reducer.context, targetCtor, enableAssertions = true, noCache = true)
                        )
                    }

                    @Test
                    @Ignore
                    fun `Check Assertions Do Not Affect Constructor Decision`() {
                        val cu = reducer.context
                            .getCUByPrimaryTypeName("com.google.javascript.jscomp.MaybeReachingVariableUse")
                        assumeTrue(cu != null)

                        val primaryType = cu.primaryType.get()
                        val ctor = primaryType
                            .getConstructorByParameterTypes("ControlFlowGraph<Node>", "Scope", "AbstractCompiler").get()
                        assertNotEquals(NodeTransformDecision.REMOVE, decideForConstructor(reducer.context, ctor, enableAssertions, noCache = false))

                        val targetType = primaryType
                            .members
                            .single { it is ClassOrInterfaceDeclaration && it.nameAsString == "ReachingUsesJoinOp" }
                            .asClassOrInterfaceDeclaration()
                        assertTrue(isTypeReachable(reducer.context, targetType, enableAssertions, noCache = true))
                        assertTrue(targetType.constructors.isEmpty())
                    }
                }
            }
        }
    }
}