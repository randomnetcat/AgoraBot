package org.randomcat.agorabot.commands.impl

import org.randomcat.agorabot.FeatureContext
import org.randomcat.agorabot.FeatureElementTag
import org.randomcat.agorabot.commands.base.BaseCommandStrategy
import org.randomcat.agorabot.queryExpectOne

object BaseCommandStrategyTag : FeatureElementTag<BaseCommandStrategy>

val FeatureContext.defaultCommandStrategy
    get() = queryExpectOne(BaseCommandStrategyTag)
