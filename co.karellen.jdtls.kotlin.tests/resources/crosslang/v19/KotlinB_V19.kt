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

// References JavaB types: PipelineBridge_V19, PipelineBridge_V19.PipelineMetrics_V19
// References KotlinA types: PipelineService_V19, PipelineId_V19, PipelineStatus_V19, PipelineAnnotation_V19

// --- StageHandler_V19 interface with suspend methods ---

interface StageHandler_V19 {
    suspend fun handle(data: PipelineData_V19)
    suspend fun cancel(reason: String)
    fun isActive(): Boolean
}

// --- PipelineData_V19 data class ---

data class PipelineData_V19(
    val payload: ByteArray = ByteArray(0),
    val sequenceNumber: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PipelineData_V19) return false
        return sequenceNumber == other.sequenceNumber && timestamp == other.timestamp
    }

    override fun hashCode(): Int = (sequenceNumber xor timestamp).toInt()
}

// --- StageAdapter_V19 — bridges JavaB and KotlinA ---

@PipelineAnnotation_V19(description = "Adapts Java bridge to Kotlin pipeline stage")
class StageAdapter_V19(
    private val bridge: PipelineBridge_V19,
    private val service: PipelineService_V19
) : StageHandler_V19 {

    private var active: Boolean = true

    override suspend fun handle(data: PipelineData_V19) {
        val metrics = bridge.getCurrentData()
        service.start(PipelineId_V19("adapter-${data.sequenceNumber}"))
    }

    override suspend fun cancel(reason: String) {
        active = false
        service.stop(PipelineId_V19(reason))
    }

    override fun isActive(): Boolean = active

    fun getBridgeData(): PipelineData_V19 = bridge.getCurrentData()

    fun getServiceStatus(): PipelineStatus_V19 = PipelineStatus_V19.Ready

    // --- Inner class referencing both JavaB and KotlinA ---

    inner class AdapterContext_V19(val tag: String) {
        fun getMetrics(): PipelineBridge_V19.PipelineMetrics_V19 =
            PipelineBridge_V19.PipelineMetrics_V19(System.currentTimeMillis(), 0, this@StageAdapter_V19)

        fun getServiceRef(): PipelineService_V19 = service
    }
}

// --- StageRegistry_V19 data object (Kotlin 1.9 feature) ---

data object StageRegistry_V19 {
    private val handlers = mutableMapOf<String, StageHandler_V19>()

    fun register(name: String, handler: StageHandler_V19) {
        handlers[name] = handler
    }

    fun lookup(name: String): StageHandler_V19? = handlers[name]

    fun createDefaultData(): PipelineData_V19 = PipelineData_V19()

    fun createWithSequence(seq: Long): PipelineData_V19 =
        PipelineData_V19(sequenceNumber = seq)

    fun createHandler(bridge: PipelineBridge_V19, service: PipelineService_V19): StageHandler_V19 =
        StageAdapter_V19(bridge, service)
}

// --- Companion object in a class ---

class StageConfig_V19(val maxRetries: Int, val timeoutMs: Long) {
    companion object {
        val DEFAULT: StageConfig_V19 = StageConfig_V19(3, 5000L)

        fun forBridge(bridge: PipelineBridge_V19): StageConfig_V19 =
            StageConfig_V19(5, 10000L)
    }
}

// --- Extension functions on JavaB types ---

fun PipelineBridge_V19.toStageHandler_V19(service: PipelineService_V19): StageHandler_V19 =
    StageAdapter_V19(this, service)

fun PipelineBridge_V19.PipelineMetrics_V19.toPipelineData_V19(): PipelineData_V19 =
    PipelineData_V19(sequenceNumber = stagesCompleted.toLong(), timestamp = timestamp)

// --- Suspend top-level function ---

suspend fun processStages_V19(handler: StageHandler_V19, items: List<PipelineData_V19>) {
    for (item in items) {
        handler.handle(item)
    }
}

// --- Lambda-based builder referencing JavaB ---

fun configureBridge_V19(bridge: PipelineBridge_V19, block: StageConfig_V19.Companion.() -> StageConfig_V19): StageConfig_V19 {
    return StageConfig_V19.Companion.block()
}
