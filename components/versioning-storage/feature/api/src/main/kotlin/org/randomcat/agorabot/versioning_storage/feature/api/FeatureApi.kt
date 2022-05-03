package org.randomcat.agorabot.versioning_storage.feature.api

import org.randomcat.agorabot.FeatureContext
import org.randomcat.agorabot.FeatureElementTag
import org.randomcat.agorabot.queryExpectOne
import org.randomcat.agorabot.versioning_storage.api.VersioningStorage

object VersioningStorageTag : FeatureElementTag<VersioningStorage>

val FeatureContext.versioningStorage
    get() = queryExpectOne(VersioningStorageTag)
