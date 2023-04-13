package org.randomcat.agorabot.features

import org.randomcat.agorabot.FeatureSource
import org.randomcat.agorabot.FeatureSourceFactory
import org.randomcat.agorabot.buttons.ButtonHandlerMap
import org.randomcat.agorabot.buttons.feature.ButtonDataTag
import org.randomcat.agorabot.buttons.feature.FeatureButtonData
import org.randomcat.agorabot.buttons.withType
import org.randomcat.agorabot.commands.ButtonTestCommand
import org.randomcat.agorabot.ofBaseCommands

@FeatureSourceFactory
fun buttonTestCommandsFactory() = FeatureSource.ofBaseCommands(
    "button_test_commands",
    mapOf(
        "button_test" to { ButtonTestCommand(it) },
    ),
)

@FeatureSourceFactory
fun buttonTestButtonsFactory() = FeatureSource.ofConstant(
    "button_test_buttons",
    ButtonDataTag,
    FeatureButtonData.RegisterHandlers(
        ButtonHandlerMap {
            withType<ButtonTestCommand.SuccessRequest> { context, _ ->
                context.event.reply("Success!").setEphemeral(true).queue()
            }

            withType<ButtonTestCommand.FailureRequest> { context, _ ->
                context.event.reply("Failure!").setEphemeral(true).queue()
            }
        },
    ),
)
