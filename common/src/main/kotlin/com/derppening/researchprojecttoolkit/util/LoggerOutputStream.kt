package com.derppening.researchprojecttoolkit.util

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.OutputStream
import java.io.PrintStream
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * [OutputStream] which writes to a [Logger].
 *
 * @param log Lambda which writes the given message into the logger.
 */
abstract class LoggerOutputStream(private val log: Logger.(String) -> Unit) : OutputStream() {

    private lateinit var ctx: String
    private lateinit var ctxLogger: Logger
    private val lk = ReentrantReadWriteLock(true)
    private val buffer = Collections.synchronizedList(ArrayList<Int>())

    override fun write(b: Int) {
        updateCtx()

        when (b) {
            '\n'.code -> flush()
            else -> buffer.add(b)
        }
    }

    override fun flush() {
        super.flush()

        lk.read {
            ctxLogger.log(String(buffer.toIntArray(), 0, buffer.size))
            buffer.clear()
        }
    }

    /**
     * Gets the logger name for the currently active context.
     */
    protected abstract fun getLoggerName(): String

    /**
     * Updates the cached context of this logger output stream.
     */
    private fun updateCtx() {
        val newCtx = getLoggerName()

        if (::ctx.isInitialized) {
            if (newCtx == ctx) {
                return
            }

            flush()
        }

        lk.write {
            ctx = newCtx
            ctxLogger = LoggerFactory.getLogger(ctx)
        }
    }
}

/**
 * A [LoggerOutputStream] which changes the name of the logger depending on the class which invokes the [OutputStream].
 */
class ClassLoggerOutputStream(log: Logger.(String) -> Unit) : LoggerOutputStream(log) {

    override fun getLoggerName(): String = getCallerStackTrace()
        .takeLastWhile {
            it.className != PrintStream::class.qualifiedName!! && it.methodName !in arrayOf(
                "println",
                "print"
            )
        }
        .first()
        .className
}

/**
 * A [LoggerOutputStream] which uses [System.out] or [System.err] as the name of the logger.
 */
class StdStreamLoggerOutputStream(private val name: String, log: Logger.(String) -> Unit) : LoggerOutputStream(log) {

    override fun getLoggerName(): String = name
}
