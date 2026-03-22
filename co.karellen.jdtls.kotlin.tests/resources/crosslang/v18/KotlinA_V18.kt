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
package crosslang.v18

// References only KotlinB types: FlowHandler_V18, StreamData_V18, FlowAdapter_V18, FlowFactory_V18

// --- @JvmInline value class ---

@JvmInline
value class StreamId_V18(val id: String) {
    fun toShortId(): String = id.take(8)
}

// --- Definitely non-nullable types ---

fun <T> requireStream_V18(value: T): T & Any =
    value ?: throw IllegalArgumentException("Stream value must not be null")

// --- Annotation class ---

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class StreamAnnotation_V18(val description: String = "")

// --- Suspend function types ---

typealias StreamSuspendAction_V18 = suspend (StreamData_V18) -> Unit

typealias FlowTransform_V18<T> = suspend (FlowHandler_V18, T) -> StreamData_V18

// --- StreamService_V18 interface (references FlowHandler_V18 from KotlinB) ---

interface StreamService_V18 {
    val handler: FlowHandler_V18
    suspend fun start(id: StreamId_V18)
    suspend fun stop(id: StreamId_V18)
    fun getFlowData(): StreamData_V18
}

// --- StreamSettings_V18 data class ---

data class StreamSettings_V18(
    val maxConcurrency: Int,
    val bufferCapacity: Int,
    val id: StreamId_V18,
    val mode: EngineMode_V18
)

// --- StreamEngine_V18 open class implementing StreamService_V18 ---

@StreamAnnotation_V18(description = "Main stream engine")
open class StreamEngine_V18(
    override val handler: FlowHandler_V18,
    val settings: StreamSettings_V18
) : StreamService_V18 {

    private val listeners: MutableList<StreamSuspendAction_V18> = mutableListOf()

    override suspend fun start(id: StreamId_V18) {
        handler.handle(FlowFactory_V18.createDefault())
    }

    override suspend fun stop(id: StreamId_V18) {
        handler.cancel(id.id)
    }

    override fun getFlowData(): StreamData_V18 = FlowFactory_V18.createDefault()

    // --- Inner class ---

    inner class EngineContext_V18(val label: String) {
        fun getEngine(): StreamEngine_V18 = this@StreamEngine_V18
        fun getHandlerRef(): FlowHandler_V18 = handler
    }

    // --- Companion object ---

    companion object {
        const val DEFAULT_BUFFER: Int = 64

        fun withDefaults(handler: FlowHandler_V18): StreamEngine_V18 {
            val id = StreamId_V18("default")
            return StreamEngine_V18(handler, StreamSettings_V18(4, DEFAULT_BUFFER, id, EngineMode_V18.NORMAL))
        }
    }
}

// --- StreamState_V18 sealed class ---

sealed class StreamState_V18 {
    data object Idle : StreamState_V18()
    data class Flowing(val throughput: Long, val data: StreamData_V18) : StreamState_V18()
    data class Paused(val reason: String) : StreamState_V18()
    data object Closed : StreamState_V18()

    fun isActive(): Boolean = this is Flowing
}

// --- EngineMode_V18 enum class ---

enum class EngineMode_V18(val level: Int) {
    NORMAL(1),
    HIGH_THROUGHPUT(2),
    LOW_LATENCY(3);

    fun isHighPerformance(): Boolean = level > 1
}

// --- Delegation ---

class DelegatingService_V18(
    private val delegate: StreamService_V18
) : StreamService_V18 by delegate {
    override fun getFlowData(): StreamData_V18 {
        val original = delegate.getFlowData()
        return FlowFactory_V18.createDefault()
    }
}

// --- Extension functions on KotlinB types ---

fun FlowHandler_V18.toStreamService_V18(settings: StreamSettings_V18): StreamService_V18 =
    StreamEngine_V18(this, settings)

fun StreamData_V18.toStreamState_V18(): StreamState_V18 =
    StreamState_V18.Flowing(0L, this)

// --- Operator overloading ---

operator fun StreamId_V18.plus(other: StreamId_V18): StreamId_V18 =
    StreamId_V18("${this.id}+${other.id}")

// --- Lambda with receiver ---

fun buildStreamEngine_V18(handler: FlowHandler_V18, block: StreamEngine_V18.() -> Unit): StreamEngine_V18 {
    val engine = StreamEngine_V18.withDefaults(handler)
    engine.block()
    return engine
}

// --- Type parameters with bounds ---

fun <T : StreamService_V18> initService_V18(service: T, id: StreamId_V18): T {
    return service
}

// --- Range usage ---

fun validBufferSizes_V18(): IntRange = 16..StreamEngine_V18.DEFAULT_BUFFER

fun isValidConcurrency_V18(n: Int): Boolean = n in 1..64
