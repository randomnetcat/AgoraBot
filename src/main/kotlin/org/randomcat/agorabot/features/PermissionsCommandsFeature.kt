package org.randomcat.agorabot.features

import org.randomcat.agorabot.FeatureDependency
import org.randomcat.agorabot.FeatureSource
import org.randomcat.agorabot.FeatureSourceFactory
import org.randomcat.agorabot.commands.PermissionsCommand
import org.randomcat.agorabot.commands.SudoCommand
import org.randomcat.agorabot.ofBaseCommands
import org.randomcat.agorabot.permissions.feature.BotPermissionMapTag
import org.randomcat.agorabot.permissions.feature.GuildPermissionMapTag

private val botMapDep = FeatureDependency.Single(BotPermissionMapTag)
private val guildMapDep = FeatureDependency.Single(GuildPermissionMapTag)

@FeatureSourceFactory
fun permissionsCommandsFactory() = FeatureSource.ofBaseCommands(
    "permissions_commands",
    listOf(botMapDep, guildMapDep),
) { strategy, context ->
    mapOf(
        "permissions" to PermissionsCommand(
            strategy,
            botMap = context[botMapDep],
            guildMap = context[guildMapDep],
        ),
        "sudo" to SudoCommand(strategy),
    )
}
