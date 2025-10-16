package org.randomcat.agorabot.buttons.feature

import org.randomcat.agorabot.FeatureElementTag
import org.randomcat.agorabot.buttons.ButtonHandlerMap
import org.randomcat.agorabot.buttons.ButtonRequestDataMap

sealed class FeatureButtonData {
    object NoButtons : FeatureButtonData()
    data class RegisterHandlers(val handlerMap: ButtonHandlerMap) : FeatureButtonData()
}

object ButtonRequestDataMapTag : FeatureElementTag<ButtonRequestDataMap>
object ButtonDataTag : FeatureElementTag<FeatureButtonData>
object ButtonHandlerMapTag : FeatureElementTag<ButtonHandlerMap>
