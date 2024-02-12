package com.derppening.researchprojecttoolkit.tool.patch

import com.derppening.researchprojecttoolkit.tool.assumeTrue
import com.derppening.researchprojecttoolkit.util.GitUnidiffPatchFile
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class ApplyPatchTest {

    @Test
    fun `Test Parse AST`() {
        val inStream = Thread.currentThread()
            .contextClassLoader
            .getResourceAsStream("CODEC-117.patch")
            .also { assumeTrue(it != null) }!!

        val patchFile = GitUnidiffPatchFile.fromInputStream(inStream)

        patchFile.files.forEach { fileDiff ->
            val testFileStream = Thread.currentThread()
                .contextClassLoader
                .getResourceAsStream("codec/66555de56715ccb185dee4dd2b25b1e93cc5c73e/src/test/org/apache/commons/codec/language/CaverphoneTest.java")
                .also { assumeTrue(it != null) }!!

            val testFileAst = testFileStream.use {
                ApplyPatch.patchFileInMemory(it.bufferedReader(), fileDiff)
            }
            testFileAst.setStorage(Path("org/apache/commons/codec/language/CaverphoneTest.java"))

            assertTrue {
                testFileAst.imports
                    .any { it.nameAsString == "junit.framework.Assert" }
            }
            assertTrue {
                testFileAst.primaryType.get()
                    .getMethodsByName("testEndMb")
                    .size == 1
            }

            println(testFileAst.toString())
        }
    }
}