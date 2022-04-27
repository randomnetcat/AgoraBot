package org.randomcat.agorabot

import org.randomcat.agorabot.buttons.feature.ButtonDataTag
import org.randomcat.agorabot.buttons.feature.FeatureButtonData
import org.randomcat.agorabot.listener.Command

abstract class AbstractFeature : Feature {
    override fun <T> query(context: FeatureContext, tag: FeatureElementTag<T>): FeatureQueryResult<T> {
        if (tag is JdaListenerTag) return tag.result(jdaListeners(context))
        if (tag is ButtonDataTag) return tag.result(buttonData(context))
        if (tag is BotCommandListTag) return tag.result(commandsInContext(context))

        return FeatureQueryResult.NotFound
    }

    protected abstract fun commandsInContext(context: FeatureContext): Map<String, Command>

    protected open fun jdaListeners(context: FeatureContext): List<Any> {
        return listOf()
    }

    protected open fun buttonData(context: FeatureContext): FeatureButtonData = FeatureButtonData.NoButtons
}

fun Feature.Companion.ofCommands(block: (context: FeatureContext) -> Map<String, Command>): Feature {
    return object : Feature {
        override fun <T> query(context: FeatureContext, tag: FeatureElementTag<T>): FeatureQueryResult<T> {
            if (tag is BotCommandListTag) return tag.result(block(context))

            return FeatureQueryResult.NotFound
        }
    }
}
