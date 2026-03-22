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
package crosslang.v19;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * PipelineBridge_V19 — bridge interface connecting Java and Kotlin pipeline types.
 * References KotlinB types (StageAdapter_V19, PipelineData_V19)
 * and JavaA types (PipelineSource_V19, AbstractPipelineStage_V19).
 */
public interface PipelineBridge_V19 {

    StageAdapter_V19 getAdapter();

    PipelineData_V19 getCurrentData();

    List<PipelineData_V19> collectData(PipelineSource_V19<?> source);

    /**
     * PipelineMetrics_V19 — inner class for tracking pipeline bridge metrics.
     */
    class PipelineMetrics_V19 {

        private final long timestamp;
        private final int stagesCompleted;
        private final StageAdapter_V19 adapter;

        public PipelineMetrics_V19(long timestamp, int stagesCompleted, StageAdapter_V19 adapter) {
            this.timestamp = timestamp;
            this.stagesCompleted = stagesCompleted;
            this.adapter = adapter;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public int getStagesCompleted() {
            return stagesCompleted;
        }

        public StageAdapter_V19 getAdapter() {
            return adapter;
        }
    }
}

/**
 * DefaultPipelineBridge_V19 — default bridge implementation connecting all layers.
 * References JavaA (PipelineSource_V19, AbstractPipelineStage_V19, PipelineConfig_V19)
 * and KotlinB (StageAdapter_V19, PipelineData_V19).
 */
class DefaultPipelineBridge_V19 extends AbstractPipelineStage_V19 implements PipelineBridge_V19 {

    private final PipelineSource_V19<?> source;
    private final StageAdapter_V19 adapter;
    private final PipelineConfig_V19 config;

    DefaultPipelineBridge_V19(PipelineSource_V19<?> source, StageAdapter_V19 adapter, PipelineConfig_V19 config) {
        super(null, "default-bridge");
        this.source = source;
        this.adapter = adapter;
        this.config = config;
    }

    @Override
    public StageAdapter_V19 getAdapter() {
        return adapter;
    }

    @Override
    public PipelineData_V19 getCurrentData() {
        return new PipelineData_V19();
    }

    @Override
    public List<PipelineData_V19> collectData(PipelineSource_V19<?> source) {
        return List.of();
    }

    @Override
    public void execute(PipelineMetrics_V19 metrics) {
        source.onMetrics(metrics);
    }

    public PipelineConfig_V19 getConfig() {
        return config;
    }
}

/**
 * PipelineBridgeListener_V19 — listener for bridge lifecycle events.
 */
interface PipelineBridgeListener_V19 {

    void onBridgeConnected(PipelineBridge_V19 bridge, StageAdapter_V19 adapter);

    void onBridgeDisconnected(PipelineBridge_V19 bridge);

    void onDataReceived(PipelineData_V19 data, PipelineBridge_V19.PipelineMetrics_V19 metrics);
}

/**
 * AsyncPipelineBridge_V19 — async bridge wrapper using CompletableFuture.
 */
class AsyncPipelineBridge_V19 {

    private final PipelineBridge_V19 delegate;

    AsyncPipelineBridge_V19(PipelineBridge_V19 delegate) {
        this.delegate = delegate;
    }

    public CompletableFuture<PipelineData_V19> fetchDataAsync() {
        return CompletableFuture.supplyAsync(delegate::getCurrentData);
    }

    public CompletableFuture<StageAdapter_V19> getAdapterAsync() {
        return CompletableFuture.supplyAsync(delegate::getAdapter);
    }

    public CompletableFuture<List<PipelineData_V19>> collectAsync(PipelineSource_V19<?> source) {
        return CompletableFuture.supplyAsync(() -> delegate.collectData(source));
    }
}

/**
 * BridgeRegistry_V19 — registry mapping names to bridge instances.
 */
class BridgeRegistry_V19 {

    private final Map<String, PipelineBridge_V19> bridges = new java.util.concurrent.ConcurrentHashMap<>();

    public void register(String name, PipelineBridge_V19 bridge) {
        bridges.put(name, bridge);
    }

    public PipelineBridge_V19 lookup(String name) {
        return bridges.get(name);
    }

    public StageAdapter_V19 lookupAdapter(String name) {
        PipelineBridge_V19 bridge = bridges.get(name);
        return bridge != null ? bridge.getAdapter() : null;
    }
}
