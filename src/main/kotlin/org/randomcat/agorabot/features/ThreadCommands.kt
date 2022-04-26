package org.randomcat.agorabot.features

import org.randomcat.agorabot.Feature
import org.randomcat.agorabot.FeatureSource
import org.randomcat.agorabot.FeatureSourceFactory
import org.randomcat.agorabot.commands.SanctifyCommand
import org.randomcat.agorabot.commands.impl.defaultCommandStrategy
import org.randomcat.agorabot.ofCommands

@FeatureSourceFactory
fun threadCommandsFactory() = FeatureSource.ofConstant("thread_commands", Feature.ofCommands { context ->
    mapOf("sanctify" to SanctifyCommand(context.defaultCommandStrategy))
})
