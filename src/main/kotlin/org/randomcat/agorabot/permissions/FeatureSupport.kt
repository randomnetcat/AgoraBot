package org.randomcat.agorabot.permissions

import org.randomcat.agorabot.FeatureContext
import org.randomcat.agorabot.FeatureElementTag
import org.randomcat.agorabot.queryExpectOne

object BotPermissionMapTag : FeatureElementTag<MutablePermissionMap>

val FeatureContext.botPermissionMap
    get() = queryExpectOne(BotPermissionMapTag)

object GuildPermissionMapTag : FeatureElementTag<MutableGuildPermissionMap>

val FeatureContext.guildPermissionMap
    get() = queryExpectOne(GuildPermissionMapTag)
