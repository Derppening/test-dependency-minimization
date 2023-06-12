package com.derppening.researchprojecttoolkit.tool.facade

import com.derppening.researchprojecttoolkit.tool.TestProjects
import com.derppening.researchprojecttoolkit.tool.assertResolvedTypeIs
import com.github.javaparser.resolution.types.ResolvedReferenceType
import com.github.javaparser.resolution.types.ResolvedTypeVariable
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals

class FuzzySymbolSolverJacksonDatabind8fTest : FuzzySymbolSolverTest() {

    override val project = TestProjects.JacksonDatabind8f

    @Test
    fun `Resolve Method with Class Hierarchical Parameter Type`() {
        val cu = project.parseSourceFile(
            "src/main/java/com/fasterxml/jackson/databind/MappingIterator.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("readAll").single()
            .body.get()
            .statements[0]
            .asReturnStmt()
            .expression.get()
            .asMethodCallExpr()
        assumeTrue { node.toString() == "readAll(new ArrayList<T>())" }

        val type = symbolSolver.calculateType(node)
        assertResolvedTypeIs<ResolvedReferenceType>(type)
        assertEquals("java.util.ArrayList", type.qualifiedName)
        assertEquals(1, type.typeParametersValues().size)
        val typeTp0 = type.typeParametersValues()[0]
            .let { assertResolvedTypeIs<ResolvedTypeVariable>(it).asTypeParameter() }
        assertEquals("com.fasterxml.jackson.databind.MappingIterator", typeTp0.containerQualifiedName)
        assertEquals("T", typeTp0.name)
    }
}