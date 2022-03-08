package org.randomcat.agorabot.guild_state.feature

import org.randomcat.agorabot.FeatureContext
import org.randomcat.agorabot.FeatureElementTag
import org.randomcat.agorabot.guild_state.GuildStateMap
import org.randomcat.agorabot.queryExpectOne

object GuildStateStorageTag : FeatureElementTag<GuildStateMap>

val FeatureContext.guildStateMap
    get() = queryExpectOne(GuildStateStorageTag)
