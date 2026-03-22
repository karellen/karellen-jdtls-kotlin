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
package crosslang.v16;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JavaB references JavaA types (CacheProvider_V16, AbstractCacheManager_V16, CacheSettings_V16)
 * and KotlinB types (StorageAdapter_V16, CacheEntry_V16).
 */
public class JavaB_V16 {

    public interface CacheBridge_V16 {
        StorageAdapter_V16 getAdapter();

        CacheEntry_V16 getEntry(String key);

        List<CacheEntry_V16> getEntries(List<String> keys);

        void putEntry(String key, CacheEntry_V16 entry);

        boolean removeEntry(String key, StorageAdapter_V16 adapter);

        public static class CacheStats_V16 {
            private final long hits;
            private final long misses;
            private final long evictions;

            public CacheStats_V16(long hits, long misses, long evictions) {
                this.hits = hits;
                this.misses = misses;
                this.evictions = evictions;
            }

            public long getHits() {
                return hits;
            }

            public long getMisses() {
                return misses;
            }

            public long getEvictions() {
                return evictions;
            }

            public double hitRate() {
                long total = hits + misses;
                return total == 0 ? 0.0 : (double) hits / total;
            }
        }
    }

    public static class DefaultCacheBridge_V16 implements CacheBridge_V16 {
        private final JavaA_V16.CacheProvider_V16<String, CacheEntry_V16> provider;
        private final JavaA_V16.AbstractCacheManager_V16 manager;
        private final StorageAdapter_V16 adapter;
        private final Map<String, CacheEntry_V16> localCache = new ConcurrentHashMap<>();

        public DefaultCacheBridge_V16(JavaA_V16.CacheProvider_V16<String, CacheEntry_V16> provider,
                                     JavaA_V16.AbstractCacheManager_V16 manager,
                                     StorageAdapter_V16 adapter) {
            this.provider = provider;
            this.manager = manager;
            this.adapter = adapter;
        }

        @Override
        public StorageAdapter_V16 getAdapter() {
            return adapter;
        }

        @Override
        public CacheEntry_V16 getEntry(String key) {
            return localCache.get(key);
        }

        @Override
        public List<CacheEntry_V16> getEntries(List<String> keys) {
            return keys.stream()
                    .map(localCache::get)
                    .filter(e -> e != null)
                    .collect(java.util.stream.Collectors.toList());
        }

        @Override
        public void putEntry(String key, CacheEntry_V16 entry) {
            localCache.put(key, entry);
        }

        @Override
        public boolean removeEntry(String key, StorageAdapter_V16 adapter) {
            return localCache.remove(key) != null;
        }

        public CacheStats_V16 computeStats() {
            return manager.getStats();
        }
    }

    public interface CacheBridgeFactory_V16 {
        CacheBridge_V16 create(JavaA_V16.CacheSettings_V16 settings, StorageAdapter_V16 adapter);
    }

    public static class CacheBridgeHolder_V16 {
        private final CacheBridge_V16 bridge;
        private final JavaA_V16.CacheRegion_V16 region;

        public CacheBridgeHolder_V16(CacheBridge_V16 bridge, JavaA_V16.CacheRegion_V16 region) {
            this.bridge = bridge;
            this.region = region;
        }

        public CacheBridge_V16 getBridge() {
            return bridge;
        }

        public JavaA_V16.CacheRegion_V16 getRegion() {
            return region;
        }
    }

    public static class BridgeConfiguration_V16 {
        private final JavaA_V16.CacheSettings_V16 settings;
        private final JavaA_V16.AbstractCacheManager_V16.EvictionPolicy_V16 policy;

        public BridgeConfiguration_V16(JavaA_V16.CacheSettings_V16 settings,
                                       JavaA_V16.AbstractCacheManager_V16.EvictionPolicy_V16 policy) {
            this.settings = settings;
            this.policy = policy;
        }

        public JavaA_V16.CacheSettings_V16 getSettings() {
            return settings;
        }

        public JavaA_V16.AbstractCacheManager_V16.EvictionPolicy_V16 getPolicy() {
            return policy;
        }
    }
}
