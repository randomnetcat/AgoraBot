package org.randomcat.agorabot.features

import org.randomcat.agorabot.FeatureSource
import org.randomcat.agorabot.FeatureSourceFactory
import org.randomcat.agorabot.commands.*
import org.randomcat.agorabot.ofBaseCommands

@FeatureSourceFactory
fun randomFactory() = FeatureSource.ofBaseCommands(
    "random_commands",
    mapOf(
        "rng" to ::RngCommand,
        "roll" to ::RollCommand,
        "cfj" to ::CfjCommand,
        "choose" to ::ChooseCommand,
        "shuffle" to ::ShuffleCommand,
    ),
)
