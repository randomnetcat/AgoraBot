package org.randomcat.agorabot.permissions

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.User
import org.randomcat.agorabot.permissions.PermissionMap.Companion.idForRole
import org.randomcat.agorabot.permissions.PermissionMap.Companion.idForUser

enum class BotPermissionState {
    ALLOW,
    DENY,
    DEFER,
    ;

    companion object {
        fun fromBoolean(isSatisfied: Boolean) = if (isSatisfied) ALLOW else DENY
    }
}

inline fun BotPermissionState.mapDeferred(block: () -> BotPermissionState): BotPermissionState {
    return if (this == BotPermissionState.DEFER) block() else this
}

fun BotPermissionState.isAllowed(): Boolean = this == BotPermissionState.ALLOW

interface BotPermissionContext {
    fun isBotAdmin(userId: String): Boolean
    fun checkGlobalPath(userId: String, path: PermissionPath): BotPermissionState
    fun checkUserGuildPath(guildId: String, userId: String, path: PermissionPath): BotPermissionState
    fun checkRoleGuildPath(guildId: String, roleId: String, path: PermissionPath): BotPermissionState
}

sealed class UserPermissionContext {
    abstract val user: User

    data class Guildless(override val user: User) : UserPermissionContext()

    data class InGuild(val member: Member) : UserPermissionContext() {
        override val user
            get() = member.user

        val guild
            get() = member.guild
    }
}

const val PERMISSION_PATH_SEPARATOR = "."

data class PermissionPath(
    val parts: ImmutableList<String>,
) {
    init {
        require(parts.none { it.contains(PERMISSION_PATH_SEPARATOR) })
    }

    constructor(parts: List<String>) : this(parts.toImmutableList())

    fun joinToString() = parts.joinToString(PERMISSION_PATH_SEPARATOR)

    companion object {
        fun fromSplitting(joined: String) = PermissionPath(joined.split(PERMISSION_PATH_SEPARATOR))
    }
}

data class PermissionScopedPath(
    val scope: String,
    val basePath: PermissionPath,
) {
    init {
        require(!scope.contains(PERMISSION_PATH_SEPARATOR))
    }

    val baseParts get() = basePath.parts
}

interface BotPermission {
    val path: PermissionScopedPath

    fun isSatisfied(botContext: BotPermissionContext, userContext: UserPermissionContext): Boolean
}

inline class PermissionMapId(val raw: String)

interface PermissionMap {
    companion object {
        fun idForUser(userId: String) = PermissionMapId("user.$userId")
        fun idForRole(roleId: String) = PermissionMapId("role.$roleId")
    }

    fun stateForId(path: PermissionPath, id: PermissionMapId): BotPermissionState?
}

fun PermissionMap.Companion.idForUser(user: User) = idForUser(userId = user.id)
fun PermissionMap.stateForUser(path: PermissionPath, userId: String) = stateForId(path, idForUser(userId = userId))
fun PermissionMap.stateForUser(path: PermissionPath, user: User) = stateForUser(path, userId = user.id)

fun PermissionMap.Companion.idForRole(role: Role) = idForRole(roleId = role.id)
fun PermissionMap.stateForRole(path: PermissionPath, roleId: String) = stateForId(path, idForRole(roleId = roleId))
fun PermissionMap.stateForRole(path: PermissionPath, role: Role) = stateForRole(path, roleId = role.id)

interface MutablePermissionMap : PermissionMap {
    fun setStateForId(path: PermissionPath, id: PermissionMapId, newState: BotPermissionState)
}

fun MutablePermissionMap.setStateForUser(path: PermissionPath, userId: String, newState: BotPermissionState) =
    setStateForId(path, idForUser(userId = userId), newState)

fun MutablePermissionMap.setStateForUser(path: PermissionPath, user: User, newState: BotPermissionState) =
    setStateForUser(path, userId = user.id, newState)

fun MutablePermissionMap.setStateForRole(path: PermissionPath, roleId: String, newState: BotPermissionState) =
    setStateForId(path, idForRole(roleId = roleId), newState)

fun MutablePermissionMap.setStateForRole(path: PermissionPath, role: Role, newState: BotPermissionState) =
    setStateForRole(path, roleId = role.id, newState)

interface GuildPermissionMap {
    fun mapForGuild(guildId: String): PermissionMap
}

interface MutableGuildPermissionMap : GuildPermissionMap {
    override fun mapForGuild(guildId: String): MutablePermissionMap
}
