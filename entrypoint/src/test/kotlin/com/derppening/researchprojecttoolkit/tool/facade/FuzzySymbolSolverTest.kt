package com.derppening.researchprojecttoolkit.tool.facade

import com.derppening.researchprojecttoolkit.GlobalConfiguration
import com.derppening.researchprojecttoolkit.tool.TestProjects
import com.derppening.researchprojecttoolkit.tool.facade.typesolver.PartitionedTypeSolver
import com.derppening.researchprojecttoolkit.util.clearMemory
import org.junit.jupiter.api.BeforeAll
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

abstract class FuzzySymbolSolverTest {

    abstract val project: TestProjects.Project

    private var _typeSolver: PartitionedTypeSolver? = null
    private var _symbolSolver: FuzzySymbolSolver? = null

    protected val typeSolver: PartitionedTypeSolver get() = _typeSolver!!
    protected val symbolSolver: FuzzySymbolSolver get() = _symbolSolver!!

    @BeforeTest
    fun setUp() {
        _typeSolver = project.getTypeSolver()
        _symbolSolver = FuzzySymbolSolver(typeSolver)
    }

    @AfterTest
    fun tearDown() {
        _symbolSolver = null
        _typeSolver = null

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