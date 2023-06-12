package com.derppening.researchprojecttoolkit

import com.derppening.researchprojecttoolkit.tool.TestProjects
import com.derppening.researchprojecttoolkit.util.clearMemory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class JavaParserTest {

    @Test
    fun `Solve Ancestors - Unexpected Type Parameters`() {
        val reducerContext = TestProjects.Collections25f.getReducerContext()

        val derivedResolvedType = reducerContext.typeSolver.solveType("org.apache.commons.collections4.bidimap.TreeBidiMap.Inverse")
        val allAncestors = derivedResolvedType.allAncestors

        val directSupertype = allAncestors.single { it.qualifiedName == "org.apache.commons.collections4.OrderedBidiMap" }
        assertEquals(2, directSupertype.typeParametersValues().size)
        with(directSupertype.typeParametersValues()[0].asTypeParameter()) {
            assertEquals("V", name)
            assertEquals("org.apache.commons.collections4.bidimap.TreeBidiMap", containerQualifiedName)
        }
        with(directSupertype.typeParametersValues()[1].asTypeParameter()) {
            assertEquals("K", name)
            assertEquals("org.apache.commons.collections4.bidimap.TreeBidiMap", containerQualifiedName)
        }

        val onceRemovedSupertype = allAncestors.single { it.qualifiedName == "org.apache.commons.collections4.BidiMap" }
        assertEquals(2, onceRemovedSupertype.typeParametersValues().size)
        with(onceRemovedSupertype.typeParametersValues()[0].asTypeParameter()) {
            assertEquals("V", name)
            assertEquals("org.apache.commons.collections4.bidimap.TreeBidiMap", containerQualifiedName)
        }

        // !! EXPECTED FAILURE: Should be K, but JavaParser emits V !!
        with(onceRemovedSupertype.typeParametersValues()[1].asTypeParameter()) {
            assertEquals("V", name)
            assertEquals("org.apache.commons.collections4.bidimap.TreeBidiMap", containerQualifiedName)
        }
    }

    @AfterTest
    fun tearDown() {
        clearMemory()
    }
}