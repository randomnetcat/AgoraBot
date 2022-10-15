package org.randomcat.agorabot.features

import org.randomcat.agorabot.*
import org.randomcat.agorabot.buttons.ButtonRequestData
import org.randomcat.agorabot.buttons.ButtonRequestDataMap
import org.randomcat.agorabot.buttons.ButtonRequestDescriptor
import org.randomcat.agorabot.buttons.ButtonRequestId
import org.randomcat.agorabot.buttons.feature.buttonRequestDataMap
import org.randomcat.agorabot.commands.base.requirements.discord_ext.ButtonsStrategy
import org.randomcat.agorabot.commands.base.requirements.discord_ext.ButtonsStrategyTag
import java.time.Instant
import java.util.*

private object ButtonStrategyCacheKey

private fun makeButtonStrategy(buttonMap: ButtonRequestDataMap): ButtonsStrategy {
    return object : ButtonsStrategy {
        override fun storeButtonRequestAndGetId(
            descriptor: ButtonRequestDescriptor,
            expiry: Instant,
        ): ButtonRequestId {
            return buttonMap.putRequest(
                ButtonRequestData(
                    descriptor = descriptor,
                    expiry = expiry,
                ),
            )
        }

        override fun invalidButtonId(): ButtonRequestId {
            return ButtonRequestId("INVALID-" + UUID.randomUUID().toString())
        }
    }
}

@FeatureSourceFactory
fun baseCommandButtonsSource() = FeatureSource.ofConstant("base_command_buttons_default", object : Feature {
    override fun <T> query(context: FeatureContext, tag: FeatureElementTag<T>): FeatureQueryResult<T> {
        if (tag is BaseCommandDependencyTag && tag.baseTag is ButtonsStrategyTag) {
            return tag.result(context.cache(ButtonStrategyCacheKey) {
                makeButtonStrategy(context.buttonRequestDataMap)
            })
        }

        return FeatureQueryResult.NotFound
    }
})
