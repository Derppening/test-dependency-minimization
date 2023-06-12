package com.derppening.researchprojecttoolkit.util

import hk.ust.cse.castle.toolkit.jvm.jsl.PredicatedFileCollector
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.reflect.KClass

/**
 * @return The top-level declaring class of this class, or `this` if this class is the top-level class.
 */
tailrec fun KClass<*>.topDeclaringClass(): KClass<*> {
    val declaringClass = this.java.declaringClass ?: return this
    return declaringClass.kotlin.topDeclaringClass()
}

/**
 * Finds all native library directories from the given root directory.
 */
fun findNativeLibraryPaths(root: Path): Set<Path> {
    val nativeLibExt = arrayOf("a", "so")

    val allNativeLibraries = PredicatedFileCollector(root).collect {
        it.extension in nativeLibExt
    }

    return allNativeLibraries
        .map { it.containingDirectory }
        .toSet()
}
