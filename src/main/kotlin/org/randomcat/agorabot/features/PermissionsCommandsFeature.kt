package org.randomcat.agorabot.features

import org.randomcat.agorabot.Feature
import org.randomcat.agorabot.FeatureSource
import org.randomcat.agorabot.FeatureSourceFactory
import org.randomcat.agorabot.commands.PermissionsCommand
import org.randomcat.agorabot.commands.SudoCommand
import org.randomcat.agorabot.commands.impl.defaultCommandStrategy
import org.randomcat.agorabot.permissions.botPermissionMap
import org.randomcat.agorabot.permissions.guildPermissionMap

@FeatureSourceFactory
fun permissionsCommandsFactory() = FeatureSource.ofConstant("permissions_commands", Feature.ofCommands { context ->
    val commandStrategy = context.defaultCommandStrategy

    mapOf(
        "permissions" to PermissionsCommand(
            commandStrategy,
            botMap = context.botPermissionMap,
            guildMap = context.guildPermissionMap,
        ),
        "sudo" to SudoCommand(commandStrategy),
    )
})
