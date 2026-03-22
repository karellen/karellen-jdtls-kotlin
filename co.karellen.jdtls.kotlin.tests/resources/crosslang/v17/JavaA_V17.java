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
package crosslang.v17;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Scheduler interface for dispatching tasks to a bridge.
 * References only JavaB types (TaskBridge_V17, TaskMetrics_V17).
 */
public interface TaskScheduler_V17<T> {

    TaskBridge_V17 getBridge();

    CompletableFuture<T> schedule(TaskBridge_V17 bridge, T payload);

    List<TaskBridge_V17.TaskMetrics_V17> collectMetrics();

    void onComplete(TaskBridge_V17 bridge, Consumer<T> callback);

    Map<String, TaskBridge_V17> listBridges();
}

/**
 * Abstract runner with an inner callback interface.
 * References only JavaB types.
 */
abstract class AbstractTaskRunner_V17 implements Runnable {

    protected final TaskBridge_V17 bridge;
    protected TaskBridge_V17.TaskMetrics_V17 lastMetrics;

    protected AbstractTaskRunner_V17(TaskBridge_V17 bridge) {
        this.bridge = bridge;
    }

    public abstract void execute(TaskBridge_V17 bridge);

    public TaskBridge_V17 getBridge() {
        return bridge;
    }

    public TaskBridge_V17.TaskMetrics_V17 getLastMetrics() {
        return lastMetrics;
    }

    @Override
    public void run() {
        execute(bridge);
    }

    /**
     * Callback interface for task lifecycle events.
     */
    public interface TaskCallback_V17 {
        void onStarted(TaskBridge_V17 bridge);

        void onProgress(TaskBridge_V17 bridge, int percent);

        void onFinished(TaskBridge_V17 bridge, TaskBridge_V17.TaskMetrics_V17 metrics);
    }

    /**
     * Inner class tracking runner state.
     */
    public static class RunnerState_V17 {
        private final TaskBridge_V17 bridge;
        private boolean active;

        public RunnerState_V17(TaskBridge_V17 bridge) {
            this.bridge = bridge;
            this.active = false;
        }

        public TaskBridge_V17 getBridge() {
            return bridge;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }
    }
}

/**
 * Configuration options for task execution.
 * References TaskBridge_V17 for default bridge config.
 */
class TaskOptions_V17 {

    private final String name;
    private final int maxRetries;
    private final long timeoutMs;
    private final TaskBridge_V17 defaultBridge;

    public TaskOptions_V17(String name, int maxRetries, long timeoutMs, TaskBridge_V17 defaultBridge) {
        this.name = name;
        this.maxRetries = maxRetries;
        this.timeoutMs = timeoutMs;
        this.defaultBridge = defaultBridge;
    }

    public String getName() {
        return name;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public TaskBridge_V17 getDefaultBridge() {
        return defaultBridge;
    }
}

/**
 * Generic result holder parameterized on bridge type.
 */
class TaskResult_V17<R> {

    private final R value;
    private final TaskBridge_V17 source;
    private final long durationMs;

    public TaskResult_V17(R value, TaskBridge_V17 source, long durationMs) {
        this.value = value;
        this.source = source;
        this.durationMs = durationMs;
    }

    public R getValue() {
        return value;
    }

    public TaskBridge_V17 getSource() {
        return source;
    }

    public long getDurationMs() {
        return durationMs;
    }
}

/**
 * Listener interface for scheduler events.
 */
interface TaskSchedulerListener_V17 {

    void onTaskQueued(TaskBridge_V17 bridge, String taskId);

    void onTaskDispatched(TaskBridge_V17 bridge, String taskId);
}
