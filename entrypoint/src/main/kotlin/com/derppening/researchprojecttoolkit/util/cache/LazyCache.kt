package com.derppening.researchprojecttoolkit.util.cache

import com.derppening.researchprojecttoolkit.tool.cache.ThreadSafeCache
import com.github.javaparser.symbolsolver.cache.Cache
import com.github.javaparser.symbolsolver.cache.InMemoryCache
import kotlin.jvm.optionals.getOrElse

class LazyCache<K, V : Any>(
    backingCache: Cache<K, V> = InMemoryCache(),
    private val mapper: (K) -> CacheDecision<V>
) : SymbolSolverCacheAccess<K, V> {

    private val cache = ThreadSafeCache(backingCache)

    constructor(backingMap: MutableMap<K, V>, mapper: (K) -> CacheDecision<V>) : this(MapCacheAdapter(backingMap), mapper)

    override operator fun get(key: K): V = cache.get(key)
        .getOrElse {
            val value = mapper(key)
            if (value is CacheDecision.Cache) {
                cache.put(key, value.value)
            }

            value.value
        }

    fun clear() = cache.removeAll()
}
