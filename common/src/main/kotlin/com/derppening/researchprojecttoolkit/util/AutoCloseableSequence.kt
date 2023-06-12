package com.derppening.researchprojecttoolkit.util

import java.util.stream.BaseStream

/**
 * A [Sequence] which is backed by an [AutoCloseable] resource.
 *
 * This utility class is used to compensate for the lack of [AutoCloseable] for sequences.
 *
 * @param backingResource The resource which needs to be closed when the sequence has been operated upon.
 * @param sequence The [Sequence] which depends on the given [backingResource].
 */
class AutoCloseableSequence<ResT : AutoCloseable, SeqT>(
    private val backingResource: ResT,
    sequence: Sequence<SeqT>
) : Sequence<SeqT> by sequence, AutoCloseable {

    /**
     * @param backingResource The resource which needs to be closed when the sequence has been operated upon.
     * @param sequenceCreator The function which creates a [Sequence] utilizing the resource backed by
     * [backingResource].
     */
    constructor(backingResource: ResT, sequenceCreator: (ResT) -> Sequence<SeqT>)
            : this(backingResource, sequenceCreator(backingResource))

    override fun close() = backingResource.close()
}

/**
 * Creates a [Sequence] with this [BaseStream] as the backing object, enabling try-with-resources to be used to manage
 * the underlying stream.
 */
fun <T> BaseStream<T, *>.asAutoCloseableSequence(): AutoCloseableSequence<BaseStream<T, *>, T> =
    AutoCloseableSequence(this, Sequence { iterator() })

/**
 * Creates a [AutoCloseableSequence] by attaching a [backingResource] to this [Sequence] to allow automatic resource
 * management.
 */
fun <SeqT, ResT : AutoCloseable> Sequence<SeqT>.backedWith(backingResource: ResT): AutoCloseableSequence<ResT, SeqT> =
    AutoCloseableSequence(backingResource, this)

/**
 * Executes the given [block] function on this [Sequence] and close it down correctly if the [Sequence] is backed by an
 * [AutoCloseable] resource.
 */
fun <T, R> Sequence<T>.use(block: (Sequence<T>) -> R): R {
    return if (this !is AutoCloseable) {
        block(this)
    } else {
        (this as AutoCloseable).use { block(this) }
    }
}

/**
 * Closes this [Sequence] if the sequence is [AutoCloseable].
 */
fun <T> Sequence<T>.close() {
    (this as? AutoCloseable)?.close()
}