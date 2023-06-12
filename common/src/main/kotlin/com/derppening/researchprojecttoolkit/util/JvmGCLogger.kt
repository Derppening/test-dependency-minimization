package com.derppening.researchprojecttoolkit.util

import com.derppening.researchprojecttoolkit.GlobalConfiguration
import hk.ust.cse.castle.toolkit.jvm.ByteUnit
import hk.ust.cse.castle.toolkit.jvm.jsl.addShutdownHook
import hk.ust.cse.castle.toolkit.jvm.jsl.currentRuntime
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.timer

/**
 * Logger for the garbage collector of the Java Virtual Machine.
 */
class JvmGCLogger private constructor() {

    /**
     * A single snapshot of memory usage at a single instant.
     *
     * @property usageMiB Memory usage at the time instant.
     * @property time The time this snapshot is taken at.
     */
    private class MemoryUsageSnapshot private constructor(
        val usageMiB: Long,
        val time: Instant
    ) {

        companion object {

            /**
             * @return An instance of [MemoryUsageSnapshot] representing the current memory usage.
             */
            fun now(): MemoryUsageSnapshot =
                MemoryUsageSnapshot(
                    ByteUnit.MEBIBYTE.convertIntegral(currentRuntime.totalMemory() - currentRuntime.freeMemory()),
                    Instant.now()
                )
        }
    }

    /**
     * Whether to globally enable emitting a new line after the message.
     */
    private val globalEmitNewLine = AtomicBoolean()

    /**
     * Whether to skip the [update] operation for the periodic logger.
     */
    private val skipPeriodicUpdate = AtomicBoolean()

    /**
     * Whether to defer logging the GC message until a GC cycle has completed.
     */
    private val deferLogMsg = AtomicBoolean(true)

    private val prevSnapshot = AtomicReference<MemoryUsageSnapshot>()
    private val preGcSnapshot = AtomicReference<MemoryUsageSnapshot>()
    private val emittedWarningMessage = AtomicBoolean(false)

    /**
     * Sets [skipPeriodicUpdate] to `true`, and runs the given [block].
     *
     * This method is akin to [AutoCloseable.use], guaranteeing that periodic updates will be re-enabled at the end,
     * even if [block] throws an exception.
     */
    private fun runSkipPeriodicUpdate(block: () -> Unit) {
        try {
            skipPeriodicUpdate.set(true)

            // Log the non-explicit GC first if one is under way
            if (preGcSnapshot.get() != null) {
                update()
            }

            block()
        } finally {
            skipPeriodicUpdate.set(false)
        }
    }

    /**
     * Logs a garbage collection event if memory is reclaimed between the [previous snapshot][prev] and the
     * [current snapshot][now].
     *
     * @param emitNewLine Whether or not to emit a new line after outputting the message to the consumer. If `null`,
     * falls back to using [globalEmitNewLine] to determine whether a new line is needed.
     * @param isExplicit Whether this invocation is from an explicit GC. If a GC is explicit but no memory is reclaimed,
     * a message will be outputted to [warnFunc] to indicate that explicit GC may have been disabled.
     * @param logFunc The consumer method which log messages will be outputted to.
     * @param warnFunc The consumer method which warning messages will be outputted to.
     */
    private fun logGc(
        prev: MemoryUsageSnapshot,
        now: MemoryUsageSnapshot,
        emitNewLine: Boolean?,
        isExplicit: Boolean,
        logFunc: (String) -> Unit,
        warnFunc: (String) -> Unit
    ) {
        if (!isExplicit && !GlobalConfiguration.INSTANCE.cmdlineOpts.logJvmGc) {
            return
        }

        val preGcSnapshot = this.preGcSnapshot.get()
        val shouldEmitNewLine = emitNewLine ?: globalEmitNewLine.get()

        if (now.usageMiB >= prev.usageMiB) {
            if (isExplicit && !emittedWarningMessage.get()) {
                emittedWarningMessage.set(true)

                warnFunc("JVM GC: No memory freed - Explicit GC may be disabled!")
                warnFunc("This message will be suppressed for subsequent explicit GC invocations.")
                if (shouldEmitNewLine) {
                    warnFunc("")
                }
            }

            if (preGcSnapshot == null) {
                return
            }
        } else if (!isExplicit && deferLogMsg.get()) {
            return
        }

        val output = buildString {
            append(if (isExplicit) "Explicit: " else "")
            append(preGcSnapshot.usageMiB)
            append(" MB -> ")
            append(now.usageMiB)
            if (isExplicit) {
                val timeTaken = (now.time.toEpochMilli() - preGcSnapshot.time.toEpochMilli())
                val rate = timeTaken.takeIf { it > 0 }
                    ?.let { (preGcSnapshot.usageMiB - now.usageMiB) * 1000 / timeTaken }
                    ?: 0L

                append(" MB in ")
                append(timeTaken)
                append(" ms (")
                append(rate.takeIf { it > 0L } ?: "inf")
                append(" MB/s)")
            } else {
                append(" MB")
            }
            append(if (shouldEmitNewLine) "\n" else "")
        }
        logFunc(output)

        this.preGcSnapshot.set(null)
    }

    /**
     * Updates [prevSnapshot] using the [current snapshot][MemoryUsageSnapshot.now].
     *
     * @return The previous snapshot.
     */
    private fun updateSnapshot(): MemoryUsageSnapshot? {
        return prevSnapshot.getAndSet(MemoryUsageSnapshot.now())
    }

    /**
     * [update] for the periodic logging thread.
     *
     * This method specifically checks whether [skipPeriodicUpdate] is set before invoking the main [update] method.
     */
    private fun periodicUpdate(
        logFunc: (String) -> Unit = DEFAULT_LOG_FUNC,
        warnFunc: (String) -> Unit = DEFAULT_WARN_FUNC
    ) {
        if (skipPeriodicUpdate.get()) {
            return
        }

        update(logFunc = logFunc, warnFunc = warnFunc)
    }

    /**
     * Updates the current memory snapshot information.
     *
     * @param emitNewLine Whether or not to emit a new line after outputting the message to the consumer. If `null`,
     * falls back to using [globalEmitNewLine] to determine whether a new line is needed.
     * @param logFunc The consumer method which log messages will be outputted to.
     * @param warnFunc The consumer method which warning messages will be outputted to.
     */
    fun update(
        emitNewLine: Boolean? = null,
        logFunc: (String) -> Unit = DEFAULT_LOG_FUNC,
        warnFunc: (String) -> Unit = DEFAULT_WARN_FUNC
    ) = update(emitNewLine, false, logFunc, warnFunc)

    /**
     * Updates the current memory snapshot information.
     *
     * @param emitNewLine Whether or not to emit a new line after outputting the message to the consumer. If `null`,
     * falls back to using [globalEmitNewLine] to determine whether a new line is needed.
     * @param isExplicit Whether this invocation is from an explicit GC.
     * @param logFunc The consumer method which log messages will be outputted to.
     * @param warnFunc The consumer method which warning messages will be outputted to.
     */
    private fun update(
        emitNewLine: Boolean?,
        isExplicit: Boolean,
        logFunc: (String) -> Unit = DEFAULT_LOG_FUNC,
        warnFunc: (String) -> Unit = DEFAULT_WARN_FUNC
    ) {
        val prev = updateSnapshot() ?: return
        val current = prevSnapshot.get()

        if (prev.usageMiB > current.usageMiB && preGcSnapshot.get() == null) {
            preGcSnapshot.set(prev)
        }

        logGc(prev, current, emitNewLine, isExplicit, logFunc, warnFunc)
    }

    /**
     * Performs an explicit garbage collection as if by calling [System.gc], and logging the memory snapshot information
     * after the garbage collection.
     *
     * @param emitNewLine Whether or not to emit a new line after outputting the message to the consumer. If `null`,
     * falls back to using [globalEmitNewLine] to determine whether a new line is needed.
     * @param logFunc The consumer method which log messages will be outputted to.
     * @param warnFunc The consumer method which warning messages will be outputted to.
     */
    fun explicitGc(
        emitNewLine: Boolean? = null,
        logFunc: (String) -> Unit = DEFAULT_LOG_FUNC,
        warnFunc: (String) -> Unit = DEFAULT_WARN_FUNC
    ) = runSkipPeriodicUpdate {
        update()
        System.gc()
        update(emitNewLine, true, logFunc, warnFunc)
    }

    /**
     * Whether to globally emit an extra newline after a log message is outputted.
     *
     * This also affects the periodic logger, but can be overridden by [update]'s `emitNewLine` parameter.
     */
    var emitNewLine: Boolean
        get() = globalEmitNewLine.get()
        set(v) = globalEmitNewLine.set(v)

    /**
     * Whether to disable deferred logging for GC messages.
     *
     * Normally, GC messages are buffered and deferred until a cycle is fully completed, which is indicated by an
     * increase of memory usage after decrease (due to GC). Disabling this will cause GC log messages to be outputted
     * even during a cycle, meaning that multiple messages may be outputted if a cycle takes longer than the period of
     * the periodic logger.
     */
    var noDeferredLogging: Boolean
        get() = !deferLogMsg.get()
        set(v) = deferLogMsg.set(!v)

    companion object {

        private val LOGGER = Logger<JvmGCLogger>()
        private const val PERIODIC_LOGGER_NAME = "GC Periodic Logger"

        private val DEFAULT_LOG_FUNC = { it: String -> LOGGER.info(it) }
        private val DEFAULT_WARN_FUNC = { it: String -> LOGGER.warn(it) }

        val INSTANCE = JvmGCLogger()
        private val periodicLogger = AtomicReference<Timer>()

        init {
            currentRuntime.addShutdownHook(name = "$PERIODIC_LOGGER_NAME Shutdown Hook") {
                LOGGER.debug("Shutdown initiated")
                periodicLogger.get()?.apply {
                    cancel()
                    purge()
                }
            }
        }

        /**
         * Enables periodic logging of the JVM GC using a [Timer].
         *
         * @param period The period to check whether a garbage collection has been executed.
         * @param logFuncOverride The consumer method which log messages will be outputted to.
         * @param warnFuncOverride The consumer method which warning messages will be outputted to.
         * @return `true` if the periodic logger is successfully created.
         */
        fun enablePeriodicLogger(
            period: Duration,
            logFuncOverride: (String) -> Unit = DEFAULT_LOG_FUNC,
            warnFuncOverride: (String) -> Unit = DEFAULT_WARN_FUNC
        ): Boolean {
            val timer = timer(
                name = PERIODIC_LOGGER_NAME,
                daemon = true,
                period = period.toMillis()
            ) {
                INSTANCE.periodicUpdate(logFunc = logFuncOverride, warnFunc = warnFuncOverride)
            }

            return if (periodicLogger.compareAndSet(null, timer)) {
                LOGGER.info("Periodic Logger enabled")
                true
            } else {
                LOGGER.warn("Periodic Logger failed to enable")
                false
            }
        }
    }
}