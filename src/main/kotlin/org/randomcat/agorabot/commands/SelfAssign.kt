package org.randomcat.agorabot.commands

import net.dv8tion.jda.api.entities.Role
import org.randomcat.agorabot.commands.base.*
import org.randomcat.agorabot.commands.base.requirements.discord.*
import org.randomcat.agorabot.commands.base.requirements.discord_ext.ExtendedGuildRequirement
import org.randomcat.agorabot.commands.base.requirements.discord_ext.InGuild
import org.randomcat.agorabot.commands.base.requirements.discord_ext.currentGuildState
import org.randomcat.agorabot.commands.base.requirements.permissions.permissions
import org.randomcat.agorabot.guild_state.get
import org.randomcat.agorabot.guild_state.update
import org.randomcat.agorabot.permissions.GuildScope
import org.randomcat.agorabot.util.DiscordPermission
import org.randomcat.agorabot.util.await

private const val SELF_ASSIGNABLE_STATE_KEY = "self_assign.assignable"
private typealias SelfAssignableStateType = List<String>

private val MANAGE_SELFASSIGN_PERMISSION = GuildScope.command("selfassign").action("manage")

fun <Arg> PendingInvocation<ContextReceiverArg<BaseCommandContext, BaseCommandExecutionReceiver, Arg>>.selfAssignAction() =
    requires(InGuild)

fun <Arg> PendingInvocation<ContextReceiverArg<BaseCommandContext, BaseCommandExecutionReceiver, Arg>>.selfAssignAction(
    block: suspend BaseCommandExecutionReceiverRequiring<ExtendedGuildRequirement>.(Arg) -> Unit,
) = selfAssignAction().execute { block(it.receiver, it.arg) }

fun <Arg> PendingInvocation<ContextReceiverArg<BaseCommandContext, BaseCommandExecutionReceiver, Arg>>.selfAssignAdminAction() =
    selfAssignAction().permissions(MANAGE_SELFASSIGN_PERMISSION)

fun <Arg> PendingInvocation<ContextReceiverArg<BaseCommandContext, BaseCommandExecutionReceiver, Arg>>.selfAssignAdminAction(
    block: suspend BaseCommandExecutionReceiverRequiring<ExtendedGuildRequirement>.(Arg) -> Unit,
) = selfAssignAdminAction().execute { block(it.receiver, it.arg) }

class SelfAssignCommand(strategy: BaseCommandStrategy) : BaseCommand(strategy) {
    private suspend inline fun BaseCommandExecutionReceiverGuilded.withRoleResolved(
        roleName: String,
        crossinline block: suspend (Role) -> Unit,
    ) {
        val role = currentGuildInfo.resolveRole(roleName) ?: run {
            respond("Could not find a role by that name.")
            return
        }

        block(role)
    }

    private suspend inline fun BaseCommandExecutionReceiverRequiring<ExtendedGuildRequirement>.withInteractableSelfAssignableRoleResolved(
        roleName: String,
        crossinline block: suspend (Role) -> Unit,
    ) {
        withRoleResolved(roleName) { role ->
            val assignableRoleIds = assignableRoleIds()

            if (!assignableRoleIds.contains(role.id)) {
                respond("That role is not self-assignable.")
                return@withRoleResolved
            }

            if (role.isPublicRole) {
                respond("Refusing to handle public role.")
                return@withRoleResolved
            }

            if (!botHasPermission(DiscordPermission.MANAGE_ROLES)) {
                respond("Cannot handle roles without MANAGE_ROLES permission! Contact a Guild admin.")
                return@withRoleResolved
            }

            if (!currentGuild.selfMember.canInteract(role)) {
                respond("Cannot interact with that role! Contact a Guild admin.")
                return@withRoleResolved
            }

            block(role)
        }
    }

    private suspend fun BaseCommandExecutionReceiverRequiring<ExtendedGuildRequirement>.handleListRequest() {
        val assignableRoles = assignableRoles()

        if (assignableRoles.isEmpty()) {
            respond("No roles are self-assignable.")
        } else {
            val rolesMessage = "The following roles can be self-assigned: " +
                    assignableRoles
                        .sortedByDescending { it.position }
                        .joinToString(separator = ", ", postfix = ".") { it.name }

            respond(rolesMessage)
        }
    }

    private suspend fun BaseCommandExecutionReceiverRequiring<ExtendedGuildRequirement>.handleAssignRequest(roleName: String) {
        withInteractableSelfAssignableRoleResolved(roleName) { role ->
            currentGuild.addRoleToMember(senderMember, role).await()
            respond("Done.")
        }
    }

    private suspend fun BaseCommandExecutionReceiverRequiring<ExtendedGuildRequirement>.handleRemoveRequest(roleName: String) {
        withInteractableSelfAssignableRoleResolved(roleName) { role ->
            currentGuild.removeRoleFromMember(senderMember, role).await()
            respond("Done.")
        }
    }

    private suspend fun BaseCommandExecutionReceiverRequiring<ExtendedGuildRequirement>.handleEnableRequest(roleName: String) {
        withRoleResolved(roleName) { role ->
            if (role.isPublicRole) {
                respond("Refusing to make everyone role self-assignable.")
                return@withRoleResolved
            }

            currentGuildState.update<SelfAssignableStateType>(SELF_ASSIGNABLE_STATE_KEY) { old ->
                old?.let { old + role.id } ?: listOf(role.id)
            }

            respond("Done.")
        }
    }

    private suspend fun BaseCommandExecutionReceiverRequiring<ExtendedGuildRequirement>.handleDisableRequest(roleName: String) {
        withRoleResolved(roleName) { role ->
            currentGuildState.update<SelfAssignableStateType>(SELF_ASSIGNABLE_STATE_KEY) { old ->
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
            noArgs().selfAssignAction() {
                handleListRequest()
            }

            args(LiteralArg("list")).selfAssignAction() { (_) ->
                handleListRequest()
            }

            args(StringArg("role_name")).selfAssignAction() { (roleName) ->
                handleAssignRequest(roleName)
            }

            args(LiteralArg("assign"), StringArg("role_name")).selfAssignAction() { (_, roleName) ->
                handleAssignRequest(roleName)
            }

            args(LiteralArg("remove"), StringArg("role_name")).selfAssignAction() { (_, roleName) ->
                handleRemoveRequest(roleName)
            }

            args(LiteralArg("enable"), StringArg("role_name")).selfAssignAdminAction() { (_, roleName) ->
                handleEnableRequest(roleName)
            }

            args(LiteralArg("disable"), StringArg("role_name")).selfAssignAdminAction() { (_, roleName) ->
                handleDisableRequest(roleName)
            }
        }
    }

    private fun BaseCommandExecutionReceiverRequiring<ExtendedGuildRequirement>.assignableRoleIds(): List<String> {
        return currentGuildState.get<SelfAssignableStateType>(SELF_ASSIGNABLE_STATE_KEY) ?: emptyList()
    }

    private fun BaseCommandExecutionReceiverRequiring<ExtendedGuildRequirement>.assignableRoles(): List<Role> {
        return assignableRoleIds().mapNotNull { currentGuild.getRoleById(it) }
    }
}
