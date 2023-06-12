package com.derppening.researchprojecttoolkit.tool.compilation

import com.derppening.researchprojecttoolkit.util.ExitCode
import com.derppening.researchprojecttoolkit.util.TemporaryPath
import com.derppening.researchprojecttoolkit.util.bootJvmHome
import com.derppening.researchprojecttoolkit.util.posix.ProcessOutput
import com.derppening.researchprojecttoolkit.util.posix.runProcess
import hk.ust.cse.castle.toolkit.jvm.jsl.PredicatedFileCollector
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CountDownLatch
import kotlin.io.path.*

/**
 * Wrapper for invoking the Java compiler.
 *
 * @param sources Collection of paths to sources (either directories or files).
 * @property classpath Classpath to pass to the compiler.
 * @property options Additional options to pass to the compiler.
 * @property jvmHome `JAVA_HOME`.
 */
class CompilerProxy(
    sources: Collection<Path>,
    private val classpath: String,
    private val options: List<String> = emptyList(),
    private val jvmHome: Path = bootJvmHome
) {

    private val filePathsString = sources
        .flatMap { sourcePathToString(it) }
        .distinct()

    private val javacPath: Path

    private val latch = CountDownLatch(1)

    private lateinit var processOutput: ProcessOutput

    /**
     * The [Process] handle to the `javac` compiler process.
     */
    val process: Process
        get() {
            latch.await()
            return processOutput.process
        }

    val isTimedOut: Boolean
        get() {
            latch.await()
            return processOutput.isTimedOut
        }

    val exitCode: ExitCode
        get() {
            latch.await()
            return processOutput.exitCode
        }

    val stdout: List<String>
        get() {
            latch.await()
            return processOutput.stdout
        }

    val stderr: List<String>
        get() {
            latch.await()
            return processOutput.stderr
        }

    init {
        require(jvmHome.isDirectory())
        javacPath = when {
            jvmHome.resolve("bin/javac").isRegularFile() -> jvmHome.resolve("bin/javac")
            jvmHome.resolve("../bin/javac").isRegularFile() -> jvmHome.resolve("../bin/javac")
            else -> error("Cannot find `javac` under \$JAVA_HOME/bin/javac or \$JAVA_HOME/../bin/javac (JAVA_HOME=$jvmHome)")
        }.toAbsolutePath()
    }

    /**
     * Runs the compilation process.
     *
     * @return Whether the compilation succeeded.
     */
    fun run(): Boolean {
        check(latch.count > 0)

        TemporaryPath.createFile().use { tempPath ->
            tempPath.path.bufferedWriter().use { writer ->
                filePathsString.forEach {
                    writer.appendLine(it)
                }
            }

            val cmdline = buildList {
                add(javacPath.toString())

                addAll(options)

                if (classpath.isNotBlank()) {
                    add("-cp")
                    add(classpath)
                }

                add("@${tempPath.path.absolutePathString()}")
            }

            processOutput = runProcess(
                cmdline,
                timeout = Duration.ofSeconds(60)
            )

            latch.countDown()
        }

        return exitCode.isSuccess
    }

    companion object {

        private fun sourcePathToString(path: Path): List<String> {
            check(path.exists())

            return if (path.isDirectory()) {
                PredicatedFileCollector(path)
                    .collect {
                        it.extension == "java"
                    }
                    .map {
                        it.absolutePathString()
                    }
            } else {
                listOf(path.absolutePathString())
            }
        }
    }
}