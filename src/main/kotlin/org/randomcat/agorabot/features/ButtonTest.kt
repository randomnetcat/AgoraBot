package org.randomcat.agorabot.features

import org.randomcat.agorabot.AbstractFeature
import org.randomcat.agorabot.FeatureContext
import org.randomcat.agorabot.FeatureSource
import org.randomcat.agorabot.FeatureSourceFactory
import org.randomcat.agorabot.buttons.ButtonHandlerMap
import org.randomcat.agorabot.buttons.feature.FeatureButtonData
import org.randomcat.agorabot.buttons.withType
import org.randomcat.agorabot.commands.ButtonTestCommand
import org.randomcat.agorabot.commands.impl.defaultCommandStrategy
import org.randomcat.agorabot.listener.Command

private val buttonTestFeature = object : AbstractFeature() {
    override fun commandsInContext(context: FeatureContext): Map<String, Command> {
        return mapOf(
            "button_test" to ButtonTestCommand(context.defaultCommandStrategy),
        )
    }

    override fun buttonData(context: FeatureContext): FeatureButtonData {
        return FeatureButtonData.RegisterHandlers(
            ButtonHandlerMap {
                withType<ButtonTestCommand.SuccessRequest> { context, request ->
                    context.event.reply("Success!").setEphemeral(true).queue()
                }

                withType<ButtonTestCommand.FailureRequest> { context, request ->
                    context.event.reply("Failure!").setEphemeral(true).queue()
                }
            }
        )
    }
}

@FeatureSourceFactory
fun buttonTestFactory() = FeatureSource.ofConstant("button_test", buttonTestFeature)
