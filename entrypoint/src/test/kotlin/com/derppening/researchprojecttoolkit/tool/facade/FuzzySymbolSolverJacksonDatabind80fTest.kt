package com.derppening.researchprojecttoolkit.tool.facade

import com.derppening.researchprojecttoolkit.tool.TestProjects
import com.derppening.researchprojecttoolkit.tool.assertResolvedTypeIs
import com.github.javaparser.ast.expr.NameExpr
import com.github.javaparser.resolution.declarations.ResolvedDeclaration
import com.github.javaparser.resolution.declarations.ResolvedEnumConstantDeclaration
import com.github.javaparser.resolution.types.ResolvedReferenceType
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FuzzySymbolSolverJacksonDatabind80fTest : FuzzySymbolSolverTest() {

    override val project = TestProjects.JacksonDatabind80f

    @Test
    fun `Resolve Type of NameExpr in SwitchStmt Scope`() {
        val cu = project.parseSourceFile(
            "src/main/java/com/fasterxml/jackson/databind/introspect/JacksonAnnotationIntrospector.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("_refinePropertyInclusion", "Annotated", "JsonInclude.Value").single()
            .body.get()
            .statements[1]
            .asIfStmt()
            .thenStmt
            .asBlockStmt()
            .statements[0]
            .asSwitchStmt()
            .entries
            .flatMap { it.labels }
            .single { it is NameExpr && it.nameAsString == "ALWAYS" }
        assumeTrue { node.toString() == "ALWAYS" }

        val type = symbolSolver.calculateType(node)
        assertResolvedTypeIs<ResolvedReferenceType>(type)
        assertEquals("com.fasterxml.jackson.databind.annotation.JsonSerialize.Inclusion", type.qualifiedName)
    }

    @Test
    fun `Resolve Declaration of NameExpr in SwitchStmt Scope`() {
        val cu = project.parseSourceFile(
            "src/main/java/com/fasterxml/jackson/databind/introspect/JacksonAnnotationIntrospector.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("_refinePropertyInclusion", "Annotated", "JsonInclude.Value").single()
            .body.get()
            .statements[1]
            .asIfStmt()
            .thenStmt
            .asBlockStmt()
            .statements[0]
            .asSwitchStmt()
            .entries
            .flatMap { it.labels }
            .single { it is NameExpr && it.nameAsString == "ALWAYS" }
        assumeTrue { node.toString() == "ALWAYS" }

        val decl = symbolSolver.resolveDeclaration(node, ResolvedDeclaration::class.java)
        assertIs<ResolvedEnumConstantDeclaration>(decl)
        assertEquals("ALWAYS", decl.name)
        val declType = decl.type
        assertResolvedTypeIs<ResolvedReferenceType>(declType)
        assertEquals("com.fasterxml.jackson.databind.annotation.JsonSerialize.Inclusion", declType.qualifiedName)
    }
}