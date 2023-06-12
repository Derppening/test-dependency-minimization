package com.derppening.researchprojecttoolkit.util

import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.ArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.CompressorInputStream
import org.apache.commons.compress.compressors.CompressorOutputStream
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorOutputStream
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.FileVisitResult
import java.nio.file.Path
import kotlin.io.path.*

/**
 * Utilities to compress/decompress files.
 */
object FileCompressionUtils {

    private val DEFAULT_ARCHIVER: (OutputStream) -> ArchiveOutputStream = {
        TarArchiveOutputStream(it).apply {
            setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
        }
    }
    private val DEFAULT_EXTRACTOR: (InputStream) -> ArchiveInputStream = ::TarArchiveInputStream
    private val DEFAULT_COMPRESSOR: (OutputStream) -> CompressorOutputStream = ::ZstdCompressorOutputStream
    private val DEFAULT_DECOMPRESSOR: (InputStream) -> CompressorInputStream = ::ZstdCompressorInputStream

    /**
     * Compresses [srcPath] and saves into [destPath].
     *
     * [srcPath] must be a file and not a directory.
     */
    fun compressFile(srcPath: Path, destPath: Path): Path {
        require(srcPath.isRegularFile())

        return srcPath.inputStream().buffered().use { inStream ->
            compress(inStream, destPath)
        }
    }

    /**
     * Compresses [a string][str] and saves into [destPath].
     */
    fun compress(str: String, destPath: Path): Path {
        return ByteArrayInputStream(str.encodeToByteArray()).use {
            compress(it, destPath)
        }
    }

    /**
     * Compresses an [inputStream] and saves into [destPath].
     */
    fun compress(inputStream: InputStream, destPath: Path): Path {
        destPath.outputStream()
            .buffered()
            .let { DEFAULT_COMPRESSOR(it) }
            .use { outStream ->
                inputStream.use { inStream ->
                    inStream.copyTo(outStream)
                }
            }

        return destPath
    }

    private fun compressFileToArchive(outStream: ArchiveOutputStream, rootDir: Path, path: Path): FileVisitResult {
        val relPath = rootDir.relativize(path).toString()

        val entry = outStream.createArchiveEntry(path, relPath)
        outStream.putArchiveEntry(entry)

        if (path.isRegularFile()) {
            path.inputStream().buffered().use { fileInStream ->
                fileInStream.copyTo(outStream)
            }
        }

        outStream.closeArchiveEntry()

        return FileVisitResult.CONTINUE
    }

    /**
     * Compresses [srcDir] into a Zstd-compressed TAR archive and saves into [destPath].
     */
    fun compressDir(srcDir: Path, destPath: Path): Path {
        require(srcDir.isDirectory())

        destPath.outputStream()
            .buffered()
            .let { DEFAULT_COMPRESSOR(it) }
            .let { DEFAULT_ARCHIVER(it) }
            .use { outStream ->
                @OptIn(ExperimentalPathApi::class)
                srcDir.visitFileTree {
                    onPreVisitDirectory { directory, _ ->
                        compressFileToArchive(outStream, srcDir, directory)
                    }

                    onVisitFile { file, _ ->
                        compressFileToArchive(outStream, srcDir, file)
                    }
                }
            }

        return destPath
    }

    /**
     * Decompresses an [inputStream], converting into a standard, uncompressed [InputStream].
     */
    fun decompressAsStream(inputStream: InputStream): InputStream =
        DEFAULT_DECOMPRESSOR(inputStream.buffered())

    /**
     * Decompresses a [file][srcPath], converting into a standard, uncompressed [InputStream].
     */
    fun decompressAsStream(srcPath: Path): InputStream {
        require(srcPath.isRegularFile())

        return decompressAsStream(srcPath.inputStream())
    }

    /**
     * Decompresses an [inputStream], converting into a [String].
     */
    fun decompressAsString(inputStream: InputStream): String {
        return decompressAsStream(inputStream)
            .bufferedReader()
            .use { it.readText() }
    }

    /**
     * Decompresses a [file][srcPath], converting into a [String].
     */
    fun decompressAsString(srcPath: Path): String {
        require(srcPath.isRegularFile())

        return decompressAsString(srcPath.inputStream())
    }

    /**
     * Decompresses a compressed archive and saves all files into [destDir].
     *
     * The directory will be created if it does not already exist. Any existing file in the directory will be
     * overwritten.
     */
    fun decompressDir(srcPath: Path, destDir: Path): Path {
        srcPath.inputStream()
            .buffered()
            .let { DEFAULT_DECOMPRESSOR(it) }
            .let { DEFAULT_EXTRACTOR(it) }
            .use { inStream: ArchiveInputStream ->
                generateSequence { inStream.nextEntry }.forEach { entry ->
                    if (!inStream.canReadEntryData(entry)) {
                        throw IOException("Unable to read entry for $entry")
                    }

                    val outPath = destDir.resolve(entry.name)
                    if (entry.isDirectory) {
                        outPath.createDirectories()
                    } else {
                        outPath.parent.createDirectories()
                        outPath.outputStream().buffered().use { outPathStream ->
                            inStream.copyTo(outPathStream)
                        }
                    }
                }
            }

        return destDir
    }
}