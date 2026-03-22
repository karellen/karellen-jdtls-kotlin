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
package crosslang.v18;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * StreamBridge_V18 — bridge interface connecting Java and Kotlin stream types.
 * References JavaA types (StreamProvider_V18, AbstractStreamProcessor_V18)
 * and KotlinB types (FlowAdapter_V18, StreamData_V18).
 */
public interface StreamBridge_V18 {

    FlowAdapter_V18 getAdapter();

    StreamData_V18 getCurrentData();

    List<StreamData_V18> collectData(StreamProvider_V18<?> provider);

    /**
     * StreamMetrics_V18 — inner class for tracking bridge metrics.
     */
    class StreamMetrics_V18 {

        private final long timestamp;
        private final int itemsProcessed;
        private final FlowAdapter_V18 adapter;

        public StreamMetrics_V18(long timestamp, int itemsProcessed, FlowAdapter_V18 adapter) {
            this.timestamp = timestamp;
            this.itemsProcessed = itemsProcessed;
            this.adapter = adapter;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public int getItemsProcessed() {
            return itemsProcessed;
        }

        public FlowAdapter_V18 getAdapter() {
            return adapter;
        }
    }
}

/**
 * DefaultStreamBridge_V18 — default bridge implementation connecting all layers.
 * References JavaA (StreamProvider_V18, AbstractStreamProcessor_V18, StreamConfig_V18)
 * and KotlinB (FlowAdapter_V18, StreamData_V18).
 */
class DefaultStreamBridge_V18 extends AbstractStreamProcessor_V18 implements StreamBridge_V18 {

    private final StreamProvider_V18<?> provider;
    private final FlowAdapter_V18 adapter;
    private final StreamConfig_V18 config;

    DefaultStreamBridge_V18(StreamProvider_V18<?> provider, FlowAdapter_V18 adapter, StreamConfig_V18 config) {
        super(null);
        this.provider = provider;
        this.adapter = adapter;
        this.config = config;
    }

    @Override
    public FlowAdapter_V18 getAdapter() {
        return adapter;
    }

    @Override
    public StreamData_V18 getCurrentData() {
        return new StreamData_V18();
    }

    @Override
    public List<StreamData_V18> collectData(StreamProvider_V18<?> provider) {
        return List.of();
    }

    @Override
    public void process(StreamMetrics_V18 metrics) {
        provider.onMetrics(metrics);
    }

    public StreamConfig_V18 getConfig() {
        return config;
    }
}

/**
 * StreamBridgeListener_V18 — listener for bridge lifecycle events.
 */
interface StreamBridgeListener_V18 {

    void onBridgeConnected(StreamBridge_V18 bridge, FlowAdapter_V18 adapter);

    void onBridgeDisconnected(StreamBridge_V18 bridge);

    void onDataReceived(StreamData_V18 data, StreamBridge_V18.StreamMetrics_V18 metrics);
}

/**
 * AsyncStreamBridge_V18 — async bridge wrapper using CompletableFuture.
 */
class AsyncStreamBridge_V18 {

    private final StreamBridge_V18 delegate;

    AsyncStreamBridge_V18(StreamBridge_V18 delegate) {
        this.delegate = delegate;
    }

    public CompletableFuture<StreamData_V18> fetchDataAsync() {
        return CompletableFuture.supplyAsync(delegate::getCurrentData);
    }

    public CompletableFuture<FlowAdapter_V18> getAdapterAsync() {
        return CompletableFuture.supplyAsync(delegate::getAdapter);
    }

    public CompletableFuture<List<StreamData_V18>> collectAsync(StreamProvider_V18<?> provider) {
        return CompletableFuture.supplyAsync(() -> delegate.collectData(provider));
    }
}

/**
 * BridgeRegistry_V18 — registry mapping names to bridge instances.
 */
class BridgeRegistry_V18 {

    private final Map<String, StreamBridge_V18> bridges = new java.util.concurrent.ConcurrentHashMap<>();

    public void register(String name, StreamBridge_V18 bridge) {
        bridges.put(name, bridge);
    }

    public StreamBridge_V18 lookup(String name) {
        return bridges.get(name);
    }

    public FlowAdapter_V18 lookupAdapter(String name) {
        StreamBridge_V18 bridge = bridges.get(name);
        return bridge != null ? bridge.getAdapter() : null;
    }
}
