package org.randomcat.agorabot

import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap
import net.dv8tion.jda.api.entities.MessageChannel
import org.randomcat.agorabot.irc.IrcChannel
import org.randomcat.agorabot.listener.CommandEventSource

sealed class CommandOutputSink {
    data class Discord(val channel: MessageChannel) : CommandOutputSink()
    data class Irc(val channel: IrcChannel) : CommandOutputSink()
}

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

    /**
     * Returns the external sinks for a given source, i.e. those that are not the source itself.
     */
    fun externalSinksFor(source: CommandEventSource): List<CommandOutputSink> {
        return when (source) {
            is CommandEventSource.Discord -> {
                listOfNotNull(
                    discordToIrcMap[source.event.channel.id]?.invoke()?.let { CommandOutputSink.Irc(it) },
                )
            }

            is CommandEventSource.Irc -> {
                listOfNotNull(
                    ircToDiscordMap[source.event.channel.name]?.invoke()?.let { CommandOutputSink.Discord(it) },
                )
            }
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
