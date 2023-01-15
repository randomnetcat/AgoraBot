package org.randomcat.agorabot.config

import org.randomcat.agorabot.CommandOutputMapping
import org.randomcat.agorabot.FeatureElementTag
import org.randomcat.agorabot.listener.MutableGuildPrefixMap

object PrefixStorageTag : FeatureElementTag<MutableGuildPrefixMap>

object CommandOutputMappingTag : FeatureElementTag<CommandOutputMapping>
