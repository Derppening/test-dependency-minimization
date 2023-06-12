package com.derppening.researchprojecttoolkit

import com.derppening.researchprojecttoolkit.util.USER_HOME
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.isDirectory

/**
 * Global configuration of the tool.
 */
class GlobalConfiguration(
    val cmdlineOpts: CommandLineOptions = CommandLineOptions()
) {

    /**
     * Data object for options passed at runtime as command-line flags.
     *
     * @param logJvmGc Whether to log JVM garbage collection.
     */
    class CommandLineOptions(
        baselineDir: Path? = null,
        val logJvmGc: Boolean = false
    ) {

        val baselineDir = baselineDir
            ?: Path("/baseline").takeIf { it.isDirectory() }
            ?: Path("/ssddata/chmakac/baseline").takeIf { it.isDirectory() }
            ?: USER_HOME.resolve("tmp/toolkit/baseline-export").takeIf { it.isDirectory() }
            ?: error("Cannot find baselineDir - Check or specify path")
    }

    init {
        if (!isInstanceInitialized) {
            _instance = this
        }
    }

    companion object {

        private lateinit var _instance: GlobalConfiguration

        /**
         * Whether [INSTANCE] has been initialized yet.
         */
        val isInstanceInitialized: Boolean get() = Companion::_instance.isInitialized

        /**
         * Singleton instance of [GlobalConfiguration].
         *
         * Depends on the constructor parameter, this value may change throughout the execution of the program.
         */
        val INSTANCE: GlobalConfiguration get() = _instance
    }
}