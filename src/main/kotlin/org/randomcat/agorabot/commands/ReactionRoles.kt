package org.randomcat.agorabot.commands

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageReaction
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.requests.RestAction
import org.randomcat.agorabot.commands.base.*
import org.randomcat.agorabot.commands.base.requirements.discord.BaseCommandExecutionReceiverGuilded
import org.randomcat.agorabot.commands.base.requirements.discord.GuildInfo
import org.randomcat.agorabot.commands.base.requirements.discord.currentGuildInfo
import org.randomcat.agorabot.commands.base.requirements.discord_ext.InGuild
import org.randomcat.agorabot.commands.base.requirements.permissions.permissions
import org.randomcat.agorabot.permissions.GuildScope
import org.randomcat.agorabot.reactionroles.MutableReactionRolesMap
import org.randomcat.agorabot.reactionroles.storageName
import org.randomcat.agorabot.util.CompletedRestAction
import org.randomcat.agorabot.util.await
import org.randomcat.agorabot.util.resolveTextChannelString

private val REACTION_ROLES_MANAGE_PERMISSION = GuildScope.command("reactionroles").action("manage")

private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'

private fun Guild.retrieveEmoteByString(string: String): RestAction<MessageReaction.ReactionEmote?> {
    return when {
        string.startsWith("<a:") && string.endsWith(">") -> {
            retrieveEmoteById(string.removeSurrounding("<a:", ">").split(":").last())
                .map { MessageReaction.ReactionEmote.fromCustom(it) }
        }

        string.startsWith("<:") && string.endsWith(">") -> {
            retrieveEmoteById(string.removeSurrounding("<:", ">").split(":").last())
                .map { MessageReaction.ReactionEmote.fromCustom(it) }
        }

        string.all { it.isAsciiDigit() } -> {
            retrieveEmoteById(string).map { MessageReaction.ReactionEmote.fromCustom(it) }
        }

        else -> {
            CompletedRestAction.ofSuccess(jda, MessageReaction.ReactionEmote.fromUnicode(string, jda))
        }
    }
}

private fun Message.addReaction(emote: MessageReaction.ReactionEmote): RestAction<Unit> {
    return when {
        emote.isEmote -> addReaction(emote.emote).map { Unit }
        emote.isEmoji -> addReaction(emote.emoji).map { Unit }
        else -> error("ReactionEmote should be either emote or emoji")
    }
}

class ReactionRolesCommand(
    strategy: BaseCommandStrategy,
    private val map: MutableReactionRolesMap,
) : BaseCommand(strategy) {
    private suspend inline fun BaseCommandExecutionReceiverGuilded.withEmoteResolved(
        emoteString: String,
        crossinline block: suspend (GuildInfo, emote: MessageReaction.ReactionEmote) -> Unit,
    ) {
        val guildInfo = currentGuildInfo

        val reaction =
            guildInfo
                .guild
                .retrieveEmoteByString(emoteString)
                .mapToResult()
                .await()
                .let {
                    if (it.isSuccess) it.get() else null
                }

        if (reaction != null) {
            block(guildInfo, reaction)
        } else {
            respond("Invalid emote.")
        }
    }

    private suspend inline fun BaseCommandExecutionReceiverGuilded.withRoleAndEmoteResolved(
        emoteString: String,
        roleString: String,
        crossinline block: suspend (GuildInfo, Role, emote: MessageReaction.ReactionEmote) -> Unit,
    ) {
        withEmoteResolved(emoteString = emoteString) { guildInfo, emote ->
            val role = guildInfo.resolveRole(roleString) ?: run {
                respond("Unable to find a role by that name.")
                return@withEmoteResolved
            }

            block(guildInfo, role, emote)
        }
    }

    override fun BaseCommandImplReceiver.impl() {
        subcommands {
            subcommand("add") {
                args(
                    StringArg("channel"),
                    StringArg("message"),
                    StringArg("emote"),
                    StringArg("role"),
                ).requires(
                    InGuild
                ).permissions(
                    REACTION_ROLES_MANAGE_PERMISSION,
                ) { (channelString, messageId, emoteString, roleString) ->
                    withRoleAndEmoteResolved(
                        emoteString = emoteString,
                        roleString = roleString,
                    ) { guildInfo, role, emote ->
                        val channel = guildInfo.guild.resolveTextChannelString(channelString) ?: run {
                            respond("Could not find text channel $channelString")
                            return@withRoleAndEmoteResolved
                        }

                        val message = runCatching { channel.retrieveMessageById(messageId).await() }.getOrNull()

                        if (message == null) {
                            respond("Could not find message $messageId in channel $channelString")
                            return@withRoleAndEmoteResolved
                        }

                        map.addRoleMapping(
                            guildId = guildInfo.guildId,
                            messageId = messageId,
                            reactionName = emote.storageName,
                            roleId = role.id,
                        )

                        respond("Done.")

                        try {
                            message.addReaction(emote).await()
                        } catch (e: Exception) {
                            respond("Could not add reaction to message (but the mapping was still added).")
                            return@withRoleAndEmoteResolved
                        }
                    }
                }
            }

            subcommand("remove") {
                args(
                    StringArg("message"),
                    StringArg("emote"),
                ).requires(
                    InGuild
                ).permissions(
                    REACTION_ROLES_MANAGE_PERMISSION,
                ) { (messageId, emoteString) ->
                    withEmoteResolved(emoteString = emoteString) { guildInfo, emote ->
                        map.removeRoleMapping(
                            guildId = guildInfo.guildId,
                            messageId = messageId,
                            reactionName = emote.storageName,
                        )

                        respond("Done.")
                    }
                }
            }
        }
    }
}
