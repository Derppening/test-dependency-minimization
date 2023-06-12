package com.derppening.researchprojecttoolkit.tool.facade

import com.derppening.researchprojecttoolkit.tool.TestProjects
import com.derppening.researchprojecttoolkit.tool.assertResolvedTypeIs
import com.derppening.researchprojecttoolkit.tool.assumeTrue
import com.derppening.researchprojecttoolkit.util.resolveDeclaration
import com.github.javaparser.resolution.declarations.ResolvedDeclaration
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration
import com.github.javaparser.resolution.types.ResolvedReferenceType
import com.github.javaparser.resolution.types.ResolvedTypeVariable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FuzzySymbolSolverLang18fTest : FuzzySymbolSolverTest() {

    override val project = TestProjects.Lang18f

    @Test
    fun `Solve Method with Type Parameter in Last Parameter`() {
        val cu = project.parseSourceFile(
            "src/test/java/org/apache/commons/lang3/ValidateTest.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("testNotEmptyCollection1").single()
            .body.get()
            .statements[1].asTryStmt()
            .tryBlock
            .statements[0].asExpressionStmt()
            .expression.asMethodCallExpr()
        assumeTrue { node.toString() == "Validate.notEmpty((Collection<?>) null)" }

        val decl = symbolSolver.resolveDeclaration<ResolvedDeclaration>(node)
        assertIs<ResolvedMethodDeclaration>(decl)
        assertEquals("notEmpty", decl.name)
        val declType = decl.declaringType()
        assertEquals("org.apache.commons.lang3.Validate", declType.qualifiedName)
        assertEquals(1, decl.numberOfParams)
        val declParam0 = assertResolvedTypeIs<ResolvedTypeVariable>(decl.getParam(0).type).asTypeParameter()
        assertEquals("T", declParam0.name)
        assertEquals("org.apache.commons.lang3.Validate.notEmpty(T).T", declParam0.qualifiedName)
        assertEquals(1, declParam0.bounds.size)
        val declParam0Bound = declParam0.bounds.single()
        assertTrue(declParam0Bound.isExtends)
        val declParam0BoundType = assertResolvedTypeIs<ResolvedReferenceType>(declParam0Bound.type)
        assertEquals("java.util.Collection", declParam0BoundType.qualifiedName)
    }

    @Test
    fun `Solve Method in Ancestor of Local Class`() {
        val cu = project.parseSourceFile(
            "src/test/java/org/apache/commons/lang3/concurrent/AbstractConcurrentInitializerTest.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("testGetConcurrent").single()
            .body.get()
            .statements[5].asForStmt()
            .body.asBlockStmt()
            .statements[1].asExpressionStmt()
            .expression.asMethodCallExpr()
        assumeTrue { node.toString() == "threads[i].start()" }

        val decl = symbolSolver.resolveDeclaration<ResolvedDeclaration>(node)
        assertIs<ResolvedMethodDeclaration>(decl)
        assertEquals("start", decl.name)
        val declType = decl.declaringType()
        assertEquals("java.lang.Thread", declType.qualifiedName)
    }
}