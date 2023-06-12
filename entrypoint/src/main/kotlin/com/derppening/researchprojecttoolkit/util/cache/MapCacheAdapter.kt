package com.derppening.researchprojecttoolkit.util.cache

import com.github.javaparser.symbolsolver.cache.Cache
import java.util.*

class MapCacheAdapter<K, V : Any>(private val map: MutableMap<K, V>) : Cache<K, V> {

    override fun put(key: K, value: V) {
        map[key] = value
    }

    override fun get(key: K): Optional<V> = Optional.ofNullable(map[key])

    override fun remove(key: K) {
        map.remove(key)
    }

    override fun removeAll() = map.clear()
    override fun size(): Long = map.size.toLong()
    override fun isEmpty(): Boolean = map.isEmpty()
    override fun contains(key: K): Boolean = map.contains(key)
}