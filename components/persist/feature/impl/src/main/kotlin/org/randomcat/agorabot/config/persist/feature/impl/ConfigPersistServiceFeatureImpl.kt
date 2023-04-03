package org.randomcat.agorabot.config.persist.feature.impl

import org.randomcat.agorabot.FeatureSource
import org.randomcat.agorabot.FeatureSourceFactory
import org.randomcat.agorabot.config.persist.feature.ConfigPersistServiceTag
import org.randomcat.agorabot.config.persist.impl.DefaultConfigPersistService

@FeatureSourceFactory
fun configPersistFactory() = FeatureSource.ofConstant("config_persist_default",
    ConfigPersistServiceTag,
    DefaultConfigPersistService,
    )
