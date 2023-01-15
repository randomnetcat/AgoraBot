package org.randomcat.agorabot.features

import org.randomcat.agorabot.AbstractFeatureSource
import org.randomcat.agorabot.FeatureDependency
import org.randomcat.agorabot.FeatureSourceContext
import org.randomcat.agorabot.FeatureSourceFactory
import org.randomcat.agorabot.buttons.ButtonHandlerMap
import org.randomcat.agorabot.buttons.feature.FeatureButtonData
import org.randomcat.agorabot.buttons.withType
import org.randomcat.agorabot.commands.ButtonTestCommand
import org.randomcat.agorabot.commands.impl.BaseCommandStrategyTag
import org.randomcat.agorabot.listener.Command

private val strategyDep = FeatureDependency.Single(BaseCommandStrategyTag)

@FeatureSourceFactory
fun buttonTestFactory() = object : AbstractFeatureSource.NoConfig() {
    override val featureName: String
        get() = "button_test"

    override val dependencies: List<FeatureDependency<*>>
        get() = listOf(strategyDep)

    override fun commandsInContext(context: FeatureSourceContext): Map<String, Command> {
        return mapOf(
            "button_test" to ButtonTestCommand(context[strategyDep]),
        )
    }

    override fun buttonData(context: FeatureSourceContext): FeatureButtonData {
        return FeatureButtonData.RegisterHandlers(
            ButtonHandlerMap {
                withType<ButtonTestCommand.SuccessRequest> { context, _ ->
                    context.event.reply("Success!").setEphemeral(true).queue()
                }

                withType<ButtonTestCommand.FailureRequest> { context, _ ->
                    context.event.reply("Failure!").setEphemeral(true).queue()
                }
            }
        )
    }
}
