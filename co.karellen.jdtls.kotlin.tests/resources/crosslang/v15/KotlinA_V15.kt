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

// v1.5 construct: @JvmInline value class
@JvmInline
value class MessageId_V15(val id: String) {
    init {
        require(id.isNotBlank()) { "MessageId must not be blank" }
    }

    fun shorten(): String = if (id.length > 8) id.substring(0, 8) else id
}

// v1.5 construct: second @JvmInline value class
@JvmInline
value class Priority_V15(val level: Int) : Comparable<Priority_V15> {
    init {
        require(level in 0..100) { "Priority level must be in 0..100" }
    }

    override fun compareTo(other: Priority_V15): Int = level.compareTo(other.level)

    fun isHigh(): Boolean = level >= 75
}

// Annotation class
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class MessageAnnotation_V15(val description: String = "")

// Interface referencing KotlinB types only: ChannelHandler_V15
interface MessageService_V15 {
    fun createHandler(id: MessageId_V15): ChannelHandler_V15
    fun removeHandler(id: MessageId_V15): Boolean
    fun listHandlers(): List<ChannelHandler_V15>
    fun findHandler(predicate: (ChannelHandler_V15) -> Boolean): ChannelHandler_V15?
}

// Data class with value class fields
data class MessageConfig_V15(
    val serviceId: MessageId_V15,
    val defaultPriority: Priority_V15,
    val maxHandlers: Int,
    val tags: List<String>
) {
    fun withPriority(priority: Priority_V15): MessageConfig_V15 = copy(defaultPriority = priority)
}

// Sealed class hierarchy
sealed class RoutingStatus_V15 {
    data class Active_V15(val handlerCount: Int) : RoutingStatus_V15()
    data class Degraded_V15(val reason: String, val activeHandlers: Int) : RoutingStatus_V15()
    data object Inactive_V15 : RoutingStatus_V15()
    data class Failed_V15(val error: Throwable) : RoutingStatus_V15()
}

// Enum class
enum class RouterMode_V15(val parallel: Boolean) {
    SEQUENTIAL(false),
    PARALLEL(true),
    BROADCAST(true),
    ROUND_ROBIN(false);

    fun describe(): String = "${name}(parallel=$parallel)"
}

// Delegation interface
interface MessageCounter_V15 {
    val count: Int
    fun increment(): MessageCounter_V15
}

// Delegation target
class SimpleCounter_V15(override val count: Int = 0) : MessageCounter_V15 {
    override fun increment(): MessageCounter_V15 = SimpleCounter_V15(count + 1)
}

// Open class using delegation, type parameters, inner class, companion object, operator overloading
@MessageAnnotation_V15(description = "Main router implementation")
open class MessageRouter_V15(
    private val config: MessageConfig_V15,
    counter: MessageCounter_V15 = SimpleCounter_V15()
) : MessageService_V15, MessageCounter_V15 by counter {

    private val handlers = mutableMapOf<MessageId_V15, ChannelHandler_V15>()
    private var status: RoutingStatus_V15 = RoutingStatus_V15.Inactive_V15
    private var mode: RouterMode_V15 = RouterMode_V15.SEQUENTIAL

    override fun createHandler(id: MessageId_V15): ChannelHandler_V15 {
        val handler = ChannelFactory_V15.createHandler(id.id)
        handlers[id] = handler
        status = RoutingStatus_V15.Active_V15(handlers.size)
        return handler
    }

    override fun removeHandler(id: MessageId_V15): Boolean {
        val removed = handlers.remove(id) != null
        if (handlers.isEmpty()) {
            status = RoutingStatus_V15.Inactive_V15
        }
        return removed
    }

    override fun listHandlers(): List<ChannelHandler_V15> = handlers.values.toList()

    override fun findHandler(predicate: (ChannelHandler_V15) -> Boolean): ChannelHandler_V15? =
        handlers.values.firstOrNull(predicate)

    // Operator overloading: get/set by MessageId_V15
    operator fun get(id: MessageId_V15): ChannelHandler_V15? = handlers[id]

    operator fun set(id: MessageId_V15, handler: ChannelHandler_V15) {
        handlers[id] = handler
    }

    // Operator overloading: plus to merge routers
    operator fun plus(other: MessageRouter_V15): MessageRouter_V15 {
        val merged = MessageRouter_V15(config)
        merged.handlers.putAll(this.handlers)
        merged.handlers.putAll(other.handlers)
        return merged
    }

    // Range usage
    fun handlersInPriorityRange(range: IntRange): List<ChannelHandler_V15> {
        return handlers.entries
            .filter { it.key.id.length in range }
            .map { it.value }
    }

    // Lambda parameter
    fun <R> withTransaction(block: (MessageRouter_V15) -> R): R {
        return try {
            block(this)
        } catch (e: Exception) {
            status = RoutingStatus_V15.Failed_V15(e)
            throw e
        }
    }

    fun getStatus(): RoutingStatus_V15 = status

    fun setMode(mode: RouterMode_V15) {
        this.mode = mode
    }

    // Inner class
    inner class RouterSnapshot_V15 {
        val handlerCount: Int = handlers.size
        val currentStatus: RoutingStatus_V15 = status
        val currentMode: RouterMode_V15 = mode
        val configId: MessageId_V15 = config.serviceId

        fun isActive(): Boolean = currentStatus is RoutingStatus_V15.Active_V15
    }

    fun snapshot(): RouterSnapshot_V15 = RouterSnapshot_V15()

    companion object {
        const val MAX_HANDLERS: Int = 256
        const val DEFAULT_TIMEOUT_MS: Long = 5000L

        fun withDefaults(): MessageRouter_V15 {
            val config = MessageConfig_V15(
                serviceId = MessageId_V15("default"),
                defaultPriority = Priority_V15(50),
                maxHandlers = MAX_HANDLERS,
                tags = listOf("default", "auto")
            )
            return MessageRouter_V15(config)
        }
    }
}

// Extension functions on KotlinB types
fun ChannelHandler_V15.toPriorityPair(priority: Priority_V15): Pair<ChannelHandler_V15, Priority_V15> =
    this to priority

fun List<ChannelHandler_V15>.filterActive(): List<ChannelHandler_V15> =
    this.filter { it.isActive() }

// Type-parameterized extension function
fun <T : ChannelHandler_V15> List<T>.sortedByName(): List<T> =
    this.sortedBy { it.name() }
