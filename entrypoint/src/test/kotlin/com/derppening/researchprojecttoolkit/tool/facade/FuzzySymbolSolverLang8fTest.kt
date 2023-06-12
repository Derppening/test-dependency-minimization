package com.derppening.researchprojecttoolkit.tool.facade

import com.derppening.researchprojecttoolkit.tool.TestProjects
import com.derppening.researchprojecttoolkit.tool.assertIsUnbounded
import com.derppening.researchprojecttoolkit.tool.assertResolvedTypeIs
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.resolution.types.ResolvedReferenceType
import com.github.javaparser.resolution.types.ResolvedWildcard
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals

class FuzzySymbolSolverLang8fTest : FuzzySymbolSolverTest() {

    override val project = TestProjects.Lang8f

    @Test
    fun `Do Not Consider Argument Types for Non Method-Declared Type Variables`() {
        val cu = project.parseSourceFile(
            "src/main/java/org/apache/commons/lang3/SerializationUtils.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .members
            .single { it is ClassOrInterfaceDeclaration && it.nameAsString == "ClassLoaderAwareObjectInputStream" }
            .asClassOrInterfaceDeclaration()
            .getConstructorByParameterTypes("InputStream", "ClassLoader").get()
            .body
            .statements[10]
            .asExpressionStmt()
            .expression
            .asMethodCallExpr()
        assumeTrue { node.toString() == "primitiveTypes.put(\"void\", void.class)" }

        val type = symbolSolver.calculateType(node)
        assertResolvedTypeIs<ResolvedReferenceType>(type)
        assertEquals(type.qualifiedName, "java.lang.Class")
        assertEquals(1, type.typeParametersValues().size)
        val typeTp0 = type.typeParametersValues()[0]
        assertResolvedTypeIs<ResolvedWildcard>(typeTp0)
        assertIsUnbounded(typeTp0)
    }
}