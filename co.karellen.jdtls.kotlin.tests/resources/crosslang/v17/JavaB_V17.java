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
import java.util.function.Function;

/**
 * Bridge interface between Java schedulers and Kotlin coroutine adapters.
 * References KotlinB types: CoroutineAdapter_V17, TaskResult_V17 (Kotlin side).
 * References JavaA types: TaskScheduler_V17, AbstractTaskRunner_V17.
 */
public interface TaskBridge_V17 {

    /**
     * Adapt this bridge to a Kotlin coroutine adapter.
     * References KotlinB: CoroutineAdapter_V17.
     */
    CoroutineAdapter_V17 toCoroutineAdapter();

    /**
     * Submit work through the bridge and get a Kotlin result.
     * References KotlinB: TaskResult_V17 (Kotlin data class).
     */
    TaskResult_V17 submit(String taskId, Map<String, Object> params);

    /**
     * Get the scheduler that owns this bridge.
     * References JavaA: TaskScheduler_V17.
     */
    TaskScheduler_V17<?> getOwner();

    /**
     * Metrics tracking for bridge operations.
     */
    class TaskMetrics_V17 {
        private long totalSubmissions;
        private long failedSubmissions;
        private double averageLatencyMs;

        public TaskMetrics_V17(long totalSubmissions, long failedSubmissions, double averageLatencyMs) {
            this.totalSubmissions = totalSubmissions;
            this.failedSubmissions = failedSubmissions;
            this.averageLatencyMs = averageLatencyMs;
        }

        public long getTotalSubmissions() {
            return totalSubmissions;
        }

        public long getFailedSubmissions() {
            return failedSubmissions;
        }

        public double getAverageLatencyMs() {
            return averageLatencyMs;
        }
    }
}

/**
 * Default bridge implementation connecting Java schedulers to Kotlin coroutine adapters.
 * References JavaA: TaskScheduler_V17, AbstractTaskRunner_V17, TaskOptions_V17.
 * References KotlinB: CoroutineAdapter_V17, TaskResult_V17.
 */
class DefaultTaskBridge_V17 extends AbstractTaskRunner_V17 implements TaskBridge_V17 {

    private final TaskScheduler_V17<?> scheduler;
    private final TaskOptions_V17 options;
    private CoroutineAdapter_V17 adapter;
    private final TaskBridge_V17.TaskMetrics_V17 metrics;

    public DefaultTaskBridge_V17(TaskScheduler_V17<?> scheduler, TaskOptions_V17 options) {
        super(null);
        this.scheduler = scheduler;
        this.options = options;
        this.metrics = new TaskBridge_V17.TaskMetrics_V17(0, 0, 0.0);
    }

    @Override
    public CoroutineAdapter_V17 toCoroutineAdapter() {
        if (adapter == null) {
            adapter = AdapterFactory_V17.create(this);
        }
        return adapter;
    }

    @Override
    public TaskResult_V17 submit(String taskId, Map<String, Object> params) {
        return toCoroutineAdapter().adaptResult(taskId);
    }

    @Override
    public TaskScheduler_V17<?> getOwner() {
        return scheduler;
    }

    @Override
    public void execute(TaskBridge_V17 bridge) {
        CoroutineAdapter_V17 coroutineAdapter = bridge.toCoroutineAdapter();
        TaskResult_V17 result = bridge.submit("exec", Map.of());
    }

    public TaskOptions_V17 getOptions() {
        return options;
    }

    public TaskBridge_V17.TaskMetrics_V17 getMetrics() {
        return metrics;
    }
}

/**
 * Batch bridge that groups multiple submissions.
 * References JavaA: AbstractTaskRunner_V17.TaskCallback_V17.
 * References KotlinB: TaskResult_V17.
 */
class BatchBridge_V17 implements TaskBridge_V17 {

    private final List<TaskBridge_V17> delegates;
    private final AbstractTaskRunner_V17.TaskCallback_V17 callback;

    public BatchBridge_V17(List<TaskBridge_V17> delegates, AbstractTaskRunner_V17.TaskCallback_V17 callback) {
        this.delegates = delegates;
        this.callback = callback;
    }

    @Override
    public CoroutineAdapter_V17 toCoroutineAdapter() {
        return delegates.get(0).toCoroutineAdapter();
    }

    @Override
    public TaskResult_V17 submit(String taskId, Map<String, Object> params) {
        TaskResult_V17 lastResult = null;
        for (TaskBridge_V17 delegate : delegates) {
            lastResult = delegate.submit(taskId, params);
        }
        return lastResult;
    }

    @Override
    public TaskScheduler_V17<?> getOwner() {
        return delegates.get(0).getOwner();
    }

    public AbstractTaskRunner_V17.TaskCallback_V17 getCallback() {
        return callback;
    }
}

/**
 * Transforming bridge that maps results.
 * References KotlinB: TaskResult_V17, CoroutineAdapter_V17.
 */
class TransformingBridge_V17 implements TaskBridge_V17 {

    private final TaskBridge_V17 inner;
    private final Function<TaskResult_V17, TaskResult_V17> transformer;

    public TransformingBridge_V17(TaskBridge_V17 inner, Function<TaskResult_V17, TaskResult_V17> transformer) {
        this.inner = inner;
        this.transformer = transformer;
    }

    @Override
    public CoroutineAdapter_V17 toCoroutineAdapter() {
        return inner.toCoroutineAdapter();
    }

    @Override
    public TaskResult_V17 submit(String taskId, Map<String, Object> params) {
        TaskResult_V17 raw = inner.submit(taskId, params);
        return transformer.apply(raw);
    }

    @Override
    public TaskScheduler_V17<?> getOwner() {
        return inner.getOwner();
    }
}

/**
 * Bridge event listener.
 * References JavaA: AbstractTaskRunner_V17.RunnerState_V17, TaskSchedulerListener_V17.
 */
class BridgeEventHandler_V17 implements TaskSchedulerListener_V17 {

    private final AbstractTaskRunner_V17.RunnerState_V17 state;

    public BridgeEventHandler_V17(AbstractTaskRunner_V17.RunnerState_V17 state) {
        this.state = state;
    }

    @Override
    public void onTaskQueued(TaskBridge_V17 bridge, String taskId) {
        state.setActive(true);
    }

    @Override
    public void onTaskDispatched(TaskBridge_V17 bridge, String taskId) {
        // no-op
    }
}
