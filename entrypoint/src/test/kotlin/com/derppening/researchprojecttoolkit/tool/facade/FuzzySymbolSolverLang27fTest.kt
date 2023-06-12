package com.derppening.researchprojecttoolkit.tool.facade

import com.derppening.researchprojecttoolkit.tool.TestProjects
import com.derppening.researchprojecttoolkit.tool.assertResolvedTypeIs
import com.derppening.researchprojecttoolkit.tool.assumeTrue
import com.github.javaparser.resolution.types.ResolvedReferenceType
import kotlin.test.Test
import kotlin.test.assertEquals

class FuzzySymbolSolverLang27fTest : FuzzySymbolSolverTest() {

    override val project = TestProjects.Lang27f

    @Test
    fun `Regression-00`() {
        val cu = project.parseSourceFile(
            "src/main/java/org/apache/commons/lang3/StringUtils.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("stripAccents", "String").single()
            .body.get()
            .statements[1].asIfStmt()
            .thenStmt.asBlockStmt()
            .statements[0].asTryStmt()
            .tryBlock
            .statements[5].asExpressionStmt()
            .expression.asVariableDeclarationExpr()
            .variables.single()
            .initializer.get().asMethodCallExpr()
        assumeTrue { node.toString() == "java.util.regex.Pattern.compile(\"\\\\p{InCombiningDiacriticalMarks}+\")" }

        val type = symbolSolver.calculateType(node)
        assertResolvedTypeIs<ResolvedReferenceType>(type)
        assertEquals("java.util.regex.Pattern", type.qualifiedName)
    }
}