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
 * KotlinB references JavaB types (CacheBridge_V16, DefaultCacheBridge_V16, CacheStats_V16)
 * and KotlinA types (StorageService_V16, CacheKey_V16, CacheManager_V16, CacheConfig_V16,
 * CacheAnnotation_V16).
 */

interface StorageHandler_V16 {
    fun get(key: String): CacheEntry_V16?

    fun put(key: String, entry: CacheEntry_V16): Boolean

    fun delete(key: String): Boolean

    fun exists(key: String): Boolean
}

data class CacheEntry_V16(
    val key: String,
    val value: ByteArray,
    val createdAt: Long,
    val version: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CacheEntry_V16) return false
        return key == other.key && version == other.version
    }

    override fun hashCode(): Int = key.hashCode() * 31 + version
}

@CacheAnnotation_V16(region = "storage", ttl = 7200L)
class StorageAdapter_V16(
    private val bridge: JavaB_V16.CacheBridge_V16,
    private val service: StorageService_V16
) : StorageHandler_V16 {

    private val stats = JavaB_V16.CacheBridge_V16.CacheStats_V16(0, 0, 0)

    override fun get(key: String): CacheEntry_V16? {
        return bridge.getEntry(key)
    }

    override fun put(key: String, entry: CacheEntry_V16): Boolean {
        bridge.putEntry(key, entry)
        return true
    }

    override fun delete(key: String): Boolean {
        return bridge.removeEntry(key, this)
    }

    override fun exists(key: String): Boolean {
        return bridge.getEntry(key) != null
    }

    fun getStorageService(): StorageService_V16 = service

    fun getBridge(): JavaB_V16.CacheBridge_V16 = bridge

    fun computeHitRate(): Double = stats.hitRate()

    companion object {
        fun withDefaultService(bridge: JavaB_V16.CacheBridge_V16): StorageAdapter_V16 {
            val handler = object : StorageHandler_V16 {
                override fun get(key: String): CacheEntry_V16? = null
                override fun put(key: String, entry: CacheEntry_V16): Boolean = false
                override fun delete(key: String): Boolean = false
                override fun exists(key: String): Boolean = false
            }
            val config = CacheConfig_V16(
                maxEntries = CacheManager_V16.DEFAULT_MAX_ENTRIES,
                ttlSeconds = CacheManager_V16.DEFAULT_TTL_SECONDS,
                compressionEnabled = false,
                handler = handler
            )
            val service = CacheManager_V16(config)
            return StorageAdapter_V16(bridge, service)
        }
    }
}

object StorageFactory_V16 {
    private val adapters = mutableListOf<StorageAdapter_V16>()

    fun createAdapter(): StorageAdapter_V16 {
        val bridge = object : JavaB_V16.CacheBridge_V16 {
            override fun getAdapter(): StorageAdapter_V16 {
                throw UnsupportedOperationException("Factory bridge")
            }

            override fun getEntry(key: String): CacheEntry_V16? = null

            override fun getEntries(keys: List<String>): List<CacheEntry_V16> = emptyList()

            override fun putEntry(key: String, entry: CacheEntry_V16) {}

            override fun removeEntry(key: String, adapter: StorageAdapter_V16): Boolean = false
        }
        val config = CacheConfig_V16(
            maxEntries = CacheManager_V16.DEFAULT_MAX_ENTRIES,
            ttlSeconds = CacheManager_V16.DEFAULT_TTL_SECONDS,
            compressionEnabled = true,
            handler = object : StorageHandler_V16 {
                override fun get(key: String): CacheEntry_V16? = null
                override fun put(key: String, entry: CacheEntry_V16): Boolean = false
                override fun delete(key: String): Boolean = false
                override fun exists(key: String): Boolean = false
            }
        )
        val service = CacheManager_V16(config)
        val adapter = StorageAdapter_V16(bridge, service)
        adapters.add(adapter)
        return adapter
    }

    fun listAdapters(): List<StorageAdapter_V16> = adapters.toList()

    fun clearAll() {
        adapters.clear()
    }
}

fun JavaB_V16.CacheBridge_V16.toStorageAdapter(service: StorageService_V16): StorageAdapter_V16 {
    return StorageAdapter_V16(this, service)
}

fun JavaB_V16.CacheBridge_V16.entryCount(): Int {
    return this.getEntries(emptyList()).size
}

fun JavaB_V16.DefaultCacheBridge_V16.refreshStats(): JavaB_V16.CacheBridge_V16.CacheStats_V16 {
    return this.computeStats()
}

interface StorageEventListener_V16 {
    fun onStore(key: CacheKey_V16, entry: CacheEntry_V16)

    fun onRemove(key: CacheKey_V16, adapter: StorageAdapter_V16)
}
