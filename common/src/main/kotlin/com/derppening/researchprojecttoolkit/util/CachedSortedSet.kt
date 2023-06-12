package com.derppening.researchprojecttoolkit.util

import java.util.*
import kotlin.collections.AbstractSet

class CachedSortedSet<E, K> private constructor(
    private val m: NavigableMap<K, E>,
    val keyMapper: (E) -> K
) : AbstractSet<E>(), NavigableSet<E> {

    constructor(
        keyMapper: (E) -> K,
        comparator: Comparator<in K>? = null,
        initial: Collection<E> = emptyList()
    ) : this(TreeMap<K, E>(comparator), keyMapper) {
        addAll(initial)
    }

    constructor(c: CachedSortedSet<E, K>) : this(c.keyMapper, c.m.comparator(), c) {
        addAll(c)
    }

    override fun iterator(): MutableIterator<E> {
        return object : MutableIterator<E> {

            private val backingIter = m.iterator()

            override fun hasNext(): Boolean = backingIter.hasNext()
            override fun next(): E = backingIter.next().value
            override fun remove() = backingIter.remove()
        }
    }

    override fun descendingIterator(): MutableIterator<E> {
        return object : MutableIterator<E> {

            private val backingIter = m.descendingMap().iterator()

            override fun hasNext(): Boolean = backingIter.hasNext()
            override fun next(): E = backingIter.next().value
            override fun remove() = backingIter.remove()
        }
    }

    override fun descendingSet(): NavigableSet<E> = CachedSortedSet(m.descendingMap(), keyMapper)

    override val size: Int get() = m.size
    override fun isEmpty(): Boolean = m.isEmpty()

    override fun contains(element: E): Boolean = m.containsKey(keyMapper(element))
    override fun add(element: E): Boolean = m.put(keyMapper(element), element) == null
    override fun remove(element: E): Boolean {
        val key = keyMapper(element)
        return if (element != null) m.remove(key) != null else m.remove(key, null)
    }

    override fun clear() = m.clear()

    override fun addAll(elements: Collection<E>): Boolean = elements.fold(false) { acc, it -> add(it) || acc }
    override fun removeAll(elements: Collection<E>): Boolean = elements.fold(false) { acc, it -> remove(it) || acc }
    override fun retainAll(elements: Collection<E>): Boolean {
        var isModified = false
        val iter = iterator()

        while (iter.hasNext()) {
            if (iter.next() !in elements) {
                iter.remove()
                isModified = true
            }
        }

        return isModified
    }

    override fun subSet(fromElement: E, fromInclusive: Boolean, toElement: E, toInclusive: Boolean): NavigableSet<E> =
        CachedSortedSet(m.subMap(keyMapper(fromElement), fromInclusive, keyMapper(toElement), toInclusive), keyMapper)
    override fun headSet(toElement: E, inclusive: Boolean): NavigableSet<E> =
        CachedSortedSet(m.headMap(keyMapper(toElement), inclusive), keyMapper)
    override fun tailSet(fromElement: E, inclusive: Boolean): NavigableSet<E> =
        CachedSortedSet(m.tailMap(keyMapper(fromElement), inclusive), keyMapper)

    override fun subSet(fromElement: E, toElement: E): SortedSet<E> = subSet(fromElement, true, toElement, true)
    override fun headSet(toElement: E): SortedSet<E> = headSet(toElement, false)
    override fun tailSet(fromElement: E): SortedSet<E> = tailSet(fromElement, true)

    override fun comparator(): Comparator<in E> =
        Comparator.comparing(keyMapper, m.comparator() ?: Comparator.comparing {
            @Suppress("UNCHECKED_CAST")
            it as Comparable<Any>
        })
    fun keyComparator(): Comparator<in K> = m.comparator()

    override fun first(): E = (m.firstEntry() ?: throw NoSuchElementException()).value
    override fun last(): E = (m.lastEntry() ?: throw NoSuchElementException()).value

    override fun lower(e: E): E = m.lowerEntry(keyMapper(e)).value
    override fun floor(e: E): E = m.floorEntry(keyMapper(e)).value
    override fun ceiling(e: E): E = m.ceilingEntry(keyMapper(e)).value
    override fun higher(e: E): E = m.higherEntry(keyMapper(e)).value

    override fun pollFirst(): E? = m.pollFirstEntry()?.value
    override fun pollLast(): E? = m.pollLastEntry()?.value
}