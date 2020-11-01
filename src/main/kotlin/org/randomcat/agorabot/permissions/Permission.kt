package org.randomcat.agorabot.permissions

import kotlinx.collections.immutable.ImmutableList
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
    fun checkGlobalPath(userId: String, path: List<String>): BotPermissionState
    fun checkGuildPath(guildId: String, userId: String, path: List<String>): BotPermissionState
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

interface BotPermission {
    val scope: String
    val path: ImmutableList<String>

    fun isSatisfied(botContext: BotPermissionContext, userContext: UserPermissionContext): Boolean
}
