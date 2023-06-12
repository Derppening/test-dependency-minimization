package com.derppening.researchprojecttoolkit.util

import java.nio.file.Path
import kotlin.io.path.*

/**
 * The command-line used to launch this JVM instance, as read from `/proc/self/cmdline`.
 */
private val bootJvmRawCommandLine: String
    get() {
        val cmdline = Path("/proc/self/cmdline")
        check(cmdline.isRegularFile() && cmdline.isReadable())

        return cmdline.readText()
    }

/**
 * The command-line used to launch this JVM instance.
 */
val bootJvmCommandLine: String
    get() = bootJvmRawCommandLine.replace('\u0000', ' ')

/**
 * The command used to invoke this JVM instance.
 */
val bootJvmCommand: String
    get() = bootJvmRawCommandLine.takeWhile { it != '\u0000' }

/**
 * The arguments passed to this JVM instance.
 */
val bootJvmArguments: List<String>
    get() = bootJvmRawCommandLine
        .split('\u0000')
        .drop(1)
        .dropLast(1)

/**
 * The path to `JAVA_HOME` of this JVM instance.
 */
val bootJvmHome: Path
    get() {
        val javaHome = checkNotNull(System.getProperty("java.home"))

        return Path(javaHome).toAbsolutePath().normalize().also { check(it.isDirectory()) }
    }

/**
 * The path to the `java` executable of this JVM instance.
 */
val bootJvmExecutable: Path
    get() = (bootJvmHome / "bin" / "java").normalize().also { check(it.isRegularFile()) }

/**
 * The classpath separator on this platform.
 */
val jvmPathSeparator: String
    get() = checkNotNull(System.getProperty("path.separator"))

/**
 * The classpath of this JVM instance, delimited by [jvmPathSeparator].
 */
val bootJvmClasspath: String
    get() = checkNotNull(System.getProperty("java.class.path"))

/**
 * The classpath of this JVM instance, separated into a [List].
 */
val bootJvmClasspaths: List<String>
    get() = splitClasspath(bootJvmClasspath)

/**
 * The major version of this JVM instance.
 */
val BOOT_JVM_VERSION by lazy {
    System.getProperty("java.runtime.version")
        .takeWhile { it.isDigit() || it == '.' }
        .split('.')
        .take(2)
        .joinToString(".")
        .let {
            if (it.toDouble() < 2.0) {
                it
            } else {
                it.takeWhile { it != '.' }
            }
        }
}

/**
 * The home directory of the current OS user.
 */
val USER_HOME by lazy {
    val userHome = checkNotNull(System.getProperty("user.home"))

    Path(userHome).toAbsolutePath().normalize().also { check(it.isDirectory()) } as Path
}

/**
 * Joins a [List] of class paths into a classpath string.
 */
@JvmName("joinClasspathStrings")
fun joinClasspath(cp: Collection<String>): String =
    cp.joinToString(jvmPathSeparator)

/**
 * Joins a [List] of class paths into a classpath string.
 */
@JvmName("joinClasspathPaths")
fun joinClasspath(cp: Collection<Path>): String =
    joinClasspath(cp.map(Path::toString))

/**
 * Splits a classpath string into a [List] of classpaths.
 */
fun splitClasspath(cp: String): List<String> =
    cp.split(jvmPathSeparator).filter { it.isNotBlank() }
