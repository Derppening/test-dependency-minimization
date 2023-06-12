package com.derppening.researchprojecttoolkit.util

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.FileAttribute
import kotlin.io.path.createDirectories
import kotlin.io.path.isDirectory

/**
 * Returns the directory containing this path.
 *
 * If this path represents a file, returns the directory which the file is contained in; Otherwise, returns the
 * directory itself.
 */
val Path.containingDirectory: Path
    get() = if (this.isDirectory()) this else this.parent

/**
 * Clones the directory structure of this [Path] to another path.
 *
 * @param dst Destination directory to copy into.
 * @param depth Depth of directory structure to copy.
 * @param linkOptions [Set] of [LinkOption] to pass to [Files.isDirectory].
 * @param fileAttributes [Set] of [FileAttribute] to pass to [Files.createDirectories].
 * @return [dst]
 */
fun Path.cloneDirectoriesTo(
    dst: Path,
    depth: Int,
    linkOptions: Set<LinkOption> = emptySet(),
    fileAttributes: Set<FileAttribute<*>> = emptySet()
): Path {
    Files.walk(this, depth)
        .use { stream ->
            stream
                .filter { it.isDirectory(*linkOptions.toTypedArray()) }
                .map { dst.resolve(this.relativize(it)) }
                .forEach { it.createDirectories(*fileAttributes.toTypedArray()) }
        }

    return dst
}