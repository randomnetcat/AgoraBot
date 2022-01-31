package org.randomcat.agorabot.config.persist.feature

import org.randomcat.agorabot.FeatureContext
import org.randomcat.agorabot.FeatureElementTag
import org.randomcat.agorabot.config.persist.ConfigPersistService
import org.randomcat.agorabot.queryExpectOne

object ConfigPersistServiceTag : FeatureElementTag<ConfigPersistService>

val FeatureContext.configPersistService
    get() = queryExpectOne(ConfigPersistServiceTag)
