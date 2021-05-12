package org.randomcat.agorabot.features

import org.randomcat.agorabot.Feature
import org.randomcat.agorabot.commands.IrcCommand
import org.randomcat.agorabot.irc.IrcConfig
import org.randomcat.agorabot.irc.MutableIrcUserListMessageMap

fun ircCommandsFeature(
    ircConfig: IrcConfig,
    ircPersistentWhoMessageMap: MutableIrcUserListMessageMap,
) = Feature.ofCommands { context ->
    mapOf(
        "irc" to IrcCommand(
            strategy = context.defaultCommandStrategy,
            lookupConnectedIrcChannel = { _, channelId ->
                ircConfig.relayConfig.entries.firstOrNull { it.discordChannelId == channelId }?.ircChannelName
            },
            persistentWhoMessageMap = ircPersistentWhoMessageMap,
        ),
    )
}
