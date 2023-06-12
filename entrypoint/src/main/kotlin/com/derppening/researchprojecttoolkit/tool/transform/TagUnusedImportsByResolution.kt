package com.derppening.researchprojecttoolkit.tool.transform

import com.derppening.researchprojecttoolkit.tool.*
import com.derppening.researchprojecttoolkit.tool.reachability.ReachableReason
import com.derppening.researchprojecttoolkit.util.isNodeInCallableHeader
import com.derppening.researchprojecttoolkit.util.isNodeInExplicitCtorStmt
import com.derppening.researchprojecttoolkit.util.synchronizedWith
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.ImportDeclaration

/**
 * Transform pass which tags unused imports from a [CompilationUnit].
 */
class TagUnusedImportsByResolution(
    override val reducerContext: ReducerContext
) : TransformPass {

    private fun isImportDeclUnused(importDecl: ImportDeclaration): Boolean {
        val referencedNodes = importDecl.inclusionReasonsData.synchronizedWith {
            map { reason ->
                when (reason) {
                    is ReachableReason.ReferencedBySymbolName -> reason.node
                    is ReachableReason.ReferencedByTypeName -> reason.node
                    else -> error("Reasons should be ReferencedBySymbolName or ReferencedByTypeName, but got ${reason.toReasonString()}")
                }
            }
        }

        if (importDecl.inclusionReasonsData.isEmpty()) {
            return true
        }

        if (referencedNodes.all {
                it.isUnusedForRemovalData ||
                        (it.isUnusedForDummyData && !isNodeInCallableHeader(it) && !isNodeInExplicitCtorStmt(it))
        }) {
            return true
        }

        return false
    }

    override fun transform(cu: CompilationUnit) {
        verifyIsInvokedOnce(cu)
        verifyIsInvokedAfter<TagImportUsage>(cu)

        cu.imports.forEach { importDecl ->
            importDecl.transformDecisionData = if (isImportDeclUnused(importDecl)) {
                 NodeTransformDecision.REMOVE
            } else {
                NodeTransformDecision.NO_OP
            }
        }
    }
}