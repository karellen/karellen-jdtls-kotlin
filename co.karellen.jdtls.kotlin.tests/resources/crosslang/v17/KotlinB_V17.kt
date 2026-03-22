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
package crosslang.v17

// References JavaB types: TaskBridge_V17, DefaultTaskBridge_V17, TaskBridge_V17.TaskMetrics_V17
// References KotlinA types: TaskService_V17, TaskId_V17, TaskState_V17,
//     TaskConfig_V17, TaskExecutor_V17, TaskAnnotation_V17, ExecutorMode_V17

/**
 * Interface for coroutine-based task handling with suspend methods.
 * Used by KotlinA's TaskService_V17 and TaskExecutor_V17.
 */
interface CoroutineHandler_V17 {
    suspend fun handleCoroutine(taskId: String): TaskResult_V17

    suspend fun cancelCoroutine(taskId: String): Boolean

    /** Suspend function type as return value (v1.7 exercise). */
    fun asSuspendFunction(): suspend (String) -> TaskResult_V17 = { taskId ->
        handleCoroutine(taskId)
    }
}

/**
 * Data class for task results, used across Java and Kotlin boundaries.
 */
data class TaskResult_V17(
    val taskId: String,
    val success: Boolean,
    val message: String,
    val durationMs: Long = 0L,
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * Coroutine adapter bridging Java's TaskBridge_V17 with Kotlin's TaskService_V17.
 * References JavaB: TaskBridge_V17, TaskBridge_V17.TaskMetrics_V17.
 * References KotlinA: TaskService_V17, TaskId_V17, TaskConfig_V17, TaskState_V17.
 */
@TaskAnnotation_V17(description = "Coroutine-to-bridge adapter")
class CoroutineAdapter_V17(
    private val bridge: TaskBridge_V17,
    private val service: TaskService_V17
) : CoroutineHandler_V17 {

    /** Suspend function type property (v1.7). */
    val onResult_V17: suspend (TaskResult_V17) -> Unit = { result ->
        if (!result.success) {
            cancelCoroutine(result.taskId)
        }
    }

    override suspend fun handleCoroutine(taskId: String): TaskResult_V17 {
        val config = TaskConfig_V17(
            taskId = TaskId_V17(taskId),
            mode = ExecutorMode_V17.PARALLEL
        )
        return service.submitTask(config)
    }

    override suspend fun cancelCoroutine(taskId: String): Boolean =
        service.cancelTask(TaskId_V17(taskId))

    fun adaptResult(taskId: String): TaskResult_V17 =
        TaskResult_V17(
            taskId = taskId,
            success = true,
            message = "Adapted from bridge",
            metadata = mapOf("bridge" to bridge.toString())
        )

    fun getMetrics(): TaskBridge_V17.TaskMetrics_V17 =
        bridge.toCoroutineAdapter().let {
            // Access metrics through the bridge's inner class
            TaskBridge_V17.TaskMetrics_V17(0, 0, 0.0)
        }

    /** Definitely non-nullable adapter method (v1.7). */
    fun <T> adaptNonNull(value: T): T & Any =
        value ?: throw IllegalStateException("Bridge produced null result")

    fun getServiceState(taskId: TaskId_V17): TaskState_V17 =
        service.getState(taskId)

    companion object {
        fun fromBridge(bridge: TaskBridge_V17, service: TaskService_V17): CoroutineAdapter_V17 =
            CoroutineAdapter_V17(bridge, service)

        /** Factory using suspend function type (v1.7). */
        fun withCallback(
            bridge: TaskBridge_V17,
            service: TaskService_V17,
            callback: suspend (TaskResult_V17) -> Unit
        ): CoroutineAdapter_V17 = CoroutineAdapter_V17(bridge, service)
    }
}

/**
 * Factory object for creating adapters from bridges.
 * References JavaB: TaskBridge_V17, DefaultTaskBridge_V17.
 * References KotlinA: TaskService_V17, TaskExecutor_V17.
 */
object AdapterFactory_V17 {
    fun create(bridge: TaskBridge_V17): CoroutineAdapter_V17 {
        val service = TaskExecutor_V17.withDefaults(
            object : CoroutineHandler_V17 {
                override suspend fun handleCoroutine(taskId: String): TaskResult_V17 =
                    TaskResult_V17(taskId, true, "Factory-created")

                override suspend fun cancelCoroutine(taskId: String): Boolean = false
            }
        )
        return CoroutineAdapter_V17(bridge, service)
    }

    /** Create with definitely non-nullable constraint (v1.7). */
    fun <T> createTyped(bridge: T & Any): CoroutineAdapter_V17 where T : TaskBridge_V17 =
        create(bridge)
}

/**
 * Sealed interface for adapter events.
 * References KotlinA: TaskId_V17, TaskState_V17.
 */
sealed interface AdapterEvent_V17 {
    val taskId: TaskId_V17

    data class Submitted_V17(
        override val taskId: TaskId_V17,
        val config: TaskConfig_V17
    ) : AdapterEvent_V17

    data class StateChanged_V17(
        override val taskId: TaskId_V17,
        val oldState: TaskState_V17,
        val newState: TaskState_V17
    ) : AdapterEvent_V17

    data class ResultReceived_V17(
        override val taskId: TaskId_V17,
        val result: TaskResult_V17
    ) : AdapterEvent_V17
}

/**
 * Event collector with suspend-based processing.
 */
class EventCollector_V17 {
    private val events = mutableListOf<AdapterEvent_V17>()

    fun record(event: AdapterEvent_V17) {
        events.add(event)
    }

    /** Suspend function type parameter (v1.7). */
    suspend fun processAll(handler: suspend (AdapterEvent_V17) -> Unit) {
        events.forEach { handler(it) }
    }

    fun <T : AdapterEvent_V17> filterEvents(type: Class<T>): List<T> =
        events.filterIsInstance(type)
}

/** Extension function on TaskBridge_V17 (Java type) returning Kotlin types. */
fun TaskBridge_V17.toResult_V17(taskId: String): TaskResult_V17 =
    this.submit(taskId, emptyMap())

/** Extension function on TaskBridge_V17 using definitely non-nullable (v1.7). */
fun <T> TaskBridge_V17.submitNonNull_V17(taskId: T & Any): TaskResult_V17 where T : CharSequence =
    this.submit(taskId.toString(), emptyMap())

/** Extension property on TaskBridge_V17.TaskMetrics_V17. */
val TaskBridge_V17.TaskMetrics_V17.successRate_V17: Double
    get() = if (totalSubmissions > 0) {
        (totalSubmissions - failedSubmissions).toDouble() / totalSubmissions
    } else 0.0

/** Top-level suspend function bridging Java and Kotlin types. */
suspend fun bridgeToService_V17(
    bridge: TaskBridge_V17,
    service: TaskService_V17,
    taskId: TaskId_V17
): TaskResult_V17 {
    val adapter = CoroutineAdapter_V17.fromBridge(bridge, service)
    return adapter.handleCoroutine(taskId.id)
}
