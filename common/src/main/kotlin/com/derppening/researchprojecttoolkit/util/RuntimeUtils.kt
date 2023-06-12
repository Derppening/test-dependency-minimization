package com.derppening.researchprojecttoolkit.util

import java.io.PrintStream
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.toPath
import kotlin.reflect.KClass

sealed class ClassLoadSource {

    data class JAR(val path: Path) : ClassLoadSource()
    data class Classes(val classpath: Path) : ClassLoadSource()
    data class Other(val path: Path?) : ClassLoadSource()
}

/**
 * @return The source of where [clazz] is loaded from.
 */
fun getClassSource(clazz: Class<*>): ClassLoadSource? {
    return runCatching {
        clazz.protectionDomain.codeSource.location.toURI().toPath()
    }.map {
        if (it.isDirectory()) {
            ClassLoadSource.Classes(it)
        } else if (it.extension == "jar") {
            ClassLoadSource.JAR(it)
        } else {
            ClassLoadSource.Other(it)
        }
    }.getOrNull()
}

fun getClassSource(clazz: KClass<*>): ClassLoadSource? = getClassSource(clazz.java)
inline fun <reified T> getClassSource(): ClassLoadSource? = getClassSource(T::class)

/**
 * @return The stack trace of the caller of this method. The top-most element of the stack trace is the caller of this
 * method, followed by callers of that method, so on.
 */
fun getCallerStackTrace(): List<StackTraceElement> {
    return Throwable().stackTrace.toList().drop(1)
}

private val STDOUT by lazy { System.out }
private val STDERR by lazy { System.err }
private val CLASS_LOGGER_OUT by lazy { PrintStream(ClassLoggerOutputStream { this.info(it) }) }
private val CLASS_LOGGER_ERR by lazy { PrintStream(ClassLoggerOutputStream { this.error(it) }) }
private val STREAM_LOGGER_OUT by lazy { PrintStream(StdStreamLoggerOutputStream("System.out") { this.info(it) }) }
private val STREAM_LOGGER_ERR by lazy { PrintStream(StdStreamLoggerOutputStream("System.err") { this.error(it) }) }

private val SYS_OUT = AtomicReference(STDOUT)
private val SYS_ERR = AtomicReference(STDERR)

/**
 * [System.out] and [System.err] redirection modes.
 */
enum class PrintRedirectMode {
    /**
     * Default mode; Prints to [System.out] or [System.err].
     */
    DEFAULT,

    /**
     * Class logger mode; Prints to logger with the class as its logger name.
     */
    LOGGER_CLASS,

    /**
     * Stream logger mode; Prints to logger with the name of the stream in Java as its logger name.
     */
    LOGGER_STREAM
}

/**
 * Redirects all calls to [PrintStream.print] and [PrintStream.println] to another sink for [System.out] and [System.err].
 */
@Synchronized
fun redirectPrintToLogger(mode: PrintRedirectMode) {
    SYS_OUT.get().flush()
    SYS_ERR.get().flush()

    val out = when (mode) {
        PrintRedirectMode.DEFAULT -> STDOUT
        PrintRedirectMode.LOGGER_CLASS -> CLASS_LOGGER_OUT
        PrintRedirectMode.LOGGER_STREAM -> STREAM_LOGGER_OUT
    }
    val err = when (mode) {
        PrintRedirectMode.DEFAULT -> STDERR
        PrintRedirectMode.LOGGER_CLASS -> CLASS_LOGGER_ERR
        PrintRedirectMode.LOGGER_STREAM -> STREAM_LOGGER_ERR
    }

    SYS_OUT.set(out)
    SYS_ERR.set(err)

    System.setOut(SYS_OUT.get())
    System.setErr(SYS_ERR.get())
}
