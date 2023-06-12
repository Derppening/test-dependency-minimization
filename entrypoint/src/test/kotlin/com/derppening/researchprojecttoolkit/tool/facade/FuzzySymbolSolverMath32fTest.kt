package com.derppening.researchprojecttoolkit.tool.facade

import com.derppening.researchprojecttoolkit.tool.TestProjects
import com.derppening.researchprojecttoolkit.tool.assertResolvedTypeIs
import com.github.javaparser.resolution.types.ResolvedReferenceType
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals

class FuzzySymbolSolverMath32fTest : FuzzySymbolSolverTest() {

    override val project = TestProjects.Math32f

    @Test
    fun `Solve Type Name with Conflicting Right-Most Name`() {
        val cu = project.parseSourceFile(
            "src/test/java/org/apache/commons/math3/geometry/euclidean/threed/PolyhedronsSetTest.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("testTetrahedron").single()
            .body.get()
            .statements[4]
            .asExpressionStmt()
            .expression
            .asVariableDeclarationExpr()
            .variables.single()
            .initializer.get()
            .asCastExpr()
            .expression
            .asMethodCallExpr()
        assumeTrue { node.toString() == "new RegionFactory<Euclidean3D>().buildConvex(new Plane(vertex3, vertex2, vertex1), new Plane(vertex2, vertex3, vertex4), new Plane(vertex4, vertex3, vertex1), new Plane(vertex1, vertex2, vertex4))" }

        val scopeNode = node.scope.get()
            .asObjectCreationExpr()

        val nodeScopeType = symbolSolver.calculateType(scopeNode)
        assertResolvedTypeIs<ResolvedReferenceType>(nodeScopeType)
        assertEquals("org.apache.commons.math3.geometry.partitioning.RegionFactory", nodeScopeType.qualifiedName)
        assertEquals(1, nodeScopeType.typeParametersValues().size)
        val nodeScopeTypeTp0 = nodeScopeType.typeParametersValues()[0]
        assertResolvedTypeIs<ResolvedReferenceType>(nodeScopeTypeTp0)
        assertEquals("org.apache.commons.math3.geometry.euclidean.threed.Euclidean3D", nodeScopeTypeTp0.qualifiedName)

        val nodeType = symbolSolver.calculateType(node)
        assertResolvedTypeIs<ResolvedReferenceType>(nodeType)
        assertEquals("org.apache.commons.math3.geometry.partitioning.Region", nodeType.qualifiedName)
        assertEquals(1, nodeType.typeParametersValues().size)
        val nodeTypeTp0 = nodeType.typeParametersValues()[0]
        assertResolvedTypeIs<ResolvedReferenceType>(nodeTypeTp0)
        assertEquals("org.apache.commons.math3.geometry.euclidean.threed.Euclidean3D", nodeTypeTp0.qualifiedName)
    }
}