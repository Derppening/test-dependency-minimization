package com.derppening.researchprojecttoolkit.tool.transform

import com.derppening.researchprojecttoolkit.tool.ReducerContext
import com.derppening.researchprojecttoolkit.visitor.TransformingCloneVisitor
import com.github.javaparser.ast.CompilationUnit
import kotlin.jvm.optionals.getOrNull

class RemoveUnusedNodes(
    override val reducerContext: ReducerContext,
    private val enableAssertions: Boolean
) : MutatingTransformPass {

    override fun transform(cu: CompilationUnit): CompilationUnit {
        verifyIsInvokedOnce(cu)

        return runCatching {
            cu.accept(TransformingCloneVisitor(reducerContext, enableAssertions), Unit) as CompilationUnit
        }.onFailure {
            throw RuntimeException("Error while cloning Compilation Unit `${cu.primaryType.flatMap { it.fullyQualifiedName }.getOrNull()}`", it)
        }.getOrThrow()
    }
}