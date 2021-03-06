package org.randomcat.agorabot.features

import org.randomcat.agorabot.Feature
import org.randomcat.agorabot.FeatureButtonData
import org.randomcat.agorabot.FeatureContext
import org.randomcat.agorabot.buttons.ButtonHandlerMap
import org.randomcat.agorabot.buttons.withType
import org.randomcat.agorabot.commands.ButtonTestCommand
import org.randomcat.agorabot.listener.Command

fun buttonTestFeature() = object : Feature {
    override fun commandsInContext(context: FeatureContext): Map<String, Command> {
        return mapOf(
            "button_test" to ButtonTestCommand(context.defaultCommandStrategy),
        )
    }

    override fun buttonData(): FeatureButtonData {
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
