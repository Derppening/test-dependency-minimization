package com.derppening.researchprojecttoolkit

import com.derppening.researchprojecttoolkit.commands.*
import com.derppening.researchprojecttoolkit.util.JvmGCLogger
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.check
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.long
import com.github.ajalt.clikt.parameters.types.path
import java.time.Duration

/**
 * Common logic for all subcommands.
 */
private class MainCmd : CliktCommand(name = "research-project-toolkit", printHelpOnEmptyArgs = true) {

    private val baselineDir by option(help = "Directory to the exported baseline properties.")
        .path()
    private val logJvmGc by option(help = "Enable logging of JVM garbage collection.")
        .flag()
    private val logJvmGcPeriod by option(help = "Sets the period of when to update the JVM garbage collection information.")
        .long()
        .default(50L)
        .check("GC logger period must be a non-negative number") { it > 0 }
    private val noDeferGcLog by option(help = "Do not defer GC logging until GC has completed.")
        .flag()

    init {
        subcommands(CMD_LIST)
    }

    override fun run() {
        val cmdlineConfig = GlobalConfiguration.CommandLineOptions(
            baselineDir = baselineDir,
            logJvmGc = logJvmGc
        )

        currentContext.findOrSetObject { GlobalConfiguration(cmdlineConfig) }

        // Start up the logger
        JvmGCLogger.INSTANCE.apply {
            noDeferredLogging = noDeferGcLog
        }
        Duration.ofMillis(logJvmGcPeriod)
            ?.takeIf { logJvmGc && it.toMillis() > 0L }
            ?.let { JvmGCLogger.enablePeriodicLogger(it) }
    }

    companion object {

        private val CMD_LIST = listOf(
            ApplyPatchCmd(),
            BaselineCmd(),
            MinimizeCmd(),
            Defects4JCmd(),
            JavaParserCmd(),
            JunitCmd(),
            SystemCmd(),
            ThesisCmd()
        )
    }
}

fun main(args: Array<String>) {
    MainCmd().main(args)
}
