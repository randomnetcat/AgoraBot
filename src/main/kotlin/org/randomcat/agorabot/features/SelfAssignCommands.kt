package org.randomcat.agorabot.features

import org.randomcat.agorabot.Feature
import org.randomcat.agorabot.FeatureSource
import org.randomcat.agorabot.FeatureSourceFactory
import org.randomcat.agorabot.commands.SelfAssignCommand
import org.randomcat.agorabot.commands.impl.defaultCommandStrategy

@FeatureSourceFactory
fun selfAssignCommandsFactory() = FeatureSource.ofConstant("self_assign_commands", Feature.ofCommands { context ->
    mapOf("selfassign" to SelfAssignCommand(context.defaultCommandStrategy))
})
