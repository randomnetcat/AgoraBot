package org.randomcat.agorabot.buttons

import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent
import kotlin.reflect.KClass

interface ButtonHandlerContext {
    val event: ButtonClickEvent
}

typealias ButtonHandler<T> = (context: ButtonHandlerContext, request: T) -> Unit

interface ButtonHandlersReceiver {
    fun <T : Any> withTypeImpl(type: KClass<T>, handler: ButtonHandler<T>)
}

inline fun <reified T : Any> ButtonHandlersReceiver.withType(noinline handler: ButtonHandler<T>) =
    withTypeImpl(T::class, handler)


data class ButtonHandlerMap(private val handlersByType: ImmutableMap<KClass<*>, ButtonHandler<*>>) {
    constructor(handlersByType: Map<KClass<*>, ButtonHandler<*>>) : this(handlersByType.toImmutableMap())

    fun toMap(): Map<KClass<*>, ButtonHandler<*>> = handlersByType
}

fun compileButtonHandlersBlock(block: ButtonHandlersReceiver.() -> Unit): ButtonHandlerMap {
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
