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

// References JavaB types: StreamBridge_V18, StreamBridge_V18.StreamMetrics_V18
// References KotlinA types: StreamService_V18, StreamId_V18, StreamState_V18, StreamAnnotation_V18

// --- FlowHandler_V18 interface with suspend methods ---

interface FlowHandler_V18 {
    suspend fun handle(data: StreamData_V18)
    suspend fun cancel(reason: String)
    fun isActive(): Boolean
}

// --- StreamData_V18 data class ---

data class StreamData_V18(
    val payload: ByteArray = ByteArray(0),
    val sequenceNumber: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StreamData_V18) return false
        return sequenceNumber == other.sequenceNumber && timestamp == other.timestamp
    }

    override fun hashCode(): Int = (sequenceNumber xor timestamp).toInt()
}

// --- FlowAdapter_V18 — bridges JavaB and KotlinA ---

@StreamAnnotation_V18(description = "Adapts Java bridge to Kotlin flow")
class FlowAdapter_V18(
    private val bridge: StreamBridge_V18,
    private val service: StreamService_V18
) : FlowHandler_V18 {

    private var active: Boolean = true

    override suspend fun handle(data: StreamData_V18) {
        val metrics = bridge.getCurrentData()
        service.start(StreamId_V18("adapter-${data.sequenceNumber}"))
    }

    override suspend fun cancel(reason: String) {
        active = false
        service.stop(StreamId_V18(reason))
    }

    override fun isActive(): Boolean = active

    fun getBridgeData(): StreamData_V18 = bridge.getCurrentData()

    fun getServiceState(): StreamState_V18 = StreamState_V18.Idle

    // --- Inner class referencing both JavaB and KotlinA ---

    inner class AdapterContext_V18(val tag: String) {
        fun getMetrics(): StreamBridge_V18.StreamMetrics_V18 =
            StreamBridge_V18.StreamMetrics_V18(System.currentTimeMillis(), 0, this@FlowAdapter_V18)

        fun getServiceRef(): StreamService_V18 = service
    }
}

// --- FlowFactory_V18 object ---

object FlowFactory_V18 {
    fun createDefault(): StreamData_V18 = StreamData_V18()

    fun createWithSequence(seq: Long): StreamData_V18 =
        StreamData_V18(sequenceNumber = seq)

    fun createHandler(bridge: StreamBridge_V18, service: StreamService_V18): FlowHandler_V18 =
        FlowAdapter_V18(bridge, service)
}

// --- Companion object in a class ---

class FlowConfig_V18(val maxRetries: Int, val timeoutMs: Long) {
    companion object {
        val DEFAULT: FlowConfig_V18 = FlowConfig_V18(3, 5000L)

        fun forBridge(bridge: StreamBridge_V18): FlowConfig_V18 =
            FlowConfig_V18(5, 10000L)
    }
}

// --- Extension functions on JavaB types ---

fun StreamBridge_V18.toFlowHandler_V18(service: StreamService_V18): FlowHandler_V18 =
    FlowAdapter_V18(this, service)

fun StreamBridge_V18.StreamMetrics_V18.toStreamData_V18(): StreamData_V18 =
    StreamData_V18(sequenceNumber = itemsProcessed.toLong(), timestamp = timestamp)

// --- Suspend top-level function ---

suspend fun processFlow_V18(handler: FlowHandler_V18, items: List<StreamData_V18>) {
    for (item in items) {
        handler.handle(item)
    }
}

// --- Lambda-based builder referencing JavaB ---

fun configureBridge_V18(bridge: StreamBridge_V18, block: FlowConfig_V18.Companion.() -> FlowConfig_V18): FlowConfig_V18 {
    return FlowConfig_V18.Companion.block()
}
