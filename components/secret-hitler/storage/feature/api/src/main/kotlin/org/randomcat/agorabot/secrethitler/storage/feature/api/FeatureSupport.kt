package org.randomcat.agorabot.secrethitler.storage.feature.api

import org.randomcat.agorabot.*
import org.randomcat.agorabot.secrethitler.storage.api.SecretHitlerMutableImpersonationMap
import org.randomcat.agorabot.secrethitler.storage.api.SecretHitlerRepository

object SecretHitlerRepositoryTag : FeatureElementTag<SecretHitlerRepository>

val FeatureContext.secretHitlerRepostitory
    get() = queryExpectOne(SecretHitlerRepositoryTag)

object SecretHitlerImpersonationMapTag : FeatureElementTag<SecretHitlerMutableImpersonationMap>

val FeatureContext.secretHitlerImpersonationMap
    get() = tryQueryExpectOne(SecretHitlerImpersonationMapTag).valueOrNull()
