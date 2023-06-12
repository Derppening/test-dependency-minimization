package com.derppening.researchprojecttoolkit.tool.facade

import com.derppening.researchprojecttoolkit.tool.TestProjects
import com.derppening.researchprojecttoolkit.tool.assertIsUnbounded
import com.derppening.researchprojecttoolkit.tool.assertResolvedTypeIs
import com.github.javaparser.resolution.declarations.ResolvedDeclaration
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration
import com.github.javaparser.resolution.types.ResolvedArrayType
import com.github.javaparser.resolution.types.ResolvedReferenceType
import com.github.javaparser.resolution.types.ResolvedWildcard
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FuzzySymbolSolverChart1fTest : FuzzySymbolSolverTest() {

    override val project = TestProjects.Chart1f

    @Test
    fun `Solve For ObjectCreationExpr on Raw Type`() {
        val cu = project.parseSourceFile(
            "source/org/jfree/chart/LegendItemCollection.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .defaultConstructor.get()
            .body
            .statements[0]
            .asExpressionStmt()
            .expression
            .asAssignExpr()
            .value
            .asObjectCreationExpr()
        assumeTrue { node.toString() == "new java.util.ArrayList()" }

        val resolvedType = symbolSolver.calculateType(node)
        assertResolvedTypeIs<ResolvedReferenceType>(resolvedType)
        assertEquals("java.util.ArrayList", resolvedType.qualifiedName)

        assertEquals(1, resolvedType.typeParametersValues().size)

        val tp0 = resolvedType.typeParametersValues()[0]
        assertResolvedTypeIs<ResolvedWildcard>(tp0)
        assertIsUnbounded(tp0)
    }

    @Test
    fun `Resolve NameExpr as ResolvedReferencedTypeDeclaration`() {
        val cu = project.parseSourceFile(
            "tests/org/jfree/chart/renderer/category/junit/AbstractCategoryItemRendererTests.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("testEquals").single()
            .body.get()
            .statements[7]
            .asExpressionStmt()
            .expression
            .asMethodCallExpr()
            .arguments[0]
            .asObjectCreationExpr()
            .arguments[1]
            .asMethodCallExpr()
            .scope.get()
            .asNameExpr()
        assumeTrue { node.toString() == "NumberFormat" }

        val resolvedDecl = symbolSolver.resolveDeclaration(node, ResolvedDeclaration::class.java)
        assertIs<ResolvedReferenceTypeDeclaration>(resolvedDecl)
        assertEquals("java.text.NumberFormat", resolvedDecl.qualifiedName)
    }

    @Test
    fun `Implement Concrete Solving of Generic Array Types`() {
        val cu = project.parseSourceFile(
            "source/org/jfree/chart/plot/Marker.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("getListeners", "Class").single()
            .body.get()
            .statements[0]
            .asReturnStmt()
            .expression.get()
            .asMethodCallExpr()
        assumeTrue { node.toString() == "this.listenerList.getListeners(listenerType)" }

        val type = symbolSolver.calculateType(node)
        assertResolvedTypeIs<ResolvedArrayType>(type)
        val componentType = type.componentType
        assertResolvedTypeIs<ResolvedReferenceType>(componentType)
        assertEquals("java.util.EventListener", componentType.qualifiedName)
    }

    @Test
    fun `Calculate Type of CastExpr`() {
        val cu = project.parseSourceFile(
            "source/org/jfree/data/statistics/DefaultMultiValueCategoryDataset.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("getValues", "Comparable", "Comparable").single()
            .body.get()
            .statements[0]
            .asReturnStmt()
            .expression.get()
            .asMethodCallExpr()
            .arguments[0]
            .asCastExpr()
        assumeTrue { node.toString() == "(List) this.data.getObject(rowKey, columnKey)" }

        val type = symbolSolver.calculateType(node)
        assertResolvedTypeIs<ResolvedReferenceType>(type)
        assertEquals("java.util.List", type.qualifiedName)
        assertEquals(1, type.typeParametersValues().size)
        val typeTp0 = type.typeParametersValues()[0]
        assertResolvedTypeIs<ResolvedWildcard>(typeTp0)
        assertIsUnbounded(typeTp0)
    }

    @Test
    fun `Calculate Type of ClassExpr`() {
        // TimeSeries.java:912
        val cu = project.parseSourceFile(
            "source/org/jfree/data/time/TimeSeries.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("removeAgedItems", "long", "boolean").single()
            .body.get()
            .statements[2]
            .asTryStmt()
            .tryBlock
            .statements[0]
            .asExpressionStmt()
            .expression
            .asVariableDeclarationExpr()
            .variables.single()
            .initializer.get()
            .asMethodCallExpr()
            .arguments[1]
            .asArrayCreationExpr()
            .initializer.get()
            .values[0]
            .asClassExpr()
        assumeTrue { node.toString() == "Class.class" }

        val type = symbolSolver.calculateType(node)
        assertResolvedTypeIs<ResolvedReferenceType>(type)
        assertEquals("java.lang.Class", type.qualifiedName)
        assertEquals(1, type.typeParametersValues().size)
        val typeTp0 = type.typeParametersValues()[0]
        assertResolvedTypeIs<ResolvedReferenceType>(typeTp0)
        assertEquals("java.lang.Class", typeTp0.qualifiedName)
    }

    @Test
    fun `Calculate Type of ArrayCreationExpr`() {
        // TimeSeries.java:912
        val cu = project.parseSourceFile(
            "source/org/jfree/data/time/RegularTimePeriod.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("createInstance", "Class", "Date", "TimeZone").single()
            .body.get()
            .statements[1]
            .asTryStmt()
            .tryBlock
            .statements[0]
            .asExpressionStmt()
            .expression
            .asVariableDeclarationExpr()
            .variables.single()
            .initializer.get()
            .asMethodCallExpr()
            .arguments[0]
            .asArrayCreationExpr()
        assumeTrue { node.toString() == "new Class[] { Date.class, TimeZone.class }" }

        val type = symbolSolver.calculateType(node)
        assertResolvedTypeIs<ResolvedArrayType>(type)
        val componentType = type.componentType
        assertResolvedTypeIs<ResolvedReferenceType>(componentType)
        assertEquals("java.lang.Class", componentType.qualifiedName)
        assertEquals(1, componentType.typeParametersValues().size)
        val componentTypeTp0 = componentType.typeParametersValues()[0]
        assertResolvedTypeIs<ResolvedWildcard>(componentTypeTp0)
        assertIsUnbounded(componentTypeTp0)
    }
}