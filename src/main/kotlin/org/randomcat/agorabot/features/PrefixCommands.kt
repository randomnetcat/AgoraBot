package org.randomcat.agorabot.features

import org.randomcat.agorabot.Feature
import org.randomcat.agorabot.FeatureSource
import org.randomcat.agorabot.FeatureSourceFactory
import org.randomcat.agorabot.commands.PrefixCommand
import org.randomcat.agorabot.commands.impl.defaultCommandStrategy
import org.randomcat.agorabot.config.prefixMap
import org.randomcat.agorabot.ofCommands

@FeatureSourceFactory
fun prefixCommandsFactory() = FeatureSource.ofConstant("prefix_commands", Feature.ofCommands { context ->
    mapOf("prefix" to PrefixCommand(context.defaultCommandStrategy, context.prefixMap))
})
