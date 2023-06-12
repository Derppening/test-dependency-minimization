package com.derppening.researchprojecttoolkit.model

import com.derppening.researchprojecttoolkit.util.ToolOutputDirectory
import java.io.BufferedReader
import kotlin.io.path.bufferedReader

/**
 * A set of loaded classes for a test method.
 */
data class LoadedClasses(
    val sourceClasses: List<String>,
    val testClasses: List<String>
) {

    companion object {

        /**
         * Creates a [LoadedClasses] from a [baseline][toolOutDir].
         */
        fun fromBaseline(toolOutDir: ToolOutputDirectory): LoadedClasses {
            return LoadedClasses(
                toolOutDir.getExpectedSourceClassesPath().bufferedReader().use(BufferedReader::readLines).filter(String::isNotBlank),
                toolOutDir.getExpectedTestClassesPath().bufferedReader().use(BufferedReader::readLines).filter(String::isNotBlank),
            )
        }
    }
}