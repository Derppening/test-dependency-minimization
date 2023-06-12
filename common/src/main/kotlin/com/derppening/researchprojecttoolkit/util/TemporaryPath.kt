package com.derppening.researchprojecttoolkit.util

import java.nio.file.Path
import java.nio.file.attribute.FileAttribute
import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.isDirectory

/**
 * A path which is temporary.
 *
 * This class add [AutoCloseable] to allow automatic resource management for temporary files and directories.
 *
 * @property path The path to the temporary file or directory.
 */
@JvmInline
value class TemporaryPath private constructor(val path: Path) : AutoCloseable {

    override fun close() {
        if (path.isDirectory()) {
            path.toFile().deleteRecursively()
        } else {
            path.deleteIfExists()
        }
    }

    companion object {

        /**
         * Creates a temporary file as if by calling [createTempFile], and wrapping it around [TemporaryPath].
         *
         * @see createTempFile
         */
        fun createFile(
            prefix: String? = null,
            suffix: String? = null,
            vararg attributes: FileAttribute<*>
        ): TemporaryPath = TemporaryPath(createTempFile(prefix, suffix, *attributes))

        /**
         * Creates a temporary file as if by calling [createTempFile], and wrapping it around [TemporaryPath].
         *
         * @see createTempFile
         */
        fun createFile(
            directory: Path? = null,
            prefix: String? = null,
            suffix: String? = null,
            vararg attributes: FileAttribute<*>
        ): TemporaryPath = TemporaryPath(createTempFile(directory, prefix, suffix, *attributes))


        /**
         * Creates a temporary directory as if by calling [createTempDirectory], and wrapping it around [TemporaryPath].
         *
         * @see createTempDirectory
         */
        fun createDirectory(
            prefix: String? = null,
            vararg attributes: FileAttribute<*>
        ): TemporaryPath = TemporaryPath(createTempDirectory(prefix, *attributes))

        /**
         * Creates a temporary directory as if by calling [createTempDirectory], and wrapping it around [TemporaryPath].
         *
         * @see createTempDirectory
         */
        fun createDirectory(
            directory: Path?,
            prefix: String? = null,
            vararg attributes: FileAttribute<*>
        ): TemporaryPath = TemporaryPath(createTempDirectory(directory, prefix, *attributes))
    }
}