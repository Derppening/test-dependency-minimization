package com.derppening.researchprojecttoolkit.util

import java.nio.file.Path
import kotlin.io.path.Path

/**
 * Prefix tree for [Path].
 *
 * This trie separates each component of a path into a level, which accelerates prefix-based lookup and filtering.
 */
class PathTrie(paths: Collection<Path>) : Set<Path> {

    init {
        check(paths.isEmpty() || paths.all { it.isAbsolute == paths.first().isAbsolute }) {
            "All input paths should be consistently absolute or relative"
        }
    }

    /**
     * Internal node.
     *
     * @property component The path component of this node.
     * @property parent The parent node of this node. If `null`, indicates that this is a root node.
     * @property isPresent Whether the element represented by this node is present in the trie.
     * @property subtrees Sub-components of this tree, if any.
     */
    private class Node(
        val component: Path,
        val parent: Node?,
        var isPresent: Boolean = false,
        val subtrees: MutableList<Node> = mutableListOf()
    ) {

        init {
            check(component.nameCount == 1 || (component.nameCount == 0 && parent == null))
        }

        /**
         * The full path represented by this node.
         *
         * Effectively resolves [component] against the [full path][fullPath] of its [parent]. Returns [component] if
         * this node is the root of the trie.
         */
        val fullPath: Path get() = parent?.fullPath?.resolve(component) ?: component
        override fun toString(): String = "$component ($fullPath)"
    }

    private val root = buildTrie(paths.map { it.normalize() })

    /**
     * Builds a trie from the given [input].
     *
     * @return A [Node] representing the root of the trie, or `null` if the input is empty.
     */
    private fun buildTrie(input: Collection<Path>): Node? {
        if (input.isEmpty()) {
            return null
        }

        val rootComponent = if (input.first().isAbsolute) input.first().root else Path("")
        val trie = input.toSet()
            .fold(Node(rootComponent, null)) { acc, it ->
                addNode(it, acc)
                acc
            }

        return optimizeTrie(trie)
    }

    /**
     * Adds a node to the given trie.
     *
     * @param input The path to add to the trie, relative to the path represented by [root].
     * @param root The trie to add the path to. Can be a sub-trie.
     * @return Inserted or existing [Node] which the [input] represents.
     */
    private tailrec fun addNode(input: Path, root: Node?): Node? {
        root ?: return root

        return when (input.nameCount) {
            0 -> {
                check(root.component == input)

                root.isPresent = true
                root
            }
            1 -> {
                root.subtrees.find { it.component == input.getName(0) }?.also { it.isPresent = true }
                    ?: Node(input.getName(0), root, true).also { root.subtrees.add(it) }
            }
            else -> {
                val childNode = root.subtrees.find { it.component == input.getName(0) }
                    ?: run {
                        Node(input.getName(0), root).also { root.subtrees.add(it) }
                    }

                addNode(input.subpath(1, input.nameCount), childNode)
            }
        }
    }

    /**
     * Optimizes the trie in-place by sorting its sub-tries by its path component.
     *
     * @return [root].
     */
    private fun optimizeTrie(root: Node?): Node? {
        root ?: return root

        return root.also { node ->
            check(node.subtrees.distinctBy { it.component }.size == node.subtrees.size) {
                "Subtrees of node=Node[$node] contains duplicate elements"
            }

            node.subtrees.sortBy { it.component }
            node.subtrees.forEach { optimizeTrie(it) }
        }
    }

    override val size = paths.size

    override fun contains(element: Path): Boolean = findPrefix(element, root)?.isPresent == true

    /**
     * Checks whether any element in this trie contains the given [prefix].
     */
    fun containsPrefix(prefix: Path): Boolean = findPrefix(prefix, root) != null

    /**
     * Finds the [Node] whose path represents the given [prefix].
     *
     * @param prefix The prefix to search in the trie, relative to the path represented by [currentNode].
     * @param currentNode The current traversed node.
     * @return The node representing the [prefix], or `null` if the node is not found.
     */
    private tailrec fun findPrefix(prefix: Path, currentNode: Node?): Node? {
        currentNode ?: return currentNode

        return when (prefix.nameCount) {
            0 -> {
                check(currentNode.component == prefix)
                currentNode
            }
            else -> {
                val idx = currentNode.subtrees.binarySearch { it.component.compareTo(prefix.getName(0)) }
                if (prefix.nameCount == 1) {
                    currentNode.subtrees.getOrNull(idx)
                } else {
                    findPrefix(prefix.subpath(1, prefix.nameCount), currentNode.subtrees.getOrNull(idx))
                }
            }
        }
    }

    /**
     * Returns all paths in this trie with the given [prefix].
     */
    fun getPrefixed(prefix: Path): List<Path> {
        return getNodesPrefix(findPrefix(prefix, root)).map { it.fullPath }
    }

    /**
     * Returns the common [Path] prefix of all elements in this trie.
     */
    fun getCommonPrefix(): Path {
        var currentNode = checkNotNull(root)
        while (currentNode.subtrees.singleOrNull() != null) {
            currentNode = currentNode.subtrees.single()
        }

        return currentNode.fullPath
    }

    /**
     * Returns all top-level paths, i.e. all shortest existing paths from the root.
     */
    // TODO: Add depth parameter
    fun getTopLevelPaths(): List<Path> {
        return getTopLevelPaths(root)
    }

    private fun getTopLevelPaths(node: Node?): List<Path> {
        return when {
            node == null -> emptyList()
            node.isPresent -> listOf(node.fullPath)
            else -> node.subtrees.flatMap { getTopLevelPaths(it) }
        }
    }

    /**
     * Returns all child nodes of this [node] as if traversed in prefix order, including this node itself.
     */
    private fun getNodesPrefix(node: Node?): List<Node> {
        node ?: return emptyList()

        return (listOf(node) + node.subtrees.flatMap { getNodesPrefix(it) })
            .filter { it.isPresent }
    }

    override fun containsAll(elements: Collection<Path>): Boolean = elements.all { contains(it) }

    override fun isEmpty(): Boolean = root == null

    /**
     * Iterator for this class.
     */
    private inner class TrieIterator : Iterator<Path> {

        private val queue = ArrayDeque<Node>()

        init {
            root?.let { queue.addLast(it) }
        }

        /**
         * Finds the next element which is present in the trie.
         */
        private fun findNext(): Node? {
            while (queue.firstOrNull()?.isPresent == false) {
                queue.addAll(queue.removeFirst().subtrees)
            }

            return queue.firstOrNull()
        }

        override fun hasNext(): Boolean = findNext() != null

        override fun next(): Path {
            val elem = findNext() ?: throw NoSuchElementException()
            queue.addAll(queue.removeFirst().subtrees)

            return elem.fullPath
        }
    }

    override fun iterator(): Iterator<Path> = TrieIterator()
}