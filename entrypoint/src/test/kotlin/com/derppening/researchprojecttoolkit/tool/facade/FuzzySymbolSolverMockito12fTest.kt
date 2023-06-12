package com.derppening.researchprojecttoolkit.tool.facade

import com.derppening.researchprojecttoolkit.tool.TestProjects
import com.derppening.researchprojecttoolkit.tool.assertResolvedTypeIs
import com.derppening.researchprojecttoolkit.util.resolveDeclaration
import com.github.javaparser.resolution.declarations.ResolvedDeclaration
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration
import com.github.javaparser.resolution.types.ResolvedArrayType
import com.github.javaparser.resolution.types.ResolvedReferenceType
import com.github.javaparser.resolution.types.ResolvedWildcard
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FuzzySymbolSolverMockito12fTest : FuzzySymbolSolverTest() {

    override val project = TestProjects.Mockito12f

    @Test
    fun `Solve toString in Interface`() {
        val cu = project.parseSourceFile(
            "src/org/mockito/internal/reporting/SmartPrinter.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getConstructorByParameterTypes("PrintingFriendlyInvocation", "PrintingFriendlyInvocation", "Integer").get()
            .body
            .statements[1].asExpressionStmt()
            .expression.asMethodCallExpr()
            .arguments[0].asBinaryExpr()
            .left.asMethodCallExpr()
            .scope.get().asMethodCallExpr()
        assumeTrue(node.toString() == "wanted.toString()")

        val resolvedDecl = symbolSolver.resolveDeclaration<ResolvedDeclaration>(node)
        assertIs<ResolvedMethodDeclaration>(resolvedDecl)
        assertEquals("toString", resolvedDecl.name)
        assertEquals(0, resolvedDecl.numberOfParams)
        val resolvedDeclType = resolvedDecl.declaringType()
        assertIs<ResolvedReferenceTypeDeclaration>(resolvedDeclType)
        assertEquals("java.lang.Object", resolvedDeclType.qualifiedName)

        val type = symbolSolver.calculateTypeInternal(node)
        assertResolvedTypeIs<ResolvedReferenceType>(type)
        assertEquals("java.lang.String", type.qualifiedName)
    }

    /**
     * ```
     * src/org/mockito/internal/creation/jmock/ClassImposterizer.java:77: error: combineLoadersOf(Class<?>,Class<?>...) has private access in SearchingClassLoader
     *     [javac]         enhancer.setClassLoader(SearchingClassLoader.combineLoadersOf(mockedType));
     *     [javac]                                                     ^
     * ```
     */
    @Test
    fun `Solve Overloading Methods with Different Visibility`() {
        val cu = project.parseSourceFile(
            "src/org/mockito/internal/creation/jmock/ClassImposterizer.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("createProxyClass", "Class<?>", "Class<?>").single()
            .body.get()
            .statements[2].asExpressionStmt()
            .expression.asMethodCallExpr()
            .arguments[0].asMethodCallExpr()
        assumeTrue(node.toString() == "SearchingClassLoader.combineLoadersOf(mockedType)")

        val resolvedDecl = symbolSolver.resolveDeclaration<ResolvedDeclaration>(node)
        assertIs<ResolvedMethodDeclaration>(resolvedDecl)
        assertEquals("combineLoadersOf", resolvedDecl.name)
        assertEquals(1, resolvedDecl.numberOfParams)
        val resolvedDeclType = resolvedDecl.declaringType()
        assertIs<ResolvedReferenceTypeDeclaration>(resolvedDeclType)
        assertEquals("org.mockito.internal.creation.jmock.SearchingClassLoader", resolvedDeclType.qualifiedName)

        val resolvedDeclParam0 = resolvedDecl.getParam(0)
        assertTrue(resolvedDeclParam0.isVariadic)
        val resolvedDeclParam0Type = assertResolvedTypeIs<ResolvedArrayType>(resolvedDeclParam0.type)
        assertEquals(1, resolvedDeclParam0Type.arrayLevel())
        val resolvedDeclParam0TypeComponent = assertResolvedTypeIs<ResolvedReferenceType>(resolvedDeclParam0Type.componentType)
        assertEquals("java.lang.Class", resolvedDeclParam0TypeComponent.qualifiedName)
    }

    @Test
    fun `Solve Type of Expression with Array Scope`() {
        val cu = project.parseSourceFile(
            "test/org/mockitousage/stubbing/StubbingWithCustomAnswerTest.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("shouldMakeSureTheInterfaceDoesNotChange").single()
            .body.get()
            .statements[0].asExpressionStmt()
            .expression.asMethodCallExpr()
            .arguments[0].asObjectCreationExpr()
            .anonymousClassBody.get()
            .single().asMethodDeclaration()
            .body.get()
            .statements[0].asExpressionStmt()
            .expression.asMethodCallExpr()
            .arguments[0].asMethodCallExpr()
            .scope.get().asMethodCallExpr()
        assumeTrue(node.toString() == "invocation.getArguments().getClass()")

        val scopeNode = node
            .scope.get().asMethodCallExpr()

        val scopeType = symbolSolver.calculateType(scopeNode)
        assertResolvedTypeIs<ResolvedArrayType>(scopeType)
        assertEquals(1, scopeType.arrayLevel())
        val scopeComponentType = assertResolvedTypeIs<ResolvedReferenceType>(scopeType.componentType)
        assertEquals("java.lang.Object", scopeComponentType.qualifiedName)

        val nodeType = symbolSolver.calculateType(node)
        assertResolvedTypeIs<ResolvedReferenceType>(nodeType)
        assertEquals("java.lang.Class", nodeType.qualifiedName)
        assertEquals(1, nodeType.typeParametersValues().size)
        assertResolvedTypeIs<ResolvedWildcard>(nodeType.typeParametersValues()[0])
    }

    @Test
    fun `Solve Type of Object getClass`() {
        val cu = project.parseSourceFile(
            "test/org/mockitousage/stubbing/StubbingWithCustomAnswerTest.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("shouldMakeSureTheInterfaceDoesNotChange").single()
            .body.get()
            .statements[0].asExpressionStmt()
            .expression.asMethodCallExpr()
            .arguments[0].asObjectCreationExpr()
            .anonymousClassBody.get()
            .single().asMethodDeclaration()
            .body.get()
            .statements[0].asExpressionStmt()
            .expression.asMethodCallExpr()
            .arguments[0].asMethodCallExpr()
            .scope.get().asMethodCallExpr()
        assumeTrue(node.toString() == "invocation.getArguments().getClass()")

        val scopeNode = node
            .scope.get().asMethodCallExpr()

        val scopeType = symbolSolver.calculateType(scopeNode)
        assumeTrue(scopeType.describe() == "java.lang.Object[]")

        val nodeType = symbolSolver.calculateType(node)
        assertResolvedTypeIs<ResolvedReferenceType>(nodeType)
        assertEquals("java.lang.Class", nodeType.qualifiedName)
        assertEquals(1, nodeType.typeParametersValues().size)
        val nodeTypeTp0 = assertResolvedTypeIs<ResolvedWildcard>(nodeType.typeParametersValues()[0])
        assertTrue(nodeTypeTp0.isExtends)
        val nodeTypeTp0Bound = assertResolvedTypeIs<ResolvedArrayType>(nodeTypeTp0.boundedType)
        assertEquals(1, nodeTypeTp0Bound.arrayLevel())
        val nodeTypeTp0BoundComponent = assertResolvedTypeIs<ResolvedReferenceType>(nodeTypeTp0Bound.componentType)
        assertEquals("java.lang.Object", nodeTypeTp0BoundComponent.qualifiedName)
    }
}