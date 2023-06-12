package com.derppening.researchprojecttoolkit.visitor

import com.derppening.researchprojecttoolkit.util.lineRange
import com.github.javaparser.ast.Node
import com.github.javaparser.ast.visitor.GenericVisitorWithDefaults
import kotlin.jvm.optionals.getOrDefault
import kotlin.jvm.optionals.getOrNull

class LineRangeExtractor(private val mode: Mode) : GenericVisitorWithDefaults<Int?, Int>() {

    enum class Mode {
        BEGIN, END
    }

    private val sorter: (Int?, Int?) -> Int? = { a, b ->
        when {
            a == null && b == null -> null
            a != null && b == null -> a
            a == null && b != null -> b
            else -> {
                checkNotNull(a)
                checkNotNull(b)

                when (mode) {
                    Mode.BEGIN -> minOf(a, b)
                    Mode.END -> maxOf(a, b)
                }
            }
        }
    }

    override fun defaultAction(n: Node, arg: Int): Int? {
        if (n.lineRange.map { arg !in it }.getOrDefault(false)) {
            return null
        }

        val childrenRanges = n.childNodes
            .fold<_, Int?>(null) { acc, it ->
                val nodeRange = it.accept(this, arg)

                sorter(acc, nodeRange)
            }

        return childrenRanges ?: run {
            arrayOf(n.begin, n.end)
                .mapNotNull { it.getOrNull() }
                .filter { it.line == arg }
                .map { it.column }
                .fold<_, Int?>(null) { acc, it ->
                    sorter(acc, it)
                }
        }
    }
}