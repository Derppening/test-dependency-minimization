package com.derppening.researchprojecttoolkit.util.cache

class CacheAccessDelegate<K0, K1, V : Any>(
    private val delegate: SymbolSolverCacheAccess<K1, V>,
    private val mapper: (K0) -> K1
) : SymbolSolverCacheAccess<K0, V> {

    override fun get(key: K0): V = delegate[mapper(key)]
}
