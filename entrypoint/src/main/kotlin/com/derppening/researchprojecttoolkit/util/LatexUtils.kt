package com.derppening.researchprojecttoolkit.util

fun reflowLatexTable(inputTableLines: List<String>): List<String> {
    if (inputTableLines.isEmpty()) {
        return inputTableLines
    }

    val elemsPerLine = inputTableLines.first { it.contains('&') }.split('&').size
    check(inputTableLines.all { line -> line.split('&').size.let { it == 1 || it == elemsPerLine } })

    val maxLenByCol = inputTableLines.fold(MutableList(elemsPerLine) { 0 }) { acc, it ->
        val split = it.trim().removeSuffix("\\\\").split('&')

        if (split.size == elemsPerLine) {
            split.forEachIndexed { idx, s ->
                acc[idx] = maxOf(acc[idx], s.trim().length)
            }
        }

        acc
    }.toList()

    return inputTableLines.map {
        val split = it.trim().removeSuffix("\\\\").split('&')

        if (split.size == elemsPerLine) {
            split.mapIndexed { idx, s -> s.trim().padEnd(maxLenByCol[idx]) }.joinToString(" & ", postfix = " \\\\")
        } else {
            it.trim()
        }
    }
}

fun reflowLatexTable(inputTable: String): String {
    return reflowLatexTable(inputTable.split('\n')).joinToString("\n")
}

fun reflowLatexTableStdin() {
    val input = generateSequence { readln().takeIf { it.isNotBlank() } }.toList()
    println(reflowLatexTable(input).joinToString("\n"))
}
