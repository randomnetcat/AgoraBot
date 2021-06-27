package org.randomcat.agorabot.buttons

import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import org.randomcat.util.requireDistinct
import kotlin.reflect.KClass

interface ButtonHandlerContext {
    val event: ButtonClickEvent
    val buttonRequestDataMap: ButtonRequestDataMap
}

typealias ButtonHandler<T> = (context: ButtonHandlerContext, request: T) -> Unit

interface ButtonHandlersReceiver {
    fun <T : Any> withTypeImpl(type: KClass<T>, handler: ButtonHandler<T>)
}

inline fun <reified T : Any> ButtonHandlersReceiver.withType(noinline handler: ButtonHandler<T>) =
    withTypeImpl(T::class, handler)


data class ButtonHandlerMap(private val handlersByType: ImmutableMap<KClass<*>, ButtonHandler<*>>) {
    companion object {
        fun mergeDisjointHandlers(handlerMaps: List<ButtonHandlerMap>): ButtonHandlerMap {
            handlerMaps.flatMap { it.handledClasses }.requireDistinct()
            return ButtonHandlerMap(handlerMaps.flatMap { it.toMap().entries }.associate { it.toPair() })
        }
    }

    constructor(handlersByType: Map<KClass<*>, ButtonHandler<*>>) : this(handlersByType.toImmutableMap())

    val handledClasses: Set<KClass<*>>
        get() = handlersByType.keys

    fun tryGetHandler(type: KClass<*>): ButtonHandler<*>? {
        return handlersByType[type]
    }

    fun toMap(): Map<KClass<*>, ButtonHandler<*>> = handlersByType
}

fun ButtonHandlerMap(block: ButtonHandlersReceiver.() -> Unit): ButtonHandlerMap {
    val map = mutableMapOf<KClass<*>, ButtonHandler<*>>()

    class ReceiverImpl : ButtonHandlersReceiver {
        override fun <T : Any> withTypeImpl(type: KClass<T>, handler: ButtonHandler<T>) {
            require(!map.containsKey(type)) { "Attempted to doubly register button request type $type" }
            map[type] = handler
        }
    }

    ReceiverImpl().block()

    return ButtonHandlerMap(map)
}
