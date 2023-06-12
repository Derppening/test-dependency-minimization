package com.derppening.researchprojecttoolkit.tool.facade

import com.derppening.researchprojecttoolkit.tool.TestProjects
import com.derppening.researchprojecttoolkit.tool.assertResolvedTypeIs
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.resolution.declarations.ResolvedAnnotationMemberDeclaration
import com.github.javaparser.resolution.declarations.ResolvedConstructorDeclaration
import com.github.javaparser.resolution.declarations.ResolvedDeclaration
import com.github.javaparser.resolution.types.ResolvedReferenceType
import com.github.javaparser.resolution.types.ResolvedWildcard
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FuzzySymbolSolverGson3fTest : FuzzySymbolSolverTest() {

    override val project = TestProjects.Gson3f

    @Test
    fun `Solve Type of Member in Annotation Class`() {
        val cu = project.parseSourceFile(
            "gson/src/main/java/com/google/gson/internal/bind/TypeAdapters.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .members
            .single { it is ClassOrInterfaceDeclaration && it.nameAsString == "EnumTypeAdapter" }
            .asClassOrInterfaceDeclaration()
            .getConstructorByParameterTypes("Class<T>").get()
            .body
            .statements[0]
            .asTryStmt()
            .tryBlock
            .statements[0]
            .asForEachStmt()
            .body
            .asBlockStmt()
            .statements[2]
            .asIfStmt()
            .thenStmt
            .asBlockStmt()
            .statements[0]
            .asExpressionStmt()
            .expression
            .asAssignExpr()
            .value
            .asMethodCallExpr()
        assumeTrue { node.toString() == "annotation.value()" }

        val type = symbolSolver.calculateType(node)
        assertResolvedTypeIs<ResolvedReferenceType>(type)
        assertEquals("java.lang.String", type.qualifiedName)
    }

    @Test
    fun `Solve Declaration of Member in Annotation Class`() {
        val cu = project.parseSourceFile(
            "gson/src/main/java/com/google/gson/internal/bind/TypeAdapters.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .members
            .single { it is ClassOrInterfaceDeclaration && it.nameAsString == "EnumTypeAdapter" }
            .asClassOrInterfaceDeclaration()
            .getConstructorByParameterTypes("Class<T>").get()
            .body
            .statements[0]
            .asTryStmt()
            .tryBlock
            .statements[0]
            .asForEachStmt()
            .body
            .asBlockStmt()
            .statements[2]
            .asIfStmt()
            .thenStmt
            .asBlockStmt()
            .statements[0]
            .asExpressionStmt()
            .expression
            .asAssignExpr()
            .value
            .asMethodCallExpr()
        assumeTrue { node.toString() == "annotation.value()" }

        val decl = symbolSolver.resolveDeclaration(node, ResolvedDeclaration::class.java)
        assertIs<ResolvedAnnotationMemberDeclaration>(decl)
        assertEquals("value", decl.name)
        val declType = decl.type
        assertResolvedTypeIs<ResolvedReferenceType>(declType)
        assertEquals("java.lang.String", declType.qualifiedName)
    }

    @Test
    fun `Solve Specificity for NullType`() {
        val cu = project.parseSourceFile(
            "gson/src/test/java/com/google/gson/functional/MapTest.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("testMapSerializationWithWildcardValues").single()
            .body.get()
            .statements[1]
            .asExpressionStmt()
            .expression
            .asMethodCallExpr()
        assumeTrue { node.toString() == "map.put(\"test\", null)" }

        val type = symbolSolver.calculateTypeInternal(node)

        assertResolvedTypeIs<ResolvedWildcard>(type)
        assertTrue(type.isBounded)
        assertTrue(type.isExtends)
        val typeBound = type.boundedType
        assertResolvedTypeIs<ResolvedReferenceType>(typeBound)
        assertEquals("java.util.Collection", typeBound.qualifiedName)
        assertEquals(1, typeBound.typeParametersValues().size)
        val typeBoundTp0 = typeBound.typeParametersValues()[0]
        assertResolvedTypeIs<ResolvedWildcard>(typeBoundTp0)
        assertTrue(typeBoundTp0.isBounded)
        assertTrue(typeBoundTp0.isExtends)
        val typeBoundTp0Bound = typeBoundTp0.boundedType
        assertResolvedTypeIs<ResolvedReferenceType>(typeBoundTp0Bound)
        assertEquals("java.lang.Integer", typeBoundTp0Bound.qualifiedName)
    }

    @Test
    fun `Workaround Mismatching Parameter vs Argument Bounds with Singular Candidate`() {
        val cu = project.parseSourceFile(
            "gson/src/main/java/com/google/gson/internal/bind/ArrayTypeAdapter.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getFieldByName("FACTORY").get()
            .variables.single()
            .initializer.get()
            .asObjectCreationExpr()
            .anonymousClassBody.get()
            .single {
                it is MethodDeclaration &&
                        it.nameAsString == "create" &&
                        it.parameters.size == 2 &&
                        it.parameters[0].typeAsString == "Gson" &&
                        it.parameters[1].typeAsString == "TypeToken<T>"
            }
            .asMethodDeclaration()
            .body.get()
            .statements[4]
            .asReturnStmt()
            .expression.get()
            .asObjectCreationExpr()
        assumeTrue { node.toString() == "new ArrayTypeAdapter(gson, componentTypeAdapter, \$Gson\$Types.getRawType(componentType))" }

        val decl = symbolSolver.resolveDeclaration(node, ResolvedDeclaration::class.java)
        assertIs<ResolvedConstructorDeclaration>(decl)
        assertEquals("ArrayTypeAdapter", decl.className)
        assertEquals("com.google.gson.internal.bind", decl.packageName)
        assertEquals(3, decl.numberOfParams)
        val declParam0 = decl.getParam(0).type
        assertResolvedTypeIs<ResolvedReferenceType>(declParam0)
        assertEquals("com.google.gson.Gson", declParam0.qualifiedName)
        val declParam1 = decl.getParam(1).type
        assertResolvedTypeIs<ResolvedReferenceType>(declParam1)
        assertEquals("com.google.gson.TypeAdapter", declParam1.qualifiedName)
        val declParam2 = decl.getParam(2).type
        assertResolvedTypeIs<ResolvedReferenceType>(declParam2)
        assertEquals("java.lang.Class", declParam2.qualifiedName)
    }
}