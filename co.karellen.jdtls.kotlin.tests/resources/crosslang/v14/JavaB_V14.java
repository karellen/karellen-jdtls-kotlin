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
package crosslang.v14;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service bridge interface connecting Java to Kotlin adapters.
 * References KotlinB types: BridgeAdapter_V14, EventData_V14.
 */
public interface ServiceBridge_V14 {

    void dispatch(EventData_V14 event, BridgeAdapter_V14 adapter);

    EventData_V14 poll(BridgeAdapter_V14 adapter, long timeoutMs);

    List<BridgeAdapter_V14> listAdapters();

    boolean isAdapterRegistered(BridgeAdapter_V14 adapter);

    /**
     * Inner configuration class for bridge tuning.
     */
    class BridgeConfig_V14 {
        private final String name;
        private int maxRetries;
        private long retryDelayMs;
        private boolean asyncMode;

        public BridgeConfig_V14(String name) {
            this.name = name;
            this.maxRetries = 3;
            this.retryDelayMs = 1000L;
            this.asyncMode = false;
        }

        public String getName() {
            return name;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        public long getRetryDelayMs() {
            return retryDelayMs;
        }

        public void setRetryDelayMs(long retryDelayMs) {
            this.retryDelayMs = retryDelayMs;
        }

        public boolean isAsyncMode() {
            return asyncMode;
        }

        public void setAsyncMode(boolean asyncMode) {
            this.asyncMode = asyncMode;
        }
    }
}

/**
 * Default bridge implementation.
 * References JavaA types: Repository_V14, AbstractProcessor_V14, ProcessorConfig_V14.
 * References KotlinB types: BridgeAdapter_V14, EventData_V14, AdapterFactory_V14.
 */
class DefaultBridge_V14 implements ServiceBridge_V14 {

    private final Repository_V14<EventData_V14> repository;
    private final AbstractProcessor_V14 processor;
    private final ProcessorConfig_V14 processorConfig;
    private final BridgeConfig_V14 bridgeConfig;
    private final Map<String, BridgeAdapter_V14> adapterRegistry;

    DefaultBridge_V14(Repository_V14<EventData_V14> repository,
                      AbstractProcessor_V14 processor,
                      ProcessorConfig_V14 processorConfig,
                      BridgeConfig_V14 bridgeConfig) {
        this.repository = repository;
        this.processor = processor;
        this.processorConfig = processorConfig;
        this.bridgeConfig = bridgeConfig;
        this.adapterRegistry = new ConcurrentHashMap<>();
    }

    @Override
    public void dispatch(EventData_V14 event, BridgeAdapter_V14 adapter) {
        processor.process(this, new AbstractProcessor_V14.Callback_V14() {
            @Override
            public void onSuccess(ServiceBridge_V14 bridge, Object result) {
                // dispatch complete
            }

            @Override
            public void onError(ServiceBridge_V14 bridge, Exception error) {
                // log error
            }
        });
    }

    @Override
    public EventData_V14 poll(BridgeAdapter_V14 adapter, long timeoutMs) {
        List<EventData_V14> results = repository.findAll(this);
        if (!results.isEmpty()) {
            EventData_V14 event = results.get(0);
            return event;
        }
        return null;
    }

    @Override
    public List<BridgeAdapter_V14> listAdapters() {
        return List.copyOf(adapterRegistry.values());
    }

    @Override
    public boolean isAdapterRegistered(BridgeAdapter_V14 adapter) {
        return adapterRegistry.containsValue(adapter);
    }

    public void registerAdapter(String name, BridgeAdapter_V14 adapter) {
        adapterRegistry.put(name, adapter);
    }

    public BridgeConfig_V14 getBridgeConfig() {
        return bridgeConfig;
    }

    public boolean isHealthy() {
        return !adapterRegistry.isEmpty() && bridgeConfig.getMaxRetries() > 0;
    }
}

/**
 * Bridge event listener for monitoring.
 * References KotlinB type: EventData_V14.
 */
interface BridgeMonitor_V14 {

    void onDispatch(EventData_V14 event);

    void onPoll(EventData_V14 event);

    void onError(EventData_V14 event, Exception error);
}

/**
 * Composite bridge wrapping multiple bridges with failover.
 * References JavaA types: AbstractProcessor_V14.Callback_V14.
 * References KotlinB types: BridgeAdapter_V14, EventData_V14.
 */
class CompositeBridge_V14 implements ServiceBridge_V14 {

    private final List<ServiceBridge_V14> delegates;
    private final AbstractProcessor_V14.Callback_V14 failoverCallback;

    CompositeBridge_V14(List<ServiceBridge_V14> delegates,
                        AbstractProcessor_V14.Callback_V14 failoverCallback) {
        this.delegates = delegates;
        this.failoverCallback = failoverCallback;
    }

    @Override
    public void dispatch(EventData_V14 event, BridgeAdapter_V14 adapter) {
        for (ServiceBridge_V14 delegate : delegates) {
            try {
                delegate.dispatch(event, adapter);
                failoverCallback.onSuccess(delegate, event);
                return;
            } catch (Exception e) {
                failoverCallback.onError(delegate, e);
            }
        }
    }

    @Override
    public EventData_V14 poll(BridgeAdapter_V14 adapter, long timeoutMs) {
        for (ServiceBridge_V14 delegate : delegates) {
            EventData_V14 result = delegate.poll(adapter, timeoutMs);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    @Override
    public List<BridgeAdapter_V14> listAdapters() {
        return delegates.stream()
                .flatMap(d -> d.listAdapters().stream())
                .toList();
    }

    @Override
    public boolean isAdapterRegistered(BridgeAdapter_V14 adapter) {
        return delegates.stream().anyMatch(d -> d.isAdapterRegistered(adapter));
    }
}

/**
 * Bridge builder using fluent API pattern.
 * References JavaA and KotlinB types.
 */
class BridgeBuilder_V14 {

    private Repository_V14<?> repository;
    private AbstractProcessor_V14 processor;
    private ProcessorConfig_V14 processorConfig;
    private ServiceBridge_V14.BridgeConfig_V14 bridgeConfig;
    private final List<BridgeMonitor_V14> monitors = new java.util.ArrayList<>();

    BridgeBuilder_V14 withRepository(Repository_V14<?> repository) {
        this.repository = repository;
        return this;
    }

    BridgeBuilder_V14 withProcessor(AbstractProcessor_V14 processor) {
        this.processor = processor;
        return this;
    }

    BridgeBuilder_V14 withProcessorConfig(ProcessorConfig_V14 processorConfig) {
        this.processorConfig = processorConfig;
        return this;
    }

    BridgeBuilder_V14 withBridgeConfig(ServiceBridge_V14.BridgeConfig_V14 bridgeConfig) {
        this.bridgeConfig = bridgeConfig;
        return this;
    }

    BridgeBuilder_V14 addMonitor(BridgeMonitor_V14 monitor) {
        this.monitors.add(monitor);
        return this;
    }

    @SuppressWarnings("unchecked")
    DefaultBridge_V14 build() {
        return new DefaultBridge_V14(
                (Repository_V14<EventData_V14>) repository,
                processor,
                processorConfig,
                bridgeConfig
        );
    }
}
