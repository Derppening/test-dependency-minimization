package com.derppening.researchprojecttoolkit.tool.facade

import com.derppening.researchprojecttoolkit.tool.TestProjects
import com.derppening.researchprojecttoolkit.tool.assertResolvedTypeIs
import com.github.javaparser.resolution.types.ResolvedArrayType
import com.github.javaparser.resolution.types.ResolvedReferenceType
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FuzzySymbolSolverMockito1fTest : FuzzySymbolSolverTest() {

    override val project = TestProjects.Mockito1f

    @Test
    fun `Resolve type parameter with no inference source`() {
        val cu = project.parseSourceFile(
            "test/org/mockitousage/bugs/varargs/VarargsNotPlayingWithAnyObjectTest.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("shouldNotAllowUsingAnyObjectForVarArgs").single()
            .body.get()
            .statements[1]
            .asTryStmt()
            .tryBlock
            .statements[0]
            .asExpressionStmt()
            .expression
            .asMethodCallExpr()
            .arguments[0]
            .asCastExpr()
            .expression
            .asMethodCallExpr()
        assumeTrue { node.toString() == "anyObject()" }

        val type = symbolSolver.calculateType(node)
            .also { it.isReferenceType }
            .asReferenceType()
        assertTrue(type.isJavaLangObject)
    }

    @Test
    fun `Concretely solve method returning array type`() {
        val cu = project.parseSourceFile(
            "src/org/mockito/internal/util/reflection/GenericMetadataSupport.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("registerTypeVariablesOn", "Type").single()
            .body.get()
            .statements[2]
            .asExpressionStmt()
            .expression
            .asVariableDeclarationExpr()
            .variables
            .single()
            .initializer.get()
            .asMethodCallExpr()
        assumeTrue { node.toString() == "((Class<?>) parameterizedType.getRawType()).getTypeParameters()" }

        val type = symbolSolver.calculateType(node)
        assertResolvedTypeIs<ResolvedArrayType>(type)
        val componentType = assertIs<ResolvedReferenceType>(type.componentType)
        assertEquals("java.lang.reflect.TypeVariable", componentType.qualifiedName)
        val componentTypeTp = componentType.typeParametersValues().single()
        assertResolvedTypeIs<ResolvedReferenceType>(componentTypeTp)
        assertEquals("java.lang.Class", componentTypeTp.qualifiedName)
    }

    @Test
    fun `Solve Scope NameExpr as Type`() {
        val cu = project.parseSourceFile(
            "test/org/mockitousage/configuration/SmartMock.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getAnnotationByName("Retention").get()
            .asSingleMemberAnnotationExpr()
            .memberValue
            .asFieldAccessExpr()
            .scope
            .asNameExpr()
        assumeTrue { node.toString() == "RetentionPolicy" }

        val type = symbolSolver.calculateType(node)
        assertResolvedTypeIs<ResolvedReferenceType>(type)
        assertEquals("java.lang.annotation.RetentionPolicy", type.qualifiedName)
    }
}