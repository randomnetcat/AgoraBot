package org.randomcat.agorabot.features

import org.randomcat.agorabot.*
import org.randomcat.agorabot.config.ConfigPersistServiceTag
import org.randomcat.agorabot.config.DefaultConfigPersistService

@FeatureSourceFactory
fun configPersistFactory() = FeatureSource.ofConstant("config_persist_default", object : Feature {
    override fun <T> query(context: FeatureContext, tag: FeatureElementTag<T>): FeatureQueryResult<T> {
        if (tag is ConfigPersistServiceTag) return tag.result(DefaultConfigPersistService)
        return FeatureQueryResult.NotFound
    }
})
