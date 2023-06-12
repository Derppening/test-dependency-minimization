package com.derppening.researchprojecttoolkit.tool.compilation

import com.derppening.researchprojecttoolkit.util.*
import com.derppening.researchprojecttoolkit.util.posix.ExecutionOutput
import com.derppening.researchprojecttoolkit.util.posix.ProcessOutput
import com.derppening.researchprojecttoolkit.util.posix.runProcess
import org.junit.platform.console.ConsoleLauncher
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CountDownLatch
import kotlin.io.path.*

class JUnitRunnerProxy(
    private val options: List<String>,
    private val workingDir: Path? = null,
    private val jvmOptions: List<String> = emptyList(),
    private val env: Map<String, String> = emptyMap(),
    overrideJvmHome: Path? = null,
    private val inheritIO: Boolean = false
) {

    private val junitConsoleLauncher: String
    private val isJunitConsoleInFatJar: Boolean

    init {
        val junitConsoleStandaloneJar = bootJvmClasspaths
            .singleOrNull {
                Path(it).last()
                    .let { p -> p.extension == "jar" && "$p".startsWith("junit-platform-console-standalone-") }
            }
            ?: JUNIT_RUNNER_CONTAINER_PATH.takeIf { it.exists() }?.toString()

        junitConsoleLauncher = junitConsoleStandaloneJar
            ?: when (val junitConsoleSrc = getClassSource(ConsoleLauncher::class)) {
                is ClassLoadSource.JAR -> junitConsoleSrc.path.toString()
                null -> error("Unable to find JUnit Platform Console Standalone in classpath")
                else -> TODO("Don't know how to use JUnit Platform Console Standalone form $junitConsoleSrc")
            }
        isJunitConsoleInFatJar = junitConsoleStandaloneJar == null
    }

    private val javaPath: Path

    private val latch = CountDownLatch(1)

    lateinit var processOutput: ProcessOutput

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
        val jvmHome = overrideJvmHome ?: bootJvmHome

        require(jvmHome.isDirectory())
        javaPath = when {
            jvmHome.resolve("bin/java").isRegularFile() -> jvmHome.resolve("bin/java")
            jvmHome.resolve("../bin/java").isRegularFile() -> jvmHome.resolve("../bin/java")
            else -> error("Cannot find `java` under \$JAVA_HOME/bin/java or \$JAVA_HOME/../bin/java (JAVA_HOME=$jvmHome)")
        }.toAbsolutePath()
    }

    fun run(): Boolean {
        check(latch.count > 0)

        val cmdline = buildList {
            add(javaPath.toString())
            if (isJunitConsoleInFatJar) {
                val effectiveJvmOptions = jvmOptions.toMutableList()
                    .apply {
                        // Find the last "-cp" option (since this is the one which will take effect), and append the
                        // `junitConsoleLauncher` to the classpath.
                        val idxOfCp = indexOfLast { it == "-cp" || it == "--classpath" }
                        if (idxOfCp in 0..<lastIndex) {
                            this[idxOfCp + 1] = "$junitConsoleLauncher:${this[idxOfCp + 1]}"
                        } else if (idxOfCp == -1) {
                            add("-cp")
                            add(junitConsoleLauncher)
                        }
                    }
                    .toList()

                addAll(effectiveJvmOptions)
                add(ConsoleLauncher::class.qualifiedName!!)
            } else {
                addAll(jvmOptions)
                add("-jar")
                add(junitConsoleLauncher)
            }

            addAll(options)
        }

        processOutput = runProcess(
            cmdline,
            timeout = Duration.ofSeconds(60)
        ) {
            environment().putAll(env)
            workingDir?.also { directory(it.toFile()) }

            if (inheritIO) {
                inheritIO()
            }
        }

        latch.countDown()

        return exitCode.isSuccess
    }

    companion object {

        private val JUNIT_RUNNER_CONTAINER_PATH = Path("/junit/junit-platform-console-standalone.jar")

        private fun runInternal(redirectIO: Boolean, args: List<String>): ExecutionOutput {
            val argsArray = args.toTypedArray()

            return if (redirectIO) {
                val result = ConsoleLauncher.execute(System.out, System.err, *argsArray)

                ExecutionOutput(
                    ExitCode(result.exitCode),
                    emptyList(),
                    emptyList()
                )
            } else {
                val stdout = StringWriter()
                val stderr = StringWriter()

                val result = ConsoleLauncher.execute(PrintWriter(stdout), PrintWriter(stderr), *argsArray)

                ExecutionOutput(
                    ExitCode(result.exitCode),
                    stdout.toString().split('\n'),
                    stderr.toString().split('\n')
                )
            }
        }

        fun help(redirectIO: Boolean = false): ExecutionOutput =
            runInternal(redirectIO, listOf("--help"))

        fun listEngines(redirectIO: Boolean = false): ExecutionOutput =
            runInternal(redirectIO, listOf("--list-engines"))

        fun run(redirectIO: Boolean = false, args: MutableList<String>.() -> Unit): ExecutionOutput =
            runInternal(redirectIO, buildList(args))
    }
}