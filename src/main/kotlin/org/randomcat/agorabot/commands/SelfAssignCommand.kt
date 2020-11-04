package org.randomcat.agorabot.commands

import net.dv8tion.jda.api.entities.Role
import org.randomcat.agorabot.commands.impl.*
import org.randomcat.agorabot.commands.impl.BaseCommand.ExecutionReceiverImpl.GuildInfo
import org.randomcat.agorabot.config.get
import org.randomcat.agorabot.config.update
import org.randomcat.agorabot.permissions.DiscordScope
import org.randomcat.agorabot.util.DiscordPermission

private const val SELF_ASSIGNABLE_STATE_KEY = "self_assign.assignable"
private typealias SelfAssignableStateType = List<String>

class SelfAssignCommand(strategy: BaseCommandStrategy) : BaseCommand(strategy) {
    private inline fun ExecutionReceiverImpl.withGuildCheck(block: (GuildInfo) -> Unit) {
        val guildInfo = currentGuildInfo() ?: run {
            respondNeedGuild()
            return
        }

        return block(guildInfo)
    }

    private inline fun ExecutionReceiverImpl.withRoleResolved(roleName: String, block: (GuildInfo, Role) -> Unit) {
        withGuildCheck { guildInfo ->
            val role = guildInfo.resolveRole(roleName) ?: run {
                respond("Could not find a role by that name.")
                return
            }

            return block(guildInfo, role)
        }
    }

    private inline fun ExecutionReceiverImpl.withInteractableRoleResolved(
        roleName: String,
        block: (GuildInfo, Role) -> Unit,
    ) {
        withRoleResolved(roleName) { guildInfo, role ->
            if (role.isPublicRole) {
                respond("Refusing to handle public role.")
                return
            }

            if (!botHasPermission(DiscordPermission.MANAGE_ROLES)) {
                respond("Cannot handle roles without MANAGE_ROLES permission! Contact a Guild admin.")
                return
            }

            if (!guildInfo.guild.selfMember.canInteract(role)) {
                respond("Cannot interact with that role! Contact a Guild admin.")
                return
            }

            block(guildInfo, role)
        }
    }

    override fun BaseCommandImplReceiver.impl() {
        subcommands {
            subcommand("list") {
                noArgs {
                    withGuildCheck { guildInfo ->
                        val assignableRoles = guildInfo.assignableRoles()

                        if (assignableRoles.isEmpty()) {
                            respond("No roles can be self-assigned.")
                        } else {
                            respond(
                                "The following roles can be self-assigned: " +
                                        assignableRoles
                                            .sortedByDescending { it.position }.map { it.name }
                                            .joinToString(", ")
                            )
                        }
                    }
                }
            }

            subcommand("assign") {
                args(StringArg("role_name")) { (roleName) ->
                    withInteractableRoleResolved(roleName) { guildInfo, role ->
                        if (guildInfo.assignableRoleIds().contains(role.id)) {
                            guildInfo.guild.addRoleToMember(guildInfo.senderMember, role).queue {
                                respond("Done.")
                            }
                        }
                    }
                }
            }

            subcommand("remove") {
                args(StringArg("role_name")) { (roleName) ->
                    withInteractableRoleResolved(roleName) { guildInfo, role ->
                        if (guildInfo.assignableRoleIds().contains(role.id)) {
                            guildInfo.guild.removeRoleFromMember(guildInfo.senderMember, role).queue {
                                respond("Done.")
                            }
                        }
                    }
                }
            }

            subcommand("enable") {
                args(StringArg("role_name")).permissions(DiscordScope.admin()) { (roleName) ->
                    withRoleResolved(roleName) { guildInfo, role ->
                        if (role.isPublicRole) {
                            respond("Refusing to make everyone role self-assignable.")
                            return@permissions
                        }

                        guildInfo.guildState.update<SelfAssignableStateType>(SELF_ASSIGNABLE_STATE_KEY) { old ->
                            old?.let { old + role.id } ?: listOf(role.id)
                        }

                        respond("Done.")
                    }
                }
            }

            subcommand("disable") {
                args(StringArg("role_name")).permissions(DiscordScope.admin()) { (roleName) ->
                    withRoleResolved(roleName) { guildInfo, role ->
                        guildInfo.guildState.update<SelfAssignableStateType>(SELF_ASSIGNABLE_STATE_KEY) { old ->
                            // Use list subtraction in order to remove all instances of it (if it's duplicated).
                            old?.let { old - listOf(role.id) } ?: listOf(role.id)
                        }

                        respond("Done.")
                    }
                }
            }
        }
    }

    private fun GuildInfo.assignableRoleIds(): List<String> {
        return guildState.get<SelfAssignableStateType>(SELF_ASSIGNABLE_STATE_KEY) ?: emptyList()
    }

    private fun GuildInfo.assignableRoles(): List<Role> {
        return assignableRoleIds().mapNotNull { guild.getRoleById(it) }
    }
}
