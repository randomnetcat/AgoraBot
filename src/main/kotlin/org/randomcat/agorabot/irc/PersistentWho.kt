package org.randomcat.agorabot.irc

import kotlinx.serialization.Serializable
import net.dv8tion.jda.api.JDA
import org.randomcat.agorabot.config.GuildState
import org.randomcat.agorabot.config.get
import org.randomcat.agorabot.config.set
import org.randomcat.agorabot.config.update
import org.randomcat.agorabot.util.DISCORD_MAX_MESSAGE_LENGTH
import org.randomcat.agorabot.util.ignoreErrors

data class IrcUserListConfig(
    val discordChannelId: String,
    val discordMessageId: String,
    val ircChannelName: String,
)

interface IrcUserListMessageMap {
    fun configsForGuild(guildId: String): List<IrcUserListConfig>
}

interface MutableIrcUserListMessageMap : IrcUserListMessageMap {
    fun addConfigForGuild(guildId: String, config: IrcUserListConfig)
    fun clearConfigsForGuild(guildId: String)
}

class GuildStateIrcUserListMessageMap(
    private val guildStateFun: (guildId: String) -> GuildState,
) : MutableIrcUserListMessageMap {
    companion object {
        private const val KEY_NAME = "irc_user_list_msgs"
    }

    private fun stateForGuild(guildId: String) = guildStateFun(guildId)

    @Serializable
    private data class IrcPersistentWhoConfigDto(
        val discordChannelId: String,
        val discordMessageId: String,
        val ircChannelName: String,
    ) {
        companion object {
            fun fromConfig(config: IrcUserListConfig) = IrcPersistentWhoConfigDto(
                discordChannelId = config.discordChannelId,
                discordMessageId = config.discordMessageId,
                ircChannelName = config.ircChannelName,
            )
        }

        fun toConfig() = IrcUserListConfig(
            discordChannelId = discordChannelId,
            discordMessageId = discordMessageId,
            ircChannelName = ircChannelName,
        )
    }

    override fun configsForGuild(guildId: String): List<IrcUserListConfig> {
        return stateForGuild(guildId)
            .get<List<IrcPersistentWhoConfigDto>>(KEY_NAME)
            ?.map { it.toConfig() }
            ?: emptyList()
    }

    override fun addConfigForGuild(guildId: String, config: IrcUserListConfig) {
        stateForGuild(guildId).update<List<IrcPersistentWhoConfigDto>>(KEY_NAME) {
            (it ?: emptyList()) + IrcPersistentWhoConfigDto.fromConfig(config)
        }
    }

    override fun clearConfigsForGuild(guildId: String) {
        stateForGuild(guildId).set<List<IrcPersistentWhoConfigDto>>(KEY_NAME, emptyList())
    }
}

fun updateIrcPersistentWho(jda: JDA, ircClient: IrcClient, userListMap: IrcUserListMessageMap) {
    for (guild in jda.guilds) {
        val configs = userListMap.configsForGuild(guild.id)

        for (config in configs) {
            ircClient.getChannel(config.ircChannelName).ifPresent { ircChannel ->
                val textPrefix = "**IRC USERS IN CHANNEL ${config.ircChannelName}**"

                val usersText =
                    ircChannel
                        .users
                        .map { it.nick }
                        .sorted()
                        .joinToString("\n")
                        .let {
                            "$textPrefix\n```\n$it\n```"
                        }
                        .let {
                            if (it.length > DISCORD_MAX_MESSAGE_LENGTH)
                                "$textPrefix\n\n<too long>"
                            else
                                it
                        }

                guild
                    .getTextChannelById(config.discordChannelId)
                    ?.retrieveMessageById(config.discordMessageId)
                    ?.queue(
                        { message ->
                            if (message.author == jda.selfUser && message.contentRaw != usersText) {
                                message.editMessage(usersText).ignoreErrors().queue()
                            }
                        },
                        { error ->
                            /* ignore */
                        },
                    )
            }
        }
    }
}
