package com.derppening.researchprojecttoolkit.tool.facade

import com.derppening.researchprojecttoolkit.tool.TestProjects
import com.derppening.researchprojecttoolkit.tool.assertResolvedTypeIs
import com.derppening.researchprojecttoolkit.util.resolveDeclaration
import com.github.javaparser.resolution.declarations.ResolvedConstructorDeclaration
import com.github.javaparser.resolution.declarations.ResolvedDeclaration
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration
import com.github.javaparser.resolution.types.ResolvedArrayType
import com.github.javaparser.resolution.types.ResolvedPrimitiveType
import com.github.javaparser.resolution.types.ResolvedReferenceType
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FuzzySymbolSolverMath17fTest : FuzzySymbolSolverTest() {

    override val project = TestProjects.Math17f

    @Test
    fun `Solve Nested Type in Superclass Scope`() {
        val cu = project.parseSourceFile(
            "src/test/java/org/apache/commons/math3/linear/SingularValueSolverTest.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("testSolveDimensionErrors").single()
            .body.get()
            .statements[4].asTryStmt()
            .tryBlock
            .statements[0].asExpressionStmt()
            .expression.asMethodCallExpr()
        val nodeScope = node
            .arguments[0].asObjectCreationExpr()
        assumeTrue { nodeScope.toString() == "new ArrayRealVectorTest.RealVectorTestImpl(b.getColumn(0))" }

        val scopeResolvedDecl = symbolSolver.resolveDeclaration<ResolvedDeclaration>(nodeScope)
        assertIs<ResolvedConstructorDeclaration>(scopeResolvedDecl)
        assertEquals("RealVectorTestImpl", scopeResolvedDecl.name)
        assertEquals("org.apache.commons.math3.linear.RealVectorAbstractTest.RealVectorTestImpl", scopeResolvedDecl.declaringType().qualifiedName)
        assertEquals(1, scopeResolvedDecl.numberOfParams)
        val scopeDeclParam0 = assertResolvedTypeIs<ResolvedArrayType>(scopeResolvedDecl.getParam(0).type)
        assertEquals(1, scopeDeclParam0.arrayLevel())
        val scopeDeclParam0Component = assertResolvedTypeIs<ResolvedPrimitiveType>(scopeDeclParam0.componentType)
        assertEquals(ResolvedPrimitiveType.DOUBLE, scopeDeclParam0Component)

        val resolvedDecl = symbolSolver.resolveDeclaration<ResolvedDeclaration>(node)
        assertIs<ResolvedMethodDeclaration>(resolvedDecl)
        assertEquals("solve", resolvedDecl.name)
        val resolvedDeclDeclType = assertIs<ResolvedReferenceTypeDeclaration>(resolvedDecl.declaringType())
        assertEquals("org.apache.commons.math3.linear.DecompositionSolver", resolvedDeclDeclType.qualifiedName)
        assertEquals(1, resolvedDecl.numberOfParams)
        val declParam0 = assertResolvedTypeIs<ResolvedReferenceType>(resolvedDecl.getParam(0).type)
        assertEquals("org.apache.commons.math3.linear.RealVector", declParam0.qualifiedName)
    }

    @Test
    fun `Match Array Argument Type to Vararg Array Parameter Types`() {
        val cu = project.parseSourceFile(
            "src/test/java/org/apache/commons/math3/util/MathArraysTest.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("testSortInPlaceFailures").single()
            .body.get()
            .statements[4].asTryStmt()
            .tryBlock
            .statements[0].asExpressionStmt()
            .expression.asMethodCallExpr()
        assumeTrue { node.toString() == "MathArrays.sortInPlace(one, two)" }

        val resolvedDecl = symbolSolver.resolveDeclaration<ResolvedDeclaration>(node)
        assertIs<ResolvedMethodDeclaration>(resolvedDecl)
    }
}