package com.derppening.researchprojecttoolkit.tool.facade

import com.derppening.researchprojecttoolkit.tool.TestProjects
import com.derppening.researchprojecttoolkit.tool.assertResolvedTypeIs
import com.github.javaparser.ast.body.EnumDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.resolution.declarations.ResolvedConstructorDeclaration
import com.github.javaparser.resolution.declarations.ResolvedDeclaration
import com.github.javaparser.resolution.types.ResolvedArrayType
import com.github.javaparser.resolution.types.ResolvedPrimitiveType
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FuzzySymbolSolverCompress47fTest : FuzzySymbolSolverTest() {

    override val project = TestProjects.Compress47f

    @Test
    fun `Solve FieldDeclaration in EnumConstantDeclaration`() {
        val cu = project.parseSourceFile(
            "src/main/java/org/apache/commons/compress/archivers/sevenz/CLI.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .members
            .single { it is EnumDeclaration && it.nameAsString == "Mode" }
            .asEnumDeclaration()
            .entries
            .single { it.nameAsString == "EXTRACT" }
            .classBody
            .single {
                it is MethodDeclaration && it.let { methodDecl ->
                    methodDecl.nameAsString == "takeAction" &&
                            methodDecl.signature.parameterTypes.size == 2 &&
                            methodDecl.signature.parameterTypes[0].asString() == "SevenZFile" &&
                            methodDecl.signature.parameterTypes[1].asString() == "SevenZArchiveEntry"
                }
            }
            .asMethodDeclaration()
            .body.get()
            .statements[5]
            .asTryStmt()
            .tryBlock
            .statements[2]
            .asWhileStmt()
            .body
            .asBlockStmt()
            .statements[0]
            .asExpressionStmt()
            .expression
            .asVariableDeclarationExpr()
            .variables.single()
            .initializer.get()
            .asCastExpr()
            .expression
            .asMethodCallExpr()
            .arguments[1]
            .asFieldAccessExpr()
            .scope
            .asNameExpr()
        assumeTrue { node.nameAsString == "buf" }

        val type = symbolSolver.calculateType(node)
        assertResolvedTypeIs<ResolvedArrayType>(type)
        assertEquals(1, type.arrayLevel())
        val componentType = type.componentType
        assertResolvedTypeIs<ResolvedPrimitiveType>(componentType)
        assertEquals(ResolvedPrimitiveType.BYTE, componentType)
    }

    @Test
    fun `Resolve Enum Constructor by Fallback`() {
        val cu = project.parseSourceFile(
            "src/main/java/org/apache/commons/compress/archivers/zip/ZipMethod.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .defaultConstructor.get()
            .body
            .statements[0]
            .asExplicitConstructorInvocationStmt()
        assumeTrue { node.toString() == "this(UNKNOWN_CODE);" }

        val decl = symbolSolver.resolveDeclaration(node, ResolvedDeclaration::class.java)
        assertIs<ResolvedConstructorDeclaration>(decl)
        assertEquals(1, decl.numberOfParams)

        val declParam0Type = decl.getParam(0).type
            .let { assertResolvedTypeIs<ResolvedPrimitiveType>(it).asPrimitive() }
        assertEquals(ResolvedPrimitiveType.INT, declParam0Type)
    }
}