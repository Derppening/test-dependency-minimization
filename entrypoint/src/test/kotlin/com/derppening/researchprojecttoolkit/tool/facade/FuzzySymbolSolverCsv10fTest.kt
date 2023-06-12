package com.derppening.researchprojecttoolkit.tool.facade

import com.derppening.researchprojecttoolkit.tool.TestProjects
import com.derppening.researchprojecttoolkit.tool.assertResolvedTypeIs
import com.derppening.researchprojecttoolkit.util.baseComponentType
import com.github.javaparser.resolution.declarations.ResolvedDeclaration
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration
import com.github.javaparser.resolution.types.ResolvedArrayType
import com.github.javaparser.resolution.types.ResolvedReferenceType
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FuzzySymbolSolverCsv10fTest : FuzzySymbolSolverTest() {

    override val project = TestProjects.Csv10f

    @Test
    fun `Resolve Declaration of MethodCallExpr with Array Param Type`() {
        val cu = project.parseSourceFile(
            "src/test/java/org/apache/commons/csv/CSVPrinterTest.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("testExcelPrintAllIterableOfArrays").single()
            .body.get()
            .statements[2]
            .asExpressionStmt()
            .expression
            .asMethodCallExpr()
        assumeTrue { node.toString() == "printer.printRecords(Arrays.asList(new String[][] { { \"r1c1\", \"r1c2\" }, { \"r2c1\", \"r2c2\" } }))" }

        val arg0Node = node.arguments[0]
            .asMethodCallExpr()

        val arg0arg0Node = arg0Node.arguments[0]
            .asArrayCreationExpr()

        val arg0arg0Type = symbolSolver.calculateType(arg0arg0Node)
        assertResolvedTypeIs<ResolvedArrayType>(arg0arg0Type)
        assertEquals(2, arg0arg0Type.arrayLevel())
        val arg0arg0ComponentType = arg0arg0Type.baseComponentType()
        assertResolvedTypeIs<ResolvedReferenceType>(arg0arg0ComponentType)
        assertEquals("java.lang.String", arg0arg0ComponentType.qualifiedName)

        val arg0Type = symbolSolver.calculateType(arg0Node)
        assertResolvedTypeIs<ResolvedReferenceType>(arg0Type)
        assertEquals("java.util.List", arg0Type.qualifiedName)
        assertEquals(1, arg0Type.typeParametersValues().size)
        val arg0TypeTp0 = arg0Type.typeParametersValues()[0]
        assertResolvedTypeIs<ResolvedArrayType>(arg0TypeTp0)
        assertEquals(1, arg0TypeTp0.arrayLevel())
        val arg0TypeTp0Component = arg0TypeTp0.componentType
        assertResolvedTypeIs<ResolvedReferenceType>(arg0TypeTp0Component)
        assertEquals("java.lang.String", arg0TypeTp0Component.qualifiedName)

        val decl = symbolSolver.resolveDeclaration(node, ResolvedDeclaration::class.java)
        assertIs<ResolvedMethodDeclaration>(decl)
        assertEquals("printRecords", decl.name)
        assertEquals("CSVPrinter", decl.className)
        assertEquals("org.apache.commons.csv", decl.packageName)
        assertEquals(1, decl.numberOfParams)
        assertEquals("java.lang.Iterable<?>", decl.getParam(0).describeType())
    }
}