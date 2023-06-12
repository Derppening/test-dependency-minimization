package com.derppening.researchprojecttoolkit.tool.cache

import com.github.javaparser.symbolsolver.cache.Cache
import java.util.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * A [Cache] implementation which is thread-safe.
 *
 * @param backingCache The cache implementation used to implement this thread-safe cache.
 * @param isFair Whether the lock should be fair. See [ReentrantReadWriteLock.isFair].
 */
class ThreadSafeCache<K, V>(
    private val backingCache: Cache<K, V>,
    isFair: Boolean = false
) : Cache<K, V> {

    private val lk = ReentrantReadWriteLock(isFair)

    init {
        require(backingCache !is ThreadSafeCache<*, *>) {
            "Backing cache for thread-safe cache cannot be an instance of thread-safe cache!"
        }
    }

    override fun put(key: K, value: V) = lk.write { backingCache.put(key, value) }
    override fun get(key: K): Optional<V> = lk.read { backingCache.get(key) }
    override fun remove(key: K) = lk.write { backingCache.remove(key) }
    override fun removeAll() = lk.write { backingCache.removeAll() }
    override fun size(): Long = lk.read { backingCache.size() }
    override fun isEmpty(): Boolean = lk.read { backingCache.isEmpty }
    override fun contains(key: K): Boolean = lk.read { backingCache.contains(key) }
}