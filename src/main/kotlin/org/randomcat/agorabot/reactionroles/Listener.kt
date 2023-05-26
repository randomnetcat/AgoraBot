package org.randomcat.agorabot.reactionroles

import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.events.message.react.GenericMessageReactionEvent
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent
import net.dv8tion.jda.api.hooks.SubscribeEvent
import org.randomcat.agorabot.util.DiscordPermission

fun reactionRolesListener(reactionRolesMap: ReactionRolesMap): Any {
    return object {
        private inline fun withResolvedRoleFor(
            event: GenericMessageReactionEvent,
            crossinline block: (Role, Member) -> Unit,
        ) {
            val guild = event.guild
            val guildId = guild.id
            val messageId = event.messageId
            val emoteName = event.emoji.storageName

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
        operator fun invoke(event: MessageReactionAddEvent) {
            if (!event.isFromGuild) return
            if (event.user == event.jda.selfUser) return

            withResolvedRoleFor(event) { role, member ->
                member.guild.addRoleToMember(member, role).queue()
            }
        }

        @SubscribeEvent
        operator fun invoke(event: MessageReactionRemoveEvent) {
            if (!event.isFromGuild) return
            if (event.user == event.jda.selfUser) return

            withResolvedRoleFor(event) { role, member ->
                member.guild.removeRoleFromMember(member, role).queue()
            }
        }
    }
}
