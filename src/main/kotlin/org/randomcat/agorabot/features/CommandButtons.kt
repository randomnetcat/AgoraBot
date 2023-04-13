package org.randomcat.agorabot.features

import org.randomcat.agorabot.*
import org.randomcat.agorabot.buttons.ButtonRequestData
import org.randomcat.agorabot.buttons.ButtonRequestDataMap
import org.randomcat.agorabot.buttons.ButtonRequestDescriptor
import org.randomcat.agorabot.buttons.ButtonRequestId
import org.randomcat.agorabot.buttons.feature.ButtonRequestDataMapTag
import org.randomcat.agorabot.commands.base.requirements.discord_ext.ButtonsStrategy
import org.randomcat.agorabot.commands.base.requirements.discord_ext.ButtonsStrategyTag
import java.time.Instant
import java.util.*

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

private val buttonMapDep = FeatureDependency.Single(ButtonRequestDataMapTag)

@FeatureSourceFactory
fun baseCommandButtonsSource() = object : FeatureSource.NoConfig {
    override val featureName: String
        get() = "base_command_buttons_default"

    override val dependencies: List<FeatureDependency<*>>
        get() = listOf(buttonMapDep)

    override val provides: List<FeatureElementTag<*>>
        get() = listOf(BaseCommandDependencyTag)

    override fun createFeature(context: FeatureSourceContext): Feature {
        val strategy = makeButtonStrategy(context[buttonMapDep])

        return Feature.singleTag(
            BaseCommandDependencyTag,
            BaseCommandDependencyResult(
                baseTag = ButtonsStrategyTag,
                value = strategy,
            )
        )
    }
}
