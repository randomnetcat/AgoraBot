package org.randomcat.agorabot

import net.dv8tion.jda.api.JDA
import org.randomcat.agorabot.buttons.ButtonHandlerMap
import org.randomcat.agorabot.commands.impl.BaseCommandStrategy
import org.randomcat.agorabot.listener.Command
import org.randomcat.agorabot.listener.QueryableCommandRegistry
import kotlin.reflect.KClass

interface FeatureContext {
    // The strategy that should be used for commands if there is no specific other need for a specific command.
    val defaultCommandStrategy: BaseCommandStrategy

    // Returns the global command registry. This should not be invoked during registration, only after commands
    // have started executing.
    fun commandRegistry(): QueryableCommandRegistry
}

sealed class FeatureButtonData {
    object NoButtons : FeatureButtonData()
    data class RegisterHandlers(val handlerMap: ButtonHandlerMap) : FeatureButtonData()
}

interface FeatureElementTag<T>

@Suppress("unused", "UNCHECKED_CAST")
fun <To, From> FeatureElementTag<From>.result(value: From): FeatureQueryResult<To> {
    // Deliberately unchecked cast. This verifies the input type while casting to the unknown return type of the query
    // function.
    return FeatureQueryResult.Found(value) as FeatureQueryResult<To>
}

sealed class FeatureQueryResult<out T> {
    data class Found<T>(val value: T) : FeatureQueryResult<T>()
    object NotFound : FeatureQueryResult<Nothing>()
}

interface Feature {
    fun <T> query(tag: FeatureElementTag<T>, context: FeatureContext): FeatureQueryResult<T>

    fun commandsInContext(context: FeatureContext): Map<String, Command>
    fun registerListenersTo(jda: JDA) {}

    fun buttonData(): FeatureButtonData = FeatureButtonData.NoButtons

    companion object {
        fun ofCommands(block: (context: FeatureContext) -> Map<String, Command>): Feature {
            return object : Feature {
                override fun <T> query(tag: FeatureElementTag<T>, context: FeatureContext): FeatureQueryResult<T> {
                    return FeatureQueryResult.NotFound
                }

                override fun commandsInContext(context: FeatureContext): Map<String, Command> {
                    return block(context)
                }
            }
        }
    }
}

abstract class AbstractFeature : Feature {
    override fun <T> query(tag: FeatureElementTag<T>, context: FeatureContext): FeatureQueryResult<T> {
        return FeatureQueryResult.NotFound
    }

    final override fun registerListenersTo(jda: JDA) {
        jda.addEventListener(*jdaListeners().toTypedArray())
    }

    protected open fun jdaListeners(): List<Any> {
        return listOf()
    }

    override fun buttonData(): FeatureButtonData = FeatureButtonData.NoButtons
}
