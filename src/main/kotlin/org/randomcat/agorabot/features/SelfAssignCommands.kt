package org.randomcat.agorabot.features

import org.randomcat.agorabot.Feature
import org.randomcat.agorabot.commands.SelfAssignCommand

fun selfAssignCommandsFeature() = Feature.ofCommands { context ->
    mapOf("selfassign" to SelfAssignCommand(context.defaultCommandStrategy))
}
