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
package crosslang.v15;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Message source interface parameterized by payload type.
 * References only JavaB types (MessageBridge_V15).
 */
public interface MessageSource_V15<T> {

    List<T> poll(MessageBridge_V15 bridge, int maxMessages);

    void acknowledge(MessageBridge_V15 bridge, T payload);

    boolean supports(MessageBridge_V15.BridgeMetrics_V15 metrics);
}

/**
 * Abstract handler with filtering capability.
 * References only JavaB types.
 */
abstract class AbstractHandler_V15 {

    protected MessageBridge_V15 bridge;

    public interface Filter_V15 {
        boolean accept(MessageBridge_V15.BridgeMetrics_V15 metrics);

        default Filter_V15 and(Filter_V15 other) {
            return metrics -> this.accept(metrics) && other.accept(metrics);
        }
    }

    public abstract void handle(MessageBridge_V15 bridge, Object payload);

    protected void logMetrics(MessageBridge_V15.BridgeMetrics_V15 metrics) {
        // logging stub
    }

    static class HandlerState_V15 {
        private boolean active;
        private long lastProcessedTimestamp;

        HandlerState_V15(boolean active) {
            this.active = active;
            this.lastProcessedTimestamp = System.currentTimeMillis();
        }

        boolean isActive() {
            return active;
        }
    }
}

/**
 * Handler configuration holder.
 * References JavaB types for bridge configuration.
 */
class HandlerConfig_V15 {

    private final String name;
    private final int maxRetries;
    private final Map<String, MessageBridge_V15> bridgeMap;
    private final Predicate<MessageBridge_V15.BridgeMetrics_V15> metricsFilter;

    public HandlerConfig_V15(String name, int maxRetries,
                             Map<String, MessageBridge_V15> bridgeMap,
                             Predicate<MessageBridge_V15.BridgeMetrics_V15> metricsFilter) {
        this.name = name;
        this.maxRetries = maxRetries;
        this.bridgeMap = bridgeMap;
        this.metricsFilter = metricsFilter;
    }

    public String getName() {
        return name;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public MessageBridge_V15 getBridge(String key) {
        return bridgeMap.get(key);
    }

    public boolean acceptMetrics(MessageBridge_V15.BridgeMetrics_V15 metrics) {
        return metricsFilter.test(metrics);
    }
}

/**
 * Batch source extending the generic message source.
 * Demonstrates parameterized type usage with JavaB references.
 */
class BatchMessageSource_V15 implements MessageSource_V15<String> {

    private final HandlerConfig_V15 config;

    BatchMessageSource_V15(HandlerConfig_V15 config) {
        this.config = config;
    }

    @Override
    public List<String> poll(MessageBridge_V15 bridge, int maxMessages) {
        return List.of();
    }

    @Override
    public void acknowledge(MessageBridge_V15 bridge, String payload) {
        // no-op
    }

    @Override
    public boolean supports(MessageBridge_V15.BridgeMetrics_V15 metrics) {
        return config.acceptMetrics(metrics);
    }
}

/**
 * Message priority enum used by handlers.
 */
enum MessagePriority_V15 {
    LOW(1),
    MEDIUM(5),
    HIGH(10),
    CRITICAL(100);

    private final int weight;

    MessagePriority_V15(int weight) {
        this.weight = weight;
    }

    public int getWeight() {
        return weight;
    }
}
