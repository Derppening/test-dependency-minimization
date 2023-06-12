package com.derppening.researchprojecttoolkit.tool.transform

import com.derppening.researchprojecttoolkit.tool.ReducerContext
import com.derppening.researchprojecttoolkit.tool.inclusionReasonsData
import com.derppening.researchprojecttoolkit.tool.transformDecisionData
import com.derppening.researchprojecttoolkit.util.canBeReferencedByName
import com.derppening.researchprojecttoolkit.util.findAll
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.TypeDeclaration

class TagStaticAnalysisClassUnusedDecls(override val reducerContext: ReducerContext) : TagUnusedDecls() {

    private fun tryRemove(typeDecl: TypeDeclaration<*>) {
        typeDecl.transformDecisionData = decideForType(typeDecl, noCache = false)
    }

    override fun transform(cu: CompilationUnit) {
        verifyIsInvokedOnce(cu)

        val types = cu.findAll<TypeDeclaration<*>> { it.canBeReferencedByName }

        types.forEach {
            tryRemove(it)
        }
    }

    companion object {

        fun decideForType(
            typeDecl: TypeDeclaration<*>,
            noCache: Boolean
        ): NodeTransformDecision {
            if (!noCache) {
                typeDecl.transformDecisionData?.let { return it }
            }

            return if (typeDecl.inclusionReasonsData.isNotEmpty()) {
                NodeTransformDecision.NO_OP
            } else {
                NodeTransformDecision.REMOVE
            }
        }
    }
}