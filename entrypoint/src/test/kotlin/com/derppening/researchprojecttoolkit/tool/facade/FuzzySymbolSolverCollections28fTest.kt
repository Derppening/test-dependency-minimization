package com.derppening.researchprojecttoolkit.tool.facade

import com.derppening.researchprojecttoolkit.tool.TestProjects
import com.derppening.researchprojecttoolkit.tool.assertIsUnbounded
import com.derppening.researchprojecttoolkit.tool.assertResolvedTypeIs
import com.derppening.researchprojecttoolkit.util.toResolvedType
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.resolution.types.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FuzzySymbolSolverCollections28fTest : FuzzySymbolSolverTest() {

    override val project = TestProjects.Collections28f

    @Test
    fun `Solve Type Name with Scope`() {
        val cu = project.parseSourceFile(
            "src/main/java/org/apache/commons/collections4/multiset/AbstractMapMultiSet.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .members
            .single { it is ClassOrInterfaceDeclaration && it.nameAsString == "MultiSetEntry" }
            .asClassOrInterfaceDeclaration()
            .getMethodsBySignature("getElement").single()
            .body.get()
            .asBlockStmt()
            .statements[0]
            .asReturnStmt()
            .expression.get()
            .asMethodCallExpr()
            .scope.get()
            .asNameExpr()
        assumeTrue { node.toString() == "parentEntry" }

        val type = symbolSolver.calculateType(node)
        assertResolvedTypeIs<ResolvedReferenceType>(type)
        assertEquals("java.util.Map.Entry", type.qualifiedName)
        assertEquals(2, type.typeParametersValues().size)
        val typeTp0 = type.typeParametersValues()[0]
            .let { assertResolvedTypeIs<ResolvedTypeVariable>(it).asTypeParameter() }
        assertEquals("E", typeTp0.name)
        assertEquals("org.apache.commons.collections4.multiset.AbstractMapMultiSet.MultiSetEntry", typeTp0.containerQualifiedName)
        val typeTp1 = type.typeParametersValues()[1]
        assertResolvedTypeIs<ResolvedReferenceType>(typeTp1)
        assertEquals("org.apache.commons.collections4.multiset.AbstractMapMultiSet.MutableInteger", typeTp1.qualifiedName)
    }

    @Test
    fun `Invoke Mention on ResolvedArrayType`() {
        val cu = project.parseSourceFile(
            "src/test/java/org/apache/commons/collections4/collection/AbstractCollectionTest.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("testCollectionToArray2").single()
            .body.get()
            .statements[9]
            .asTryStmt()
            .tryBlock
            .statements[0]
            .asExpressionStmt()
            .expression
            .asAssignExpr()
            .value
            .asMethodCallExpr()
        assumeTrue { node.toString() == "getCollection().toArray(null)" }

        val scopeNode = node.scope.get()
            .asMethodCallExpr()

        val scopeType = symbolSolver.calculateTypeInternal(scopeNode)
        assertResolvedTypeIs<ResolvedReferenceType>(scopeType)
        assertEquals("java.util.Collection", scopeType.qualifiedName)
        assertEquals(1, scopeType.typeParametersValues().size)
        val scopeTypeTp0 = scopeType.typeParametersValues()[0]
            .let { assertResolvedTypeIs<ResolvedTypeVariable>(it).asTypeParameter() }
        assertEquals("E", scopeTypeTp0.name)
        assertEquals("org.apache.commons.collections4.collection.AbstractCollectionTest", scopeTypeTp0.containerQualifiedName)

        val internalType = symbolSolver.calculateTypeInternal(node)
        assertResolvedTypeIs<ResolvedArrayType>(internalType)
        assertEquals(1, internalType.arrayLevel())
        val internalTypeComponent = internalType.componentType
        assertResolvedTypeIs<ResolvedWildcard>(internalTypeComponent)
        assertIsUnbounded(internalTypeComponent)

        val type = symbolSolver.calculateType(node)
        assertResolvedTypeIs<ResolvedArrayType>(type)
        assertEquals(1, type.arrayLevel())
        val typeComponent = type.componentType
        assertResolvedTypeIs<ResolvedReferenceType>(typeComponent)
        assertTrue(typeComponent.isJavaLangObject)
    }

    @Test
    fun `Solve for Java MapEntry`() {
        val cu = project.parseSourceFile(
            "src/main/java/org/apache/commons/collections4/multimap/AbstractMultiValuedMap.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .members
            .single { it is ClassOrInterfaceDeclaration && it.nameAsString == "KeysMultiSet" }
            .asClassOrInterfaceDeclaration()
            .members
            .single { it is ClassOrInterfaceDeclaration && it.nameAsString == "MapEntryTransformer" }
            .asClassOrInterfaceDeclaration()
            .getMethodsBySignature("transform", "Map.Entry<K,Collection<V>>").single()
            .parameters[0]
            .type
        assumeTrue { node.toString() == "Map.Entry<K, Collection<V>>" }

        val type = symbolSolver.toResolvedType<ResolvedType>(node)
        assertResolvedTypeIs<ResolvedReferenceType>(type)
    }
}