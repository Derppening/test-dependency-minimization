package com.derppening.researchprojecttoolkit.tool

import com.derppening.researchprojecttoolkit.GlobalConfiguration
import com.derppening.researchprojecttoolkit.tool.facade.typesolver.PartitionedTypeSolver
import com.derppening.researchprojecttoolkit.util.clearMemory
import com.derppening.researchprojecttoolkit.util.createResolvedRefType
import com.github.javaparser.resolution.types.ResolvedReferenceType
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AssumptionsTest {

    @Test
    fun `How to get base type of a generic type`() {
        val typeSolver = PartitionedTypeSolver(
            emptyList(),
            emptyList(),
            true
        )
        val type = createResolvedRefType(
            typeSolver.solveType("java.lang.Class"),
            listOf(createResolvedRefType(typeSolver.solveType("java.lang.Object")))
        )

        assumeTrue { type.describe() == "java.lang.Class<java.lang.Object>" }

        val rawType = type.toRawType().erasure()
            .also { assertResolvedTypeIs<ResolvedReferenceType>(it) }
            .asReferenceType()
        assertEquals("java.lang.Class", rawType.qualifiedName)
        assertEquals("java.lang.Class", rawType.describe())
        assertTrue(rawType.isRawType)
        assertTrue { rawType.typeParametersValues().isEmpty() }
    }

    @AfterTest
    fun tearDown() {
        clearMemory()
    }

    companion object {

        @JvmStatic
        @BeforeAll
        fun setUpAll() {
            GlobalConfiguration()
        }
    }
}