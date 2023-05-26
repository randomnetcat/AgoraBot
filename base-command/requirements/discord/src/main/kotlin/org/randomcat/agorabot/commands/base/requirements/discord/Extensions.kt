package org.randomcat.agorabot.commands.base.requirements.discord

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import org.randomcat.agorabot.commands.base.BaseCommandExecutionReceiverRequiring
import org.randomcat.agorabot.util.DiscordPermission


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
