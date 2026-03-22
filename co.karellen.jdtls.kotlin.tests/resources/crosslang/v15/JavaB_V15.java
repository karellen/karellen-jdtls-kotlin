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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bridge interface connecting Java sources to Kotlin channel adapters.
 * References KotlinB types: ChannelAdapter_V15, MessagePayload_V15.
 */
public interface MessageBridge_V15 {

    void send(MessagePayload_V15 payload, ChannelAdapter_V15 adapter);

    MessagePayload_V15 receive(ChannelAdapter_V15 adapter, long timeoutMs);

    List<ChannelAdapter_V15> listAdapters();

    /**
     * Inner metrics class tracking bridge statistics.
     */
    class BridgeMetrics_V15 {
        private long messagesSent;
        private long messagesReceived;
        private long errorCount;

        public BridgeMetrics_V15() {
            this.messagesSent = 0;
            this.messagesReceived = 0;
            this.errorCount = 0;
        }

        public void recordSent() {
            messagesSent++;
        }

        public void recordReceived() {
            messagesReceived++;
        }

        public void recordError() {
            errorCount++;
        }

        public long getMessagesSent() {
            return messagesSent;
        }

        public long getMessagesReceived() {
            return messagesReceived;
        }

        public long getErrorCount() {
            return errorCount;
        }

        public double getErrorRate() {
            long total = messagesSent + messagesReceived;
            return total == 0 ? 0.0 : (double) errorCount / total;
        }
    }
}

/**
 * Default bridge implementation.
 * References JavaA types: MessageSource_V15, AbstractHandler_V15, HandlerConfig_V15.
 * References KotlinB types: ChannelAdapter_V15, MessagePayload_V15, ChannelFactory_V15.
 */
class DefaultMessageBridge_V15 implements MessageBridge_V15 {

    private final MessageSource_V15<MessagePayload_V15> source;
    private final AbstractHandler_V15 handler;
    private final HandlerConfig_V15 config;
    private final BridgeMetrics_V15 metrics;
    private final Map<String, ChannelAdapter_V15> adapterRegistry;

    DefaultMessageBridge_V15(MessageSource_V15<MessagePayload_V15> source,
                             AbstractHandler_V15 handler,
                             HandlerConfig_V15 config) {
        this.source = source;
        this.handler = handler;
        this.config = config;
        this.metrics = new BridgeMetrics_V15();
        this.adapterRegistry = new ConcurrentHashMap<>();
    }

    @Override
    public void send(MessagePayload_V15 payload, ChannelAdapter_V15 adapter) {
        handler.handle(this, payload);
        metrics.recordSent();
    }

    @Override
    public MessagePayload_V15 receive(ChannelAdapter_V15 adapter, long timeoutMs) {
        List<MessagePayload_V15> results = source.poll(this, 1);
        if (!results.isEmpty()) {
            MessagePayload_V15 payload = results.get(0);
            source.acknowledge(this, payload);
            metrics.recordReceived();
            return payload;
        }
        return null;
    }

    @Override
    public List<ChannelAdapter_V15> listAdapters() {
        return List.copyOf(adapterRegistry.values());
    }

    public void registerAdapter(String name, ChannelAdapter_V15 adapter) {
        adapterRegistry.put(name, adapter);
    }

    public BridgeMetrics_V15 getMetrics() {
        return metrics;
    }

    public boolean isHealthy() {
        return metrics.getErrorRate() < 0.1 && source.supports(metrics);
    }
}

/**
 * Bridge event listener for monitoring.
 * References KotlinB type: MessagePayload_V15.
 */
interface BridgeListener_V15 {

    void onSend(MessagePayload_V15 payload);

    void onReceive(MessagePayload_V15 payload);

    void onError(MessagePayload_V15 payload, Exception error);
}

/**
 * Composite bridge wrapping multiple bridges with failover.
 * References JavaA types: AbstractHandler_V15.Filter_V15.
 * References KotlinB types: ChannelAdapter_V15, MessagePayload_V15.
 */
class CompositeBridge_V15 implements MessageBridge_V15 {

    private final List<MessageBridge_V15> delegates;
    private final AbstractHandler_V15.Filter_V15 filter;

    CompositeBridge_V15(List<MessageBridge_V15> delegates,
                        AbstractHandler_V15.Filter_V15 filter) {
        this.delegates = delegates;
        this.filter = filter;
    }

    @Override
    public void send(MessagePayload_V15 payload, ChannelAdapter_V15 adapter) {
        for (MessageBridge_V15 delegate : delegates) {
            BridgeMetrics_V15 metrics = new BridgeMetrics_V15();
            if (filter.accept(metrics)) {
                delegate.send(payload, adapter);
                return;
            }
        }
    }

    @Override
    public MessagePayload_V15 receive(ChannelAdapter_V15 adapter, long timeoutMs) {
        for (MessageBridge_V15 delegate : delegates) {
            MessagePayload_V15 result = delegate.receive(adapter, timeoutMs);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    @Override
    public List<ChannelAdapter_V15> listAdapters() {
        return delegates.stream()
                .flatMap(d -> d.listAdapters().stream())
                .toList();
    }
}

/**
 * Bridge builder using fluent API pattern.
 * References JavaA and KotlinB types.
 */
class BridgeBuilder_V15 {

    private MessageSource_V15<?> source;
    private AbstractHandler_V15 handler;
    private HandlerConfig_V15 config;
    private final List<BridgeListener_V15> listeners = new java.util.ArrayList<>();

    BridgeBuilder_V15 withSource(MessageSource_V15<?> source) {
        this.source = source;
        return this;
    }

    BridgeBuilder_V15 withHandler(AbstractHandler_V15 handler) {
        this.handler = handler;
        return this;
    }

    BridgeBuilder_V15 withConfig(HandlerConfig_V15 config) {
        this.config = config;
        return this;
    }

    BridgeBuilder_V15 addListener(BridgeListener_V15 listener) {
        this.listeners.add(listener);
        return this;
    }

    @SuppressWarnings("unchecked")
    DefaultMessageBridge_V15 build() {
        return new DefaultMessageBridge_V15(
                (MessageSource_V15<MessagePayload_V15>) source,
                handler,
                config
        );
    }
}
