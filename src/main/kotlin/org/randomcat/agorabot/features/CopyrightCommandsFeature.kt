package org.randomcat.agorabot.features

import org.randomcat.agorabot.FeatureSource
import org.randomcat.agorabot.FeatureSourceFactory
import org.randomcat.agorabot.commands.CopyrightCommand
import org.randomcat.agorabot.ofBaseCommands

@FeatureSourceFactory
fun copyrightCommandsFactory() = FeatureSource.ofBaseCommands(
    "copyright_commands",
    mapOf(
        "copyright" to ::CopyrightCommand,
    ),
)
