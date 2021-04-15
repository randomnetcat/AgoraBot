package org.randomcat.agorabot.features

import org.randomcat.agorabot.Feature
import org.randomcat.agorabot.commands.CfjCommand
import org.randomcat.agorabot.commands.ChooseCommand
import org.randomcat.agorabot.commands.RngCommand
import org.randomcat.agorabot.commands.RollCommand

fun randomCommandsFeature() = Feature.ofCommands { context ->
    val commandStrategy = context.defaultCommandStrategy

    mapOf(
        "rng" to RngCommand(commandStrategy),
        "roll" to RollCommand(commandStrategy),
        "cfj" to CfjCommand(commandStrategy),
        "choose" to ChooseCommand(commandStrategy),
    )
}
