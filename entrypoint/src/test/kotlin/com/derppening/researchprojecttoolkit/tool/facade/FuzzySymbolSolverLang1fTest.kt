package com.derppening.researchprojecttoolkit.tool.facade

import com.derppening.researchprojecttoolkit.tool.TestProjects
import com.derppening.researchprojecttoolkit.tool.assertIsUnbounded
import com.derppening.researchprojecttoolkit.tool.assertResolvedTypeIs
import com.github.javaparser.resolution.declarations.ResolvedDeclaration
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration
import com.github.javaparser.resolution.types.ResolvedPrimitiveType
import com.github.javaparser.resolution.types.ResolvedReferenceType
import com.github.javaparser.resolution.types.ResolvedTypeVariable
import com.github.javaparser.resolution.types.ResolvedWildcard
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FuzzySymbolSolverLang1fTest : FuzzySymbolSolverTest() {

    override val project = TestProjects.Lang1f

    @Test
    fun `Solve MethodCallExpr with Overloaded Methods Containing Tp with Same Name`() {
        val cu = project.parseSourceFile(
            "src/main/java/org/apache/commons/lang3/Validate.java",
            symbolSolver
        )
        val node = cu.primaryType.get()
            .getMethodsBySignature("validIndex", "T", "int")
            .single {
                it.typeParameters.size == 1
                        && it.typeParameters[0].asTypeParameter().typeBound.size == 1
                        && it.typeParameters[0].asTypeParameter().typeBound[0].isClassOrInterfaceType
                        && it.typeParameters[0].asTypeParameter().typeBound[0].asString() == "Collection<?>"
            }
            .body.get()
            .statements[0]
            .asReturnStmt()
            .expression.get()
            .asMethodCallExpr()
        assumeTrue { node.toString() == "validIndex(collection, index, DEFAULT_VALID_INDEX_COLLECTION_EX_MESSAGE, Integer.valueOf(index))" }

        with(symbolSolver.resolveMethodCallExprFallback(node)) {
            val decl = this

            assertIs<ResolvedMethodDeclaration>(decl)
            assertEquals("validIndex", decl.name)
            assertEquals("Validate", decl.className)
            assertEquals("org.apache.commons.lang3", decl.packageName)

            val declParam0Type = decl.getParam(0).type
                .let { assertResolvedTypeIs<ResolvedTypeVariable>(it).asTypeParameter() }
            assertEquals("T", declParam0Type.name)
            assertEquals(
                "org.apache.commons.lang3.Validate.validIndex(T, int, java.lang.String, java.lang.Object...)",
                declParam0Type.containerQualifiedName
            )
            assertTrue(declParam0Type.hasLowerBound())
            assertEquals(1, declParam0Type.bounds.size)
            val declParam0TypeBound = declParam0Type.lowerBound
            assertResolvedTypeIs<ResolvedReferenceType>(declParam0TypeBound)
            assertEquals("java.util.Collection", declParam0TypeBound.qualifiedName)
            assertEquals(1, declParam0TypeBound.typeParametersValues().size)
            val declParam0TypeBoundTp0 = declParam0TypeBound.typeParametersValues()[0]
            assertResolvedTypeIs<ResolvedWildcard>(declParam0TypeBoundTp0)
            assertIsUnbounded(declParam0TypeBoundTp0)

            val declParam1Type = decl.getParam(1).type
            assertResolvedTypeIs<ResolvedPrimitiveType>(declParam1Type)
            assertEquals(ResolvedPrimitiveType.INT, declParam1Type)
        }

        with(symbolSolver.resolveDeclaration(node, ResolvedDeclaration::class.java)) {
            val decl = this

            assertIs<ResolvedMethodDeclaration>(decl)
            assertEquals("validIndex", decl.name)
            assertEquals("Validate", decl.className)
            assertEquals("org.apache.commons.lang3", decl.packageName)

            val declParam0Type = decl.getParam(0).type
                .let { assertResolvedTypeIs<ResolvedTypeVariable>(it).asTypeParameter() }
            assertEquals("T", declParam0Type.name)
            assertEquals(
                "org.apache.commons.lang3.Validate.validIndex(T, int, java.lang.String, java.lang.Object...)",
                declParam0Type.containerQualifiedName
            )
            assertTrue(declParam0Type.hasLowerBound())
            assertEquals(1, declParam0Type.bounds.size)
            val declParam0TypeBound = declParam0Type.lowerBound
            assertResolvedTypeIs<ResolvedReferenceType>(declParam0TypeBound)
            assertEquals("java.util.Collection", declParam0TypeBound.qualifiedName)
            assertEquals(1, declParam0TypeBound.typeParametersValues().size)
            val declParam0TypeBoundTp0 = declParam0TypeBound.typeParametersValues()[0]
            assertResolvedTypeIs<ResolvedWildcard>(declParam0TypeBoundTp0)
            assertIsUnbounded(declParam0TypeBoundTp0)

            val declParam1Type = decl.getParam(1).type
            assertResolvedTypeIs<ResolvedPrimitiveType>(declParam1Type)
            assertEquals(ResolvedPrimitiveType.INT, declParam1Type)
        }

        val internalType = symbolSolver.calculateTypeInternal(node)
            .let { assertResolvedTypeIs<ResolvedTypeVariable>(it).asTypeParameter() }
        assertEquals("T", internalType.name)
        assertEquals("org.apache.commons.lang3.Validate.validIndex(T, int)", internalType.containerQualifiedName)

        val type = symbolSolver.calculateType(node)
        assertResolvedTypeIs<ResolvedReferenceType>(type)
        assertEquals("java.util.Collection", type.qualifiedName)
        assertEquals(1, type.typeParametersValues().size)
        val typeTp0 = type.typeParametersValues()[0]
        assertResolvedTypeIs<ResolvedWildcard>(typeTp0)
        assertIsUnbounded(typeTp0)
    }
}