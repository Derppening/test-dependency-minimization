package com.derppening.researchprojecttoolkit.tool.facade

import com.derppening.researchprojecttoolkit.tool.TestProjects
import com.derppening.researchprojecttoolkit.tool.assertResolvedTypeIs
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.resolution.types.ResolvedReferenceType
import com.github.javaparser.resolution.types.ResolvedTypeVariable
import com.github.javaparser.resolution.types.ResolvedWildcard
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FuzzySymbolSolverCollections27fTest : FuzzySymbolSolverTest() {

    override val project = TestProjects.Collections27f

    @Test
    fun `Preserve Type Variables Declared in Expression Container`() {
        val cu = project.parseSourceFile(
            "src/main/java/org/apache/commons/collections4/CollectionUtils.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("forAllButLastDo", "Iterable<T>", "C").single()
            .body.get()
            .statements[0]
            .asReturnStmt()
            .expression.get()
            .asConditionalExpr()
            .thenExpr
            .asMethodCallExpr()
        assumeTrue { node.toString() == "IterableUtils.forEachButLast(collection, closure)" }

        val arg0Node = node.arguments[0].asNameExpr()
        val arg0Type = symbolSolver.calculateType(arg0Node)
        assertResolvedTypeIs<ResolvedReferenceType>(arg0Type)
        assertEquals("java.lang.Iterable", arg0Type.qualifiedName)
        assertEquals(1, arg0Type.typeParametersValues().size)
        val arg0TypeTp0 = arg0Type.typeParametersValues()[0]
            .let { assertResolvedTypeIs<ResolvedTypeVariable>(it).asTypeParameter() }
        assertEquals("T", arg0TypeTp0.name)
        assertEquals("org.apache.commons.collections4.CollectionUtils.forAllButLastDo(java.lang.Iterable<T>, C)", arg0TypeTp0.containerQualifiedName)

        val arg1Node = node.arguments[1].asNameExpr()
        val arg1InternalType = symbolSolver.calculateTypeInternal(arg1Node)
            .let { assertResolvedTypeIs<ResolvedTypeVariable>(it).asTypeParameter() }
        assertEquals("C", arg1InternalType.name)
        assertEquals("org.apache.commons.collections4.CollectionUtils.forAllButLastDo(java.lang.Iterable<T>, C)", arg1InternalType.containerQualifiedName)

        val arg1Type = symbolSolver.calculateType(arg1Node)
        assertResolvedTypeIs<ResolvedReferenceType>(arg1Type)
        assertEquals("org.apache.commons.collections4.Closure", arg1Type.qualifiedName)
        assertEquals(1, arg1Type.typeParametersValues().size)
        val arg1TypeTp0 = arg1Type.typeParametersValues()[0]
        assertResolvedTypeIs<ResolvedWildcard>(arg1TypeTp0)
        assertTrue(arg1TypeTp0.isBounded)
        assertTrue(arg1TypeTp0.isSuper)
        val arg1TypeTp0Bound = arg1TypeTp0.boundedType
            .let { assertResolvedTypeIs<ResolvedTypeVariable>(it).asTypeParameter() }
        assertEquals("T", arg1TypeTp0Bound.name)
        assertEquals("org.apache.commons.collections4.CollectionUtils.forAllButLastDo(java.lang.Iterable<T>, C)", arg1TypeTp0Bound.containerQualifiedName)

        val internalType = symbolSolver.calculateTypeInternal(node)
            .let { assertResolvedTypeIs<ResolvedTypeVariable>(it).asTypeParameter() }
        assertEquals("T", internalType.name)
        assertEquals("org.apache.commons.collections4.CollectionUtils.forAllButLastDo(java.lang.Iterable<T>, C)", internalType.containerQualifiedName)

        val type = symbolSolver.calculateType(node)
        assertResolvedTypeIs<ResolvedReferenceType>(type)
        assertTrue(type.isJavaLangObject)
    }

    @Test
    fun `Flatten Type of Anon Class when Solving This Type`() {
        val cu = project.parseSourceFile(
            "src/main/java/org/apache/commons/collections4/map/ListOrderedMap.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .members
            .single { it is ClassOrInterfaceDeclaration && it.nameAsString == "ValuesView" }
            .asClassOrInterfaceDeclaration()
            .getMethodsBySignature("iterator").single()
            .body.get()
            .statements[0].asReturnStmt()
            .expression.get().asObjectCreationExpr()
            .anonymousClassBody.get()
            .single { it is MethodDeclaration && it.nameAsString == "next" && it.parameters.isEmpty() }
            .asMethodDeclaration()
            .body.get()
            .statements[0].asReturnStmt()
            .expression.get().asMethodCallExpr()
        assumeTrue { node.toString() == "getIterator().next().getValue()" }

        val nodeScope = node.scope.get().asMethodCallExpr()
        val baseScope = nodeScope.scope.get().asMethodCallExpr()

        val thisType = symbolSolver.getTypeOfThisIn(node, true)
        assertResolvedTypeIs<ResolvedReferenceType>(thisType)
        assertEquals("org.apache.commons.collections4.iterators.AbstractUntypedIteratorDecorator", thisType.qualifiedName)
        assertEquals(2, thisType.typeParametersValues().size)
        val thisTypeTp0 = thisType.typeParametersValues()[0]
        assertResolvedTypeIs<ResolvedReferenceType>(thisTypeTp0)
        assertEquals("java.util.Map.Entry", thisTypeTp0.qualifiedName)
        assertEquals(2, thisTypeTp0.typeParametersValues().size)
        val thisTypeTp0Tp0 = thisTypeTp0.typeParametersValues()[0]
        assertResolvedTypeIs<ResolvedReferenceType>(thisTypeTp0Tp0)
        assertTrue(thisTypeTp0Tp0.isJavaLangObject)
        val thisTypeTp0Tp1 = thisTypeTp0.typeParametersValues()[1]
            .let { assertResolvedTypeIs<ResolvedTypeVariable>(it).asTypeParameter() }
        assertEquals("V", thisTypeTp0Tp1.name)
        assertEquals("org.apache.commons.collections4.map.ListOrderedMap.ValuesView", thisTypeTp0Tp1.containerQualifiedName)

        val baseScopeType = symbolSolver.calculateTypeInternal(baseScope)
        assertResolvedTypeIs<ResolvedReferenceType>(baseScopeType)
        assertEquals("java.util.Iterator", baseScopeType.qualifiedName)
        assertEquals(1, baseScopeType.typeParametersValues().size)
        val baseScopeTypeTp0 = baseScopeType.typeParametersValues()[0]
        assertResolvedTypeIs<ResolvedReferenceType>(baseScopeTypeTp0)
        assertEquals("java.util.Map.Entry", baseScopeTypeTp0.qualifiedName)
        assertEquals(2, baseScopeTypeTp0.typeParametersValues().size)
        val baseScopeTypeTp0Tp0 = baseScopeTypeTp0.typeParametersValues()[0]
        assertResolvedTypeIs<ResolvedReferenceType>(baseScopeTypeTp0Tp0)
        assertTrue(baseScopeTypeTp0Tp0.isJavaLangObject)
        val baseScopeTypeTp0Tp1 = baseScopeTypeTp0.typeParametersValues()[1]
            .let { assertResolvedTypeIs<ResolvedTypeVariable>(it).asTypeParameter() }
        assertEquals("V", baseScopeTypeTp0Tp1.name)
        assertEquals("org.apache.commons.collections4.map.ListOrderedMap.ValuesView", baseScopeTypeTp0Tp1.containerQualifiedName)

        val nodeScopeType = symbolSolver.calculateTypeInternal(nodeScope)
        assertResolvedTypeIs<ResolvedReferenceType>(nodeScopeType)
        assertEquals("java.util.Map.Entry", nodeScopeType.qualifiedName)
        assertEquals(2, nodeScopeType.typeParametersValues().size)
        val nodeScopeTypeTp0 = baseScopeTypeTp0.typeParametersValues()[0]
        assertResolvedTypeIs<ResolvedReferenceType>(nodeScopeTypeTp0)
        assertTrue(nodeScopeTypeTp0.isJavaLangObject)
        val nodeScopeTypeTp1 = baseScopeTypeTp0.typeParametersValues()[1]
            .let { assertResolvedTypeIs<ResolvedTypeVariable>(it).asTypeParameter() }
        assertEquals("V", nodeScopeTypeTp1.name)
        assertEquals("org.apache.commons.collections4.map.ListOrderedMap.ValuesView", nodeScopeTypeTp1.containerQualifiedName)

        val nodeType = symbolSolver.calculateTypeInternal(node)
            .let { assertResolvedTypeIs<ResolvedTypeVariable>(it).asTypeParameter() }
        assertEquals("V", nodeType.name)
        assertEquals("org.apache.commons.collections4.map.ListOrderedMap.ValuesView", nodeType.containerQualifiedName)
    }

    @Test
    fun `Solve Type of NameExpr where Variable Decl Type is Fully Qualified`() {
        val cu = project.parseSourceFile(
            "src/test/java/org/apache/commons/collections4/list/CursorableLinkedListTest.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("testContainsAll").single()
            .body.get()
            .statements[3].asExpressionStmt()
            .expression.asMethodCallExpr()
            .scope.get().asNameExpr()
        assumeTrue { node.toString() == "list2" }

        val nodeType = symbolSolver.calculateTypeInternal(node)
        assertResolvedTypeIs<ResolvedReferenceType>(nodeType)
    }
}