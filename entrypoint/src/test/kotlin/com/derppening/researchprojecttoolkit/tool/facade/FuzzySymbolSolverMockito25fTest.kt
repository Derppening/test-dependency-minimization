package com.derppening.researchprojecttoolkit.tool.facade

import com.derppening.researchprojecttoolkit.tool.TestProjects
import com.derppening.researchprojecttoolkit.tool.assertIsUnbounded
import com.derppening.researchprojecttoolkit.tool.assertResolvedTypeIs
import com.derppening.researchprojecttoolkit.util.boundedTypes
import com.derppening.researchprojecttoolkit.util.elements
import com.derppening.researchprojecttoolkit.util.resolveDeclaration
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration
import com.github.javaparser.resolution.types.ResolvedIntersectionType
import com.github.javaparser.resolution.types.ResolvedReferenceType
import com.github.javaparser.resolution.types.ResolvedWildcard
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FuzzySymbolSolverMockito25fTest : FuzzySymbolSolverTest() {

    override val project = TestProjects.Mockito25f

    @Test
    fun `Recursively Expand Type Variables Used in Argument`() {
        val cu = project.parseSourceFile(
            "test/org/mockito/internal/stubbing/defaultanswers/ReturnsGenericDeepStubsTest.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("as_expected_fail_with_a_CCE_on_callsite_when_erasure_takes_place_for_example___StringBuilder_is_subject_to_erasure").single()
            .body.get()
            .statements[1]
            .asExpressionStmt()
            .expression
            .asVariableDeclarationExpr()
            .variables.single()
            .initializer.get()
            .asMethodCallExpr()

        assumeTrue { node.toString() == "mock.twoTypeParams(new StringBuilder()).append(2).append(3)" }

        val scopeAppendNode = node.scope.get()
            .asMethodCallExpr()
        val scopeTwoTypeParamsNode = scopeAppendNode.scope.get()
            .asMethodCallExpr()
        val scopeMockNode = scopeTwoTypeParamsNode.scope.get()
            .asNameExpr()

        val scopeMockType = symbolSolver.calculateType(scopeMockNode)
        assertResolvedTypeIs<ResolvedReferenceType>(scopeMockType)
        assertEquals("org.mockito.internal.stubbing.defaultanswers.ReturnsGenericDeepStubsTest.GenericsNest", scopeMockType.qualifiedName)
        assertEquals(1, scopeMockType.typeParametersValues().size)
        val mockNodeTypeTp0 = scopeMockType.typeParametersValues()[0]
        assertResolvedTypeIs<ResolvedWildcard>(mockNodeTypeTp0)
        assertIsUnbounded(mockNodeTypeTp0)

        val scopeTwoTypeParamsType = symbolSolver.calculateType(scopeTwoTypeParamsNode)
        assertResolvedTypeIs<ResolvedReferenceType>(scopeTwoTypeParamsType)
        assertEquals("java.lang.StringBuilder", scopeTwoTypeParamsType.qualifiedName)

        val scopeAppendType = symbolSolver.calculateType(scopeAppendNode)
        assertResolvedTypeIs<ResolvedReferenceType>(scopeAppendType)
        assertEquals("java.lang.StringBuilder", scopeAppendType.qualifiedName)

        val type = symbolSolver.calculateType(node)
        assertResolvedTypeIs<ResolvedReferenceType>(type)
        assertEquals("java.lang.StringBuilder", type.qualifiedName)
    }

    @Test
    fun `Solve Wildcard for Intersection Types`() {
        val cu = project.parseSourceFile(
            "test/org/mockito/internal/stubbing/defaultanswers/ReturnsGenericDeepStubsTest.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("will_return_default_value_on_non_mockable_nested_generic").single()
            .body.get()
            .statements[2]
            .asExpressionStmt()
            .expression
            .asMethodCallExpr()
            .scope.get()
            .asMethodCallExpr()
            .arguments[0]
            .asMethodCallExpr()
            .scope.get()
            .asMethodCallExpr()
            .scope.get()
            .asMethodCallExpr()
            .scope.get()
            .asMethodCallExpr()
        assumeTrue { node.toString() == "genericsNest.returningNonMockableNestedGeneric()" }

        val scopeNode = node.scope.get()
            .asNameExpr()

        val scopeType = symbolSolver.calculateTypeInternal(scopeNode)
        assertResolvedTypeIs<ResolvedReferenceType>(scopeType)
        assertEquals("org.mockito.internal.stubbing.defaultanswers.ReturnsGenericDeepStubsTest.GenericsNest", scopeType.qualifiedName)
        assertEquals(1, scopeType.typeParametersValues().size)
        val scopeTypeTp0 = scopeType.typeParametersValues()[0]
        assertResolvedTypeIs<ResolvedWildcard>(scopeTypeTp0)
        assertIsUnbounded(scopeTypeTp0)

        val type = symbolSolver.calculateTypeInternal(node)
        assertResolvedTypeIs<ResolvedReferenceType>(type)
        assertEquals("java.util.Map", type.qualifiedName)
        assertEquals(2, type.typeParametersValues().size)
        val typeTp0 = type.typeParametersValues()[0]
        assertResolvedTypeIs<ResolvedReferenceType>(typeTp0)
        assertEquals("java.lang.String", typeTp0.qualifiedName)
        val typeTp1 = type.typeParametersValues()[1]
        assertResolvedTypeIs<ResolvedWildcard>(typeTp1)
        assertTrue(typeTp1.isBounded)
        assertTrue(typeTp1.isExtends)
        val typeTp1Bounds = typeTp1.boundedTypes
            .also { assertEquals(2, it.size) }
        val typeTp1Bound0 = typeTp1Bounds[0]
        assertResolvedTypeIs<ResolvedReferenceType>(typeTp1Bound0)
        assertEquals("java.lang.Comparable", typeTp1Bound0.qualifiedName)
        assertEquals(1, typeTp1Bound0.typeParametersValues().size)
        val typeTp1Bound0Tp0 = typeTp1Bound0.typeParametersValues()[0]
        assertResolvedTypeIs<ResolvedWildcard>(typeTp1Bound0Tp0)
        assertIsUnbounded(typeTp1Bound0Tp0)
        val typeTp1Bound1 = typeTp1Bounds[1]
        assertResolvedTypeIs<ResolvedReferenceType>(typeTp1Bound1)
        assertEquals("java.lang.Cloneable", typeTp1Bound1.qualifiedName)
    }

    @Test
    fun `Reduction Tp with Tp and Non-Tp-Intersection Types`() {
        val cu = project.parseSourceFile(
            "test/org/mockito/internal/stubbing/defaultanswers/ReturnsGenericDeepStubsTest.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("can_create_mock_from_multiple_type_variable_bounds_when_return_type_of_parameterized_method_is_a_typevar_that_is_referencing_a_typevar_on_class").single()
            .body.get()
            .statements[1]
            .asExpressionStmt()
            .expression
            .asVariableDeclarationExpr()
            .variables.single()
            .initializer.get()
            .asCastExpr()
            .expression
            .asMethodCallExpr()
        assumeTrue { node.toString() == "mock.typeVarWithTypeParams()" }

        val type = symbolSolver.calculateType(node)
        assertResolvedTypeIs<ResolvedIntersectionType>(type)
        val typeBounds = type.elements
        assertEquals(2, typeBounds.size)
        val typeBound0 = typeBounds[0]
        assertResolvedTypeIs<ResolvedReferenceType>(typeBound0)
        assertEquals("java.lang.Comparable", typeBound0.qualifiedName)
        assertEquals(1, typeBound0.typeParametersValues().size)
        val typeBound0Tp0 = typeBound0.typeParametersValues()[0]
        assertResolvedTypeIs<ResolvedWildcard>(typeBound0Tp0)
        assertIsUnbounded(typeBound0Tp0)

        val typeBound1 = typeBounds[1]
        assertResolvedTypeIs<ResolvedReferenceType>(typeBound1)
        assertEquals("java.lang.Cloneable", typeBound1.qualifiedName)
    }

    @Test
    fun `isMethodMoreSpecific for Vararg-ending Method Differing in Number of Args`() {
        val cu = project.parseSourceFile(
            "src/org/mockito/internal/creation/jmock/SearchingClassLoader.java",
            symbolSolver
        )

        val methodA = cu.primaryType.get()
            .getMethodsBySignature("combineLoadersOf", "Class<?>").single()
        val methodB = cu.primaryType.get()
            .getMethodsBySignature("combineLoadersOf", "Class<?>", "Class<?>").single()

        val resolvedMethodA = symbolSolver.resolveDeclaration<ResolvedMethodDeclaration>(methodA)
        val resolvedMethodB = symbolSolver.resolveDeclaration<ResolvedMethodDeclaration>(methodB)

        assertFalse(symbolSolver.isMethodMoreSpecific(resolvedMethodA, resolvedMethodB))
        assertTrue(symbolSolver.isMethodMoreSpecific(resolvedMethodB, resolvedMethodA))
    }
}