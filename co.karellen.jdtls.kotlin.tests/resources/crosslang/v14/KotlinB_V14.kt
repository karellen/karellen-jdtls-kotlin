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
package crosslang.v14

// Interface referenced by KotlinA (BaseService_V14)
interface EventHandler_V14 {
    fun name(): String
    fun isActive(): Boolean
    fun handle(event: EventData_V14): Boolean
    fun shutdown()
}

// Data class referenced by JavaB (ServiceBridge_V14) and KotlinA
data class EventData_V14(
    val id: String,
    val source: String,
    val payload: Map<String, String>,
    val timestamp: Long
) {
    fun hasKey(key: String): Boolean = payload.containsKey(key)

    fun valueOrDefault(key: String, default: String): String =
        payload.getOrDefault(key, default)

    companion object {
        fun empty(): EventData_V14 =
            EventData_V14(id = "", source = "", payload = emptyMap(), timestamp = 0L)
    }
}

// Class referencing JavaB (ServiceBridge_V14) and KotlinA (BaseService_V14, ServiceConfig_V14)
class BridgeAdapter_V14(
    private val adapterName: String,
    private val bridge: ServiceBridge_V14,
    private val service: BaseService_V14,
    private val config: ServiceConfig_V14
) : EventHandler_V14 {

    private var active: Boolean = true
    private val processedIds = mutableSetOf<String>()

    override fun name(): String = adapterName

    override fun isActive(): Boolean = active

    override fun handle(event: EventData_V14): Boolean {
        if (!active) return false
        bridge.dispatch(event, this)
        processedIds.add(event.id)
        return true
    }

    override fun shutdown() {
        active = false
    }

    fun pollFromBridge(timeoutMs: Long): EventData_V14? {
        return bridge.poll(this, timeoutMs)
    }

    fun listPeerAdapters(): List<BridgeAdapter_V14> {
        @Suppress("UNCHECKED_CAST")
        return bridge.listAdapters() as List<BridgeAdapter_V14>
    }

    fun processedCount(): Int = processedIds.size

    fun hasProcessed(id: String): Boolean = id in processedIds

    fun getServiceConfig(): ServiceConfig_V14 = config

    // Inner class for adapter statistics
    inner class AdapterStats_V14 {
        val name: String = adapterName
        val isActive: Boolean = active
        val processed: Int = processedIds.size
        val peerCount: Int = bridge.listAdapters().size
        val serviceName: String = config.serviceName

        override fun toString(): String =
            "AdapterStats(name=$name, active=$isActive, processed=$processed, peers=$peerCount)"
    }

    fun stats(): AdapterStats_V14 = AdapterStats_V14()

    companion object {
        const val DEFAULT_TIMEOUT_MS: Long = 5000L

        fun create(name: String, bridge: ServiceBridge_V14, service: BaseService_V14): BridgeAdapter_V14 {
            val config = ServiceConfig_V14(
                serviceName = name,
                maxConnections = 5,
                timeoutMs = DEFAULT_TIMEOUT_MS,
                tags = listOf("adapter", "auto"),
                retryEnabled = false
            )
            return BridgeAdapter_V14(name, bridge, service, config)
        }
    }
}

// Object (singleton) factory referenced by KotlinA
object AdapterFactory_V14 {
    private val registry = mutableMapOf<String, EventHandler_V14>()

    fun createHandler(name: String): EventHandler_V14 {
        val handler = object : EventHandler_V14 {
            private var active = true

            override fun name(): String = name

            override fun isActive(): Boolean = active

            override fun handle(event: EventData_V14): Boolean {
                return active
            }

            override fun shutdown() {
                active = false
            }
        }
        registry[name] = handler
        return handler
    }

    fun getHandler(name: String): EventHandler_V14? = registry[name]

    fun registeredNames(): Set<String> = registry.keys.toSet()

    fun clear() {
        registry.values.forEach { it.shutdown() }
        registry.clear()
    }
}

// Sealed interface for adapter events
sealed interface AdapterEvent_V14 {
    val adapterName: String

    data class Connected_V14(override val adapterName: String, val peerCount: Int) : AdapterEvent_V14
    data class Disconnected_V14(override val adapterName: String, val reason: String) : AdapterEvent_V14
    data class EventReceived_V14(override val adapterName: String, val event: EventData_V14) : AdapterEvent_V14
    data class Error_V14(override val adapterName: String, val error: Throwable) : AdapterEvent_V14
}

// Extension functions on JavaB types
fun ServiceBridge_V14.dispatchAll(events: List<EventData_V14>, adapter: BridgeAdapter_V14) {
    events.forEach { dispatch(it, adapter) }
}

fun ServiceBridge_V14.BridgeConfig_V14.isHighRetry(): Boolean =
    maxRetries >= 5

fun ServiceBridge_V14.BridgeConfig_V14.summary(): String =
    "name=$name, retries=$maxRetries, delay=$retryDelayMs, async=$isAsyncMode"

// Type alias for readability
typealias EventProcessor_V14 = (EventData_V14) -> Boolean
