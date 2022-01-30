package org.randomcat.agorabot.commands.impl

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.randomcat.agorabot.listener.CommandEventSource
import org.randomcat.agorabot.listener.CommandInvocation
import org.randomcat.agorabot.listener.tryRespondWithText
import org.randomcat.agorabot.util.DiscordPermission
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

interface BaseCommandDiscordRequirement {
    val currentMessageEvent: MessageReceivedEvent
}

interface BaseCommandGuildRequirement : BaseCommandDiscordRequirement

typealias BaseCommandExecutionReceiverDiscord = BaseCommandExecutionReceiverRequiring<BaseCommandDiscordRequirement>
typealias BaseCommandExecutionReceiverGuilded = BaseCommandExecutionReceiverRequiring<BaseCommandGuildRequirement>

val BaseCommandExecutionReceiverDiscord.currentMessageEvent
    get() = requirement().currentMessageEvent

val BaseCommandExecutionReceiverDiscord.currentGuildInfo
    get() = if (currentMessageEvent.isFromGuild) GuildInfo(currentMessageEvent) else null

val BaseCommandExecutionReceiverGuilded.currentGuildInfo
    @JvmName("currentGuildInfoGuaranteed")
    get() = GuildInfo(currentMessageEvent)

val BaseCommandExecutionReceiverDiscord.currentJda: JDA
    get() = currentMessageEvent.jda

val BaseCommandExecutionReceiverDiscord.currentChannel: MessageChannel
    get() = currentMessageEvent.channel

val BaseCommandExecutionReceiverDiscord.currentChannelId: String
    get() = currentChannel.id

val BaseCommandExecutionReceiverDiscord.currentGuild: Guild?
    get() = currentGuildInfo?.guild

val BaseCommandExecutionReceiverGuilded.currentGuild: Guild
    @JvmName("currentGuildGuaranteed")
    get() = currentGuildInfo.guild

val BaseCommandExecutionReceiverDiscord.currentGuildId: String?
    get() = currentGuildInfo?.guildId

val BaseCommandExecutionReceiverGuilded.currentGuildId: String
    @JvmName("currentGuildIdGuaranteed")
    get() = currentGuildInfo.guildId

val BaseCommandExecutionReceiverGuilded.senderMember
    get() = currentGuildInfo.senderMember

fun BaseCommandExecutionReceiverDiscord.botHasPermission(permission: DiscordPermission): Boolean {
    return currentGuild?.selfMember?.hasPermission(permission) ?: false
}


object BaseCommandDefaultArgumentStrategy : BaseCommandArgumentStrategy {
    override fun sendArgumentErrorResponse(
        source: CommandEventSource,
        invocation: CommandInvocation,
        errorMessage: String,
        usage: String,
    ) {
        source.tryRespondWithText("$errorMessage. Usage: ${usage.ifBlank { NO_ARGUMENTS }}")
    }
}
