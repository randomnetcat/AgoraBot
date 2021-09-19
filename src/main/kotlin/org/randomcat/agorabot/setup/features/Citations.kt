package org.randomcat.agorabot.setup.features

import org.randomcat.agorabot.config.parsing.features.CitationsConfig
import org.randomcat.agorabot.config.parsing.features.readCitationsConfig
import org.randomcat.agorabot.setup.BotDataPaths

fun setupCitationsConfig(paths: BotDataPaths): CitationsConfig? {
    return readCitationsConfig(paths.featureConfigDir.resolve("citations.json"))
}
