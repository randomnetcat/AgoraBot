package org.randomcat.agorabot.commands

import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.entities.emoji.EmojiUnion
import org.randomcat.agorabot.commands.base.*
import org.randomcat.agorabot.commands.base.requirements.discord.BaseCommandExecutionReceiverGuilded
import org.randomcat.agorabot.commands.base.requirements.discord.GuildInfo
import org.randomcat.agorabot.commands.base.requirements.discord.currentGuildInfo
import org.randomcat.agorabot.commands.base.requirements.discord_ext.InGuild
import org.randomcat.agorabot.commands.base.requirements.permissions.permissions
import org.randomcat.agorabot.permissions.GuildScope
import org.randomcat.agorabot.reactionroles.MutableReactionRolesMap
import org.randomcat.agorabot.reactionroles.storageName
import org.randomcat.agorabot.util.await
import org.randomcat.agorabot.util.resolveTextChannelString

private val REACTION_ROLES_MANAGE_PERMISSION = GuildScope.command("reactionroles").action("manage")

class ReactionRolesCommand(
    strategy: BaseCommandStrategy,
    private val map: MutableReactionRolesMap,
) : BaseCommand(strategy) {
    private suspend inline fun BaseCommandExecutionReceiverGuilded.withEmoteResolved(
        emoteString: String,
        crossinline block: suspend (GuildInfo, emote: EmojiUnion) -> Unit,
    ) {
        val guildInfo = currentGuildInfo
        val emote = runCatching { Emoji.fromFormatted(emoteString) }.getOrNull()

        if (emote != null) {
            block(guildInfo, emote)
        } else {
            respond("Invalid emote.")
        }
    }

    private suspend inline fun BaseCommandExecutionReceiverGuilded.withRoleAndEmoteResolved(
        emoteString: String,
        roleString: String,
        crossinline block: suspend (GuildInfo, Role, emote: EmojiUnion) -> Unit,
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
