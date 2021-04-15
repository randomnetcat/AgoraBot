package org.randomcat.agorabot.features

import org.randomcat.agorabot.Feature
import org.randomcat.agorabot.commands.CopyrightCommand

fun copyrightCommandsFeature() = Feature.ofCommands { context ->
    mapOf("copyright" to CopyrightCommand(context.defaultCommandStrategy))
}
