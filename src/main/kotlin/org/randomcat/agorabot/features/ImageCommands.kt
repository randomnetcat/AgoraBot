package org.randomcat.agorabot.features

import org.randomcat.agorabot.FeatureSource
import org.randomcat.agorabot.FeatureSourceFactory
import org.randomcat.agorabot.commands.CheckSquareCommand
import org.randomcat.agorabot.ofBaseCommands

@FeatureSourceFactory
fun imageCommandsFactory() = FeatureSource.ofBaseCommands(
    "image_commands",
    mapOf(
        "check_square" to ::CheckSquareCommand,
    ),
)
