package com.derppening.researchprojecttoolkit.tool.facade

import com.derppening.researchprojecttoolkit.tool.TestProjects
import com.derppening.researchprojecttoolkit.tool.assertResolvedTypeIs
import com.derppening.researchprojecttoolkit.util.parameters
import com.derppening.researchprojecttoolkit.util.resolveDeclaration
import com.derppening.researchprojecttoolkit.util.toResolvedType
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.resolution.declarations.ResolvedDeclaration
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration
import com.github.javaparser.resolution.types.ResolvedPrimitiveType
import com.github.javaparser.resolution.types.ResolvedReferenceType
import com.github.javaparser.resolution.types.ResolvedType
import com.github.javaparser.resolution.types.ResolvedTypeVariable
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.assertDoesNotThrow
import kotlin.test.*

class FuzzySymbolSolverJacksonDatabind20fTest : FuzzySymbolSolverTest() {

    override val project = TestProjects.JacksonDatabind20f

    @Test
    fun `Resolve Fallback Ambiguous Method between Vararg and No Vararg`() {
        val cu = project.parseSourceFile(
            "src/test/java/com/fasterxml/jackson/databind/ser/TestEnumSerialization.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("testEnumSet").single()
            .body.get()
            .statements[1].asExpressionStmt()
            .expression.asVariableDeclarationExpr()
            .variables.single { it.nameAsString == "value" }
            .initializer.get().asMethodCallExpr()
        assumeTrue { node.toString() == "EnumSet.of(TestEnum.B)" }

        val decl = symbolSolver.resolveMethodCallExprFallback(node)
        assertIs<ResolvedMethodDeclaration>(decl)
        assertEquals("of", decl.name)
        assertEquals("EnumSet", decl.className)
        assertEquals("java.util", decl.packageName)
        assertEquals(1, decl.numberOfParams)
        val declParam0Type = decl.parameters[0].type
            .let { assertResolvedTypeIs<ResolvedTypeVariable>(it).asTypeParameter() }
        assertEquals("E", declParam0Type.name)
    }

    @Test
    fun `Resolve Fallback Ambiguous Method between Vararg and No Vararg 2`() {
        val type = typeSolver.solveType("java.util.EnumSet")
        val method1 = type.declaredMethods
            .single {
                it.name == "of" &&
                        it.numberOfParams == 1 &&
                        it.parameters[0].let { it.type.describe() == "E" && !it.isVariadic}
            }
        val method2 = type.declaredMethods
            .single {
                it.name == "of" &&
                        it.numberOfParams == 2 &&
                        it.parameters[0].let { it.type.describe() == "E" && !it.isVariadic} &&
                        it.parameters[1].let { it.type.describe() == "E[]" && it.isVariadic }
            }

        assertTrue(symbolSolver.isMethodMoreSpecific(method1, method2))
        assertFalse(symbolSolver.isMethodMoreSpecific(method2, method1))
    }

    @Test
    fun `Regression-00`() {
        val cu = project.parseSourceFile(
            "src/main/java/com/fasterxml/jackson/databind/ObjectMapper.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("registerModule", "Module").single()
            .body.get()
            .statements[6].asExpressionStmt()
            .expression.asMethodCallExpr()
            .arguments[0].asObjectCreationExpr()
            .anonymousClassBody.get()
            .single {
                it is MethodDeclaration &&
                        it.nameAsString == "isEnabled" &&
                        it.parameters.size == 1 &&
                        it.parameters[0].typeAsString == "JsonFactory.Feature"
            }
            .asMethodDeclaration()
            .body.get()
            .statements[0].asReturnStmt()
            .expression.get().asMethodCallExpr()
        assumeTrue { node.toString() == "mapper.isEnabled(f)" }

        val decl = symbolSolver.resolveMethodCallExprFallback(node)
        assertIs<ResolvedMethodDeclaration>(decl)
        assertEquals("isEnabled", decl.name)
        assertEquals("ObjectMapper", decl.className)
        assertEquals("com.fasterxml.jackson.databind", decl.packageName)
        assertEquals(1, decl.numberOfParams)
        val declParam0Type = decl.parameters[0].type
        assertResolvedTypeIs<ResolvedReferenceType>(declParam0Type)
        assertEquals("com.fasterxml.jackson.core.JsonFactory.Feature", declParam0Type.qualifiedName)
    }

    @Test
    fun `Regression-01`() {
        val cu = project.parseSourceFile(
            "src/test/java/com/fasterxml/jackson/databind/struct/TestObjectIdDeserialization.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("assertEmployees", "Employee", "Employee").single()
            .parameters[0].type

        val resolvedType = symbolSolver.toResolvedType<ResolvedType>(node)
        assertResolvedTypeIs<ResolvedReferenceType>(resolvedType)
        assertEquals("com.fasterxml.jackson.databind.struct.TestObjectId.Employee", resolvedType.qualifiedName)

        assertDoesNotThrow {
            typeSolver.solveType("com.fasterxml.jackson.databind.struct.TestObjectIdDeserialization")
                .allMethods
        }
    }

    @Test
    fun `Regression-02`() {
        val cu = project.parseSourceFile(
            "src/test/java/com/fasterxml/jackson/databind/module/TestSimpleModule.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .members
            .single { it is ClassOrInterfaceDeclaration && it.nameAsString == "ContextVerifierModule" }
            .asClassOrInterfaceDeclaration()
            .getMethodsBySignature("setupModule", "SetupContext").single()
            .body.get()
            .statements[1].asExpressionStmt()
            .expression.asMethodCallExpr()

        val decl = symbolSolver.resolveDeclaration<ResolvedDeclaration>(node)
        assertIs<ResolvedMethodDeclaration>(decl)
        assertEquals("assertNotNull", decl.name)
        assertEquals("TestCase", decl.className)
        assertEquals("junit.framework", decl.packageName)
        assertEquals(1, decl.numberOfParams)
        val declParam0Type = decl.parameters[0].type
        assertResolvedTypeIs<ResolvedReferenceType>(declParam0Type)
        assertTrue(declParam0Type.isJavaLangObject)
    }

    @Test
    fun `Regression-03`() {
        val cu = project.parseSourceFile(
            "src/test/java/com/fasterxml/jackson/databind/module/TestSimpleModule.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .members
            .single { it is ClassOrInterfaceDeclaration && it.nameAsString == "ContextVerifierModule" }
            .asClassOrInterfaceDeclaration()
            .getMethodsBySignature("setupModule", "SetupContext").single()
            .body.get()
            .statements[2].asExpressionStmt()
            .expression.asMethodCallExpr()

        val decl = symbolSolver.resolveDeclaration<ResolvedDeclaration>(node)
        assertIs<ResolvedMethodDeclaration>(decl)
        assertEquals("assertTrue", decl.name)
        assertEquals("TestCase", decl.className)
        assertEquals("junit.framework", decl.packageName)
        assertEquals(1, decl.numberOfParams)
        val declParam0Type = decl.parameters[0].type
        assertResolvedTypeIs<ResolvedPrimitiveType>(declParam0Type)
        assertEquals(ResolvedPrimitiveType.BOOLEAN, declParam0Type)
    }
}