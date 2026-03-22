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
package crosslang.v19

// References only KotlinB types: StageHandler_V19, PipelineData_V19, StageAdapter_V19, StageRegistry_V19

// --- @JvmInline value class ---

@JvmInline
value class PipelineId_V19(val id: String) {
    fun toShortId(): String = id.take(8)
}

// --- data object (Kotlin 1.9 feature) ---

data object PipelineDefaults_V19 {
    const val MAX_STAGES: Int = 16
    const val DEFAULT_TIMEOUT_MS: Long = 30000L

    fun createDefaultData(): PipelineData_V19 = PipelineData_V19()
}

// --- Definitely non-nullable types ---

fun <T> ensurePipeline_V19(value: T): T & Any =
    value ?: throw IllegalArgumentException("Pipeline value must not be null")

// --- Annotation class ---

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class PipelineAnnotation_V19(val description: String = "")

// --- Suspend function types ---

typealias PipelineSuspendAction_V19 = suspend (PipelineData_V19) -> Unit

typealias StageTransform_V19<T> = suspend (StageHandler_V19, T) -> PipelineData_V19

// --- PipelineService_V19 interface (references StageHandler_V19 from KotlinB) ---

interface PipelineService_V19 {
    val handler: StageHandler_V19
    suspend fun start(id: PipelineId_V19)
    suspend fun stop(id: PipelineId_V19)
    fun getData(): PipelineData_V19
}

// --- PipelineSettings_V19 data class ---

data class PipelineSettings_V19(
    val maxConcurrency: Int,
    val bufferCapacity: Int,
    val id: PipelineId_V19,
    val mode: ExecutionMode_V19
)

// --- PipelineExecutor_V19 open class implementing PipelineService_V19 ---

@PipelineAnnotation_V19(description = "Main pipeline executor")
open class PipelineExecutor_V19(
    override val handler: StageHandler_V19,
    val settings: PipelineSettings_V19
) : PipelineService_V19 {

    private val listeners: MutableList<PipelineSuspendAction_V19> = mutableListOf()

    // --- Suspend anonymous function (Kotlin 1.9 feature) ---

    val suspendBlock_V19 = suspend fun() {
        handler.handle(StageRegistry_V19.createDefaultData())
    }

    override suspend fun start(id: PipelineId_V19) {
        handler.handle(StageRegistry_V19.createDefaultData())
    }

    override suspend fun stop(id: PipelineId_V19) {
        handler.cancel(id.id)
    }

    override fun getData(): PipelineData_V19 = StageRegistry_V19.createDefaultData()

    // --- Range-until (Kotlin 1.9 feature: ..<) ---

    fun processStages(count: Int): List<PipelineData_V19> {
        val results = mutableListOf<PipelineData_V19>()
        for (i in 0..<count) {
            results.add(PipelineData_V19(sequenceNumber = i.toLong()))
        }
        return results
    }

    // --- Inner class ---

    inner class ExecutorContext_V19(val label: String) {
        fun getExecutor(): PipelineExecutor_V19 = this@PipelineExecutor_V19
        fun getHandlerRef(): StageHandler_V19 = handler
    }

    // --- data companion object (Kotlin 1.9 feature) ---

    data companion object {
        const val DEFAULT_BUFFER: Int = 64

        fun withDefaults(handler: StageHandler_V19): PipelineExecutor_V19 {
            val id = PipelineId_V19("default")
            return PipelineExecutor_V19(handler, PipelineSettings_V19(4, DEFAULT_BUFFER, id, ExecutionMode_V19.NORMAL))
        }
    }
}

// --- PipelineStatus_V19 sealed class ---

sealed class PipelineStatus_V19 {
    data object Ready : PipelineStatus_V19()
    data class Processing(val throughput: Long, val data: PipelineData_V19) : PipelineStatus_V19()
    data class Complete(val totalProcessed: Int) : PipelineStatus_V19()
    data object Aborted : PipelineStatus_V19()

    fun isActive(): Boolean = this is Processing
}

// --- ExecutionMode_V19 enum class ---

enum class ExecutionMode_V19(val level: Int) {
    NORMAL(1),
    HIGH_THROUGHPUT(2),
    LOW_LATENCY(3);

    fun isHighPerformance(): Boolean = level > 1
}

// --- Delegation ---

class DelegatingPipelineService_V19(
    private val delegate: PipelineService_V19
) : PipelineService_V19 by delegate {
    override fun getData(): PipelineData_V19 {
        val original = delegate.getData()
        return StageRegistry_V19.createDefaultData()
    }
}

// --- Extension functions on KotlinB types ---

fun StageHandler_V19.toPipelineService_V19(settings: PipelineSettings_V19): PipelineService_V19 =
    PipelineExecutor_V19(this, settings)

fun PipelineData_V19.toPipelineStatus_V19(): PipelineStatus_V19 =
    PipelineStatus_V19.Processing(0L, this)

// --- Operator overloading ---

operator fun PipelineId_V19.plus(other: PipelineId_V19): PipelineId_V19 =
    PipelineId_V19("${this.id}+${other.id}")

// --- Lambda with receiver ---

fun buildPipelineExecutor_V19(handler: StageHandler_V19, block: PipelineExecutor_V19.() -> Unit): PipelineExecutor_V19 {
    val executor = PipelineExecutor_V19.withDefaults(handler)
    executor.block()
    return executor
}

// --- Type parameters with bounds ---

fun <T : PipelineService_V19> initService_V19(service: T, id: PipelineId_V19): T {
    return service
}

// --- Range-until usage in top-level function (Kotlin 1.9 feature: ..<) ---

fun validBufferSizes_V19(): List<Int> {
    val sizes = mutableListOf<Int>()
    for (i in 16..<PipelineExecutor_V19.DEFAULT_BUFFER) {
        sizes.add(i)
    }
    return sizes
}

fun isValidConcurrency_V19(n: Int): Boolean = n in 1..64
