package com.derppening.researchprojecttoolkit.model

/**
 * Wrapper class for a call graph stored as a points-to graph using [NodeT].
 */
data class CallGraph<NodeT>(
    val edges: List<Edge<NodeT>>,
    val equalityOp: (NodeT, NodeT) -> Boolean = { lhs, rhs -> lhs == rhs }
) {

    data class Edge<NodeT>(val src: NodeT, val tgt: NodeT) {

        constructor(edge: Pair<NodeT, NodeT>) : this(edge.first, edge.second)
    }

    val groupBySrc = edges.groupBy({ it.src }, { it.tgt })

    /**
     * Finds all methods reachable from the [src].
     */
    fun reachableFrom(src: NodeT): Set<NodeT> {
        if (!groupBySrc.containsKey(src)) {
            return emptySet()
        }

        val reachableMethods = mutableSetOf(src)
        val queue = ArrayDeque<NodeT>().apply {
            addAll(groupBySrc[src].orEmpty())
        }
        while (queue.isNotEmpty()) {
            val currentNode = queue.removeFirst()
            reachableMethods.add(currentNode)

            queue.addAll(groupBySrc[currentNode].orEmpty().filterNot { it in reachableMethods || it in queue })
        }
        return reachableMethods
    }
}