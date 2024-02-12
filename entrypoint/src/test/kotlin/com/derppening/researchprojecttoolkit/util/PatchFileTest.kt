package com.derppening.researchprojecttoolkit.util

import com.derppening.researchprojecttoolkit.tool.assumeTrue
import kotlin.io.path.Path
import kotlin.test.*

class PatchFileTest {

    @Test
    fun `Test Parse Git Diff File (COLLECTIONS-580)`() {
        val inStream = Thread.currentThread()
            .contextClassLoader
            .getResourceAsStream("COLLECTIONS-580.patch")
            .also { assumeTrue(it != null) }!!

        val patchFile = GitUnidiffPatchFile.fromInputStream(inStream)

        assertEquals("diff --git a/src/test/java/org/apache/commons/collections4/map/MultiValueMapTest.java b/src/test/java/org/apache/commons/collections4/map/MultiValueMapTest.java", patchFile.header)

        val extHeaders = patchFile.extHeader
        val extHeader = assertNotNull(extHeaders.singleOrNull())

        val extIndexHeader = assertIs<GitUnidiffPatchFile.ExtHeader.Index>(extHeader)
        assertEquals("8e66cac9", extIndexHeader.fromHash)
        assertEquals("5b415057", extIndexHeader.toHash)
        assertEquals("100644", extIndexHeader.mode)

        val fileDiffs = patchFile.files
        val fileDiff = assertNotNull(fileDiffs.singleOrNull())
        assertEquals(Path("a/src/test/java/org/apache/commons/collections4/map/MultiValueMapTest.java"), fileDiff.fromFile)
        assertEquals(Path("b/src/test/java/org/apache/commons/collections4/map/MultiValueMapTest.java"), fileDiff.toFile)

        val hunk = assertNotNull(fileDiff.hunks.singleOrNull())
        assertEquals(1, hunk.fromSpan.line)
        assertEquals(444, hunk.fromSpan.extent)
        assertEquals(1, hunk.toSpan.line)
        assertEquals(482, hunk.toSpan.extent)
        assertTrue { hunk.spanContext.isEmpty() }
        assertEquals(444, hunk.lines.filter { it.first == GitUnidiffPatchFile.LineType.CONTEXT }.size)
        assertEquals(38, hunk.lines.filter { it.first == GitUnidiffPatchFile.LineType.INSERT }.size)
        assertEquals(0, hunk.lines.filter { it.first == GitUnidiffPatchFile.LineType.DELETE }.size)
    }

    @Test
    fun `Test Parse Git Diff File (CODEC-117)`() {
        val inStream = Thread.currentThread()
            .contextClassLoader
            .getResourceAsStream("CODEC-117.patch")
            .also { assumeTrue(it != null) }!!

        val patchFile = GitUnidiffPatchFile.fromInputStream(inStream)

        assertEquals("diff --git a/src/test/org/apache/commons/codec/language/CaverphoneTest.java b/src/test/org/apache/commons/codec/language/CaverphoneTest.java", patchFile.header)

        val extHeaders = patchFile.extHeader
        val extHeader = assertNotNull(extHeaders.singleOrNull())

        val extIndexHeader = assertIs<GitUnidiffPatchFile.ExtHeader.Index>(extHeader)
        assertEquals("a4cb492f", extIndexHeader.fromHash)
        assertEquals("00f7d964", extIndexHeader.toHash)
        assertEquals("100644", extIndexHeader.mode)

        val fileDiffs = patchFile.files
        val fileDiff = assertNotNull(fileDiffs.singleOrNull())
        assertEquals(Path("a/src/test/org/apache/commons/codec/language/CaverphoneTest.java"), fileDiff.fromFile)
        assertEquals(Path("b/src/test/org/apache/commons/codec/language/CaverphoneTest.java"), fileDiff.toFile)

        val hunk = assertNotNull(fileDiff.hunks.singleOrNull())
        assertEquals(1, hunk.fromSpan.line)
        assertEquals(350, hunk.fromSpan.extent)
        assertEquals(1, hunk.toSpan.line)
        assertEquals(359, hunk.toSpan.extent)
        assertTrue { hunk.spanContext.isEmpty() }
        assertEquals(350, hunk.lines.filter { it.first == GitUnidiffPatchFile.LineType.CONTEXT }.size)
        assertEquals(9, hunk.lines.filter { it.first == GitUnidiffPatchFile.LineType.INSERT }.size)
        assertEquals(0, hunk.lines.filter { it.first == GitUnidiffPatchFile.LineType.DELETE }.size)
    }
}