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

object ButtonRequestDataMapTag : FeatureElementTag<ButtonRequestDataMap>

val FeatureContext.buttonRequestDataMap
    get() = queryExpectOne(ButtonRequestDataMapTag)

private object ButtonHandlerMapCacheKey

val FeatureContext.buttonHandlerMap
    get() = cache(ButtonHandlerMapCacheKey) {
        ButtonHandlerMap.mergeDisjointHandlers(
            queryAll(ButtonDataTag).values.filterIsInstance<FeatureButtonData.RegisterHandlers>().map { it.handlerMap },
        )
    }

object CommandOutputMappingTag : FeatureElementTag<CommandOutputMapping>

val FeatureContext.commandOutputMapping
    get() = queryExpectOne(CommandOutputMappingTag)
