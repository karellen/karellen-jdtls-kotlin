/*
 * Copyright 2026 Karellen, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package crosslang.v16

/**
 * KotlinA references only KotlinB types
 * (StorageHandler_V16, CacheEntry_V16, StorageAdapter_V16, StorageFactory_V16).
 */

@JvmInline
value class CacheKey_V16(val key: String) {
    fun toStorageKey(): String = "cache:$key"
}

interface StorageService_V16 {
    fun getHandler(): StorageHandler_V16

    fun store(key: CacheKey_V16, entry: CacheEntry_V16): Boolean

    fun retrieve(key: CacheKey_V16): CacheEntry_V16?

    fun listHandlers(): List<StorageHandler_V16>
}

data class CacheConfig_V16(
    val maxEntries: Int,
    val ttlSeconds: Long,
    val compressionEnabled: Boolean,
    val handler: StorageHandler_V16
)

open class CacheManager_V16(
    private val config: CacheConfig_V16
) : StorageService_V16 {

    private val entries = mutableMapOf<CacheKey_V16, CacheEntry_V16>()

    override fun getHandler(): StorageHandler_V16 = config.handler

    override fun store(key: CacheKey_V16, entry: CacheEntry_V16): Boolean {
        if (entries.size >= config.maxEntries) return false
        entries[key] = entry
        return true
    }

    override fun retrieve(key: CacheKey_V16): CacheEntry_V16? = entries[key]

    override fun listHandlers(): List<StorageHandler_V16> = listOf(config.handler)

    inner class CacheIterator_V16 : Iterator<CacheEntry_V16> {
        private val delegate = entries.values.iterator()

        override fun hasNext(): Boolean = delegate.hasNext()

        override fun next(): CacheEntry_V16 = delegate.next()
    }

    companion object {
        const val DEFAULT_MAX_ENTRIES = 10000
        const val DEFAULT_TTL_SECONDS = 3600L

        fun createWithDefaults(handler: StorageHandler_V16): CacheManager_V16 {
            return CacheManager_V16(
                CacheConfig_V16(DEFAULT_MAX_ENTRIES, DEFAULT_TTL_SECONDS, false, handler)
            )
        }

        fun fromFactory(): CacheManager_V16 {
            val adapter = StorageFactory_V16.createAdapter()
            return createWithDefaults(adapter)
        }
    }
}

sealed class CacheState_V16 {
    data class Warm_V16(val hitCount: Long, val handler: StorageHandler_V16) : CacheState_V16()
    data class Cold_V16(val lastAccess: Long) : CacheState_V16()
    data class Expired_V16(val entry: CacheEntry_V16, val expiredAt: Long) : CacheState_V16()
}

enum class CachePolicy_V16 {
    LRU {
        override fun shouldEvict(entry: CacheEntry_V16, ageMillis: Long): Boolean =
            ageMillis > 60000
    },
    LFU {
        override fun shouldEvict(entry: CacheEntry_V16, ageMillis: Long): Boolean =
            ageMillis > 120000
    },
    FIFO {
        override fun shouldEvict(entry: CacheEntry_V16, ageMillis: Long): Boolean =
            ageMillis > 30000
    };

    abstract fun shouldEvict(entry: CacheEntry_V16, ageMillis: Long): Boolean
}

annotation class CacheAnnotation_V16(
    val region: String = "default",
    val ttl: Long = 3600L
)

operator fun CacheKey_V16.plus(other: CacheKey_V16): CacheKey_V16 =
    CacheKey_V16("${this.key}:${other.key}")

operator fun CacheKey_V16.contains(substring: String): Boolean =
    this.key.contains(substring)

fun StorageHandler_V16.findEntries(
    keys: List<CacheKey_V16>,
    filter: (CacheEntry_V16) -> Boolean
): List<CacheEntry_V16> {
    return keys.mapNotNull { key ->
        StorageFactory_V16.createAdapter().let { adapter ->
            adapter.get(key.toStorageKey())
        }
    }.filter(filter)
}

fun <T> StorageHandler_V16.withTransaction(block: (StorageHandler_V16) -> T): T {
    return block(this)
}

class DelegatingStorageService_V16(
    private val delegate: StorageService_V16
) : StorageService_V16 by delegate {
    private var accessCount = 0

    override fun retrieve(key: CacheKey_V16): CacheEntry_V16? {
        accessCount++
        return delegate.retrieve(key)
    }
}

fun processRange_V16(handler: StorageHandler_V16): List<CacheKey_V16> {
    return (1..10).map { i ->
        CacheKey_V16("range-key-$i")
    }
}

fun <K, V> transformEntries_V16(
    entries: Map<K, CacheEntry_V16>,
    transform: (CacheEntry_V16) -> V
): Map<K, V> {
    return entries.mapValues { (_, entry) -> transform(entry) }
}
