package org.randomcat.agorabot.features

import org.randomcat.agorabot.Feature
import org.randomcat.agorabot.commands.HaltCommand
import org.randomcat.agorabot.commands.StopCommand
import org.randomcat.agorabot.commands.impl.defaultCommandStrategy

fun adminCommandsFeature(writeHammertimeChannelFun: (channelId: String) -> Unit) = Feature.ofCommands { context ->
    val commandStrategy = context.defaultCommandStrategy

    mapOf(
        "halt" to HaltCommand(commandStrategy),
        "stop" to StopCommand(commandStrategy, writeChannelFun = writeHammertimeChannelFun),
    )
}
