package org.randomcat.agorabot.config

import org.randomcat.agorabot.FeatureContext
import org.randomcat.agorabot.FeatureElementTag
import org.randomcat.agorabot.listener.MutableGuildPrefixMap
import org.randomcat.agorabot.queryExpectOne

object ConfigPersistServiceTag : FeatureElementTag<ConfigPersistService>

val FeatureContext.configPersistService
    get() = queryExpectOne(ConfigPersistServiceTag)

object PrefixStorageTag : FeatureElementTag<MutableGuildPrefixMap>

val FeatureContext.prefixMap
    get() = queryExpectOne(PrefixStorageTag)

object GuildStateStorageTag : FeatureElementTag<GuildStateMap>

val FeatureContext.guildStateMap
    get() = queryExpectOne(GuildStateStorageTag)

object VersioningStorageTag : FeatureElementTag<VersioningStorage>

val FeatureContext.versioningStorage
    get() = queryExpectOne(VersioningStorageTag)
