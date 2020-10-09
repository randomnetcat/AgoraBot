package org.randomcat.agorabot.irc

import kotlinx.collections.immutable.ImmutableList

data class IrcGlobalConfig(
    val nickname: String,
    val server: String,
    val port: Int,
    val serverIsSecure: Boolean,
)

data class IrcConnectionConfig(
    val ircChannelName: String,
    val discordChannelId: String,
)

data class IrcConfig(
    val global: IrcGlobalConfig,
    val connections: ImmutableList<IrcConnectionConfig>,
)
