package org.randomcat.agorabot.features

import org.randomcat.agorabot.FeatureSource
import org.randomcat.agorabot.FeatureSourceFactory
import org.randomcat.agorabot.commands.SanctifyCommand
import org.randomcat.agorabot.ofBaseCommands

@FeatureSourceFactory
fun threadCommandsFactory() = FeatureSource.ofBaseCommands(
    "thread_commands",
    mapOf("sanctify" to ::SanctifyCommand),
)
