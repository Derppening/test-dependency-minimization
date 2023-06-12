package com.derppening.researchprojecttoolkit.tool.facade

import com.derppening.researchprojecttoolkit.tool.TestProjects
import com.derppening.researchprojecttoolkit.tool.assertResolvedTypeIs
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.resolution.types.ResolvedReferenceType
import com.github.javaparser.resolution.types.ResolvedTypeVariable
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals

class FuzzySymbolSolverMath4fTest : FuzzySymbolSolverTest() {

    override val project = TestProjects.Math4f

    @Test
    fun `Regression for a-b Specificity`() {
        val cu = project.parseSourceFile(
            "src/main/java/org/apache/commons/math3/geometry/partitioning/RegionFactory.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("union", "Region<S>", "Region<S>").single()
            .body.get()
            .statements[0]
            .asExpressionStmt()
            .expression
            .asVariableDeclarationExpr()
            .variables.single()
            .initializer.get()
            .asMethodCallExpr()
        assumeTrue { node.toString() == "region1.getTree(false).merge(region2.getTree(false), new UnionMerger())" }

        val type = symbolSolver.calculateType(node)
        assertResolvedTypeIs<ResolvedReferenceType>(type)
        assertEquals("org.apache.commons.math3.geometry.partitioning.BSPTree", type.qualifiedName)
        assertEquals(1, type.typeParametersValues().size)
        val typeTp0 = type.typeParametersValues()[0]
            .let { assertResolvedTypeIs<ResolvedTypeVariable>(it).asTypeParameter() }
        assertEquals("S", typeTp0.name)
        assertEquals("org.apache.commons.math3.geometry.partitioning.RegionFactory", typeTp0.containerQualifiedName)
    }

    @Test
    fun `Regression for Unimplemented Operation in findInferredTypes`() {
        val cu = project.parseSourceFile(
            "src/main/java/org/apache/commons/math3/geometry/partitioning/RegionFactory.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .members
            .single { it is ClassOrInterfaceDeclaration && it.nameAsString == "DifferenceMerger" }
            .asClassOrInterfaceDeclaration()
            .getMethodsBySignature("merge", "BSPTree<S>", "BSPTree<S>", "BSPTree<S>", "boolean", "boolean").single()
            .body.get()
            .statements[0]
            .asIfStmt()
            .thenStmt
            .asBlockStmt()
            .statements[0]
            .asExpressionStmt()
            .expression
            .asVariableDeclarationExpr()
            .variables.single()
            .initializer.get()
            .asMethodCallExpr()
        assumeTrue { node.toString() == "recurseComplement(leafFromInstance ? tree : leaf)" }

        val type = symbolSolver.calculateType(node)
        assertResolvedTypeIs<ResolvedReferenceType>(type)
        assertEquals("org.apache.commons.math3.geometry.partitioning.BSPTree", type.qualifiedName)
        val typeTp0 = type.typeParametersValues()[0]
            .let { assertResolvedTypeIs<ResolvedTypeVariable>(it).asTypeParameter() }
        assertEquals("S", typeTp0.name)
        assertEquals("org.apache.commons.math3.geometry.partitioning.RegionFactory", typeTp0.containerQualifiedName)
    }
}