package com.derppening.researchprojecttoolkit.util

import com.github.javaparser.ast.body.CallableDeclaration
import java.util.function.BinaryOperator
import java.util.stream.Collectors

/**
 * Implementation of [Collectors.throwingMerger] for [CallableDeclaration].
 */
object ThrowingMergerForCallable : BinaryOperator<CallableDeclaration<*>> {

    override fun apply(t: CallableDeclaration<*>, u: CallableDeclaration<*>): CallableDeclaration<*> {
        val tAstId = t.astBasedId
        val uAstId = u.astBasedId
        if (tAstId == uAstId) {
            // Same key
            return t
        }

        val tQSig = t.getQualifiedSignature(null)
        val uQSig = u.getQualifiedSignature(null)
        error("Duplicate key $tQSig (${tAstId}) and $uQSig (${uAstId})")
    }
}