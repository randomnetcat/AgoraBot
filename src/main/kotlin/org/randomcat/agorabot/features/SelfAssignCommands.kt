package org.randomcat.agorabot.features

import org.randomcat.agorabot.FeatureSource
import org.randomcat.agorabot.FeatureSourceFactory
import org.randomcat.agorabot.commands.SelfAssignCommand
import org.randomcat.agorabot.ofBaseCommands

@FeatureSourceFactory
fun selfAssignCommandsFactory() = FeatureSource.ofBaseCommands(
    "self_assign_commands",
    mapOf("selfassign" to ::SelfAssignCommand),
)
