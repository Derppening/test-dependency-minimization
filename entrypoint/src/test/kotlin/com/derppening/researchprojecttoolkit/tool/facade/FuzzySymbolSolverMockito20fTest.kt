package com.derppening.researchprojecttoolkit.tool.facade

import com.derppening.researchprojecttoolkit.tool.TestProjects
import com.derppening.researchprojecttoolkit.tool.assertResolvedTypeIs
import com.github.javaparser.resolution.declarations.ResolvedDeclaration
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration
import com.github.javaparser.resolution.types.ResolvedPrimitiveType
import com.github.javaparser.resolution.types.ResolvedReferenceType
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FuzzySymbolSolverMockito20fTest : FuzzySymbolSolverTest() {

    override val project = TestProjects.Mockito20f

    @Test
    fun `Regression for Missing Classpath Element`() {
        val cu = project.parseSourceFile(
            "src/org/mockito/internal/creation/bytebuddy/MockMethodInterceptor.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("interceptSuperCallable", "Object", "Method", "Object[]", "Callable<?>")
            .single()
            .getAnnotationByName("BindingPriority").get()
            .asSingleMemberAnnotationExpr()
            .memberValue
            .asBinaryExpr()
            .left
            .asFieldAccessExpr()
        assumeTrue { node.toString() == "BindingPriority.DEFAULT" }

        val scopeNode = node.scope.asNameExpr()
        val scopeType = symbolSolver.calculateType(scopeNode)
        assertResolvedTypeIs<ResolvedReferenceType>(scopeType)
        assertEquals("net.bytebuddy.instrumentation.method.bytecode.bind.annotation.BindingPriority", scopeType.qualifiedName)

        val decl = symbolSolver.resolveDeclaration(node, ResolvedDeclaration::class.java)
        assertIs<ResolvedValueDeclaration>(decl)
        assertEquals("DEFAULT", decl.name)
        val declType = decl.type
        assertResolvedTypeIs<ResolvedPrimitiveType>(declType)
        assertEquals(ResolvedPrimitiveType.DOUBLE, declType)
    }
}