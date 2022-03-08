package org.randomcat.agorabot.config

import org.randomcat.agorabot.CommandOutputMapping
import org.randomcat.agorabot.FeatureContext
import org.randomcat.agorabot.FeatureElementTag
import org.randomcat.agorabot.listener.MutableGuildPrefixMap
import org.randomcat.agorabot.queryExpectOne

object PrefixStorageTag : FeatureElementTag<MutableGuildPrefixMap>

val FeatureContext.prefixMap
    get() = queryExpectOne(PrefixStorageTag)

object VersioningStorageTag : FeatureElementTag<VersioningStorage>

val FeatureContext.versioningStorage
    get() = queryExpectOne(VersioningStorageTag)

object CommandOutputMappingTag : FeatureElementTag<CommandOutputMapping>

val FeatureContext.commandOutputMapping
    get() = queryExpectOne(CommandOutputMappingTag)
