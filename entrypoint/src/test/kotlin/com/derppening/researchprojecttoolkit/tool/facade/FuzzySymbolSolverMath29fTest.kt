package com.derppening.researchprojecttoolkit.tool.facade

import com.derppening.researchprojecttoolkit.tool.TestProjects
import com.derppening.researchprojecttoolkit.tool.assertResolvedTypeIs
import com.derppening.researchprojecttoolkit.util.resolveDeclaration
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.resolution.declarations.ResolvedConstructorDeclaration
import com.github.javaparser.resolution.declarations.ResolvedDeclaration
import com.github.javaparser.resolution.types.ResolvedArrayType
import com.github.javaparser.resolution.types.ResolvedReferenceType
import com.github.javaparser.resolution.types.ResolvedVoidType
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FuzzySymbolSolverMath29fTest : FuzzySymbolSolverTest() {

    override val project = TestProjects.Math29f

    @Test
    fun `Solve Type Name with Conflicting Right-Most Name`() {
        val cu = project.parseSourceFile(
            "src/main/java/org/apache/commons/math3/linear/OpenMapRealVector.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .members
            .single { it is ClassOrInterfaceDeclaration && it.nameAsString == "OpenMapSparseIterator" }
            .asClassOrInterfaceDeclaration()
            .getMethodsBySignature("next").single()
            .body.get()
            .statements[0]
            .asExpressionStmt()
            .expression
            .asMethodCallExpr()
        assumeTrue { node.toString() == "iter.advance()" }
        val scopeNode = node.scope.get()
            .asNameExpr()

        val scopeType = symbolSolver.calculateType(scopeNode)
        assertResolvedTypeIs<ResolvedReferenceType>(scopeType)
        assertEquals("org.apache.commons.math3.util.OpenIntToDoubleHashMap.Iterator", scopeType.qualifiedName)

        val type = symbolSolver.calculateType(node)
        assertResolvedTypeIs<ResolvedVoidType>(type)
    }

    @Test
    fun `Solve Vararg Constructor`() {
        val cu = project.parseSourceFile(
            "src/main/java/org/apache/commons/math3/fraction/FractionConversionException.java",
            symbolSolver
        )
        val node = cu.primaryType.get().asClassOrInterfaceDeclaration()
            .getConstructorByParameterTypes("double", "long", "long").get()
            .body
            .statements[0]
            .asExplicitConstructorInvocationStmt()
        assumeTrue { node.toString() == "super(LocalizedFormats.FRACTION_CONVERSION_OVERFLOW, value, p, q);" }

        val resolvedDecl = symbolSolver.resolveDeclaration<ResolvedDeclaration>(node)
        assertIs<ResolvedConstructorDeclaration>(resolvedDecl)
        assertEquals(2, resolvedDecl.numberOfParams)
        assertEquals("org.apache.commons.math3.exception.ConvergenceException", resolvedDecl.declaringType().qualifiedName)
        val declParam0Type = assertResolvedTypeIs<ResolvedReferenceType>(resolvedDecl.getParam(0).type)
        assertEquals("org.apache.commons.math3.exception.util.Localizable", declParam0Type.qualifiedName)
        val declParam1Type = assertResolvedTypeIs<ResolvedArrayType>(resolvedDecl.getParam(1).type)
        assertEquals(1, declParam1Type.arrayLevel())
        val declParam1ComponentType = assertResolvedTypeIs<ResolvedReferenceType>(declParam1Type.componentType)
        assertTrue(declParam1ComponentType.isJavaLangObject)
    }
}