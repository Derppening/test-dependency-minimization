package com.derppening.researchprojecttoolkit.tool.facade.typesolver

import com.github.javaparser.resolution.TypeSolver
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration
import com.github.javaparser.resolution.model.SymbolReference
import com.github.javaparser.resolution.types.ResolvedType
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver

/**
 * A type solver which splits child solvers into four groups:
 *
 * - JRE Solver: Solver which only uses classes in the `java.*` or `javax.*` packages (see
 *   [ReflectionTypeSolver.filterName]).
 * - Source Solvers: Solvers which find types from project source roots.
 * - Library Solvers: Solvers which find types from library or generated sources.
 * - Full Solver: Solver which uses all available classes on the JVM classpath.
 */
class PartitionedTypeSolver(
    sourceSolvers: List<TypeSolver>,
    librarySolvers: List<TypeSolver>,
    private val useFullSolver: Boolean
) : TypeSolver {

    private val groupingSolver = run {
        val solverGroups = listOfNotNull(
            GroupingTypeSolver.SolverGroup(JRE_GROUP_KEY, ReflectionTypeSolver(true)),
            GroupingTypeSolver.SolverGroup(SOURCE_GROUP_KEY, sourceSolvers),
            GroupingTypeSolver.SolverGroup(LIBRARY_GROUP_KEY, librarySolvers),
            GroupingTypeSolver.SolverGroup(FULL_GROUP_KEY, ReflectionTypeSolver(false)).takeIf { useFullSolver }
        )

        GroupingTypeSolver(solverGroups)
    }

    override fun getParent(): TypeSolver? = groupingSolver.parent
    override fun setParent(parent: TypeSolver) = groupingSolver.setParent(parent)

    override fun tryToSolveType(name: String): SymbolReference<ResolvedReferenceTypeDeclaration> =
        groupingSolver.tryToSolveType(name)

    fun getSolverForType(resolvedType: ResolvedType): TypeSolver? =
        groupingSolver.getSolverForType(resolvedType)
    fun getSolverForResolvedDecl(decl: ResolvedReferenceTypeDeclaration): TypeSolver? =
        groupingSolver.getSolverForResolvedDecl(decl)

    fun isSolvedBySourceSolvers(resolvedType: ResolvedType): Boolean =
        groupingSolver.isTypeSolvedByGroup(resolvedType, SOURCE_GROUP_KEY)
    fun isSolvedBySourceSolvers(decl: ResolvedReferenceTypeDeclaration): Boolean =
        groupingSolver.isResolvedDeclSolvedByGroup(decl, SOURCE_GROUP_KEY)

    companion object {

        private const val JRE_GROUP_KEY = "jre"
        private const val SOURCE_GROUP_KEY = "source"
        private const val LIBRARY_GROUP_KEY = "library"
        private const val FULL_GROUP_KEY = "full"
    }
}