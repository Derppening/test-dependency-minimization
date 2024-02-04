package com.derppening.researchprojecttoolkit.util

import java.io.InputStream
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.bufferedReader

data class GitUnidiffPatchFile(
    val header: String,
    val extHeader: List<ExtHeader>,
    val files: List<FileDiff>,
) {

    sealed class ExtHeader {
        data class OldMode(val mode: String) : ExtHeader()
        data class NewMode(val mode: String) : ExtHeader()
        data class DeletedFileMode(val mode: String) : ExtHeader()
        data class NewFileMode(val mode: String) : ExtHeader()
        data class CopyFrom(val path: Path) : ExtHeader()
        data class CopyTo(val path: Path) : ExtHeader()
        data class RenameFrom(val path: Path) : ExtHeader()
        data class RenameTo(val path: Path) : ExtHeader()
        data class SimilarityIndex(val value: Int) : ExtHeader()
        data class DissimilarityIndex(val value: Int) : ExtHeader()
        data class Index(val fromHash: String, val toHash: String, val mode: String) : ExtHeader()

        companion object {

            fun parseLineOrNull(line: String): ExtHeader? {
                if (line.startsWith("old mode" )) {
                    return OldMode(line.removePrefix("old mode "))
                }
                if (line.startsWith("new mode" )) {
                    return NewMode(line.removePrefix("new mode "))
                }
                if (line.startsWith("deleted file mode" )) {
                    return DeletedFileMode(line.removePrefix("deleted file mode "))
                }
                if (line.startsWith("new file mode" )) {
                    return NewFileMode(line.removePrefix("new file mode "))
                }
                if (line.startsWith("copy from" )) {
                    return CopyFrom(Path(line.removePrefix("copy from ")))
                }
                if (line.startsWith("copy to" )) {
                    return CopyTo(Path(line.removePrefix("copy to ")))
                }
                if (line.startsWith("rename from" )) {
                    return RenameFrom(Path(line.removePrefix("rename from ")))
                }
                if (line.startsWith("rename to" )) {
                    return RenameTo(Path(line.removePrefix("rename to ")))
                }
                if (line.startsWith("similarity index" )) {
                    return SimilarityIndex(line.removePrefix("similarity index ").toInt())
                }
                if (line.startsWith("dissimilarity index" )) {
                    return DissimilarityIndex(line.removePrefix("dissimilarity index ").toInt())
                }
                if (line.startsWith("index ")) {
                    val tokens = line.split(' ')
                        .also { check(it.size == 3) }
                    val (fromHash, toHash) = tokens[1].split("..")
                        .also { check(it.size == 2) }

                    return Index(fromHash, toHash, tokens[2])
                }

                return null
            }
        }
    }

    data class Span(
        val line: Int,
        val extent: Int,
    ) {

        companion object {

            fun parse(s: String): Span {
                val (line, extent) = s.split(',')
                return Span(line.toInt(), extent.toInt())
            }
        }
    }

    enum class LineType {
        INSERT, DELETE, CONTEXT,
    }

    data class Hunk(
        val fromSpan: Span,
        val toSpan: Span,
        val spanContext: String,
        val lines: List<Pair<LineType, String>>
    ) {

        companion object {

            fun parse(lines: MutableList<String>): Hunk {
                val hunkHeader = lines.removeFirst()
                val (spans, context) = hunkHeader
                    .split("@@")
                    .drop(1)
                    .also { check(it.size == 2) }
                    .map { it.trim() }
                val (fromSpan, toSpan) = spans
                    .split(' ')
                    .map { it.drop(1) }

                val diffLines = buildList {
                    while (true) {
                        if (lines.firstOrNull()?.startsWith("@@") != false) {
                            break
                        }

                        val line = lines.removeFirst()
                        when (line.firstOrNull()) {
                            '+' -> add(LineType.INSERT to line.drop(1))
                            '-' -> add(LineType.DELETE to line.drop(1))
                            ' ' -> add(LineType.CONTEXT to line.drop(1))
                            else -> throw UnsupportedOperationException()
                        }
                    }
                }

                return Hunk(
                    Span.parse(fromSpan),
                    Span.parse(toSpan),
                    context,
                    diffLines
                )
            }
        }
    }

    data class FileDiff(
        val fromFile: Path,
        val toFile: Path,
        val hunks: List<Hunk>,
    ) {

        companion object {

            fun parse(lines: MutableList<String>): FileDiff {
                check(lines.first().startsWith("---"))

                val fromFile = Path(lines.removeFirst().removePrefix("--- "))
                val toFile = Path(lines.removeFirst().removePrefix("+++ "))
                val hunks = buildList {
                    while (lines.getOrNull(0)?.startsWith("@@") == true) {
                        add(Hunk.parse(lines))
                    }
                }

                return FileDiff(fromFile, toFile, hunks)
            }
        }
    }

    companion object {

        private fun fromContent(lines: MutableList<String>): GitUnidiffPatchFile {
            val header = lines.removeFirst()
            val extHeaders = buildList {
                while (true) {
                    val extHeader = ExtHeader.parseLineOrNull(lines.first()) ?: return@buildList
                    add(extHeader)
                    lines.removeFirst()
                }
            }

            val files = buildList {
                while (lines.isNotEmpty()) {
                    while (lines.getOrNull(0)?.isBlank() == true) {
                        lines.removeFirst()
                    }
                    if (lines.isEmpty()) {
                        return@buildList
                    }

                    add(FileDiff.parse(lines))
                }
            }

            return GitUnidiffPatchFile(header, extHeaders, files)
        }

        fun fromFile(p: Path): GitUnidiffPatchFile {
            val lines = p.bufferedReader().readLines().toMutableList()
            return fromContent(lines)
        }

        fun fromInputStream(inStream: InputStream): GitUnidiffPatchFile {
            val lines = inStream.bufferedReader().readLines().toMutableList()
            return fromContent(lines)
        }
    }
}