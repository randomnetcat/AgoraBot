package org.randomcat.agorabot.features

import org.randomcat.agorabot.Feature
import org.randomcat.agorabot.FeatureSource
import org.randomcat.agorabot.FeatureSourceFactory
import org.randomcat.agorabot.commands.*
import org.randomcat.agorabot.commands.impl.defaultCommandStrategy
import org.randomcat.agorabot.ofCommands

@FeatureSourceFactory
fun randomFactory() = FeatureSource.ofConstant("random_commands", Feature.ofCommands { context ->
    val commandStrategy = context.defaultCommandStrategy

    mapOf(
        "rng" to RngCommand(commandStrategy),
        "roll" to RollCommand(commandStrategy),
        "cfj" to CfjCommand(commandStrategy),
        "choose" to ChooseCommand(commandStrategy),
        "shuffle" to ShuffleCommand(commandStrategy),
    )
})
