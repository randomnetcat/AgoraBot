package org.randomcat.agorabot.community_message.feature

import org.randomcat.agorabot.FeatureContext
import org.randomcat.agorabot.FeatureElementTag
import org.randomcat.agorabot.community_message.CommunityMessageStorage
import org.randomcat.agorabot.queryExpectOne

object CommunityMessageStorageTag : FeatureElementTag<CommunityMessageStorage>

val FeatureContext.communityMessageStorage
    get() = queryExpectOne(CommunityMessageStorageTag)
