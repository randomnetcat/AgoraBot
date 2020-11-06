package org.randomcat.agorabot.commands

import net.dv8tion.jda.api.entities.Role
import org.randomcat.agorabot.commands.impl.*
import org.randomcat.agorabot.commands.impl.BaseCommand.ExecutionReceiverImpl.GuildInfo
import org.randomcat.agorabot.config.get
import org.randomcat.agorabot.config.update
import org.randomcat.agorabot.permissions.GuildScope
import org.randomcat.agorabot.util.DiscordPermission

private const val SELF_ASSIGNABLE_STATE_KEY = "self_assign.assignable"
private typealias SelfAssignableStateType = List<String>

private val MANAGE_SELFASSIGN_PERMISSION = GuildScope.command("selfassign").action("manage")

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
                return@withGuildCheck
            }

            block(guildInfo, role)
        }
    }

    private inline fun ExecutionReceiverImpl.withInteractableRoleResolved(
        roleName: String,
        block: (GuildInfo, Role) -> Unit,
    ) {
        withRoleResolved(roleName) { guildInfo, role ->
            if (role.isPublicRole) {
                respond("Refusing to handle public role.")
                return@withRoleResolved
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

    private inline fun ExecutionReceiverImpl.withInteractableSelfAssignableRoleResolved(
        roleName: String,
        block: (GuildInfo, Role) -> Unit,
    ) {
        withInteractableRoleResolved(roleName) { guildInfo, role ->
            val assignableRoleIds = guildInfo.assignableRoleIds()

            if (!assignableRoleIds.contains(role.id)) {
                respond("That role is not self-assignable.")
                return@withInteractableRoleResolved
            }

            block(guildInfo, role)
        }
    }

    private fun ExecutionReceiverImpl.handleListRequest() {
        withGuildCheck { guildInfo ->
            val assignableRoles = guildInfo.assignableRoles()

            if (assignableRoles.isEmpty()) {
                respond("No roles are self-assignable.")
            } else {
                val rolesPart = "The following roles can be self-assigned: " +
                        assignableRoles
                            .sortedByDescending { it.position }.map { it.name }
                            .joinToString(separator = ", ", postfix = ".")

                val actionPart =
                    if (senderHasPermission(MANAGE_SELFASSIGN_PERMISSION))
                        "The valid options for the \"action\" parameter are " +
                                "list, assign, remove, enable, and disable."
                    else
                        "The valid options for the \"action\" parameter are list, assign, and remove."

                respond("$rolesPart\n\n$actionPart")
            }
        }
    }

    private fun ExecutionReceiverImpl.handleAssignRequest(roleName: String) {
        withInteractableSelfAssignableRoleResolved(roleName) { guildInfo, role ->
            guildInfo.guild.addRoleToMember(guildInfo.senderMember, role).queue {
                respond("Done.")
            }
        }
    }

    private fun ExecutionReceiverImpl.handleRemoveRequest(roleName: String) {
        withInteractableSelfAssignableRoleResolved(roleName) { guildInfo, role ->
            guildInfo.guild.removeRoleFromMember(guildInfo.senderMember, role).queue {
                respond("Done.")
            }
        }
    }

    private fun ExecutionReceiverImpl.handleEnableRequest(roleName: String) {
        withRoleResolved(roleName) { guildInfo, role ->
            if (role.isPublicRole) {
                respond("Refusing to make everyone role self-assignable.")
                return@withRoleResolved
            }

            guildInfo.guildState.update<SelfAssignableStateType>(SELF_ASSIGNABLE_STATE_KEY) { old ->
                old?.let { old + role.id } ?: listOf(role.id)
            }

            respond("Done.")
        }
    }

    private fun ExecutionReceiverImpl.handleDisableRequest(roleName: String) {
        withRoleResolved(roleName) { guildInfo, role ->
            guildInfo.guildState.update<SelfAssignableStateType>(SELF_ASSIGNABLE_STATE_KEY) { old ->
                // Use list subtraction in order to remove all instances of it (if it's duplicated).
                old?.let { old - listOf(role.id) } ?: listOf(role.id)
            }

            respond("Done.")
        }
    }

    override fun BaseCommandImplReceiver.impl() {
        // Unfortunately this has to be a matchFirst instead of subcommands to handle the no arguments case and the
        // role name without an "assign" subcommand case.
        matchFirst {
            noArgs {
                handleListRequest()
            }

            args(StringArg("argument")) { (arg) ->
                when (arg.toLowerCase()) {
                    "list" -> handleListRequest()
                    else -> handleAssignRequest(arg)
                }
            }

            args(StringArg("action"), StringArg("role_name")) { (action, roleName) ->
                when (action.toLowerCase()) {
                    "assign" -> handleAssignRequest(roleName)
                    "remove" -> handleRemoveRequest(roleName)
                    "enable" -> handleEnableRequest(roleName)
                    "disable" -> handleDisableRequest(roleName)
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
