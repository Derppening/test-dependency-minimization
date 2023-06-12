package com.derppening.researchprojecttoolkit.tool.facade

import com.derppening.researchprojecttoolkit.tool.TestProjects
import com.derppening.researchprojecttoolkit.tool.assertResolvedTypeIs
import com.derppening.researchprojecttoolkit.tool.assumeTrue
import com.derppening.researchprojecttoolkit.util.resolveDeclaration
import com.derppening.researchprojecttoolkit.util.toResolvedType
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.resolution.declarations.ResolvedDeclaration
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration
import com.github.javaparser.resolution.types.ResolvedReferenceType
import com.github.javaparser.resolution.types.ResolvedType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FuzzySymbolSolverLang19fTest : FuzzySymbolSolverTest() {

    override val project = TestProjects.Lang19f

    @Test
    fun `Regression-00`() {
        val cu = project.parseSourceFile(
            "src/test/java/org/apache/commons/lang3/AnnotationUtilsTest.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("testHashCode").single()
            .body.get()
            .statements[1].asExpressionStmt()
            .expression.asMethodCallExpr()
            .arguments[0].asMethodCallExpr()
        assumeTrue { node.toString() == "test.hashCode()" }

        val decl = symbolSolver.resolveDeclaration<ResolvedDeclaration>(node)
        assertIs<ResolvedMethodDeclaration>(decl)
        assertEquals("hashCode", decl.name)
        val declType = decl.declaringType()
        assertEquals("java.lang.Object", declType.qualifiedName)
    }

    @Test
    fun `Solve Nested Type in Java Lang`() {
        val cu = project.parseSourceFile(
            "src/main/java/org/apache/commons/lang3/concurrent/BasicThreadFactory.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .members
            .single { it is ClassOrInterfaceDeclaration && it.nameAsString == "Builder" }.asClassOrInterfaceDeclaration()
            .getMethodsBySignature("uncaughtExceptionHandler", "Thread.UncaughtExceptionHandler").single()
            .parameters[0]
            .type
        assumeTrue { node.toString() == "Thread.UncaughtExceptionHandler" }

        val type = symbolSolver.toResolvedType<ResolvedType>(node)
        assertResolvedTypeIs<ResolvedReferenceType>(type)
        assertEquals("java.lang.Thread.UncaughtExceptionHandler", type.qualifiedName)
    }
}