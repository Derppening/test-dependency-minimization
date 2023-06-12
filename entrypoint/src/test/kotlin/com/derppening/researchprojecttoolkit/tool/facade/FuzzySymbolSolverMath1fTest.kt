package com.derppening.researchprojecttoolkit.tool.facade

import com.derppening.researchprojecttoolkit.tool.TestProjects
import com.derppening.researchprojecttoolkit.tool.assertResolvedTypeIs
import com.github.javaparser.resolution.types.ResolvedReferenceType
import com.github.javaparser.resolution.types.ResolvedTypeVariable
import com.github.javaparser.resolution.types.ResolvedWildcard
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FuzzySymbolSolverMath1fTest : FuzzySymbolSolverTest() {

    override val project = TestProjects.Math1f

    @Test
    fun `Do Not Recursively Expand Type Param Bounds`() {
        val cu = project.parseSourceFile(
            "src/main/java/org/apache/commons/math3/linear/ArrayFieldVector.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getConstructorByParameterTypes("ArrayFieldVector<T>").get()
            .body
            .statements[1]
            .asExpressionStmt()
            .expression
            .asAssignExpr()
            .value
            .asMethodCallExpr()
        assumeTrue { node.toString() == "v.getField()" }

        val type = symbolSolver.calculateType(node)
        assertResolvedTypeIs<ResolvedReferenceType>(type)
        assertEquals("org.apache.commons.math3.Field", type.qualifiedName)
        assertEquals(1, type.typeParametersValues().size)
        val typeTp0 = type.typeParametersValues()[0]
            .let { assertResolvedTypeIs<ResolvedTypeVariable>(it).asTypeParameter() }
        assertEquals("T", typeTp0.name)
        assertEquals("org.apache.commons.math3.linear.ArrayFieldVector", typeTp0.containerQualifiedName)
    }

    @Test
    fun `Do Not Recursively Expand Type Param Bounded Wildcard`() {
        val cu = project.parseSourceFile(
            "src/main/java/org/apache/commons/math3/util/Pair.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getConstructorByParameterTypes("Pair<? extends K,? extends V>").get()
            .body
            .statements[0]
            .asExplicitConstructorInvocationStmt()
        assumeTrue { node.toString() == "this(entry.getKey(), entry.getValue());" }

        val arg0Node = node.arguments[0].asMethodCallExpr()
        val arg0ScopeNode = arg0Node.scope.get().asNameExpr()

        val arg0ScopeType = symbolSolver.calculateType(arg0ScopeNode)
        assertResolvedTypeIs<ResolvedReferenceType>(arg0ScopeType)
        assertEquals("org.apache.commons.math3.util.Pair", arg0ScopeType.qualifiedName)
        assertEquals(2, arg0ScopeType.typeParametersValues().size)
        val arg0ScopeTypeTp0 = arg0ScopeType.typeParametersValues()[0]
        assertResolvedTypeIs<ResolvedWildcard>(arg0ScopeTypeTp0)
        assertTrue(arg0ScopeTypeTp0.isBounded)
        assertTrue(arg0ScopeTypeTp0.isExtends)
        val arg0ScopeTypeTp0Bound = arg0ScopeTypeTp0.boundedType
            .let { assertResolvedTypeIs<ResolvedTypeVariable>(it).asTypeParameter() }
        assertEquals("K", arg0ScopeTypeTp0Bound.name)
        assertEquals("org.apache.commons.math3.util.Pair", arg0ScopeTypeTp0Bound.containerQualifiedName)
        val arg0ScopeTypeTp1 = arg0ScopeType.typeParametersValues()[1]
        assertResolvedTypeIs<ResolvedWildcard>(arg0ScopeTypeTp1)
        assertTrue(arg0ScopeTypeTp1.isBounded)
        assertTrue(arg0ScopeTypeTp1.isExtends)
        val arg0ScopeTypeTp1Bound = arg0ScopeTypeTp1.boundedType
            .let { assertResolvedTypeIs<ResolvedTypeVariable>(it).asTypeParameter() }
        assertEquals("V", arg0ScopeTypeTp1Bound.name)
        assertEquals("org.apache.commons.math3.util.Pair", arg0ScopeTypeTp1Bound.containerQualifiedName)

        val arg0Type = symbolSolver.calculateTypeInternal(arg0Node)
            .let { assertResolvedTypeIs<ResolvedTypeVariable>(it).asTypeParameter() }
        assertEquals("K", arg0Type.name)
        assertEquals("org.apache.commons.math3.util.Pair", arg0Type.containerQualifiedName)

        val arg1Node = node.arguments[1].asMethodCallExpr()
        val arg1ScopeNode = arg0Node.scope.get().asNameExpr()

        val arg1ScopeType = symbolSolver.calculateType(arg1ScopeNode)
        assertResolvedTypeIs<ResolvedReferenceType>(arg1ScopeType)
        assertEquals("org.apache.commons.math3.util.Pair", arg1ScopeType.qualifiedName)
        assertEquals(2, arg1ScopeType.typeParametersValues().size)
        val arg1ScopeTypeTp0 = arg1ScopeType.typeParametersValues()[0]
        assertResolvedTypeIs<ResolvedWildcard>(arg1ScopeTypeTp0)
        assertTrue(arg1ScopeTypeTp0.isBounded)
        assertTrue(arg1ScopeTypeTp0.isExtends)
        val arg1ScopeTypeTp0Bound = arg1ScopeTypeTp0.boundedType
            .let { assertResolvedTypeIs<ResolvedTypeVariable>(it).asTypeParameter() }
        assertEquals("K", arg1ScopeTypeTp0Bound.name)
        assertEquals("org.apache.commons.math3.util.Pair", arg1ScopeTypeTp0Bound.containerQualifiedName)
        val arg1ScopeTypeTp1 = arg1ScopeType.typeParametersValues()[1]
        assertResolvedTypeIs<ResolvedWildcard>(arg1ScopeTypeTp1)
        assertTrue(arg1ScopeTypeTp1.isBounded)
        assertTrue(arg1ScopeTypeTp1.isExtends)
        val arg1ScopeTypeTp1Bound = arg1ScopeTypeTp1.boundedType
            .let { assertResolvedTypeIs<ResolvedTypeVariable>(it).asTypeParameter() }
        assertEquals("V", arg1ScopeTypeTp1Bound.name)
        assertEquals("org.apache.commons.math3.util.Pair", arg1ScopeTypeTp1Bound.containerQualifiedName)

        val arg1Type = symbolSolver.calculateTypeInternal(arg1Node)
            .let { assertResolvedTypeIs<ResolvedTypeVariable>(it).asTypeParameter() }
        assertEquals("V", arg1Type.name)
        assertEquals("org.apache.commons.math3.util.Pair", arg1Type.containerQualifiedName)
    }
}