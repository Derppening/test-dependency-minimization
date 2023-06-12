package com.derppening.researchprojecttoolkit.tool.facade

import com.derppening.researchprojecttoolkit.tool.TestProjects
import com.derppening.researchprojecttoolkit.util.resolveDeclaration
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.resolution.UnsolvedSymbolException
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class FuzzySymbolSolverJacksonDatabind5fTest : FuzzySymbolSolverTest() {

    override val project = TestProjects.JacksonDatabind5f

    @Test
    fun `Cannot Solve Variable as Type`() {
        val cu = project.parseSourceFile(
            "src/main/java/com/fasterxml/jackson/databind/deser/std/DateDeserializers.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .members
            .single {
                it is ClassOrInterfaceDeclaration && it.nameAsString == "SqlDateDeserializer"
            }.asClassOrInterfaceDeclaration()
            .getMethodsBySignature("deserialize", "JsonParser", "DeserializationContext").single()
            .body.get()
            .statements[1].asReturnStmt()
            .expression.get().asConditionalExpr()
            .elseExpr.asObjectCreationExpr()
            .arguments[0].asMethodCallExpr()
            .scope.get().asNameExpr()
        assumeTrue { node.toString() == "d" }

        assertFailsWith<UnsolvedSymbolException> { symbolSolver.resolveDeclaration<ResolvedReferenceTypeDeclaration>(node) }
    }
}