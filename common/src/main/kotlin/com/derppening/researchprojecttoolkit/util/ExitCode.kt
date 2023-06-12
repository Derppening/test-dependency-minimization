package com.derppening.researchprojecttoolkit.util

import hk.ust.cse.castle.toolkit.jvm.posix.PosixSignal
import hk.ust.cse.castle.toolkit.jvm.posix.ShReservedExitCode

/**
 * Wrapper class around an exit code.
 *
 * @property code The actual code returned by the process.
 */
@JvmInline
value class ExitCode(val code: Int) {

    val isSuccess: Boolean get() = code == 0
    val isFailure: Boolean get() = code != 0

    /**
     * The interpretation of this exit code as returned by a shell.
     */
    val shellError: ShReservedExitCode? get() = ShReservedExitCode.resolveExitCode(code)

    /**
     * The interpretation of this exit code as if it has been terminated by a Unix signal.
     */
    val errorSignal: PosixSignal? get() = PosixSignal.getBySignalNumber(code - 128)

    /**
     * @return A descriptive string on the status of the exit code.
     */
    fun asDescriptiveString(): String {
        return if (isSuccess) {
            "Success"
        } else {
            "Failure (${errorSignal?.let { "${it.name} - ${it.toDescriptiveString()}" } ?: code})"
        }
    }

    override fun toString(): String = code.toString()
}
