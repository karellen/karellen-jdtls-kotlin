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

// Annotation class
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class ServiceAnnotation_V14(val description: String = "")

// Interface referencing only KotlinB types: EventHandler_V14
interface BaseService_V14 {
    fun registerHandler(handler: EventHandler_V14)
    fun removeHandler(handler: EventHandler_V14): Boolean
    fun listHandlers(): List<EventHandler_V14>
    fun findHandler(predicate: (EventHandler_V14) -> Boolean): EventHandler_V14?
}

// Data class with multiple fields
data class ServiceConfig_V14(
    val serviceName: String,
    val maxConnections: Int,
    val timeoutMs: Long,
    val tags: List<String>,
    val retryEnabled: Boolean
) {
    // Operator overloading: plus to merge configs
    operator fun plus(other: ServiceConfig_V14): ServiceConfig_V14 = copy(
        maxConnections = this.maxConnections + other.maxConnections,
        tags = this.tags + other.tags
    )
}

// Sealed class hierarchy with 3 subclasses
sealed class ServiceStatus_V14 {
    data class Active_V14(val handlerCount: Int, val uptime: Long) : ServiceStatus_V14()
    data class Inactive_V14(val reason: String) : ServiceStatus_V14()
    data class Error_V14(val error: Throwable, val failedAt: Long) : ServiceStatus_V14()
}

// Enum class with methods
enum class ProcessorType_V14(val parallel: Boolean) {
    SYNC(false),
    ASYNC(true),
    BATCH(true),
    STREAMING(true);

    fun describe(): String = "${name}(parallel=$parallel)"

    fun isCompatibleWith(other: ProcessorType_V14): Boolean =
        this.parallel == other.parallel
}

// Delegation interface
interface ServiceCounter_V14 {
    val count: Int
    fun increment(): ServiceCounter_V14
    fun reset(): ServiceCounter_V14
}

// Delegation target
class DefaultCounter_V14(override val count: Int = 0) : ServiceCounter_V14 {
    override fun increment(): ServiceCounter_V14 = DefaultCounter_V14(count + 1)
    override fun reset(): ServiceCounter_V14 = DefaultCounter_V14(0)
}

// Open class using delegation, type parameters, inner class, companion object,
// operator overloading, ranges, lambdas — references only KotlinB types
@ServiceAnnotation_V14(description = "Main service processor implementation")
open class ServiceProcessor_V14<T>(
    private val config: ServiceConfig_V14,
    counter: ServiceCounter_V14 = DefaultCounter_V14()
) : BaseService_V14, ServiceCounter_V14 by counter {

    private val handlers = mutableMapOf<String, EventHandler_V14>()
    private var status: ServiceStatus_V14 = ServiceStatus_V14.Inactive_V14("not started")
    private var processorType: ProcessorType_V14 = ProcessorType_V14.SYNC

    override fun registerHandler(handler: EventHandler_V14) {
        handlers[handler.name()] = handler
        status = ServiceStatus_V14.Active_V14(handlers.size, System.currentTimeMillis())
    }

    override fun removeHandler(handler: EventHandler_V14): Boolean {
        val removed = handlers.remove(handler.name()) != null
        if (handlers.isEmpty()) {
            status = ServiceStatus_V14.Inactive_V14("all handlers removed")
        }
        return removed
    }

    override fun listHandlers(): List<EventHandler_V14> = handlers.values.toList()

    override fun findHandler(predicate: (EventHandler_V14) -> Boolean): EventHandler_V14? =
        handlers.values.firstOrNull(predicate)

    // Operator overloading: get by name
    operator fun get(name: String): EventHandler_V14? = handlers[name]

    // Operator overloading: set by name
    operator fun set(name: String, handler: EventHandler_V14) {
        handlers[name] = handler
    }

    // Range usage
    fun handlersInRange(range: IntRange): List<EventHandler_V14> {
        return handlers.entries
            .filter { it.key.length in range }
            .map { it.value }
    }

    // Lambda parameter with type parameter
    fun <R> withTransaction(block: (ServiceProcessor_V14<T>) -> R): R {
        return try {
            block(this)
        } catch (e: Exception) {
            status = ServiceStatus_V14.Error_V14(e, System.currentTimeMillis())
            throw e
        }
    }

    // Process using EventData_V14 (KotlinB type)
    fun processEvent(event: EventData_V14) {
        val targetHandler = handlers[event.source]
        targetHandler?.handle(event)
    }

    // Range iteration
    fun processEventBatch(events: List<EventData_V14>) {
        for (i in 0..events.size - 1) {
            processEvent(events[i])
        }
    }

    fun getStatus(): ServiceStatus_V14 = status

    fun setProcessorType(type: ProcessorType_V14) {
        this.processorType = type
    }

    // Inner class
    inner class ProcessorSnapshot_V14 {
        val handlerCount: Int = handlers.size
        val currentStatus: ServiceStatus_V14 = status
        val currentType: ProcessorType_V14 = processorType
        val configName: String = config.serviceName

        fun isActive(): Boolean = currentStatus is ServiceStatus_V14.Active_V14
    }

    fun snapshot(): ProcessorSnapshot_V14 = ProcessorSnapshot_V14()

    companion object {
        const val MAX_HANDLERS: Int = 128
        const val DEFAULT_TIMEOUT_MS: Long = 3000L

        fun <T> withDefaults(): ServiceProcessor_V14<T> {
            val config = ServiceConfig_V14(
                serviceName = "default",
                maxConnections = 10,
                timeoutMs = DEFAULT_TIMEOUT_MS,
                tags = listOf("default", "auto"),
                retryEnabled = true
            )
            return ServiceProcessor_V14(config)
        }
    }
}

// Extension functions on KotlinB types
fun EventHandler_V14.toPair(priority: Int): Pair<EventHandler_V14, Int> =
    this to priority

fun List<EventHandler_V14>.filterActive(): List<EventHandler_V14> =
    this.filter { it.isActive() }

// Type-parameterized extension function
fun <T : EventHandler_V14> List<T>.sortedByName(): List<T> =
    this.sortedBy { it.name() }
