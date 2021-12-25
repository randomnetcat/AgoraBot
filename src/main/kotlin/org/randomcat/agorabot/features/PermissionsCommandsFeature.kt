package org.randomcat.agorabot.features

import org.randomcat.agorabot.Feature
import org.randomcat.agorabot.commands.PermissionsCommand
import org.randomcat.agorabot.commands.SudoCommand
import org.randomcat.agorabot.commands.impl.defaultCommandStrategy
import org.randomcat.agorabot.permissions.MutableGuildPermissionMap
import org.randomcat.agorabot.permissions.MutablePermissionMap

fun permissionsCommandsFeature(
    botPermissionMap: MutablePermissionMap,
    guildPermissionMap: MutableGuildPermissionMap,
) = Feature.ofCommands { context ->
    val commandStrategy = context.defaultCommandStrategy

    mapOf(
        "permissions" to PermissionsCommand(
            commandStrategy,
            botMap = botPermissionMap,
            guildMap = guildPermissionMap,
        ),
        "sudo" to SudoCommand(commandStrategy),
    )
}
