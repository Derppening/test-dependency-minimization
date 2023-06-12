package com.derppening.researchprojecttoolkit.tool.facade

import com.derppening.researchprojecttoolkit.tool.TestProjects
import com.derppening.researchprojecttoolkit.tool.assertResolvedTypeIs
import com.derppening.researchprojecttoolkit.util.toResolvedType
import com.github.javaparser.resolution.types.ResolvedReferenceType
import com.github.javaparser.resolution.types.ResolvedType
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals

class FuzzySymbolSolverJacksonXml1fTest : FuzzySymbolSolverTest() {

    override val project = TestProjects.JacksonXml1f

    @Test
    fun `Resolve Parameter Type with Nested Name Component 1`() {
        val cu = project.parseSourceFile(
            "src/main/java/com/fasterxml/jackson/dataformat/xml/XmlFactory.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("configure", "FromXmlParser.Feature", "boolean").single()
            .parameters[0]
            .type

        assumeTrue { node.toString() == "FromXmlParser.Feature" }

        val type = symbolSolver.toResolvedType<ResolvedType>(node)
        assertResolvedTypeIs<ResolvedReferenceType>(type)
        assertEquals("com.fasterxml.jackson.dataformat.xml.deser.FromXmlParser.Feature", type.qualifiedName)
    }

    @Test
    fun `Resolve Parameter Type with Nested Name Component 2`() {
        val cu = project.parseSourceFile(
            "src/main/java/com/fasterxml/jackson/dataformat/xml/XmlFactory.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("configure", "ToXmlGenerator.Feature", "boolean").single()
            .parameters[0]
            .type

        assumeTrue { node.toString() == "ToXmlGenerator.Feature" }

        val type = symbolSolver.toResolvedType<ResolvedType>(node)
        assertResolvedTypeIs<ResolvedReferenceType>(type)
        assertEquals("com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator.Feature", type.qualifiedName)
    }
}