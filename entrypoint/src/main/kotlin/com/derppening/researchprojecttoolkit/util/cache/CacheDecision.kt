package com.derppening.researchprojecttoolkit.util.cache

sealed class CacheDecision<V> {

    abstract val value: V

    class Cache<V>(override val value: V) : CacheDecision<V>()
    class NoCache<V>(override val value: V) : CacheDecision<V>()

    companion object {

        fun <V> make(value: V, shouldCache: Boolean) =
            if (shouldCache) Cache(value) else NoCache(value)
    }
}
