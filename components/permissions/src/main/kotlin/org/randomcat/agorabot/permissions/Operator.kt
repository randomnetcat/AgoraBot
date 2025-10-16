package org.randomcat.agorabot.permissions

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

data class LogicalOrPermission(val components: ImmutableList<BotPermission>) : BotPermission {
    constructor(components: List<BotPermission>) : this(components.toImmutableList())

    override fun readable(): String {
        if (components.size == 1) return components.single().readable()

        return components.joinToString(" or ") {
            if (it is LogicalOrPermission) it.readable() else "(${it.readable()})"
        }
    }

    override fun isSatisfied(botContext: BotPermissionContext, userContext: UserPermissionContext): Boolean {
        return components.any { it.isSatisfied(botContext, userContext) }
    }
}
