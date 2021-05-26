@file:OptIn(ExperimentalTime::class)

package org.randomcat.agorabot.irc

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.MessageBuilder
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.hooks.SubscribeEvent
import org.kitteh.irc.client.library.event.channel.*
import org.kitteh.irc.client.library.event.helper.ActorEvent
import org.kitteh.irc.client.library.event.helper.ChannelEvent
import org.kitteh.irc.client.library.event.user.UserQuitEvent
import org.randomcat.agorabot.listener.*
import org.randomcat.agorabot.util.DiscordMessage
import org.randomcat.agorabot.util.disallowMentions
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

private val logger = LoggerFactory.getLogger("AgoraBotIRC")

private fun IrcChannel.sendDiscordMessage(message: DiscordMessage) {
    val senderName = message.member?.nickname ?: message.author.name

    val fullMessage =
        senderName +
                " says: " +
                message.contentDisplay +
                (message.attachments
                    .map { it.url }
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString(separator = "\n", prefix = "\n")
                    ?: "")

    sendSplitMultiLineMessage(fullMessage)
}

private class DisarmState {
    private val _isDisarmed = AtomicBoolean(false)

    fun isDisarmed(): Boolean = _isDisarmed.get()

    /**
     * Disarms this connection. Returns true if this changed the diarmed state from false to true, and false
     * otherwise.
     */
    // compareAndExchange returns the old value; if that old value is false, then we changed the value, otherwise
    // it is true and nothing changed.
    fun disarm(): Boolean = !_isDisarmed.compareAndExchange(false, true)
}

private fun formatIrcNameForDiscord(name: String): String {
    return "**$name**"
}

private fun ircCommandParser(connection: IrcRelayEntry): CommandParser? {
    val prefix = connection.ircCommandPrefix ?: return null
    return GlobalPrefixCommandParser(prefix)
}

private fun connectIrcAndDiscordChannels(
    ircClient: IrcClient,
    jda: JDA,
    connection: IrcRelayEntry,
    commandRegistry: CommandRegistry,
) {
    val discordChannelId = connection.discordChannelId
    val ircChannelName = connection.ircChannelName

    // Don't hold on to the channel because JDA doesn't allow keeping objects for indefinite time periods.
    if (jda.getTextChannelById(connection.discordChannelId) == null) {
        logger.error(
            "Could not find Discord channel ${connection.discordChannelId} " +
                    "when connecting to IRC channel ${connection.ircChannelName}!"
        )
        return
    }

    ircClient.addChannel(connection.ircChannelName)
    ircClient.eventManager.registerEventListener(IrcListener(object : IrcMessageHandler {
        private val commandParser = ircCommandParser(connection)

        private val disarmState = DisarmState()
        private fun isDisarmed() = disarmState.isDisarmed()

        private fun <E> E.isInRelevantChannel() where E : ActorEvent<IrcUser>, E : ChannelEvent =
            channel.name == ircChannelName

        private fun tryDiscordChannel() = jda.getTextChannelById(discordChannelId)

        private fun requireDiscordChannel() = tryDiscordChannel() ?: null.also {
            if (disarmState.disarm()) {
                logger.error(
                    "Discord channel $discordChannelId could not be found in order to send a message! " +
                            "Disarming this connection."
                )
            }
        }

        private fun relayToDiscord(text: String) {
            val channel = requireDiscordChannel() ?: return

            (MessageBuilder(text).buildAll(MessageBuilder.SplitPolicy.NEWLINE)).forEach {
                channel.sendMessage(it)
                    .disallowMentions()
                    .queue()
            }
        }

        override fun onMessage(event: ChannelMessageEvent) {
            if (isDisarmed()) return
            if (!event.isInRelevantChannel()) return
            relayToDiscord(formatIrcNameForDiscord(event.actor.nick) + " says: " + event.message)

            if (commandParser != null) {
                val source = CommandEventSource.Irc(event)

                @Suppress("UNUSED_VARIABLE")
                val ensureExhaustive = when (val parseResult = commandParser.parse(source)) {
                    is CommandParseResult.Ignore -> {
                    }

                    is CommandParseResult.Invocation -> {
                        commandRegistry.invokeCommand(source, parseResult.invocation)
                    }
                }
            }
        }

        override fun onCtcpMessage(event: ChannelCtcpEvent) {
            if (isDisarmed()) return
            if (!event.isInRelevantChannel()) return

            val message = event.message
            if (!message.startsWith("ACTION ")) return // ACTION means a /me command
            relayToDiscord(formatIrcNameForDiscord(event.actor.nick) + " " + message.removePrefix("ACTION "))
        }

        private fun handleAnyLeaveEvent(event: ActorEvent<IrcUser>) {
            relayToDiscord("${formatIrcNameForDiscord(event.actor.nick)} left IRC.")
        }

        override fun onJoin(event: ChannelJoinEvent) {
            if (isDisarmed()) return
            if (!event.isInRelevantChannel()) return
            if (!connection.relayJoinLeaveMessages) return
            relayToDiscord("${formatIrcNameForDiscord(event.actor.nick)} joined IRC.")
        }

        override fun onPart(event: ChannelPartEvent) {
            if (isDisarmed()) return
            if (!event.isInRelevantChannel()) return
            if (!connection.relayJoinLeaveMessages) return
            handleAnyLeaveEvent(event)
        }

        override fun onUnexpectedPart(event: UnexpectedChannelLeaveViaPartEvent) {
            if (isDisarmed()) return
            if (!event.isInRelevantChannel()) return
            if (!connection.relayJoinLeaveMessages) return
            handleAnyLeaveEvent(event)
        }

        override fun onQuit(event: UserQuitEvent) {
            if (isDisarmed()) return
            if (!event.user.channels.contains(ircChannelName)) return
            if (!connection.relayJoinLeaveMessages) return
            handleAnyLeaveEvent(event)
        }
    }))

    val startTime = TimeSource.Monotonic.markNow()

    // The IRC client takes some time to connect, so we won't disarm the IRC side until one minute has passed since it
    // *should* have connected. 1 minute should (hopefully) be plenty of time.
    val ircGraceEnd = startTime + Duration.minutes(1)

    jda.addEventListener(object {
        private val disarmState = DisarmState()
        private fun isDisarmed() = disarmState.isDisarmed()

        @SubscribeEvent
        fun onMessage(event: GuildMessageReceivedEvent) {
            if (isDisarmed()) return
            if (event.channel.id != discordChannelId) return
            if (event.author.id == event.jda.selfUser.id) return

            val optChannel = ircClient.getChannel(ircChannelName)

            optChannel.ifPresentOrElse({ channel ->
                val message = event.message

                channel.sendDiscordMessage(message)
            }, {
                if (ircGraceEnd.hasPassedNow()) {
                    logger.error(
                        "IRC channel $ircChannelName could not be found in order to send a message! " +
                                "Disarming this connection."
                    )

                    disarmState.disarm()
                } else {
                    logger.warn(
                        "IRC channel $ircChannelName could not be found in order to send a message! " +
                                "Not disarming because of grace period."
                    )
                }
            })
        }
    })
}

fun initializeIrcRelay(
    ircClient: IrcClient,
    ircRelayConfig: IrcRelayConfig,
    jda: JDA,
    commandRegistry: CommandRegistry,
) {
    val ircConnections = ircRelayConfig.entries

    for (ircConnection in ircConnections) {
        logger.info(
            "Connecting IRC channel ${ircConnection.ircChannelName} " +
                    "to Discord channel ${ircConnection.discordChannelId}."
        )

        connectIrcAndDiscordChannels(
            ircClient = ircClient,
            jda = jda,
            connection = ircConnection,
            commandRegistry = commandRegistry,
        )
    }
}
