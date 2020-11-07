package org.randomcat.agorabot.reactionroles

import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.events.message.guild.react.GenericGuildMessageReactionEvent
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionAddEvent
import net.dv8tion.jda.api.events.message.guild.react.GuildMessageReactionRemoveEvent
import net.dv8tion.jda.api.hooks.SubscribeEvent
import org.randomcat.agorabot.util.DiscordPermission

fun reactionRolesListener(reactionRolesMap: ReactionRolesMap): Any {
    return object {
        private inline fun withResolvedRoleFor(
            event: GenericGuildMessageReactionEvent,
            crossinline block: (Role, Member) -> Unit,
        ) {
            val guild = event.guild
            val guildId = guild.id
            val messageId = event.messageId
            val emoteName = event.reactionEmote.storageName

            val roleId = reactionRolesMap.roleIdFor(
                guildId = guildId,
                messageId = messageId,
                reactionName = emoteName,
            ) ?: return

            val role = guild.getRoleById(roleId) ?: return

            if (guild.selfMember.hasPermission(DiscordPermission.MANAGE_ROLES)) {
                event.retrieveMember().queue({ member ->
                    block(role, member)
                }, {
                    // if the member no longer exists, just give up
                })
            }
        }

        @SubscribeEvent
        operator fun invoke(event: GuildMessageReactionAddEvent) {
            withResolvedRoleFor(event) { role, member ->
                member.guild.addRoleToMember(member, role).queue()
            }
        }

        @SubscribeEvent
        operator fun invoke(event: GuildMessageReactionRemoveEvent) {
            withResolvedRoleFor(event) { role, member ->
                member.guild.removeRoleFromMember(member, role).queue()
            }
        }
    }
}
