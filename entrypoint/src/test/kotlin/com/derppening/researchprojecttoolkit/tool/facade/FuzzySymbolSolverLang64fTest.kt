package com.derppening.researchprojecttoolkit.tool.facade

import com.derppening.researchprojecttoolkit.tool.TestProjects
import com.github.javaparser.resolution.declarations.ResolvedConstructorDeclaration
import com.github.javaparser.resolution.declarations.ResolvedDeclaration
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertIs

class FuzzySymbolSolverLang64fTest : FuzzySymbolSolverTest() {

    override val project = TestProjects.Lang64f

    @Test
    fun `Eliminate candidates with mismatching visibility`() {
        val cu = project.parseSourceFile(
            "src/test/org/apache/commons/lang/enums/ValuedEnumTest.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("testCompareTo_classloader_equal").single()
            .body.get()
            .statements[1]
            .asIfStmt()
            .thenStmt
            .asBlockStmt()
            .statements[1]
            .asExpressionStmt()
            .expression
            .asVariableDeclarationExpr()
            .variables.single()
            .initializer.get()
            .asObjectCreationExpr()
        assumeTrue { node.toString() == "new URLClassLoader(urlCL.getURLs(), null)" }

        val decl = symbolSolver.resolveDeclaration(node, ResolvedDeclaration::class.java)
        assertIs<ResolvedConstructorDeclaration>(decl)
    }
}