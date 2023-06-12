package com.derppening.researchprojecttoolkit.util

import hk.ust.cse.castle.toolkit.jvm.jsl.currentRuntime
import hk.ust.cse.castle.toolkit.jvm.util.RuntimeAssertions
import java.util.concurrent.ForkJoinPool
import kotlin.streams.toList

/**
 * Runs the given [block] in a [ForkJoinPool].
 *
 * @param concurrency Optional number of concurrent tasks that can be executed. If this value is positive, the value
 * will be clipped to the maximum usable threads as specified in [Runtime.availableProcessors]; Otherwise, if the value
 * is negative, the absolute value will be used without clipping.
 * @param block The function to execute.
 */
fun <R> runInForkJoinPool(concurrency: Int? = null, block: () -> R): R {
    val numThreads = when {
        concurrency == 0 || concurrency == null -> currentRuntime.availableProcessors()
        concurrency > 0 -> minOf(concurrency, currentRuntime.availableProcessors())
        concurrency < 0 -> -concurrency
        else -> RuntimeAssertions.unreachable()
    }
    val pool = ForkJoinPool(numThreads)

    return try {
        pool.submit<R> {
            block()
        }.get()
    } finally {
        pool.shutdown()
    }
}

/**
 * Maps the elements in this [List] into another using a [ForkJoinPool].
 *
 * @param threadFactory The thread factory to create threads (which will be displayed when using loggers).
 * @param concurrency Optional number of concurrent tasks that can be executed. This value will be clipped to the
 * maximum usable threads as specified in [Runtime.availableProcessors].
 * @param transform The transformation function.
 */
fun <T : Any, R> List<T>.mapWithThreadPool(
    threadFactory: ForkJoinPool.ForkJoinWorkerThreadFactory = ForkJoinPool.defaultForkJoinWorkerThreadFactory,
    concurrency: Int? = null,
    transform: (T) -> R
): List<R> {
    val numThreads = minOf(concurrency ?: Int.MAX_VALUE, currentRuntime.availableProcessors())
    val pool = ForkJoinPool(numThreads, threadFactory, null, false)

    return try {
        pool.submit<List<R>> {
            parallelStream()
                .map {
                    val result = transform(it)

                    result
                }
                .toList()
        }.get()
    } finally {
        pool.shutdown()
    }
}