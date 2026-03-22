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
import java.util.function.Consumer;

/**
 * PipelineSource_V19 — generic interface for sourcing pipeline data via a bridge.
 * References only JavaB types (PipelineBridge_V19, PipelineMetrics_V19).
 */
public interface PipelineSource_V19<T> {

    PipelineBridge_V19 getBridge();

    List<T> emit(PipelineBridge_V19 bridge);

    void onMetrics(PipelineBridge_V19.PipelineMetrics_V19 metrics);

    boolean isCompatible(PipelineBridge_V19 bridge);
}

/**
 * AbstractPipelineStage_V19 — abstract pipeline stage that uses PipelineBridge_V19.
 */
abstract class AbstractPipelineStage_V19 {

    protected final PipelineBridge_V19 bridge;
    protected final String stageName;

    protected AbstractPipelineStage_V19(PipelineBridge_V19 bridge, String stageName) {
        this.bridge = bridge;
        this.stageName = stageName;
    }

    public abstract void execute(PipelineBridge_V19.PipelineMetrics_V19 metrics);

    public PipelineBridge_V19 getBridge() {
        return bridge;
    }

    public String getStageName() {
        return stageName;
    }

    /**
     * StageCallback_V19 — inner interface for stage lifecycle callbacks.
     */
    public interface StageCallback_V19 {

        void onStageStarted(PipelineBridge_V19 bridge, String stageName);

        void onStageCompleted(PipelineBridge_V19.PipelineMetrics_V19 metrics);

        default void onStageError(PipelineBridge_V19 bridge, Throwable error) {
            // default no-op
        }
    }
}

/**
 * PipelineConfig_V19 — configuration holder for pipeline stages.
 */
class PipelineConfig_V19 {

    private final String name;
    private final int parallelism;
    private final boolean ordered;
    private final PipelineBridge_V19 bridge;

    PipelineConfig_V19(String name, int parallelism, boolean ordered, PipelineBridge_V19 bridge) {
        this.name = name;
        this.parallelism = parallelism;
        this.ordered = ordered;
        this.bridge = bridge;
    }

    public String getName() {
        return name;
    }

    public int getParallelism() {
        return parallelism;
    }

    public boolean isOrdered() {
        return ordered;
    }

    public PipelineBridge_V19 getBridge() {
        return bridge;
    }
}

/**
 * PipelineSourceFactory_V19 — factory for creating pipeline sources via bridges.
 */
class PipelineSourceFactory_V19 {

    public <T> PipelineSource_V19<T> create(PipelineBridge_V19 bridge, Consumer<T> sink) {
        return new PipelineSource_V19<T>() {
            @Override
            public PipelineBridge_V19 getBridge() {
                return bridge;
            }

            @Override
            public List<T> emit(PipelineBridge_V19 b) {
                return List.of();
            }

            @Override
            public void onMetrics(PipelineBridge_V19.PipelineMetrics_V19 metrics) {
                // delegate to sink
            }

            @Override
            public boolean isCompatible(PipelineBridge_V19 b) {
                return bridge.equals(b);
            }
        };
    }
}

/**
 * StageCallbackAdapter_V19 — adapter implementing StageCallback_V19.
 */
class StageCallbackAdapter_V19 implements AbstractPipelineStage_V19.StageCallback_V19 {

    private final PipelineBridge_V19 targetBridge;

    StageCallbackAdapter_V19(PipelineBridge_V19 targetBridge) {
        this.targetBridge = targetBridge;
    }

    @Override
    public void onStageStarted(PipelineBridge_V19 bridge, String stageName) {
        // record start
    }

    @Override
    public void onStageCompleted(PipelineBridge_V19.PipelineMetrics_V19 metrics) {
        // record completion
    }
}
