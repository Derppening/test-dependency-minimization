package com.derppening.researchprojecttoolkit.util.posix

import com.derppening.researchprojecttoolkit.util.ExitCode
import com.derppening.researchprojecttoolkit.util.Logger
import com.derppening.researchprojecttoolkit.util.posix.Process.LOGGER
import hk.ust.cse.castle.toolkit.jvm.posix.PosixSignal
import java.time.Duration
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.lang.Process as JProcess

private object Process {

    val LOGGER = Logger<Process>()
}

/**
 * @property exitCode Exit code of the process.
 * @property stdout The output read from [Process.getInputStream].
 * @property stderr The output read from [Process.getErrorStream].
 */
open class ExecutionOutput(
    val exitCode: ExitCode,
    val stdout: List<String>,
    val stderr: List<String>
)

/**
 * @property process The resulting [Process] object.
 * @property isTimedOut Whether the process has timed out.
 * @property exitCode Exit code of the process.
 * @property stdout The output read from [Process.getInputStream].
 * @property stderr The output read from [Process.getErrorStream].
 */
class ProcessOutput(
    val process: JProcess,
    val isTimedOut: Boolean,
    exitCode: ExitCode,
    stdout: List<String>,
    stderr: List<String>
) : ExecutionOutput(exitCode, stdout, stderr)

/**
 * Runs a process with the given [command][cmd], [arguments][args].
 *
 * @param timeout The timeout to wait for the process. Disables the timeout if `null`.
 * @param configure Additional configuration for [ProcessBuilder].
 */
fun runProcess(
    cmd: String,
    vararg args: String,
    timeout: Duration? = null,
    configure: ProcessBuilder.() -> Unit = {}
): ProcessOutput =
    runProcess(
        listOf(cmd, *args),
        timeout,
        configure
    )

/**
 * Runs a process with the given [command][command].
 *
 * @param timeout The timeout to wait for the process. Disables the timeout if `null`.
 * @param configure Additional configuration for [ProcessBuilder].
 */
fun runProcess(
    command: List<String>,
    timeout: Duration?,
    configure: ProcessBuilder.() -> Unit = {}
): ProcessOutput {
    val stdoutExecutor = Executors.newSingleThreadExecutor()
    val stderrExecutor = Executors.newSingleThreadExecutor()

    val stdoutBuffer = Collections.synchronizedList(mutableListOf<String>())
    val stderrBuffer = Collections.synchronizedList(mutableListOf<String>())

    try {
        val process = ProcessBuilder(command).apply(configure).start()

        val stdoutFuture = stdoutExecutor.submit<List<String>> {
            process.inputStream
                .bufferedReader()
                .use {
                    while (true) {
                        stdoutBuffer.add(it.readLine() ?: break)
                    }
                }

            stdoutBuffer
        }
        val stderrFuture = stderrExecutor.submit<List<String>> {
            process.errorStream
                .bufferedReader()
                .use {
                    while (true) {
                        stderrBuffer.add(it.readLine() ?: break)
                    }
                }

            stderrBuffer
        }

        val isTimedOut = if (timeout != null) {
            !process.waitFor(timeout.seconds, TimeUnit.SECONDS)
        } else {
            process.waitFor()
            false
        }

        if (isTimedOut) {
            LOGGER.warn("Process timed out - Waiting for process to gracefully shut down")
            process.destroy()

            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                LOGGER.warn("Process timed out - Forcibly stopping process")
                process.destroyForcibly().waitFor()
            }
        }

        val exitCode = ExitCode(if (isTimedOut) (128 + PosixSignal.SIGKILL.getNumber()) else process.exitValue())
        val stdout = runCatching { stdoutFuture.get(5, TimeUnit.SECONDS) }
            .recoverCatching { stdoutBuffer }
            .getOrThrow()
        val stderr = runCatching { stderrFuture.get(5, TimeUnit.SECONDS) }
            .recoverCatching { stderrBuffer }
            .getOrThrow()
        return ProcessOutput(process, isTimedOut, exitCode, stdout, stderr)
    } finally {
        stderrExecutor.shutdown()
        stdoutExecutor.shutdown()
    }
}
