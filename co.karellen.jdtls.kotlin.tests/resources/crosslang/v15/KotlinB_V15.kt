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
package crosslang.v15

// Interface referenced by KotlinA (MessageService_V15)
interface ChannelHandler_V15 {
    fun name(): String
    fun isActive(): Boolean
    fun handle(payload: MessagePayload_V15): Boolean
    fun shutdown()
}

// Data class referenced by JavaB (MessageBridge_V15) and KotlinA
data class MessagePayload_V15(
    val id: String,
    val content: ByteArray,
    val headers: Map<String, String>,
    val timestamp: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MessagePayload_V15) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    fun contentAsString(): String = content.decodeToString()

    fun hasHeader(key: String): Boolean = headers.containsKey(key)

    companion object
}

// Class referencing JavaB (MessageBridge_V15) and KotlinA (MessageService_V15)
class ChannelAdapter_V15(
    private val channelName: String,
    private val bridge: MessageBridge_V15,
    private val service: MessageService_V15
) : ChannelHandler_V15 {

    private var active: Boolean = true
    private val processedIds = mutableSetOf<String>()

    override fun name(): String = channelName

    override fun isActive(): Boolean = active

    override fun handle(payload: MessagePayload_V15): Boolean {
        if (!active) return false
        bridge.send(payload, this)
        processedIds.add(payload.id)
        return true
    }

    override fun shutdown() {
        active = false
    }

    fun receiveFromBridge(timeoutMs: Long): MessagePayload_V15? {
        return bridge.receive(this, timeoutMs)
    }

    fun listPeerAdapters(): List<ChannelAdapter_V15> {
        @Suppress("UNCHECKED_CAST")
        return bridge.listAdapters() as List<ChannelAdapter_V15>
    }

    fun processedCount(): Int = processedIds.size

    fun hasProcessed(id: String): Boolean = id in processedIds

    fun getMetrics(): MessageBridge_V15.BridgeMetrics_V15 = bridge.getMetrics()

    // Inner class for adapter statistics
    inner class AdapterStats_V15 {
        val adapterName: String = channelName
        val isActive: Boolean = active
        val processed: Int = processedIds.size
        val peerCount: Int = bridge.listAdapters().size

        override fun toString(): String =
            "AdapterStats(name=$adapterName, active=$isActive, processed=$processed, peers=$peerCount)"
    }

    fun stats(): AdapterStats_V15 = AdapterStats_V15()
}

// Object (singleton) referenced by KotlinA (MessageRouter_V15.createHandler)
object ChannelFactory_V15 {
    private val registry = mutableMapOf<String, ChannelHandler_V15>()

    fun createHandler(name: String): ChannelHandler_V15 {
        val handler = object : ChannelHandler_V15 {
            private var active = true

            override fun name(): String = name

            override fun isActive(): Boolean = active

            override fun handle(payload: MessagePayload_V15): Boolean {
                return active
            }

            override fun shutdown() {
                active = false
            }
        }
        registry[name] = handler
        return handler
    }

    fun getHandler(name: String): ChannelHandler_V15? = registry[name]

    fun registeredNames(): Set<String> = registry.keys.toSet()

    fun clear() {
        registry.values.forEach { it.shutdown() }
        registry.clear()
    }
}

// Sealed interface for channel events
sealed interface ChannelEvent_V15 {
    val channelName: String

    data class Connected_V15(override val channelName: String, val adapterCount: Int) : ChannelEvent_V15
    data class Disconnected_V15(override val channelName: String, val reason: String) : ChannelEvent_V15
    data class MessageReceived_V15(override val channelName: String, val payload: MessagePayload_V15) : ChannelEvent_V15
    data class Error_V15(override val channelName: String, val error: Throwable) : ChannelEvent_V15
}

// Extension functions on JavaB types
fun MessageBridge_V15.sendAll(payloads: List<MessagePayload_V15>, adapter: ChannelAdapter_V15) {
    payloads.forEach { send(it, adapter) }
}

fun MessageBridge_V15.BridgeMetrics_V15.isHealthy(): Boolean =
    errorRate < 0.05

fun MessageBridge_V15.BridgeMetrics_V15.summary(): String =
    "sent=$messagesSent, received=$messagesReceived, errors=$errorCount, rate=$errorRate"

// Companion-object-like utilities for MessagePayload_V15
fun MessagePayload_V15.Companion.empty(): MessagePayload_V15 =
    MessagePayload_V15(id = "", content = ByteArray(0), headers = emptyMap(), timestamp = 0L)

// Type alias for readability
typealias PayloadProcessor_V15 = (MessagePayload_V15) -> Boolean
