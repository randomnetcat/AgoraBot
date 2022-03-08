package org.randomcat.agorabot.config

import org.randomcat.agorabot.*
import org.randomcat.agorabot.buttons.ButtonHandlerMap
import org.randomcat.agorabot.buttons.ButtonRequestDataMap
import org.randomcat.agorabot.listener.MutableGuildPrefixMap

object PrefixStorageTag : FeatureElementTag<MutableGuildPrefixMap>

val FeatureContext.prefixMap
    get() = queryExpectOne(PrefixStorageTag)

object VersioningStorageTag : FeatureElementTag<VersioningStorage>

val FeatureContext.versioningStorage
    get() = queryExpectOne(VersioningStorageTag)

object CommandOutputMappingTag : FeatureElementTag<CommandOutputMapping>

val FeatureContext.commandOutputMapping
    get() = queryExpectOne(CommandOutputMappingTag)
