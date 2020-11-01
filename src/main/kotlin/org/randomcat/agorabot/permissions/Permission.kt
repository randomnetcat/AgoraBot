package org.randomcat.agorabot.permissions

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.User

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
    fun checkGuildPath(guildId: String, userId: String, path: PermissionPath): BotPermissionState
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
