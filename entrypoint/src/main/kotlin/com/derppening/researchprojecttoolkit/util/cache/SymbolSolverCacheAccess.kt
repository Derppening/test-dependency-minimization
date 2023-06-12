package com.derppening.researchprojecttoolkit.util.cache

interface SymbolSolverCacheAccess<K, V : Any> {
    operator fun get(key: K): V
}