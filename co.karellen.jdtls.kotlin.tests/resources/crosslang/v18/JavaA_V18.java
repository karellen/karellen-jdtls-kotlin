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
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * StreamProvider_V18 — generic interface for providing streams via a bridge.
 * References only JavaB types (StreamBridge_V18, StreamMetrics_V18).
 */
public interface StreamProvider_V18<T> {

    StreamBridge_V18 getBridge();

    List<T> provide(StreamBridge_V18 bridge);

    void onMetrics(StreamBridge_V18.StreamMetrics_V18 metrics);
}

/**
 * AbstractStreamProcessor_V18 — abstract processor that uses StreamBridge_V18.
 */
abstract class AbstractStreamProcessor_V18 {

    protected final StreamBridge_V18 bridge;

    protected AbstractStreamProcessor_V18(StreamBridge_V18 bridge) {
        this.bridge = bridge;
    }

    public abstract void process(StreamBridge_V18.StreamMetrics_V18 metrics);

    public StreamBridge_V18 getBridge() {
        return bridge;
    }

    /**
     * StreamFilter_V18 — inner interface for filtering stream elements.
     */
    public interface StreamFilter_V18<E> extends Predicate<E> {

        StreamBridge_V18 getSourceBridge();

        default boolean acceptsMetrics(StreamBridge_V18.StreamMetrics_V18 metrics) {
            return metrics != null;
        }
    }
}

/**
 * StreamConfig_V18 — configuration holder referencing bridge types.
 */
class StreamConfig_V18 {

    private final String name;
    private final int bufferSize;
    private final StreamBridge_V18 bridge;

    StreamConfig_V18(String name, int bufferSize, StreamBridge_V18 bridge) {
        this.name = name;
        this.bufferSize = bufferSize;
        this.bridge = bridge;
    }

    public String getName() {
        return name;
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public StreamBridge_V18 getBridge() {
        return bridge;
    }
}

/**
 * StreamCallback_V18 — functional callback interface using bridge metrics.
 */
interface StreamCallback_V18 {

    void onData(StreamBridge_V18.StreamMetrics_V18 metrics, byte[] data);

    default void onError(StreamBridge_V18 bridge, Throwable error) {
        // default no-op
    }
}

/**
 * StreamProviderFactory_V18 — factory for creating providers via bridges.
 */
class StreamProviderFactory_V18 {

    public <T> StreamProvider_V18<T> create(StreamBridge_V18 bridge, Consumer<T> consumer) {
        return new StreamProvider_V18<T>() {
            @Override
            public StreamBridge_V18 getBridge() {
                return bridge;
            }

            @Override
            public List<T> provide(StreamBridge_V18 b) {
                return List.of();
            }

            @Override
            public void onMetrics(StreamBridge_V18.StreamMetrics_V18 metrics) {
                // delegate
            }
        };
    }
}
