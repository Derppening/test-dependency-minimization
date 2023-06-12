package com.derppening.researchprojecttoolkit.tool.facade

import com.derppening.researchprojecttoolkit.tool.TestProjects
import com.derppening.researchprojecttoolkit.tool.assertIsUnbounded
import com.derppening.researchprojecttoolkit.tool.assertResolvedTypeIs
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.resolution.types.ResolvedReferenceType
import com.github.javaparser.resolution.types.ResolvedTypeVariable
import com.github.javaparser.resolution.types.ResolvedWildcard
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FuzzySymbolSolverGson1fTest : FuzzySymbolSolverTest() {

    override val project = TestProjects.Gson1f

    @Test
    fun `Regression for Solving Specificity`() {
        val cu = project.parseSourceFile(
            "gson/src/main/java/com/google/gson/DefaultTypeAdapters.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .members
            .single { it is ClassOrInterfaceDeclaration && it.nameAsString == "EnumTypeAdapter" }
            .asClassOrInterfaceDeclaration()
            .getMethodsBySignature("deserialize", "JsonElement", "Type", "JsonDeserializationContext").single()
            .body.get()
            .statements[0]
            .asReturnStmt()
            .expression.get()
            .asCastExpr()
            .expression
            .asMethodCallExpr()
        assumeTrue { node.toString() == "Enum.valueOf((Class<T>) classOfT, json.getAsString())" }

        val type = symbolSolver.calculateType(node)
        assertResolvedTypeIs<ResolvedReferenceType>(type)
        assertTrue(type.isJavaLangEnum)
        assertEquals(1, type.typeParametersValues().size)
        val typeTp0 = type.typeParametersValues()[0]
            .let { assertResolvedTypeIs<ResolvedTypeVariable>(it).asTypeParameter() }
        assertEquals("T", typeTp0.name)
        assertEquals("com.google.gson.DefaultTypeAdapters.EnumTypeAdapter", typeTp0.containerQualifiedName)
    }

    @Test
    fun `Use Scope for Solving Generics If Available`() {
        val cu = project.parseSourceFile(
            "gson/src/main/java/com/google/gson/ParameterizedTypeHandlerMap.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("getHandlerForTypeHierarchy", "Class<?>").single()
            .body.get()
            .statements[0]
            .asForEachStmt()
            .body.asBlockStmt()
            .statements[0].asIfStmt()
            .condition.asMethodCallExpr()
            .scope.get().asFieldAccessExpr()
        assumeTrue { node.toString() == "entry.first" }

        val scope = node.scope.asNameExpr()

        val scopeType = symbolSolver.calculateTypeInternal(scope)
        assertResolvedTypeIs<ResolvedReferenceType>(scopeType)
        assertEquals("com.google.gson.Pair", scopeType.qualifiedName)
        assertEquals(2, scopeType.typeParametersValues().size)
        val scopeTypeTp0 = scopeType.typeParametersValues()[0]
        assertResolvedTypeIs<ResolvedReferenceType>(scopeTypeTp0)
        assertEquals("java.lang.Class", scopeTypeTp0.qualifiedName)
        assertEquals(1, scopeTypeTp0.typeParametersValues().size)
        val scopeTypeTp0Tp0 = scopeTypeTp0.typeParametersValues()[0]
        assertResolvedTypeIs<ResolvedWildcard>(scopeTypeTp0Tp0)
        assertIsUnbounded(scopeTypeTp0Tp0)
        val scopeTypeTp1 = assertResolvedTypeIs<ResolvedTypeVariable>(scopeType.typeParametersValues()[1])
            .asTypeParameter()
        assertEquals("T", scopeTypeTp1.name)
        assertEquals("com.google.gson.ParameterizedTypeHandlerMap", scopeTypeTp1.containerQualifiedName)

        val nodeType = symbolSolver.calculateTypeInternal(node)
        assertResolvedTypeIs<ResolvedReferenceType>(nodeType)
        assertEquals("java.lang.Class", nodeType.qualifiedName)
        assertEquals(1, nodeType.typeParametersValues().size)
        val nodeTypeTp0 = nodeType.typeParametersValues()[0]
        assertResolvedTypeIs<ResolvedWildcard>(nodeTypeTp0)
        assertIsUnbounded(nodeTypeTp0)
    }
}