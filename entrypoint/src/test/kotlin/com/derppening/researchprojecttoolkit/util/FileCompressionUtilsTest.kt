package com.derppening.researchprojecttoolkit.util

import com.derppening.researchprojecttoolkit.tool.assumeTrue
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FileCompressionUtilsTest {

    private var tempPath: TemporaryPath? = null

    @Test
    fun `Test Decompress File As String`() {
        val inStream = Thread.currentThread()
            .contextClassLoader
            .getResourceAsStream("file-to-decompress.txt.zst")
            .also { assumeTrue(it != null) }
            .let { it!! }

        val text = FileCompressionUtils.decompressAsString(inStream)
        assertEquals("Hello World!", text)
    }

    @Test
    fun `Test Decompress File As Stream`() {
        val inStream = Thread.currentThread()
            .contextClassLoader
            .getResourceAsStream("file-to-decompress.txt.zst")
            .also { assumeTrue(it != null) }
            .let { it!! }

        val zstdInStream = FileCompressionUtils.decompressAsStream(inStream)
        assertEquals("Hello World!", zstdInStream.reader().readText())
    }

    @Test
    fun `Test File Compression-Decompression Roundtrip`() {
        tempPath = TemporaryPath.createFile()

        Thread.currentThread()
            .contextClassLoader
            .getResourceAsStream("file-to-compress.txt")
            .also { assumeTrue(it != null) }
            .let { it!! }
            .use { inStream ->
                FileCompressionUtils.compress(inStream, tempPath!!.path)
            }

        val text = FileCompressionUtils.decompressAsString(tempPath!!.path)
        assertEquals("Hello World!", text)
    }

    @Test
    fun `Test String Compression-Decompression Roundtrip`() {
        tempPath = TemporaryPath.createFile()

        FileCompressionUtils.compress("Hello World!", tempPath!!.path)

        val text = FileCompressionUtils.decompressAsString(tempPath!!.path)
        assertEquals("Hello World!", text)
    }

    @AfterTest
    fun tearDown() {
        tempPath?.close()
        tempPath = null
    }
}