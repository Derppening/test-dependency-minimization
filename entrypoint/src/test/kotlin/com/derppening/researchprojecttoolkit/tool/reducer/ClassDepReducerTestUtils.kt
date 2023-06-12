package com.derppening.researchprojecttoolkit.tool.reducer

import com.derppening.researchprojecttoolkit.tool.inclusionReasonsData
import com.derppening.researchprojecttoolkit.tool.isUnusedForRemovalData
import com.derppening.researchprojecttoolkit.tool.transform.TagGroundTruthUnusedDecls
import com.derppening.researchprojecttoolkit.tool.transform.TagStaticAnalysisMemberUnusedDecls
import com.derppening.researchprojecttoolkit.tool.transformDecisionData
import com.derppening.researchprojecttoolkit.util.*
import com.github.javaparser.ast.body.*
import com.github.javaparser.ast.expr.AnnotationExpr
import hk.ust.cse.castle.toolkit.jvm.util.RuntimeAssertions
import kotlin.jvm.optionals.getOrDefault
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.fail

object ClassDepReducerTestUtils {

    fun testReductionCacheStability(isNotFatal: Boolean = false, reducerCreator: () -> AbstractReducer) {
        var errorCount = 0

        val reducer = reducerCreator()
            .also { reducer ->
                reducer.run()
                reducer.taggedCUs
            }

        val cus = reducer.context
            .loadedCompilationUnits
            .filter { it.primaryType.flatMap { it.fullyQualifiedName }.isPresent }
            .sortedBy { it.primaryType.flatMap { it.fullyQualifiedName }.get() }

        cus.forEach { cu ->
            val types = cu.findAll<TypeDeclaration<*>>().sortedWith(NodeAstComparator())

            types.forEach { type ->
                val noCacheDecision = TagStaticAnalysisMemberUnusedDecls.isTypeReachable(
                    reducer.context,
                    type,
                    enableAssertions = true,
                    noCache = true
                )
                val cacheDecision = TagStaticAnalysisMemberUnusedDecls.isTypeReachable(
                    reducer.context,
                    type,
                    enableAssertions = true,
                    noCache = false
                )

                val errString by lazy {
                    buildString {
                        append("Expected ")
                        append(type.fullyQualifiedName.getOrDefault("${type.nameAsString} (${type.fullRangeString})"))
                        append(" to be (noCache=true)=")
                        append(noCacheDecision)
                        append(" but got (noCache=false)=")
                        append(cacheDecision)
                        appendLine()
                        append("Inclusion Reasons:")
                        appendLine()
                        type.inclusionReasonsData.synchronizedWith {
                            map { it.toReasonString() }
                                .distinct()
                                .sorted()
                                .joinToString("\n") { "- $it" }
                        }.let { append(it) }
                    }
                }

                if (isNotFatal) {
                    if (noCacheDecision != cacheDecision) {
                        System.err.println(errString)
                        System.err.println()
                        errorCount += 1
                    }
                } else {
                    assertEquals(noCacheDecision, cacheDecision, errString)
                }
            }

            val callables = cu.findAll<CallableDeclaration<*>>().sortedWith(NodeAstComparator())

            callables.forEach { callable ->
                when (callable) {
                    is ConstructorDeclaration -> {
                        val noCacheDecision = TagStaticAnalysisMemberUnusedDecls.decideForConstructor(
                            reducer.context,
                            callable,
                            enableAssertions = true,
                            noCache = true
                        )
                        val cacheDecision = TagStaticAnalysisMemberUnusedDecls.decideForConstructor(
                            reducer.context,
                            callable,
                            enableAssertions = true,
                            noCache = false
                        )

                        val errString by lazy {
                            buildString {
                                append("Expected ")
                                with(reducer.context) { append(callable.getQualifiedSignature()) }
                                append(" to be (noCache=true)=")
                                append(noCacheDecision)
                                append(" but got (noCache=false)=")
                                append(cacheDecision)
                                appendLine()
                                append("Inclusion Reasons:")
                                appendLine()
                                callable.inclusionReasonsData.synchronizedWith {
                                    map { it.toReasonString() }
                                        .distinct()
                                        .sorted()
                                        .joinToString("\n") { "- $it" }
                                }.let { append(it) }
                            }
                        }

                        if (isNotFatal) {
                            if (noCacheDecision != cacheDecision) {
                                System.err.println(errString)
                                System.err.println()
                                errorCount += 1
                            }
                        } else {
                            assertEquals(noCacheDecision, cacheDecision, errString)
                        }
                    }

                    is MethodDeclaration -> {
                        val noCacheDecision = TagStaticAnalysisMemberUnusedDecls.decideForMethod(
                            reducer.context,
                            callable,
                            enableAssertions = true,
                            noCache = true
                        )
                        val cacheDecision = TagStaticAnalysisMemberUnusedDecls.decideForMethod(
                            reducer.context,
                            callable,
                            enableAssertions = true,
                            noCache = false
                        )

                        val errString by lazy {
                            buildString {
                                append("Expected ")
                                with(reducer.context) { append(callable.getQualifiedSignature()) }
                                append(" to be (noCache=true)=")
                                append(noCacheDecision)
                                append(" but got (noCache=false)=")
                                append(cacheDecision)
                                appendLine()
                                append("Inclusion Reasons:")
                                appendLine()
                                callable.inclusionReasonsData.synchronizedWith {
                                    map { it.toReasonString() }
                                        .distinct()
                                        .sorted()
                                        .joinToString("\n") { "- $it" }
                                }.let { append(it) }
                            }
                        }

                        if (isNotFatal) {
                            if (noCacheDecision != cacheDecision) {
                                System.err.println(errString)
                                System.err.println()
                                errorCount += 1
                            }
                        } else {
                            assertEquals(noCacheDecision, cacheDecision, errString)
                        }
                    }

                    else -> RuntimeAssertions.unreachable("${callable::class.simpleName}")
                }
            }

            val enumConsts = cu.findAll<EnumConstantDeclaration>().sortedWith(NodeAstComparator())

            enumConsts.forEach { enumConst ->
                val noCacheDecision = TagStaticAnalysisMemberUnusedDecls.decideForEnumConstant(
                    reducer.context,
                    enumConst,
                    enableAssertions = true,
                    noCache = true
                )
                val cacheDecision = TagStaticAnalysisMemberUnusedDecls.decideForEnumConstant(
                    reducer.context,
                    enumConst,
                    enableAssertions = true,
                    noCache = false
                )

                val errString by lazy {
                    buildString {
                        append("Expected ")
                        with(reducer.context) { append(enumConst.getQualifiedName()) }
                        append(" to be (noCache=true)=")
                        append(noCacheDecision)
                        append(" but got (noCache=false)=")
                        append(cacheDecision)
                        appendLine()
                        append("Inclusion Reasons:")
                        appendLine()
                        enumConst.inclusionReasonsData.synchronizedWith {
                            map { it.toReasonString() }
                                .distinct()
                                .sorted()
                                .joinToString("\n") { "- $it" }
                        }.let { append(it) }
                    }
                }

                if (isNotFatal) {
                    if (noCacheDecision != cacheDecision) {
                        System.err.println(errString)
                        System.err.println()
                        errorCount += 1
                    }
                } else {
                    assertEquals(noCacheDecision, cacheDecision, errString)
                }
            }

            val fieldVars = cu.findAll<VariableDeclarator> { it.isFieldVar }.sortedWith(NodeAstComparator())

            fieldVars.forEach { fieldVar ->
                val noCacheDecision = TagStaticAnalysisMemberUnusedDecls.decideForFieldDecl(
                    reducer.context,
                    fieldVar,
                    enableAssertions = true,
                    noCache = true
                )
                val cacheDecision = TagStaticAnalysisMemberUnusedDecls.decideForFieldDecl(
                    reducer.context,
                    fieldVar,
                    enableAssertions = true,
                    noCache = false
                )

                val errString by lazy {
                    buildString {
                        append("Expected ")
                        with(reducer.context) { append(fieldVar.getQualifiedName()) }
                        append(" to be (noCache=true)=")
                        append(noCacheDecision)
                        append(" but got (noCache=false)=")
                        append(cacheDecision)
                        appendLine()
                        append("Inclusion Reasons:")
                        appendLine()
                        fieldVar.inclusionReasonsData.synchronizedWith {
                            map { it.toReasonString() }
                                .distinct()
                                .sorted()
                                .joinToString("\n") { "- $it" }
                        }.let { append(it) }
                    }
                }

                if (isNotFatal) {
                    if (noCacheDecision != cacheDecision) {
                        System.err.println(errString)
                        System.err.println()
                        errorCount += 1
                    }
                } else {
                    assertEquals(noCacheDecision, cacheDecision, errString)
                }
            }
        }

        if (isNotFatal && errorCount > 0) {
            fail("Inconsistencies detected")
        }
    }

    fun testBaselineReductionCacheStability(reducerCreator: () -> AbstractReducer) {
        val reducer = reducerCreator()
            .also { reducer ->
                reducer.run()
                reducer.taggedCUs
            }

        val cus = reducer.context
            .loadedCompilationUnits
            .filter { it.primaryType.flatMap { it.fullyQualifiedName }.isPresent }
            .sortedBy { it.primaryType.flatMap { it.fullyQualifiedName }.get() }

        cus.forEach { cu ->
            val types = cu.findAll<TypeDeclaration<*>>().sortedWith(NodeAstComparator())

            types.forEach { type ->
                assertEquals(
                    TagGroundTruthUnusedDecls.isTypeReachable(
                        reducer.context,
                        type,
                        enableAssertions = true,
                        noCache = true
                    ),
                    TagGroundTruthUnusedDecls.isTypeReachable(
                        reducer.context,
                        type,
                        enableAssertions = true,
                        noCache = false
                    )
                )
            }

            val callables = cu.findAll<CallableDeclaration<*>>().sortedWith(NodeAstComparator())

            callables.forEach { callable ->
                when (callable) {
                    is ConstructorDeclaration -> {
                        assertEquals(
                            TagGroundTruthUnusedDecls.decideForConstructor(
                                reducer.context,
                                callable,
                                enableAssertions = true,
                                noCache = true
                            ),
                            TagGroundTruthUnusedDecls.decideForConstructor(
                                reducer.context,
                                callable,
                                enableAssertions = true,
                                noCache = false
                            )
                        )
                    }

                    is MethodDeclaration -> {
                        assertEquals(
                            TagGroundTruthUnusedDecls.decideForMethod(
                                reducer.context,
                                callable,
                                enableAssertions = true,
                                noCache = true
                            ),
                            TagGroundTruthUnusedDecls.decideForMethod(
                                reducer.context,
                                callable,
                                enableAssertions = true,
                                noCache = false
                            )
                        )
                    }

                    else -> RuntimeAssertions.unreachable("${callable::class.simpleName}")
                }
            }

            val enumConsts = cu.findAll<EnumConstantDeclaration>().sortedWith(NodeAstComparator())

            enumConsts.forEach { enumConst ->
                assertEquals(
                    TagGroundTruthUnusedDecls.decideForEnumConstant(
                        reducer.context,
                        enumConst,
                        enableAssertions = true,
                        noCache = true
                    ),
                    TagGroundTruthUnusedDecls.decideForEnumConstant(
                        reducer.context,
                        enumConst,
                        enableAssertions = true,
                        noCache = false
                    )
                )
            }

            val fieldVars = cu.findAll<VariableDeclarator> { it.isFieldVar }.sortedWith(NodeAstComparator())

            fieldVars.forEach { fieldVar ->
                val noCacheDecision = TagGroundTruthUnusedDecls.decideForFieldDecl(
                    reducer.context,
                    fieldVar,
                    enableAssertions = true,
                    noCache = true
                )
                val cacheDecision = TagGroundTruthUnusedDecls.decideForFieldDecl(
                    reducer.context,
                    fieldVar,
                    enableAssertions = true,
                    noCache = false
                )

                assertEquals(
                    noCacheDecision,
                    cacheDecision,
                    buildString {
                        append("Expected ")
                        with(reducer.context) { append(fieldVar.getQualifiedName()) }
                        append(" to be (noCache=true)=")
                        append(noCacheDecision)
                        append(" but got (noCache=false)=")
                        append(cacheDecision)
                        appendLine()
                        append("Inclusion Reasons:")
                        appendLine()
                        fieldVar.inclusionReasonsData.synchronizedWith {
                            map { it.toReasonString() }
                                .distinct()
                                .sorted()
                                .joinToString("\n") { "- $it" }
                        }.let { append(it) }
                    }
                )
            }
        }
    }

    fun testParallelReductionCorrectness(reducerCreator: (numThreads: Int?) -> AbstractReducer) {
        val seqReducer = reducerCreator(1)
            .also { reducer ->
                reducer.run()
                reducer.taggedCUs
            }
        val parReducer = reducerCreator(null)
            .also { reducer ->
                reducer.run()
                reducer.taggedCUs
            }

        val seqCUs = seqReducer.context
            .loadedCompilationUnits
            .filter { it.primaryType.flatMap { it.fullyQualifiedName }.isPresent }
            .sortedBy { it.primaryType.flatMap { it.fullyQualifiedName }.get() }
        val parCUs = parReducer.context
            .loadedCompilationUnits
            .filter { it.primaryType.flatMap { it.fullyQualifiedName }.isPresent }
            .sortedBy { it.primaryType.flatMap { it.fullyQualifiedName }.get() }
        assertEquals(parCUs.size, seqCUs.size)

        seqCUs.zip(parCUs).forEachIndexed { cuIdx, (seqCU, parCU) ->
            assertEquals(
                seqCU.primaryType.flatMap { it.fullyQualifiedName }.get(),
                parCU.primaryType.flatMap { it.fullyQualifiedName }.get(),
                "Expected `${seqCU.primaryType.flatMap { it.fullyQualifiedName }.get()}` at index $cuIdx, got `${parCU.primaryType.flatMap { it.fullyQualifiedName }.get()}`"
            )

            val seqImports = seqCU.imports.sortedWith(NodeAstComparator())
            val parImports = seqCU.imports.sortedWith(NodeAstComparator())

            seqImports.zip(parImports).forEach { (seqImport, parImport) ->
                assertEquals(seqImport.range, parImport.range)
                assertEquals(seqImport.nameAsString, parImport.nameAsString)
                assertEquals(seqImport.isStatic, parImport.isStatic)
                assertEquals(seqImport.isAsterisk, parImport.isAsterisk)

                assertEquals(seqImport.isUnusedForRemovalData, parImport.isUnusedForRemovalData)
            }

            val seqTypes = seqCU.findAll<TypeDeclaration<*>>().sortedWith(NodeAstComparator())
            val parTypes = parCU.findAll<TypeDeclaration<*>>().sortedWith(NodeAstComparator())

            seqTypes.zip(parTypes).forEach { (seqType, parType) ->
                assertEquals(seqType.range, parType.range)
                assertEquals(seqType.nameAsString, parType.nameAsString)

                assertEquals(
                    seqType.transformDecisionData,
                    parType.transformDecisionData,
                    buildString {
                        append("Expected ")
                        append(seqType.fullyQualifiedName.getOrDefault(seqType.name))
                        append(" to be ")
                        append(seqType.transformDecisionData)
                        append(" but got ")
                        append(parType.transformDecisionData)
                        appendLine()
                        append("Seq Inclusion Reasons: ")
                        appendLine()
                        seqType.inclusionReasonsData.synchronizedWith {
                            map { it.toReasonString() }.sorted()
                                .joinToString("\n") { "- $it" }
                        }.let { append(it) }
                        appendLine()
                        append("Par Inclusion Reasons: ")
                        appendLine()
                        parType.inclusionReasonsData.synchronizedWith {
                            map { it.toReasonString() }.sorted()
                                .joinToString("\n") { "- $it" }
                        }.let { append(it) }
                    }
                )
            }

            val seqCallables = seqCU.findAll<CallableDeclaration<*>>().sortedWith(NodeAstComparator())
            val parCallables = parCU.findAll<CallableDeclaration<*>>().sortedWith(NodeAstComparator())

            seqCallables.zip(parCallables).forEach { (seqCallable, parCallable) ->
                assertEquals(seqCallable.range, parCallable.range)
                assertEquals(seqCallable.nameAsString, parCallable.nameAsString)
                assertContentEquals(seqCallable.parameters, parCallable.parameters)

                assertEquals(
                    seqCallable.transformDecisionData,
                    parCallable.transformDecisionData,
                    buildString {
                        append("Expected ")
                        with(seqReducer.context) { append(seqCallable.getQualifiedSignature()) }
                        append(" to be ")
                        append(seqCallable.transformDecisionData)
                        append(" but got ")
                        append(parCallable.transformDecisionData)
                        appendLine()
                        append("Seq Inclusion Reasons: ")
                        appendLine()
                        seqCallable.inclusionReasonsData.synchronizedWith {
                            map { it.toReasonString() }.sorted()
                                .joinToString("\n") { "- $it" }
                        }.let { append(it) }
                        appendLine()
                        append("Par Inclusion Reasons: ")
                        appendLine()
                        parCallable.inclusionReasonsData.synchronizedWith {
                            map { it.toReasonString() }.sorted()
                                .joinToString("\n") { "- $it" }
                        }.let { append(it) }
                    }
                )
            }

            val seqEnumConsts = seqCU.findAll<EnumConstantDeclaration>().sortedWith(NodeAstComparator())
            val parEnumConsts = parCU.findAll<EnumConstantDeclaration>().sortedWith(NodeAstComparator())

            seqEnumConsts.zip(parEnumConsts).forEach { (seqEnumConst, parEnumConst) ->
                assertEquals(seqEnumConst.range, parEnumConst.range)
                assertEquals(seqEnumConst.nameAsString, parEnumConst.nameAsString)

                assertEquals(
                    seqEnumConst.transformDecisionData,
                    parEnumConst.transformDecisionData,
                    buildString {
                        append("Expected ")
                        with(seqReducer.context) { append(seqEnumConst.getQualifiedName()) }
                        append(" to be ")
                        append(seqEnumConst.transformDecisionData)
                        append(" but got ")
                        append(parEnumConst.transformDecisionData)
                        appendLine()
                        append("Seq Inclusion Reasons: ")
                        appendLine()
                        seqEnumConst.inclusionReasonsData.synchronizedWith {
                            map { it.toReasonString() }.sorted()
                                .joinToString("\n") { "- $it" }
                        }.let { append(it) }
                        appendLine()
                        append("Par Inclusion Reasons: ")
                        appendLine()
                        parEnumConst.inclusionReasonsData.synchronizedWith {
                            map { it.toReasonString() }.sorted()
                                .joinToString("\n") { "- $it" }
                        }.let { append(it) }
                    }
                )
            }

            val seqFieldVars = seqCU.findAll<VariableDeclarator> { it.isFieldVar }.sortedWith(NodeAstComparator())
            val parFieldVars = parCU.findAll<VariableDeclarator> { it.isFieldVar }.sortedWith(NodeAstComparator())

            seqFieldVars.zip(parFieldVars).forEach { (seqFieldVar, parFieldVar) ->
                assertEquals(seqFieldVar.range, parFieldVar.range)
                assertEquals(seqFieldVar.nameAsString, parFieldVar.nameAsString)
                assertEquals(seqFieldVar.typeAsString, parFieldVar.typeAsString)

                assertEquals(
                    seqFieldVar.transformDecisionData,
                    parFieldVar.transformDecisionData,
                    buildString {
                        append("Expected ")
                        with(seqReducer.context) { append(seqFieldVar.getQualifiedName()) }
                        append(" to be ")
                        append(seqFieldVar.transformDecisionData)
                        append(" but got ")
                        append(parFieldVar.transformDecisionData)
                        appendLine()
                        append("Seq Inclusion Reasons: ")
                        appendLine()
                        seqFieldVar.inclusionReasonsData.synchronizedWith {
                            map { it.toReasonString() }.sorted()
                                .joinToString("\n") { "- $it" }
                        }.let { append(it) }
                        appendLine()
                        append("Par Inclusion Reasons: ")
                        appendLine()
                        parFieldVar.inclusionReasonsData.synchronizedWith {
                            map { it.toReasonString() }.sorted()
                                .joinToString("\n") { "- $it" }
                        }.let { append(it) }
                    }
                )
            }

            val seqAnnoExprs = seqCU.findAll<AnnotationExpr>().sortedWith(NodeAstComparator())
            val parAnnoExprs = parCU.findAll<AnnotationExpr>().sortedWith(NodeAstComparator())

            seqAnnoExprs.zip(parAnnoExprs).forEach { (seqAnnoExpr, parAnnoExpr) ->
                assertEquals(seqAnnoExpr.range, parAnnoExpr.range)
                assertEquals(seqAnnoExpr.nameAsString, parAnnoExpr.nameAsString)

                assertEquals(
                    seqAnnoExpr.transformDecisionData,
                    parAnnoExpr.transformDecisionData,
                    buildString {
                        append("Expected annotation in ")
                        append(seqAnnoExpr.fullRangeString)
                        append(" to be ")
                        append(seqAnnoExpr.transformDecisionData)
                        append(" but got ")
                        append(parAnnoExpr.transformDecisionData)
                    }
                )
            }

            val seqInitDecls = seqCU.findAll<InitializerDeclaration>().sortedWith(NodeAstComparator())
            val parInitDecls = parCU.findAll<InitializerDeclaration>().sortedWith(NodeAstComparator())

            seqInitDecls.zip(parInitDecls).forEach { (seqInitDecl, parInitDecl) ->
                assertEquals(seqInitDecl.range, parInitDecl.range)

                assertEquals(
                    seqInitDecl.transformDecisionData,
                    parInitDecl.transformDecisionData,
                    buildString {
                        append("Expected initializer declaration in ")
                        append(seqInitDecl.fullRangeString)
                        append(" to be ")
                        append(seqInitDecl.transformDecisionData)
                        append(" but got ")
                        append(parInitDecl.transformDecisionData)
                    }
                )
            }
        }
    }
}