package com.derppening.researchprojecttoolkit.tool.patch

import com.derppening.researchprojecttoolkit.util.GitUnidiffPatchFile
import com.derppening.researchprojecttoolkit.visitor.ASTDiffGenerator
import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.CompilationUnit
import java.io.BufferedReader
import java.nio.file.Path
import kotlin.io.path.bufferedWriter

object ApplyPatch {

    fun patchFiles(rootDir: Path, patchFile: GitUnidiffPatchFile) {
        patchFile.files.forEach {
            patchFile(rootDir, it)
        }
    }

    private fun patchFile(rootDir: Path, diff: GitUnidiffPatchFile.FileDiff) {
        val (fromAst, toAst) = parseAsts(diff)
        val diffs = ASTDiffGenerator.from(fromAst, toAst)

        val relFilePath = diff.toFile.subpath(1, diff.fromFile.nameCount)
        val absFilePath = rootDir.resolve(relFilePath)

        val testFileAst = StaticJavaParser.parse(absFilePath)
        diffs.forEach { patch ->
            patch(testFileAst)
        }
        absFilePath.bufferedWriter().use { writer ->
            writer.write(testFileAst.toString())
        }
    }

    fun patchFileInMemory(reader: BufferedReader, diff: GitUnidiffPatchFile.FileDiff): CompilationUnit {
        val (fromAst, toAst) = parseAsts(diff)
        val diffs = ASTDiffGenerator.from(fromAst, toAst)

        val testFileAst = StaticJavaParser.parse(reader)
        diffs.forEach { patch ->
            patch(testFileAst)
        }

        return testFileAst
    }

    private fun parseAsts(file: GitUnidiffPatchFile.FileDiff): Pair<CompilationUnit, CompilationUnit> {
        val fromFilePath = file.fromFile.subpath(1, file.fromFile.nameCount)
        val toFilePath = file.toFile.subpath(1, file.fromFile.nameCount)

        require(fromFilePath == toFilePath)
        require(file.hunks.size == 1)

        val hunk = file.hunks.single()

        val fromContent = hunk.lines
            .filter { it.first != GitUnidiffPatchFile.LineType.INSERT }
            .joinToString("\n") { it.second }
        val toContent = hunk.lines
            .filter { it.first != GitUnidiffPatchFile.LineType.DELETE }
            .joinToString("\n") { it.second }

        val fromAst = StaticJavaParser.parse(fromContent)
        if (!fromAst.storage.isPresent) {
            fromAst.setStorage(fromFilePath)
        }
        val toAst = StaticJavaParser.parse(toContent)
        if (!toAst.storage.isPresent) {
            toAst.setStorage(toFilePath)
        }

        return fromAst to toAst
    }
}