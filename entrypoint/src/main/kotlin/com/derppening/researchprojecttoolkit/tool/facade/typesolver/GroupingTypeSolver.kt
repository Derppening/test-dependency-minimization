package com.derppening.researchprojecttoolkit.tool.facade.typesolver

import com.github.javaparser.resolution.TypeSolver
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration
import com.github.javaparser.resolution.model.SymbolReference
import com.github.javaparser.resolution.model.typesystem.NullType
import com.github.javaparser.resolution.types.*
import com.github.javaparser.symbolsolver.cache.InMemoryCache
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * A type solver which splits type solvers into groups, trying each solver in a group before falling back to the next
 * solver group.
 */
class GroupingTypeSolver<KeyT>(
    private val solverGroups: List<SolverGroup<KeyT>>,
    private val exceptionHandler: (Throwable) -> Boolean = { true }
) : TypeSolver {

    /**
     * A solver group, identified by a [key].
     */
    data class SolverGroup<KeyT>(
        val key: KeyT,
        val solvers: List<TypeSolver>
    ) {

        constructor(key: KeyT, solver: TypeSolver) : this(key, listOf(solver))
    }

    private sealed class TypeCacheEntry<KeyT> {

        abstract val symbolName: String
        abstract val symbolRef: SymbolReference<ResolvedReferenceTypeDeclaration>

        data class Solved<KeyT>(
            override val symbolName: String,
            override val symbolRef: SymbolReference<ResolvedReferenceTypeDeclaration>,
            val solverGroup: SolverGroup<KeyT>,
            val solver: TypeSolver
        ) : TypeCacheEntry<KeyT>()

        data class Unsolved<KeyT>(
            override val symbolName: String,
            override val symbolRef: SymbolReference<ResolvedReferenceTypeDeclaration>
        ) : TypeCacheEntry<KeyT>()
    }

    private val lk = ReentrantReadWriteLock()

    private val typeCache = InMemoryCache<String, TypeCacheEntry<KeyT>>()

    private var _parent: TypeSolver? = null

    init {
        require(solverGroups.map { it.key }.let { it.size == it.toSet().size }) {
            "All solveGroups keys should be distinct"
        }
        require(solverGroups.all { it.solvers.distinct().size == it.solvers.size }) {
            "No solver should appear more than once in each solver group"
        }

        solverGroups.forEach { group ->
            group.solvers.forEach {
                it.parent = this
            }
        }
    }

    constructor(vararg solverGroup: SolverGroup<KeyT>, exceptionHandler: (Throwable) -> Boolean = { true }) :
            this(solverGroup.toList(), exceptionHandler)

    override fun getParent(): TypeSolver? = _parent
    override fun setParent(parent: TypeSolver) {
        check(_parent == null) { "This TypeSolver already has a parent." }
        check(_parent != this) { "The parent of this TypeSolver cannot be itself." }

        _parent = parent
    }

    private fun getEntryForType(name: String): TypeCacheEntry<KeyT> {
        val cachedSymbol = lk.read { typeCache[name] }
        if (cachedSymbol.isPresent) {
            return cachedSymbol.get()
        }

        for (solverGroup in solverGroups) {
            for (typeSolver in solverGroup.solvers) {
                val entry = runCatching {
                    typeSolver.tryToSolveType(name)
                }.map {
                    if (it.isSolved) {
                        TypeCacheEntry.Solved(
                            name,
                            it,
                            solverGroup,
                            typeSolver
                        )
                    } else {
                        null
                    }
                }.recoverCatching {
                    if (!exceptionHandler(it)) {
                        throw it
                    } else {
                        null
                    }
                }.getOrThrow()

                if (entry != null) {
                    lk.write {
                        typeCache.put(name, entry)
                    }

                    return entry
                }
            }
        }

        val entry = TypeCacheEntry.Unsolved<KeyT>(
            name,
            SymbolReference.unsolved()
        )
        lk.write {
            typeCache.put(name, entry)
        }
        return entry
    }

    override fun tryToSolveType(name: String): SymbolReference<ResolvedReferenceTypeDeclaration> =
        getEntryForType(name.takeWhile { it != '<' }).symbolRef

    private fun ResolvedType.asNameToSolver(): String? {
        return when (this) {
            is ResolvedVoidType, is NullType, is ResolvedPrimitiveType -> {
                // void, null and primitives do not need a type solver to solver for
                null
            }
            is ResolvedReferenceType -> qualifiedName
            is ResolvedArrayType -> componentType.asNameToSolver()
            is ResolvedLambdaConstraintType -> bound.asNameToSolver()
            is ResolvedTypeVariable, is ResolvedWildcard -> {
                // Concrete type of generic parameters/wildcards are unknown, so don't try to find a solver for it
                null
            }
            else -> TODO("Don't know how to get name for $this (Type: ${this::class.java})")
        }
    }

    fun getSolverForType(name: String): TypeSolver? =
        (getEntryForType(name) as? TypeCacheEntry.Solved)?.solver

    fun getSolverForType(resolvedType: ResolvedType): TypeSolver? =
        resolvedType.asNameToSolver()?.let { getSolverForType(it) }

    fun getSolverGroupForType(name: String): SolverGroup<KeyT>? =
        (getEntryForType(name) as? TypeCacheEntry.Solved)?.solverGroup

    fun getSolverGroupForType(resolvedType: ResolvedType): SolverGroup<KeyT>? =
        resolvedType.asNameToSolver()?.let { getSolverGroupForType(it) }

    private fun isTypeSolvedByGroup(resolvedType: ResolvedType, solverGroup: SolverGroup<KeyT>): Boolean =
        getSolverGroupForType(resolvedType) == solverGroup

    fun isTypeSolvedByGroup(resolvedType: ResolvedType, solverGroupKey: KeyT): Boolean =
        isTypeSolvedByGroup(resolvedType, solverGroups.single { it.key == solverGroupKey })

    fun getSolverForResolvedDecl(decl: ResolvedReferenceTypeDeclaration): TypeSolver? =
        getSolverForType(decl.qualifiedName)

    fun getSolverGroupForResolvedDecl(decl: ResolvedReferenceTypeDeclaration): SolverGroup<KeyT>? =
        getSolverGroupForType(decl.qualifiedName)

    fun isResolvedDeclSolvedByGroup(decl: ResolvedReferenceTypeDeclaration, solverGroup: SolverGroup<KeyT>): Boolean =
        getSolverGroupForResolvedDecl(decl) == solverGroup

    fun isResolvedDeclSolvedByGroup(decl: ResolvedReferenceTypeDeclaration, solverGroupKey: KeyT): Boolean =
        isResolvedDeclSolvedByGroup(decl, solverGroups.single { it.key == solverGroupKey })
}
