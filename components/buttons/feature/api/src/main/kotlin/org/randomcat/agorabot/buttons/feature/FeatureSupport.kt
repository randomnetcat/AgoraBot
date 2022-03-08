package org.randomcat.agorabot.buttons.feature

import org.randomcat.agorabot.FeatureContext
import org.randomcat.agorabot.FeatureElementTag
import org.randomcat.agorabot.buttons.ButtonHandlerMap
import org.randomcat.agorabot.buttons.ButtonRequestDataMap
import org.randomcat.agorabot.queryExpectOne

sealed class FeatureButtonData {
    object NoButtons : FeatureButtonData()
    data class RegisterHandlers(val handlerMap: ButtonHandlerMap) : FeatureButtonData()
}

object ButtonRequestDataMapTag : FeatureElementTag<ButtonRequestDataMap>

val FeatureContext.buttonRequestDataMap
    get() = queryExpectOne(ButtonRequestDataMapTag)

private object ButtonHandlerMapCacheKey

object ButtonDataTag : FeatureElementTag<FeatureButtonData>

val FeatureContext.buttonHandlerMap
    get() = cache(ButtonHandlerMapCacheKey) {
        ButtonHandlerMap.mergeDisjointHandlers(
            queryAll(ButtonDataTag).values.filterIsInstance<FeatureButtonData.RegisterHandlers>().map { it.handlerMap },
        )
    }
