package com.derppening.researchprojecttoolkit.tool.facade

import com.derppening.researchprojecttoolkit.tool.TestProjects
import com.derppening.researchprojecttoolkit.tool.assertResolvedTypeIs
import com.github.javaparser.resolution.types.ResolvedArrayType
import com.github.javaparser.resolution.types.ResolvedReferenceType
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class FuzzySymbolSolverCli40fTest : FuzzySymbolSolverTest() {

    override val project = TestProjects.Cli40f

    @Test
    fun `Implement isMoreSpecific for Array and Wildcard`() {
        val cu = project.parseSourceFile(
            "src/test/java/org/apache/commons/cli/TypeHandlerTest.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("testCreateValueFiles").single()
            .body.get()
            .statements[0]
            .asExpressionStmt()
            .expression
            .asMethodCallExpr()
        assumeTrue { node.toString() == "TypeHandler.createValue(\"some.files\", PatternOptionBuilder.FILES_VALUE)" }

        val type = symbolSolver.calculateType(node)
        assertResolvedTypeIs<ResolvedArrayType>(type)
        assertEquals(1, type.arrayLevel())
        val componentType = type.componentType
        assertResolvedTypeIs<ResolvedReferenceType>(componentType)
        assertEquals("java.io.File", componentType.qualifiedName)
    }
}