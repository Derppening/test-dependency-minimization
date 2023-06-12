package com.derppening.researchprojecttoolkit.tool.transform

import com.derppening.researchprojecttoolkit.tool.ReducerContext
import com.derppening.researchprojecttoolkit.tool.appliedPasses
import com.derppening.researchprojecttoolkit.tool.reducer.AbstractReducer
import com.derppening.researchprojecttoolkit.util.UnaryOperatorKt
import com.github.javaparser.ast.CompilationUnit
import java.util.stream.Collectors

sealed interface CompilationUnitTransformer {

    /**
     * Performs the transformation.
     */
    fun apply(cu: CompilationUnit): CompilationUnit

    operator fun invoke(cu: CompilationUnit) = apply(cu)
}

/**
 * A pass which mutates a [CompilationUnit] with the given [context][reducerContext].
 */
sealed interface Pass : CompilationUnitTransformer {

    /**
     * Type utilities bound to the context of the [AbstractReducer].
     */
    val reducerContext: ReducerContext
}

/**
 * Marks that this [cu] has been invoked by this instance of [Pass].
 */
fun Pass.markInvoked(cu: CompilationUnit) {
    cu.appliedPasses.add(this)
}

/**
 * Verifies that this pass is only invoked once on [cu].
 */
inline fun <reified PassT : Pass> PassT.verifyIsInvokedOnce(cu: CompilationUnit) {
    check(cu.appliedPasses.filterIsInstance<PassT>().none()) {
        "${this::class.simpleName} cannot be invoked multiple times"
    }
    markInvoked(cu)
}

/**
 * Verifies that this pass is invoked before [PassT] was invoked on [cu].
 */
inline fun <reified PassT : Pass> Pass.verifyIsInvokedBefore(cu: CompilationUnit) {
    check(cu.appliedPasses.filterIsInstance<PassT>().none()) {
        "${PassT::class.simpleName} must be invoked before ${this::class.simpleName} is invoked"
    }
}

/**
 * Verifies that this pass is invoked after [PassT] has been invoked on [cu].
 */
inline fun <reified PassT : Pass> Pass.verifyIsInvokedAfter(cu: CompilationUnit) {
    check(cu.appliedPasses.filterIsInstance<PassT>().any()) {
        "${this::class.simpleName} must be invoked after ${PassT::class.simpleName} is invoked (${cu.appliedPasses})"
    }
}

sealed interface Pipeline<PassT : Pass> : CompilationUnitTransformer

/**
 * A transform pass which transforms a [CompilationUnit] in-place.
 */
interface TransformPass : Pass {

    /**
     * Performs the transformation.
     */
    fun transform(cu: CompilationUnit)

    override fun apply(cu: CompilationUnit): CompilationUnit = cu.also { transform(it) }
}

fun TransformPass(
    reducerContext: ReducerContext,
    transformBlock: (CompilationUnit) -> Unit
) = object : TransformPass {

    override val reducerContext = reducerContext
    override fun transform(cu: CompilationUnit) = transformBlock(cu)
}

@JvmInline
value class TransformPipeline(private val pipeline: List<TransformPass>) : Pipeline<TransformPass> {

    constructor(vararg passes: TransformPass) : this(passes.toList())
    constructor(pipeline: TransformPipeline, vararg passes: TransformPass) :
            this(pipeline.pipeline + passes.toList())

    override fun apply(cu: CompilationUnit): CompilationUnit = pipeline.fold(cu) { acc, it ->
        it.apply(acc)
    }
}

fun TransformPass.toMutatingPass() = MutatingTransformPass(reducerContext) { this(it) }
fun TransformPass.toPipeline() = TransformPipeline(this)
infix fun TransformPass.andThen(pass: TransformPass) = TransformPipeline(this, pass)
infix fun TransformPipeline.andThen(pass: TransformPass) = TransformPipeline(this, pass)

interface MutatingTransformPass : Pass {

    /**
     * Performs the transformation.
     */
    fun transform(cu: CompilationUnit): CompilationUnit

    override fun apply(cu: CompilationUnit): CompilationUnit = transform(cu)
}

fun MutatingTransformPass(
    reducerContext: ReducerContext,
    transformBlock: (CompilationUnit) -> CompilationUnit
) = object : MutatingTransformPass {

    override val reducerContext = reducerContext
    override fun transform(cu: CompilationUnit) = transformBlock(cu)
}

@JvmInline
value class MutatingTransformPipeline(private val pipeline: List<MutatingTransformPass>) :
    Pipeline<MutatingTransformPass> {

    constructor(vararg passes: MutatingTransformPass) : this(passes.toList())
    constructor(pipeline: MutatingTransformPipeline, vararg passes: MutatingTransformPass) :
            this(pipeline.pipeline + passes.toList())

    override fun apply(cu: CompilationUnit) = pipeline.fold(cu) { acc, it ->
        it.apply(acc)
    }
}

fun MutatingTransformPass.toPipeline() = MutatingTransformPipeline(this)
infix fun MutatingTransformPass.andThen(pass: MutatingTransformPass) = MutatingTransformPipeline(this, pass)
infix fun MutatingTransformPipeline.andThen(pass: MutatingTransformPass) = MutatingTransformPipeline(this, pass)

@JvmInline
value class PhasedPipeline private constructor(
    private val pipelines: MutableList<UnaryOperatorKt<Collection<CompilationUnit>>>
) {

    constructor() : this(mutableListOf())

    fun phase(parallel: Boolean = true, transformation: () -> CompilationUnitTransformer): PhasedPipeline {
        pipelines.add(UnaryOperatorKt {
            with(it) { if (parallel) parallelStream() else stream() }
                .map { cu -> transformation()(cu) }
                .collect(Collectors.toList())
        })

        return this
    }

    operator fun invoke(cus: List<CompilationUnit>) = pipelines.fold(cus as Collection<CompilationUnit>) { acc, op ->
        op(acc)
    }.toList()
}

fun PhasedPipeline(block: PhasedPipeline.() -> Unit) =
    PhasedPipeline().apply(block)
