package com.derppening.researchprojecttoolkit.util

import java.net.URLClassLoader
import java.nio.file.Path

/**
 * Classloader which loads from a set of directories in the file system.
 */
class FileSystemClassLoader(
    private val classPath: List<Path>,
    parent: ClassLoader? = ClassLoader.getSystemClassLoader()?.parent
) : URLClassLoader(classPath.map { it.toUri().toURL() }.toTypedArray(), parent) {

    override fun toString(): String = "FileSystemClassLoader{classPath=$classPath}"
}
