package org.randomcat.agorabot.features

import org.randomcat.agorabot.Feature
import org.randomcat.agorabot.FeatureSource
import org.randomcat.agorabot.FeatureSourceFactory
import org.randomcat.agorabot.commands.CheckSquareCommand
import org.randomcat.agorabot.commands.impl.defaultCommandStrategy
import org.randomcat.agorabot.ofCommands

@FeatureSourceFactory
fun imageCommandsFactory() = FeatureSource.ofConstant("image_commands", Feature.ofCommands { context ->
    mapOf("check_square" to CheckSquareCommand(context.defaultCommandStrategy))
})
