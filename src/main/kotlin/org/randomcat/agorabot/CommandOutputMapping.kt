package org.randomcat.agorabot

import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap
import net.dv8tion.jda.api.entities.MessageChannel
import org.randomcat.agorabot.irc.IrcChannel
import org.randomcat.agorabot.listener.CommandEventSource

data class CommandOutputMapping(
    private val discordToIrcMap: ImmutableMap<String /*ChannelID*/, () -> IrcChannel?>,
    private val ircToDiscordMap: ImmutableMap<String /*ChannelName*/, () -> MessageChannel?>,
) {
    constructor(
        discordToIrcMap: Map<String /*ChannelID*/, () -> IrcChannel?>,
        ircToDiscordMap: Map<String /*ChannelName*/, () -> MessageChannel?>,
    ) : this(
        discordToIrcMap = discordToIrcMap.toImmutableMap(),
        ircToDiscordMap = ircToDiscordMap.toImmutableMap(),
    )

    fun ircResponseChannelFor(source: CommandEventSource): IrcChannel? {
        return when (source) {
            is CommandEventSource.Irc -> source.event.channel
            is CommandEventSource.Discord -> discordToIrcMap[source.event.channel.id]?.invoke()
        }
    }

    fun discordResponseChannnelFor(source: CommandEventSource): MessageChannel? {
        return when (source) {
            is CommandEventSource.Discord -> source.event.channel
            is CommandEventSource.Irc -> ircToDiscordMap[source.event.channel.name]?.invoke()
        }
    }

    companion object {
        private val EMPTY by lazy {
            CommandOutputMapping(
                discordToIrcMap = emptyMap(),
                ircToDiscordMap = emptyMap(),
            )
        }

        fun empty(): CommandOutputMapping = EMPTY
    }
}
