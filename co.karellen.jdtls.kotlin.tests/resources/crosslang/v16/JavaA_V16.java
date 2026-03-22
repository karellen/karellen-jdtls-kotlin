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
import java.util.function.Function;

/**
 * JavaA references only JavaB types.
 * Provides cache provider abstractions and settings.
 */
public class JavaA_V16 {

    public interface CacheProvider_V16<K, V> {
        CacheBridge_V16 getBridge();

        V lookup(K key, CacheBridge_V16 bridge);

        List<CacheBridge_V16> listBridges();

        Map<K, V> bulkLoad(List<K> keys, Function<K, CacheBridge_V16> bridgeFactory);
    }

    public static abstract class AbstractCacheManager_V16 {
        protected final CacheBridge_V16 bridge;

        public AbstractCacheManager_V16(CacheBridge_V16 bridge) {
            this.bridge = bridge;
        }

        public abstract CacheBridge_V16.CacheStats_V16 getStats();

        public abstract void invalidate(CacheBridge_V16 bridge);

        public interface EvictionPolicy_V16 {
            boolean shouldEvict(CacheBridge_V16 bridge, long entryAge);

            CacheBridge_V16 selectVictim(List<CacheBridge_V16> candidates);
        }

        public static class LruEviction_V16 implements EvictionPolicy_V16 {
            @Override
            public boolean shouldEvict(CacheBridge_V16 bridge, long entryAge) {
                return entryAge > 3600;
            }

            @Override
            public CacheBridge_V16 selectVictim(List<CacheBridge_V16> candidates) {
                return candidates.isEmpty() ? null : candidates.get(0);
            }
        }
    }

    public static class CacheSettings_V16 {
        private final int maxSize;
        private final long ttlMillis;
        private final AbstractCacheManager_V16.EvictionPolicy_V16 policy;

        public CacheSettings_V16(int maxSize, long ttlMillis,
                                 AbstractCacheManager_V16.EvictionPolicy_V16 policy) {
            this.maxSize = maxSize;
            this.ttlMillis = ttlMillis;
            this.policy = policy;
        }

        public int getMaxSize() {
            return maxSize;
        }

        public long getTtlMillis() {
            return ttlMillis;
        }

        public AbstractCacheManager_V16.EvictionPolicy_V16 getPolicy() {
            return policy;
        }
    }

    public interface CacheListener_V16<V> {
        void onEviction(V value, CacheBridge_V16 bridge);

        void onExpiration(V value, CacheBridge_V16 bridge);
    }

    public static class CacheRegion_V16 {
        private final String name;
        private final CacheSettings_V16 settings;

        public CacheRegion_V16(String name, CacheSettings_V16 settings) {
            this.name = name;
            this.settings = settings;
        }

        public String getName() {
            return name;
        }

        public CacheSettings_V16 getSettings() {
            return settings;
        }
    }
}
