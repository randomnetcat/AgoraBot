package org.randomcat.agorabot.config

import org.randomcat.agorabot.FeatureContext
import org.randomcat.agorabot.FeatureElementTag
import org.randomcat.agorabot.queryExpectOne

object ConfigPersistServiceTag : FeatureElementTag<ConfigPersistService>

val FeatureContext.configPersistService
    get() = queryExpectOne(ConfigPersistServiceTag)
