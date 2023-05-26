package org.randomcat.agorabot

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import org.randomcat.agorabot.irc.IrcChannel
import org.randomcat.agorabot.listener.CommandEventSource

sealed class CommandOutputSink {
    data class Discord(val channel: MessageChannel) : CommandOutputSink()
    data class Irc(val channel: IrcChannel) : CommandOutputSink()
}

data class CommandOutputMapping(
    private val sinksForDiscordFun: (CommandEventSource.Discord) -> List<CommandOutputSink>,
    private val sinksForIrcFun: (CommandEventSource.Irc) -> List<CommandOutputSink>,
) {

    /**
     * Returns the external sinks for a given source, i.e. those that are not the source itself.
     */
    fun externalSinksFor(source: CommandEventSource): List<CommandOutputSink> {
        return when (source) {
            is CommandEventSource.Discord -> {
                sinksForDiscordFun(source)
            }

            is CommandEventSource.Irc -> {
                sinksForIrcFun(source)
            }
        }
    }

    companion object {
        private val EMPTY by lazy {
            CommandOutputMapping(
                sinksForDiscordFun = { emptyList() },
                sinksForIrcFun = { emptyList() },
            )
        }

        fun empty(): CommandOutputMapping = EMPTY
    }
}
