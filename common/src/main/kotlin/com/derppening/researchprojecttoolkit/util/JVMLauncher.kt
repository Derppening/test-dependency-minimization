package com.derppening.researchprojecttoolkit.util

import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.isRegularFile

/**
 * Launches a new instance of JVM.
 *
 * @param arguments Arguments passed to the newly created JVM process.
 * @param jvmHome If specified, use the provided path as `JAVA_HOME`.
 * @param classpath Additional classpath elements to pass to the new JVM process.
 * @param inheritBootJvmClasspath If `true`, pass the classpath of this JVM to the new JVM process as well.
 * @param configure Additional configuration to the [ProcessBuilder] instance.
 * @return An instance of [Process] representing the created JVM process.
 */
fun launchJvm(
    arguments: List<String>,
    jvmHome: Path? = null,
    classpath: List<String> = emptyList(),
    inheritBootJvmClasspath: Boolean = true,
    configure: ProcessBuilder.() -> Unit = {}
): Process {
    val javaPath = (jvmHome?.let { it / "bin" / "java" } ?: bootJvmExecutable)
        .also { check(it.isRegularFile()) }
        .toString()
    val cp = if (inheritBootJvmClasspath) {
        classpath + bootJvmClasspaths
    } else {
        classpath
    }.joinToString(jvmPathSeparator)
    val cpArgs = cp.takeIf { it.isNotBlank() }?.let { listOf("-cp", it) } ?: emptyList()

    val pb = ProcessBuilder(listOf(javaPath) + cpArgs + arguments)
        .apply {
            environment()["JAVA_HOME"] = javaPath
        }
        .apply(configure)
    return pb.start()
}

/**
 * @see [launchJvm]
 */
fun launchJvm(
    vararg arguments: String,
    jvmHome: Path? = null,
    classpath: List<String> = emptyList(),
    inheritBootJvmClasspath: Boolean = true,
    configure: ProcessBuilder.() -> Unit = {}
): Process = launchJvm(arguments.toList(), jvmHome, classpath, inheritBootJvmClasspath, configure)
