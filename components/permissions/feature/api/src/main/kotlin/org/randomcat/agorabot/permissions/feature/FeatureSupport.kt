package org.randomcat.agorabot.permissions.feature

import org.randomcat.agorabot.FeatureElementTag
import org.randomcat.agorabot.permissions.BotPermissionContext
import org.randomcat.agorabot.permissions.MutableGuildPermissionMap
import org.randomcat.agorabot.permissions.MutablePermissionMap

object BotPermissionMapTag : FeatureElementTag<MutablePermissionMap>
object GuildPermissionMapTag : FeatureElementTag<MutableGuildPermissionMap>
object BotPermissionContextTag : FeatureElementTag<BotPermissionContext>
