package com.derppening.researchprojecttoolkit.tool.facade

import com.derppening.researchprojecttoolkit.tool.TestProjects
import com.derppening.researchprojecttoolkit.tool.assertResolvedTypeIs
import com.derppening.researchprojecttoolkit.util.parameters
import com.derppening.researchprojecttoolkit.util.resolveDeclaration
import com.github.javaparser.resolution.declarations.ResolvedDeclaration
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration
import com.github.javaparser.resolution.types.ResolvedPrimitiveType
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FuzzySymbolSolverJxPath1fTest : FuzzySymbolSolverTest() {

    override val project = TestProjects.JxPath1f

    @Test
    fun `Solve for Method with Multiple Classes of Same QName in Classpath`() {
        val cu = project.parseSourceFile(
            "src/java/org/apache/commons/jxpath/xml/DOMParser.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("parseXML", "InputStream").single()
            .body.get()
            .statements[0].asTryStmt()
            .tryBlock
            .statements[3].asExpressionStmt()
            .expression.asMethodCallExpr()
        assumeTrue { node.toString() == "factory.setIgnoringElementContentWhitespace(isIgnoringElementContentWhitespace())" }

        val nodeArg0 = node
            .arguments[0].asMethodCallExpr()

        val arg0Type = symbolSolver.calculateType(nodeArg0)
        assertResolvedTypeIs<ResolvedPrimitiveType>(arg0Type)
        assertEquals(ResolvedPrimitiveType.BOOLEAN, arg0Type)

        val decl = symbolSolver.resolveDeclaration<ResolvedDeclaration>(node)
        assertIs<ResolvedMethodDeclaration>(decl)
        assertEquals("setIgnoringElementContentWhitespace", decl.name)
        assertEquals(1, decl.numberOfParams)
        assertEquals("javax.xml.parsers.DocumentBuilderFactory", decl.declaringType().qualifiedName)
        val declParam0Type = decl.parameters[0].type
        assertResolvedTypeIs<ResolvedPrimitiveType>(declParam0Type)
        assertEquals(ResolvedPrimitiveType.BOOLEAN, declParam0Type)
    }
}