package com.derppening.researchprojecttoolkit.util

import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade

/**
 * Clears all memory wherever possible.
 */
fun clearMemory() {
    JavaParserFacade.clearInstances()
    JvmGCLogger.INSTANCE.explicitGc()
}

/**
 * Returns a default set of GC arguments for JVM based on [jvmVersion].
 */
fun getDefaultGcArgs(jvmVersion: String = BOOT_JVM_VERSION): List<String> {
    val jvmMajorVersion = jvmVersion
        .let { if (it.toDouble() < 2.0) it.split(".")[1].toInt() else it.toInt() }
    return when {
        jvmMajorVersion < 9 -> listOf("-XX:+UseG1GC", "-XX:-UseBiasedLocking")
        jvmMajorVersion < 15 -> listOf("-XX:+UseShenandoahGC", "-XX:-UseBiasedLocking")
        else -> listOf("-XX:+UseShenandoahGC")
    }
}
