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

// References only KotlinB types: CoroutineHandler_V17, TaskResult_V17,
// CoroutineAdapter_V17, AdapterFactory_V17

/**
 * JvmInline value class wrapping a task identifier (Kotlin 1.7 stable).
 */
@JvmInline
value class TaskId_V17(val id: String) {
    fun shortId(): String = id.take(8)
}

/**
 * Definitely non-nullable type constraint (Kotlin 1.7).
 * Ensures the returned value is never null regardless of T's nullability.
 */
fun <T> ensureNotNull_V17(value: T): T & Any =
    value ?: throw IllegalArgumentException("Value must not be null")

/**
 * Generic processor using definitely non-nullable types in class context.
 */
class NonNullProcessor_V17<T> {
    fun <R> mapNonNull(value: R, transform: (R & Any) -> T & Any): T & Any {
        val nonNullValue: R & Any = value ?: throw IllegalArgumentException("Input must not be null")
        return transform(nonNullValue)
    }

    fun requireNonNull(value: T): T & Any =
        value ?: throw IllegalStateException("Required non-null value")
}

/**
 * Annotation for marking task-related declarations.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class TaskAnnotation_V17(val description: String = "")

/**
 * Sealed class representing task execution states.
 */
sealed class TaskState_V17 {
    data object Pending_V17 : TaskState_V17()
    data class Running_V17(val taskId: TaskId_V17, val progress: Int) : TaskState_V17()
    data class Completed_V17(val taskId: TaskId_V17, val result: TaskResult_V17) : TaskState_V17()
    data class Failed_V17(val taskId: TaskId_V17, val error: Throwable) : TaskState_V17()
}

/**
 * Enum for executor operation modes.
 */
enum class ExecutorMode_V17(val parallelism: Int) {
    SEQUENTIAL(1),
    PARALLEL(4),
    UNLIMITED(Int.MAX_VALUE);

    fun isParallel(): Boolean = parallelism > 1
}

/**
 * Configuration data class for task execution.
 */
data class TaskConfig_V17(
    val taskId: TaskId_V17,
    val mode: ExecutorMode_V17 = ExecutorMode_V17.SEQUENTIAL,
    val maxRetries: Int = 3,
    val timeoutMs: Long = 5000L,
    val tags: List<String> = emptyList()
)

/**
 * Service interface with suspend methods and suspend function type properties.
 * References KotlinB: CoroutineHandler_V17.
 */
@TaskAnnotation_V17(description = "Core task service")
interface TaskService_V17 {
    val handler: CoroutineHandler_V17

    /** Suspend function type as property (Kotlin 1.7 feature exercise). */
    val suspendAction_V17: suspend () -> TaskResult_V17

    suspend fun submitTask(config: TaskConfig_V17): TaskResult_V17

    suspend fun cancelTask(taskId: TaskId_V17): Boolean

    fun getState(taskId: TaskId_V17): TaskState_V17
}

/**
 * Delegating interface for handler operations.
 */
interface HandlerDelegate_V17 {
    val delegate: CoroutineHandler_V17

    suspend fun delegatedSubmit(taskId: TaskId_V17): TaskResult_V17 =
        delegate.handleCoroutine(taskId.id)
}

/**
 * Open class implementing TaskService_V17 with delegation, operator overloading,
 * companion object, inner class, ranges, lambdas, extension functions, and type params.
 * References KotlinB: CoroutineHandler_V17, TaskResult_V17, CoroutineAdapter_V17.
 */
@TaskAnnotation_V17(description = "Default task executor")
open class TaskExecutor_V17(
    override val handler: CoroutineHandler_V17,
    private val config: TaskConfig_V17
) : TaskService_V17, HandlerDelegate_V17 {

    override val delegate: CoroutineHandler_V17 get() = handler

    private val states = mutableMapOf<TaskId_V17, TaskState_V17>()

    /** Suspend function type property (v1.7 exercise). */
    override val suspendAction_V17: suspend () -> TaskResult_V17 = {
        handler.handleCoroutine(config.taskId.id)
    }

    override suspend fun submitTask(config: TaskConfig_V17): TaskResult_V17 {
        states[config.taskId] = TaskState_V17.Running_V17(config.taskId, 0)
        val result = handler.handleCoroutine(config.taskId.id)
        states[config.taskId] = TaskState_V17.Completed_V17(config.taskId, result)
        return result
    }

    override suspend fun cancelTask(taskId: TaskId_V17): Boolean {
        states[taskId] = TaskState_V17.Failed_V17(taskId, CancellationException("Cancelled"))
        return true
    }

    override fun getState(taskId: TaskId_V17): TaskState_V17 =
        states[taskId] ?: TaskState_V17.Pending_V17

    /** Operator overloading: get task state by index. */
    operator fun get(index: Int): TaskState_V17? =
        states.values.toList().getOrNull(index)

    /** Operator overloading: plus combines executors' states. */
    operator fun plus(other: TaskExecutor_V17): Map<TaskId_V17, TaskState_V17> =
        states + other.states

    /** Range-based batch processing. */
    fun processRange(ids: IntRange): List<TaskConfig_V17> =
        ids.map { i ->
            TaskConfig_V17(
                taskId = TaskId_V17("task-$i"),
                mode = if (i % 2 == 0) ExecutorMode_V17.PARALLEL else ExecutorMode_V17.SEQUENTIAL
            )
        }

    /** Lambda-accepting method with type parameter and definitely non-nullable constraint. */
    fun <R> transformResults(
        results: List<TaskResult_V17>,
        transform: (TaskResult_V17) -> R
    ): List<R & Any> =
        results.mapNotNull { r -> transform(r)?.let { ensureNotNull_V17(it) } }

    /** Inner class for tracking execution batches. */
    inner class ExecutionBatch_V17(val batchId: String) {
        private val configs = mutableListOf<TaskConfig_V17>()

        fun add(config: TaskConfig_V17) {
            configs.add(config)
        }

        fun currentState(): Map<TaskId_V17, TaskState_V17> =
            configs.associate { it.taskId to this@TaskExecutor_V17.getState(it.taskId) }

        suspend fun executeAll(): List<TaskResult_V17> =
            configs.map { this@TaskExecutor_V17.submitTask(it) }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 5000L

        fun withDefaults(handler: CoroutineHandler_V17): TaskExecutor_V17 =
            TaskExecutor_V17(
                handler,
                TaskConfig_V17(taskId = TaskId_V17("default"), mode = ExecutorMode_V17.SEQUENTIAL)
            )
    }

    class CancellationException(message: String) : RuntimeException(message)
}

/** Extension function on TaskResult_V17 to convert to a state. */
fun TaskResult_V17.toState_V17(taskId: TaskId_V17): TaskState_V17 =
    if (success) TaskState_V17.Completed_V17(taskId, this)
    else TaskState_V17.Failed_V17(taskId, RuntimeException(message))

/** Extension property on CoroutineHandler_V17. */
val CoroutineHandler_V17.description_V17: String
    get() = "CoroutineHandler instance"

/** Top-level suspend function using suspend function type parameter (v1.7). */
suspend fun executeWithCallback_V17(
    action: suspend (TaskId_V17) -> TaskResult_V17,
    onComplete: suspend (TaskResult_V17) -> Unit,
    taskId: TaskId_V17
) {
    val result = action(taskId)
    onComplete(result)
}

/**
 * Delegation example: wraps a CoroutineHandler_V17 via `by` delegation.
 */
class DelegatingHandler_V17(
    private val inner: CoroutineHandler_V17
) : CoroutineHandler_V17 by inner {
    override suspend fun handleCoroutine(taskId: String): TaskResult_V17 {
        // pre-processing before delegation
        return inner.handleCoroutine(taskId)
    }
}
