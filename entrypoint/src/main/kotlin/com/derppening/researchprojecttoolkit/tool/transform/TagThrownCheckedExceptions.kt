package com.derppening.researchprojecttoolkit.tool.transform

import com.derppening.researchprojecttoolkit.tool.ReducerContext
import com.derppening.researchprojecttoolkit.tool.checkedExceptionSources
import com.derppening.researchprojecttoolkit.util.*
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.stmt.TryStmt
import com.github.javaparser.resolution.types.ResolvedType
import java.util.*

class TagThrownCheckedExceptions(override val reducerContext: ReducerContext) : TransformPass {

    private fun processTryStmt(tryStmt: TryStmt) {
        if (tryStmt.catchClauses.isEmpty()) {
            return
        }

        val catchClauseCaughtTypes = tryStmt.catchClauses
            .map { it to it.parameter.type }
            .map { (k, v) -> k to reducerContext.toResolvedType<ResolvedType>(v) }
        val tryBlockThrowingNodes = with(reducerContext) {
            getCanThrowExceptionSet(tryStmt.tryBlock)
        }
        val tryBlockExceptionsByType = tryBlockThrowingNodes
            .entries
            .flatMap { (k, v) -> v.map { it to k } }
            .groupByTo(TreeMap(RESOLVED_REF_TYPE_COMPARATOR)) { it.first }
            .mapValues { (_, v) -> v.map { it.second }.toCollection(NodeRangeTreeSet()) }

        tryBlockExceptionsByType
            .filter { (exType, _) -> exType.isCheckedException() }
            .forEach { (thrownType, nodes) ->
                for ((catchClause, caughtType) in catchClauseCaughtTypes) {
                    // Ignore catch clauses which do not involve thrownType in its class hierarchy
                    if (!(caughtType.isAssignableBy(thrownType) || thrownType.isAssignableBy(caughtType))) {
                        continue
                    }

                    val caughtTypes = catchClause.parameter
                        .type
                        .let {
                            if (it.isUnionType) {
                                it.asUnionType().elements
                            } else {
                                listOf(it.asReferenceType())
                            }
                        }
                    val caughtResolvedTypes = caughtType
                        .let {
                            if (it.isUnionType) {
                                it.asUnionType().elements.map { it.asReferenceType() }
                            } else {
                                listOf(it.asReferenceType())
                            }
                        }

                    check(caughtTypes.size == caughtResolvedTypes.size)

                    val catchClauseExceptionTypes = caughtTypes.zip(caughtResolvedTypes)

                    nodes.forEach { node ->
                        catchClauseExceptionTypes
                            .first { (_, resolvedCaughtType) ->
                                resolvedCaughtType.isAssignableBy(thrownType) || thrownType.isAssignableBy(resolvedCaughtType)
                            }
                            .let { (caughtTypeAst, _) ->
                                catchClause.checkedExceptionSources
                                    .computeIfAbsent(caughtTypeAst) { mutableListOf() }
                                    .add(node)
                            }
                    }

                    // If the caught type is not a subtype of thrown type, no more catch clauses will be able to handle
                    // this exception, since this clause already handles all exceptions thrown by the node
                    if (caughtType == thrownType || !thrownType.isAssignableBy(caughtType) || caughtResolvedTypes.any { !thrownType.isAssignableBy(it) }) {
                        break
                    }
                }
            }
    }

    override fun transform(cu: CompilationUnit) {
        verifyIsInvokedOnce(cu)

        val tryStmts = cu.findAll<TryStmt>()

        tryStmts.forEach {
            processTryStmt(it)
        }
    }

    companion object {

        private val LOGGER = Logger<TagThrownCheckedExceptions>()
    }
}