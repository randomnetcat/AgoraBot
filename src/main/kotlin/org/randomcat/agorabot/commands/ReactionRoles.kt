package org.randomcat.agorabot.commands

import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.MessageReaction
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.requests.RestAction
import org.randomcat.agorabot.commands.impl.*
import org.randomcat.agorabot.commands.impl.BaseCommand.ExecutionReceiverImpl.GuildInfo
import org.randomcat.agorabot.permissions.GuildScope
import org.randomcat.agorabot.reactionroles.MutableReactionRolesMap
import org.randomcat.agorabot.reactionroles.storageName
import org.randomcat.agorabot.util.CompletedRestAction

private val REACTION_ROLES_MANAGE_PERMISSION = GuildScope.command("reactionroles").action("manage")

private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'

class ReactionRolesCommand(
    strategy: BaseCommandStrategy,
    private val map: MutableReactionRolesMap,
) : BaseCommand(strategy) {
    private fun Guild.retrieveEmoteStorageName(string: String): RestAction<String?> {
        val reactionEmoteAction = when {
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

        return reactionEmoteAction.mapToResult().map { (if (it.isSuccess) it.get() else null)?.storageName }
    }

    private inline fun ExecutionReceiverImpl.withEmoteResolved(
        emoteString: String,
        crossinline block: (GuildInfo, reactionStorageName: String) -> Unit,
    ) {
        requiresGuild { guildInfo ->
            guildInfo.guild.retrieveEmoteStorageName(emoteString).queue { reactionStorageName ->
                if (reactionStorageName != null) {
                    block(guildInfo, reactionStorageName)
                } else {
                    respond("Invalid emote.")
                }
            }
        }
    }

    private inline fun ExecutionReceiverImpl.withRoleAndEmoteResolved(
        emoteString: String,
        roleString: String,
        crossinline block: (GuildInfo, Role, reactionStorageName: String) -> Unit,
    ) {
        withEmoteResolved(emoteString = emoteString) { guildInfo, reactionStorageName ->
            val role = guildInfo.resolveRole(roleString) ?: run {
                respond("Unable to find a role by that name.")
                return@withEmoteResolved
            }

            block(guildInfo, role, reactionStorageName)
        }
    }

    override fun BaseCommandImplReceiver.impl() {
        subcommands {
            subcommand("add") {
                args(
                    StringArg("message"),
                    StringArg("emote"),
                    StringArg("role"),
                ).permissions(
                    REACTION_ROLES_MANAGE_PERMISSION,
                ) { (messageId, emoteString, roleString) ->
                    withRoleAndEmoteResolved(
                        emoteString = emoteString,
                        roleString = roleString,
                    ) { guildInfo, role, reactionStorageName ->
                        map.addRoleMapping(
                            guildId = guildInfo.guildId,
                            messageId = messageId,
                            reactionName = reactionStorageName,
                            roleId = role.id,
                        )

                        respond("Done.")
                    }
                }
            }

            subcommand("remove") {
                args(
                    StringArg("message"),
                    StringArg("emote"),
                ).permissions(
                    REACTION_ROLES_MANAGE_PERMISSION,
                ) { (messageId, emoteString) ->
                    withEmoteResolved(emoteString = emoteString) { guildInfo, reactionStorageName ->
                        map.removeRoleMapping(
                            guildId = guildInfo.guildId,
                            messageId = messageId,
                            reactionName = reactionStorageName,
                        )

                        respond("Done.")
                    }
                }
            }
        }
    }
}
