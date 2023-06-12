package com.derppening.researchprojecttoolkit.util

import hk.ust.cse.castle.toolkit.jvm.jsl.PredicatedFileCollector
import java.nio.file.LinkOption
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile

private fun resolveSymlinkOption(followSymlinks: Boolean): Array<LinkOption> =
    if (followSymlinks) emptyArray() else arrayOf(LinkOption.NOFOLLOW_LINKS)

/**
 * Finds all JRE installations in the given [searchPaths].
 *
 * This method uses `bin/java` as an anchor to locate JRE installations.
 *
 * @param followSymlinks Whether to follow symlinks when descending into subdirectories.
 */
fun findJre(searchPaths: List<Path>, followSymlinks: Boolean = false): List<Path> {
    val linkOptions = resolveSymlinkOption(followSymlinks)
    return searchPaths.flatMap { searchPath ->
        PredicatedFileCollector(searchPath, false).collect {
            it.isDirectory(*linkOptions) && it.resolve("bin/java").isRegularFile()
        }
    }
}

/**
 * Finds all JDK installations in the given [searchPaths].
 *
 * This method uses `bin/javac` as an anchor to locate JRE installations.
 *
 * @param followSymlinks Whether to follow symlinks when descending into subdirectories.
 */
fun findJdk(searchPaths: List<Path>, followSymlinks: Boolean = false): List<Path> {
    val linkOptions = resolveSymlinkOption(followSymlinks)
    return searchPaths.flatMap { searchPath ->
        PredicatedFileCollector(searchPath, false).collect {
            it.isDirectory(*linkOptions) && it.resolve("bin/javac").isRegularFile()
        }
    }
}
