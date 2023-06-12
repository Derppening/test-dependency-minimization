package com.derppening.researchprojecttoolkit.tool.facade

import com.derppening.researchprojecttoolkit.tool.TestProjects
import com.derppening.researchprojecttoolkit.tool.assertResolvedTypeIs
import com.github.javaparser.resolution.types.ResolvedPrimitiveType
import com.github.javaparser.resolution.types.ResolvedType
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals

class FuzzySymbolSolverJsoup86fTest : FuzzySymbolSolverTest() {

    override val project = TestProjects.Jsoup86f

    @Test
    fun `Solve Type of Member in Annotation Class`() {
        val cu = project.parseSourceFile(
            "src/main/java/org/jsoup/parser/TokeniserState.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getFieldByName("eof").get()
            .variables
            .single { it.nameAsString == "eof" }
            .type
        assumeTrue { node.toString() == "char" }

        val type = symbolSolver.toResolvedType(node, ResolvedType::class.java)
        assertResolvedTypeIs<ResolvedPrimitiveType>(type)
        assertEquals(ResolvedPrimitiveType.CHAR, type)
    }
}