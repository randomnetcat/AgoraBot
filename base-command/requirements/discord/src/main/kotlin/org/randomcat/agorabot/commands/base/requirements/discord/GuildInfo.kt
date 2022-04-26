package org.randomcat.agorabot.commands.base.requirements.discord

import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.randomcat.agorabot.util.resolveRoleString

class GuildInfo(
    private val event: MessageReceivedEvent,
) {
    init {
        require(event.isFromGuild)
    }

    val guild by lazy { event.guild }
    val guildId by lazy { guild.id }
    val senderMember by lazy { event.member ?: error("Expected event to have a Member") }

    fun resolveRole(roleString: String): Role? = guild.resolveRoleString(roleString)
}
