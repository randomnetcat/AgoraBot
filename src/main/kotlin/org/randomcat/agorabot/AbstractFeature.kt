package org.randomcat.agorabot

import org.randomcat.agorabot.buttons.ButtonHandlerMap
import org.randomcat.agorabot.listener.Command

sealed class FeatureButtonData {
    object NoButtons : FeatureButtonData()
    data class RegisterHandlers(val handlerMap: ButtonHandlerMap) : FeatureButtonData()
}

abstract class AbstractFeature : Feature {
    override fun <T> query(context: FeatureContext, tag: FeatureElementTag<T>): FeatureQueryResult<T> {
        if (tag is JdaListenerTag) return tag.result(jdaListeners(context))
        if (tag is ButtonDataTag) return tag.result(buttonData())
        if (tag is BotCommandListTag) return tag.result(commandsInContext(context))

        return FeatureQueryResult.NotFound
    }

    protected abstract fun commandsInContext(context: FeatureContext): Map<String, Command>

    protected open fun jdaListeners(context: FeatureContext): List<Any> {
        return listOf()
    }

    protected open fun buttonData(): FeatureButtonData = FeatureButtonData.NoButtons
}

fun Feature.Companion.ofCommands(block: (context: FeatureContext) -> Map<String, Command>): Feature {
    return object : Feature {
        override fun <T> query(context: FeatureContext, tag: FeatureElementTag<T>): FeatureQueryResult<T> {
            if (tag is BotCommandListTag) return tag.result(block(context))

            return FeatureQueryResult.NotFound
        }
    }
}
