package org.randomcat.agorabot.permissions.feature

import org.randomcat.agorabot.FeatureContext
import org.randomcat.agorabot.FeatureElementTag
import org.randomcat.agorabot.permissions.BotPermissionContext
import org.randomcat.agorabot.permissions.MutableGuildPermissionMap
import org.randomcat.agorabot.permissions.MutablePermissionMap
import org.randomcat.agorabot.queryExpectOne

object BotPermissionMapTag : FeatureElementTag<MutablePermissionMap>

val FeatureContext.botPermissionMap
    get() = queryExpectOne(BotPermissionMapTag)

object GuildPermissionMapTag : FeatureElementTag<MutableGuildPermissionMap>

val FeatureContext.guildPermissionMap
    get() = queryExpectOne(GuildPermissionMapTag)

object BotPermissionContextTag : FeatureElementTag<BotPermissionContext>

val FeatureContext.botPermissionContext
    get() = queryExpectOne(BotPermissionContextTag)
