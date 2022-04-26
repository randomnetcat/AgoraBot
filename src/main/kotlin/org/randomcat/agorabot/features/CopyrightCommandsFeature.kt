package org.randomcat.agorabot.features

import org.randomcat.agorabot.Feature
import org.randomcat.agorabot.FeatureSource
import org.randomcat.agorabot.FeatureSourceFactory
import org.randomcat.agorabot.commands.CopyrightCommand
import org.randomcat.agorabot.commands.impl.defaultCommandStrategy
import org.randomcat.agorabot.ofCommands

@FeatureSourceFactory
fun copyrightCommandsFactory() = FeatureSource.ofConstant("copyright_commands", Feature.ofCommands { context ->
    mapOf("copyright" to CopyrightCommand(context.defaultCommandStrategy))
})
